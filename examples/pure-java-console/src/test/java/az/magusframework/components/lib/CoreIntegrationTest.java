package az.magusframework.components.lib;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.telemetry.TelemetryHelper;
import az.magusframework.components.lib.observability.core.tracing.span.SpanHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;


import az.magusframework.components.lib.observability.core.metrics.Outcome;
import org.junit.jupiter.api.DisplayName;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CoreIntegrationTest {

    @AfterEach
    void cleanup() {
        // Ensure no leakage between tests when run on same worker thread
        SpanHolder.clear();
    }

    @Test
    @DisplayName("SUCCESS: time() returns value and records timer with outcome=SUCCESS")
    void verifySuccessfulTelemetryFlow() {
        var recorder = new RecordingMetricsRecorder();
        var telemetry = new TelemetryHelper(recorder);

        String result = telemetry.time("user.fetch", MetricTags.empty(), () -> "MagusUser");

        assertThat(result).isEqualTo("MagusUser");

        var timers = recorder.eventsOf(RecordingMetricsRecorder.TimerEvent.class);
        assertThat(timers).hasSize(1);

        var t = timers.get(0);
        assertThat(t.metricName()).isEqualTo("user.fetch");
        assertThat(t.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(t.durationNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("ERROR: time() rethrows exception and records timer with outcome=TECHNICAL_ERROR")
    void verifyFailureTelemetryFlow() {
        var recorder = new RecordingMetricsRecorder();
        var telemetry = new TelemetryHelper(recorder);

        RuntimeException ex = new RuntimeException("Database timeout");

        assertThatThrownBy(() ->
                telemetry.time("order.process", MetricTags.empty(), () -> { throw ex; })
        ).isSameAs(ex);

        var timers = recorder.eventsOf(RecordingMetricsRecorder.TimerEvent.class);
        assertThat(timers).hasSize(1);

        var t = timers.get(0);
        assertThat(t.metricName()).isEqualTo("order.process");
        assertThat(t.outcome()).isEqualTo(Outcome.TECHNICAL_ERROR);
        assertThat(t.durationNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("NESTED: inner failure records inner+outer timers and SpanHolder is clean afterwards")
    void nestedFailure_recordsTwoTimers_andCleansThreadLocal() {
        var recorder = new RecordingMetricsRecorder();
        var telemetry = new TelemetryHelper(recorder);

        assertThat(SpanHolder.current()).isNull();

        assertThatThrownBy(() ->
                telemetry.time("outer", MetricTags.empty(), () ->
                        telemetry.time("inner", MetricTags.empty(), () -> {
                            throw new IllegalStateException("boom");
                        })
                )
        ).isInstanceOf(IllegalStateException.class);

        var timers = recorder.eventsOf(RecordingMetricsRecorder.TimerEvent.class);
        assertThat(timers).hasSize(2);

        assertThat(timers.get(0).metricName()).isEqualTo("inner");
        assertThat(timers.get(0).outcome()).isEqualTo(Outcome.TECHNICAL_ERROR);

        assertThat(timers.get(1).metricName()).isEqualTo("outer");
        assertThat(timers.get(1).outcome()).isEqualTo(Outcome.TECHNICAL_ERROR);

        assertThat(SpanHolder.current()).isNull();
    }

    @Test
    @DisplayName("Recorder SPI: counter/gauge/duration are captured")
    void recorderSpi_methods_areRecorded() {
        var recorder = new RecordingMetricsRecorder();

        recorder.incrementCounter("http.requests.total", MetricTags.empty());
        recorder.recordGauge("jvm.threads.live", MetricTags.empty(), 42.0);
        recorder.recordDuration("external.call", MetricTags.empty(), 123_000_000L);

        assertThat(recorder.eventsOf(RecordingMetricsRecorder.CounterEvent.class)).hasSize(1);
        assertThat(recorder.eventsOf(RecordingMetricsRecorder.GaugeEvent.class)).hasSize(1);
        assertThat(recorder.eventsOf(RecordingMetricsRecorder.DurationEvent.class)).hasSize(1);

        var c = recorder.eventsOf(RecordingMetricsRecorder.CounterEvent.class).get(0);
        assertThat(c.metricName()).isEqualTo("http.requests.total");

        var g = recorder.eventsOf(RecordingMetricsRecorder.GaugeEvent.class).get(0);
        assertThat(g.metricName()).isEqualTo("jvm.threads.live");
        assertThat(g.value()).isEqualTo(42.0);

        var d = recorder.eventsOf(RecordingMetricsRecorder.DurationEvent.class).get(0);
        assertThat(d.metricName()).isEqualTo("external.call");
        assertThat(d.durationNanos()).isEqualTo(123_000_000L);
    }
}
