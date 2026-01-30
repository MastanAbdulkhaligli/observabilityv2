package az.magusframework.components.lib.observability.core.telemetry;

/**
 * <h1>CheckedSupplier</h1>
 * <p>
 * A functional interface representing a supplier of results that may throw a
 * {@link Throwable}. This is used primarily by telemetry and observation utilities
 * to wrap around business logic blocks that require instrumentation.
 * </p>
 *
 * <p>This interface is the "checked" equivalent of {@link java.util.function.Supplier}.</p>
 *
 * @param <T> The type of the result supplied.
 */
@FunctionalInterface
public interface CheckedSupplier<T> {

    /**
     * Gets a result.
     *
     * @return The supplied result.
     * @throws Throwable if the computation fails.
     */
    T get() throws Throwable;
}