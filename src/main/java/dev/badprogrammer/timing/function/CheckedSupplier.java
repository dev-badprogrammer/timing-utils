package dev.badprogrammer.timing.function;

import dev.badprogrammer.timing.util.StopWatch;

/**
 * A functional interface equivalent to {@link java.util.function.Supplier}, but designed to support methods that
 * declare checked exceptions.
 *
 * <p>Java's standard {@code Supplier<T>} cannot be used directly with methods that throw checked exceptions (e.g.,
 * {@code SQLException}, {@code IOException}), because its {@code get()} method does not declare {@code throws}. This
 * forces callers into ugly {@code try/catch} wrapping just to construct the lambda, losing the original exception
 * type in the process.</p>
 *
 * <p>This interface solves that by declaring {@code throws Exception} on its single abstract method, allowing the
 * compiler to accept any value-returning lambda — throwing or non-throwing — without any wrapping. The original
 * exception propagates as-is to the caller.</p>
 *
 * <p><b>Usage example:</b></p>
 *
 * <p>Without CheckedSupplier — forced ugly wrapping and the original type is lost:
 * <blockquote>
 * {@snippet lang = "java":
 * Supplier<Connection> s = () -> {
 *     try {
 *         // throws SQLException
 *         return dbUtils.getConnection();
 *     } catch (SQLException e) {
 *         // original type lost
 *         throw new RuntimeException(e);
 *     }
 * };
 *}
 * </blockquote>
 *
 * <p>With CheckedSupplier — clean and direct:
 * <blockquote>
 * {@snippet lang = "java":
 * CheckedSupplier<Connection> s = () -> dbUtils.getConnection();
 *}
 * </blockquote>
 *
 * <p>Use {@link CheckedRunnable} instead, if the method returns void.</p>
 *
 * @param <T> the type of value produced by this supplier
 *
 * @see CheckedRunnable
 * @see java.util.function.Supplier
 * @see StopWatch
 */
@FunctionalInterface
public interface CheckedSupplier<T> {

    /**
     * Produces a result that may throw a checked exception.
     *
     * @return the produced value; may be {@code null}
     *
     * @throws Exception any checked or unchecked exception thrown by the underlying method
     */
    // Intentionally generic — the caller code may throw any checked exception, and narrowing the type would defeat the
    // interface's purpose. Mirrors `java.util.concurrent.Callable` pattern.
    @SuppressWarnings("java:S112")
    T get() throws Exception;
}
