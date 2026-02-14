package az.magusframework.components.lib.autoconfigure;

import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.aspectj.policy.AspectjObservationPolicy;
import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.observability.core.telemetry.TelemetryHelper;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import az.magusframework.components.lib.observability.core.tracing.RandomTraceIdGenerator;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Initializes ObservabilityBootstrap for AspectJ aspects.
 *
 * Ensures ObservabilityBootstrap.init(...) runs once during Spring Boot startup,
 * before any request can hit woven controller/service/repository join points.
 *
 * Also publishes YAML-driven AspectJ observation policy so woven aspects can decide
 * at runtime whether to observe or just proceed.
 */
@AutoConfiguration
@ConditionalOnClass(ObservabilityBootstrap.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAspectjBootstrapAutoConfiguration {

    /**
     * Runs after Spring has created singletons (MetricsRecorder, TraceIdGenerator, etc.),
     * but before the application is considered ready to serve traffic.
     */
    @Bean
    public SmartInitializingSingleton observabilityAspectjBootstrap(
            ObservabilityProperties props,
            MetricsRecorder metricsRecorder,
            TraceIdGenerator traceIdGenerator
    ) {
        return () -> {
            var svc = props.getService();

            // TelemetryHelper requires MetricsRecorder (per your codebase)
            TelemetryHelper telemetry = new TelemetryHelper(metricsRecorder);

            // Initialize bootstrap used by woven aspects
            ObservabilityBootstrap.init(
                    svc.getAppName(),
                    svc.getServiceName(),
                    telemetry,
                    traceIdGenerator
            );

            // ---- publish AspectJ policy from YAML ----
            // Note: requires ObservabilityProperties to include "observability.aspectj.*" block.
            var a = props.getAspectj();

            AspectjObservationPolicy.set(
                    new AspectjObservationPolicy.Config(
                            a.isEnabled(),
                            toLayerCfg(a.getController()),
                            toLayerCfg(a.getService()),
                            toLayerCfg(a.getRepository())
                    )
            );
        };
    }

    /**
     * Provide a TraceIdGenerator if the app didn't define one and your tracing auto-config
     * didn't create one yet.
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceIdGenerator traceIdGenerator() {
        return new RandomTraceIdGenerator();
    }

    // ---------------- policy helpers ----------------

    private static AspectjObservationPolicy.LayerConfig toLayerCfg(ObservabilityProperties.Aspectj.LayerSelector s) {
        var inc = compile(s.getIncludeRegex());
        var exc = compile(s.getExcludeRegex());

        return new AspectjObservationPolicy.LayerConfig(
                s.isEnabled(),
                safeList(s.getIncludePackages()),
                safeList(s.getExcludePackages()),
                inc,
                exc
        );
    }

    private static List<Pattern> compile(List<String> rules) {
        if (rules == null || rules.isEmpty()) return List.of();

        return rules.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(String::trim)
                .map(Pattern::compile)
                .toList();
    }

    private static List<String> safeList(List<String> v) {
        return (v == null) ? List.of() : v;
    }
}
