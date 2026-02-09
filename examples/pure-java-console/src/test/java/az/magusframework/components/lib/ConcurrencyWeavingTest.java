package az.magusframework.components.lib;


import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.observability.core.telemetry.TelemetryHelper;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.span.SpanHolder;
import az.magusframework.components.lib.testapp.controller.PaymentController;
import az.magusframework.components.lib.TestTracingBackend;
import org.junit.jupiter.api.*;

import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

public class ConcurrencyWeavingTest {

    @BeforeEach
    void init() {
        resetBootstrap();
        TelemetryHelper telemetry = new TelemetryHelper(new NoopMetricsRecorderForTests());
        ObservabilityBootstrap.init("demo-app", "demo-service", telemetry, new UniqueIds());
    }

    @AfterEach
    void cleanup() {
        TestTracingBackend.Installer.uninstallBestEffort();
        resetBootstrap();
    }

    @Test
    void concurrentCalls_shouldNotLeakSpanHolderOrMdc() throws Exception {
        TestTracingBackend backend = new TestTracingBackend();
        TestTracingBackend.Installer.install(backend.asFactory());

        int threads = 8;
        int tasks = 200;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> work = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                work.add(() -> {
                    PaymentController controller = new PaymentController();
                    String r = controller.payOk();
                    if (!"OK".equals(r)) return false;

                    // must be clean at end of call
                    boolean spanClean = (SpanHolder.current() == null);
                    boolean mdcClean = (MDC.get("trace.id") == null && MDC.get("span.id") == null);
                    return spanClean && mdcClean;
                });
            }

            List<Future<Boolean>> results = pool.invokeAll(work);
            for (Future<Boolean> f : results) {
                assertThat(f.get()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    static final class UniqueIds implements TraceIdGenerator {
        private final ThreadLocal<Integer> seq = ThreadLocal.withInitial(() -> 0);

        @Override public String newTraceId() {
            int n = seq.get() + 1;
            seq.set(n);
            return String.format("%032x", n);
        }

        @Override public String newSpanId() {
            int n = seq.get();
            return String.format("%016x", n);
        }
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
