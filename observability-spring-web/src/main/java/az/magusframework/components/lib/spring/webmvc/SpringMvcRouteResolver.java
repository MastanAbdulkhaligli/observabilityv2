package az.magusframework.components.lib.spring.webmvc;

import az.magusframework.components.lib.servlet.route.HttpRouteResolver;
import az.magusframework.components.lib.servlet.route.RouteBucketing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class SpringMvcRouteResolver implements HttpRouteResolver {

    private static final String BEST =
            org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE;

    @Override
    public String resolveRoute(HttpServletRequest request, HttpServletResponse response) {
        Object best = request.getAttribute(BEST);
        if (best == null) return null;

        String p = best.toString();
        if (p == null || p.isBlank()) return null;

        return RouteBucketing.clamp(p);
    }
}
