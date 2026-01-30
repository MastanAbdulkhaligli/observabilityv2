package az.magusframework.components.lib.observability.core.tracing;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RandomTraceIdGeneratorTest {

    private static final int TRACE_ID_LEN = 32;
    private static final int SPAN_ID_LEN  = 16;

    private final RandomTraceIdGenerator gen = new RandomTraceIdGenerator();

    @Test
    void newTraceId_hasCorrectLengthAndLowercaseHex_andNotAllZeros() {
        String id = gen.newTraceId();

        assertNotNull(id);
        assertEquals(TRACE_ID_LEN, id.length(), "traceId must be 32 hex chars");
        assertTrue(isLowerHex(id), "traceId must be lowercase hex");
        assertFalse(isAllZerosHex(id), "traceId must not be all zeros");
    }

    @Test
    void newSpanId_hasCorrectLengthAndLowercaseHex_andNotAllZeros() {
        String id = gen.newSpanId();

        assertNotNull(id);
        assertEquals(SPAN_ID_LEN, id.length(), "spanId must be 16 hex chars");
        assertTrue(isLowerHex(id), "spanId must be lowercase hex");
        assertFalse(isAllZerosHex(id), "spanId must not be all zeros");
    }

    @Test
    void generatesManyTraceIds_withoutDuplicates_sanityCheck() {
        // Probabilistic test: collisions are astronomically unlikely at this scale.
        int n = 10_000;
        Set<String> ids = new java.util.HashSet<>(n);

        for (int i = 0; i < n; i++) {
            String id = gen.newTraceId();
            assertTrue(ids.add(id), "Unexpected duplicate traceId at i=" + i);
        }
    }

    @Test
    void generatesManySpanIds_withoutDuplicates_sanityCheck() {
        // Still extremely unlikely to collide at this scale (64-bit).
        // Keep sample modest to avoid any concern of flakiness.
        int n = 50_000;
        Set<String> ids = new java.util.HashSet<>(n);

        for (int i = 0; i < n; i++) {
            String id = gen.newSpanId();
            assertTrue(ids.add(id), "Unexpected duplicate spanId at i=" + i);
        }
    }

    @Test
    void concurrentGeneration_traceIds_areValidAndNoExceptions() throws Exception {
        int threads = 8;
        int perThread = 5_000;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            ConcurrentMap<String, Boolean> seen = new ConcurrentHashMap<>();
            AtomicInteger invalid = new AtomicInteger(0);

            Callable<Void> task = () -> {
                for (int i = 0; i < perThread; i++) {
                    String id = gen.newTraceId();
                    if (id == null
                            || id.length() != TRACE_ID_LEN
                            || !isLowerHex(id)
                            || isAllZerosHex(id)) {
                        invalid.incrementAndGet();
                    }
                    seen.put(id, Boolean.TRUE);
                }
                return null;
            };

            for (Future<Void> f : pool.invokeAll(java.util.Collections.nCopies(threads, task))) {
                f.get(); // propagate exceptions
            }

            assertEquals(0, invalid.get(), "Found invalid traceId(s) in concurrent generation");
            assertEquals(threads * perThread, seen.size(), "Unexpected duplicates in concurrent traceId generation");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentGeneration_spanIds_areValidAndNoExceptions() throws Exception {
        int threads = 8;
        int perThread = 10_000;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            ConcurrentMap<String, Boolean> seen = new ConcurrentHashMap<>();
            AtomicInteger invalid = new AtomicInteger(0);

            Callable<Void> task = () -> {
                for (int i = 0; i < perThread; i++) {
                    String id = gen.newSpanId();
                    if (id == null
                            || id.length() != SPAN_ID_LEN
                            || !isLowerHex(id)
                            || isAllZerosHex(id)) {
                        invalid.incrementAndGet();
                    }
                    seen.put(id, Boolean.TRUE);
                }
                return null;
            };

            for (Future<Void> f : pool.invokeAll(java.util.Collections.nCopies(threads, task))) {
                f.get();
            }

            assertEquals(0, invalid.get(), "Found invalid spanId(s) in concurrent generation");
            assertEquals(threads * perThread, seen.size(), "Unexpected duplicates in concurrent spanId generation");
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- helpers ----------

    private static boolean isLowerHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!ok) return false;
        }
        return true;
    }

    private static boolean isAllZerosHex(String s) {
        // All-zeros means every char is '0'
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') return false;
        }
        return true;
    }
}
