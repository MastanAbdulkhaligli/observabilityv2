package az.magusframework.components.lib.autoconfigure;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "observability.otel")
public class ObservabilityOtelProperties {

    private String serviceName = "unknown-service";
    private String serviceVersion = "1.0.0";
    private String environment = "local";

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getServiceVersion() { return serviceVersion; }
    public void setServiceVersion(String serviceVersion) { this.serviceVersion = serviceVersion; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
}
//@ConfigurationProperties(prefix = "observability.otel")
//public class ObservabilityOtelProperties {
//    private String serviceName = "unknown-service";
//    private String serviceVersion = "1.0.0";
//    private String environment = "local";
//    private final Exporter exporter = new Exporter();
//    private final Metrics metrics = new Metrics();
//
//    public String getServiceName() { return serviceName; }
//    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
//
//    public String getServiceVersion() { return serviceVersion; }
//    public void setServiceVersion(String serviceVersion) { this.serviceVersion = serviceVersion; }
//
//    public String getEnvironment() { return environment; }
//    public void setEnvironment(String environment) { this.environment = environment; }
//
//    public Exporter getExporter() { return exporter; }
//    public Metrics getMetrics() { return metrics; }
//
//    public static class Exporter {
//        private final Otlp otlp = new Otlp();
//        public Otlp getOtlp() { return otlp; }
//
//        public static class Otlp {
//            private String endpoint = "http://localhost:4317";
//            public String getEndpoint() { return endpoint; }
//            public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
//        }
//    }
//
//    public static class Metrics {
//        private long exportIntervalMs = 10000;
//        public long getExportIntervalMs() { return exportIntervalMs; }
//        public void setExportIntervalMs(long exportIntervalMs) { this.exportIntervalMs = exportIntervalMs; }
//    }
//}
