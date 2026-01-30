package az.magusframework.components.lib;

import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.observability.core.metrics.Outcome;
import az.magusframework.components.lib.observability.core.metrics.TimerSample;
import az.magusframework.components.lib.observability.core.tags.MetricTags;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class RecordingMetricsRecorder implements MetricsRecorder {

    // ----------------------------
    // Recorded events (assertable)
    // ----------------------------

    public sealed interface Event permits TimerEvent, CounterEvent, GaugeEvent, DurationEvent {}

    public record TimerEvent(
            String metricName,
            MetricTags tags,
            Outcome outcome,
            long durationNanos
    ) implements Event {}

    public record CounterEvent(
            String metricName,
            MetricTags tags
    ) implements Event {}

    public record GaugeEvent(
            String metricName,
            MetricTags tags,
            double value
    ) implements Event {}

    public record DurationEvent(
            String metricName,
            MetricTags tags,
            long durationNanos
    ) implements Event {}

    private final List<Event> events = new CopyOnWriteArrayList<>();

    // ----------------------------
    // SPI methods
    // ----------------------------

    @Override
    public TimerSample startTimer(String metricName) {
        // TimerSample validates metricName != null and startNanos >= 0
        return new TimerSample(metricName, System.nanoTime());
    }

    @Override
    public void stopTimer(TimerSample sample, MetricTags tags, Outcome outcome) {
        // SPI guideline says: fail silently if backend unavailable / unexpected
        if (sample == null) return;

        long duration = Math.max(0L, sample.elapsedNanos());
        events.add(new TimerEvent(sample.metricName(), tags, outcome, duration));
    }

    @Override
    public void incrementCounter(String metricName, MetricTags tags) {
        events.add(new CounterEvent(metricName, tags));
    }

    @Override
    public void recordGauge(String metricName, MetricTags tags, double value) {
        events.add(new GaugeEvent(metricName, tags, value));
    }

    @Override
    public void recordDuration(String metricName, MetricTags tags, long durationNanos) {
        events.add(new DurationEvent(metricName, tags, Math.max(0L, durationNanos)));
    }

    // ----------------------------
    // Test helpers
    // ----------------------------

    public List<Event> events() {
        return events;
    }

    public <T extends Event> List<T> eventsOf(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public TimerEvent lastTimer() {
        List<TimerEvent> timers = eventsOf(TimerEvent.class);
        if (timers.isEmpty()) {
            throw new AssertionError("No TimerEvent recorded");
        }
        return timers.get(timers.size() - 1);
    }

    public void clear() {
        events.clear();
    }
}
