package dev.badprogrammer.timing.type;

import java.util.LongSummaryStatistics;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TimingStatistics}.
 *
 * <p>Constructs instances directly via {@link TimingStatistics#of} with hand-built {@link LongSummaryStatistics}
 * values, rather than through {@link dev.badprogrammer.timing.util.StopWatch}, so these tests verify the class's own
 * computation and reporting behavior independently of how {@code StopWatch} happens to populate it.</p>
 *
 * <p><b>Why this class has tests despite looking like a plain value type:</b> {@code TimingStatistics} isn't purely
 * a data holder — it contains a genuine conditional branch ({@link TimingStatistics#getMinNanos()} and
 * {@link TimingStatistics#getMaxNanos()} return {@code 0} rather than delegating to
 * {@link LongSummaryStatistics#getMin()}/{@code getMax()} when there are zero successful iterations, since those
 * JDK methods return sentinel values in that case that would otherwise leak through) and a conditional
 * {@code toString()} (the "Last exception" line only appears when failures occurred). Both are real logic worth
 * protecting, not just field returns.</p>
 *
 * <p>Only covers behavior with real logic — the zero-successful-iterations branch, unit conversion, and the
 * conditional "Last exception" line in {@code toString()}. Plain stored-value getters
 * ({@code getSuccessfulIterations()}, {@code getFailedIterations()}, {@code getTotalIterations()},
 * {@code hasFailures()}, {@code getLastException()}) and the straightforward sum/average/min/max happy-path
 * computations are not separately tested here since they're already exercised via
 * {@code StopWatchTest.TimingStatisticsComputationTest} and involve no logic beyond what
 * {@link LongSummaryStatistics} itself already guarantees.</p>
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimingStatisticsTest {

    private static LongSummaryStatistics statsOf(long... values) {
        final var stats = new LongSummaryStatistics();
        for (final var value : values) {
            stats.accept(value);
        }
        return stats;
    }

    @Nested
    class NanosComputationTest {

        @Test
        void getMinNanos_returns_zero_when_no_successful_iterations() {
            // All iterations failed — the underlying LongSummaryStatistics has no recorded values
            final var result = TimingStatistics.of(statsOf(), 5, new IllegalStateException("Fail"));
            assertEquals(0L, result.getMinNanos());
        }

        @Test
        void getMaxNanos_returns_zero_when_no_successful_iterations() {
            final var result = TimingStatistics.of(statsOf(), 5, new IllegalStateException("Fail"));
            assertEquals(0L, result.getMaxNanos());
        }

        @Test
        void getTotalNanos_returns_zero_when_no_successful_iterations() {
            final var result = TimingStatistics.of(statsOf(), 5, new IllegalStateException("Fail"));
            assertEquals(0L, result.getTotalNanos());
        }

        @Test
        void getAverageNanos_returns_zero_when_no_successful_iterations() {
            final var result = TimingStatistics.of(statsOf(), 5, new IllegalStateException("Fail"));
            assertEquals(0.0, result.getAverageNanos());
        }
    }

    @Nested
    class MillisConversionTest {

        @Test
        void getTotalMillis_converts_nanos_to_millis() {
            final var result = TimingStatistics.of(statsOf(3_000_000L, 2_000_000L), 0, null);
            assertEquals(5L, result.getTotalMillis());
        }

        @Test
        void getAverageMillis_converts_nanos_to_millis() {
            final var result = TimingStatistics.of(statsOf(1_000_000L, 3_000_000L), 0, null);
            assertEquals(2.0, result.getAverageMillis());
        }

        @Test
        void getMinMillis_converts_nanos_to_millis() {
            final var result = TimingStatistics.of(statsOf(5_000_000L, 2_000_000L), 0, null);
            assertEquals(2L, result.getMinMillis());
        }

        @Test
        void getMaxMillis_converts_nanos_to_millis() {
            final var result = TimingStatistics.of(statsOf(5_000_000L, 2_000_000L), 0, null);
            assertEquals(5L, result.getMaxMillis());
        }
    }

    @Nested
    class ToStringTest {

        @Test
        void toString_includes_iteration_counts_and_timing_metrics() {
            final var result = TimingStatistics.of(statsOf(1_000_000L, 2_000_000L), 0, null);
            final var text   = result.toString();

            assertTrue(text.contains("Total iterations = 2"));
            assertTrue(text.contains("Successful iterations = 2"));
            assertTrue(text.contains("Failed iterations = 0"));
        }

        @Test
        void toString_includes_last_exception_when_failures_present() {
            final var result = TimingStatistics.of(statsOf(1_000_000L), 1, new IllegalStateException("Boom"));
            final var text   = result.toString();

            assertTrue(text.contains("Last exception"));
            assertTrue(text.contains("IllegalStateException"));
            assertTrue(text.contains("Boom"));
        }

        @Test
        void toString_excludes_last_exception_when_no_failures() {
            final var result = TimingStatistics.of(statsOf(1_000_000L), 0, null);
            final var text   = result.toString();

            assertFalse(text.contains("Last exception"));
        }
    }
}
