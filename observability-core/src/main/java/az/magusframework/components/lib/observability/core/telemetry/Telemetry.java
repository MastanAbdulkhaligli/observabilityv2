package az.magusframework.components.lib.observability.core.telemetry;

import az.magusframework.components.lib.observability.core.tags.MetricTags;

/**
 * <h1>Telemetry</h1>
 * <p>
 * The high-level API for instrumenting application logic. This interface provides
 * convenient methods to capture latency, throughput, and custom durations while
 * automatically managing error classification and outcome tracking.
 * </p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * return telemetry.execute("order.process.duration", tags, () -> {
 * return orderService.process(order);
 * });
 * }</pre>
 */
public interface Telemetry {

    /**
     * Executes a code block and records its execution time.
     * <p>
     * This method automatically handles success and failure outcomes,
     * classifying exceptions into {@code BUSINESS_ERROR} or {@code TECHNICAL_ERROR}
     * based on the framework's core policy.
     * </p>
     *
     * @param metricName The name of the duration metric to record.
     * @param tags       Metadata dimensions for the metric.
     * @param action     The business logic to be timed.
     * @param <T>        The return type of the action.
     * @return The result of the action.
     * @throws RuntimeException if the action throws a checked or unchecked exception.
     */
    <T> T time(String metricName, MetricTags tags, CheckedSupplier<T> action);

    /**
     * Increments a monotonic counter (e.g., "messages.processed", "login.attempts").
     *
     * @param metricName The name of the counter.
     * @param tags       Metadata dimensions for the metric.
     */
    void increment(String metricName, MetricTags tags);

    /**
     * Records a specific duration.
     * Useful for async operations or timing events where the start time was
     * captured outside of this thread.
     *
     * @param metricName    The name of the duration metric.
     * @param tags          Metadata dimensions for the metric.
     * @param durationNanos Precise duration in nanoseconds.
     */
    void recordDuration(String metricName, MetricTags tags, long durationNanos);
}