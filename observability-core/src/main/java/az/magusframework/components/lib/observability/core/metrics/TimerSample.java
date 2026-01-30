package az.magusframework.components.lib.observability.core.metrics;

import java.util.Objects;

/**
 * <h1>TimerSample</h1>
 * <p>
 * Captures the starting state of a timed operation. This object acts as a
 * handle that must be passed back to the {@link MetricsRecorder} to finalize
 * a latency measurement.
 * </p>
 *
 * <p><b>Performance Note:</b> As a Record, this class is a lightweight
 * data carrier. It is intended to be short-lived and is highly suitable
 * for stack allocation by the JVM (Escape Analysis).</p>
 *
 * @param metricName The canonical name of the metric being measured.
 * @param startNanos The monotonic start time in nanoseconds, captured via {@code System.nanoTime()}.
 */
public record TimerSample(String metricName, long startNanos) {

    /**
     * Canonical constructor with validation to ensure the sample is always valid.
     * * @throws NullPointerException if metricName is null.
     */
    public TimerSample {
        Objects.requireNonNull(metricName, "metricName cannot be null for a TimerSample");

        if (startNanos < 0) {
            throw new IllegalArgumentException("startNanos cannot be negative");
        }
    }

    /**
     * Utility method to calculate elapsed time relative to this sample.
     * * @return Duration in nanoseconds since the sample was started.
     */
    public long elapsedNanos() {
        return System.nanoTime() - startNanos;
    }
}