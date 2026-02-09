package az.magusframework.components.lib.observability.core.error;


import az.magusframework.components.lib.observability.core.metrics.Outcome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>ErrorClassifier</h1>
 * <p>
 * This class is the central authority for categorizing application failures.
 * It maps {@link Throwable} instances and HTTP status codes into low-cardinality
 * {@link ErrorInfo} objects used for structured logging and metrics.
 * </p>
 * * <p><b>Performance:</b> Exception names are cached locally to avoid the
 * overhead of reflection on high-throughput execution paths.</p>
 */
public final class ErrorClassifier {

    // --- Error Codes (Constants) ---
    public static final String CODE_AUTH_FAILURE = "HTTP_AUTH_FAILURE";
    public static final String CODE_THROTTLED = "HTTP_THROTTLED";
    public static final String CODE_SERVER_ERROR = "HTTP_5XX";
    public static final String CODE_CLIENT_ERROR = "HTTP_4XX";
    public static final String CODE_UNEXPECTED = "UNEXPECTED_RUNTIME_EXCEPTION";

    private static final Map<Class<? extends Throwable>, String> CLASS_NAME_CACHE = new ConcurrentHashMap<>();

    private ErrorClassifier() {
        // Prevent instantiation of utility class
    }

    /**
     * Categorizes a failure based on response status and exception type.
     * * @param t The observed exception (may be null).
     * @param httpStatus The HTTP response status code (may be null).
     * @return A standardized {@link ErrorInfo} classification.
     */
    public static ErrorInfo classify(Throwable t, Integer httpStatus) {
        if (httpStatus != null) {
            // Security and Rate-Limiting are elevated to TECHNICAL to alert Ops teams.
            if (isSecurityIssue(httpStatus)) {
                return info(ErrorKind.TECHNICAL, CODE_AUTH_FAILURE, t);
            }
            if (httpStatus == 429) {
                return info(ErrorKind.TECHNICAL, CODE_THROTTLED, t);
            }

            if (httpStatus >= 500) {
                return info(ErrorKind.TECHNICAL, CODE_SERVER_ERROR, t);
            }
            if (httpStatus >= 400) {
                return info(ErrorKind.BUSINESS, CODE_CLIENT_ERROR, t);
            }
        }

        return info(ErrorKind.TECHNICAL, CODE_UNEXPECTED, t);
    }

    /**
     * Maps the final state of an operation to a high-level outcome.
     * * @param status The response status code.
     * @param error The thrown exception.
     * @return {@link Outcome#SUCCESS}, {@link Outcome#BUSINESS_ERROR}, or {@link Outcome#TECHNICAL_ERROR}.
     */
    public static Outcome determineOutcome(Integer status, Throwable error) {
        if (error != null || (status != null && status >= 500)) {
            return Outcome.TECHNICAL_ERROR;
        }
        if (status != null && status >= 400) {
            return Outcome.BUSINESS_ERROR;
        }
        return Outcome.SUCCESS;
    }

    /**
     * Resolves the HTTP status class for dashboard slicing (e.g., 2xx, 4xx).
     * * @param status The integer status code.
     * @return A low-cardinality string representation.
     */
    public static String resolveStatusClass(Integer status) {
        if (status == null) return "unknown";
        if (status >= 500) return "5xx";
        if (status >= 400) return "4xx";
        if (status >= 300) return "3xx";
        if (status >= 200) return "2xx";
        return "1xx";
    }

    private static ErrorInfo info(ErrorKind kind, String code, Throwable t) {
        return new ErrorInfo(kind, code, getCachedSimpleName(t));
    }

    private static boolean isSecurityIssue(int status) {
        return status == 401 || status == 403;
    }

    private static String getCachedSimpleName(Throwable t) {
        if (t == null) return null;
        // Reflection is slow; computeIfAbsent ensures we only pay the penalty once per class type.
        return CLASS_NAME_CACHE.computeIfAbsent(t.getClass(), Class::getSimpleName);
    }



    
}