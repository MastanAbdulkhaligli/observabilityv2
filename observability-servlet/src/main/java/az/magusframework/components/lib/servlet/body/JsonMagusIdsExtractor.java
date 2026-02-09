package az.magusframework.components.lib.servlet.body;


import az.magusframework.components.lib.observability.logging.LoggingContext;
import az.magusframework.components.lib.servlet.support.CachedBodyHttpServletRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public final class JsonMagusIdsExtractor implements RequestBodyExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonMagusIdsExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int maxBodyBytes;

    public JsonMagusIdsExtractor(int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    public void extract(HttpServletRequest request) {
        // This extractor expects request to be CachedBodyHttpServletRequest.
        // If not, just no-op safely.
        if (!(request instanceof CachedBodyHttpServletRequest cached)) {
            return;
        }

        byte[] body = cached.cachedBody();
        if (body == null || body.length == 0) return;
        if (body.length > maxBodyBytes) return;

        // only try JSON-ish bodies
        String ct = request.getContentType();
        if (ct == null || !ct.toLowerCase().contains("application/json")) return;

        try {
            String s = new String(body, StandardCharsets.UTF_8);
            JsonNode root = MAPPER.readTree(s);
            if (root == null || root.isMissingNode()) return;

            String requestNumber = root.hasNonNull("requestNumber") ? root.get("requestNumber").asText() : null;
            String compId = root.hasNonNull("compId") ? root.get("compId").asText() : null;

            if ((requestNumber != null && !requestNumber.isBlank())
                    || (compId != null && !compId.isBlank())) {
                LoggingContext.putMagusIds(requestNumber, compId);
            }
        } catch (Exception e) {
            log.debug("Failed to parse JSON for Magus IDs: {}", e.getMessage());
        }
    }
}