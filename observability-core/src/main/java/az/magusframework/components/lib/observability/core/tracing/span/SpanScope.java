package az.magusframework.components.lib.observability.core.tracing.span;

/**
 * <h1>SpanScope</h1>
 * <p>
 * An {@link AutoCloseable} handle that defines the lifetime of a {@link Span}
 * being active on the current thread.
 * </p>
 *
 * <p><b>Usage Pattern:</b></p>
 * <p>This interface MUST be used with a {@code try-with-resources} block to ensure
 * the span is correctly deactivated and the previous context is restored,
 * even if an exception occurs.</p>
 *
 * <pre>{@code
 * try (SpanScope scope = span.activate()) {
 * // This code is now "inside" the span's context.
 * // MDC and ThreadLocal state are set here.
 * doWork();
 * } // scope.close() is called automatically here, cleaning up the thread.
 * }</pre>
 */
public interface SpanScope extends AutoCloseable {

    /**
     * Closes this scope and restores the previous span to the current thread.
     * <p>
     * Implementation must be idempotent; calling {@code close()} multiple times
     * should have no side effects.
     * </p>
     */
    @Override
    void close();
}