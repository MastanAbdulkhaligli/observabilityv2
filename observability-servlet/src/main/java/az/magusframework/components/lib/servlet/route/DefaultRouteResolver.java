package az.magusframework.components.lib.servlet.route;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class DefaultRouteResolver implements HttpRouteResolver {

    @Override
    public String resolveRoute(HttpServletRequest request, HttpServletResponse response) {
        return RouteBucketing.bucketEarly(request.getRequestURI());
    }
}
