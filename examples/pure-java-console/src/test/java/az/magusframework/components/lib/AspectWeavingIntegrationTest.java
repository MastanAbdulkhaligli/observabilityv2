package az.magusframework.components.lib;

import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.observability.core.error.ErrorClassifier;
import az.magusframework.components.lib.observability.core.error.ErrorInfo;
import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.observability.core.metrics.Outcome;
import az.magusframework.components.lib.observability.core.metrics.TimerSample;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.telemetry.TelemetryHelper;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.Tracing;
import az.magusframework.components.lib.observability.core.tracing.span.*;
import az.magusframework.components.lib.testapp.service.PaymentService;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AspectWeavingIntegrationTest {
    private TelemetryHelper telemetry;
    private TestTelemetry testTelemetry;
    private TraceIdGenerator traceIds;
    private TestTracingBackend tracingBackend;
    private MockedStatic<ErrorClassifier> mockedErrorClassifier;

    @BeforeEach
    void setUp() {
        testTelemetry = new TestTelemetry();
        telemetry = new TelemetryHelper(testTelemetry);
        traceIds = new FixedIds("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "1111111111111111");
        mockedErrorClassifier = Mockito.mockStatic(ErrorClassifier.class);

        ObservabilityBootstrapTestAccess.resetForTests();
        ObservabilityBootstrap.init("demo-app", "demo-service", telemetry, traceIds);

        tracingBackend = new TestTracingBackend();
        TracingTestInstaller.install(tracingBackend);

        MDC.clear();
        SpanHolder.clear();
    }

    @AfterEach
    void tearDown() {
        mockedErrorClassifier.close();
        MDC.clear();
        SpanHolder.clear();
        ObservabilityBootstrapTestAccess.resetForTests();
        TracingTestInstaller.uninstallBestEffort();
    }

    @Test
    void serviceAspect_shouldCreateSpan_andRecordTwoDurations_onSuccess() {
        // when
        PaymentService svc = new PaymentService();
        String res = svc.pay(); // <-- IMPORTANT: aspect must intercept THIS call

        // then
        assertThat(res).isEqualTo("OK");

        // verify tracing happened (means aspect fired -> executor fired -> Tracing backend invoked)
        TestSpan span = tracingBackend.lastCreatedSpan();
        assertThat(span).isNotNull();
        assertThat(span.isEnded()).isTrue();

        // Operation name depends on your extractor; keep it tolerant but meaningful
        assertThat(span.name()).contains("PaymentService").contains("pay");

        // verify metrics happened
        assertThat(testTelemetry.durationCalls).hasSize(2);
        assertThat(testTelemetry.durationCalls).allSatisfy(call -> {
            assertThat(call.metric).isIn("obs.layer.duration", "obs.layer.exclusive_duration");
            assertThat(call.tags).containsEntry("outcome", Outcome.SUCCESS.name());
        });

        // verify no MDC leaks
        assertThat(MDC.get("trace.id")).isNull();
        assertThat(MDC.get("span.id")).isNull();
    }

    @Test
    void serviceAspect_shouldRecordBusinessError_onException() {
        PaymentService svc = new PaymentService();

        // IMPORTANT:
        // PaymentService.failBusiness() likely throws a NEW IllegalArgumentException instance,
        // so eq(ex) will NOT match. Use isA(...) or any(Throwable.class).
        mockedErrorClassifier.when(() -> ErrorClassifier.classify(Mockito.isA(IllegalArgumentException.class), Mockito.<Integer>any()))
                .thenReturn(new ErrorInfo(ErrorKind.BUSINESS, "INVALID_INPUT", "IllegalArgumentException"));

        // If your executor uses determineOutcome, stub it broadly too
        mockedErrorClassifier.when(() -> ErrorClassifier.determineOutcome(Mockito.<Integer>any(), Mockito.isA(IllegalArgumentException.class)))
                .thenReturn(Outcome.BUSINESS_ERROR);

        Throwable thrown = catchThrowable(svc::failBusiness);
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);

        TestSpan span = tracingBackend.lastCreatedSpan();
        assertThat(span).isNotNull();
        assertThat(span.status()).isEqualTo(SpanStatus.ERROR);
        assertThat(span.errorKind()).isEqualTo(ErrorKind.BUSINESS);
        assertThat(span.errorCode()).isEqualTo("INVALID_INPUT");
        assertThat(span.isEnded()).isTrue();

        assertThat(testTelemetry.durationCalls).hasSize(2);
        assertThat(testTelemetry.durationCalls).allSatisfy(call -> {
            assertThat(call.tags)
                    .containsEntry("outcome", Outcome.BUSINESS_ERROR.name())
                    .containsEntry("error_kind", ErrorKind.BUSINESS.name())
                    .containsEntry("error_code", "INVALID_INPUT");
        });
    }

    // -------------------------------------------------------------------------
    // Test Doubles
    // -------------------------------------------------------------------------

    static class TestTelemetry implements MetricsRecorder {
        static class DurationCall {
            final String metric;
            final Map<String, String> tags;
            final long nanos;

            DurationCall(String metric, Map<String, String> tags, long nanos) {
                this.metric = metric;
                this.tags = tags;
                this.nanos = nanos;
            }
        }

        final List<DurationCall> durationCalls = new ArrayList<>();

        @Override public TimerSample startTimer(String metricName) {
            return new TimerSample(metricName, System.nanoTime());
        }

        @Override public void stopTimer(TimerSample sample, MetricTags tags, Outcome outcome) {
            long nanos = System.nanoTime() - sample.startNanos();
            Map<String, String> m = new LinkedHashMap<>();
            tags.asList().forEach(t -> m.put(t.key(), t.value()));
            durationCalls.add(new DurationCall(sample.metricName(), m, nanos));
        }

        @Override public void incrementCounter(String metricName, MetricTags tags) {}
        @Override public void recordGauge(String metricName, MetricTags tags, double value) {}

        @Override public void recordDuration(String metricName, MetricTags tags, long durationNanos) {
            Map<String, String> m = new LinkedHashMap<>();
            tags.asList().forEach(t -> m.put(t.key(), t.value()));
            durationCalls.add(new DurationCall(metricName, m, durationNanos));
        }
    }

    static class FixedIds implements TraceIdGenerator {
        private final String traceId;
        private final String spanId;
        FixedIds(String traceId, String spanId) { this.traceId = traceId; this.spanId = spanId; }
        @Override public String newTraceId() { return traceId; }
        @Override public String newSpanId() { return spanId; }
    }

    static class TestTracingBackend {
        private final AtomicInteger seq = new AtomicInteger(0);
        private final List<TestSpan> spans = new ArrayList<>();

        Span childSpan(String operation, MetricTags tags, TraceIdGenerator gen) {
            Span parent = SpanHolder.current();
            String traceId = (parent != null) ? parent.context().traceId() : gen.newTraceId();
            String parentId = (parent != null) ? parent.context().spanId() : null;

            String spanId = String.format("%016x", seq.incrementAndGet());
            TestSpan s = new TestSpan(operation, new SpanContext(traceId, spanId, parentId, true));
            spans.add(s);
            return s;
        }

        TestSpan lastCreatedSpan() { return spans.isEmpty() ? null : spans.get(spans.size() - 1); }
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

        @Override public SpanScope activate() {
            SpanHolder.push(this);
            return () -> SpanHolder.pop();
        }

        @Override public void end() {
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

        @Override public Span childSpan(String operation, MetricTags tags, TraceIdGenerator gen) {
            return backend.childSpan(operation, tags, gen);
        }

        @Override public Span httpRootSpan(String name, MetricTags tags, SpanContext inboundParent, TraceIdGenerator gen) {
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
                // ok
            } catch (Exception e) {
                throw new RuntimeException("Failed to reset bootstrap", e);
            }
        }
    }
}
