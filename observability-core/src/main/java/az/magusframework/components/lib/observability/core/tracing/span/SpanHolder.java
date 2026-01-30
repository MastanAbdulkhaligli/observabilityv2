package az.magusframework.components.lib.observability.core.tracing.span;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * <h1>SpanHolder</h1>
 * <p>
 * The central authority for the active tracing state on the current thread.
 * This class acts as a <b>Thread-Local Stack</b> that maintains the "breadcrumb trail"
 * of spans as execution moves through different layers of the application.
 * </p>
 * * <h3>Core Concepts:</h3>
 * <ul>
 * <li><b>Nesting:</b> Supports hierarchical spans (e.g., Controller -> Service -> Repository).</li>
 * <li><b>Isolation:</b> Uses {@link ThreadLocal} to ensure traces from different users/requests never mix.</li>
 * <li><b>Memory Management:</b> Automatically cleans up its internal {@code ThreadLocal} reference
 * once the stack is empty to prevent memory leaks in shared thread pools.</li>
 * </ul>
 * * <h3>Thread Safety Warning:</h3>
 * <p>
 * This class is thread-safe for the <i>current</i> thread. It does <b>not</b> automatically
 * propagate context across asynchronous boundaries (e.g., {@code CompletableFuture} or thread pools).
 * Context must be manually captured and restored when jumping threads.
 * </p>
 * * [Image of a stack-based context propagation diagram showing nested span activation and deactivation]
 */
public final class SpanHolder {

    /**
     * Internal stack of spans. We use {@link ArrayDeque} as it is more
     * efficient than {@link java.util.Stack} for LIFO operations.
     */
    private static final ThreadLocal<Deque<Span>> STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private SpanHolder() {
        // Prevent instantiation of utility class
    }

    /**
     * Pushes a new span onto the stack, making it the {@link #current()} span.
     * * @param span The span to activate. If null, the operation is ignored.
     */
    public static void push(Span span) {
        if (span == null) return;
        STACK.get().push(span);
    }

    /**
     * Removes the span from the top of the stack and returns it.
     * <p>
     * If the stack becomes empty after this operation, the {@link ThreadLocal}
     * is completely removed to ensure no reference leaks remain on the thread.
     * </p>
     * * @return The popped {@link Span}, or {@code null} if the stack was already empty.
     */
    public static Span pop() {
        Deque<Span> deque = STACK.get();
        Span popped = deque.isEmpty() ? null : deque.pop();
        if (deque.isEmpty()) {
            STACK.remove();
        }
        return popped;
    }

    /**
     * Retrieves the currently active span without removing it from the stack.
     * Use this for logging MDC or adding attributes to the "live" operation.
     * * @return The active {@link Span}, or {@code null} if no tracing is active.
     */
    public static Span current() {
        Deque<Span> deque = STACK.get();
        return deque.isEmpty() ? null : deque.peek();
    }

    /**
     * Forcefully wipes the tracing stack for the current thread.
     * <p>
     * <b>Usage:</b> This should typically be called in a {@code finally} block at the
     * outermost boundary of a request (e.g., in a Servlet Filter) to guarantee
     * a clean slate for the next request handled by this thread.
     * </p>
     */
    public static void clear() {
        STACK.remove();
    }
}