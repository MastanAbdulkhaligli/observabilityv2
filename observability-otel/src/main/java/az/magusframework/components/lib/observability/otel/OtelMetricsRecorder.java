package az.magusframework.components.lib.observability.otel;

import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.observability.core.metrics.Outcome;
import az.magusframework.components.lib.observability.core.metrics.TimerSample;
import az.magusframework.components.lib.observability.core.tags.MetricTag;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class OtelMetricsRecorder implements MetricsRecorder {

    public enum DurationUnit {
        NS("ns", 1d),
        MS("ms", 1_000_000d),
        S("s", 1_000_000_000d);

        private final String otelUnit;
        private final double nanosPerUnit;

        DurationUnit(String otelUnit, double nanosPerUnit) {
            this.otelUnit = otelUnit;
            this.nanosPerUnit = nanosPerUnit;
        }

        public String otelUnit() { return otelUnit; }

        public double fromNanos(long nanos) {
            long safe = Math.max(0L, nanos);
            return safe / nanosPerUnit;
        }
    }

    private final Meter meter;
    private final DurationUnit durationUnit;

    private final ConcurrentMap<String, LongCounter> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DoubleGauge> gauges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, DoubleHistogram> histograms = new ConcurrentHashMap<>();

    public OtelMetricsRecorder(OpenTelemetry openTelemetry) {
        this(openTelemetry, DurationUnit.MS);
    }

    public OtelMetricsRecorder(OpenTelemetry openTelemetry, DurationUnit durationUnit) {
        Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");

        // FIX 1: Correct Meter creation for current OTel API
        this.meter = openTelemetry.meterBuilder("observability-metrics")
                .setInstrumentationVersion("1.0.0")
                .build();

        this.durationUnit = (durationUnit == null) ? DurationUnit.MS : durationUnit;
    }

    @Override
    public TimerSample startTimer(String metricName) {
        return new TimerSample(normalizeMetricName(metricName),System.nanoTime());
    }

    @Override
    public void stopTimer(TimerSample sample, MetricTags tags, Outcome outcome) {
        if (sample == null) return;

        long durationNanos = System.nanoTime() - sample.startNanos();
        String metricName = normalizeMetricName(sample.metricName());

        MetricTags finalTags = (outcome == null)
                ? (tags == null ? MetricTags.empty() : tags)
                : (tags == null ? MetricTags.empty() : tags).with(new MetricTag("outcome", outcome.name()));

        recordDuration(metricName, finalTags, durationNanos);
    }

    @Override
    public void incrementCounter(String metricName, MetricTags tags) {
        String name = normalizeMetricName(metricName);
        LongCounter counter = counters.computeIfAbsent(name, this::createCounter);
        counter.add(1, toAttributes(tags));
    }

    @Override
    public void recordGauge(String metricName, MetricTags tags, double value) {
        String name = normalizeMetricName(metricName);
        DoubleGauge gauge = gauges.computeIfAbsent(name, this::createGauge);

        // FIX 2: Use .set() instead of .record() for Synchronous Gauges
        gauge.set(value, toAttributes(tags));
    }

    @Override
    public void recordDuration(String metricName, MetricTags tags, long durationNanos) {
        String name = normalizeMetricName(metricName);
        DoubleHistogram hist = histograms.computeIfAbsent(name, this::createHistogram);

        double v = durationUnit.fromNanos(durationNanos);
        hist.record(v, toAttributes(tags));
    }

    // -------------------- instrument builders --------------------

    private LongCounter createCounter(String metricName) {
        return meter.counterBuilder(metricName)
                .setDescription(metricName + " count")
                .setUnit("1")
                .build();
    }

    private DoubleGauge createGauge(String metricName) {
        // This creates a Synchronous Gauge (Standard OTel SDK)
        return meter.gaugeBuilder(metricName)
                .setDescription(metricName + " gauge")
                .setUnit("1")
                .build();
    }

    private DoubleHistogram createHistogram(String metricName) {
        return meter.histogramBuilder(metricName)
                .setDescription(metricName + " duration")
                .setUnit(durationUnit.otelUnit())
                .build();
    }

    // -------------------- tags -> attributes --------------------

    private static Attributes toAttributes(MetricTags tags) {
        AttributesBuilder b = Attributes.builder();
        if (tags == null) return b.build();

        for (MetricTag t : tags.asList()) {
            if (t == null) continue;
            String k = sanitizeKey(t.key());
            String v = sanitizeValue(t.value());
            if (k == null || v == null) continue;
            b.put(AttributeKey.stringKey(k), v);
        }
        return b.build();
    }

    // -------------------- sanitation helpers --------------------

    private static String normalizeMetricName(String metricName) {
        String n = (metricName == null) ? "" : metricName.trim();
        if (n.isEmpty()) return "unknown.metric";
        n = n.replace(' ', '_');
        if (n.length() > 200) n = n.substring(0, 200);
        return n;
    }

    private static String sanitizeKey(String key) {
        if (key == null) return null;
        String k = key.trim();
        if (k.isEmpty()) return null;
        k = k.replace(' ', '_').toLowerCase(Locale.ROOT);
        if (k.length() > 80) k = k.substring(0, 80);
        return k;
    }

    private static String sanitizeValue(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > 120) v = v.substring(0, 120);
        return v;
    }
}
