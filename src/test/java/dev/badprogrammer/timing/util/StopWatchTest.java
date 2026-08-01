package dev.badprogrammer.timing.util;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.badprogrammer.timing.function.CheckedRunnable;
import dev.badprogrammer.timing.function.CheckedSupplier;
import dev.badprogrammer.timing.type.TimingStatistics;

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
 * <p>Behavior shared across all four repeated-measurement variants — statistical computation (min/max/average/total,
 * millis-nanos consistency) and warmup handling (timing exclusion, failure exclusion) — is verified once in
 * {@link SharedRepeatedMeasurementBehaviorTest} rather than duplicated across every variant. All four converge on the
 * exact same underlying loop and {@link java.util.LongSummaryStatistics}-based computation — repeating those assertions
 * per variant would test the same code path four times without adding real confidence.</p>
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class StopWatchTest {

    private static final int  SLEEP_MILLIS           = 100;
    private static final long LOWER_TOLERANCE_MILLIS = 90;
    private static final long UPPER_TOLERANCE_MILLIS = 150;

    @Test
    void private_constructor_throws_when_invoked_via_reflection() throws Exception {
        final var constructor = StopWatch.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        // Reflection wraps the constructor's actual exception inside InvocationTargetException — use getCause() to
        // unwrap and verify it's genuinely UnsupportedOperationException.
        final var ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }

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
    class MeasureRepeatedlyRunnableTest {

        @Test
        void records_correct_iteration_counts_when_all_succeed() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::voidMethod, 10, 0);
            assertEquals(10L, result.getSuccessfulIterations());
        }

        @Test
        void zero_failed_iterations_when_all_succeed() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::voidMethod, 10, 0);
            assertEquals(0, result.getFailedIterations());
            assertFalse(result.hasFailures());
        }

        @Test
        void records_correct_iteration_counts_when_some_succeed_and_some_fail() {
            final var counter = new AtomicInteger(0);
            // 9 iterations — calls 3, 6, 9 throw → 3 failures, 6 successes
            final var result = StopWatch.measureRepeatedly(() -> {
                if (counter.incrementAndGet() % 3 == 0) {
                    throw new IllegalStateException("Fail");
                }
            }, 9, 0);

            assertEquals(6L, result.getSuccessfulIterations());
            assertEquals(3, result.getFailedIterations());
            assertTrue(result.hasFailures());
        }

        @Test
        void last_exception_accessible_after_failures() {
            final var result = StopWatch.measureRepeatedly(() -> {
                throw new IllegalStateException("Fail");
            }, 5, 0);

            assertTrue(result.getLastException()
                             .isPresent());
            assertInstanceOf(IllegalStateException.class, result.getLastException()
                                                                .get());
        }

        @Test
        void records_correct_iteration_counts_when_all_fail() {
            // Every iteration fails — measureRepeatedly still returns normally rather than throwing.
            final var result = StopWatch.measureRepeatedly(() -> {
                throw new IllegalStateException("This always fails");
            }, 5, 0);

            assertNotNull(result);
            assertEquals(0L, result.getSuccessfulIterations());
            assertEquals(5, result.getFailedIterations());
            assertTrue(result.hasFailures());
        }

        @Test
        void throws_when_method_is_null() {
            assertThrows(NullPointerException.class, () -> StopWatch.measureRepeatedly((Runnable) null, 5, 0));
        }
    }

    @Nested
    class MeasureRepeatedlySupplierTest {

        @Test
        void records_correct_iteration_counts_when_all_succeed() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::returnValue, 10, 0);
            assertEquals(10L, result.getSuccessfulIterations());
        }

        @Test
        void zero_failed_iterations_when_all_succeed() {
            final var result = StopWatch.measureRepeatedly(StopWatchTest::returnValue, 10, 0);
            assertEquals(0, result.getFailedIterations());
            assertFalse(result.hasFailures());
        }

        @Test
        void records_correct_iteration_counts_when_some_succeed_and_some_fail() {
            final var counter = new AtomicInteger(0);
            // 9 iterations — calls 3, 6, 9 throw → 3 failures, 6 successes
            final var result = StopWatch.measureRepeatedly(() -> {
                if (counter.incrementAndGet() % 3 == 0) {
                    throw new IllegalStateException("Fail");
                }
                return "Ok";
            }, 9, 0);

            assertEquals(6L, result.getSuccessfulIterations());
            assertEquals(3, result.getFailedIterations());
            assertTrue(result.hasFailures());
        }

        @Test
        void last_exception_accessible_after_failures() {
            final var counter = new AtomicInteger(0);
            // 6 iterations — calls 2, 4, 6 throw. The last exception is from call 6.
            final var result = StopWatch.measureRepeatedly(() -> {
                if (counter.incrementAndGet() % 2 == 0) {
                    throw new IllegalStateException("Fail " + counter.get());
                }
                return "Ok";
            }, 6, 0);

            assertTrue(result.getLastException()
                             .isPresent());
            assertInstanceOf(IllegalStateException.class, result.getLastException()
                                                                .get());
            assertEquals("Fail 6", result.getLastException()
                                         .get()
                                         .getMessage());
        }

        @Test
        void records_correct_iteration_counts_when_all_fail() {
            // Every iteration fails — measureRepeatedly still returns normally rather than throwing.
            final var result = StopWatch.measureRepeatedly(() -> {
                throw new IllegalStateException("This always fails");
            }, 5, 0);

            assertNotNull(result);
            assertEquals(0L, result.getSuccessfulIterations());
            assertEquals(5, result.getFailedIterations());
            assertTrue(result.hasFailures());
        }

        @Test
        void throws_when_method_is_null() {
            assertThrows(NullPointerException.class, () -> StopWatch.measureRepeatedly((Supplier<String>) null, 5, 0));
        }
    }

    @Nested
    class MeasureRepeatedlyCheckedRunnableTest {

        @Test
        void records_correct_iteration_counts_when_all_succeed() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::voidMethodChecked, 10, 0);
            assertEquals(10L, result.getSuccessfulIterations());
        }

        @Test
        void zero_failed_iterations_when_all_succeed() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::voidMethodChecked, 10, 0);
            assertEquals(0, result.getFailedIterations());
            assertFalse(result.hasFailures());
        }

        @Test
        void records_correct_iteration_counts_when_some_succeed_and_some_fail() {
            final var counter = new AtomicInteger(0);
            // 6 iterations — calls 2, 4, 6 throw → 3 failures, 3 successes
            final var result = StopWatch.measureRepeatedlyChecked(() -> {
                if (counter.incrementAndGet() % 2 == 0) {
                    throw new IOException("Fail");
                }
            }, 6, 0);

            assertEquals(3L, result.getSuccessfulIterations());
            assertEquals(3, result.getFailedIterations());
            assertTrue(result.hasFailures());
        }

        @Test
        void last_exception_accessible_after_failures() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::voidMethodThrowCheckedException, 3, 0);

            assertTrue(result.getLastException()
                             .isPresent());
            assertInstanceOf(IOException.class, result.getLastException()
                                                      .get());
        }

        @Test
        void records_correct_iteration_counts_when_all_fail() {
            // Every iteration fails — measureRepeatedlyChecked still returns normally rather than throwing.
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::voidMethodThrowCheckedException, 5, 0);

            assertNotNull(result);
            assertEquals(0L, result.getSuccessfulIterations());
            assertEquals(5, result.getFailedIterations());
            assertTrue(result.hasFailures());
        }

        @Test
        void throws_when_method_is_null() {
            assertThrows(NullPointerException.class,
                         () -> StopWatch.measureRepeatedlyChecked((CheckedRunnable) null, 5, 0));
        }
    }

    @Nested
    class MeasureRepeatedlyCheckedSupplierTest {

        @Test
        void records_correct_iteration_counts_when_all_succeed() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::returnValueChecked, 10, 0);
            assertEquals(10L, result.getSuccessfulIterations());
        }

        @Test
        void zero_failed_iterations_when_all_succeed() {
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::returnValueChecked, 10, 0);
            assertEquals(0, result.getFailedIterations());
            assertFalse(result.hasFailures());
        }

        @Test
        void records_correct_iteration_counts_when_some_succeed_and_some_fail() {
            final var counter = new AtomicInteger(0);
            // 6 iterations — calls 2, 4, 6 throw → 3 failures, 3 successes
            final var result = StopWatch.measureRepeatedlyChecked(() -> {
                if (counter.incrementAndGet() % 2 == 0) {
                    throw new SQLException("Fail");
                }
                return "Ok";
            }, 6, 0);

            assertEquals(3L, result.getSuccessfulIterations());
            assertEquals(3, result.getFailedIterations());
            assertTrue(result.hasFailures());
        }

        @Test
        void last_exception_accessible_after_failures() {
            final var counter = new AtomicInteger(0);
            final var result = StopWatch.measureRepeatedlyChecked(() -> {
                if (counter.incrementAndGet() == 3) {
                    throw new SQLException("Checked fail");
                }
                return "Ok";
            }, 5, 0);

            assertTrue(result.getLastException()
                             .isPresent());
            assertInstanceOf(SQLException.class, result.getLastException()
                                                       .get());
            assertEquals("Checked fail", result.getLastException()
                                               .get()
                                               .getMessage());
        }

        @Test
        void records_correct_iteration_counts_when_all_fail() {
            // Every iteration fails — measureRepeatedlyChecked still returns normally rather than throwing.
            final var result = StopWatch.measureRepeatedlyChecked(StopWatchTest::throwCheckedException, 5, 0);

            assertNotNull(result);
            assertEquals(0L, result.getSuccessfulIterations());
            assertEquals(5, result.getFailedIterations());
            assertTrue(result.hasFailures());
        }

        @Test
        void throws_when_method_is_null() {
            assertThrows(NullPointerException.class,
                         () -> StopWatch.measureRepeatedlyChecked((CheckedSupplier<String>) null, 5, 0));
        }

        @Test
        void warmup_failure_is_not_counted_for_checked_supplier() {
            final var counter = new AtomicInteger(0);
            final var result = StopWatch.measureRepeatedlyChecked(() -> {
                // First 2 calls (warmup) throw; remaining 5 succeed — proves a warmup failure never increments
                // failedIterations or sets lastException. Verified separately from the Supplier variant, since
                // measureRepeatedlyChecked is a genuinely separate implementation, not a delegate.
                if (counter.incrementAndGet() <= 2) {
                    throw new SQLException("Warmup fail");
                }
                return "Ok";
            }, 5, 2);

            assertEquals(5L, result.getSuccessfulIterations());
            assertEquals(0, result.getFailedIterations());
            assertFalse(result.hasFailures());
            assertTrue(result.getLastException()
                             .isEmpty());
        }

        @Test
        void warmup_iterations_excluded_from_timing_for_checked_supplier() {
            final var callCount = new AtomicInteger(0);
            final var result = StopWatch.measureRepeatedlyChecked(() -> {
                callCount.incrementAndGet();
                return "Ok";
            }, 5, 2);

            assertEquals(7, callCount.get());
            assertEquals(5L, result.getTotalIterations());
        }
    }

    /**
     * Verifies statistical computation (min/max/average/total, millis-nanos consistency) shared across all four
     * repeated-measurement entry points, tested once here rather than duplicated across every variant above. Every
     * variant produces a {@link TimingStatistics}, and these computed values behave identically regardless of which
     * {@code StopWatch} method produced the object.
     *
     * <p><b>Warmup handling is not tested here for all four variants</b> — {@code measureRepeatedly(Supplier, ...)}
     * and {@code measureRepeatedlyChecked(CheckedSupplier, ...)} are separate, independently-compiled implementations
     * (they catch different exception types), not one shared loop. {@code Runnable}/{@code CheckedRunnable} delegate
     * into their respective {@code Supplier}/{@code CheckedSupplier} counterparts, so warmup handling is verified once
     * per pair — here for the {@code Supplier} pair, and separately in
     * {@link MeasureRepeatedlyCheckedSupplierTest#warmup_failure_is_not_counted_for_checked_supplier()} for the
     * {@code CheckedSupplier}
     * pair.</p>
     */
    @Nested
    class SharedRepeatedMeasurementBehaviorTest {

        @Test
        void warmup_failure_is_not_counted() {
            final var counter = new AtomicInteger(0);
            final var result = StopWatch.measureRepeatedly(() -> {
                // First 2 calls (warmup) throw; remaining 5 succeed — proves a warmup failure never increments
                // failedIterations or sets lastException.
                if (counter.incrementAndGet() <= 2) {
                    throw new IllegalStateException("Warmup fail");
                }
                return "Ok";
            }, 5, 2);

            assertEquals(5L, result.getSuccessfulIterations());
            assertEquals(0, result.getFailedIterations());
            assertFalse(result.hasFailures());
            assertTrue(result.getLastException()
                             .isEmpty());
        }

        @Test
        void warmup_iterations_excluded_from_timing() {
            final var callCount = new AtomicInteger(0);
            final var result = StopWatch.measureRepeatedly(() -> {
                callCount.incrementAndGet();
                return "Ok";
            }, 5, 2);

            assertEquals(7, callCount.get());
            assertEquals(5L, result.getTotalIterations());
        }

        @Test
        void statistics_cover_successful_iterations_only() {
            final var counter = new AtomicInteger(0);
            // Even calls throw — only odd calls contribute to stats
            final var result = StopWatch.measureRepeatedly(() -> {
                if (counter.incrementAndGet() % 2 == 0) {
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
