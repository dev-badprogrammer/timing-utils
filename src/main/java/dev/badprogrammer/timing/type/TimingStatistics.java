package dev.badprogrammer.timing.type;

import java.util.LongSummaryStatistics;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import dev.badprogrammer.timing.function.CheckedRunnable;
import dev.badprogrammer.timing.function.CheckedSupplier;
import dev.badprogrammer.timing.util.StopWatch;

/**
 * Holds the aggregated statistics of a method measured <em>repeatedly</em> via
 * {@link StopWatch#measureRepeatedly(Runnable, int, int)}, {@link StopWatch#measureRepeatedly(Supplier, int, int)},
 * {@link StopWatch#measureRepeatedlyChecked(CheckedRunnable, int, int)}, or
 * {@link StopWatch#measureRepeatedlyChecked(CheckedSupplier, int, int)} — timing distribution, success/failure counts,
 * and elapsed time across all iterations.
 *
 * <p><b>Statistics cover successful iterations only</b> — a failed iteration's elapsed time is meaningless on its own.
 * Failed iteration timings are discarded entirely — there's no meaningful way to compare or average them against each
 * other; an instant validation failure and a 30-second timeout are both "failures," but their durations mean completely
 * different things. Failures are represented by a count and the last exception instead — which already conveys more
 * than any timing data could.</p>
 *
 * <p>All statistical calculations (total, average, min, max, count) are delegated entirely to
 * {@link LongSummaryStatistics}, which is a battle-tested JDK class that handles precision and rounding correctly. No
 * custom arithmetic is performed here.</p>
 *
 * <p>Internally all values are stored in nanoseconds — the unit captured by {@link System#nanoTime()} — to preserve
 * full precision throughout aggregation. Millisecond conversions are performed only at the point of retrieval, after
 * all aggregation is complete, ensuring no accumulated rounding error.</p>
 *
 * <p><b>Always returned — never thrown.</b> {@code measureRepeatedly()} always returns the ({@code TimingStatistics})
 * object regardless of whether some iterations failed. Inspect {@link #hasFailures()} and {@link #getLastException()}
 * to handle failures.</p>
 *
 * <p>Instances of this class are immutable and created exclusively by {@link StopWatch}.</p>
 *
 * @see StopWatch
 * @see TimedResult
 * @see LongSummaryStatistics
 */
public final class TimingStatistics {

    /**
     * JDK-provided statistics container holding a sum, average, min, max, and count across all successful iterations.
     * All values are in nanoseconds.
     */
    private final LongSummaryStatistics statistics;

    /**
     * Number of iterations that threw an exception and did not return a value.
     */
    private final int failedIterations;

    /**
     * The last exception thrown (if any) during iteration. {@code null} when all iterations succeeded.
     */
    private final Exception lastException;

    /**
     * Constructs a {@code TimingStatistics} from the given statistics and failure count.
     * Private — construction is routed through {@link #of} to keep instance creation centralized in one place,
     * consistent with the rest of the library's factory-method pattern.
     *
     * @param statistics       the aggregated nanosecond timings from all successful iterations; must not be
     *                         {@code null}
     * @param failedIterations the number of iterations that threw an exception; must be {@code >= 0}
     * @param lastException    the last exception thrown during iteration; {@code null} if all iterations succeeded
     */
    private TimingStatistics(final LongSummaryStatistics statistics, final int failedIterations,
                             final Exception lastException) {
        this.statistics       = statistics;
        this.failedIterations = failedIterations;
        this.lastException    = lastException;
    }

    /**
     * Creates a {@code TimingStatistics} with the given statistics and failure count.
     *
     * <p>Not intended for direct use — get instances via {@link StopWatch#measureRepeatedly(Runnable, int, int)},
     * {@link StopWatch#measureRepeatedly(Supplier, int, int)},
     * {@link StopWatch#measureRepeatedlyChecked(CheckedRunnable, int, int)}, or
     * {@link StopWatch#measureRepeatedlyChecked(CheckedSupplier, int, int)}.
     *
     * @param statistics       the aggregated nanosecond timings from all successful iterations; must not be
     *                         {@code null}
     * @param failedIterations the number of iterations that threw an exception; must be {@code >= 0}
     * @param lastException    the last exception thrown during iteration; {@code null} if all iterations succeeded
     *
     * @return a new {@code TimingStatistics} instance
     */
    public static TimingStatistics of(final LongSummaryStatistics statistics, final int failedIterations,
                                      final Exception lastException) {
        return new TimingStatistics(statistics, failedIterations, lastException);
    }

    /**
     * Returns the total number of iterations attempted, including both successful and failed ones.
     *
     * @return total iteration count; always {@code >= 0}
     */
    public long getTotalIterations() {
        return getSuccessfulIterations() + getFailedIterations();
    }

    /**
     * Returns the number of iterations that completed successfully (i.e., did not throw).
     *
     * @return successful iteration count; always {@code >= 0}
     */
    public long getSuccessfulIterations() {
        return statistics.getCount();
    }

    /**
     * Returns the number of iterations that threw an exception.
     *
     * <p>If an iteration throws, the failure count is increased and the execution continues. Failed iterations will not
     * contribute their elapsed time to the statistics.</p>
     *
     * @return failed iteration count; always {@code >= 0}
     */
    public int getFailedIterations() {
        return failedIterations;
    }

    /**
     * Returns the total elapsed time across all successful iterations, in nanoseconds.
     *
     * @return total elapsed nanos; 0 if there were no successful iterations
     */
    public long getTotalNanos() {
        return statistics.getSum();
    }

    /**
     * Returns the average elapsed time per successful iteration, in nanoseconds.
     *
     * <p>The average is computed by {@link LongSummaryStatistics}, which handles precision correctly. No custom
     * rounding applied. The result is a {@code double} to preserve fractional nanos.</p>
     *
     * @return average elapsed nanos as a {@code double}; {@code 0.0} if no successful iterations
     */
    public double getAverageNanos() {
        return statistics.getAverage();
    }

    /**
     * Returns the shortest elapsed time among all successful iterations, in nanoseconds.
     *
     * @return minimum elapsed nanos; 0 if no successful iterations were recorded
     */
    public long getMinNanos() {
        return statistics.getCount() == 0 ? 0L : statistics.getMin();
    }

    /**
     * Returns the longest elapsed time among all successful iterations, in nanoseconds.
     *
     * @return maximum elapsed nanos; 0 if no successful iterations were recorded
     */
    public long getMaxNanos() {
        return statistics.getCount() == 0 ? 0L : statistics.getMax();
    }

    /**
     * Returns the total elapsed time across all successful iterations, in milliseconds.
     *
     * <p>Conversion is applied after aggregation, so no precision is lost during the statistical calculations
     * themselves.</p>
     *
     * @return total elapsed milliseconds; 0 if no successful iterations
     */
    public long getTotalMillis() {
        return TimeUnit.NANOSECONDS.toMillis(getTotalNanos());
    }

    /**
     * Returns the average elapsed time per successful iteration, in milliseconds.
     *
     * <p>Conversion is applied after aggregation ({@code averageNanos / 1_000_000}). The result is a {@code double} to
     * preserve fractional milliseconds.</p>
     *
     * @return average elapsed milliseconds; {@code 0.0} if no successful iterations
     */
    public double getAverageMillis() {
        return getAverageNanos() / 1_000_000.0;
    }

    /**
     * Returns the shortest elapsed time among all successful iterations, in milliseconds.
     *
     * @return minimum elapsed milliseconds; 0 if no successful iterations were recorded
     */
    public long getMinMillis() {
        return TimeUnit.NANOSECONDS.toMillis(getMinNanos());
    }

    /**
     * Returns the longest elapsed time among all successful iterations, in milliseconds.
     *
     * @return maximum elapsed milliseconds; 0 if no successful iterations were recorded
     */
    public long getMaxMillis() {
        return TimeUnit.NANOSECONDS.toMillis(getMaxNanos());
    }

    /**
     * Returns {@code true} if one or more iterations threw an exception.
     *
     * @return {@code true} if any iteration failed; {@code false} if all succeeded
     */
    public boolean hasFailures() {
        return failedIterations > 0;
    }

    /**
     * Returns the last exception thrown (if any) during iteration.
     *
     * <p>When multiple iterations fail, only the last exception is retained. The exception type and message typically
     * convey more about the failure than any timing data would.</p>
     *
     * <blockquote>
     * {@snippet lang = "java":
     * TimingStatistics stats = StopWatch.measureRepeatedlyChecked(() -> dbUtils.getConnection(), 1_000, 5);
     *
     * if (stats.hasFailures()) {
     *     stats.getLastException()
     *          .ifPresent(e -> System.out.printf("%d of %d iterations failed. Last exception: %s",
     *                          stats.getFailedIterations(), stats.getTotalIterations(), e));
     * }
     *}
     * </blockquote>
     *
     * @return the last exception thrown, or {@link Optional#empty()} if all iterations succeeded
     */
    public Optional<Exception> getLastException() {
        return Optional.ofNullable(lastException);
    }

    /**
     * Returns a human-readable summary of the aggregated timing statistics.
     *
     * @return multi-line string containing all key metrics in milliseconds, plus iteration counts
     */
    @Override
    public String toString() {
        final var delimiter = ", ";
        final var prefix    = TimingStatistics.class.getSimpleName() + "[";
        final var suffix    = "]";
        final var result    = new StringJoiner(delimiter, prefix, suffix);

        result.add("Total iterations = %d".formatted(getTotalIterations()));
        result.add("Successful iterations = %d".formatted(getSuccessfulIterations()));
        result.add("Failed iterations = %d".formatted(getFailedIterations()));
        result.add("Total elapsed time = %dms".formatted(getTotalMillis()));
        result.add("Average elapsed time = %.3fms".formatted(getAverageMillis()));
        result.add("Minimum elapsed time = %dms".formatted(getMinMillis()));
        result.add("Maximum elapsed time = %dms".formatted(getMaxMillis()));

        // Failure details — only when failures occurred
        if (hasFailures()) {
            getLastException().ifPresent(e -> result.add("Last exception = %s: %s".formatted(e.getClass()
                                                                                              .getCanonicalName(),
                                                                                             e.getMessage())));
        }

        return result.toString();
    }
}
