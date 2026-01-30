//package az.magusframework.components.lib.observability.core;
//
//import az.magusframework.components.lib.observability.core.error.ErrorClassifier;
//import az.magusframework.components.lib.observability.core.telemetry.TelemetryHelper;
//import az.magusframework.components.lib.observability.core.tracing.span.SimpleSpanFactory;
//import org.junit.jupiter.api.Test;
//
//public class CoreIntegrationTest {
//
//    @Test
//    void verifyEndToEndTraceAndMetrics() {
//        // 1. Setup the "Magus" Environment
//        var recorder = new NoopMetricsRecorder();
//        var factory = new SimpleSpanFactory();
//        var helper = new TelemetryHelper(recorder, factory, new ErrorClassifier());
//
//        // 2. Simulate a nested business operation
//        helper.execute("parent-op", MetricTags.empty(), () -> {
//
//            // Inside the parent, we should have a Span
//            assertThat(SpanHolder.current()).isNotNull();
//            assertThat(SpanHolder.current().name()).isEqualTo("parent-op");
//
//            // Execute a child operation
//            return helper.execute("child-op", MetricTags.empty(), () -> {
//                assertThat(SpanHolder.current().name()).isEqualTo("child-op");
//                return "Success";
//            });
//        });
//
//        // 3. Verify cleanup
//        assertThat(SpanHolder.current()).isNull();
//    }
//}