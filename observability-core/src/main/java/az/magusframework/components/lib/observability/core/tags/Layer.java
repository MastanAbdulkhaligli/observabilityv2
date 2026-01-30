package az.magusframework.components.lib.observability.core.tags;

/**
 * <h1>Layer</h1>
 * <p>
 * Defines the logical architectural layers of an application for telemetry classification.
 * Standardizing these layers allows for consistent dashboarding, where latency and
 * error rates can be sliced by architectural responsibility.
 * </p>
 */
public enum Layer {

    /**
     * Inbound entry points (REST Controllers, SOAP Endpoints, Message Listeners).
     * Represents the outermost boundary of the application.
     */
    CONTROLLER("controller"),

    /**
     * Core business logic and orchestration.
     * Where the primary domain rules reside.
     */
    SERVICE("service"),

    /**
     * Data access and persistence layer (SQL, NoSQL, Cache).
     * Focuses on internal state management.
     */
    REPOSITORY("repository"),

    /**
     * Outbound communication to external systems (Third-party APIs, legacy services).
     * Focuses on remote dependencies.
     */
    EXTERNAL_SERVICE("external_service");

    private final String label;

    Layer(String label) {
        this.label = label;
    }

    /**
     * @return The lowercase, machine-readable label used for metric tags and log keys.
     */
    public String label() {
        return label;
    }

    /**
     * Resolves the Layer from a string label (useful for dynamic configuration).
     */
    public static Layer fromLabel(String label) {
        for (Layer l : values()) {
            if (l.label.equalsIgnoreCase(label)) return l;
        }
        throw new IllegalArgumentException("Unknown layer label: " + label);
    }
}