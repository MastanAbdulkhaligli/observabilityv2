package az.magusframework.components.lib;



import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.observability.core.error.ErrorClassifier;
import az.magusframework.components.lib.observability.core.error.ErrorInfo;
import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.observability.core.metrics.Outcome;
import az.magusframework.components.lib.observability.core.metrics.TimerSample;
import az.magusframework.components.lib.observability.core.tags.MetricTag;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.telemetry.TelemetryHelper;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.Tracing;
import az.magusframework.components.lib.observability.core.tracing.span.*;

import az.magusframework.components.lib.testapp.controller.PaymentController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * OPTION 2: We mock ErrorClassifier here because this test is about:
 *  - weaving works
 *  - exceptions bubble across layers
 *  - spans/metrics are tagged consistently across all layers
 *
 * ErrorClassifier mapping rules are tested separately in its own unit tests.
 */
public class ErrorPropagationWeavingTest {

    private TestTelemetry telemetry;
    private TraceIdGenerator traceIds;

    private TestTracingBackend tracingBackend;
    private MockedStatic<ErrorClassifier> mockedClassifier;

    @BeforeEach
    void setUp() {
        telemetry = new TestTelemetry();
        traceIds = new FixedIds("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "1111111111111111");

        // reset bootstrap + install telemetry/tracing
        ObservabilityBootstrapTestAccess.resetForTests();
        TelemetryHelper helper = new TelemetryHelper(telemetry);
        ObservabilityBootstrap.init("demo-app", "demo-service", helper, traceIds);

        tracingBackend = new TestTracingBackend();
        TracingTestInstaller.install(tracingBackend);

        // IMPORTANT: mock classifier (OPTION 2)
        mockedClassifier = Mockito.mockStatic(ErrorClassifier.class);

        // hygiene
        MDC.clear();
        SpanHolder.clear();
    }

    @AfterEach
    void tearDown() {
        if (mockedClassifier != null) mockedClassifier.close();
        MDC.clear();
        SpanHolder.clear();
        ObservabilityBootstrapTestAccess.resetForTests();
        TracingTestInstaller.uninstallBestEffort();
    }

    @Test
    void businessException_shouldMarkAllLayerSpansAsBusinessError() {
        // Arrange: classifier returns BUSINESS + BIZ_001 for IllegalArgumentException
        mockedClassifier.when(() ->
                        ErrorClassifier.classify(
                                argThat(t -> t instanceof IllegalArgumentException),
                                any()
                        )
                )
                .thenReturn(new ErrorInfo(ErrorKind.BUSINESS, "BIZ_001", "IllegalArgumentException"));

        PaymentController controller = new PaymentController();

        // Act
        Throwable thrown = catchThrowable(controller::payBusinessFail);

        // Assert: original exception must bubble
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);

        // And spans: controller + service + repo should all be ERROR and BUSINESS
        List<TestSpan> spans = tracingBackend.spans();
        assertThat(spans).hasSize(3);

        spans.forEach(s -> {
            assertThat(s.isEnded()).isTrue();
            assertThat(s.status()).isEqualTo(SpanStatus.ERROR);

            assertThat(s.errorKind()).isEqualTo(ErrorKind.BUSINESS);
            assertThat(s.errorCode()).isEqualTo("BIZ_001");
        });

        // And metrics: each layer records 2 durations (inclusive/exclusive)
        // total calls = 3 layers * 2 metrics = 6
        assertThat(telemetry.durationCalls).hasSize(6);

        telemetry.durationCalls.forEach(call -> {
            assertThat(call.tags).containsEntry("outcome", Outcome.BUSINESS_ERROR.name());
            assertThat(call.tags).containsEntry("error_kind", ErrorKind.BUSINESS.name());
            assertThat(call.tags).containsEntry("error_code", "BIZ_001");
        });

        // no MDC leak
        assertThat(MDC.get("trace.id")).isNull();
        assertThat(MDC.get("span.id")).isNull();
    }

    @Test
    void technicalException_shouldMarkAllLayerSpansAsTechnicalError() {
        // Arrange: classifier returns TECHNICAL + TECH_500 for IllegalStateException
        mockedClassifier.when(() ->
                        ErrorClassifier.classify(
                                argThat(t -> t instanceof IllegalStateException),
                                any()
                        )
                )
                .thenReturn(new ErrorInfo(ErrorKind.TECHNICAL, "TECH_500", "IllegalStateException"));

        PaymentController controller = new PaymentController();

        // Act
        Throwable thrown = catchThrowable(controller::payTechnicalFail);

        // Assert: original exception must bubble
        assertThat(thrown).isInstanceOf(IllegalStateException.class);

        // And spans: controller + service + repo should all be ERROR and TECHNICAL
        List<TestSpan> spans = tracingBackend.spans();
        assertThat(spans).hasSize(3);

        spans.forEach(s -> {
            assertThat(s.isEnded()).isTrue();
            assertThat(s.status()).isEqualTo(SpanStatus.ERROR);

            assertThat(s.errorKind()).isEqualTo(ErrorKind.TECHNICAL);
            assertThat(s.errorCode()).isEqualTo("TECH_500");
        });

        // And metrics: each layer records 2 durations (inclusive/exclusive)
        assertThat(telemetry.durationCalls).hasSize(6);

        telemetry.durationCalls.forEach(call -> {
            assertThat(call.tags).containsEntry("outcome", Outcome.TECHNICAL_ERROR.name());
            assertThat(call.tags).containsEntry("error_kind", ErrorKind.TECHNICAL.name());
            assertThat(call.tags).containsEntry("error_code", "TECH_500");
        });

        // no MDC leak
        assertThat(MDC.get("trace.id")).isNull();
        assertThat(MDC.get("span.id")).isNull();
    }

    // ------------------------------------------------------------------------------------
    // Test doubles (self-contained)
    // ------------------------------------------------------------------------------------

    static final class TestTelemetry implements MetricsRecorder {

        static final class DurationCall {
            final String metric;
            final Map<String, String> tags;
            final long nanos;

            DurationCall(String metric, Map<String, String> tags, long nanos) {
                this.metric = metric;
                this.tags = tags;
                this.nanos = nanos;
            }
        }

        final List<DurationCall> durationCalls = new CopyOnWriteArrayList<>();

        @Override
        public TimerSample startTimer(String metricName) {
            return new TimerSample(metricName, System.nanoTime());
        }

        @Override
        public void stopTimer(TimerSample sample, MetricTags tags, Outcome outcome) {
            long nanos = System.nanoTime() - sample.startNanos();
            Map<String, String> m = new LinkedHashMap<>();
            tags.asList().forEach(t -> m.put(t.key(), t.value()));
            durationCalls.add(new DurationCall(sample.metricName(), m, nanos));
        }

        @Override public void incrementCounter(String metricName, MetricTags tags) {}
        @Override public void recordGauge(String metricName, MetricTags tags, double value) {}

        @Override
        public void recordDuration(String metricName, MetricTags tags, long durationNanos) {
            Map<String, String> m = new LinkedHashMap<>();
            tags.asList().forEach(t -> m.put(t.key(), t.value()));
            durationCalls.add(new DurationCall(metricName, m, durationNanos));
        }
    }

    static final class FixedIds implements TraceIdGenerator {
        private final String traceId;
        private final String spanId;
        FixedIds(String traceId, String spanId) { this.traceId = traceId; this.spanId = spanId; }
        @Override public String newTraceId() { return traceId; }
        @Override public String newSpanId() { return spanId; }
    }

    /**
     * Simple backend that captures spans in creation order.
     */
    static final class TestTracingBackend {
        private final AtomicInteger seq = new AtomicInteger(0);
        private final List<TestSpan> spans = new CopyOnWriteArrayList<>();

        Span childSpan(String operation, MetricTags tags, TraceIdGenerator gen) {
            Span parent = SpanHolder.current();

            String traceId = (parent != null) ? parent.context().traceId() : gen.newTraceId();
            String parentId = (parent != null) ? parent.context().spanId() : null;

            String spanId = String.format("%016x", seq.incrementAndGet());
            TestSpan s = new TestSpan(operation, new SpanContext(traceId, spanId, parentId, true));
            spans.add(s);
            return s;
        }

        List<TestSpan> spans() { return spans; }
    }

    static final class TestSpan implements Span {
        private final String name;
        private final SpanContext ctx;

        boolean ended;
        SpanStatus status = SpanStatus.UNSET;

        ErrorKind recordedErrorKind;
        String recordedErrorCode;
        Throwable exception;

        long start = System.nanoTime();
        long end = -1;

        TestSpan(String name, SpanContext ctx) { this.name = name; this.ctx = ctx; }

        @Override public SpanContext context() { return ctx; }
        @Override public SpanKind kind() { return SpanKind.INTERNAL; }
        @Override public String name() { return name; }
        @Override public void setName(String name) {}
        @Override public long startNanos() { return start; }
        @Override public long endNanos() { return end; }
        @Override public boolean isEnded() { return ended; }
        @Override public SpanStatus status() { return status; }
        @Override public ErrorKind errorKind() { return recordedErrorKind; }
        @Override public String errorCode() { return recordedErrorCode; }
        @Override public void setStatus(SpanStatus s) { this.status = s; }

        @Override public void recordError(ErrorKind kind, String code) { recordedErrorKind = kind; recordedErrorCode = code; }
        @Override public void recordException(Throwable t) { exception = t; }

        @Override
        public SpanScope activate() {
            SpanHolder.push(this);
            return () -> SpanHolder.pop();
        }

        @Override
        public void end() {
            if (ended) return;
            ended = true;
            end = System.nanoTime();
        }

        @Override public String errorType() { return exception != null ? exception.getClass().getName() : null; }
        @Override public String errorMessage() { return exception != null ? exception.getMessage() : null; }
        @Override public void setAttribute(String key, String value) {}
        @Override public void setAttributes(MetricTags tags) {}
    }

    static final class TracingTestInstaller {
        private static Object previous;
        private static Field installedField;

        static void install(TestTracingBackend backend) {
            SpanFactory facade = new TracingFacade(backend);
            try {
                // try common field names you may have used
                for (String fieldName : List.of("spanFactory", "factory", "INSTANCE", "current")) {
                    try {
                        Field f = Tracing.class.getDeclaredField(fieldName);
                        f.setAccessible(true);
                        if (!SpanFactory.class.isAssignableFrom(f.getType())) continue;

                        previous = f.get(null);
                        installedField = f;
                        f.set(null, facade);
                        return;
                    } catch (NoSuchFieldException ignored) {}
                }
                throw new IllegalStateException("Cannot install test SpanFactory into Tracing (field not found)");
            } catch (Exception e) {
                throw new RuntimeException("Failed to install test tracing backend", e);
            }
        }

        static void uninstallBestEffort() {
            try {
                if (installedField != null) {
                    installedField.setAccessible(true);
                    installedField.set(null, previous);
                }
            } catch (Exception ignored) {}
            previous = null;
            installedField = null;
        }
    }

    static final class TracingFacade implements SpanFactory {
        private final TestTracingBackend backend;
        TracingFacade(TestTracingBackend backend) { this.backend = backend; }

        @Override
        public Span childSpan(String operation, MetricTags tags, TraceIdGenerator gen) {
            return backend.childSpan(operation, tags, gen);
        }

        @Override
        public Span httpRootSpan(String name, MetricTags tags, SpanContext inboundParent, TraceIdGenerator gen) {
            // for these tests just reuse childSpan
            return backend.childSpan(name, tags, gen);
        }
    }

    static final class ObservabilityBootstrapTestAccess {
        static void resetForTests() {
            try {
                Method m = ObservabilityBootstrap.class.getDeclaredMethod("resetForTests");
                m.setAccessible(true);
                m.invoke(null);
            } catch (NoSuchMethodException ignored) {
                // ok if not present
            } catch (Exception e) {
                throw new RuntimeException("Failed to reset bootstrap", e);
            }
        }
    }
}
