package az.magusframework.components.lib.observability.otel;

import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.span.SpanFactory;
import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.tracing.span.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;

/**
 * <h1>OtelSpanFactory</h1>
 * <p>
 * The OpenTelemetry-backed implementation of the {@link SpanFactory} contract.
 * </p>
 *
 * <p>This factory bridges the gap between the Magus core identity model and the
 * OpenTelemetry SDK. It ensures that while the backend utilizes OTel's export
 * capabilities, the ID generation and tagging adhere to the Magus {@code LogFields}
 * and {@code TraceIdGenerator} standards.</p>
 *
 * <h3>Context Management:</h3>
 * <p>While OTel manages its own internal context, this factory integrates with
 * {@link SpanHolder} to ensure that Magus-managed spans correctly participate
 * in OTel traces.</p>
 *
 *
 */
public class OtelSpanFactory implements SpanFactory {

    private final Tracer tracer;
    private final TraceIdGenerator idGenerator;

    /**
     * Constructor with explicit OpenTelemetry injection.
     * Spring will provide both dependencies when backend=otel.
     */
    public OtelSpanFactory(
            TraceIdGenerator idGenerator,
            OpenTelemetry openTelemetry) {

        if (idGenerator == null) {
            throw new IllegalArgumentException("TraceIdGenerator must not be null");
        }
        if (openTelemetry == null) {
            throw new IllegalArgumentException("OpenTelemetry must not be null when backend=otel");
        }

        this.idGenerator = idGenerator;
        this.tracer = openTelemetry.getTracer("observability-lib", "1.0.0");
    }

    @Override
    public Span childSpan(String operation, MetricTags tags, TraceIdGenerator generator) {
        Span parent = SpanHolder.current();
        if (parent == null) {
            return null;
        }

        String traceId = parent.context().traceId();
        String parentSpanId = parent.context().spanId();

        return createOtelSpan(operation, tags, traceId, parentSpanId, SpanKind.INTERNAL);
    }

    @Override
    public Span httpRootSpan(String name, MetricTags tags, SpanContext inboundParent, TraceIdGenerator generator) {
        String traceId = (inboundParent != null)
                ? inboundParent.traceId()
                : generator.newTraceId();

        String parentSpanId = (inboundParent != null)
                ? inboundParent.spanId()
                : null;

        return createOtelSpan(name, tags, traceId, parentSpanId, SpanKind.SERVER);
    }

    private Span createOtelSpan(
            String operationName,
            MetricTags tags,
            String traceId,
            String parentSpanId,
            SpanKind kind) {

        String spanId = idGenerator.newSpanId();

        // Use real operation name for the span
        SpanBuilder builder = tracer.spanBuilder(
                operationName != null && !operationName.isBlank()
                        ? operationName
                        : "unnamed-operation"
        );

        // Inject custom IDs as attributes (low-cardinality)
        builder.setAttribute("custom.trace_id", traceId);
        builder.setAttribute("custom.span_id", spanId);
        if (parentSpanId != null) {
            builder.setAttribute("custom.parent_span_id", parentSpanId);
        }

        // Add all domain tags as OTel attributes
        if (tags != null) {
            tags.asList().forEach(tag -> {
                if (tag.key() != null && tag.value() != null) {
                    builder.setAttribute(tag.key(), tag.value());
                }
            });
        }

        io.opentelemetry.api.trace.Span otelSpan = builder.startSpan();

        return new OtelSpanAdapter(
                otelSpan,
                traceId,
                spanId,
                parentSpanId,
                kind,
                operationName
        );
    }
}
