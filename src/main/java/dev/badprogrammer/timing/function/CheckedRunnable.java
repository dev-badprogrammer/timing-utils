package dev.badprogrammer.timing.function;

import dev.badprogrammer.timing.util.StopWatch;

/**
 * A functional interface equivalent to {@link Runnable}, but designed to support methods that declare checked
 * exceptions.
 *
 * <p>Java's standard {@link Runnable} cannot be used directly with methods that throw checked exceptions (e.g.,
 * {@code SQLException}, {@code IOException}), because its {@code run()} method does not declare {@code throws}. This
 * forces callers into ugly {@code try/catch} wrapping just to construct the lambda, losing the original exception type
 * in the process.</p>
 *
 * <p>This interface solves that by declaring {@code throws Exception} on its single abstract method, allowing the
 * compiler to accept any void returning lambda — throwing or non-throwing — without any wrapping. The original
 * exception propagates as-is to the caller.</p>
 *
 * <p><b>Usage example:</b></p>
 *
 * <p>Without CheckedRunnable — forced ugly wrapping and the original type is lost:
 * <blockquote>
 * {@snippet lang = "java":
 * Runnable r = () -> {
 *     try {
 *         // throws SQLException
 *         dbUtils.closeConnection();
 *     } catch (SQLException e) {
 *         // original type lost
 *         throw new RuntimeException(e);
 *     }
 * };
 *}
 * </blockquote>
 *
 * <p>With CheckedRunnable — clean and direct:
 * <blockquote>
 * {@snippet lang = "java":
 * CheckedRunnable r = () -> dbUtils.closeConnection();
 *}
 * </blockquote>
 *
 * <p>Use {@link CheckedSupplier} instead, if the method returns a value.</p>
 *
 * @see CheckedSupplier
 * @see Runnable
 * @see StopWatch
 */
@FunctionalInterface
public interface CheckedRunnable {

    /**
     * Executes an action that may throw a checked exception.
     *
     * @throws Exception any checked or unchecked exception thrown by the underlying method
     */
    // Intentionally generic — the caller code may throw any checked exception, and narrowing the type would defeat the
    // interface's purpose. Mirrors `java.util.concurrent.Callable` pattern.
    @SuppressWarnings("java:S112")
    void run() throws Exception;
}
