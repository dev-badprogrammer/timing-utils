package dev.badprogrammer.timing.type;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import dev.badprogrammer.timing.function.CheckedRunnable;
import dev.badprogrammer.timing.function.CheckedSupplier;
import dev.badprogrammer.timing.util.StopWatch;

/**
 * Holds the outcome of a method measured <em>once</em> via {@link StopWatch#measure(Runnable)},
 * {@link StopWatch#measure(Supplier)}, {@link StopWatch#measureChecked(CheckedRunnable)}, or
 * {@link StopWatch#measureChecked(CheckedSupplier)} — its return value and the elapsed time.
 *
 * <p>Elapsed time is stored internally in nanoseconds (the highest precision available from {@link System#nanoTime()})
 * and exposed as milliseconds via conversion methods like {@link #getElapsedMillis()} for human-readable reporting.
 * Keeping nanos internally prevents early truncation before any aggregation is performed.</p>
 *
 * <p>Instances of this class are immutable and created exclusively by {@link StopWatch}.</p>
 *
 * @param <T> the return type of the measured method
 *
 * @see StopWatch
 * @see TimingStatistics
 */
public final class TimedResult<T> {

    /**
     * The value returned by the measured method. {@code null} for void methods ({@code TimedResult<Void>}).
     */
    private final T result;

    /**
     * Nanoseconds elapsed during the one-time method invocation.
     */
    private final long elapsedNanos;

    /**
     * Constructs a {@code TimedResult} with the given return value and elapsed time.
     * Private — construction is routed through {@link #of} to keep instance creation centralized in one place,
     * consistent with the rest of the library's factory-method pattern.
     *
     * @param result       the value returned by the measured method; may be {@code null}
     * @param elapsedNanos the elapsed time in nanoseconds; must be {@code >= 0}
     */
    private TimedResult(final T result, final long elapsedNanos) {
        this.result       = result;
        this.elapsedNanos = elapsedNanos;
    }

    /**
     * Creates a {@code TimedResult} with the given return value and elapsed time.
     *
     * <p>Not intended for direct use — get instances via {@link StopWatch#measure(Runnable)},
     * {@link StopWatch#measure(Supplier)}, {@link StopWatch#measureChecked(CheckedRunnable)}, or
     * {@link StopWatch#measureChecked(CheckedSupplier)}.
     *
     * @param <T>          the return type of the measured method
     * @param result       the value returned by the measured method; may be {@code null}
     * @param elapsedNanos the elapsed time in nanoseconds; must be {@code >= 0}
     *
     * @return a new {@code TimedResult} instance
     */
    public static <T> TimedResult<T> of(final T result, final long elapsedNanos) {
        return new TimedResult<>(result, elapsedNanos);
    }

    /**
     * Returns the value produced by the measured method.
     *
     * @return the method's return value; always {@code null} for {@code TimedResult<Void>} since void methods produce
     *         no value
     */
    public T getResult() {
        return result;
    }

    /**
     * Returns the raw elapsed time in nanoseconds.
     *
     * <p>Use this when sub-millisecond precision is needed, or when feeding values into
     * {@link java.util.LongSummaryStatistics} for aggregation.</p>
     *
     * @return elapsed time in nanoseconds; always {@code >= 0}
     */
    public long getElapsedNanos() {
        return elapsedNanos;
    }

    /**
     * Returns the elapsed time in milliseconds.
     *
     * <p>Converted from nanoseconds after measurement — no precision is lost during timing itself. For human-readable
     * reporting this is the preferred unit.</p>
     *
     * @return elapsed time in milliseconds; always {@code >= 0}
     */
    public long getElapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
    }

    /**
     * Returns a human-readable summary of this result.
     *
     * @return string in the format {@code TimedResult[ElapsedMillis = Xms, ElapsedNanos = Yns]}
     */
    @Override
    public String toString() {
        return "TimedResult[ElapsedMillis = %dms, ElapsedNanos = %dns]".formatted(getElapsedMillis(),
                                                                                  getElapsedNanos());
    }
}
