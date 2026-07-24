package dev.badprogrammer.timing.util.examples;

import java.sql.SQLException;

import dev.badprogrammer.timing.type.TimedResult;
import dev.badprogrammer.timing.type.TimingStatistics;
import dev.badprogrammer.timing.util.StopWatch;

/**
 * Demonstrates all eight variants of {@link StopWatch}.
 *
 * <pre>
 *   measure()                   — Runnable  (returns void, no checked exception)
 *   measure()                   — Supplier  (returns value, no checked exception)
 *   measureChecked()            — CheckedRunnable (returns void, checked exception)
 *   measureChecked()            — CheckedSupplier (returns value, checked exception)
 *   measureRepeatedly()         — Runnable  (repeated, returns void, no checked exception)
 *   measureRepeatedly()         — Supplier  (repeated, returns value, no checked exception)
 *   measureRepeatedlyChecked()  — CheckedRunnable (repeated, returns void, checked exception)
 *   measureRepeatedlyChecked()  — CheckedSupplier (repeated, returns value, checked exception)
 * </pre>
 */
public class StopWatchDemo {

    /**
     * Counter to throw exception on every 5th call — demonstrates failure tracking in repeated measurement.
     */
    private static int callCounter = 0;

    public static void main(String[] args) throws Exception {

        // SINGLE MEASUREMENT
        separator("SCENARIO 1: measure(Runnable) — returns void, no checked exception");
        TimedResult<Void> result1 = StopWatch.measure(StopWatchDemo::publishEvent);
        System.out.println(result1);
        System.out.println("Result: " + result1.getResult() + " (always null for void methods)");
        System.out.printf("ElapsedMillis: %dms, ElapsedNanos: %dns", result1.getElapsedMillis(),
                          result1.getElapsedNanos());

        separator("SCENARIO 2: measure(Supplier) — returns value, no checked exception");
        TimedResult<String> result2 = StopWatch.measure(() -> StopWatchDemo.getUserById(123));
        System.out.println(result2);
        System.out.println("Result: " + result2.getResult());
        System.out.printf("ElapsedMillis: %dms, ElapsedNanos: %dns", result2.getElapsedMillis(),
                          result2.getElapsedNanos());

        separator("SCENARIO 3: measureChecked(CheckedRunnable) — returns void, checked exception");
        TimedResult<Void> result3 = StopWatch.measureChecked(StopWatchDemo::closeConnection);
        System.out.println(result3);
        System.out.println("Result: " + result3.getResult() + " (always null for void methods)");
        System.out.printf("ElapsedMillis: %dms, ElapsedNanos: %dns", result3.getElapsedMillis(),
                          result3.getElapsedNanos());

        separator("SCENARIO 4: measureChecked(CheckedSupplier) — returns value, checked exception");
        TimedResult<String> result4 = StopWatch.measureChecked(StopWatchDemo::getConnection);
        System.out.println(result4);
        System.out.println("Result: " + result4.getResult());
        System.out.printf("ElapsedMillis: %dms, ElapsedNanos: %dns", result4.getElapsedMillis(),
                          result4.getElapsedNanos());

        // REPEATED MEASUREMENT
        separator("SCENARIO 5: measureRepeatedly(Runnable) — 20 iterations, 3 warmup");
        TimingStatistics stats1 = StopWatch.measureRepeatedly(StopWatchDemo::publishEvent, 20, 3);
        System.out.println(stats1);

        separator("SCENARIO 6: measureRepeatedly(Supplier) — 20 iterations, 3 warmup");
        TimingStatistics stats2 = StopWatch.measureRepeatedly(() -> StopWatchDemo.getUserById(102), 20, 3);
        System.out.println(stats2);

        separator("SCENARIO 7: measureRepeatedlyChecked(CheckedRunnable) — 20 iterations, 3 warmup");
        TimingStatistics stats3 = StopWatch.measureRepeatedlyChecked(StopWatchDemo::closeConnection, 20, 3);
        System.out.println(stats3);

        separator("SCENARIO 8: measureRepeatedlyChecked(CheckedSupplier) — 20 iterations, 3 warmup");
        TimingStatistics stats4 = StopWatch.measureRepeatedlyChecked(StopWatchDemo::getConnection, 20, 3);
        System.out.println(stats4);

        separator("SCENARIO 9: measureRepeatedlyChecked with failures (every fifth call throws)");
        TimingStatistics stats5 = StopWatch.measureRepeatedlyChecked(StopWatchDemo::unreliableService, 20, 0);
        System.out.println(stats5);
        if (stats5.hasFailures()) {
            stats5.getLastException()
                  .ifPresent(e -> System.out.printf("%d of %d iterations failed. Last exception: %s",
                                                    stats5.getFailedIterations(), stats5.getTotalIterations(), e));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // ------------------------------------------- Private Simulated Methods -------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Returns a value, does not declare a checked exception → measure / measureRepeatedly.
     */
    private static String getUserById(int id) {
        simulateWork(2);
        return "User#" + id;
    }

    /**
     * Returns void and does not declare a checked exception → measure / measureRepeatedly.
     */
    private static void publishEvent() {
        simulateWork(3);
    }

    /**
     * Returns a value and declares a checked exception → measureChecked / measureRepeatedlyChecked.
     */
    @SuppressWarnings("java:S1130")
    private static String getConnection() throws SQLException {
        simulateWork(10);
        return "Connection#" + System.nanoTime();
    }

    /**
     * Returns void and declares a checked exception → measureChecked / measureRepeatedlyChecked.
     */
    @SuppressWarnings("java:S1130")
    private static void closeConnection() throws SQLException {
        simulateWork(5);
    }

    /**
     * Returns a value and declares a checked exception → measureRepeatedlyChecked.
     */
    private static String unreliableService() throws SQLException {
        callCounter++;
        simulateWork(5);
        if (callCounter % 5 == 0) {
            throw new SQLException("Simulated failure on call " + callCounter);
        }
        return "Result#" + callCounter;
    }

    // Intentional — Thread.sleep() here generates real elapsed time to measure, not a substitute for thread
    // synchronization (which this rule is meant to catch). There's nothing to synchronize with.
    @SuppressWarnings("java:S2925")
    private static void simulateWork(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread()
                  .interrupt();
        }
    }

    private static void separator(String title) {
        System.out.println();
        System.out.println("-".repeat(80));
        System.out.println(title);
        System.out.println("-".repeat(80));
    }
}
