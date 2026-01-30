package az.magusframework.components.lib.observability.core.tracing.span;

import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;

/**
 * <h1>SpanFactory</h1>
 * <p>
 * The primary entry point for the tracing subsystem. This interface defines the
 * contract for generating {@link Span} instances across different execution contexts.
 * </p>
 * * <p><b>Core Responsibilities:</b></p>
 * <ul>
 * <li><b>Context Linking:</b> Automatically linking new spans to the active parent in the {@link SpanHolder}.</li>
 * <li><b>Identity Generation:</b> Coordinating with a {@link TraceIdGenerator} to assign globally unique IDs.</li>
 * <li><b>Protocol Translation:</b> Reconstructing trace segments from external headers (W3C/B3).</li>
 * </ul>
 * * <p><b>Design Policy:</b> Spans returned by this factory are initialized but <b>not started</b>.
 * The caller is responsible for invoking {@code span.start()} to capture the monotonic start time.</p>
 *
 * [Image of factory pattern diagram showing the creation of root and child spans from a parent context]
 */
public interface SpanFactory {

    /**
     * Creates a child span for an internal operation within the current process.
     * <p>
     * This method automatically looks up the currently active span in the
     * {@link SpanHolder} to establish a parent-child relationship. If no active
     * span exists, the implementation should decide whether to return {@code null}
     * or promote this to a new root span (Framework preference: promote to root).
     * </p>
     *
     * @param operation The logical name of the operation (e.g., "db.query", "service.validate").
     * @param tags      Initial set of metadata/attributes to attach to the span.
     * @param generator The ID generator to use for creating the new Span ID.
     * @return A new, unstarted {@link Span} linked to the current thread's context.
     */
    Span childSpan(String operation, MetricTags tags, TraceIdGenerator generator);

    /**
     * Creates a root span at the entry point of the system (typically a Servlet Filter).
     * <p>
     * If an {@code inboundParent} is provided, this span is linked to that remote
     * context, effectively continuing a distributed trace started in another service.
     * If {@code inboundParent} is null, a brand-new Trace ID is generated.
     * </p>
     *
     * @param name          The name of the inbound request or operation (e.g., "GET /api/users").
     * @param tags          Initial attributes (e.g., http.method, user.agent).
     * @param inboundParent The extracted identity from remote headers, or null if this is a fresh trace.
     * @param generator     The ID generator to use for new Trace/Span IDs.
     * @return A new, unstarted {@link Span} initialized as a SERVER or entry-point span.
     */
    Span httpRootSpan(String name, MetricTags tags, SpanContext inboundParent, TraceIdGenerator generator);

}