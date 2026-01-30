# Observability Library Architecture

## Goal
Provide a small, composable observability toolkit that works in:
- **Pure Java** (no Spring dependency)
- **Servlet environments** (generic `Filter`)
- **Spring Boot** (via wiring + optional starter)
- **Tracing backends**: `simple` (in-memory) and `otel` (OpenTelemetry)

Core principles:
- **Low cardinality** tags/attributes by default
- **Clear layering** (controller/service/repository/external)
- **Single place** to decide outcome/error classification
- **Request scope** (HTTP filter) separated from **invocation scope** (aspects)

---

## Module boundaries

### 1) `observability-core`
**Responsibilities**
- Contracts + primitives:
    - `MetricsRecorder`, `TelemetryHelper`
    - `Span`, `SpanContext`, `SpanFactory`, `SpanHolder`
    - `TraceIdGenerator`, `RandomTraceIdGenerator`
    - `TracePropagation` (W3C + B3)
    - Tag model: `MetricTags`, `MetricTag`, `Layer`, `TagsFactory`
    - Error model: `ErrorClassifier`, `ErrorInfo`, `ErrorKind`, `Outcome`

**Must NOT depend on**
- Spring, Servlet API, SLF4J, OTel

**Key invariants**
- Tags should remain **low-cardinality** (see TAGGING_SCHEMA.md)
- `SpanHolder` is a **ThreadLocal stack**; must be cleared by scopes

---

### 2) `observability-slf4j`
**Responsibilities**
- Writes canonical fields into SLF4J MDC:
    - `LoggingContext` uses `LogFields` keys
    - `TraceMdcScope` safely sets trace MDC and restores previous state

**Depends on**
- `observability-core`
- `slf4j-api`

**Does NOT do**
- HTTP-specific extraction
- Span creation
- Metrics recording

---

### 3) `observability-aspectj`
**Responsibilities**
- Layer instrumentation using AspectJ:
    - `ControllerObservationAspect`
    - `ServiceObservationAspect`
    - `RepositoryObservationAspect`
- Bootstrapping runtime:
    - `ObservabilityBootstrap` + `ObservabilityRuntime`
- Execution orchestration:
    - `ObservationExecutor` (spans + MDC + metrics + error classification)
- Policy:
    - `LayerObservationPolicy`, `LayerPolicyRegistry`
- Timing:
    - `ExclusiveTimingStack` for inclusive/exclusive durations

**Depends on**
- `observability-core`
- `observability-slf4j`
- `aspectjrt`

**Runtime requirement**
- AspectJ weaving via `-javaagent:aspectjweaver.jar`
- `META-INF/aop.xml` must include your app packages and aspect classes

---

### 4) `observability-servlet`
**Responsibilities**
- Request-scoped logging and metrics via `ObservabilityHttpLoggingFilter`:
    - correlation fields (`requestId`, Magus ids if present)
    - route/path/method/status/duration/outcome
    - cleans up MDC fields it owns

**Depends on**
- `observability-core`
- `observability-slf4j`
- Servlet API (provided)

**Important boundary**
- Filter owns request fields.
- Aspects own per-invocation context fields (layer/operation/entity/db_operation).

---

### 5) `observability-otel`
**Responsibilities**
- Bridge your `Span` abstraction to OpenTelemetry:
    - `OtelSpanFactory` creates spans using injected `OpenTelemetry`
    - `OtelSpanAdapter` adapts OTel span to your `Span` interface

**Depends on**
- `observability-core`
- OTel API/SDK (via BOM)

**Notes**
- Your design uses your `TraceIdGenerator` for span IDs and stores them as attributes.
- OTel actual trace/span IDs are still managed by OTel; your custom IDs are supplemental.

---

## Runtime flows

### HTTP request flow (Spring Boot / Servlet)
1. **Servlet filter** starts request scope:
    - extract inbound trace (`TracePropagation.extract`)
    - put correlation + http fields into MDC (`LoggingContext`)
    - derive `http.outcome` via `ErrorClassifier.toHttpOutcome(...)`
2. **Controller aspect** wraps controller method:
    - creates child span from current span (via `SpanHolder`)
    - creates tags via `BaseTagsFactory`
    - records metrics and durations
3. **Service / Repository aspects** do the same, nested
4. **Finally blocks**:
    - aspects end span + record durations
    - filter logs response + clears request MDC

---

## Span & Scope model

### ThreadLocal span stack
- `SpanHolder` holds the current span stack (nesting supported).
- Activation is done by `Span.activate()` returning `SpanScope`:
    - `SimpleSpan.activate()` -> `DefaultSpanScope` pushes/pops span
    - `OtelSpanAdapter.activate()` uses `delegate.makeCurrent()` + pushes/pops on `SpanHolder`

### Why both OTel Context + SpanHolder?
- OTel needs `Context` for exporter correctness.
- Your aspects need a unified abstraction (`SpanHolder.current()`) independent of backend.

---

## Error and outcome model

### Classification
- `ErrorClassifier.classify(Throwable, httpStatus?)` returns:
    - `ErrorKind.BUSINESS` for 4xx (or later domain rules)
    - `ErrorKind.TECHNICAL` for 5xx / unhandled exceptions
- `Outcome` mapping in aspects:
    - business -> `BUSINESS_ERROR`
    - technical -> `TECHNICAL_ERROR`

### Span status
- On exception: set span `SpanStatus.ERROR`, record exception details
- On success: set `SpanStatus.OK`
- Important: Business errors may not always mark span ERROR depending on policy; your current executor marks ERROR on any thrown exception.

---

## Configuration in Spring Boot (example app)
- Initialize runtime once:
    - `ObservabilityBootstrap.init(appName, serviceName, telemetryHelper, traceIdGenerator)`
    - `Tracing.setSpanFactory(spanFactory)`
- Configure layer policies from properties (full/nospan/metricsonly/disabled)

---

## Extension points
- Add `Layer.EXTERNAL_SERVICE` aspect for outbound calls
- Add domain classification in `ErrorClassifier` (BusinessException, ValidationException)
- Add Spring Boot starter for auto-wiring (optional)

---

## Non-goals / Known limitations
- ThreadLocal propagation does not cross async boundaries automatically.
    - For async/executors you must copy MDC + span context explicitly.
- Metrics cardinality can explode if you put user ids, paths with variables, etc.
    - Enforce schema and route normalization (see TAGGING_SCHEMA.md).
