package az.magusframework.components.lib.aspectj.error;

import az.magusframework.components.lib.observability.core.error.ErrorClassifier;
import az.magusframework.components.lib.observability.core.error.ErrorInfo;
import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.tags.Layer;
import az.magusframework.components.lib.observability.core.tracing.span.Span;
import az.magusframework.components.lib.observability.core.tracing.span.SpanHolder;
import az.magusframework.components.lib.observability.core.tracing.span.SpanStatus;
import az.magusframework.components.lib.observability.logging.LoggingContext;

public final class ErrorObservation {

    private ErrorObservation() {}

    public static void observe(Throwable error, Layer layer) {

        ErrorInfo info = ErrorClassifier.classify(error, null);

        // ---- logging context ----
        LoggingContext.putErrorFromThrowableIfAbsent(
                error,
                layer != null ? layer.name() : null
        );

        LoggingContext.putErrorKindAndCode(
                info.kind() != null ? info.kind().name() : null,
                info.code()
        );

        // ---- span status (NEW: on-span, not ThreadLocal side channel) ----
        Span current = SpanHolder.current();
        if (current != null) {
            if (info.kind() == ErrorKind.TECHNICAL) {
                current.recordError(info.kind(), info.code());
            } else {
                current.setStatus(SpanStatus.OK);
            }
        }
    }
}
