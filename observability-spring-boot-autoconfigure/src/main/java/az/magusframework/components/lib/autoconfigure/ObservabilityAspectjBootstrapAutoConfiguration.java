package az.magusframework.components.lib.autoconfigure;

import az.magusframework.components.lib.aspectj.bootstrap.ObservabilityBootstrap;
import az.magusframework.components.lib.observability.core.metrics.MetricsRecorder;
import az.magusframework.components.lib.observability.core.telemetry.TelemetryHelper;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;


/**
 * Initializes ObservabilityBootstrap for AspectJ aspects.
 *
 * Ensures ObservabilityBootstrap.init(...) runs once during Spring Boot startup,
 * before any request can hit woven controller/service/repository join points.
 */
@AutoConfiguration
@ConditionalOnClass(ObservabilityBootstrap.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAspectjBootstrapAutoConfiguration {

    /**
     * Runs after Spring has created singletons (TelemetryHelper, TraceIdGenerator, etc.),
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

            ObservabilityBootstrap.init(
                    svc.getAppName(),
                    svc.getServiceName(),
                    telemetry,
                    traceIdGenerator
            );
        };
    }

    /**
     * Provide a TraceIdGenerator if the app didn't define one and your tracing auto-config
     * didn't create one yet. (If you already always create TraceIdGenerator elsewhere, you
     * can remove this method.)
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceIdGenerator traceIdGenerator() {
        // If your project already has a default generator bean elsewhere, delete this.
        return new az.magusframework.components.lib.observability.core.tracing.RandomTraceIdGenerator();
    }
}
