package az.magusframework.components.lib;


import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.Tracing;
import az.magusframework.components.lib.observability.core.tracing.span.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class TestTracingBackend {

    private final AtomicInteger seq = new AtomicInteger(0);
    private final List<TestSpan> spans = new CopyOnWriteArrayList<>();

    public List<TestSpan> spans() { return spans; }
    public TestSpan last() { return spans.isEmpty() ? null : spans.get(spans.size() - 1); }

    public SpanFactory asFactory() {
        return new SpanFactory() {
            @Override public Span childSpan(String operation, MetricTags tags, TraceIdGenerator gen) {
                return create(operation, gen);
            }
            @Override public Span httpRootSpan(String name, MetricTags tags, SpanContext inboundParent, TraceIdGenerator gen) {
                return create(name, gen);
            }
        };
    }

    private TestSpan create(String operation, TraceIdGenerator gen) {
        Span parent = SpanHolder.current();
        String traceId = (parent != null) ? parent.context().traceId() : gen.newTraceId();
        String parentId = (parent != null) ? parent.context().spanId() : null;

        String spanId = String.format("%016x", seq.incrementAndGet());
        TestSpan s = new TestSpan(operation, new SpanContext(traceId, spanId, parentId, true));
        spans.add(s);
        return s;
    }

    public static final class Installer {
        private static Object previous;
        private static Field installedField;

        public static void install(SpanFactory factory) {
            try {
                for (String fieldName : List.of("spanFactory", "factory", "INSTANCE", "current")) {
                    try {
                        Field f = Tracing.class.getDeclaredField(fieldName);
                        f.setAccessible(true);
                        if (!SpanFactory.class.isAssignableFrom(f.getType())) continue;

                        previous = f.get(null);
                        installedField = f;
                        f.set(null, factory);
                        return;
                    } catch (NoSuchFieldException ignored) {}
                }
                throw new IllegalStateException("Cannot install test SpanFactory into Tracing (field not found)");
            } catch (Exception e) {
                throw new RuntimeException("Failed to install test tracing backend", e);
            }
        }

        public static void uninstallBestEffort() {
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

    public static final class TestSpan implements Span {
        private final String name;
        private final SpanContext ctx;

        private boolean ended;
        private SpanStatus status = SpanStatus.UNSET;

        private az.magusframework.components.lib.observability.core.error.ErrorKind errorKind;
        private String errorCode;
        private Throwable exception;

        private final long start = System.nanoTime();
        private long end = -1;

        public TestSpan(String name, SpanContext ctx) {
            this.name = name;
            this.ctx = ctx;
        }

        @Override public SpanContext context() { return ctx; }
        @Override public SpanKind kind() { return SpanKind.INTERNAL; }
        @Override public String name() { return name; }
        @Override public void setName(String name) {}
        @Override public long startNanos() { return start; }
        @Override public long endNanos() { return end; }
        @Override public boolean isEnded() { return ended; }
        @Override public SpanStatus status() { return status; }
        @Override public az.magusframework.components.lib.observability.core.error.ErrorKind errorKind() { return errorKind; }
        @Override public String errorCode() { return errorCode; }
        @Override public void setStatus(SpanStatus s) { this.status = s; }

        @Override public void recordError(az.magusframework.components.lib.observability.core.error.ErrorKind kind, String code) {
            this.errorKind = kind;
            this.errorCode = code;
        }

        @Override public void recordException(Throwable t) { this.exception = t; }

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

    public TestTracingBackend() {}
}
