# Observability Framework for Java / Spring Boot

A production-grade, modular observability library for Java and Spring Boot applications — providing **distributed tracing**, **structured logging**, **metrics collection**, and **OpenTelemetry integration** through a unified, auto-configurable API.

> Built and battle-tested in a real production environment serving multiple government and banking integrations.

---

## Why This Exists

Most observability setups are either too opinionated (lock you into one vendor) or too manual (require instrumentation everywhere). This framework provides:

- **Auto-instrumentation** via AspectJ — zero boilerplate on your service/controller/repository layers
- **Provider-agnostic core** — swap between OpenTelemetry, SLF4J, or no-op backends without changing your code
- **Spring Boot Starter** — single dependency, zero XML, production-ready out of the box
- **W3C Trace Context** propagation across service boundaries

---

## Modules

```
observabilityv2/
├── observability-core                    # Core abstractions: Tracer, MetricsCollector, Logger interfaces
├── observability-aspectj                 # AspectJ-based auto-instrumentation (controllers, services, repositories)
├── observability-otel                    # OpenTelemetry implementation + OTLP export pipeline
├── observability-slf4j                   # SLF4J/MDC structured logging implementation
├── observability-servlet                 # Servlet filter for HTTP trace context extraction
├── observability-spring-web              # Spring Web MVC integration (request tracing, correlation IDs)
├── observability-spring-boot-autoconfigure  # Spring Boot auto-configuration
├── observability-spring-boot-starter     # Single-dependency starter (include this in your app)
├── observability-noop                    # No-op implementation for testing / disabled environments
├── examples/                             # Working example Spring Boot application
└── docs/                                 # Architecture diagrams and documentation
```

---

## Quick Start

Add the starter to your Spring Boot project:

**Gradle**
```groovy
implementation 'com.mastan:observability-spring-boot-starter:1.0.0'
```

**Maven**
```xml
<dependency>
    <groupId>com.mastan</groupId>
    <artifactId>observability-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

That's it. Auto-configuration wires up tracing, logging, and metrics automatically.

---

## Features

### Distributed Tracing
- W3C Trace Context (`traceparent` / `tracestate`) propagation across HTTP boundaries
- Automatic span creation for controller, service, and repository layers via AspectJ
- Correlation ID strategy for cross-service log correlation
- OpenTelemetry SDK integration with OTLP export to any compatible backend (Jaeger, Tempo, etc.)

### Structured Logging
- SLF4J MDC enrichment — every log line automatically includes `traceId`, `spanId`, `correlationId`
- JSON log format ready for ELK pipeline ingestion (Filebeat → Logstash → Elasticsearch → Kibana)
- Error enrichment with structured exception metadata

### Metrics Collection
- Per-layer performance metrics (controller, service, repository)
- Low-cardinality tagging schema — safe for high-throughput production use
- Prometheus-compatible export

### Auto-Instrumentation (AspectJ)
No manual span creation needed. Annotate once or rely on defaults:

```java
// Automatic — no code changes needed for standard Spring components

// Or explicit if needed:
@Observed(name = "payment.process")
public PaymentResult processPayment(PaymentRequest request) {
    // tracing, timing, and logging handled automatically
}
```

### Provider Flexibility
```yaml
# application.yml
observability:
  provider: otel        # or: slf4j, noop
  tracing:
    enabled: true
    exporter: otlp
    endpoint: http://localhost:4318
  metrics:
    enabled: true
  logging:
    structured: true
    include-trace-context: true
```

---

## Architecture

```
Your Spring Boot App
        │
        ▼
┌─────────────────────────────────────┐
│     observability-spring-web        │  ← HTTP filter, correlation ID injection
│     observability-aspectj           │  ← Auto-instrumentation (AOP)
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│        observability-core           │  ← Tracer / MetricsCollector / Logger interfaces
└──────┬───────────────┬──────────────┘
       │               │
       ▼               ▼
┌────────────┐  ┌──────────────────┐
│  otel      │  │  slf4j           │   ← Pluggable implementations
│  provider  │  │  provider        │
└─────┬──────┘  └──────────────────┘
      │
      ▼
OpenTelemetry Collector (OTLP)
      │
      ├──→ Jaeger / Tempo  (traces)
      ├──→ Prometheus       (metrics)
      └──→ ELK Stack        (logs)
```

---

## Running the Example

```bash
git clone https://github.com/MastanAbdulkhaligli/observabilityv2.git
cd observabilityv2

# Start the observability stack (Jaeger + Prometheus + ELK)
docker-compose -f examples/docker-compose.yml up -d

# Run the example app
./gradlew :examples:bootRun
```

Then open:
- **Jaeger UI**: http://localhost:16686
- **Kibana**: http://localhost:5601
- **Prometheus**: http://localhost:9090

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| Auto-instrumentation | AspectJ |
| Tracing | OpenTelemetry SDK, W3C Trace Context |
| Metrics | Micrometer, Prometheus |
| Logging | SLF4J, Logback, MDC |
| Log pipeline | Filebeat → Logstash → Elasticsearch → Kibana |
| Export protocol | OTLP (HTTP/gRPC) |
| Build | Gradle (multi-module) |

---

## Production Context

This framework was designed and used in production at **DOST RIM** (Azerbaijan), monitoring Java/Spring Boot microservices integrated with government APIs (ASAN Bridge, IAMAS, Egov) and banking systems (Kapital Bank). It handles distributed trace correlation across service boundaries in a multi-service environment.

---

## Roadmap

- [ ] Async/reactive support (WebFlux, CompletableFuture)
- [ ] Kafka message tracing
- [ ] Maven Central publication
- [ ] Grafana dashboard templates

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

## Author

**Mastan Abdulkhaligli**  
Senior Backend Engineer | Distributed Systems & Observability  
[LinkedIn](https://linkedin.com/in/your-profile) · [Email](mailto:mastanabdulkhaligli@gmail.com)
