package az.magusframework.components.lib.observability.core.tracing.span;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class SpanContextTest {

    @Test
    @DisplayName("Should create valid context and normalize input to lowercase")
    void shouldCreateValidContext() {
        var traceId = "4BF92F3577B34DA6A3CE929D0E0E4736"; // Mixed case
        var spanId = "00F067AA0BA902B7";

        var context = new SpanContext(traceId, spanId, null, true);

        assertThat(context.traceId()).isEqualTo(traceId.toLowerCase());
        assertThat(context.spanId()).isEqualTo(spanId.toLowerCase());
        assertThat(context.parentSpanId()).isNull();
        assertThat(context.sampled()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "too-short",
            "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", // Non-hex
            "00000000000000000000000000000000"  // All zeros (W3C invalid)
    })
    @DisplayName("Should throw exception for invalid Trace IDs")
    void shouldRejectInvalidTraceId(String invalidId) {
        assertThatThrownBy(() -> new SpanContext(invalidId, "00f067aa0ba902b7", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traceId");
    }

    @Test
    @DisplayName("Should reject Span ID if it is all zeros")
    void shouldRejectZeroSpanId() {
        assertThatThrownBy(() -> new SpanContext("4bf92f3577b34da6a3ce929d0e0e4736", "0000000000000000", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spanId");
    }
}