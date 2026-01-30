package az.magusframework.components.lib.observability.core.tracing.span;

import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.tags.MetricTags;


/**
 * <h1>SimpleSpan</h1>
 * <p>
 * The standard, high-performance implementation of the {@link Span} interface.
 * Manages the internal state of a trace segment, including monotonic timing,
 * error classification, and attribute storage.
 * </p>
 */
public final class SimpleSpan implements Span {

    private final SpanContext context;
    private final SpanKind kind;
    private final long startNanos;

    private volatile long endNanos = -1L;
    private volatile boolean ended;

    private volatile SpanStatus status = SpanStatus.UNSET;

    private volatile ErrorKind errorKind;
    private volatile String errorCode;

    private volatile String name;

    private volatile String errorType;
    private volatile String errorMessage;


    public SimpleSpan(SpanContext context, SpanKind kind, long startNanos) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        if (kind == null) kind = SpanKind.INTERNAL;
        if (startNanos < 0) startNanos = 0;

        this.context = context;
        this.kind = kind;
        this.startNanos = startNanos;
    }

    @Override
    public SpanContext context() {
        return context;
    }

    @Override
    public SpanKind kind() {
        return kind;
    }

    @Override
    public String name() {
        return name;
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
        return errorKind;
    }

    @Override
    public String errorCode() {
        return errorCode;
    }

    @Override
    public void setName(String name) {
        // Keep low-cardinality in your usage; we only clamp for safety.
        this.name = clamp(name, 80);
    }

    @Override
    public void setStatus(SpanStatus status) {
        if (status == null) return;
        this.status = status;
    }

    @Override
    public void recordError(ErrorKind kind, String code) {
        this.errorKind = kind;
        this.errorCode = clamp(code, 64);
        this.status = SpanStatus.ERROR;
    }

    @Override
    public void end() {
        if (ended) return;
        synchronized (this) {
            if (ended) return;
            this.endNanos = System.nanoTime();
            this.ended = true;
            if (this.status == SpanStatus.UNSET) {
                // Default: if nobody set status, consider OK.
                this.status = SpanStatus.OK;
            }

//            if (this.status == SpanStatus.ERROR) {
//                org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SimpleSpan.class);
//                logger.info("Span ended with error: type={}, message={}, code={}, kind={}",
//                        this.errorType,
//                        this.errorMessage,
//                        this.errorCode,
//                        this.errorKind);
//            }
        }
    }

    @Override
    public SpanScope activate() {
        return new DefaultSpanScope(this);
    }

    private static String clamp(String v, int max) {
        if (v == null) return null;
        String s = v.trim();
        if (s.isEmpty()) return null;
        return (s.length() > max) ? s.substring(0, max) : s;
    }

    @Override
    public void recordException(Throwable t) {
        if (t == null) return;
        this.errorType = t.getClass().getName();
        this.errorMessage = clamp(t.getMessage(), 256);
    }

    @Override
    public String errorType() { return errorType; }

    @Override
    public String errorMessage() { return errorMessage; }

    @Override
    public void setAttribute(String key, String value) {
        // SimpleSpan is no-op / in-memory → we can just ignore or log
        // If you want to track attributes locally, add a Map<String, String> attributes field
        // For now, simplest: do nothing (no-op)
        // System.out.println("setAttribute ignored in SimpleSpan: " + key + "=" + value);
    }

    @Override
    public void setAttributes(MetricTags tags) {
        if (tags != null) {
            tags.asList().forEach(tag -> setAttribute(tag.key(), tag.value()));
        }
    }
}