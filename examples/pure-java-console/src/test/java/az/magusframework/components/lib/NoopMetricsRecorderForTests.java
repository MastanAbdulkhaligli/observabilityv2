package az.magusframework.components.lib;
import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.observability.core.metrics.Outcome;
import az.magusframework.components.lib.observability.core.metrics.TimerSample;
import az.magusframework.components.lib.observability.core.tags.MetricTags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NoopMetricsRecorderForTests implements MetricsRecorder {

    public static class DurationCall {
        public final String metric;
        public final Map<String, String> tags;
        public final long nanos;

        public DurationCall(String metric, Map<String, String> tags, long nanos) {
            this.metric = metric;
            this.tags = tags;
            this.nanos = nanos;
        }
    }

    private final List<DurationCall> durationCalls = new ArrayList<>();

    public List<DurationCall> durationCalls() {
        return durationCalls;
    }

    // -------------------------------------------------------
    // Timer support
    // -------------------------------------------------------

    @Override
    public TimerSample startTimer(String metricName) {
        return new TimerSample(metricName, System.nanoTime());
    }

    @Override
    public void stopTimer(TimerSample sample, MetricTags tags, Outcome outcome) {
        long nanos = System.nanoTime() - sample.startNanos();

        Map<String, String> tagMap = new LinkedHashMap<>();
        tags.asList().forEach(t -> tagMap.put(t.key(), t.value()));

        tagMap.put("outcome", outcome.name());

        durationCalls.add(new DurationCall(sample.metricName(), tagMap, nanos));
    }

    // -------------------------------------------------------
    // Optional metric APIs
    // -------------------------------------------------------

    @Override
    public void incrementCounter(String metricName, MetricTags tags) {
        // ignore for tests
    }

    @Override
    public void recordGauge(String metricName, MetricTags tags, double value) {
        // ignore for tests
    }

    @Override
    public void recordDuration(String metricName, MetricTags tags, long durationNanos) {
        Map<String, String> tagMap = new LinkedHashMap<>();
        tags.asList().forEach(t -> tagMap.put(t.key(), t.value()));
        durationCalls.add(new DurationCall(metricName, tagMap, durationNanos));
    }
}
