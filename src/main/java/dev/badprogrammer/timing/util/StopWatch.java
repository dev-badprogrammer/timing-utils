package dev.badprogrammer.timing.util;

import java.util.LongSummaryStatistics;
import java.util.Objects;
import java.util.function.Supplier;

import dev.badprogrammer.timing.function.CheckedRunnable;
import dev.badprogrammer.timing.function.CheckedSupplier;
import dev.badprogrammer.timing.type.TimedResult;
import dev.badprogrammer.timing.type.TimingStatistics;

/**
 * A utility class for measuring the execution time of any method invocation.
 *
 * <p>Intended for situational, deliberate use — performance investigations, benchmarking specific methods during
 * development or testing, or diagnosing a known performance problem. Results are returned directly to the caller as
 * {@link TimedResult} or {@link TimingStatistics} objects for programmatic inspection.</p>
 *
 * <p><b>This class provides two categories of measurement:</b></p>
 * <ul>
 *   <li><b>Single measurement</b> — measures the given method's elapsed execution time by invoking it <em>once</em>
 *   and returns a {@link TimedResult} containing its return value and the elapsed time.</li>
 *   <li><b>Repeated measurement</b> — measures the given method's elapsed execution time by invoking it
 *   <em>repeatedly</em> and returns a {@link TimingStatistics} containing aggregated statistics (total, average,
 *   min, and max elapsed times) across all iterations. Suitable for realistic performance profiling.</li>
 * </ul>
 *
 * <p>For permanent, ambient timing that logs elapsed time automatically in production code, use {@link TimingLogger}
 * instead.</p>
 *
 * <h2><b>Design Principles</b></h2>
 * <ul>
 *   <li><b>No side effects</b> — this class does nothing beyond timing. It does not retry, cache, modify, or
 *   intercept the measured method in any way. Any side effects observed are the method's own.</li>
 *   <li><b>Measure in nanos, report in millis</b> — {@link System#nanoTime()} is used internally for maximum
 *   precision. Millisecond conversions are performed only at the point of result retrieval, after all aggregation is
 *   complete.</li>
 *   <li><b>Always returns — never throws</b> — {@code measureRepeatedly()} always returns a {@link TimingStatistics}
 *   regardless of failures. Inspect {@link TimingStatistics#hasFailures()} and
 *   {@link TimingStatistics#getLastException()} to handle failures.</li>
 *   <li><b>Statistics cover successful iterations only</b> — a failed iteration's elapsed time is meaningless on its
 *   own. Failed iteration timings are discarded entirely — there's no meaningful way to compare or average them
 *   against each other; an instant validation failure and a 30-second timeout are both "failures," but their
 *   durations mean completely different things. Failures are represented by a count and the last exception
 *   instead — which already conveys more than any timing data could.</li>
 * </ul>
 *
 * <h2><b>Method Naming</b> — {@code measure} vs {@code measureChecked}</h2>
 *
 * <p>Each measurement operation comes in two named variants to eliminate compiler ambiguity and make the intent
 * explicit at the call site:</p>
 * <ul>
 *   <li>{@code measure} — for methods that <em>do not declare</em> any checked exceptions. Accepts {@link Runnable} or
 *   {@link Supplier}.</li>
 *   <li>{@code measureChecked} — for methods that <em>declare</em> checked exceptions (e.g. {@code SQLException},
 *   {@code IOException}). Accepts {@link CheckedRunnable} or {@link CheckedSupplier}. The name signals to the reader
 *   that a checked exception is involved.</li>
 * </ul>
 *
 * <p>See the project README's "Design Decisions &amp; Their Reasoning" section for the full reasoning, including why
 * plain method overloading doesn't work here.</p>
 *
 * <h2><b>Usage Examples</b></h2>
 *
 * <p>Single measurement — methods that return void and <em>do not declare</em> any checked exceptions
 * <blockquote>
 * {@snippet lang = "java":
 * final TimedResult<Void> timedResult   = StopWatch.measure(() -> eventPublisher.publishEvent());
 * final Void              result        = timedResult.getResult(); // result is always null
 * final long              elapsedMillis = timedResult.getElapsedMillis();
 *}
 * </blockquote>
 *
 * <p>Single measurement — methods that return a value and <em>do not declare</em> any checked exceptions
 * <blockquote>
 * {@snippet lang = "java":
 * final TimedResult<User> timedResult   = StopWatch.measure(() -> userService.getUserById(101));
 * final User              result        = timedResult.getResult();
 * final long              elapsedMillis = timedResult.getElapsedMillis();
 *}
 * </blockquote>
 *
 * <p>Single measurement — methods that return void and <em>declare</em> checked exceptions (e.g., SQLException)
 * <blockquote>
 * {@snippet lang = "java":
 * final TimedResult<Void> timedResult   = StopWatch.measureChecked(() -> dbUtils.closeConnection());
 * final Void              result        = timedResult.getResult(); // result is always null
 * final long              elapsedMillis = timedResult.getElapsedMillis();
 *}
 * </blockquote>
 *
 * <p>Single measurement — methods that return a value and <em>declare</em> checked exceptions (e.g., SQLException)
 * <blockquote>
 * {@snippet lang = "java":
 * final TimedResult<Connection> timedResult   = StopWatch.measureChecked(() -> dbUtils.getConnection());
 * final Connection              result        = timedResult.getResult();
 * final long                    elapsedMillis = timedResult.getElapsedMillis();
 *}
 * </blockquote>
 *
 * <p>Repeated measurement - methods that return void and <em>do not declare</em> any checked exceptions,
 * 1000 iterations, 5 warmup calls
 * <blockquote>
 * {@snippet lang = "java":
 * final TimingStatistics stats = StopWatch.measureRepeatedly(() -> eventPublisher.publishEvent(), 1_000, 5);
 * // stats.getResult(); // does not compile — no getResult() on TimingStatistics
 *}
 * </blockquote>
 *
 * <p>Repeated measurement - methods that return a value and <em>do not declare</em> any checked exceptions,
 * 1000 iterations, 5 warmup calls
 * <blockquote>
 * {@snippet lang = "java":
 * final TimingStatistics stats = StopWatch.measureRepeatedly(() -> userService.getUserById(101), 1_000, 5);
 * // stats.getResult(); // does not compile — no getResult() on TimingStatistics
 *}
 * </blockquote>
 *
 * <p>Repeated measurement — methods that return void and <em>declare</em> checked exceptions (e.g., SQLException),
 * 1000 iterations, 5 warmup calls
 * <blockquote>
 * {@snippet lang = "java":
 * final TimingStatistics stats = StopWatch.measureRepeatedlyChecked(() -> dbUtils.closeConnection(), 1_000, 5);
 * // stats.getResult(); // does not compile — no getResult() on TimingStatistics
 *}
 * </blockquote>
 *
 * <p>Repeated measurement — methods that return a value and <em>declare</em> checked exceptions (e.g., SQLException),
 * 1000 iterations, 5 warmup calls
 * <blockquote>
 * {@snippet lang = "java":
 * final TimingStatistics stats = StopWatch.measureRepeatedlyChecked(() -> dbUtils.getConnection(), 1_000, 5);
 * // stats.getResult(); // does not compile — no getResult() on TimingStatistics
 *}
 * </blockquote>
 *
 * <p>Repeated measurement — failed iterations details are captured and surfaced via {@code hasFailures()} and
 * {@code getLastException()}
 * <blockquote>
 * {@snippet lang = "java":
 * final TimingStatistics stats = StopWatch.measureRepeatedlyChecked(() -> dbUtils.getConnection(), 1_000, 5);
 * // stats.getResult(); // does not compile — no getResult() on TimingStatistics
 *
 * if (stats.hasFailures()) {
 *      stats.getLastException()
 *           .ifPresent(e -> System.out.printf("%d of %d iterations failed. Last exception: %s",
 *                                             stats.getFailedIterations(), stats.getTotalIterations(), e));
 * }
 *}
 * </blockquote>
 *
 * <p><b>Note:</b> {@code measureRepeatedly()} and {@code measureRepeatedlyChecked()} discard each invocation's
 * return value. Holding onto the result of every iteration — 1000 connection objects, for example — would consume
 * memory needlessly and provide no benefit for performance measurement, which only cares about elapsed time.</p>
 *
 * <p>This class is stateless and cannot be instantiated.</p>
 *
 * @see TimedResult
 * @see TimingStatistics
 * @see TimingLogger
 * @see CheckedRunnable
 * @see CheckedSupplier
 */
public final class StopWatch {

    private static final String METHOD_MUST_NOT_BE_NULL = "method must not be null";

    /**
     * Private constructor — this is a stateless utility class and must not be instantiated.
     */
    private StopWatch() {
        throw new UnsupportedOperationException("StopWatch is a utility class and cannot be instantiated.");
    }

    /**
     * Measures the given method's elapsed execution time by invoking it <em>once</em>. Intended for methods that return
     * void and <em>do not declare</em> any checked exceptions — for methods that do, use
     * {@link #measureChecked(CheckedRunnable)}.
     *
     * <p>The return type {@code TimedResult<Void>} is for consistency with the value-returning variants.
     * {@code getResult()} will always return {@code null}.</p>
     *
     * @param method the {@link Runnable} to measure; must not be {@code null}
     *
     * @return a {@code TimedResult<Void>} containing the elapsed time
     *
     * @throws RuntimeException     if the measured method throws a runtime exception
     * @throws NullPointerException if {@code method} is {@code null}
     */
    public static TimedResult<Void> measure(final Runnable method) {
        Objects.requireNonNull(method, METHOD_MUST_NOT_BE_NULL);
        return measure(() -> {
            method.run();
            return null;
        });
    }

    /**
     * Measures the given method's elapsed execution time by invoking it <em>once</em>. Intended for methods that return
     * a value and <em>do not declare</em> any checked exceptions — for methods that do, use
     * {@link #measureChecked(CheckedSupplier)}.
     *
     * @param <T>    the return type of the method being measured
     * @param method the {@link Supplier} to measure; must not be {@code null}
     *
     * @return a {@code TimedResult<T>} containing the method's return value and the elapsed time
     *
     * @throws RuntimeException     if the measured method throws a runtime exception
     * @throws NullPointerException if {@code method} is {@code null}
     */
    public static <T> TimedResult<T> measure(final Supplier<T> method) {
        Objects.requireNonNull(method, METHOD_MUST_NOT_BE_NULL);
        final var startNanos = System.nanoTime();
        return TimedResult.of(method.get(), System.nanoTime() - startNanos);
    }

    /**
     * Measures the given method's elapsed execution time by invoking it <em>once</em>. Intended for methods that return
     * void and <em>declare</em> checked exceptions — for methods that do not, use {@link #measure(Runnable)}.
     *
     * <p>The return type {@code TimedResult<Void>} is for consistency with the value-returning variants.
     * {@code getResult()} will always return {@code null}.</p>
     *
     * @param method the {@link CheckedRunnable} to measure; must not be {@code null}
     *
     * @return a {@code TimedResult<Void>} containing the elapsed time
     *
     * @throws Exception            if the measured method throws a checked or unchecked exception
     * @throws NullPointerException if {@code method} is {@code null}
     */
    public static TimedResult<Void> measureChecked(final CheckedRunnable method) throws Exception {
        Objects.requireNonNull(method, METHOD_MUST_NOT_BE_NULL);
        return measureChecked(() -> {
            method.run();
            return null;
        });
    }

    /**
     * Measures the given method's elapsed execution time by invoking it <em>once</em>. Intended for methods that return
     * a value and <em>declare</em> checked exceptions — for methods that do not, use {@link #measure(Supplier)}.
     *
     * @param <T>    the return type of the method being measured
     * @param method the {@link CheckedSupplier} to measure; must not be {@code null}
     *
     * @return a {@code TimedResult<T>} containing the method's return value and the elapsed time
     *
     * @throws Exception            if the measured method throws a checked or unchecked exception
     * @throws NullPointerException if {@code method} is {@code null}
     */
    public static <T> TimedResult<T> measureChecked(final CheckedSupplier<T> method) throws Exception {
        Objects.requireNonNull(method, METHOD_MUST_NOT_BE_NULL);
        final var startNanos = System.nanoTime();
        return TimedResult.of(method.get(), System.nanoTime() - startNanos);
    }

    /**
     * Measures the given method's elapsed execution time by invoking it <em>repeatedly</em>, {@code iterations} times.
     * Intended for methods that return void and <em>do not declare</em> any checked exceptions — for methods that do,
     * use {@link #measureRepeatedlyChecked(CheckedRunnable, int, int)}.
     *
     * <p><b>Warmup Iterations</b> — the first {@code warmupIterations} invocations are executed normally but excluded
     * from statistics. This prevents JVM class-loading and JIT compilation overhead from skewing the results. Warmup
     * side effects (e.g., database connections being made) still occur. A warmup invocation that throws is also
     * discarded entirely — it does not increment {@link TimingStatistics#getFailedIterations()} or update
     * {@link TimingStatistics#getLastException()}, consistent with excluding warmup from every other measurement.</p>
     *
     * <p><b>Failure Handling</b> — if an iteration throws, the failure count is incremented and execution continues.
     * Statistics cover successful iterations only — failed timings are excluded to avoid skewing performance analysis.
     * The result is always returned; inspect {@link TimingStatistics#hasFailures()} and
     * {@link TimingStatistics#getLastException()} to check for and retrieve the last exception thrown.</p>
     *
     * <p><b>Note:</b> Since this is a void method, there is no return value to retain.</p>
     *
     * @param method           the {@link Runnable} to measure repeatedly; must not be {@code null}
     * @param iterations       the number of iterations to measure and include in the statistics (excluding warmup);
     *                         must be {@code >= 1}
     * @param warmupIterations the number of initial iterations to discard before timing begins; must be {@code >= 0}
     *                         and {@code < iterations}
     *
     * @return a {@code TimingStatistics} containing aggregated timing statistics across all successful (non-warmup)
     *         invocations, plus failure tracking
     *
     * @throws IllegalArgumentException if {@code iterations < 1}, {@code warmupIterations < 0}, or
     *                                  {@code warmupIterations >= iterations}
     * @throws NullPointerException     if {@code method} is {@code null}
     */
    public static TimingStatistics measureRepeatedly(final Runnable method, final int iterations,
                                                     final int warmupIterations) {
        Objects.requireNonNull(method, METHOD_MUST_NOT_BE_NULL);
        return measureRepeatedly(() -> {
            method.run();
            return null;
        }, iterations, warmupIterations);
    }

    /**
     * Measures the given method's elapsed execution time by invoking it <em>repeatedly</em>, {@code iterations} times.
     * Intended for methods that return a value and <em>do not declare</em> any checked exceptions — for methods that
     * do, use {@link #measureRepeatedlyChecked(CheckedSupplier, int, int)}.
     *
     * <p><b>Warmup Iterations</b> — the first {@code warmupIterations} invocations are executed normally but excluded
     * from statistics. This prevents JVM class-loading and JIT compilation overhead from skewing the results. Warmup
     * side effects (e.g., database connections being made) still occur. A warmup invocation that throws is also
     * discarded entirely — it does not increment {@link TimingStatistics#getFailedIterations()} or update
     * {@link TimingStatistics#getLastException()}, consistent with excluding warmup from every other measurement.</p>
     *
     * <p><b>Failure Handling</b> — if an iteration throws, the failure count is incremented and execution continues.
     * Statistics cover successful iterations only — failed timings are excluded to avoid skewing performance analysis.
     * The result is always returned; inspect {@link TimingStatistics#hasFailures()} and
     * {@link TimingStatistics#getLastException()} to check for and retrieve the last exception thrown.</p>
     *
     * <p><b>Note:</b> {@code measureRepeatedly()} discards each invocation's return value. Holding onto the result of
     * every iteration — 1000 connection objects, for example — would consume memory needlessly and provide no benefit
     * for performance measurement, which only cares about elapsed time.</p>
     *
     * @param <T>              the return type of the method being measured
     * @param method           the {@link Supplier} to measure repeatedly; must not be {@code null}
     * @param iterations       the number of iterations to measure and include in the statistics (excluding warmup);
     *                         must be {@code >= 1}
     * @param warmupIterations the number of initial iterations to discard before timing begins; must be {@code >= 0}
     *                         and {@code < iterations}
     *
     * @return a {@code TimingStatistics} containing aggregated timing statistics across all successful (non-warmup)
     *         invocations, plus failure tracking
     *
     * @throws IllegalArgumentException if {@code iterations < 1}, {@code warmupIterations < 0}, or
     *                                  {@code warmupIterations >= iterations}
     * @throws NullPointerException     if {@code method} is {@code null}
     */
    public static <T> TimingStatistics measureRepeatedly(final Supplier<T> method, final int iterations,
                                                         final int warmupIterations) {
        Objects.requireNonNull(method, METHOD_MUST_NOT_BE_NULL);
        validateArguments(iterations, warmupIterations);

        final var        statistics       = new LongSummaryStatistics();
        var              failedIterations = 0;
        RuntimeException lastException    = null;

        for (int i = 0; i < iterations + warmupIterations; i++) {
            final var isWarmup   = i < warmupIterations;
            final var startNanos = System.nanoTime();

            try {
                method.get();
                if (!isWarmup) {
                    // Record elapsed time for successful, non-warmup iterations only. Failed timings would skew the
                    // average, min, and max; warmup timings are unrepresentative before the JVM reaches a steady state
                    // — both are excluded to keep the numbers meaningful.
                    statistics.accept(System.nanoTime() - startNanos);
                }
            } catch (RuntimeException e) {
                if (!isWarmup) {
                    failedIterations++;
                    lastException = e;
                }
            }
        }

        // Always return — never throw. The caller inspects hasFailures() and getLastException() instead of catching
        // an exception.
        return TimingStatistics.of(statistics, failedIterations, lastException);
    }

    /**
     * Measures the given method's elapsed execution time by invoking it <em>repeatedly</em>, {@code iterations} times.
     * Intended for methods that return void and <em>declare</em> checked exceptions — for methods that do not, use
     * {@link #measureRepeatedly(Runnable, int, int)}.
     *
     * <p><b>Warmup Iterations</b> — the first {@code warmupIterations} invocations are executed normally but excluded
     * from statistics. This prevents JVM class-loading and JIT compilation overhead from skewing the results. Warmup
     * side effects (e.g., database connections being made) still occur. A warmup invocation that throws is also
     * discarded entirely — it does not increment {@link TimingStatistics#getFailedIterations()} or update
     * {@link TimingStatistics#getLastException()}, consistent with excluding warmup from every other measurement.</p>
     *
     * <p><b>Failure Handling</b> — if an iteration throws, the failure count is incremented and execution continues.
     * Statistics cover successful iterations only — failed timings are excluded to avoid skewing performance analysis.
     * The result is always returned; inspect {@link TimingStatistics#hasFailures()} and
     * {@link TimingStatistics#getLastException()} to check for and retrieve the last exception thrown.</p>
     *
     * <p><b>Note:</b> Since this is a void method, there is no return value to retain.</p>
     *
     * <p><b>Note:</b> Unlike {@link #measureChecked(CheckedRunnable)}, this method <em>does not</em> declare
     * {@code throws Exception} in its signature. The {@code Checked} suffix refers to the input type accepted —
     * {@link CheckedRunnable}, which may declare a checked exception — not to whether exceptions are propagated. Any
     * exception thrown by an iteration is caught internally and surfaced via {@link TimingStatistics#hasFailures()}
     * and {@link TimingStatistics#getLastException()}. This method always returns — it never throws.</p>
     *
     * @param method           the {@link CheckedRunnable} to measure repeatedly; must not be {@code null}
     * @param iterations       the number of iterations to measure and include in the statistics (excluding warmup);
     *                         must be {@code >= 1}
     * @param warmupIterations the number of initial iterations to discard before timing begins; must be {@code >= 0}
     *                         and {@code < iterations}
     *
     * @return a {@code TimingStatistics} containing aggregated timing statistics across all successful (non-warmup)
     *         invocations, plus failure tracking
     *
     * @throws IllegalArgumentException if {@code iterations < 1}, {@code warmupIterations < 0}, or
     *                                  {@code warmupIterations >= iterations}
     * @throws NullPointerException     if {@code method} is {@code null}
     * @see #measureChecked(CheckedRunnable) measureChecked(CheckedRunnable) — the single-invocation variant that
     *         <em>does</em> propagate exceptions
     */
    public static TimingStatistics measureRepeatedlyChecked(final CheckedRunnable method, final int iterations,
                                                            final int warmupIterations) {
        Objects.requireNonNull(method, METHOD_MUST_NOT_BE_NULL);
        return measureRepeatedlyChecked(() -> {
            method.run();
            return null;
        }, iterations, warmupIterations);
    }

    /**
     * Measures the given method's elapsed execution time by invoking it <em>repeatedly</em>, {@code iterations} times.
     * Intended for methods that return a value and <em>declare</em> checked exceptions — for methods that do not, use
     * {@link #measureRepeatedly(Supplier, int, int)}.
     *
     * <p><b>Warmup Iterations</b> — the first {@code warmupIterations} invocations are executed normally but excluded
     * from statistics. This prevents JVM class-loading and JIT compilation overhead from skewing the results. Warmup
     * side effects (e.g., database connections being made) still occur. A warmup invocation that throws is also
     * discarded entirely — it does not increment {@link TimingStatistics#getFailedIterations()} or update
     * {@link TimingStatistics#getLastException()}, consistent with excluding warmup from every other measurement.</p>
     *
     * <p><b>Failure Handling</b> — if an iteration throws, the failure count is incremented and execution continues.
     * Statistics cover successful iterations only — failed timings are excluded to avoid skewing performance analysis.
     * The result is always returned; inspect {@link TimingStatistics#hasFailures()} and
     * {@link TimingStatistics#getLastException()} to check for and retrieve the last exception thrown.</p>
     *
     * <p><b>Note:</b> {@code measureRepeatedly()} discards each invocation's return value. Holding onto the result of
     * every iteration — 1000 connection objects, for example — would consume memory needlessly and provide no benefit
     * for performance measurement, which only cares about elapsed time.</p>
     *
     * <p><b>Note:</b> Unlike {@link #measureChecked(CheckedSupplier)}, this method <em>does not</em> declare
     * {@code throws Exception} in its signature. The {@code Checked} suffix refers to the input type accepted —
     * {@link CheckedSupplier}, which may declare a checked exception — not to whether exceptions are propagated. Any
     * exception thrown by an iteration is caught internally and surfaced via {@link TimingStatistics#hasFailures()}
     * and {@link TimingStatistics#getLastException()}. This method always returns — it never throws.</p>
     *
     * @param <T>              the return type of the method being measured
     * @param method           the {@link CheckedSupplier} to measure repeatedly; must not be {@code null}
     * @param iterations       the number of iterations to measure and include in the statistics (excluding warmup);
     *                         must be {@code >= 1}
     * @param warmupIterations the number of initial iterations to discard before timing begins; must be {@code >= 0}
     *                         and {@code < iterations}
     *
     * @return a {@code TimingStatistics} containing aggregated timing statistics across all successful (non-warmup)
     *         invocations, plus failure tracking
     *
     * @throws IllegalArgumentException if {@code iterations < 1}, {@code warmupIterations < 0}, or
     *                                  {@code warmupIterations >= iterations}
     * @throws NullPointerException     if {@code method} is {@code null}
     * @see #measureChecked(CheckedSupplier) measureChecked(CheckedSupplier) — the single-invocation variant that
     *         <em>does</em> propagate exceptions
     */
    public static <T> TimingStatistics measureRepeatedlyChecked(final CheckedSupplier<T> method, final int iterations,
                                                                final int warmupIterations) {
        Objects.requireNonNull(method, METHOD_MUST_NOT_BE_NULL);
        validateArguments(iterations, warmupIterations);

        final var statistics       = new LongSummaryStatistics();
        var       failedIterations = 0;
        Exception lastException    = null;

        for (int i = 0; i < iterations + warmupIterations; i++) {
            final var isWarmup   = (i < warmupIterations);
            final var startNanos = System.nanoTime();

            try {
                method.get();
                if (!isWarmup) {
                    // Record elapsed time for successful, non-warmup iterations only. Failed timings would skew the
                    // average, min, and max; warmup timings are unrepresentative before the JVM reaches a steady state
                    // — both are excluded to keep the numbers meaningful.
                    statistics.accept(System.nanoTime() - startNanos);
                }
            } catch (Exception e) {
                if (!isWarmup) {
                    failedIterations++;
                    lastException = e;
                }
            }
        }

        // Always return — never throw. The caller inspects hasFailures() and getLastException() instead of catching
        // an exception.
        return TimingStatistics.of(statistics, failedIterations, lastException);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // ------------------------------------------------ Private Helpers ------------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Validates arguments shared by all {@code measureRepeatedly} variants.
     *
     * @throws IllegalArgumentException if {@code iterations < 1}, or
     *                                  if {@code warmupIterations < 0}, or
     *                                  if {@code warmupIterations >= iterations}
     */
    private static void validateArguments(final int iterations, final int warmupIterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be >= 1, but was %d".formatted(iterations));
        }
        if (warmupIterations < 0) {
            throw new IllegalArgumentException("warmupIterations must be >= 0, but was %d".formatted(warmupIterations));
        }
        if (warmupIterations >= iterations) {
            throw new IllegalArgumentException(
                    "warmupIterations (%d) must be less than iterations (%d)".formatted(warmupIterations, iterations));
        }
    }
}
