package az.magusframework.components.lib.observability.core.tracing.span;

import java.util.Locale;

/**
 * <h1>SpanContext</h1>
 * <p>
 * An immutable value object representing the globally unique identity of a span.
 * This class serves as the "Passport" for a trace, containing the identifiers required
 * to correlate operations across distributed systems.
 * </p>
 *
 * <p><b>Compliance:</b> This implementation follows the <b>W3C Trace Context</b>
 * specification for trace and span identifier formats.</p>
 *
 * <h3>Key Components:</h3>
 * <ul>
 * <li><b>Trace ID:</b> A 32-character hex string identifying the entire end-to-end transaction.</li>
 * <li><b>Span ID:</b> A 16-character hex string identifying this specific unit of work.</li>
 * <li><b>Parent Span ID:</b> A 16-character hex string identifying the caller (nullable).</li>
 * <li><b>Sampled:</b> A flag indicating whether this trace should be recorded by the backend.</li>
 * </ul>
 */
public final class SpanContext {

    /** 32 lowercase hex characters (128-bit) */
    private final String traceId;

    /** 16 lowercase hex characters (64-bit) */
    private final String spanId;

    /** 16 lowercase hex characters (64-bit) or null for root spans */
    private final String parentSpanId;

    /** True if the trace is selected for recording/sampling */
    private final boolean sampled;

    /**
     * Constructs a validated SpanContext.
     * * @param traceId      The 32-hex trace identifier.
     * @param spanId       The 16-hex span identifier.
     * @param parentSpanId The identifier of the parent span (optional).
     * @param sampled      The sampling decision.
     * @throws IllegalArgumentException if IDs do not meet W3C hex/length requirements.
     */
    public SpanContext(String traceId, String spanId, String parentSpanId, boolean sampled) {
        this.traceId = validateTraceId(traceId);
        this.spanId = validateSpanId(spanId);
        this.parentSpanId = (parentSpanId == null || parentSpanId.isEmpty())
                ? null
                : validateSpanId(parentSpanId);
        this.sampled = sampled;
    }

    /** @return The canonical 32-character lowercase hex Trace ID. */
    public String traceId() {
        return traceId;
    }

    /** @return The canonical 16-character lowercase hex Span ID. */
    public String spanId() {
        return spanId;
    }

    /** @return The parent Span ID, or null if this is a root context. */
    public String parentSpanId() {
        return parentSpanId;
    }

    /** @return True if this context is marked for sampling. */
    public boolean sampled() {
        return sampled;
    }

    // ------------------------------------------------------------
    // Validation (W3C compatible constraints)
    // ------------------------------------------------------------

    /**
     * Validates TraceID: 32 chars, hex-only, and not 'all-zeros'.
     */
    private static String validateTraceId(String traceId) {
        String t = lower(trimToNull(traceId));
        if (t == null || !isHex(t, 32) || isAllZeros(t)) {
            throw new IllegalArgumentException("traceId must be 32 lowercase hex chars and not all zeros");
        }
        return t;
    }

    /**
     * Validates SpanID: 16 chars, hex-only, and not 'all-zeros'.
     */
    private static String validateSpanId(String spanId) {
        String s = lower(trimToNull(spanId));
        if (s == null || !isHex(s, 16) || isAllZeros(s)) {
            throw new IllegalArgumentException("spanId must be 16 lowercase hex chars and not all zeros");
        }
        return s;
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    /**
     * Performs a strict hex-character check for the specified length.
     */
    private static boolean isHex(String s, int len) {
        if (s == null || s.length() != len) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!ok) return false;
        }
        return true;
    }

    /**
     * Checks for the 'invalid' state of all zeros, as per W3C specification.
     */
    private static boolean isAllZeros(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') return false;
        }
        return true;
    }
}