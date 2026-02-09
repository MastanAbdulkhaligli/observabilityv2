package az.magusframework.components.lib.aspectj.exacution;

import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.aspectj.metadata.InvocationMetadata;
import az.magusframework.components.lib.aspectj.policy.LayerObservationPolicy;
import az.magusframework.components.lib.aspectj.policy.LayerPolicyRegistry;
import az.magusframework.components.lib.aspectj.support.MdcScope;
import az.magusframework.components.lib.aspectj.timing.ExclusiveTimingStack;
import az.magusframework.components.lib.observability.core.error.ErrorClassifier;
import az.magusframework.components.lib.observability.core.error.ErrorInfo;
import az.magusframework.components.lib.observability.core.error.ErrorKind;
import az.magusframework.components.lib.observability.core.metrics.Outcome;
import az.magusframework.components.lib.observability.core.tags.MetricTag;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.tracing.Tracing;
import az.magusframework.components.lib.observability.core.tracing.span.Span;
import az.magusframework.components.lib.observability.core.tracing.span.SpanScope;
import az.magusframework.components.lib.observability.core.tracing.span.SpanStatus;
import az.magusframework.components.lib.observability.logging.LoggingContext;
import az.magusframework.components.lib.observability.tracing.TraceMdcScope;
import org.aspectj.lang.ProceedingJoinPoint;

import java.util.Objects;

public final class ObservationExecutor {

    private static final String METRIC_DURATION = "obs.layer.duration";
    private static final String METRIC_EXCLUSIVE_DURATION = "obs.layer.exclusive_duration";

    private static final String TAG_OUTCOME = "outcome";
    private static final String TAG_ERROR_KIND = "error_kind";
    private static final String TAG_ERROR_CODE = "error_code";

    private ObservationExecutor() {}

    public static Object observe(
            ProceedingJoinPoint pjp,
            InvocationMetadata meta,
            MetricTags baseTags
    ) throws Throwable {

        Objects.requireNonNull(pjp, "pjp is required");
        Objects.requireNonNull(meta, "meta is required");
        Objects.requireNonNull(baseTags, "baseTags is required");

        LayerObservationPolicy policy = LayerPolicyRegistry.policyFor(meta.layer());

        if (!policy.createSpan() && !policy.recordMetrics()) {
            return pjp.proceed();
        }

        ExclusiveTimingStack.Scope timingScope = null;
        if (policy.recordMetrics()) {
            timingScope = ExclusiveTimingStack.enter();
        }

        Span span = null;
        if (policy.createSpan()) {
            span = Tracing.get().childSpan(meta.operation(), baseTags, ObservabilityBootstrap.traceIdGen());
        }

        if (span == null && !policy.recordMetrics()) {
            return pjp.proceed();
        }

        LoggingContext.clearErrorOnly();

        Outcome operationOutcome = Outcome.SUCCESS;
        ErrorInfo errorInfo = null;
        Throwable thrown = null;

        try (
                TraceMdcScope traceMdcScope = (span != null) ? TraceMdcScope.set(span) : null;
                SpanScope spanScope = (span != null) ? span.activate() : null;
                MdcScope mdcScope = new MdcScope(
                        ObservabilityBootstrap.serviceName(),
                        meta.layer().name(),
                        meta.operation()
                )
        ) {
            try {
                return pjp.proceed();

            } catch (Throwable e) {
                thrown = e;

                // ------------------------------
                // CLASSIFICATION (NULL SAFE)
                // ------------------------------
                errorInfo = ErrorClassifier.classify(e, null);

                if (errorInfo == null) {
                    errorInfo = new ErrorInfo(
                            ErrorKind.TECHNICAL,
                            "UNCLASSIFIED",
                            e.getClass().getSimpleName()
                    );
                }

                // outcome mapping
                operationOutcome = (errorInfo.kind() == ErrorKind.BUSINESS)
                        ? Outcome.BUSINESS_ERROR
                        : Outcome.TECHNICAL_ERROR;

                if (span != null) {
                    span.recordError(errorInfo.kind(), errorInfo.code());
                    span.recordException(e);
                    span.setStatus(SpanStatus.ERROR);
                }

                LoggingContext.putErrorFromThrowableIfAbsent(e, meta.layer().name());
                LoggingContext.putErrorKindAndCode(errorInfo.kind().name(), errorInfo.code());

                throw e;
            }

        } finally {
            recordLayerDurationsIfEnabled(policy, timingScope, baseTags, operationOutcome, errorInfo);

            if (span != null) {
                if (operationOutcome == Outcome.SUCCESS) {
                    span.setStatus(SpanStatus.OK);
                }
                span.end();
            }
        }
    }

    private static void recordLayerDurationsIfEnabled(
            LayerObservationPolicy policy,
            ExclusiveTimingStack.Scope timingScope,
            MetricTags baseTags,
            Outcome outcome,
            ErrorInfo errorInfoOrNull
    ) {
        if (!policy.recordMetrics() || timingScope == null) {
            return;
        }

        timingScope.close();
        ExclusiveTimingStack.Result r = timingScope.result();

        MetricTags tags = baseTags.with(new MetricTag(TAG_OUTCOME, outcome.name()));

        if (errorInfoOrNull != null) {
            tags = tags
                    .with(new MetricTag(TAG_ERROR_KIND, errorInfoOrNull.kind().name()))
                    .with(new MetricTag(TAG_ERROR_CODE, errorInfoOrNull.code()));
        }

        ObservabilityBootstrap.telemetry()
                .recordDuration(METRIC_DURATION, tags, r.inclusiveNanos);

        ObservabilityBootstrap.telemetry()
                .recordDuration(METRIC_EXCLUSIVE_DURATION, tags, r.exclusiveNanos);
    }
}
