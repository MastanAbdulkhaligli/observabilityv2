package az.magusframework.components.lib.observability.core.tracing.span;

/**
 * <h1>SpanStatus</h1>
 * <p>
 * Defines the canonical result of a span's operation, following the W3C and
 * OpenTelemetry specification for trace status.
 * </p>
 *
 * <p><b>Status Mapping:</b></p>
 * <ul>
 * <li><b>UNSET:</b> The default state. Indicates the operation finished without
 * explicit success or failure being recorded.</li>
 * <li><b>OK:</b> Explicitly marked as successful. In most backends, this is
 * treated as a "healthy" span.</li>
 * <li><b>ERROR:</b> Indicates a failure occurred. This state typically triggers
 * error-rate alerts and visual highlighting in tracing UI.</li>
 * </ul>
 */
public enum SpanStatus {

    /**
     * The default status. Represents a span that has not been explicitly
     * marked as successful or failed.
     */
    UNSET,

    /**
     * Indicates that the operation was successful.
     */
    OK,

    /**
     * Indicates that the operation failed.
     */
    ERROR
}