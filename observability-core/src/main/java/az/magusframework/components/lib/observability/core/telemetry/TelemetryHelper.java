package az.magusframework.components.lib.observability.core.telemetry;

import az.magusframework.components.lib.observability.core.error.ErrorClassifier;
import az.magusframework.components.lib.observability.core.error.ErrorInfo;
import az.magusframework.components.lib.observability.core.logging.LogFields;
import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.observability.core.metrics.Outcome;
import az.magusframework.components.lib.observability.core.metrics.TimerSample;
import az.magusframework.components.lib.observability.core.tags.MetricTag;
import az.magusframework.components.lib.observability.core.tags.MetricTags;

import java.util.Objects;

/**
 * <h1>TelemetryHelper</h1>
 * <p>
 * Orchestrates the lifecycle of an observed operation. This implementation ensures
 * total consistency between metrics and logging by utilizing canonical {@link LogFields}.
 * </p>
 */
public final class TelemetryHelper implements Telemetry {

    private final MetricsRecorder metricsRecorder;

    public TelemetryHelper(MetricsRecorder metricsRecorder) {
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder, "MetricsRecorder is required");
    }

    @Override
    public <T> T time(String metricName, MetricTags baseTags, CheckedSupplier<T> action) {
        Objects.requireNonNull(action, "Action cannot be null");

        final TimerSample sample = metricsRecorder.startTimer(metricName);
        final MetricTags tags = (baseTags != null) ? baseTags : MetricTags.empty();

        try {
            T result = action.get();
            recordCompletion(sample, tags, Outcome.SUCCESS, null);
            return result;

        } catch (Throwable t) {
            final Outcome failureOutcome = ErrorClassifier.determineOutcome(null, t);
            recordCompletion(sample, tags, failureOutcome, t);
            throw propagate(t);
        }
    }

    @Override
    public void increment(String metricName, MetricTags tags) {
        if (metricName == null || metricName.isBlank()) return;
        metricsRecorder.incrementCounter(metricName, tags != null ? tags : MetricTags.empty());
    }

    @Override
    public void recordDuration(String metricName, MetricTags tags, long durationNanos) {
        if (metricName == null || metricName.isBlank()) return;
        metricsRecorder.recordDuration(
                metricName,
                tags != null ? tags : MetricTags.empty(),
                Math.max(0, durationNanos)
        );
    }

    // --- Private Infrastructure ---

    /**
     * Records the final state of the operation.
     * Uses LogFields constants to ensure keys match the logging schema exactly.
     */
    private void recordCompletion(TimerSample sample, MetricTags baseTags, Outcome outcome, Throwable error) {
        // Use LogFields.Http.OUTCOME for the metric tag key
        MetricTags finalTags = baseTags.with(new MetricTag(LogFields.Http.OUTCOME, outcome.name()));

        if (error != null) {
            ErrorInfo info = ErrorClassifier.classify(error, null);

            // PROFESSIONAL STYLE: References canonical constants instead of magic strings
            finalTags = finalTags
                    .with(new MetricTag(LogFields.Error.KIND, info.kind().name()))
                    .with(new MetricTag(LogFields.Error.CODE, info.code()));
        }

        metricsRecorder.stopTimer(sample, finalTags, outcome);
    }

    private RuntimeException propagate(Throwable t) {
        if (t instanceof RuntimeException re) return re;
        if (t instanceof Error e) throw e;
        return new RuntimeException(t);
    }
}