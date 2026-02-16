package az.magusframework.components.lib.autoconfigure;

import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityMetricsAutoConfiguration {

    // ---------- NOOP ----------

    @Bean
    @ConditionalOnMissingBean(MetricsRecorder.class)
    @ConditionalOnProperty(prefix = "observability.metrics", name = "backend", havingValue = "noop", matchIfMissing = true)
    @ConditionalOnClass(name = "az.magusframework.components.lib.observability.noop.NoopMetricsRecorder")
    public MetricsRecorder noopMetricsRecorder() {
        return newInstance(
                "az.magusframework.components.lib.observability.noop.NoopMetricsRecorder",
                MetricsRecorder.class
        );
    }

    // ---------- OTEL ----------

    @Bean
    @ConditionalOnMissingBean(MetricsRecorder.class)
    @ConditionalOnProperty(prefix = "observability.metrics", name = "backend", havingValue = "otel")
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnClass(name = "az.magusframework.components.lib.observability.otel.OtelMetricsRecorder")
    public MetricsRecorder otelMetricsRecorder(OpenTelemetry openTelemetry, ObservabilityProperties props) {
        String unitStr = null;
        if (props != null && props.getMetrics() != null && props.getMetrics().getOtel() != null) {
            unitStr = props.getMetrics().getOtel().getDurationUnit();
        }

        Object unitEnum = parseDurationUnit(unitStr);

        return newInstance(
                "az.magusframework.components.lib.observability.otel.OtelMetricsRecorder",
                MetricsRecorder.class,
                new Class<?>[]{OpenTelemetry.class, unitEnum.getClass()},
                new Object[]{openTelemetry, unitEnum}
        );
    }

    // Fail fast: backend=otel but no OpenTelemetry bean
    @Bean
    @ConditionalOnProperty(prefix = "observability.metrics", name = "backend", havingValue = "otel")
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public MissingOtelForMetrics missingOpenTelemetryForMetrics() {
        return new MissingOtelForMetrics();
    }

    public static final class MissingOtelForMetrics {
        public MissingOtelForMetrics() {
            throw new IllegalStateException(
                    "observability.metrics.backend=otel but no OpenTelemetry bean found. " +
                            "Provide an OpenTelemetry bean (agent/autoconfig/your config) or set observability.metrics.backend=noop."
            );
        }
    }

    // ----------------- helpers -----------------

    private static Object parseDurationUnit(String unitStr) {
        Class<?> enumType;
        try {
            enumType = Class.forName("az.magusframework.components.lib.observability.otel.OtelMetricsRecorder$DurationUnit");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("OtelMetricsRecorder.DurationUnit not found on classpath", e);
        }

        String u = (unitStr == null) ? "ms" : unitStr.trim().toLowerCase();
        String constant = switch (u) {
            case "ns", "nanos" -> "NS";
            case "s", "sec", "secs", "seconds" -> "S";
            case "ms", "millis", "milliseconds", "" -> "MS";
            default -> "MS";
        };

        try {
            return Enum.valueOf((Class<? extends Enum>) enumType, constant);
        } catch (Exception e) {
            return Enum.valueOf((Class<? extends Enum>) enumType, "MS");
        }
    }

    private static <T> T newInstance(String fqcn, Class<T> type) {
        try {
            Class<?> c = Class.forName(fqcn);
            Object o = c.getDeclaredConstructor().newInstance();
            return type.cast(o);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate " + fqcn, e);
        }
    }

    private static <T> T newInstance(String fqcn, Class<T> type, Class<?>[] ctorTypes, Object[] ctorArgs) {
        try {
            Class<?> c = Class.forName(fqcn);
            Object o = c.getDeclaredConstructor(ctorTypes).newInstance(ctorArgs);
            return type.cast(o);
        } catch (Exception e) {
            // Fallback to ctor(OpenTelemetry) if you ever remove DurationUnit ctor
            if (ctorArgs != null && ctorArgs.length >= 1 && ctorArgs[0] instanceof OpenTelemetry otel) {
                try {
                    Class<?> c = Class.forName(fqcn);
                    Object o = c.getDeclaredConstructor(OpenTelemetry.class).newInstance(otel);
                    return type.cast(o);
                } catch (Exception ex) {
                    throw new IllegalStateException("Failed to instantiate " + fqcn + " with OpenTelemetry", ex);
                }
            }
            throw new IllegalStateException("Failed to instantiate " + fqcn, e);
        }
    }
}


