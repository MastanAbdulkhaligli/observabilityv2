package az.magusframework.components.lib.observability.core.metrics;

import az.magusframework.components.lib.observability.core.tags.MetricTags;

/**
 * <h1>MetricsRecorder</h1>
 * <p>
 * The central SPI (Service Provider Interface) for metric collection within the Magus Framework.
 * This interface abstracts the underlying metrics engine (e.g., Micrometer, OpenTelemetry, Prometheus).
 * </p>
 *
 * <p><b>Implementation Guidelines:</b></p>
 * <ul>
 * <li><b>Thread Safety:</b> All implementations MUST be thread-safe.</li>
 * <li><b>Cardinality:</b> Implementations should handle high-cardinality tag protection if possible,
 * though the framework aims to provide low-cardinality data.</li>
 * <li><b>No-op Behavior:</b> If a metric backend is unavailable, methods should fail silently
 * to prevent application crashes.</li>
 * </ul>
 */
public interface MetricsRecorder {

    /**
     * Starts a monotonic timer for measuring latency.
     *
     * @param metricName The canonical name of the metric (e.g., "http.server.requests").
     * @return A {@link TimerSample} capturing the start state.
     */
    TimerSample startTimer(String metricName);

    /**
     * Records the duration of a timed operation.
     *
     * @param sample  The initial sample created via {@link #startTimer(String)}.
     * @param tags    Metadata tags for dimensional analysis.
     * @param outcome The final result of the operation for success/failure tracking.
     */
    void stopTimer(TimerSample sample, MetricTags tags, Outcome outcome);

    /**
     * Increments a monotonic counter (e.g., total requests, error count).
     *
     * @param metricName The name of the counter.
     * @param tags       Metadata tags.
     */
    void incrementCounter(String metricName, MetricTags tags);

    /**
     * Records an instantaneous value (e.g., current thread count, memory usage).
     *
     * @param metricName The name of the gauge.
     * @param tags       Metadata tags.
     * @param value      The current value.
     */
    void recordGauge(String metricName, MetricTags tags, double value);

    /**
     * Records a pre-calculated duration. Useful for async operations or
     * external timing sources.
     *
     * @param metricName    The name of the distribution summary or timer.
     * @param tags          Metadata tags.
     * @param durationNanos Precise duration in nanoseconds.
     */
    void recordDuration(String metricName, MetricTags tags, long durationNanos);
}