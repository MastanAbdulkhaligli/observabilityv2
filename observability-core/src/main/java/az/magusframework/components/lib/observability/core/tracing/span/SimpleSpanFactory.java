package az.magusframework.components.lib.observability.core.tracing.span;

import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;

/**
 * <h1>SimpleSpanFactory</h1>
 * <p>
 * The default implementation of {@link SpanFactory} for the Magus observability kernel.
 * This class is responsible for the logical assembly of {@link SimpleSpan} instances,
 * ensuring that Trace IDs are preserved and Span IDs are generated correctly.
 * </p>
 * * <h3>Key Behaviors:</h3>
 * <ul>
 * <li><b>Context Awareness:</b> Automatically detects the active span from {@link SpanHolder}
 * to create nested child segments.</li>
 * <li><b>Timing Initialization:</b> Captures the high-resolution start time using
 * {@code System.nanoTime()} immediately upon instantiation.</li>
 * <li><b>W3C Alignment:</b> Ensures that child spans inherit the {@code TraceID} and
 * {@code Sampled} flag from their parents.</li>
 * </ul>
 */
public final class SimpleSpanFactory implements SpanFactory {

    /**
     * Creates a new child span linked to the current thread's active span.
     * <p>
     * <b>Nesting Logic:</b> This method retrieves the parent context from {@link SpanHolder}.
     * The child will share the parent's {@code TraceID} but receive a fresh {@code SpanID}.
     * The parent's {@code SpanID} becomes the child's {@code parentSpanId}.
     * </p>
     *
     * @param operation The name of the internal unit of work.
     * @param tags      Metadata to attach to the span.
     * @param generator The generator used to create the new unique Span ID.
     * @return A {@link SimpleSpan} in the INTERNAL kind, or {@code null} if no parent exists.
     * @throws IllegalArgumentException if the generator is null.
     */
    @Override
    public Span childSpan(String operation, MetricTags tags, TraceIdGenerator generator) {
        if (generator == null) {
            throw new IllegalArgumentException("generator must not be null");
        }

        Span parent = SpanHolder.current();
        if (parent == null) {
            return null;
        }

        SpanContext pc = parent.context();

        SpanContext child = new SpanContext(
                pc.traceId(),
                generator.newSpanId(),
                pc.spanId(),
                pc.sampled()
        );

        return new SimpleSpan(child, SpanKind.INTERNAL, System.nanoTime());
    }

    /**
     * Creates a root span for an inbound request, typically at the edge of the service.
     * <p>
     * <b>Propagation Logic:</b> If an {@code inboundParent} is provided (extracted from
     * headers), this method "continues" that trace. If null, it "initiates" a brand
     * new trace with a fresh {@code TraceID}.
     * </p>
     *
     * @param name          The name of the inbound request (e.g., HTTP Route).
     * @param tags          Initial tags like HTTP method or URL.
     * @param inboundParent The context extracted from the caller, or null.
     * @param generator     The generator for Trace and Span IDs.
     * @return A {@link SimpleSpan} in the SERVER kind.
     * @throws IllegalArgumentException if the generator is null.
     */
    @Override
    public Span httpRootSpan(String name, MetricTags tags, SpanContext inboundParent, TraceIdGenerator generator) {
        if (generator == null) {
            throw new IllegalArgumentException("generator must not be null");
        }

        final boolean sampled = (inboundParent == null) ? true : inboundParent.sampled();

        SpanContext rootContext;
        if (inboundParent == null) {
            // Initiate a new global trace
            rootContext = new SpanContext(
                    generator.newTraceId(),
                    generator.newSpanId(),
                    null,
                    sampled
            );
        } else {
            // Continue an existing distributed trace
            rootContext = new SpanContext(
                    inboundParent.traceId(),
                    generator.newSpanId(),
                    inboundParent.spanId(),
                    sampled
            );
        }

        return new SimpleSpan(rootContext, SpanKind.SERVER, System.nanoTime());
    }
}