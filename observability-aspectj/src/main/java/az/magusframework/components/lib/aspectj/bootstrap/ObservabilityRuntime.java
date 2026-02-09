package az.magusframework.components.lib.aspectj.bootstrap;

import az.magusframework.components.lib.observability.core.telemetry.TelemetryHelper;
import az.magusframework.components.lib.observability.core.tracing.TraceIdGenerator;

import java.util.Objects;

public final class ObservabilityRuntime {

    private final String appName;
    private final String serviceName;
    private final TelemetryHelper telemetry;
    private final TraceIdGenerator traceIdGenerator;


    public ObservabilityRuntime(
            String appName,
            String serviceName,
            TelemetryHelper telemetry,
            TraceIdGenerator traceIdGenerator
    ) {
        this.appName = require(appName, "appName");
        this.serviceName = require(serviceName, "serviceName");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.traceIdGenerator =
                Objects.requireNonNull(traceIdGenerator, "traceIdGenerator");
    }

    public String appName() {
        return appName;
    }

    public String serviceName() {
        return serviceName;
    }

    public TelemetryHelper telemetry() {
        return telemetry;
    }

    public TraceIdGenerator traceIdGenerator() {
        return traceIdGenerator;
    }


    private static String require(String v, String name) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(
                    name + " must not be null or blank"
            );
        }
        return v;
    }
}
