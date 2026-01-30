package az.magusframework.components.lib.observability.core.tracing.span;

import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.tags.MetricTags;

/**
 * <h1>Span</h1>
 * <p>
 * Represents a single operation within a trace. A span tracks the lifecycle
 * of a unit of work, capturing timing, status, and metadata attributes.
 * </p>
 *
 * <p><b>Lifecycle Rules:</b></p>
 * <ul>
 * <li>A span must be {@link #end()}ed to calculate duration and report data.</li>
 * <li>Once ended, a span is immutable; further modifications will be ignored.</li>
 * </ul>
 */
public interface Span {

    SpanContext context();

    SpanKind kind();

    /** Free-form name, optional. Keep low-cardinality. */
    String name();

    /** Start time in nanos (monotonic). */
    long startNanos();

    /** End time in nanos (monotonic), or -1 if not ended. */
    long endNanos();

    boolean isEnded();

    SpanStatus status();

    /** Low-cardinality error classification. */
    ErrorKind errorKind();

    /** Low-cardinality stable code (e.g., HTTP_5XX, UNHANDLED_EXCEPTION). */
    String errorCode();

    /** Set name (optional). Keep low-cardinality. */
    void setName(String name);

    /** Set status explicitly. */
    void setStatus(SpanStatus status);

    /** Record error classification. Implementation should set status=ERROR. */
    void recordError(ErrorKind kind, String code);

    /** End the span. Safe to call multiple times. */
    void end();

    /**
     * Activate this span as current on this thread.
     * This uses SpanHolder (ThreadLocal stack).
     */
    SpanScope activate();

    /** Optional: enrich with full exception details (type + message) */
    void recordException(Throwable t);

    String errorType();
    String errorMessage();

    void setAttribute(String key, String value);

    default void setAttributes(MetricTags tags) {
        if (tags == null) return;
        tags.asList().forEach(tag -> setAttribute(tag.key(), tag.value()));
    }
}