package az.magusframework.components.lib.observability.core.tracing;

import az.magusframework.components.lib.observability.core.tracing.span.SpanContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TracePropagationTest {

    // ---------- Helpers ----------
    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    private static String validTraceId32() {
        return "4bf92f3577b34da6a3ce929d0e0e4736";
    }

    private static String validSpanId16() {
        return "00f067aa0ba902b7";
    }

    private static String validParentSpanId16() {
        return "1111111111111111";
    }

    // ---------- Tests: extract precedence & baggage ----------

    @Test
    void extract_prefersW3cOverB3SingleAndB3Multi() {
        Map<String, String> h = new HashMap<>();

        h.put("traceparent", "00-" + validTraceId32() + "-" + validSpanId16() + "-01");
        h.put("b3", validTraceId32() + "-" + validSpanId16() + "-0-" + validParentSpanId16());
        h.put("x-b3-traceid", validTraceId32());
        h.put("x-b3-spanid", "2222222222222222");
        h.put("x-b3-sampled", "0");
        h.put("baggage", "k1=v1");

        TracePropagation.Extracted ex = TracePropagation.extract(h);

        assertNotNull(ex.inboundParent());
        assertEquals(validTraceId32(), ex.inboundParent().traceId());
        assertEquals(validSpanId16(), ex.inboundParent().spanId());
        assertNull(ex.inboundParent().parentSpanId());
        assertTrue(ex.inboundParent().sampled());
        assertEquals("k1=v1", ex.baggage());
    }

    @Test
    void extract_usesB3SingleWhenNoW3c() {
        Map<String, String> h = new HashMap<>();

        String traceId16 = "463ac35c9f6413ad";
        h.put("b3", traceId16 + "-" + validSpanId16() + "-1-" + validParentSpanId16());
        h.put("baggage", "k=v");

        TracePropagation.Extracted ex = TracePropagation.extract(h);

        assertNotNull(ex.inboundParent());
        assertEquals("0000000000000000" + traceId16, ex.inboundParent().traceId());
        assertEquals(validSpanId16(), ex.inboundParent().spanId());
        assertEquals(validParentSpanId16(), ex.inboundParent().parentSpanId());
        assertTrue(ex.inboundParent().sampled());
        assertEquals("k=v", ex.baggage());
    }

    @Test
    void extract_usesB3MultiWhenNoW3cOrB3Single() {
        Map<String, String> h = new HashMap<>();

        h.put("x-b3-traceid", validTraceId32());
        h.put("x-b3-spanid", validSpanId16());
        h.put("x-b3-sampled", "0");
        h.put("x-b3-parentspanid", validParentSpanId16());

        TracePropagation.Extracted ex = TracePropagation.extract(h);

        assertNotNull(ex.inboundParent());
        assertEquals(validTraceId32(), ex.inboundParent().traceId());
        assertEquals(validSpanId16(), ex.inboundParent().spanId());
        assertEquals(validParentSpanId16(), ex.inboundParent().parentSpanId());
        assertFalse(ex.inboundParent().sampled());
    }

    @Test
    void extract_headerLookupIsCaseInsensitive() {
        Map<String, String> h = new HashMap<>();

        h.put("TraceParent", "00-" + validTraceId32() + "-" + validSpanId16() + "-00");
        h.put("Baggage", "userId=7");

        TracePropagation.Extracted ex = TracePropagation.extract(h);

        assertNotNull(ex.inboundParent());
        assertEquals(validTraceId32(), ex.inboundParent().traceId());
        assertEquals(validSpanId16(), ex.inboundParent().spanId());
        assertFalse(ex.inboundParent().sampled());
        assertEquals("userId=7", ex.baggage());
    }

    // ---------- W3C edge cases ----------

    @Test
    void extract_w3cInvalid_traceIdAllZeros_fallsBackToOthers() {
        Map<String, String> h = new HashMap<>();

        h.put("traceparent", "00-" + repeat('0', 32) + "-" + validSpanId16() + "-01");
        h.put("x-b3-traceid", validTraceId32());
        h.put("x-b3-spanid", validSpanId16());
        h.put("x-b3-sampled", "1");

        TracePropagation.Extracted ex = TracePropagation.extract(h);

        assertNotNull(ex.inboundParent());
        assertEquals(validTraceId32(), ex.inboundParent().traceId());
    }

    @Test
    void extract_w3cSampledFlagIsRespected() {
        Map<String, String> h = new HashMap<>();
        h.put("traceparent", "00-" + validTraceId32() + "-" + validSpanId16() + "-00");

        TracePropagation.Extracted ex = TracePropagation.extract(h);

        assertNotNull(ex.inboundParent());
        assertFalse(ex.inboundParent().sampled());
    }

    // ---------- B3 flags precedence ----------

    @Test
    void extract_b3Multi_flags1ForcesSampledTrue() {
        Map<String, String> h = new HashMap<>();

        h.put("x-b3-traceid", validTraceId32());
        h.put("x-b3-spanid", validSpanId16());
        h.put("x-b3-sampled", "0");
        h.put("x-b3-flags", "1");

        TracePropagation.Extracted ex = TracePropagation.extract(h);

        assertNotNull(ex.inboundParent());
        assertTrue(ex.inboundParent().sampled());
    }

    // ---------- normalizeB3TraceId ----------

    @Test
    void normalizeB3TraceId_pads16To32_andRejectsInvalid() {
        String traceId16 = "463ac35c9f6413ad";

        assertEquals("0000000000000000" + traceId16,
                TracePropagation.normalizeB3TraceId(traceId16));

        assertNull(TracePropagation.normalizeB3TraceId(repeat('0', 16)));
        assertNull(TracePropagation.normalizeB3TraceId("not-hex"));
        assertNull(TracePropagation.normalizeB3TraceId("123"));

        assertEquals(validTraceId32(),
                TracePropagation.normalizeB3TraceId(validTraceId32().toUpperCase()));
    }

    // ---------- injectOutbound ----------

    @Test
    void injectOutbound_writesW3cAndB3MultiAndBaggage() {
        SpanContext ctx = new SpanContext(validTraceId32(), validSpanId16(), null, true);

        Map<String, String> out = new HashMap<>();
        TracePropagation.injectOutbound(out, ctx, "k=v");

        assertEquals("00-" + validTraceId32() + "-" + validSpanId16() + "-01",
                out.get("traceparent"));

        assertEquals(validTraceId32(), out.get("X-B3-TraceId"));
        assertEquals(validSpanId16(), out.get("X-B3-SpanId"));
        assertEquals("1", out.get("X-B3-Sampled"));
        assertEquals("k=v", out.get("baggage"));
    }

    @Test
    void injectOutbound_doesNothingWhenCurrentIsNull() {
        Map<String, String> out = new HashMap<>();
        TracePropagation.injectOutbound(out, null, "k=v");
        assertTrue(out.isEmpty());
    }

    @Test
    void injectOutbound_doesNotWriteBlankBaggage() {
        SpanContext ctx = new SpanContext(validTraceId32(), validSpanId16(), null, true);

        Map<String, String> out = new HashMap<>();
        TracePropagation.injectOutbound(out, ctx, "   ");

        assertNull(out.get("baggage"));
    }
}
