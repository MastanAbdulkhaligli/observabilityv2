package az.magusframework.components.lib.servlet.body;

import jakarta.servlet.http.HttpServletRequest;

public final class NoopRequestBodyExtractor implements RequestBodyExtractor {
    @Override
    public void extract(HttpServletRequest request) {
        // no-op
    }
}