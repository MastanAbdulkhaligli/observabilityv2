# Tagging Schema & Cardinality Rules

## Why this exists
Metrics systems (Prometheus/OTel metrics) suffer from **cardinality explosions**.
This library enforces a low-cardinality tagging model for stable dashboards and alerts.

---

## Core tag set (Layer metrics)

Produced by `BaseTagsFactory.forInvocation(meta)` and used by `ObservationExecutor`:

| Tag key     | Source | Allowed values / notes |
|-------------|--------|------------------------|
| `app`       | `ObservabilityBootstrap.appName()` | Static, low-cardinality |
| `service`   | `ObservabilityBootstrap.serviceName()` | Static, low-cardinality |
| `layer`     | `meta.layer().name()` | `CONTROLLER`, `SERVICE`, `REPOSITORY`, `EXTERNAL_SERVICE` |
| `operation` | `OperationName.of(jp)` | `Class#method` sanitized & clamped |
| `outcome`   | `ObservationExecutor` | `SUCCESS`, `BUSINESS_ERROR`, `TECHNICAL_ERROR`, ... |

### Operation format
- Generated as: `<SimpleClassName>#<methodName>`
- Sanitized: only `[A-Za-z0-9_-\.#]`, everything else becomes `_`
- Clamped to 64 characters (current code)

**✅ Good**
- `UserService#loadProfile`
- `DemoRepository#findById`

**❌ Bad**
- raw URLs, SQL strings, request IDs

---

## Repository extra tags

Added in `RepositoryObservationAspect`:

| Tag key | Meaning | Rules |
|---|---|---|
| `entity` | Derived from repository class name | e.g. `User` from `UserRepository`, clamped to 32 |
| `db_operation` | Guessed by method prefix | One of: `SELECT/INSERT/UPDATE/DELETE/UPSERT/COUNT/EXISTS/UNKNOWN` (normalized) |

**Cardinality warning**
- `entity` is safe if it comes from class names.
- Do not inject table names dynamically.

---

## HTTP tags (request-level metrics)
Your servlet filter should emit a separate metric family (e.g., `obs.http.server.duration`).
Recommended tag keys (align with `LogFields.Http` and `TagsFactory.httpServer`):

| Tag key | Example | Cardinality rule |
|---|---|---|
| `http_method` | `GET` | low |
| `http_route`  | `/users/{id}` | must be **route template**, not raw path |
| `status_class` | `2xx`, `4xx`, `5xx` | low |
| `outcome` | `SUCCESS`, `BUSINESS_ERROR`, `TECHNICAL_ERROR` | low |

**Rule: never use raw path** like `/users/12345`.

---

## Allowed tag keys (recommended allowlist)

### Layer metrics (`obs.layer.*`)
- `app`
- `service`
- `layer`
- `operation`
- `outcome`
- `entity` (repository only)
- `db_operation` (repository only)
- `target` / `protocol` (external service calls, future aspect)

### HTTP metrics (`obs.http.*`)
- `app`
- `service`
- `http_method`
- `http_route`
- `status_class`
- `outcome`

---

## Disallowed tags (always high-cardinality)
Never use these values as metric tags:
- user identifiers (fin, tin, phone, email)
- request ids, trace ids, span ids
- full exception message
- raw SQL
- full URLs with query params
- headers values

If you need them, put them in **logs** (and even there, prefer structured + sampling).

---

## Naming conventions
- Metric names: `obs.<area>.<thing>` (dot-separated)
    - `obs.layer.duration`
    - `obs.layer.exclusive_duration`
    - `obs.http.server.duration`
- Tag keys: lowercase snake or lowercase words
    - You currently use `db_operation` and `http_route` which is fine.

---

## Testing cardinality
Before production:
- Load test common endpoints.
- Inspect exported metric series count.
- If series count grows linearly with traffic → you likely used high-cardinality tag values.
