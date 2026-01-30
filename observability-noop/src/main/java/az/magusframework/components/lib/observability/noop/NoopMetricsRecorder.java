package az.magusframework.components.lib.observability.noop;

import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.observability.core.metrics.Outcome;
import az.magusframework.components.lib.observability.core.metrics.TimerSample;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * <h1>NoopMetricsRecorder</h1>
 * <p>
 * A development/testing implementation that logs metric events to the console
 * and SLF4J instead of shipping them to a real monitoring backend.
 * </p>
 */
public class NoopMetricsRecorder implements MetricsRecorder {

    private static final Logger log = LoggerFactory.getLogger(NoopMetricsRecorder.class);

    @Override
    public TimerSample startTimer(String metricName) {
        System.out.println("**********");
        System.out.println("startTimer");

        // Uses the v2 Record constructor
        TimerSample sample = new TimerSample(metricName, System.nanoTime());

        log.info("NoopMetricsRecorder.startTimer metric={}", metricName);
        return sample;
    }

    @Override
    public void stopTimer(TimerSample sample, MetricTags tags, Outcome outcome) {
        System.out.println("**********");
        System.out.println("stopTimer");

        long durationNanos = -1L;
        String metric = "unknown";

        if (sample != null) {
            // Updated to use v2 Record accessors and utility
            durationNanos = sample.elapsedNanos();
            metric = sample.metricName();
        }

        log.info("NoopMetricsRecorder.stopTimer metric={} outcome={} durationMs={} tags={}",
                metric,
                outcome,
                (durationNanos >= 0 ? durationNanos / 1_000_000.0 : null),
                tags);

        if (tags != null) {
            for (var t : tags.asList()) {
                // Behaviour preserved: loop exists but logging is commented out as per your original
                // log.info("TAG {}={}", t.key(), t.value());
            }
        }
    }

    @Override
    public void incrementCounter(String metricName, MetricTags tags) {
        System.out.println("**********");
        System.out.println("incrementCounter");
        log.info("NoopMetricsRecorder.incrementCounter metric={} tags={}", metricName, tags);
    }

    @Override
    public void recordGauge(String metricName, MetricTags tags, double value) {
        System.out.println("**********");
        System.out.println("recordGauge");
        log.info("NoopMetricsRecorder.recordGauge metric={} value={} tags={}",
                metricName, value, tags);
    }

    @Override
    public void recordDuration(String metricName, MetricTags tags, long durationNanos) {
        System.out.println("**********");
        System.out.println("recordDuration");

        String effectiveMetricName = (metricName == null || metricName.isBlank()) ? "unknown" : metricName;
        long effectiveDuration = (durationNanos < 0) ? 0 : durationNanos;

        log.info("NoopMetricsRecorder.recordDuration metric={} durationMs={} durationNanos={} tags={}",
                effectiveMetricName,
                effectiveDuration / 1_000_000.0,
                effectiveDuration,
                tags);

        if (tags != null) {
            for (var t : tags.asList()) {
                // Behaviour preserved
            }
        }
    }
}