package az.magusframework.components.lib.observability.core.metrics;

/**
 * Standardized result types for an observed operation.
 * <p>
 * These values are used as metric tags (e.g., {@code outcome="SUCCESS"})
 * to enable error-rate analysis and dashboard slicing.
 * </p>
 */
public enum Outcome {
    /**
     * The operation completed normally and met all business requirements.
     */
    SUCCESS,

    /**
     * The operation was interrupted due to a timeout threshold.
     */
    TIMEOUT,

    /**
     * The operation was explicitly canceled (e.g., client disconnected).
     */
    CANCELED,

    /**
     * The operation failed due to a business-level rejection (e.g., 4xx status).
     */
    BUSINESS_ERROR,

    /**
     * The operation failed due to a system or unexpected error (e.g., 5xx status).
     */
    TECHNICAL_ERROR
}