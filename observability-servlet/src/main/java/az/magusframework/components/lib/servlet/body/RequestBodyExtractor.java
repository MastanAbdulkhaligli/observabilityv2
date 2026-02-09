package az.magusframework.components.lib.servlet.body;

import jakarta.servlet.http.HttpServletRequest;

public interface RequestBodyExtractor {
    void extract(HttpServletRequest request);
}