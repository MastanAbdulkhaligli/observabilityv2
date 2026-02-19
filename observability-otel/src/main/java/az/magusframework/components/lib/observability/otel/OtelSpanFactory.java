package az.magusframework.components.lib.observability.otel;

import az.magusframework.components.lib.observability.core.tags.MetricTags;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.span.Span;
import az.magusframework.components.lib.observability.core.tracing.span.SpanContext;
import az.magusframework.components.lib.observability.core.tracing.span.SpanFactory;
import az.magusframework.components.lib.observability.core.tracing.span.SpanHolder;
import az.magusframework.components.lib.observability.core.tracing.span.SpanKind;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.Objects;

public class OtelSpanFactory implements SpanFactory {

    private final Tracer tracer;

    public OtelSpanFactory(
            TraceIdGenerator idGenerator, // kept for signature compatibility (not used for OTel IDs)
            OpenTelemetry openTelemetry
    ) {
        if (openTelemetry == null) {
            throw new IllegalArgumentException("OpenTelemetry must not be null when backend=otel");
        }
        // idGenerator intentionally not used for IDs in OTel mode
        this.tracer = openTelemetry.getTracer("observability-lib", "1.0.0");
    }

    @Override
    public Span childSpan(String operation, MetricTags tags, TraceIdGenerator generator) {
        Span parent = SpanHolder.current();
        if (parent == null) return null;

        // In OTel mode: parent is already current when your code uses activate()
        // So we just start a child span using current context.
        return createOtelSpanWithCurrentParent(operation, tags, SpanKind.INTERNAL, parent.context());
    }

    @Override
    public Span httpRootSpan(String name, MetricTags tags, SpanContext inboundParent, TraceIdGenerator generator) {
        // If inbound parent exists => set REMOTE parent so trace continues.
        // If not => setNoParent so new trace starts.
        return createOtelServerSpan(name, tags, inboundParent);
    }

    // =========================================================
    // Core creation
    // =========================================================

    private Span createOtelServerSpan(String operationName, MetricTags tags, SpanContext inboundParent) {
        SpanBuilder builder = tracer.spanBuilder(safeName(operationName));

        // If you want: builder.setSpanKind(io.opentelemetry.api.trace.SpanKind.SERVER);
        // (Not required for traceparent correctness, but good practice.)

        // IMPORTANT: make parent explicit. If missing => new trace.
        if (isValid(inboundParent)) {
            io.opentelemetry.api.trace.SpanContext remoteParent = toRemoteOtelParent(inboundParent);
            builder.setParent(Context.root().with(io.opentelemetry.api.trace.Span.wrap(remoteParent)));
        } else {
            builder.setNoParent();
        }

        applyTags(builder, tags);

        io.opentelemetry.api.trace.Span otelSpan = builder.startSpan();
        io.opentelemetry.api.trace.SpanContext sc = otelSpan.getSpanContext();

        String parentSpanId = isValid(inboundParent) ? inboundParent.spanId() : null;

        return new OtelSpanAdapter(
                otelSpan,
                sc.getTraceId(),
                sc.getSpanId(),
                parentSpanId,
                sc.isSampled(),
                SpanKind.SERVER,
                operationName
        );
    }

    private Span createOtelSpanWithCurrentParent(
            String operationName,
            MetricTags tags,
            SpanKind kind,
            SpanContext magusParentContextOrNull
    ) {
        SpanBuilder builder = tracer.spanBuilder(safeName(operationName));
        // Child of CURRENT OTel context (because parent span activate() called delegate.makeCurrent()).
        // So do NOT setNoParent here.

        applyTags(builder, tags);

        io.opentelemetry.api.trace.Span otelSpan = builder.startSpan();
        io.opentelemetry.api.trace.SpanContext sc = otelSpan.getSpanContext();

        String parentSpanId = (magusParentContextOrNull != null) ? magusParentContextOrNull.spanId() : null;

        return new OtelSpanAdapter(
                otelSpan,
                sc.getTraceId(),
                sc.getSpanId(),
                parentSpanId,
                sc.isSampled(),
                kind,
                operationName
        );
    }

    // =========================================================
    // Helpers
    // =========================================================

    private static void applyTags(SpanBuilder builder, MetricTags tags) {
        if (tags == null) return;
        tags.asList().forEach(tag -> {
            if (tag.key() != null && tag.value() != null) {
                builder.setAttribute(tag.key(), tag.value());
            }
        });
    }

    private static String safeName(String operationName) {
        if (operationName == null) return "unnamed-operation";
        String s = operationName.trim();
        return s.isEmpty() ? "unnamed-operation" : s;
    }

    private static boolean isValid(SpanContext p) {
        if (p == null) return false;
        String tid = p.traceId();
        String sid = p.spanId();
        return tid != null && !tid.isBlank() && sid != null && !sid.isBlank();
    }

    private static io.opentelemetry.api.trace.SpanContext toRemoteOtelParent(SpanContext inboundParent) {
        Objects.requireNonNull(inboundParent, "inboundParent");

        TraceFlags flags = inboundParent.sampled()
                ? TraceFlags.getSampled()
                : TraceFlags.getDefault();

        // tracestate not supported in your SpanContext model; default OK.
        return io.opentelemetry.api.trace.SpanContext.createFromRemoteParent(
                inboundParent.traceId(),
                inboundParent.spanId(),
                flags,
                TraceState.getDefault()
        );
    }
}
