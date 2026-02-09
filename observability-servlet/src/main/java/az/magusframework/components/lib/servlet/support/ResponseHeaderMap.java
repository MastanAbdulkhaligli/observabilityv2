package az.magusframework.components.lib.servlet.support;

import jakarta.servlet.http.HttpServletResponse;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Set;

public final class ResponseHeaderMap extends AbstractMap<String, String> {

    private final HttpServletResponse response;

    public ResponseHeaderMap(HttpServletResponse response) {
        this.response = response;
    }

    @Override
    public String put(String key, String value) {
        response.setHeader(key, value);
        return value;
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return Collections.emptySet();
    }
}