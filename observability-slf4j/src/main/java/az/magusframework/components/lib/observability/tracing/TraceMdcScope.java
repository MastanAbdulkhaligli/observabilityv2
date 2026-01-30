package az.magusframework.components.lib.observability.tracing;

import az.magusframework.components.lib.observability.core.tracing.span.Span;
import az.magusframework.components.lib.observability.core.tracing.span.SpanHolder;
import az.magusframework.components.lib.observability.logging.LoggingContext;

/**
 * Synchronizes the current Span (SpanHolder.current()) into SLF4J MDC
 * for the duration of the scope.
 *
 * Usage:
 *   try (var ms = TraceMdcScope.fromCurrent()) {
 *       ...
 *   }
 *
 * Or:
 *   try (var ms = TraceMdcScope.fromSpan(span)) {
 *       ...
 *   }
 */
public final class TraceMdcScope implements AutoCloseable {

    private final LoggingContext.TraceState prev;

    private TraceMdcScope(LoggingContext.TraceState prev) {
        this.prev = prev;
    }

    /**
     * Set MDC from the current active span (SpanHolder.current()).
     * If there is no current span, it clears trace MDC (by LoggingContext behavior).
     */
    public static TraceMdcScope fromCurrent() {
        LoggingContext.TraceState prev = LoggingContext.captureTraceState();
        Span current = SpanHolder.current();
        LoggingContext.putTraceContext(current);
        return new TraceMdcScope(prev);
    }

    /**
     * Set MDC to the provided span and restore previous trace MDC on close.
     */
    public static TraceMdcScope fromSpan(Span span) {
        LoggingContext.TraceState prev = LoggingContext.captureTraceState();
        LoggingContext.putTraceContext(span);
        return new TraceMdcScope(prev);
    }

    /**
     * Alias for fromSpan(span) (kept for semantic clarity: "set next").
     */
    public static TraceMdcScope set(Span next) {
        return fromSpan(next);
    }

    @Override
    public void close() {
        LoggingContext.restoreTraceState(prev);
    }
}
