package az.magusframework.components.lib.observability.core.tags;

/**
 * <h1>MetricTag</h1>
 * <p>
 * An immutable key-value pair used to add dimensions to metrics.
 * </p>
 * * <p><b>Cardinality Warning:</b>
 * Tags must be low-cardinality. Avoid using unique identifiers (UUIDs, timestamps, or raw email addresses)
 * as tag values. High cardinality can cause significant performance degradation in metric backends.
 * </p>
 *
 * @param key   The tag name. Should be lowercase and use underscores or dots (e.g., "http.status").
 * @param value The tag value. Should be a stable, bounded set of strings.
 */
public record MetricTag(String key, String value) {
}