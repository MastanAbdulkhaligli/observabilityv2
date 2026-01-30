package az.magusframework.components.lib.observability.core.tags;

import java.util.Objects;

/**
 * <h1>TagsFactory</h1>
 * <p>
 * Enforces a unified dimensional schema for all metrics within the Magus Framework.
 * Standardization at this level ensures that dashboards and alerts remain stable
 * across different microservices.
 * </p>
 *
 * <p><b>Cardinality Control:</b> All input strings are sanitized and length-clamped
 * to prevent accidental infrastructure overloads.</p>
 */
public final class TagsFactory {

    // Internal Schema Constants (Professional standard: use constants for keys)
    private static final String TAG_APP = "app";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_LAYER = "layer";
    private static final String TAG_OPERATION = "operation";
    private static final String TAG_HTTP_METHOD = "http.method";
    private static final String TAG_HTTP_ROUTE = "http.route";
    private static final String TAG_STATUS_CLASS = "http.status_code";
    private static final String TAG_ENTITY = "db.entity";
    private static final String TAG_DB_OP = "db.operation";
    private static final String TAG_TARGET = "target.system";
    private static final String TAG_PROTOCOL = "rpc.protocol";

    private TagsFactory() {
        // Utility class
    }

    /**
     * Builds standard tags for an HTTP Server request.
     */
    public static MetricTags httpServer(String app, String service, String method, String route, String statusClass) {
        return MetricTags.empty()
                .with(new MetricTag(TAG_APP, sanitize(app, "unknown-app")))
                .with(new MetricTag(TAG_SERVICE, sanitize(service, "unknown-service")))
                .with(new MetricTag(TAG_HTTP_METHOD, sanitize(method, "UNKNOWN")))
                .with(new MetricTag(TAG_HTTP_ROUTE, sanitize(route, "UNKNOWN")))
                .with(new MetricTag(TAG_STATUS_CLASS, sanitize(statusClass, "unknown")));
    }

    /**
     * Builds tags for Controller (Web) layer operations.
     */
    public static MetricTags controller(String app, String service, String operation, String method, String route) {
        return base(app, service, Layer.CONTROLLER, operation)
                .with(new MetricTag(TAG_HTTP_METHOD, method))
                .with(new MetricTag(TAG_HTTP_ROUTE, route));
    }

    /**
     * Builds tags for Service (Business Logic) layer operations.
     */
    public static MetricTags service(String app, String service, String operation) {
        return base(app, service, Layer.SERVICE, operation);
    }

    /**
     * Builds tags for Repository (Data Access) layer operations.
     */
    public static MetricTags repository(String app, String service, String method, String entity, String dbOp) {
        return base(app, service, Layer.REPOSITORY, method)
                .with(new MetricTag(TAG_ENTITY, sanitize(entity, "unknown")))
                .with(new MetricTag(TAG_DB_OP, sanitize(dbOp, "UNKNOWN")));
    }

    /**
     * Builds tags for Outbound/External system calls.
     */
    public static MetricTags externalCall(String app, String service, String target, String operation, String protocol) {
        return base(app, service, Layer.EXTERNAL_SERVICE, operation)
                .with(new MetricTag(TAG_TARGET, sanitize(target, "unknown")))
                .with(new MetricTag(TAG_PROTOCOL, sanitize(protocol, "unknown")));
    }

    // --- Private Infrastructure ---

    /**
     * The internal core schema required for every layer-aware metric.
     */
    private static MetricTags base(String app, String service, Layer layer, String operation) {
        Objects.requireNonNull(layer, "Layer classification is mandatory");
        return MetricTags.empty()
                .with(new MetricTag(TAG_APP, sanitize(app, "unknown-app")))
                .with(new MetricTag(TAG_SERVICE, sanitize(service, "unknown-service")))
                .with(new MetricTag(TAG_LAYER, layer.name()))
                .with(new MetricTag(TAG_OPERATION, sanitize(operation, "unknown-op")));
    }

    private static String sanitize(String input, String defaultValue) {
        if (input == null || input.isBlank()) return defaultValue;
        String s = input.trim();
        // Clamping prevents massive strings from entering the tag set
        return (s.length() > 100) ? s.substring(0, 100) : s;
    }
}