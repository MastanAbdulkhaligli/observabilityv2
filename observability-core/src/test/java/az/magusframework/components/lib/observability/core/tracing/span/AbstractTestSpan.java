package az.magusframework.components.lib.observability.core.tracing.span;

import az.magusframework.components.lib.observability.core.error.ErrorKind;

abstract class AbstractTestSpan implements Span {
    @Override public SpanContext context() { return null; }
    @Override public SpanKind kind() { return SpanKind.INTERNAL; }
    @Override public String name() { return null; }
    @Override public long startNanos() { return 0; }
    @Override public long endNanos() { return -1; }
    @Override public boolean isEnded() { return false; }
    @Override public SpanStatus status() { return SpanStatus.UNSET; }
    @Override public ErrorKind errorKind() { return null; }
    @Override public String errorCode() { return null; }
    @Override public void setName(String name) {}
    @Override public void setStatus(SpanStatus status) {}
    @Override public void recordError(ErrorKind kind, String code) {}
    @Override public void end() {}
    @Override public SpanScope activate() { return () -> {}; }
    @Override public void recordException(Throwable t) {}
    @Override public String errorType() { return null; }
    @Override public String errorMessage() { return null; }
    @Override public void setAttribute(String key, String value) {}
}
