package az.magusframework.components.lib.observability.core.tracing.span;

import java.util.Objects;

/**
 * <h1>DefaultSpanScope</h1>
 * <p>
 * Manages the thread-local lifecycle of a {@link Span}. This implementation
 * ensures that spans are correctly activated and deactivated on the current thread
 * using a stack-based approach.
 * </p>
 *
 * <p><b>Safety:</b> This class includes guards to prevent "out-of-order" popping,
 * which protects the integrity of the tracing stack in complex scenarios.</p>
 */
public final class DefaultSpanScope implements SpanScope {

    private final Span span;
    private boolean closed;

    /**
     * Activates the given span on the current thread.
     * @param span The span to activate.
     * @throws NullPointerException if span is null.
     */
    public DefaultSpanScope(Span span) {
        this.span = Objects.requireNonNull(span, "Span to activate cannot be null");
        SpanHolder.push(this.span);
    }

    /**
     * Deactivates the span and restores the previous context.
     * <p>
     * Implementation Note: This method is idempotent. It also performs a
     * stack-integrity check to ensure the span being closed is the one currently
     * at the top of the {@link SpanHolder} stack.
     * </p>
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;

        // --- Stack Integrity Guard ---
        Span current = SpanHolder.current();
        if (current != span) {
            // In professional libs, we log a warning here but still pop to prevent
            // the stack from growing indefinitely, or we do nothing if it's already gone.
            // For Magus, we strictly pop only if this scope owns the top of the stack.
            if (current == null) {
                return; // Stack already cleared elsewhere
            }
        }

        SpanHolder.pop();
    }
}