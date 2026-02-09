package az.magusframework.components.lib.observability.otel;

import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.tracing.span.*;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

public class OtelSpanAdapter implements Span {

    private final io.opentelemetry.api.trace.Span delegate;
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final SpanKind kind;
    private final String operationName;
    private final long startNanos;

    private boolean ended = false;
    private long endNanos = -1L;
    private SpanStatus status = SpanStatus.UNSET;
    private ErrorKind errorKindLocal = null;
    private String errorCodeLocal = null;
    private String errorTypeLocal = null;
    private String errorMessageLocal = null;

    /**
     * Constructs a new adapter for an existing OTel span.
     *
     * @param delegate      The actual OTel span created by a Tracer.
     * @param traceId       The W3C Trace ID string.
     * @param spanId        The W3C Span ID string.
     * @param parentSpanId  The W3C Parent ID string (nullable).
     * @param kind          The logical role of the span (e.g., SERVER, INTERNAL).
     * @param operationName The high-level name of the operation.
     */
    public OtelSpanAdapter(
            io.opentelemetry.api.trace.Span delegate,
            String traceId,
            String spanId,
            String parentSpanId,
            SpanKind kind,
            String operationName) {

        this.delegate = delegate;
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.kind = kind;
        this.operationName = operationName;
        this.startNanos = System.nanoTime();
    }

    /**
     * Reconstructs a Magus {@link SpanContext} from the adapter's state.
     * Currently assumes traces are always sampled (hardcoded {@code true}).
     */
    @Override
    public SpanContext context() {
        return new SpanContext(traceId, spanId, parentSpanId, true);
    }

    @Override
    public SpanKind kind() {
        return kind;
    }

    @Override
    public String name() {
        return operationName != null ? operationName : "otel-span";
    }

    @Override
    public long startNanos() {
        return startNanos;
    }

    @Override
    public long endNanos() {
        return endNanos;
    }

    @Override
    public boolean isEnded() {
        return ended;
    }

    @Override
    public SpanStatus status() {
        return status;
    }

    @Override
    public ErrorKind errorKind() {
        return errorKindLocal;
    }

    @Override
    public String errorCode() {
        return errorCodeLocal;
    }

    /**
     * @implNote OpenTelemetry does not support renaming spans after they have been started.
     */
    @Override
    public void setName(String name) {
        // OpenTelemetry does not support renaming after start
    }

    /**
     * Maps Magus {@link SpanStatus} to OTel {@link StatusCode}.
     */
    @Override
    public void setStatus(SpanStatus status) {
        this.status = status;
        if (status == SpanStatus.OK) {
            delegate.setStatus(StatusCode.OK);
        } else if (status == SpanStatus.ERROR) {
            delegate.setStatus(StatusCode.ERROR);
        }
    }

    /**
     * Records error details and propagates them to OTel attributes.
     * Adds {@code error.kind} and {@code error.code} to the OTel span for advanced filtering.
     */
    @Override
    public void recordError(ErrorKind kind, String code) {
        this.errorKindLocal = kind;
        this.errorCodeLocal = code;
        this.status = SpanStatus.ERROR;

        delegate.setStatus(StatusCode.ERROR);
        delegate.setAttribute("error.kind", kind != null ? kind.name() : "UNKNOWN");
        delegate.setAttribute("error.code", code != null ? code : "UNKNOWN");
    }

    /**
     * Finalizes the span duration and closes the OTel delegate.
     */
    @Override
    public void end() {
        if (ended) return;
        ended = true;
        endNanos = System.nanoTime();
        delegate.end();
    }

    /**
     * Synchronizes tracing context across Magus and OTel systems.
     * <p>
     * 1. Makes the OTel span current in the OTel Context.<br>
     * 2. Pushes the Magus Span to {@link SpanHolder} for aspect-based parent lookup.
     * </p>
     *
     * @return A {@link SpanScope} that cleans up both OTel and Magus thread-locals on close.
     */
    @Override
    public SpanScope activate() {
        Scope otelScope = delegate.makeCurrent();
        SpanHolder.push(this);  // ← Push to stack so aspects can find parent

        return () -> {
            otelScope.close();
            SpanHolder.pop();     // ← Pop to clean up stack
        };
    }

    /**
     * Records a Java exception as an OTel Span Event.
     */
    @Override
    public void recordException(Throwable t) {
        if (t != null) {
            this.errorTypeLocal = t.getClass().getName();
            this.errorMessageLocal = t.getMessage();
            delegate.recordException(t);
        }
    }

    @Override
    public String errorType() {
        return errorTypeLocal;
    }

    @Override
    public String errorMessage() {
        return errorMessageLocal;
    }

    @Override
    public void setAttribute(String key, String value) {
        if (key != null && value != null) {
            delegate.setAttribute(key, value);
        }
    }

    /**
     * Bulk-attaches {@link MetricTags} as OTel attributes.
     */
    @Override
    public void setAttributes(MetricTags tags) {
        if (tags != null) {
            tags.asList().forEach(tag -> setAttribute(tag.key(), tag.value()));
        }
    }
}
