package az.magusframework.components.lib.observability.core.logging;

/**
 * <h1>LogFields</h1>
 * <p>
 * Defines the canonical MDC (Mapped Diagnostic Context) keys used throughout the Magus Framework.
 * These constants ensure consistency between Java code, log aggregators (ELK, Splunk),
 * and visualization tools (Grafana).
 * </p>
 * * <p><b>Naming Convention:</b> This class follows a "flat-namespace" approach but suggests
 * a nested structure for JSON encoders.</p>
 */
public final class LogFields {

    private LogFields() {
        // Namespace only
    }

    /**
     * W3C and OpenTelemetry compatible distributed tracing fields.
     */
    public static final class Trace {
        private Trace() {}
        public static final String TRACE_ID       = "trace.id";
        public static final String SPAN_ID        = "trace.span_id";
        public static final String PARENT_SPAN_ID = "trace.parent_id";
        public static final String SAMPLED        = "trace.sampled";
    }

    /**
     * Identity metadata for the emitting service.
     */
    public static final class Service {
        private Service() {}
        public static final String NAME      = "service.name";
        public static final String MODULE    = "service.module";
        public static final String COMPONENT = "service.component";
        public static final String ENV       = "service.env";
        public static final String VERSION   = "service.version";
    }

    /**
     * Infrastructure and hardware metadata.
     */
    public static final class Runtime {
        private Runtime() {}
        public static final String HOST = "host.name";
        public static final String IP   = "host.ip";
        public static final String NODE = "k8s.node.name";
    }

    /**
     * Business-specific correlation identifiers (Magus Legacy Support).
     */
    public static final class Correlation {
        private Correlation() {}
        public static final String REQUEST_ID     = "correlation.id";
        public static final String COMP_ID        = "magus.comp_id";
        public static final String REQUEST_NUMBER = "magus.request_number";
        public static final String BAGGAGE        = "correlation.baggage";
    }

    /**
     * Inbound and Outbound HTTP metadata.
     */
    public static final class Http {
        private Http() {}
        public static final String DIRECTION   = "http.direction";
        public static final String METHOD      = "http.request.method";
        public static final String ROUTE       = "http.route";
        public static final String PATH        = "http.request.path";
        public static final String STATUS      = "http.response.status_code";
        public static final String DURATION_MS = "http.duration_ms";
        public static final String CLIENT_IP   = "http.client.ip";
        public static final String OUTCOME     = "http.outcome";
    }

    /**
     * Internal execution context and logical layers.
     */
    public static final class Context {
        private Context() {}
        public static final String LAYER         = "context.layer";
        public static final String OPERATION     = "context.operation";
        public static final String ENTITY        = "context.entity";
        public static final String DB_OPERATION  = "context.db_operation";
        public static final String TARGET_SYSTEM = "context.target_system";
        public static final String PROTOCOL      = "context.protocol";
    }

    /**
     * Detailed error and exception metadata.
     */
    public static final class Error {
        private Error() {}
        public static final String TYPE    = "error.type";
        public static final String MESSAGE = "error.message";
        public static final String LAYER   = "error.layer";
        public static final String KIND    = "error.kind"; // BUSINESS | TECHNICAL
        public static final String CODE    = "error.code";
    }
}