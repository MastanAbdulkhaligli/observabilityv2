package az.magusframework.components.lib;


import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.observability.core.error.ErrorClassifier;
import az.magusframework.components.lib.observability.core.telemetry.TelemetryHelper;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.testapp.controller.PaymentController;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class NestedChainWeavingTest {

    private MockedStatic<ErrorClassifier> mockedErrorClassifier;
    private TestTracingBackend backend;

    @BeforeEach
    void init() {
        mockedErrorClassifier = Mockito.mockStatic(ErrorClassifier.class);

        resetBootstrap();
        TelemetryHelper telemetry = new TelemetryHelper(new NoopMetricsRecorderForTests());
        TraceIdGenerator ids = new FixedIds("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "1111111111111111");
        ObservabilityBootstrap.init("demo-app", "demo-service", telemetry, ids);

        backend = new TestTracingBackend(); // instance holder not needed; factory uses internal list
        TestTracingBackend.Installer.install(new TestTracingBackend(){}.asFactory()); // trick not good
    }

    @AfterEach
    void cleanup() {
        mockedErrorClassifier.close();
        MDC.clear();
        az.magusframework.components.lib.observability.core.tracing.span.SpanHolder.clear();
        TestTracingBackend.Installer.uninstallBestEffort();
        resetBootstrap();
    }

    @Test
    void controllerServiceRepo_shouldProduceParentChildChain() {
        // install backend properly
        TestTracingBackend real = new TestTracingBackend();
        TestTracingBackend.Installer.install(real.asFactory());

        PaymentController controller = new PaymentController();
        String res = controller.payOk();
        assertThat(res).isEqualTo("OK");

        List<TestTracingBackend.TestSpan> spans = real.spans();
        assertThat(spans).hasSize(3);

        TestTracingBackend.TestSpan controllerSpan = spans.get(0);
        TestTracingBackend.TestSpan serviceSpan = spans.get(1);
        TestTracingBackend.TestSpan repoSpan = spans.get(2);

        assertThat(controllerSpan.context().parentSpanId()).isNull();
        assertThat(serviceSpan.context().parentSpanId()).isEqualTo(controllerSpan.context().spanId());
        assertThat(repoSpan.context().parentSpanId()).isEqualTo(serviceSpan.context().spanId());

        assertThat(controllerSpan.isEnded()).isTrue();
        assertThat(serviceSpan.isEnded()).isTrue();
        assertThat(repoSpan.isEnded()).isTrue();
    }

    // ---- helpers ----
    static class FixedIds implements TraceIdGenerator {
        private final String traceId;
        private final String spanId;
        FixedIds(String traceId, String spanId) { this.traceId = traceId; this.spanId = spanId; }
        @Override public String newTraceId() { return traceId; }
        @Override public String newSpanId() { return spanId; }
    }

    static void resetBootstrap() {
        try {
            Method m = ObservabilityBootstrap.class.getDeclaredMethod("resetForTests");
            m.setAccessible(true);
            m.invoke(null);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
