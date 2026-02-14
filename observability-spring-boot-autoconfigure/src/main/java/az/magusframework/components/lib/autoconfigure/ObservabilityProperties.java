package az.magusframework.components.lib.autoconfigure;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    private final Service service = new Service();
    private final Http http = new Http();
    private final Tracing tracing = new Tracing();
    private final Metrics metrics = new Metrics();
    private final Layer layer = new Layer();
    private final Aspectj aspectj = new Aspectj();

    public Service getService() { return service; }
    public Http getHttp() { return http; }
    public Tracing getTracing() { return tracing; }
    public Metrics getMetrics() { return metrics; }
    public Layer getLayer() { return layer; }
    public Aspectj getAspectj() { return aspectj; }

    public static class Service {
        private String appName = "unknown-app";
        private String serviceName = "demo-service";
        private String module = "unknown-module";
        private String component = "unknown-component";
        private String env = "local";

        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }

        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }

        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }

        public String getComponent() { return component; }
        public void setComponent(String component) { this.component = component; }

        public String getEnv() { return env; }
        public void setEnv(String env) { this.env = env; }
    }

    public static class Http {
        private boolean enabled = true;
        private int filterOrder = Integer.MIN_VALUE + 10;
        private int maxCachedBodyBytes = 0;
        private int maxBodyBytesToParse = 4096;
        private boolean extractMagusIdsFromJsonBody = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getFilterOrder() { return filterOrder; }
        public void setFilterOrder(int filterOrder) { this.filterOrder = filterOrder; }

        public int getMaxCachedBodyBytes() { return maxCachedBodyBytes; }
        public void setMaxCachedBodyBytes(int maxCachedBodyBytes) { this.maxCachedBodyBytes = maxCachedBodyBytes; }

        public int getMaxBodyBytesToParse() { return maxBodyBytesToParse; }
        public void setMaxBodyBytesToParse(int maxBodyBytesToParse) { this.maxBodyBytesToParse = maxBodyBytesToParse; }

        public boolean isExtractMagusIdsFromJsonBody() { return extractMagusIdsFromJsonBody; }
        public void setExtractMagusIdsFromJsonBody(boolean v) { this.extractMagusIdsFromJsonBody = v; }
    }

    public static class Tracing {
        private String backend = "simple"; // simple|otel
        public String getBackend() { return backend; }
        public void setBackend(String backend) { this.backend = backend; }
    }

    public static class Metrics {
        private String backend = "noop"; // noop|otel
        private final Otel otel = new Otel();

        public String getBackend() { return backend; }
        public void setBackend(String backend) { this.backend = backend; }

        public Otel getOtel() { return otel; }

        public static class Otel {
            private String durationUnit = "ms"; // ns|ms|s
            public String getDurationUnit() { return durationUnit; }
            public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }
        }
    }

    public static class Layer {
        private String controller = "full";
        private String service = "full";
        private String repository = "full";

        public String getController() { return controller; }
        public void setController(String controller) { this.controller = controller; }

        public String getService() { return service; }
        public void setService(String service) { this.service = service; }

        public String getRepository() { return repository; }
        public void setRepository(String repository) { this.repository = repository; }
    }

    public static class Aspectj {
        private boolean enabled = true;
        private final LayerSelector controller = new LayerSelector();
        private final LayerSelector service = new LayerSelector();
        private final LayerSelector repository = new LayerSelector();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public LayerSelector getController() { return controller; }
        public LayerSelector getService() { return service; }
        public LayerSelector getRepository() { return repository; }

        public static class LayerSelector {
            private boolean enabled = true;

            /**
             * "package" => within(*..controller..*)
             * "annotation" => @within(RestController/Service/Repository)
             */
            private String match = "package";

            private java.util.List<String> includePackages = java.util.List.of();
            private java.util.List<String> excludePackages = java.util.List.of();

            private java.util.List<String> includeRegex = java.util.List.of();
            private java.util.List<String> excludeRegex = java.util.List.of();

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            public String getMatch() { return match; }
            public void setMatch(String match) { this.match = match; }

            public java.util.List<String> getIncludePackages() { return includePackages; }
            public void setIncludePackages(java.util.List<String> v) { this.includePackages = (v == null) ? java.util.List.of() : v; }

            public java.util.List<String> getExcludePackages() { return excludePackages; }
            public void setExcludePackages(java.util.List<String> v) { this.excludePackages = (v == null) ? java.util.List.of() : v; }

            public java.util.List<String> getIncludeRegex() { return includeRegex; }
            public void setIncludeRegex(java.util.List<String> v) { this.includeRegex = (v == null) ? java.util.List.of() : v; }

            public java.util.List<String> getExcludeRegex() { return excludeRegex; }
            public void setExcludeRegex(java.util.List<String> v) { this.excludeRegex = (v == null) ? java.util.List.of() : v; }
        }
    }

}
