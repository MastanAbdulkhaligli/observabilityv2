package az.magusframework.components.lib.observability.logging;

import az.magusframework.components.lib.observability.core.logging.LogFields;
import az.magusframework.components.lib.observability.core.tracing.span.Span;
import az.magusframework.components.lib.observability.core.tracing.span.SpanContext;
import org.slf4j.MDC;

import java.util.Optional;

/**
 * <h1>LoggingContext</h1>
 * <p>
 * High-performance adapter for SLF4J MDC. Responsible for populating
 * and clearing structured logging fields using canonical keys defined in {@link LogFields}.
 * </p>
 */
public final class LoggingContext {

    private LoggingContext() {}

    /**
     * Captures current trace state from MDC for manual propagation (e.g., across threads).
     */
    public record TraceState(
            String traceId,
            String spanId,
            String parentSpanId,
            String sampled
    ) {}

    public static TraceState captureTraceState() {
        return new TraceState(
                MDC.get(LogFields.Trace.TRACE_ID),
                MDC.get(LogFields.Trace.SPAN_ID),
                MDC.get(LogFields.Trace.PARENT_SPAN_ID),
                MDC.get(LogFields.Trace.SAMPLED)
        );
    }
    public static void clearErrorOnly() {
        MDC.remove(LogFields.Error.TYPE);
        MDC.remove(LogFields.Error.MESSAGE);
        MDC.remove(LogFields.Error.LAYER);
        MDC.remove(LogFields.Error.KIND);
        MDC.remove(LogFields.Error.CODE);
    }

    public static void restoreTraceState(TraceState state) {
        if (state == null) {
            clearTraceContext();
            return;
        }
        putOrRemove(LogFields.Trace.TRACE_ID, state.traceId());
        putOrRemove(LogFields.Trace.SPAN_ID, state.spanId());
        putOrRemove(LogFields.Trace.PARENT_SPAN_ID, state.parentSpanId());
        putOrRemove(LogFields.Trace.SAMPLED, state.sampled());
    }

    // --- Trace Context ---

    public static void putTraceContext(Span span) {
        putTraceContext(Optional.ofNullable(span).map(Span::context).orElse(null));
    }

    public static void putTraceContext(SpanContext ctx) {
        if (ctx == null) {
            clearTraceContext();
            return;
        }
        MDC.put(LogFields.Trace.TRACE_ID, ctx.traceId());
        MDC.put(LogFields.Trace.SPAN_ID, ctx.spanId());
        putOrRemove(LogFields.Trace.PARENT_SPAN_ID, ctx.parentSpanId());
        MDC.put(LogFields.Trace.SAMPLED, String.valueOf(ctx.sampled()));
    }

    // --- Service & Runtime ---

    public static void putServiceMetadata(String name, String module, String component, String env) {
        MDC.put(LogFields.Service.NAME, name);
        MDC.put(LogFields.Service.MODULE, module);
        MDC.put(LogFields.Service.COMPONENT, component);
        MDC.put(LogFields.Service.ENV, env);
    }

    public static void putRuntimeContext(String host, String ip, String node) {
        MDC.put(LogFields.Runtime.HOST, host);
        MDC.put(LogFields.Runtime.IP, ip);
        MDC.put(LogFields.Runtime.NODE, node);
    }

    public static void putErrorKindAndCode(String kind, String code) {
        if (kind != null) MDC.put(LogFields.Error.KIND, kind);
        if (code != null) MDC.put(LogFields.Error.CODE, code);
    }

    // --- HTTP & Correlation ---

    public static void putHttpContext(String method, String path, String route, String direction) {
        MDC.put(LogFields.Http.METHOD, method);
        MDC.put(LogFields.Http.PATH, path);
        MDC.put(LogFields.Http.ROUTE, route);
        MDC.put(LogFields.Http.DIRECTION, direction);
    }

    public static void putHttpResponse(int status, long durationMs, String outcome) {
        MDC.put(LogFields.Http.STATUS, String.valueOf(status));
        MDC.put(LogFields.Http.DURATION_MS, String.valueOf(durationMs));
        MDC.put(LogFields.Http.OUTCOME, outcome);
    }

    public static void putCorrelationIds(String requestId, String baggage) {
        MDC.put(LogFields.Correlation.REQUEST_ID, requestId);
        putOrRemove(LogFields.Correlation.BAGGAGE, baggage);
    }

    public static void putMagusMetadata(String requestNumber, String compId) {
        putOrRemove(LogFields.Correlation.REQUEST_NUMBER, requestNumber);
        putOrRemove(LogFields.Correlation.COMP_ID, compId);
    }

    // --- Error Handling ---

    public static void putErrorContext(String type, String message, String kind, String code, String layer) {
        MDC.put(LogFields.Error.TYPE, type);
        putOrRemove(LogFields.Error.MESSAGE, message);
        MDC.put(LogFields.Error.KIND, kind);
        MDC.put(LogFields.Error.CODE, code);
        MDC.put(LogFields.Error.LAYER, layer);
    }

    public static void putServiceContext(String serviceName, String layer, String operation) {
        MDC.put(LogFields.Service.NAME, serviceName);
        MDC.put(LogFields.Context.LAYER, layer);
        MDC.put(LogFields.Context.OPERATION, operation);
    }

    public static void putErrorLayerIfAbsent(String layer) {
        if (layer == null) return;
        if (MDC.get(LogFields.Error.LAYER) == null) {
            MDC.put(LogFields.Error.LAYER, layer);
        }
    }
    public static void putMagusIds(String requestNumber, String compId) {
        if (requestNumber != null && !requestNumber.isBlank()) {
            MDC.put(LogFields.Correlation.REQUEST_NUMBER, requestNumber);
        }
        if (compId != null && !compId.isBlank()) {
            MDC.put(LogFields.Correlation.COMP_ID, compId);
        }
    }

    public static void putCorrelationId(String requestId) {
        if(requestId != null && !requestId.isBlank()) {
            MDC.put(LogFields.Correlation.REQUEST_ID, requestId);
        }
    }

    public static void putHttpMethodAndPath(String method, String path) {

        if(method != null && !method.isBlank()) {
            MDC.put(LogFields.Http.METHOD, method);
        }

        if(path != null && !path.isBlank()) {
            MDC.put(LogFields.Http.PATH, path);
        }
    }

    public static void putHttpDirection(String direction) {
        if(direction != null && !direction.isBlank()) {
            MDC.put(LogFields.Http.DIRECTION, direction);
        }
    }

    public static void putHttpClientIp(String clientIp) {
        if(clientIp != null && !clientIp.isBlank()) {
            MDC.put(LogFields.Http.CLIENT_IP, clientIp);
        }
    }

    public static void putHttpRoute(String route) {
        if(route != null && !route.isBlank()) {
            MDC.put(LogFields.Http.ROUTE, route);
        }
    }

    public static void putHttpStatusAndDuration(int status, long durationMs) {
        MDC.put(LogFields.Http.STATUS, String.valueOf(status));
        MDC.put(LogFields.Http.DURATION_MS, String.valueOf(durationMs));
    }

    public static void putHttpOutcome(String outcome) {
        if(outcome != null && !outcome.isBlank()) {
            MDC.put(LogFields.Http.OUTCOME, outcome);
        }
    }







    public static void putErrorFromThrowableIfAbsent(Throwable e, String layer) {
        if (e == null) return;

        putErrorLayerIfAbsent(layer);

        if (MDC.get(LogFields.Error.TYPE) == null) {
            MDC.put(LogFields.Error.TYPE, e.getClass().getName());
        }

        if (MDC.get(LogFields.Error.MESSAGE) == null && e.getMessage() != null) {
            MDC.put(LogFields.Error.MESSAGE, e.getMessage());
        }
    }

    // --- Cleanup Methods ---

    public static void clearTraceContext() {
        MDC.remove(LogFields.Trace.TRACE_ID);
        MDC.remove(LogFields.Trace.SPAN_ID);
        MDC.remove(LogFields.Trace.PARENT_SPAN_ID);
        MDC.remove(LogFields.Trace.SAMPLED);
    }

    public static void clearContextOnly() {
        MDC.remove(LogFields.Context.LAYER);
        MDC.remove(LogFields.Context.OPERATION);
        MDC.remove(LogFields.Context.ENTITY);
        MDC.remove(LogFields.Context.DB_OPERATION);
        MDC.remove(LogFields.Context.TARGET_SYSTEM);
        MDC.remove(LogFields.Context.PROTOCOL);
    }



    public static void clearAll() {
        MDC.clear();
    }

    private static void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}