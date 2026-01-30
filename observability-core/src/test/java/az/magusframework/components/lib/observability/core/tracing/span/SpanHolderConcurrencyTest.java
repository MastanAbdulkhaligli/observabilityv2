package az.magusframework.components.lib.observability.core.tracing.span;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

//No matter how many users hit the system, how deep the call stack is,
// or how aggressively threads are reused — every request gets its own clean,
// correct trace.

class SpanHolderConcurrencyTest {

    @AfterEach
    void cleanup() {
        SpanHolder.clear();
    }

    @Test
    void stressTest_nestedSpans_concurrentThreads_noLeakage() throws Exception {

        int threads = 8;          // number of concurrent threads
        int iterations = 1_000;   // spans per thread
        int depth = 5;            // nesting depth per iteration

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Void>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;

            futures.add(pool.submit(() -> {
                for (int i = 0; i < iterations; i++) {

                    // ---- Build nested stack ----
                    for (int d = 0; d < depth; d++) {
                        TestSpan span = new TestSpan("T" + threadId + "-I" + i + "-D" + d);
                        SpanHolder.push(span);

                        // Current span must always be the most recent
                        Span current = SpanHolder.current();
                        assertNotNull(current);
                        assertSame(span, current);
                    }

                    // ---- Pop in reverse order ----
                    for (int d = depth - 1; d >= 0; d--) {
                        Span popped = SpanHolder.pop();
                        assertNotNull(popped);
                        assertTrue(popped instanceof TestSpan);
                        assertTrue(((TestSpan) popped).id.endsWith("D" + d));
                    }

                    // ---- Stack must be empty ----
                    assertNull(SpanHolder.current(),
                            "Thread " + threadId + " leaked span at iteration " + i);
                }
                return null;
            }));
        }

        // ---- Await completion ----
        for (Future<Void> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }

        pool.shutdownNow();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    // ---------------------------------------------------------------------
    // Minimal Span stub
    // ---------------------------------------------------------------------
    static final class TestSpan extends AbstractTestSpan {

        final String id;

        TestSpan(String id) {
            this.id = id;
        }


        @Override
        public String toString() {
            return "TestSpan(" + id + ")";
        }
    }
}
