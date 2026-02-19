package az.magusframework.components.lib.observability.otel;

import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.tracing.span.Span;
import az.magusframework.components.lib.observability.core.tracing.span.SpanContext;
import az.magusframework.components.lib.observability.core.tracing.span.SpanHolder;
import az.magusframework.components.lib.observability.core.tracing.span.SpanKind;
import az.magusframework.components.lib.observability.core.tracing.span.SpanScope;
import az.magusframework.components.lib.observability.core.tracing.span.SpanStatus;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import java.util.Objects;

public class OtelSpanAdapter implements Span {

    private final io.opentelemetry.api.trace.Span delegate;

    // IMPORTANT: These MUST match the delegate’s real SpanContext IDs
    private final String traceId;
    private final String spanId;
    private final String parentSpanId;
    private final boolean sampled;

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
     * @param delegate      Actual OTel span
     * @param traceId       MUST be delegate.getSpanContext().getTraceId()
     * @param spanId        MUST be delegate.getSpanContext().getSpanId()
     * @param parentSpanId  Inbound parent span id (nullable)
     * @param sampled       MUST be delegate.getSpanContext().isSampled()
     * @param kind          Magus span kind
     * @param operationName Operation name
     */
    public OtelSpanAdapter(
            io.opentelemetry.api.trace.Span delegate,
            String traceId,
            String spanId,
            String parentSpanId,
            boolean sampled,
            SpanKind kind,
            String operationName
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.sampled = sampled;
        this.kind = kind;
        this.operationName = operationName;
        this.startNanos = System.nanoTime();
    }

    @Override
    public SpanContext context() {
        return new SpanContext(traceId, spanId, parentSpanId, sampled);
    }

    @Override
    public SpanKind kind() {
        return kind;
    }

    @Override
    public String name() {
        return (operationName != null && !operationName.isBlank()) ? operationName : "otel-span";
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
     * OpenTelemetry does not support renaming after start; no-op.
     */
    @Override
    public void setName(String name) {
        // no-op
    }

    @Override
    public void setStatus(SpanStatus status) {
        this.status = status;

        if (status == SpanStatus.OK) {
            delegate.setStatus(StatusCode.OK);
        } else if (status == SpanStatus.ERROR) {
            delegate.setStatus(StatusCode.ERROR);
        }
    }

    @Override
    public void recordError(ErrorKind kind, String code) {
        this.errorKindLocal = kind;
        this.errorCodeLocal = code;
        this.status = SpanStatus.ERROR;

        delegate.setStatus(StatusCode.ERROR);
        delegate.setAttribute("error.kind", kind != null ? kind.name() : "UNKNOWN");
        delegate.setAttribute("error.code", code != null ? code : "UNKNOWN");
    }

    @Override
    public void end() {
        if (ended) return;
        ended = true;
        endNanos = System.nanoTime();
        delegate.end();
    }

    @Override
    public SpanScope activate() {
        Scope otelScope = delegate.makeCurrent();
        SpanHolder.push(this);

        return () -> {
            try {
                otelScope.close();
            } finally {
                SpanHolder.pop();
            }
        };
    }

    @Override
    public void recordException(Throwable t) {
        if (t == null) return;
        this.errorTypeLocal = t.getClass().getName();
        this.errorMessageLocal = t.getMessage();
        delegate.recordException(t);
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
        if (key == null || value == null) return;
        delegate.setAttribute(key, value);
    }

    @Override
    public void setAttributes(MetricTags tags) {
        if (tags == null) return;
        tags.asList().forEach(tag -> setAttribute(tag.key(), tag.value()));
    }
}
