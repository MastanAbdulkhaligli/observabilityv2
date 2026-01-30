# Logging Schema (MDC / JSON Fields)

## Overview
This library writes structured logging fields into SLF4J MDC using canonical keys from:
- `org.example.observability.core.logging.LogFields`
- `com.example.observability.slf4j.logging.LoggingContext`

A JSON encoder (Logback/Log4j2) should serialize MDC into nested JSON objects.

---

## Canonical field keys

### Trace fields (`LogFields.Trace`)
| MDC key | Meaning |
|---|---|
| `id` | trace id |
| `spanId` | span id |
| `parentSpanId` | parent span id |
| `sampled` | `true/false` |

Populated by:
- `LoggingContext.putTraceContext(Span)` or `putTraceContext(SpanContext)`
  Used by:
- `TraceMdcScope.set(span)` in aspects

---

### Service fields (`LogFields.Service`)
| MDC key | Meaning |
|---|---|
| `name` | service name |
| `module` | module name |
| `component` | component name |
| `env` | environment |

Populated by:
- typically the HTTP filter or bootstrap wiring (`putServiceMetadata(...)`)

---

### Runtime fields (`LogFields.Runtime`)
| MDC key | Meaning |
|---|---|
| `host` | host |
| `ip` | ip |
| `node` | node name |

Populated by:
- HTTP filter (request scope)

---

### Correlation fields (`LogFields.Correlation`)
| MDC key | Meaning |
|---|---|
| `requestId` | correlation id |
| `compId` | Magus comp id |
| `requestNumber` | Magus request number |
| `baggage` | propagation baggage |

Populated by:
- HTTP filter

---

### HTTP fields (`LogFields.Http`)
| MDC key | Meaning |
|---|---|
| `direction` | inbound/outbound |
| `method` | GET/POST |
| `route` | route template (preferred) |
| `path` | raw path (avoid if possible) |
| `status` | numeric HTTP status |
| `durationMs` | response duration |
| `clientIp` | client ip |
| `outcome` | SUCCESS/BUSINESS_ERROR/TECHNICAL_ERROR |

Populated by:
- HTTP filter

---

### Execution Context fields (`LogFields.Context`)
| MDC key | Meaning |
|---|---|
| `layer` | CONTROLLER/SERVICE/REPOSITORY |
| `operation` | Class#method |
| `entity` | repository entity |
| `dbOperation` | SELECT/INSERT/... |
| `targetSystem` | external target |
| `protocol` | http/soap/kafka/etc |

Populated by:
- Aspects via `MdcScope` and (repository aspect) additional tags

Ownership:
- Filter owns request + http + runtime + correlation
- Aspects own `Context.*`

---

### Error fields (`LogFields.Error`)
| MDC key | Meaning |
|---|---|
| `type` | exception FQN |
| `message` | exception message |
| `errorLayer` | layer that observed error |
| `kind` | BUSINESS/TECHNICAL |
| `code` | stable code (HTTP_5XX, UNHANDLED_EXCEPTION, ...) |

Populated by:
- `ObservationExecutor` catch path
- `ErrorObservation.observe(...)` (if used)
- `LoggingContext.putErrorKindAndCode(...)`

---

## JSON layout expectation
Recommended JSON shape (example):

```json
{
  "timestamp": "2026-01-29T12:00:00.000Z",
  "level": "INFO",
  "logger": "org.example.MyService",
  "message": "request finished",
  "trace": {
    "id": "4bf92f3577b34da6a3ce929d0e0e4736",
    "spanId": "00f067aa0ba902b7",
    "parentSpanId": "b9c7c989f97918e1",
    "sampled": "true"
  },
  "service": {
    "name": "demo-service",
    "module": "unknown-module",
    "component": "unknown-component",
    "env": "local"
  },
  "context": {
    "layer": "SERVICE",
    "operation": "UserService#loadProfile",
    "entity": "User",
    "dbOperation": "SELECT"
  },
  "http": {
    "method": "GET",
    "route": "/users/{id}",
    "status": "200",
    "durationMs": "12.5",
    "outcome": "SUCCESS",
    "clientIp": "10.0.0.1"
  },
  "correlation": {
    "requestId": "req-123",
    "requestNumber": "RN-456",
    "compId": "COMP-99"
  },
  "error": {
    "kind": "TECHNICAL",
    "code": "UNHANDLED_EXCEPTION",
    "type": "java.lang.RuntimeException",
    "message": "boom",
    "errorLayer": "REPOSITORY"
  }
}
