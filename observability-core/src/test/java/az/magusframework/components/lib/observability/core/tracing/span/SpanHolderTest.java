package az.magusframework.components.lib.observability.core.tracing.span;

import az.magusframework.components.lib.observability.core.error.ErrorKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class SpanHolderTest {

    @AfterEach
    void cleanup() {
        // Ensure no leakage between tests when run on same worker thread
        SpanHolder.clear();
    }

    @Test
    void push_null_isIgnored() {
        SpanHolder.push(null);
        assertNull(SpanHolder.current());
        assertNull(SpanHolder.pop());
    }

    @Test
    void current_returnsNull_whenEmpty() {
        assertNull(SpanHolder.current());
    }

    @Test
    void pop_returnsNull_whenEmpty() {
        assertNull(SpanHolder.pop());
    }

    @Test
    void pushAndCurrentAndPop_followLifoOrder() {
        Span s1 = new TestSpan("s1");
        Span s2 = new TestSpan("s2");
        Span s3 = new TestSpan("s3");

        SpanHolder.push(s1);
        assertSame(s1, SpanHolder.current());

        SpanHolder.push(s2);
        assertSame(s2, SpanHolder.current());

        SpanHolder.push(s3);
        assertSame(s3, SpanHolder.current());

        assertSame(s3, SpanHolder.pop());
        assertSame(s2, SpanHolder.current());

        assertSame(s2, SpanHolder.pop());
        assertSame(s1, SpanHolder.current());

        assertSame(s1, SpanHolder.pop());
        assertNull(SpanHolder.current());

        // extra pop is safe
        assertNull(SpanHolder.pop());
    }

    @Test
    void clear_wipesStack() {
        Span s1 = new TestSpan("s1");
        SpanHolder.push(s1);

        assertSame(s1, SpanHolder.current());
        SpanHolder.clear();

        assertNull(SpanHolder.current());
        assertNull(SpanHolder.pop());
    }

    @Test
    void threadLocal_isolation_betweenThreads() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> taskA = () -> {
                SpanHolder.clear();
                SpanHolder.push(new TestSpan("A1"));
                SpanHolder.push(new TestSpan("A2"));
                Span current = SpanHolder.current();
                return ((TestSpan) current).id;
            };

            Callable<String> taskB = () -> {
                SpanHolder.clear();
                SpanHolder.push(new TestSpan("B1"));
                Span current = SpanHolder.current();
                return ((TestSpan) current).id;
            };

            Future<String> fa = pool.submit(taskA);
            Future<String> fb = pool.submit(taskB);

            assertEquals("A2", fa.get());
            assertEquals("B1", fb.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void threadReuse_doesNotLeak_whenClearOrStackEmptyRemovalHappens() throws Exception {
        // This test validates the "STACK.remove()" behavior indirectly by using a single-thread pool
        // where the same thread is reused across tasks.
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // Task 1: push then pop until empty -> SpanHolder.pop() should remove the ThreadLocal
            Future<Void> t1 = pool.submit(() -> {
                SpanHolder.clear();
                SpanHolder.push(new TestSpan("X"));
                assertNotNull(SpanHolder.current());
                assertNotNull(SpanHolder.pop()); // empties stack -> removes TL
                assertNull(SpanHolder.current());
                return null;
            });
            t1.get();

            // Task 2: should see clean slate even though same thread is reused
            Future<Void> t2 = pool.submit(() -> {
                assertNull(SpanHolder.current(), "Expected no span leaked across reused thread");
                return null;
            });
            t2.get();
        } finally {
            pool.shutdownNow();
        }
    }
    static final class TestSpan extends AbstractTestSpan {
        final String id;
        TestSpan(String id) { this.id = id; }
    }

}
