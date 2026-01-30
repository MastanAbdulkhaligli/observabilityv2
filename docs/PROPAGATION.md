# Trace Propagation (W3C + B3 + Baggage)

This document describes how trace context is **extracted** from inbound requests and **injected** into outbound requests in this observability library.

The implementation is centered around:
- `org.example.observability.core.tracing.TracePropagation`
- `org.example.observability.core.tracing.TraceContext`
- `org.example.observability.core.tracing.span.Span` / `SpanContext`
- `org.example.observability.core.tracing.span.SpanHolder` (ThreadLocal current span)

---

## 1) Supported propagation formats

### 1.1 W3C Trace Context (preferred)
Inbound headers:
- `traceparent`
- `tracestate` (optional; accepted, usually ignored)
- `baggage` (optional)

Outbound headers:
- `traceparent`
- `baggage` (if present)

### 1.2 B3 (fallback)
Inbound (either form may appear):
- Single header: `b3`
- Multi header:
    - `X-B3-TraceId`
    - `X-B3-SpanId`
    - `X-B3-Sampled`
    - `X-B3-Flags`
    - `X-B3-ParentSpanId` (optional)

Outbound (recommended):
- Multi header:
    - `X-B3-TraceId`
    - `X-B3-SpanId`
    - `X-B3-Sampled`

Header matching MUST be case-insensitive.

---

## 2) Extraction (inbound)

### 2.1 Precedence rules
When multiple formats exist on the same request, extraction follows this strict order:

1) **W3C**: `traceparent`
2) **B3 single**: `b3`
3) **B3 multi**: `X-B3-TraceId` + `X-B3-SpanId`

If a higher-precedence header is present but invalid, the extractor SHOULD fall back to the next available format.

### 2.2 Extraction result
Extraction produces:

- `parentTraceContext`: a `TraceContext` representing the **inbound parent span**
- `baggage`: optional baggage string (if supported/kept)

Conceptually:
- Your server span becomes a **child** of `parentTraceContext`.
- Baggage is propagated unchanged (but should be sanitized; see Security).

---

## 3) W3C parsing rules

### 3.1 `traceparent` format
`traceparent` must be:

`version-traceid-spanid-flags`

Example:
`00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`

### 3.2 Validation
Reject as invalid if:
- version is not 2 hex chars
- trace-id is not 32 hex chars, or is all zeros
- span-id is not 16 hex chars, or is all zeros
- flags is not 2 hex chars

### 3.3 Sampling
Flags are 1 byte; bit0 indicates sampling:
- `flags & 0x01 == 1` → sampled = true
- otherwise sampled = false

### 3.4 Mapping to `TraceContext`
`TraceContext` fields should be:
- `traceId = <trace-id>`
- `spanId = <span-id>` (the inbound parent span id)
- `parentSpanId = null` (W3C header already expresses the parent)
- `sampled = derived from flags`

`tracestate` MAY be stored separately if you want future compatibility, but can be ignored safely for now.

---

## 4) B3 parsing rules

### 4.1 B3 single header format (`b3`)
Example:
`b3: 80f198ee56343ba864fe8b2a57d3eff7-e457b5a2e4d86bd1-1`

Parts:
- traceId
- spanId
- sampled/debug (optional)

Allowed sampled forms:
- `0` / `1`
- `d` (debug)

### 4.2 B3 multi headers
- `X-B3-TraceId`: 16 or 32 hex
- `X-B3-SpanId`: 16 hex
- `X-B3-Sampled`: `0|1|true|false`
- `X-B3-Flags`: `1` means debug
- `X-B3-ParentSpanId`: optional; not required for correctness

### 4.3 TraceId normalization
If traceId is 16 hex (64-bit), normalize to 32 hex by left-padding with zeros.

Reject traceId/spanId if:
- non-hex
- wrong length (traceId must be 16 or 32; spanId must be 16)
- all zeros

### 4.4 Sampling resolution
Sampling should be resolved as:
1) If `X-B3-Flags == 1` or b3 uses `-d` → sampled = true
2) Else if sampled is present:
    - `1|true` → true
    - `0|false` → false
3) Else: use library default (recommend **false** for safety) or configurable default

**Note:** If your current code defaults to sampled=true when missing, document that explicitly and consider making it configurable.

### 4.5 Mapping to `TraceContext`
- `traceId = normalized traceId`
- `spanId = inbound parent spanId`
- `parentSpanId = X-B3-ParentSpanId` (optional; otherwise null)
- `sampled = resolved`

---

## 5) Injection (outbound)

Outbound propagation is used for client calls (HTTP/SOAP/etc).
You inject the **current span** as the parent for downstream.

### 5.1 What is “current span”?
Current span is obtained from your runtime (typical options):
- `SpanHolder.current()` (ThreadLocal)
- or a backend context (OTel `Context.current()`)

Your library uses `SpanHolder` as the backend-neutral “current”.

### 5.2 W3C injection
Write:
- `traceparent = 00-<traceId>-<spanId>-<flags>`

Where:
- `<traceId>` is the trace id of the current span context
- `<spanId>` is the span id of the current span context
- `<flags>` uses sampled bit (01 if sampled else 00)

Also write:
- `baggage` if present and allowed

### 5.3 B3 injection
Recommended to inject **multi** form (max compatibility):

- `X-B3-TraceId = <traceId>` (32 hex preferred)
- `X-B3-SpanId = <spanId>`
- `X-B3-Sampled = 1|0`

Optionally:
- `X-B3-Flags = 1` when debug

### 5.4 Dual injection (W3C + B3)
Some platforms inject both. If you do, follow:
- Always inject **W3C**
- Inject **B3 multi** additionally only if configured

Default recommendation:
- **Inject W3C only** unless you must support legacy B3-only systems.

---

## 6) Baggage rules

### 6.1 What is baggage?
Baggage is a set of key/value pairs propagated along requests (W3C `baggage` header).

### 6.2 Handling baggage safely
Baggage is untrusted input. Rules:
- Enforce a maximum header length (e.g., 2KB–8KB)
- Optionally allowlist baggage keys that you keep/forward
- Never put PII or secrets into baggage
- Avoid logging baggage by default (or log only in debug with truncation)

---

## 7) Threading & async implications

Both MDC and `SpanHolder` are typically ThreadLocal.
If execution crosses threads (executors, @Async, reactive):
- capture current span context + MDC map
- restore them in the worker thread
- ensure scopes are closed and MDC cleared

Without propagation, spans may appear disconnected in traces.

---

## 8) Recommended defaults (production)

### Extraction
- precedence: W3C → B3 single → B3 multi
- if invalid, fallback to next available
- if none available: create new root trace

### Injection
- default: inject W3C only
- support B3 multi behind a config flag

### Sampling
- respect inbound sampling flags when present
- default missing sampling to **false** (safer) or configurable

---

## 9) Quick examples

### W3C inbound
Request headers:
- `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`

Extracted parent:
- traceId = `4bf92f...4736`
- spanId = `00f067aa0ba902b7`
- sampled = true

Server span becomes child of that parent.

### B3 multi inbound
- `X-B3-TraceId: 80f198ee56343ba8`
- `X-B3-SpanId: e457b5a2e4d86bd1`
- `X-B3-Sampled: 1`

Normalize traceId to:
- `000000000000000080f198ee56343ba8`

---

## 10) Compatibility notes
- If both W3C and B3 exist, W3C wins.
- Keep header casing tolerant.
- Keep normalization strict to avoid corrupted trace graphs.
