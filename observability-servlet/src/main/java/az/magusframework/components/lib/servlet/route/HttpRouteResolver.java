package az.magusframework.components.lib.servlet.route;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface HttpRouteResolver {
    /**
     * Return a low-cardinality route (template preferred) or null if unavailable.
     * Must be SAFE for metrics cardinality.
     */
    String resolveRoute(HttpServletRequest request, HttpServletResponse response);
}
