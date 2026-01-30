package az.magusframework.components.lib;

import az.magusframework.components.lib.observability.core.logging.LogFields;
import az.magusframework.components.lib.observability.core.tracing.span.Span;
import az.magusframework.components.lib.observability.core.tracing.span.SpanContext;
import az.magusframework.components.lib.observability.logging.LoggingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
public class LoggingJsonMdcIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LoggingJsonMdcIntegrationTest.class);

    @AfterEach
    void cleanup() {
        LoggingContext.clearAll();
    }

    @Test
    void jsonLog_containsMdcFields_whenProvided() {
        // 1) Put trace
        SpanContext ctx = new SpanContext(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "00f067aa0ba902b7",
                "1122334455667788",
                true
        );

        // If your Span is an interface/abstract type, just call putTraceContext(ctx) directly
        LoggingContext.putTraceContext(ctx);

        // 2) Put service metadata
        LoggingContext.putServiceMetadata("demo-service", "pure-java-console", "example", "test");

        // 3) Put correlation
        LoggingContext.putCorrelationIds("req-1", "bag=1");

        // 4) Emit log
        log.info("hello from test with mdc");

        // 5) Assert MDC (this validates your library behavior independent of JSON encoder)
        assertThat(MDC.get(LogFields.Trace.TRACE_ID)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(MDC.get(LogFields.Trace.SPAN_ID)).isEqualTo("00f067aa0ba902b7");
        assertThat(MDC.get(LogFields.Trace.PARENT_SPAN_ID)).isEqualTo("1122334455667788");
        assertThat(MDC.get(LogFields.Trace.SAMPLED)).isEqualTo("true");

        assertThat(MDC.get(LogFields.Service.NAME)).isEqualTo("demo-service");
        assertThat(MDC.get(LogFields.Correlation.REQUEST_ID)).isEqualTo("req-1");
    }
}