package az.magusframework.components.lib.observability.otel;


import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.tags.MetricTag;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.span.*;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;


import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class OtelSpanFactoryTest {

    private InMemorySpanExporter exporter;
    private OpenTelemetry openTelemetry;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();

        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    @AfterEach
    void tearDown() {
        exporter.reset();
        // Avoid leakage across tests
        while (SpanHolder.current() != null) {
            SpanHolder.pop();
        }
    }

    // ----------------------------
    // Constructor validation
    // ----------------------------

    @Test
    void constructor_nullTraceIdGenerator_throws() {
        assertThatThrownBy(() -> new OtelSpanFactory(null, openTelemetry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TraceIdGenerator");
    }

    @Test
    void constructor_nullOpenTelemetry_throws() {
        assertThatThrownBy(() -> new OtelSpanFactory(new FixedIds(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OpenTelemetry");
    }

    // ----------------------------
    // httpRootSpan
    // ----------------------------

    @Test
    void httpRootSpan_withoutInboundParent_usesGeneratorTraceId_andNoParentAttribute() {
        FixedIds ids = new FixedIds(
                // 32 lowercase hex
                "4bf92f3577b34da6a3ce929d0e0e4736",
                // 16 lowercase hex (span ids)
                "00f067aa0ba902b7"
        );

        OtelSpanFactory factory = new OtelSpanFactory(ids, openTelemetry);

        MetricTags tags = MetricTags.of(List.of(new MetricTag("service.env", "test")));
        Span root = factory.httpRootSpan("GET /ping", tags, null, ids);

        assertThat(root).isNotNull();
        assertThat(root.kind()).isEqualTo(SpanKind.SERVER);
        assertThat(root.context().traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(root.context().parentSpanId()).isNull();

        root.end();

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);

        SpanData sd = spans.get(0);

        assertThat(sd.getAttributes().get(AttributeKey.stringKey("custom.trace_id")))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(sd.getAttributes().get(AttributeKey.stringKey("custom.span_id")))
                .isEqualTo("00f067aa0ba902b7");
        assertThat(sd.getAttributes().get(AttributeKey.stringKey("custom.parent_span_id")))
                .isNull();

        assertThat(sd.getAttributes().get(AttributeKey.stringKey("service.env")))
                .isEqualTo("test");
    }

    @Test
    void httpRootSpan_withInboundParent_usesInboundTraceId_andSetsParentAttribute() {
        FixedIds ids = new FixedIds(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // generator trace (won't be used)
                "1111111111111111"                  // new span id
        );

        OtelSpanFactory factory = new OtelSpanFactory(ids, openTelemetry);

        SpanContext inbound = new SpanContext(
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", // 32 lowercase hex
                "2222222222222222",                 // 16 lowercase hex
                null,
                true
        );

        Span root = factory.httpRootSpan("GET /users", null, inbound, ids);

        assertThat(root).isNotNull();
        assertThat(root.context().traceId()).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertThat(root.context().parentSpanId()).isEqualTo("2222222222222222");

        root.end();

        SpanData sd = exporter.getFinishedSpanItems().get(0);

        assertThat(sd.getAttributes().get(AttributeKey.stringKey("custom.trace_id")))
                .isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        // NOTE: Above line has a typo fix below (keep reading)
    }

    @Test
    void httpRootSpan_blankName_usesUnnamedOperation_inOtelSpanName() {
        FixedIds ids = new FixedIds(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7"
        );
        OtelSpanFactory factory = new OtelSpanFactory(ids, openTelemetry);

        Span root = factory.httpRootSpan("   ", null, null, ids);
        root.end();

        SpanData sd = exporter.getFinishedSpanItems().get(0);
        assertThat(sd.getName()).isEqualTo("unnamed-operation");
    }

    // ----------------------------
    // childSpan
    // ----------------------------

    @Test
    void childSpan_whenNoParentInSpanHolder_returnsNull() {
        OtelSpanFactory factory = new OtelSpanFactory(new FixedIds(), openTelemetry);

        Span child = factory.childSpan("db.query", null, new FixedIds());
        assertThat(child).isNull();
    }

    @Test
    void childSpan_whenParentActive_inheritsTraceId_andSetsCustomParentSpanId() {
        FixedIds ids = new FixedIds(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7", // parent span
                "0000000000000001"  // child span (must not be all zeros)
        );

        OtelSpanFactory factory = new OtelSpanFactory(ids, openTelemetry);

        Span parent = factory.httpRootSpan("GET /root", null, null, ids);

        try (SpanScope scope = parent.activate()) {
            assertThat(SpanHolder.current()).isSameAs(parent);

            Span child = factory.childSpan("service.work", null, ids);
            assertThat(child).isNotNull();
            assertThat(child.kind()).isEqualTo(SpanKind.INTERNAL);

            assertThat(child.context().traceId()).isEqualTo(parent.context().traceId());
            assertThat(child.context().parentSpanId()).isEqualTo(parent.context().spanId());

            child.end();
        } finally {
            parent.end();
        }

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans).hasSize(2);

        SpanData childSd = spans.stream()
                .filter(s -> s.getName().equals("service.work"))
                .findFirst()
                .orElseThrow();

        assertThat(childSd.getAttributes().get(AttributeKey.stringKey("custom.trace_id")))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(childSd.getAttributes().get(AttributeKey.stringKey("custom.span_id")))
                .isEqualTo("0000000000000001");
        assertThat(childSd.getAttributes().get(AttributeKey.stringKey("custom.parent_span_id")))
                .isEqualTo("00f067aa0ba902b7");
    }

    // ----------------------------
    // OtelSpanAdapter behavior
    // ----------------------------

    @Test
    void adapter_recordError_setsStatusAndAttributes() {
        FixedIds ids = new FixedIds(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7"
        );
        OtelSpanFactory factory = new OtelSpanFactory(ids, openTelemetry);

        Span s = factory.httpRootSpan("GET /fail", null, null, ids);

        s.recordError(ErrorKind.TECHNICAL, "E-500");
        assertThat(s.status()).isEqualTo(SpanStatus.ERROR);
        assertThat(s.errorKind()).isEqualTo(ErrorKind.TECHNICAL);
        assertThat(s.errorCode()).isEqualTo("E-500");

        s.end();

        SpanData sd = exporter.getFinishedSpanItems().get(0);
        assertThat(sd.getAttributes().get(AttributeKey.stringKey("error.kind"))).isEqualTo("TECHNICAL");
        assertThat(sd.getAttributes().get(AttributeKey.stringKey("error.code"))).isEqualTo("E-500");
    }

    @Test
    void adapter_activate_pushesToSpanHolder_andPopsOnClose() {
        FixedIds ids = new FixedIds(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7"
        );
        OtelSpanFactory factory = new OtelSpanFactory(ids, openTelemetry);

        Span s = factory.httpRootSpan("GET /scope", null, null, ids);

        assertThat(SpanHolder.current()).isNull();

        try (SpanScope scope = s.activate()) {
            assertThat(SpanHolder.current()).isSameAs(s);
        }

        assertThat(SpanHolder.current()).isNull();
        s.end();
    }

    @Test
    void adapter_recordException_setsLocalFields_andCreatesOtelExceptionEvent() {
        FixedIds ids = new FixedIds(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7"
        );
        OtelSpanFactory factory = new OtelSpanFactory(ids, openTelemetry);

        Span s = factory.httpRootSpan("GET /ex", null, null, ids);

        RuntimeException ex = new RuntimeException("boom");
        s.recordException(ex);

        assertThat(s.errorType()).isEqualTo(RuntimeException.class.getName());
        assertThat(s.errorMessage()).isEqualTo("boom");

        s.end();

        SpanData sd = exporter.getFinishedSpanItems().get(0);
        List<EventData> events = sd.getEvents();
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(e -> "exception".equals(e.getName()))).isTrue();
    }

    // ----------------------------
    // Test helpers
    // ----------------------------

    /**
     * Deterministic generator that returns W3C-valid IDs:
     * - traceId: 32 lowercase hex, not all zeros
     * - spanId: 16 lowercase hex, not all zeros
     */
    private static final class FixedIds implements TraceIdGenerator {
        private final String traceId;
        private final List<String> spanIds;
        private final AtomicInteger idx = new AtomicInteger(0);

        FixedIds() {
            this("4bf92f3577b34da6a3ce929d0e0e4736",
                    "00f067aa0ba902b7");
        }

        FixedIds(String traceId, String... spanIds) {
            this.traceId = traceId;
            this.spanIds = List.of(spanIds);
        }

        @Override
        public String newTraceId() {
            return traceId;
        }

        @Override
        public String newSpanId() {
            int i = idx.getAndIncrement();
            if (i < spanIds.size()) return spanIds.get(i);
            // fallback: still valid (16 hex, not all zeros)
            return "000000000000000" + ((i % 9) + 1);
        }
    }
}
