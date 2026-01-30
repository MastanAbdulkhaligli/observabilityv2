package az.magusframework.components.lib.observability.core.tracing.span;

import az.magusframework.components.lib.observability.core.tags.MetricTags;

/**
 * <h1>SpanBuilder</h1>
 * <p>
 * A fluent API for configuring a {@link Span} before it begins.
 * This ensures that metadata and parentage are established before the
 * monotonic start time is captured.
 * </p>
 *
 * <p><b>Thread Safety:</b> Builders are generally not thread-safe and should
 * be used within a single method scope.</p>
 */
public interface SpanBuilder {

    /**
     * Sets the parent span explicitly.
     * If not called, the builder typically defaults to the currently active span
     * in the {@link SpanHolder}.
     */
    SpanBuilder setParent(Span parent);

    /**
     * Sets the role of the span (e.g., SERVER, CLIENT, INTERNAL).
     * Defaults to {@link SpanKind#INTERNAL}.
     */
    SpanBuilder setKind(SpanKind kind);

    /**
     * Adds a metadata attribute to the span before it starts.
     * Use this for static data known at creation time (e.g., 'db.system').
     */
    SpanBuilder setAttribute(String key, String value);

    /**
     * Batch-adds attributes from a {@link MetricTags} collection.
     */
    default SpanBuilder setAttributes(MetricTags tags) {
        if (tags != null) {
            tags.asList().forEach(tag -> setAttribute(tag.key(), tag.value()));
        }
        return this;
    }

//    Tracing.get()
//            .httpRootSpan("process-order", tags, inboundContext, generator)
//    .setAttributes(baseTags)          // ← one line instead of many .setAttribute()
//    .start();

    /**
     * Finalizes the configuration, captures the start timestamp,
     * and returns the live Span.
     *
     * @return A started {@link Span} instance.
     */
    Span start();
}