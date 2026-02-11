package az.magusframework.components.lib.autoconfigure;

import az.magusframework.components.lib.observability.core.tracing.RandomTraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.Tracing;
import az.magusframework.components.lib.observability.core.tracing.span.SimpleSpanFactory;
import az.magusframework.components.lib.observability.core.tracing.span.SpanFactory;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnClass(Tracing.class)
public class ObservabilityTracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceIdGenerator traceIdGenerator() {
        return new RandomTraceIdGenerator();
    }

    // ---------- SIMPLE (default) ----------
    @Bean
    @ConditionalOnMissingBean(SpanFactory.class)
    @ConditionalOnProperty(prefix = "observability.tracing", name = "backend", havingValue = "simple", matchIfMissing = true)
    public SpanFactory simpleSpanFactory() {
        return new SimpleSpanFactory();
    }

    // ---------- OTEL (Option A) ----------
    @Bean
    @ConditionalOnMissingBean(SpanFactory.class)
    @ConditionalOnProperty(prefix = "observability.tracing", name = "backend", havingValue = "otel")
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnClass(name = "az.magusframework.components.lib.observability.otel.OtelSpanFactory")
    public SpanFactory otelSpanFactory(TraceIdGenerator traceIdGenerator, OpenTelemetry openTelemetry) {

        return newInstance(
                "az.magusframework.components.lib.observability.otel.OtelSpanFactory",
                SpanFactory.class,
                new Class<?>[]{TraceIdGenerator.class, OpenTelemetry.class},
                new Object[]{traceIdGenerator, openTelemetry}
        );
    }

    // Fail fast: backend=otel but missing OpenTelemetry bean
    @Bean
    @ConditionalOnProperty(prefix = "observability.tracing", name = "backend", havingValue = "otel")
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public MissingOtelForTracing missingOpenTelemetryForTracing() {
        return new MissingOtelForTracing();
    }

    public static final class MissingOtelForTracing {
        public MissingOtelForTracing() {
            throw new IllegalStateException(
                    "observability.tracing.backend=otel but no OpenTelemetry bean found. " +
                            "Provide an OpenTelemetry bean (agent/autoconfig/your config) or set observability.tracing.backend=simple."
            );
        }
    }

    // Register global span factory as soon as bean exists
//    @Bean
//    @ConditionalOnBean(SpanFactory.class)
//    public Object tracingInitializer(SpanFactory spanFactory) {
//        Tracing.setSpanFactory(spanFactory);
//        return new Object();
//    }

    @Bean
    @ConditionalOnBean(SpanFactory.class)
    public SmartInitializingSingleton tracingInitializer(SpanFactory spanFactory) {
        return () -> Tracing.setSpanFactory(spanFactory);
    }

    private static <T> T newInstance(String fqcn, Class<T> type, Class<?>[] ctorTypes, Object[] ctorArgs) {
        try {
            Class<?> c = Class.forName(fqcn);
            Object o = c.getDeclaredConstructor(ctorTypes).newInstance(ctorArgs);
            return type.cast(o);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate " + fqcn, e);
        }
    }
}
//@ConditionalOnClass(Tracing.class)
//@AutoConfiguration
//public class ObservabilityTracingAutoConfiguration {
//
//    @Bean
//    @ConditionalOnMissingBean
//    public TraceIdGenerator traceIdGenerator() {
//        return new RandomTraceIdGenerator();
//    }
//
//    @Bean
//    @ConditionalOnMissingBean
//    public SpanFactory spanFactory(ObservabilityProperties props, TraceIdGenerator traceIdGenerator) {
//
//        String backend = (props.getTracing().getBackend() == null) ? "simple" : props.getTracing().getBackend().toLowerCase();
//
//        // default: SIMPLE
//        if (!backend.equals("otel") && !backend.equals("opentelemetry")) {
//            return new SimpleSpanFactory();
//        }
//
//        // if OTEL selected, require OtelSpanFactory on classpath
//        try {
//            Class<?> c = Class.forName("az.magusframework.components.lib.observability.otel.OtelSpanFactory");
//            // constructor: (TraceIdGenerator, OpenTelemetry) -> you must have OpenTelemetry bean too
//            throw new IllegalStateException("backend=otel requires OpenTelemetry bean wiring (see next section)");
//        } catch (ClassNotFoundException e) {
//            throw new IllegalStateException("backend=otel but OtelSpanFactory not on classpath. Add observability-otel module.");
//        }
//    }
//
//    /**
//     * IMPORTANT: set the global factory once Spring context is ready.
//     * Using ApplicationReady avoids half-constructed beans order issues.
//     */
//    @EventListener(ApplicationReadyEvent.class)
//    public void registerSpanFactory(SpanFactory spanFactory) {
//        Tracing.setSpanFactory(spanFactory);
//    }
//}
