package az.magusframework.components.lib.observability.core.tags;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <h1>MetricTags</h1>
 * <p>
 * An immutable, ordered collection of {@link MetricTag} objects.
 * This class ensures that tag keys are unique and provides a safe way to
 * accumulate dimensions across different layers of an application.
 * </p>
 *
 * <p><b>Thread Safety:</b> This class is deeply immutable and thread-safe.</p>
 */
public final class MetricTags {

    private static final MetricTags EMPTY = new MetricTags(Collections.emptyList());

    private final List<MetricTag> tags;

    private MetricTags(List<MetricTag> tags) {
        // List.copyOf handles null check and provides an unmodifiable list
        this.tags = List.copyOf(tags);
    }

    /**
     * Creates a new instance from a list of tags.
     * Handles null by returning an empty instance.
     */
    public static MetricTags of(List<MetricTag> tags) {
        if (tags == null || tags.isEmpty()) return EMPTY;

        // Ensure uniqueness if the input list has duplicate keys
        return fromMap(toMap(tags));
    }

    /** @return A shared singleton instance of an empty tag set. */
    public static MetricTags empty() {
        return EMPTY;
    }

    /**
     * @return An unmodifiable view of the underlying tags.
     */
    public List<MetricTag> asList() {
        return tags;
    }

    /**
     * Adds a tag to the collection. If a tag with the same key already exists,
     * it is replaced. This follows the "Local Overrides Global" principle.
     *
     * @param tag The tag to add or update.
     * @return A new immutable MetricTags instance.
     */
    public MetricTags with(MetricTag tag) {
        if (tag == null) return this;

        // Use LinkedHashMap to preserve insertion order (better for log readability)
        Map<String, String> merged = toMap(this.tags);
        merged.put(tag.key(), tag.value());

        return fromMap(merged);
    }

    /**
     * Merges another set of tags into this one.
     */
    public MetricTags withAll(MetricTags other) {
        if (other == null || other.tags.isEmpty()) return this;

        Map<String, String> merged = toMap(this.tags);
        for (MetricTag t : other.tags) {
            merged.put(t.key(), t.value());
        }

        return fromMap(merged);
    }

    @Override
    public String toString() {
        if (tags.isEmpty()) return "{}";
        return tags.stream()
                .map(t -> t.key() + "=" + t.value())
                .collect(Collectors.joining(", ", "{", "}"));
    }

    // --- Private Utilities ---

    private static Map<String, String> toMap(List<MetricTag> source) {
        Map<String, String> map = new LinkedHashMap<>();
        for (MetricTag t : source) {
            if (t != null) map.put(t.key(), t.value());
        }
        return map;
    }

    private static MetricTags fromMap(Map<String, String> map) {
        List<MetricTag> list = new ArrayList<>(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            list.add(new MetricTag(entry.getKey(), entry.getValue()));
        }
        return new MetricTags(list);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricTags that = (MetricTags) o;
        return Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tags);
    }
}