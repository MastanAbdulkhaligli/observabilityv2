package az.magusframework.components.lib.servlet.route;

public class RouteBucketing {
    private RouteBucketing() {}

    public static final String ROUTE_UNMAPPED = "UNMAPPED";
    public static final String ROUTE_DOCS = "DOCS";
    public static final String ROUTE_STATIC = "STATIC";
    public static final String ROUTE_ACTUATOR = "/actuator/{endpoint}";

    /**
     * Early route bucketing: used before handler mapping exists.
     * LOW cardinality only.
     */
    public static String bucketEarly(String uri) {
        if (uri == null || uri.isBlank()) return ROUTE_UNMAPPED;

        if (uri.startsWith("/actuator")) return ROUTE_ACTUATOR;
        if (uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs")) return ROUTE_DOCS;

        // crude static bucket (adjust to your needs)
        if (uri.startsWith("/assets/")
                || uri.startsWith("/static/")
                || uri.endsWith(".js")
                || uri.endsWith(".css")
                || uri.endsWith(".png")
                || uri.endsWith(".jpg")
                || uri.endsWith(".svg")) {
            return ROUTE_STATIC;
        }

        // "/{first}/**"
        String[] parts = uri.split("/");
        if (parts.length >= 2 && !parts[1].isBlank()) {
            return "/" + parts[1] + "/**";
        }

        return ROUTE_UNMAPPED;
    }

    public static String clamp(String route) {
        if (route == null) return "UNKNOWN";
        String r = route.trim();
        if (r.isEmpty()) return "UNKNOWN";
        return (r.length() > 120) ? r.substring(0, 120) : r;
    }
}
