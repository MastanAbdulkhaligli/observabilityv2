package az.magusframework.components.lib.observability.core.tracing;

import az.magusframework.components.lib.observability.core.tracing.span.SimpleSpanFactory;
import az.magusframework.components.lib.observability.core.tracing.span.SpanFactory;


/**
 * <h1>Tracing</h1>
 * <p>
 * The global entry point for distributed tracing in the Magus Framework.
 * This class provides a centralized static accessor to the active {@link SpanFactory}.
 * </p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * Span span = Tracing.get().newSpan("operation-name").start();
 * }</pre>
 */
public final class Tracing {

    // A lightweight, no-op or simple implementation as a safety default
    private static final SpanFactory DEFAULT_FACTORY = new SimpleSpanFactory();

    // volatile ensures that changes made by setSpanFactory are immediately visible to all threads
    private static volatile SpanFactory current = DEFAULT_FACTORY;

    private Tracing() {
        // Namespace only
    }

    /**
     * Replaces the active span factory.
     * <p>
     * This method is intended for use during application bootstrap (e.g., Spring initialization).
     * Once the application has started, the factory should remain stable.
     * </p>
     *
     * @param factory The new factory implementation. If null, the default is restored.
     */
    public static void setSpanFactory(SpanFactory factory) {
        current = (factory != null) ? factory : DEFAULT_FACTORY;
    }

    /**
     * Returns the active {@link SpanFactory}.
     * <p>
     * Guaranteed to return a non-null instance. If no custom factory has been
     * registered, a {@link SimpleSpanFactory} is returned.
     * </p>
     *
     * @return The current span factory.
     */
    public static SpanFactory get() {
        return current;
    }
}