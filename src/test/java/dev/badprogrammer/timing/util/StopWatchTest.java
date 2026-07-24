package dev.badprogrammer.timing.util;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.badprogrammer.timing.function.CheckedRunnable;
import dev.badprogrammer.timing.function.CheckedSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StopWatch}.
 *
 * <p>Verifies return values, elapsed time recording, exception propagation, null-argument rejection, warmup exclusion,
 * failure tracking, and argument validation across all eight method variants.</p>
 *
 * <p><b>Test organization</b> — nested classes group tests by method-overload, covering delegation-specific concerns
 * (does this particular overload correctly propagate/catch exceptions, count iterations, reject nulls).</p>
 *
 * <p>Statistical computation facts (min/max/average/total, millis-nanos consistency) are verified once in
 * {@link TimingStatisticsComputationTest} rather than duplicated across all four repeated-measurement variants. All
 * four converge on the exact same underlying {@link java.util.LongSummaryStatistics}-based computation — repeating
 * those assertions per variant would test the same code path four times without adding real confidence.</p>
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class StopWatchTest {

    private static final int  SLEEP_MILLIS           = 100;
    private static final long LOWER_TOLERANCE_MILLIS = 90;
    private static final long UPPER_TOLERANCE_MILLIS = 150;

    @Nested
    class ArgumentValidationTest {

        @Test
        void throws_when_iterations_is_zero() {
            assertThrows(IllegalArgumentException.class,
                         () -> StopWatch.measureRepeatedly(StopWatchTest::returnValue, 0, 0));
        }

        @Test
        void throws_when_iterations_is_negative() {
            assertThrows(IllegalArgumentException.class,
                         () -> StopWatch.measureRepeatedly(StopWatchTest::returnValue, -1, 0));
        }

        @Test
        void throws_when_warmup_iterations_is_negative() {
            assertThrows(IllegalArgumentException.class,
                         () -> StopWatch.measureRepeatedly(StopWatchTest::returnValue, 10, -1));
        }

        @Test
        void throws_when_warmup_iterations_equals_iterations() {
            assertThrows(IllegalArgumentException.class,
                         () -> StopWatch.measureRepeatedly(StopWatchTest::returnValue, 5, 5));
        }

        @Test
        void throws_when_warmup_iterations_exceeds_iterations() {
            assertThrows(IllegalArgumentException.class,
                         () -> StopWatch.measureRepeatedly(StopWatchTest::returnValue, 5, 6));
        }

        @Test
        void accepts_zero_warmup_iterations() {
            assertDoesNotThrow(() -> StopWatch.measureRepeatedly(StopWatchTest::returnValue, 5, 0));
        }

        @Test
        void accepts_warmup_iterations_less_than_iterations() {
            assertDoesNotThrow(() -> StopWatch.measureRepeatedly(StopWatchTest::returnValue, 5, 4));
        }

        @Test
        void exception_message_contains_invalid_value() {
            final var ex = assertThrows(IllegalArgumentException.class,
                                        () -> StopWatch.measureRepeatedly(StopWatchTest::returnValue, -5, 0));
            assertTrue(ex.getMessage()
                         .contains("-5"));
        }
    }

    @Nested
    class MeasureSupplierTest {

        @Test
        void returns_method_value() {
            final var result = StopWatch.measure(StopWatchTest::returnValue);
            assertEquals("Result", result.getResult());
        }

        @Test
        void records_positive_elapsed_nanos() {
            final var result = StopWatch.measure(StopWatchTest::returnValue);
            assertTrue(result.getElapsedNanos() > 0);
        }

        @Test
        void records_elapsed_time_consistent_with_work_duration() {
            final var result = StopWatch.measure(() -> {
                simulateWork();
                return "Value";
            });
            assertTrue(result.getElapsedMillis() >= LOWER_TOLERANCE_MILLIS,
                       "Expected >= %dms but was %dms".formatted(LOWER_TOLERANCE_MILLIS, result.getElapsedMillis()));
            assertTrue(result.getElapsedMillis() <= UPPER_TOLERANCE_MILLIS,
                       "Expected <= %dms but was %dms".formatted(UPPER_TOLERANCE_MILLIS, result.getElapsedMillis()));
        }

        @Test
        void propagates_runtime_exception() {
            final var ex = assertThrows(IllegalStateException.class,
                                        () -> StopWatch.measure(StopWatchTest::throwUncheckedException));
            assertEquals("A simulated failure", ex.getMessage());
        }

        @Test
        void slower_method_records_more_elapsed_time() {
            final var fast = StopWatch.measure(StopWatchTest::returnValue);
            final var slow = StopWatch.measure(() -> {
                simulateWork();
                return "Value";
            });
            assertTrue(slow.getElapsedNanos() > fast.getElapsedNanos());
        }

        @Test
        void throws_when_method_is_null() {
            final var ex = assertThrows(NullPointerException.class, () -> StopWatch.measure((Supplier<String>) null));
            assertEquals("method must not be null", ex.getMessage());
        }
    }

    @Nested
    class MeasureRunnableTest {

        @Test
        void returns_null_result() {
            final var result = StopWatch.measure(StopWatchTest::voidMethod);
            assertNull(result.getResult());
        }

        @Test
        void records_positive_elapsed_nanos() {
            final var result = StopWatch.measure(StopWatchTest::voidMethod);
            assertTrue(result.getElapsedNanos() > 0);
        }

        @Test
        void records_elapsed_time_consistent_with_work_duration() {
            final var result = StopWatch.measure(StopWatchTest::simulateWork);
            assertTrue(result.getElapsedMillis() >= LOWER_TOLERANCE_MILLIS,
                       "Expected >= %dms but was %dms".formatted(LOWER_TOLERANCE_MILLIS, result.getElapsedMillis()));
            assertTrue(result.getElapsedMillis() <= UPPER_TOLERANCE_MILLIS,
                       "Expected <= %dms but was %dms".formatted(UPPER_TOLERANCE_MILLIS, result.getElapsedMillis()));
        }

        @Test
        void propagates_runtime_exception() {
            final var ex = assertThrows(IllegalStateException.class,
                                        () -> StopWatch.measure(StopWatchTest::throwUncheckedException));
            assertEquals("A simulated failure", ex.getMessage());
        }

        @Test
        void throws_when_method_is_null() {
            assertThrows(NullPointerException.class, () -> StopWatch.measure((Runnable) null));
        }
    }

    @Nested
    class MeasureCheckedSupplierTest {

        @Test
        void returns_method_value() throws Exception {
            final var result = StopWatch.measureChecked(StopWatchTest::returnValueChecked);
            assertEquals("Result", result.getResult());
        }

        @Test
        void records_positive_elapsed_nanos() throws Exception {
            final var result = StopWatch.measureChecked(StopWatchTest::returnValueChecked);
            assertTrue(result.getElapsedNanos() > 0);
        }

        @Test
        void records_elapsed_time_consistent_with_work_duration() throws Exception {
            final var result = StopWatch.measureChecked(() -> {
                simulateWork();
                return "Value";
            });
            assertTrue(result.getElapsedMillis() >= LOWER_TOLERANCE_MILLIS,
                       "Expected >= %dms but was %dms".formatted(LOWER_TOLERANCE_MILLIS, result.getElapsedMillis()));
            assertTrue(result.getElapsedMillis() <= UPPER_TOLERANCE_MILLIS,
                       "Expected <= %dms but was %dms".formatted(UPPER_TOLERANCE_MILLIS, result.getElapsedMillis()));
        }

        @Test
        void propagates_checked_exception() {
            final var ex = assertThrows(SQLException.class,
                                        () -> StopWatch.measureChecked(StopWatchTest::throwCheckedException));
            assertEquals("A simulated failure", ex.getMessage());
        }

        @Test
        void throws_when_method_is_null() {
            assertThrows(NullPointerException.class, () -> StopWatch.measureChecked((CheckedSupplier<String>) null));
        }
    }

    @Nested
    class MeasureCheckedRunnableTest {

        @Test
        void returns_null_result() throws Exception {
            final var result = StopWatch.measureChecked(StopWatchTest::voidMethodChecked);
            assertNull(result.getResult());
        }

        @Test
        void records_positive_elapsed_nanos() throws Exception {
            final var result = StopWatch.measureChecked(StopWatchTest::voidMethodChecked);
            assertTrue(result.getElapsedNanos() > 0);
        }

        @Test
        void records_elapsed_time_consistent_with_work_duration() throws Exception {
            final var result = StopWatch.measureChecked(StopWatchTest::simulateWork);
            assertTrue(result.getElapsedMillis() >= LOWER_TOLERANCE_MILLIS,
                       "Expected >= %dms but was %dms".formatted(LOWER_TOLERANCE_MILLIS, result.getElapsedMillis()));
            assertTrue(result.getElapsedMillis() <= UPPER_TOLERANCE_MILLIS,
                       "Expected <= %dms but was %dms".formatted(UPPER_TOLERANCE_MILLIS, result.getElapsedMillis()));
        }

        @Test
        void propagates_checked_exception() {
            final var ex = assertThrows(IOException.class,
                                        () -> StopWatch.measureChecked(StopWatchTest::voidMethodThrowCheckedException));
            assertEquals("A simulated void failure", ex.getMessage());
        }

        @Test
        void throws_when_method_is_null() {
            assertThrows(NullPointerException.class, () -> StopWatch.measureChecked((CheckedRunnable) null));
        }
    }

    @Nested
    class MeasureRepeatedlySupplierTest {

        @Test
        void records_correct_successful_iterations_count() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::returnValue, 10, 2);
            assertEquals(10L, result.getSuccessfulIterations());
        }

        @Test
        void warmup_iterations_excluded_from_statistics() {
            // 5 iterations + 2 warmups = 7 total executions, only 5 counted
            final var result = StopWatch.measureRepeatedly(StopWatchTest::returnValue, 5, 2);
            assertEquals(5L, result.getSuccessfulIterations());
        }

        @Test
        void zero_failed_iterations_when_all_succeed() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::returnValue, 10, 0);
            assertEquals(0, result.getFailedIterations());
            assertFalse(result.hasFailures());
        }

        @Test
        void records_failed_iterations() {
            final var counter = new int[]{ 0 };
            // 9 iterations — calls 3, 6, 9 throw → 3 failures, 6 successes
            final var result = StopWatch.measureRepeatedly(() -> {
                counter[0]++;
                if (counter[0] % 3 == 0) {
                    throw new IllegalStateException("Fail");
                }
                return "Ok";
            }, 9, 0);

            assertNotNull(result);
            assertTrue(result.hasFailures());
            assertEquals(3, result.getFailedIterations());
            assertEquals(6L, result.getSuccessfulIterations());
            assertEquals(9L, result.getTotalIterations());
        }

        @Test
        void last_exception_accessible_after_failures() {
            final var counter = new int[]{ 0 };
            // 6 iterations — calls 2, 4, 6 throw. The last exception is from call 6.
            final var result = StopWatch.measureRepeatedly(() -> {
                counter[0]++;
                if (counter[0] % 2 == 0) {
                    throw new IllegalStateException("Fail " + counter[0]);
                }
                return "Ok";
            }, 6, 0);

            assertTrue(result.hasFailures());
            assertEquals(3, result.getFailedIterations());
            assertTrue(result.getLastException()
                             .isPresent());
            assertInstanceOf(IllegalStateException.class, result.getLastException()
                                                                .get());
            assertEquals("Fail 6", result.getLastException()
                                         .get()
                                         .getMessage());
        }

        @Test
        void always_returns_result() {
            // All iterations fail — result must still be returned
            final var result = StopWatch.measureRepeatedly(() -> {
                throw new IllegalStateException("This always fails");
            }, 5, 0);

            assertNotNull(result);
            assertEquals(5, result.getFailedIterations());
            assertEquals(0L, result.getSuccessfulIterations());
            assertTrue(result.hasFailures());
            assertTrue(result.getLastException()
                             .isPresent());
            assertInstanceOf(IllegalStateException.class, result.getLastException()
                                                                .get());
            assertEquals("This always fails", result.getLastException()
                                                    .get()
                                                    .getMessage());
        }

        @Test
        void throws_when_method_is_null() {
            assertThrows(NullPointerException.class, () -> StopWatch.measureRepeatedly((Supplier<String>) null, 5, 0));
        }
    }

    @Nested
    class MeasureRepeatedlyRunnableTest {

        @Test
        void records_correct_successful_iterations_count() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::voidMethod, 10, 0);
            assertEquals(10L, result.getSuccessfulIterations());
        }

        @Test
        void warmup_iterations_excluded_from_statistics() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::voidMethod, 5, 2);
            assertEquals(5L, result.getSuccessfulIterations());
        }

        @Test
        void zero_failed_iterations_when_all_succeed() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::voidMethod, 10, 0);
            assertEquals(0, result.getFailedIterations());
            assertFalse(result.hasFailures());
        }

        @Test
        void records_failed_iterations() {
            final var counter = new int[]{ 0 };
            // 9 iterations — calls 3, 6, 9 throw → 3 failures, 6 successes
            final var result = StopWatch.measureRepeatedly(() -> {
                counter[0]++;
                if (counter[0] % 3 == 0) {
                    throw new IllegalStateException("Fail");
                }
            }, 9, 0);

            assertNotNull(result);
            assertTrue(result.hasFailures());
            assertEquals(3, result.getFailedIterations());
            assertEquals(6L, result.getSuccessfulIterations());
            assertEquals(9L, result.getTotalIterations());
        }

        @Test
        void last_exception_accessible_after_failures() {
            final var result = StopWatch.measureRepeatedly(() -> {
                throw new IllegalStateException("Fail");
            }, 5, 0);

            assertTrue(result.hasFailures());
            assertTrue(result.getLastException()
                             .isPresent());
            assertInstanceOf(IllegalStateException.class, result.getLastException()
                                                                .get());
        }

        @Test
        void always_returns_result() {
            final var result = StopWatch.measureRepeatedly(() -> {
                throw new IllegalStateException("This always fails");
            }, 5, 0);

            assertNotNull(result);
            assertEquals(5, result.getFailedIterations());
            assertEquals(0L, result.getSuccessfulIterations());
            assertTrue(result.hasFailures());
        }

        @Test
        void throws_when_method_is_null() {
            assertThrows(NullPointerException.class, () -> StopWatch.measureRepeatedly((Runnable) null, 5, 0));
        }
    }

    @Nested
    class MeasureRepeatedlyCheckedSupplierTest {

        @Test
        void records_correct_successful_iterations_count() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::returnValueChecked, 10, 2);
            assertEquals(10L, result.getSuccessfulIterations());
        }

        @Test
        void warmup_iterations_excluded_from_statistics() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::returnValueChecked, 5, 2);
            assertEquals(5L, result.getSuccessfulIterations());
        }

        @Test
        void zero_failed_iterations_when_all_succeed() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::returnValueChecked, 10, 0);
            assertEquals(0, result.getFailedIterations());
            assertFalse(result.hasFailures());
        }

        @Test
        void records_failed_iterations() {
            final var counter = new int[]{ 0 };
            // 6 iterations — calls 2, 4, 6 throw → 3 failures, 3 successes
            final var result = StopWatch.measureRepeatedlyChecked(() -> {
                counter[0]++;
                if (counter[0] % 2 == 0) {
                    throw new SQLException("Fail");
                }
                return "Ok";
            }, 6, 0);

            assertNotNull(result);
            assertTrue(result.hasFailures());
            assertEquals(3, result.getFailedIterations());
            assertEquals(3L, result.getSuccessfulIterations());
            assertEquals(6L, result.getTotalIterations());
        }

        @Test
        void last_exception_accessible_after_failures() {
            final var counter = new int[]{ 0 };
            final var result = StopWatch.measureRepeatedlyChecked(() -> {
                counter[0]++;
                if (counter[0] == 3) {
                    throw new SQLException("Checked fail");
                }
                return "Ok";
            }, 5, 0);

            assertTrue(result.hasFailures());
            assertTrue(result.getLastException()
                             .isPresent());
            assertInstanceOf(SQLException.class, result.getLastException()
                                                       .get());
            assertEquals("Checked fail", result.getLastException()
                                               .get()
                                               .getMessage());
        }

        @Test
        void always_returns_result() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::throwCheckedException, 5, 0);

            assertNotNull(result);
            assertEquals(5, result.getFailedIterations());
            assertEquals(0L, result.getSuccessfulIterations());
        }

        @Test
        void throws_when_method_is_null() {
            assertThrows(NullPointerException.class,
                         () -> StopWatch.measureRepeatedlyChecked((CheckedSupplier<String>) null, 5, 0));
        }
    }

    @Nested
    class MeasureRepeatedlyCheckedRunnableTest {

        @Test
        void records_correct_successful_iterations_count() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::voidMethodChecked, 10, 0);
            assertEquals(10L, result.getSuccessfulIterations());
        }

        @Test
        void warmup_iterations_excluded_from_statistics() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::voidMethodChecked, 5, 2);
            assertEquals(5L, result.getSuccessfulIterations());
        }

        @Test
        void zero_failed_iterations_when_all_succeed() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::voidMethodChecked, 10, 0);
            assertEquals(0, result.getFailedIterations());
            assertFalse(result.hasFailures());
        }

        @Test
        void records_failed_iterations() {
            final var counter = new int[]{ 0 };
            // 6 iterations — calls 2, 4, 6 throw → 3 failures, 3 successes
            final var result = StopWatch.measureRepeatedlyChecked(() -> {
                counter[0]++;
                if (counter[0] % 2 == 0) {
                    throw new IOException("Fail");
                }
            }, 6, 0);

            assertNotNull(result);
            assertTrue(result.hasFailures());
            assertEquals(3, result.getFailedIterations());
            assertEquals(3L, result.getSuccessfulIterations());
            assertEquals(6L, result.getTotalIterations());
        }

        @Test
        void last_exception_accessible_after_failures() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::voidMethodThrowCheckedException, 3, 0);

            assertTrue(result.hasFailures());
            assertTrue(result.getLastException()
                             .isPresent());
            assertInstanceOf(IOException.class, result.getLastException()
                                                      .get());
        }

        @Test
        void always_returns_result() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::voidMethodThrowCheckedException, 5, 0);

            assertNotNull(result);
            assertEquals(5, result.getFailedIterations());
            assertEquals(0L, result.getSuccessfulIterations());
        }

        @Test
        void throws_when_method_is_null() {
            assertThrows(NullPointerException.class,
                         () -> StopWatch.measureRepeatedlyChecked((CheckedRunnable) null, 5, 0));
        }
    }

    /**
     * Verifies {@link dev.badprogrammer.timing.type.TimingStatistics}'s own computed values (min/max/average/total,
     * millis-nanos consistency) — tested once here rather than duplicated across all four repeated-measurement
     * variants above. Every variant delegates into the same underlying statistics computation, regardless of which
     * {@code StopWatch} entry point produced it.
     */
    @Nested
    class TimingStatisticsComputationTest {

        @Test
        void statistics_cover_successful_iterations_only() {
            final var counter = new int[]{ 0 };
            // Even calls throw — only odd calls contribute to stats
            final var result = StopWatch.measureRepeatedly(() -> {
                counter[0]++;
                if (counter[0] % 2 == 0) {
                    throw new IllegalStateException("Fail");
                }
                return "Ok";
            }, 6, 0);

            // 3 successes, 3 failures — stats should only reflect 3
            assertEquals(3L, result.getSuccessfulIterations());
            assertTrue(result.getTotalNanos() > 0);
        }

        @Test
        void min_is_less_than_or_equal_to_max() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::returnValue, 10, 0);
            assertTrue(result.getMinNanos() <= result.getMaxNanos());
        }

        @Test
        void average_is_between_min_and_max() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::returnValue, 10, 0);
            assertTrue(result.getAverageNanos() >= result.getMinNanos());
            assertTrue(result.getAverageNanos() <= result.getMaxNanos());
        }

        @Test
        void total_elapsed_time_is_positive() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::returnValue, 10, 0);
            assertTrue(result.getTotalNanos() > 0);
        }

        @Test
        void millis_are_consistent_with_nanos() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::returnValue, 10, 0);
            assertEquals(TimeUnit.NANOSECONDS.toMillis(result.getTotalNanos()), result.getTotalMillis());
            assertEquals(TimeUnit.NANOSECONDS.toMillis(result.getMinNanos()), result.getMinMillis());
            assertEquals(TimeUnit.NANOSECONDS.toMillis(result.getMaxNanos()), result.getMaxMillis());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // ----------------------------------------------- Private Helpers  ------------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------

    // Intentional — Thread.sleep() here generates real elapsed time to measure, not a substitute for thread
    // synchronization (which this rule is meant to catch). There's nothing to synchronize with.
    @SuppressWarnings("java:S2925")
    private static void simulateWork() {
        try {
            Thread.sleep(SLEEP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread()
                  .interrupt();
        }
    }

    private static String returnValue() {
        return "Result";
    }

    @SuppressWarnings("java:S1130")
    private static String returnValueChecked() throws SQLException {
        return "Result";
    }

    private static String throwCheckedException() throws SQLException {
        throw new SQLException("A simulated failure");
    }

    private static String throwUncheckedException() {
        throw new IllegalStateException("A simulated failure");
    }

    private static void voidMethod() {
        // does nothing — used for Runnable tests
    }

    @SuppressWarnings("java:S1130")
    private static void voidMethodChecked() throws IOException {
        // does nothing — used for CheckedRunnable tests
    }

    private static void voidMethodThrowCheckedException() throws IOException {
        throw new IOException("A simulated void failure");
    }
}
