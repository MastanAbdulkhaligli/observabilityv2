package az.magusframework.components.lib.observability.slf4j.tracing;

import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.logging.LogFields;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.tracing.span.*;
import az.magusframework.components.lib.observability.logging.LoggingContext;
import az.magusframework.components.lib.observability.tracing.TraceMdcScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class LoggingContextTraceMdcScopeIntegrationTest {

    // --- fixed valid ids (SpanContext validates hex + length + not all zeros) ---
    private static final String TRACE_ID_32 = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID_16  = "00f067aa0ba902b7";
    private static final String PARENT_16   = "1111111111111111";

    @AfterEach
    void cleanup() {
        // Important: MDC + SpanHolder are thread-local; clean to avoid test interference.
        MDC.clear();
        SpanHolder.clear();
    }

    // ---------------------------------------------------------------------
    // LoggingContext tests
    // ---------------------------------------------------------------------

    @Test
    void putTraceContext_writesExpectedMdcFields_andHandlesNullParent() {
        SpanContext ctx = new SpanContext(TRACE_ID_32, SPAN_ID_16, null, true);

        LoggingContext.putTraceContext(ctx);

        assertEquals(TRACE_ID_32, MDC.get(LogFields.Trace.TRACE_ID));
        assertEquals(SPAN_ID_16, MDC.get(LogFields.Trace.SPAN_ID));
        assertNull(MDC.get(LogFields.Trace.PARENT_SPAN_ID), "parentSpanId should be absent when null");
        assertEquals("true", MDC.get(LogFields.Trace.SAMPLED));
    }

    @Test
    void putTraceContext_writesParentSpanId_whenPresent() {
        SpanContext ctx = new SpanContext(TRACE_ID_32, SPAN_ID_16, PARENT_16, false);

        LoggingContext.putTraceContext(ctx);

        assertEquals(TRACE_ID_32, MDC.get(LogFields.Trace.TRACE_ID));
        assertEquals(SPAN_ID_16, MDC.get(LogFields.Trace.SPAN_ID));
        assertEquals(PARENT_16, MDC.get(LogFields.Trace.PARENT_SPAN_ID));
        assertEquals("false", MDC.get(LogFields.Trace.SAMPLED));
    }

    @Test
    void putTraceContext_null_clearsTraceFields() {
        // seed something first
        MDC.put(LogFields.Trace.TRACE_ID, TRACE_ID_32);
        MDC.put(LogFields.Trace.SPAN_ID, SPAN_ID_16);
        MDC.put(LogFields.Trace.PARENT_SPAN_ID, PARENT_16);
        MDC.put(LogFields.Trace.SAMPLED, "true");

        LoggingContext.putTraceContext((SpanContext) null);

        assertNull(MDC.get(LogFields.Trace.TRACE_ID));
        assertNull(MDC.get(LogFields.Trace.SPAN_ID));
        assertNull(MDC.get(LogFields.Trace.PARENT_SPAN_ID));
        assertNull(MDC.get(LogFields.Trace.SAMPLED));
    }

    @Test
    void captureAndRestoreTraceState_roundTrip_restoresExactly() {
        MDC.put(LogFields.Trace.TRACE_ID, "t1");
        MDC.put(LogFields.Trace.SPAN_ID, "s1");
        MDC.put(LogFields.Trace.PARENT_SPAN_ID, "p1");
        MDC.put(LogFields.Trace.SAMPLED, "false");

        LoggingContext.TraceState saved = LoggingContext.captureTraceState();

        // mutate MDC
        MDC.put(LogFields.Trace.TRACE_ID, "t2");
        MDC.remove(LogFields.Trace.PARENT_SPAN_ID);
        MDC.put(LogFields.Trace.SAMPLED, "true");

        LoggingContext.restoreTraceState(saved);

        assertEquals("t1", MDC.get(LogFields.Trace.TRACE_ID));
        assertEquals("s1", MDC.get(LogFields.Trace.SPAN_ID));
        assertEquals("p1", MDC.get(LogFields.Trace.PARENT_SPAN_ID));
        assertEquals("false", MDC.get(LogFields.Trace.SAMPLED));
    }

    @Test
    void putCorrelationIds_removesBlankBaggage() {
        LoggingContext.putCorrelationIds("req-1", "   ");

        assertEquals("req-1", MDC.get(LogFields.Correlation.REQUEST_ID));
        assertNull(MDC.get(LogFields.Correlation.BAGGAGE), "blank baggage must be removed");
    }

    // ---------------------------------------------------------------------
    // TraceMdcScope tests (integration)
    // ---------------------------------------------------------------------

    @Test
    void traceMdcScope_fromSpan_setsMdc_thenRestoresPrevious_onClose() {
        // previous trace in MDC
        MDC.put(LogFields.Trace.TRACE_ID, "prevTrace");
        MDC.put(LogFields.Trace.SPAN_ID, "prevSpan");
        MDC.put(LogFields.Trace.SAMPLED, "false");

        Span next = new TestSpan(new SpanContext(TRACE_ID_32, SPAN_ID_16, null, true), "next");

        try (TraceMdcScope scope = TraceMdcScope.fromSpan(next)) {
            assertEquals(TRACE_ID_32, MDC.get(LogFields.Trace.TRACE_ID));
            assertEquals(SPAN_ID_16, MDC.get(LogFields.Trace.SPAN_ID));
            assertEquals("true", MDC.get(LogFields.Trace.SAMPLED));
        }

        // restored
        assertEquals("prevTrace", MDC.get(LogFields.Trace.TRACE_ID));
        assertEquals("prevSpan", MDC.get(LogFields.Trace.SPAN_ID));
        assertEquals("false", MDC.get(LogFields.Trace.SAMPLED));
    }

    @Test
    void traceMdcScope_nestedScopes_restoreCorrectly() {
        Span s1 = new TestSpan(new SpanContext(TRACE_ID_32, SPAN_ID_16, null, true), "s1");
        Span s2 = new TestSpan(new SpanContext(TRACE_ID_32, "2222222222222222", SPAN_ID_16, true), "s2");

        // baseline
        MDC.put(LogFields.Trace.TRACE_ID, "base");
        MDC.put(LogFields.Trace.SPAN_ID, "baseSpan");
        MDC.put(LogFields.Trace.SAMPLED, "false");

        try (TraceMdcScope outer = TraceMdcScope.fromSpan(s1)) {
            assertEquals(SPAN_ID_16, MDC.get(LogFields.Trace.SPAN_ID));

            try (TraceMdcScope inner = TraceMdcScope.fromSpan(s2)) {
                assertEquals("2222222222222222", MDC.get(LogFields.Trace.SPAN_ID));
                assertEquals(SPAN_ID_16, MDC.get(LogFields.Trace.PARENT_SPAN_ID));
            }

            // back to outer
            assertEquals(SPAN_ID_16, MDC.get(LogFields.Trace.SPAN_ID));
            assertNull(MDC.get(LogFields.Trace.PARENT_SPAN_ID));
        }

        // back to baseline
        assertEquals("base", MDC.get(LogFields.Trace.TRACE_ID));
        assertEquals("baseSpan", MDC.get(LogFields.Trace.SPAN_ID));
        assertEquals("false", MDC.get(LogFields.Trace.SAMPLED));
    }

    @Test
    void traceMdcScope_fromCurrent_readsSpanHolderCurrent() {
        Span s1 = new TestSpan(new SpanContext(TRACE_ID_32, SPAN_ID_16, null, true), "s1");
        Span s2 = new TestSpan(new SpanContext(TRACE_ID_32, "2222222222222222", SPAN_ID_16, true), "s2");

        SpanHolder.push(s1);
        try (TraceMdcScope outer = TraceMdcScope.fromCurrent()) {
            assertEquals(SPAN_ID_16, MDC.get(LogFields.Trace.SPAN_ID));

            SpanHolder.push(s2);
            try (TraceMdcScope inner = TraceMdcScope.fromCurrent()) {
                assertEquals("2222222222222222", MDC.get(LogFields.Trace.SPAN_ID));
                assertEquals(SPAN_ID_16, MDC.get(LogFields.Trace.PARENT_SPAN_ID));
            } finally {
                SpanHolder.pop();
            }

            // after inner close, we restored MDC to outer's previous state
            assertEquals(SPAN_ID_16, MDC.get(LogFields.Trace.SPAN_ID));
        } finally {
            SpanHolder.pop();
        }
    }

    @Test
    void traceMdcScope_fromCurrent_whenNoSpan_clearsTraceMdc_thenRestoresPrev() {
        MDC.put(LogFields.Trace.TRACE_ID, "prevTrace");
        MDC.put(LogFields.Trace.SPAN_ID, "prevSpan");
        MDC.put(LogFields.Trace.SAMPLED, "true");

        assertNull(SpanHolder.current());

        try (TraceMdcScope scope = TraceMdcScope.fromCurrent()) {
            // LoggingContext.putTraceContext(null) => clears trace MDC
            assertNull(MDC.get(LogFields.Trace.TRACE_ID));
            assertNull(MDC.get(LogFields.Trace.SPAN_ID));
            assertNull(MDC.get(LogFields.Trace.SAMPLED));
        }

        // restored
        assertEquals("prevTrace", MDC.get(LogFields.Trace.TRACE_ID));
        assertEquals("prevSpan", MDC.get(LogFields.Trace.SPAN_ID));
        assertEquals("true", MDC.get(LogFields.Trace.SAMPLED));
    }

    @Test
    void threadIsolation_mdcAndSpanHolder_doNotLeakAcrossThreads() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> t1 = () -> {
                SpanHolder.clear();
                MDC.clear();

                Span span = new TestSpan(new SpanContext(TRACE_ID_32, SPAN_ID_16, null, true), "A");
                SpanHolder.push(span);

                try (TraceMdcScope s = TraceMdcScope.fromCurrent()) {
                    return MDC.get(LogFields.Trace.SPAN_ID);
                } finally {
                    SpanHolder.pop();
                }
            };

            Callable<String> t2 = () -> {
                SpanHolder.clear();
                MDC.clear();

                Span span = new TestSpan(new SpanContext(TRACE_ID_32, "2222222222222222", null, false), "B");
                SpanHolder.push(span);

                try (TraceMdcScope s = TraceMdcScope.fromCurrent()) {
                    return MDC.get(LogFields.Trace.SPAN_ID) + ":" + MDC.get(LogFields.Trace.SAMPLED);
                } finally {
                    SpanHolder.pop();
                }
            };

            Future<String> f1 = pool.submit(t1);
            Future<String> f2 = pool.submit(t2);

            assertEquals(SPAN_ID_16, f1.get());
            assertEquals("2222222222222222:false", f2.get());
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------
    // Minimal Span stub that fully implements your Span interface
    // ---------------------------------------------------------------------
    static final class TestSpan implements Span {
        private final SpanContext ctx;
        private final SpanKind kind = SpanKind.INTERNAL;
        private volatile String name;
        private final long start = System.nanoTime();
        private volatile long end = -1L;
        private volatile boolean ended;

        private volatile SpanStatus status = SpanStatus.UNSET;
        private volatile ErrorKind errorKind;
        private volatile String errorCode;
        private volatile String errorType;
        private volatile String errorMessage;

        TestSpan(SpanContext ctx, String name) {
            this.ctx = ctx;
            this.name = name;
        }

        @Override public SpanContext context() { return ctx; }
        @Override public SpanKind kind() { return kind; }
        @Override public String name() { return name; }
        @Override public long startNanos() { return start; }
        @Override public long endNanos() { return end; }
        @Override public boolean isEnded() { return ended; }
        @Override public SpanStatus status() { return status; }
        @Override public ErrorKind errorKind() { return errorKind; }
        @Override public String errorCode() { return errorCode; }

        @Override public void setName(String name) { this.name = name; }

        @Override public void setStatus(SpanStatus status) {
            if (status != null) this.status = status;
        }

        @Override public void recordError(ErrorKind kind, String code) {
            this.errorKind = kind;
            this.errorCode = code;
            this.status = SpanStatus.ERROR;
        }

        @Override public void end() {
            if (ended) return;
            this.end = System.nanoTime();
            this.ended = true;
            if (this.status == SpanStatus.UNSET) this.status = SpanStatus.OK;
        }

        @Override
        public SpanScope activate() {
            SpanHolder.push(this);
            return () -> SpanHolder.pop();
        }

        @Override public void recordException(Throwable t) {
            if (t == null) return;
            this.errorType = t.getClass().getName();
            this.errorMessage = t.getMessage();
            this.status = SpanStatus.ERROR;
        }

        @Override public String errorType() { return errorType; }
        @Override public String errorMessage() { return errorMessage; }

        @Override public void setAttribute(String key, String value) {
            // no-op for test span
        }

        @Override
        public void setAttributes(MetricTags tags) {
            // keep default behavior OR no-op; either way is fine for this integration test
            Span.super.setAttributes(tags);
        }
    }
}
