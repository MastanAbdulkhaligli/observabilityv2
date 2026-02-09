package az.magusframework.components.lib.aspectj.bootstrap;

import az.magusframework.components.lib.aspectj.policy.LayerObservationPolicy;
import az.magusframework.components.lib.aspectj.policy.LayerPolicyRegistry;
import az.magusframework.components.lib.observability.core.tags.Layer;
import az.magusframework.components.lib.observability.core.telemetry.TelemetryHelper;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;

public final class ObservabilityBootstrap {

    /**
     * Published runtime instance.
     *
     * Volatile ensures safe publication across threads and classloaders.
     */
    private static volatile ObservabilityRuntime runtime;

    private ObservabilityBootstrap() {}

    // ---- initialization ----

    /**
     * Initializes observability with explicit layer pointcut configuration.
     *
     * <p>This method must be called exactly once during application startup.</p>
     */
    public static synchronized void init(
            String appName,
            String serviceName,
            TelemetryHelper telemetry,
            TraceIdGenerator traceIdGenerator
    ) {
        if (runtime != null) {
            throw new IllegalStateException(
                    "ObservabilityBootstrap is already initialized"
            );
        }

        runtime = new ObservabilityRuntime(
                appName,
                serviceName,
                telemetry,
                traceIdGenerator
        );
    }

    public static void ensureInitialized() {
        if (runtime == null) {
            throw new IllegalStateException(
                    "ObservabilityBootstrap.init(...) must be called before using aspects"
            );
        }
    }

    /**
     * Configure observation policy for a specific layer.
     * Can be called multiple times before first use.
     */
    public static void configureLayer(Layer layer, LayerObservationPolicy policy) {
        LayerPolicyRegistry.override(layer, policy);
    }

    // ---- accessors ----

    public static String appName() {
        return require().appName();
    }

    public static String serviceName() {
        return require().serviceName();
    }

    public static TelemetryHelper telemetry() {
        return require().telemetry();
    }

    public static TraceIdGenerator traceIdGen() {
        return require().traceIdGenerator();
    }

    // ---- test / lifecycle support ----

    static void resetForTests() {
        runtime = null;
    }

    private static ObservabilityRuntime require() {
        ObservabilityRuntime r = runtime;
        if (r == null) {
            throw new IllegalStateException(
                    "ObservabilityBootstrap is not initialized"
            );
        }
        return r;
    }
}
