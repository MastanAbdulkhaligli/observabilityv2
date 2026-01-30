package az.magusframework.components.lib.observability.core.tracing;

import az.magusframework.components.lib.observability.core.tracing.span.SpanContext;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class TracePropagation {

    private TracePropagation() {}

    // ===== Header names =====
    public static final String H_TRACEPARENT = "traceparent";
    public static final String H_TRACESTATE  = "tracestate"; // optional (we ignore content)
    public static final String H_BAGGAGE     = "baggage";

    public static final String H_B3_SINGLE   = "b3";
    public static final String H_B3_TRACEID  = "x-b3-traceid";
    public static final String H_B3_SPANID   = "x-b3-spanid";
    public static final String H_B3_SAMPLED  = "x-b3-sampled";
    public static final String H_B3_FLAGS    = "x-b3-flags";
    public static final String H_B3_PARENT   = "x-b3-parentspanid";

    // ===== Result =====
    public record Extracted(SpanContext inboundParent, String baggage) {}

    /**
     * Extract inbound trace context with precedence:
     * 1) W3C traceparent
     * 2) B3 single header "b3"
     * 3) B3 multi headers X-B3-*
     */
    public static Extracted extract(Map<String, String> headers) {
        String baggage = get(headers, H_BAGGAGE).orElse(null);

        // 1) W3C
        Optional<SpanContext> w3c = parseW3C(get(headers, H_TRACEPARENT).orElse(null));
        if (w3c.isPresent()) {
            return new Extracted(w3c.get(), baggage);
        }

        // 2) B3 single
        Optional<SpanContext> b3single = parseB3Single(get(headers, H_B3_SINGLE).orElse(null));
        if (b3single.isPresent()) {
            return new Extracted(b3single.get(), baggage);
        }

        // 3) B3 multi
        Optional<SpanContext> b3multi = parseB3Multi(headers);
        if (b3multi.isPresent()) {
            return new Extracted(b3multi.get(), baggage);
        }

        return new Extracted(null, baggage);
    }

    /**
     * Inject outbound propagation headers.
     * You inject the CURRENT span as the "parent" for downstream.
     */
    public static void injectOutbound(Map<String, String> outHeaders, SpanContext current, String baggage) {
        if (current == null) return;

        // W3C
        outHeaders.put(H_TRACEPARENT, formatTraceparent(current.traceId(), current.spanId(), current.sampled()));

        // B3 multi (preferred)
        // NOTE: keeping your original output keys exactly as-is (capitalized X-B3-*)
        outHeaders.put("X-B3-TraceId", current.traceId());
        outHeaders.put("X-B3-SpanId",  current.spanId());
        outHeaders.put("X-B3-Sampled", current.sampled() ? "1" : "0");

        // Optional baggage passthrough
        if (baggage != null && !baggage.isBlank()) {
            outHeaders.put(H_BAGGAGE, baggage);
        }
    }

    // =====================================================================
    // Parsing
    // =====================================================================

    static Optional<SpanContext> parseW3C(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) return Optional.empty();
        String tp = traceparent.trim();

        String[] parts = tp.split("-");
        if (parts.length != 4) return Optional.empty();

        String version = lower(parts[0]);
        String traceId = lower(parts[1]);
        String spanId  = lower(parts[2]);
        String flags   = lower(parts[3]);

        if (!isHex(version, 2)) return Optional.empty();
        if (!isHex(traceId, 32) || isAllZeros(traceId)) return Optional.empty();
        if (!isHex(spanId, 16) || isAllZeros(spanId)) return Optional.empty();
        if (!isHex(flags, 2)) return Optional.empty();

        boolean sampled = (Integer.parseInt(flags, 16) & 0x01) == 1;

        // Same semantic as before: inbound parent from headers, parentSpanId = null
        return Optional.of(new SpanContext(traceId, spanId, null, sampled));
    }

    static Optional<SpanContext> parseB3Single(String b3) {
        if (b3 == null || b3.isBlank()) return Optional.empty();
        String v = b3.trim();

        // special cases "0" or "1" or "d" (debug only)
        if ("0".equals(v) || "1".equals(v) || "d".equalsIgnoreCase(v)) {
            return Optional.empty();
        }

        String[] parts = v.split("-");
        if (parts.length < 2) return Optional.empty();

        String traceIdRaw = lower(parts[0]);
        String spanId     = lower(parts[1]);

        String sampledStr = (parts.length >= 3) ? parts[2] : null;
        String parentSpan = (parts.length >= 4) ? lower(parts[3]) : null;

        String traceId = normalizeB3TraceId(traceIdRaw);
        if (traceId == null) return Optional.empty();

        if (!isHex(spanId, 16) || isAllZeros(spanId)) return Optional.empty();

        Boolean sampled = parseB3Sampled(sampledStr);
        boolean sampledBool = sampled != null ? sampled : true;

        return Optional.of(new SpanContext(traceId, spanId, parentSpan, sampledBool));
    }

    static Optional<SpanContext> parseB3Multi(Map<String, String> headers) {
        String traceIdRaw = get(headers, H_B3_TRACEID).orElse(null);
        String spanId     = get(headers, H_B3_SPANID).orElse(null);

        if (traceIdRaw == null || spanId == null) return Optional.empty();

        String traceId = normalizeB3TraceId(lower(traceIdRaw));
        if (traceId == null) return Optional.empty();

        String s = lower(spanId);
        if (!isHex(s, 16) || isAllZeros(s)) return Optional.empty();

        String flags = get(headers, H_B3_FLAGS).orElse(null);
        if ("1".equals(flags)) {
            return Optional.of(new SpanContext(traceId, s, null, true));
        }

        String sampledStr = get(headers, H_B3_SAMPLED).orElse(null);
        Boolean sampled = parseB3Sampled(sampledStr);
        boolean sampledBool = sampled != null ? sampled : true;

        String parentSpan = get(headers, H_B3_PARENT).orElse(null);
        parentSpan = (parentSpan != null) ? lower(parentSpan) : null;

        return Optional.of(new SpanContext(traceId, s, parentSpan, sampledBool));
    }

    // =====================================================================
    // Helpers (unchanged)
    // =====================================================================

    public static String normalizeB3TraceId(String traceIdRaw) {
        if (traceIdRaw == null) return null;
        String t = lower(traceIdRaw);

        if (isHex(t, 16)) {
            if (isAllZeros(t)) return null;
            return "0000000000000000" + t;
        }
        if (isHex(t, 32)) {
            if (isAllZeros(t)) return null;
            return t;
        }
        return null;
    }

    static String formatTraceparent(String traceId32, String spanId16, boolean sampled) {
        return "00-" + traceId32 + "-" + spanId16 + "-" + (sampled ? "01" : "00");
    }

    static Boolean parseB3Sampled(String sampled) {
        if (sampled == null) return null;
        String s = sampled.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "1", "true" -> true;
            case "0", "false" -> false;
            case "d" -> true;
            default -> null;
        };
    }

    static Optional<String> get(Map<String, String> headers, String lowerKey) {
        if (headers == null) return Optional.empty();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(lowerKey)) {
                return Optional.ofNullable(e.getValue());
            }
        }
        return Optional.empty();
    }

    static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    static boolean isHex(String s, int len) {
        if (s == null || s.length() != len) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!ok) return false;
        }
        return true;
    }

    static boolean isAllZeros(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') return false;
        }
        return true;
    }
}
