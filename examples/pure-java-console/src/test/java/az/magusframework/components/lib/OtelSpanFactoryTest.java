package az.magusframework.components.lib;

import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.span.*;
import az.magusframework.components.lib.observability.otel.OtelSpanFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class OtelSpanFactoryTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private OpenTelemetry openTelemetry;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    @AfterEach
    void tearDown() {
        // make sure the pipeline is flushed and context is clean
        tracerProvider.shutdown();
        exporter.reset();
        SpanHolder.clear(); // IMPORTANT for test isolation (if you have it; otherwise remove)
        MDC.clear();
    }

    @Test
    void nestedSpans_parentChildValidation_viaSpanHolder_andCustomAttributes() {
        // Valid W3C IDs (your SpanContext validates these strictly)
        String rootTraceId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 32 lower-hex
        String rootSpanId  = "1111111111111111";                 // 16 lower-hex
        String childSpanId = "2222222222222222";                 // 16 lower-hex

        TraceIdGenerator rootGen  = new FixedIdsGenerator(rootTraceId, rootSpanId);
        TraceIdGenerator childGen = new FixedIdsGenerator(rootTraceId, childSpanId);

        OtelSpanFactory factory = new OtelSpanFactory(
                // idGenerator is used for spanId in createOtelSpan
                new FixedSpanIdGeneratorSequence(rootSpanId, childSpanId),
                openTelemetry
        );

        // Root span (SERVER)
        Span root = factory.httpRootSpan(
                "GET /demo",
                null,
                null,
                rootGen
        );
        assertNotNull(root);
        assertEquals(SpanKind.SERVER, root.kind());
        assertEquals("GET /demo", root.name());
        assertEquals(rootTraceId, root.context().traceId());
        assertEquals(rootSpanId, root.context().spanId());
        assertNull(root.context().parentSpanId());

        // Nested child span (INTERNAL)
        Span child = null;
        try (SpanScope rootScope = root.activate()) {
            assertSame(root, SpanHolder.current(), "Root span must be current while activated");

            child = factory.childSpan("repo.select", null, childGen);
            assertNotNull(child, "childSpan must return a span when parent is active");
            assertEquals(SpanKind.INTERNAL, child.kind());
            assertEquals(rootTraceId, child.context().traceId(), "Child must inherit traceId");
            assertEquals(childSpanId, child.context().spanId());
            assertEquals(rootSpanId, child.context().parentSpanId(), "Child must point to root spanId as parent");

            try (SpanScope childScope = child.activate()) {
                assertSame(child, SpanHolder.current(), "Child must be current inside child scope");
            }
        } finally {
            child.end();
            root.end();
        }

        // Export verification (Jaeger-ready shape: names, status, attributes)
        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(2, spans.size(), "Expect exactly root + child exported");

        SpanData rootExport = findByName(spans, "GET /demo")
                .orElseThrow(() -> new AssertionError("Missing exported root span"));
        SpanData childExport = findByName(spans, "repo.select")
                .orElseThrow(() -> new AssertionError("Missing exported child span"));

        // Your factory sets custom IDs as attributes.
        assertEquals(rootTraceId, stringAttr(rootExport, "custom.trace_id"));
        assertEquals(rootSpanId,  stringAttr(rootExport, "custom.span_id"));
        assertFalse(rootExport.getAttributes().asMap().containsKey(AttributeKey.stringKey("custom.parent_span_id")));

        assertEquals(rootTraceId, stringAttr(childExport, "custom.trace_id"));
        assertEquals(childSpanId, stringAttr(childExport, "custom.span_id"));
        assertEquals(rootSpanId,  stringAttr(childExport, "custom.parent_span_id"));
    }

    @Test
    void errorRecording_recordsStatusError_errorAttributes_andExceptionEvent() {
        String traceId     = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String rootSpanId  = "aaaaaaaaaaaaaaaa";
        String childSpanId = "cccccccccccccccc";

        OtelSpanFactory factory = new OtelSpanFactory(
                new FixedSpanIdGeneratorSequence(rootSpanId, childSpanId),
                openTelemetry
        );

        TraceIdGenerator gen = new FixedIdsGenerator(traceId, rootSpanId);

        Span root = factory.httpRootSpan("POST /payments", null, null, gen);
        assertNotNull(root);

        Span child = null;
        try (SpanScope rootScope = root.activate()) {
            child = factory.childSpan("db.insert.payment", null, gen);
            assertNotNull(child);

            try (SpanScope childScope = child.activate()) {
                RuntimeException boom = new RuntimeException("db timeout");
                child.recordException(boom);
                child.recordError(ErrorKind.TECHNICAL, "DB_TIMEOUT");
                child.setStatus(SpanStatus.ERROR);
            }
        } finally {
            // end child first, then root (standard)
            // (if child is null, end() would NPE; keep it simple)
            //noinspection ConstantConditions
            child.end();
            root.end();
        }

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(2, spans.size());

        SpanData childExport = findByName(spans, "db.insert.payment")
                .orElseThrow(() -> new AssertionError("Missing child span"));

        // Jaeger-ready error semantics:
        assertEquals(StatusCode.ERROR, childExport.getStatus().getStatusCode());

        // Your adapter writes these:
        assertEquals("TECHNICAL", stringAttr(childExport, "error.kind"));
        assertEquals("DB_TIMEOUT", stringAttr(childExport, "error.code"));

        // Exception recorded -> should show as an "exception" event in OTel data
        boolean hasExceptionEvent = childExport.getEvents().stream()
                .map(EventData::getName)
                .anyMatch("exception"::equals);
        assertTrue(hasExceptionEvent, "Expected an exception event on the span");
    }

    @Test
    void mdcPropagation_pattern_isCorrect_inNestedScopes_andRestoresPreviousValues() {
        // Your OtelSpanAdapter does NOT set MDC by itself.
        // A staff-grade test verifies the *pattern* used with your SpanContext,
        // and that MDC is restored correctly in nested scopes.

        String traceId     = "cccccccccccccccccccccccccccccccc";
        String rootSpanId  = "1111111111111111";
        String childSpanId = "2222222222222222";

        OtelSpanFactory factory = new OtelSpanFactory(
                new FixedSpanIdGeneratorSequence(rootSpanId, childSpanId),
                openTelemetry
        );

        TraceIdGenerator gen = new FixedIdsGenerator(traceId, rootSpanId);

        // Pre-existing MDC should be restored after spans close
        MDC.put("trace.id", "preexisting-trace");
        MDC.put("service.name", "preexisting-service");

        Span root = factory.httpRootSpan("GET /mdc", null, null, gen);
        Span child;

        try (SpanScope rootScope = root.activate();
             MdcScope ignored1 = MdcScope.fromSpanContext(root.context())) {

            assertEquals(traceId, MDC.get("trace.id"));
            assertEquals(rootSpanId, MDC.get("trace.span_id"));

            child = factory.childSpan("svc.inner", null, gen);
            assertNotNull(child);

            try (SpanScope childScope = child.activate();
                 MdcScope ignored2 = MdcScope.fromSpanContext(child.context())) {

                assertEquals(traceId, MDC.get("trace.id"));
                assertEquals(childSpanId, MDC.get("trace.span_id"));
                assertEquals(rootSpanId, MDC.get("trace.parent_id"));
            } finally {
                child.end();
            }

            // After child closes, root MDC must be restored (still root)
            assertEquals(traceId, MDC.get("trace.id"));
            assertEquals(rootSpanId, MDC.get("trace.span_id"));
            assertNull(MDC.get("trace.parent_id"));
        } finally {
            root.end();
        }

        // After root closes, original MDC must be back
        assertEquals("preexisting-trace", MDC.get("trace.id"));
        assertEquals("preexisting-service", MDC.get("service.name"));
        assertNull(MDC.get("trace.span_id"));
        assertNull(MDC.get("trace.parent_id"));
    }

    // ------------------------------
    // Helpers (test-only)
    // ------------------------------

    private static Optional<SpanData> findByName(List<SpanData> spans, String name) {
        return spans.stream().filter(s -> name.equals(s.getName())).findFirst();
    }

    private static String stringAttr(SpanData span, String key) {
        var k = AttributeKey.stringKey(key);
        String v = span.getAttributes().get(k);
        assertNotNull(v, "Missing attribute: " + key);
        return v;
    }

    /**
     * Fixed traceId + spanId generator (for deterministic tests).
     * NOTE: Your factory does NOT use generator.newSpanId() for spanId (it uses idGenerator field),
     * but tests often pass this generator anyway for uniformity.
     */
    private static final class FixedIdsGenerator implements TraceIdGenerator {
        private final String traceId32;
        private final String spanId16;

        private FixedIdsGenerator(String traceId32, String spanId16) {
            this.traceId32 = traceId32;
            this.spanId16 = spanId16;
        }

        @Override public String newTraceId() { return traceId32; }
        @Override public String newSpanId() { return spanId16; }
    }

    /**
     * Your OtelSpanFactory uses its constructor-injected idGenerator.newSpanId()
     * for *every* span creation. This provides a deterministic sequence.
     */
    private static final class FixedSpanIdGeneratorSequence implements TraceIdGenerator {
        private final String[] ids;
        private int idx = 0;

        private FixedSpanIdGeneratorSequence(String... ids) {
            this.ids = ids;
        }

        @Override
        public String newTraceId() {
            // unused by your factory in createOtelSpan for traceId (it takes a param)
            return "dddddddddddddddddddddddddddddddd";
        }

        @Override
        public String newSpanId() {
            if (idx >= ids.length) {
                throw new IllegalStateException("No more spanIds in sequence");
            }
            return ids[idx++];
        }
    }

    /**
     * Test-only MDC scope that simulates what your slf4j module (TraceMdcScope) should do:
     * set MDC from SpanContext, then restore previous MDC on close.
     */
    private static final class MdcScope implements AutoCloseable {
        private final String prevTraceId;
        private final String prevSpanId;
        private final String prevParentId;

        private MdcScope(String prevTraceId, String prevSpanId, String prevParentId) {
            this.prevTraceId = prevTraceId;
            this.prevSpanId = prevSpanId;
            this.prevParentId = prevParentId;
        }

        static MdcScope fromSpanContext(SpanContext ctx) {
            String prevTraceId = MDC.get("trace.id");
            String prevSpanId  = MDC.get("trace.span_id");
            String prevParent  = MDC.get("trace.parent_id");

            MDC.put("trace.id", ctx.traceId());
            MDC.put("trace.span_id", ctx.spanId());
            if (ctx.parentSpanId() != null) {
                MDC.put("trace.parent_id", ctx.parentSpanId());
            } else {
                MDC.remove("trace.parent_id");
            }
            // sampled flag example (optional)
            MDC.put("trace.sampled", String.valueOf(ctx.sampled()));

            return new MdcScope(prevTraceId, prevSpanId, prevParent);
        }

        @Override
        public void close() {
            putOrRemove("trace.id", prevTraceId);
            putOrRemove("trace.span_id", prevSpanId);
            putOrRemove("trace.parent_id", prevParentId);
            MDC.remove("trace.sampled");
        }

        private static void putOrRemove(String k, String v) {
            if (v == null) MDC.remove(k);
            else MDC.put(k, v);
        }
    }
}
