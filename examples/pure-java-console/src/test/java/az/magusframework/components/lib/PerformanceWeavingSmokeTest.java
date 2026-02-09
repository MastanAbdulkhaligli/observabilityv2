package az.magusframework.components.lib;



import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.observability.core.metrics.Outcome;
import az.magusframework.components.lib.observability.core.metrics.TimerSample;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.telemetry.TelemetryHelper;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.Tracing;
import az.magusframework.components.lib.observability.core.tracing.span.*;

import az.magusframework.components.lib.testapp.controller.PaymentController;
import org.junit.jupiter.api.*;

import org.slf4j.MDC;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * Performance smoke test: verifies weaving overhead is not insane.
 *
 * NOTE:
 * - This is NOT a micro-benchmark (JMH is for that).
 * - Keep thresholds loose; treat as a regression alarm only.
 */
class PerformanceWeavingSmokeTest {

    private static final int WARMUP = 5_000;
    private static final int ITERS  = 50_000;

    private TelemetryHelper telemetry;
    private TraceIdGenerator traceIds;

    private TestTracingBackend tracingBackend;

    @BeforeEach
    void setUp() {
        // Optional: skip on CI (perf is noisy)
        assumeFalse("true".equalsIgnoreCase(System.getenv("CI")), "Skip perf smoke on CI");

        telemetry = new TelemetryHelper(new NoopMetricsRecorderForTests());
        traceIds = new FixedIds("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "1111111111111111");

        ObservabilityBootstrapTestAccess.resetForTests();
        ObservabilityBootstrap.init("demo-app", "demo-service", telemetry, traceIds);

        tracingBackend = new TestTracingBackend();
        TracingTestInstaller.install(tracingBackend);

        MDC.clear();
        SpanHolder.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SpanHolder.clear();
        ObservabilityBootstrapTestAccess.resetForTests();
        TracingTestInstaller.uninstallBestEffort();
    }

    @Test
    void weavingOverhead_shouldBeReasonable_smoke() {
        PaymentController controller = new PaymentController();

        // Warmup (JIT)
        for (int i = 0; i < WARMUP; i++) {
            controller.payOk();
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERS; i++) {
            controller.payOk();
        }
        long end = System.nanoTime();

        long totalNanos = end - start;
        double nanosPerCall = (double) totalNanos / ITERS;

        // We want a *very* loose guardrail (machine dependent).
        // Example: fail only if something is catastrophically wrong.
        // Tune this number based on your laptop runs.
        double MAX_NANOS_PER_CALL = 200_000; // 200 µs/call (very forgiving)
        assertThat(nanosPerCall)
                .as("nanos/call = %.1f", nanosPerCall)
                .isLessThan(MAX_NANOS_PER_CALL);

        // Sanity: ensure aspects actually ran (spans created)
        assertThat(tracingBackend.spans()).isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // Test support
    // -------------------------------------------------------------------------

    static final class NoopMetricsRecorderForTests implements MetricsRecorder {
        @Override public TimerSample startTimer(String metricName) { return new TimerSample(metricName, System.nanoTime()); }
        @Override public void stopTimer(TimerSample sample, MetricTags tags, Outcome outcome) {}
        @Override public void incrementCounter(String metricName, MetricTags tags) {}
        @Override public void recordGauge(String metricName, MetricTags tags, double value) {}
        @Override public void recordDuration(String metricName, MetricTags tags, long durationNanos) {}
    }

    static final class FixedIds implements TraceIdGenerator {
        private final String traceId;
        private final String spanId;
        FixedIds(String traceId, String spanId) { this.traceId = traceId; this.spanId = spanId; }
        @Override public String newTraceId() { return traceId; }
        @Override public String newSpanId() { return spanId; }
    }

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

        private volatile boolean ended;
        private volatile SpanStatus status = SpanStatus.UNSET;

        TestSpan(String name, SpanContext ctx) {
            this.name = name;
            this.ctx = ctx;
        }

        @Override public SpanContext context() { return ctx; }
        @Override public SpanKind kind() { return SpanKind.INTERNAL; }
        @Override public String name() { return name; }
        @Override public void setName(String name) {}
        @Override public long startNanos() { return 0; }
        @Override public long endNanos() { return 0; }
        @Override public boolean isEnded() { return ended; }
        @Override public SpanStatus status() { return status; }
        @Override public ErrorKind errorKind() { return null; }
        @Override public String errorCode() { return null; }
        @Override public void setStatus(SpanStatus s) { this.status = s; }
        @Override public void recordError(ErrorKind kind, String code) {}
        @Override public void recordException(Throwable t) {}

        @Override
        public SpanScope activate() {
            SpanHolder.push(this);
            return () -> SpanHolder.pop();
        }

        @Override
        public void end() {
            ended = true;
        }

        @Override public String errorType() { return null; }
        @Override public String errorMessage() { return null; }
        @Override public void setAttribute(String key, String value) {}
        @Override public void setAttributes(MetricTags tags) {}
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

    static final class ObservabilityBootstrapTestAccess {
        static void resetForTests() {
            try {
                Method m = ObservabilityBootstrap.class.getDeclaredMethod("resetForTests");
                m.setAccessible(true);
                m.invoke(null);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                throw new RuntimeException("Failed to reset bootstrap", e);
            }
        }
    }
}
