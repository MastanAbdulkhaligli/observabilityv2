package az.magusframework.components.lib.observability.core.error;

/**
 * A snapshot of a classified error, used for structured logging and span metadata.
 * * @param kind          The classification of the error (BUSINESS or TECHNICAL).
 * @param code          A stable, low-cardinality error code (e.g., "HTTP_5XX", "AUTH_FAILURE").
 * Used for metric grouping and dashboard filters.
 * @param exceptionName The simple name or FQCN of the underlying exception for debugging.
 */
public record ErrorInfo(
        ErrorKind kind,
        String code,
        String exceptionName
) {
    /**
     * Compact constructor to ensure the record is never created in an invalid state.
     */
    public ErrorInfo {
        if (kind == null) kind = ErrorKind.TECHNICAL;
        if (code == null || code.isBlank()) code = "UNKNOWN_ERROR";
    }
}