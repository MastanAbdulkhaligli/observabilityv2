package az.magusframework.components.lib.observability.core.tracing;

/**
 * <h1>TraceIdGenerator</h1>
 * <p>
 * Strategy interface for generating globally unique identifiers for distributed tracing.
 * </p>
 * * <p><b>Standard Compliance:</b></p>
 * <ul>
 * <li>Implementations should default to <b>W3C Trace Context</b> compatibility.</li>
 * <li>Trace IDs are typically 128-bit (32 hex characters).</li>
 * <li>Span IDs are typically 64-bit (16 hex characters).</li>
 * </ul>
 *
 * <p>Thread-safety: All implementations MUST be thread-safe.</p>
 */
public interface TraceIdGenerator {

    /**
     * Generates a new, globally unique Trace ID.
     * <p>
     * A Trace ID represents a single end-to-end transaction.
     * </p>
     * * @return A non-null, lowercase hex string representing a 128-bit identifier.
     */
    String newTraceId();

    /**
     * Generates a new Span ID.
     * <p>
     * A Span ID represents a specific operation within a trace.
     * </p>
     * * @return A non-null, lowercase hex string representing a 64-bit identifier.
     */
    String newSpanId();
}