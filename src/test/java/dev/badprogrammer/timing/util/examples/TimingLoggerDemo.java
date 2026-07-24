package dev.badprogrammer.timing.util.examples;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.badprogrammer.timing.util.TimingLogger;

/**
 * Demonstrates TimingLogger usage across all supported scenarios.
 *
 * <p>Notice that in every case the method body is completely unchanged — only one try-with-resources line is added at
 * the top.</p>
 */
public class TimingLoggerDemo {

    private static final Logger logger = LoggerFactory.getLogger(TimingLoggerDemo.class);

    public static void main(String[] args) throws Exception {

        separator("SCENARIO 1: void, no checked exception");
        publishEvent();

        separator("SCENARIO 2: returns value, no checked exception");
        getUserById(101);

        separator("SCENARIO 3: void, checked exception");
        closeConnection();

        separator("SCENARIO 4: returns value, checked exception");
        getConnection();

        separator("SCENARIO 5: slow threshold breach — expect WARN in the log");
        fetchWithSlowThreshold();

        separator("SCENARIO 6: method throws — timing still logged");
        try {
            methodThatThrows();
        } catch (SQLException e) {
            System.out.println("Exception propagated correctly: " + e.getMessage());
        }

        separator("SCENARIO 7: multiple methods, same logger");
        publishEvent();
        getUserById(101);
        closeConnection();
        getConnection();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // ------------------------------------------- Private Simulated Methods -------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Returns a value and does not declare a checked exception.
     */
    private static String getUserById(int id) {
        try (TimingLogger ignored = TimingLogger.start("getUserById", logger)) {
            simulateWork(2);
            return "User#" + id;
        }
    }

    /**
     * Returns void and does not declare a checked exception.
     */
    private static void publishEvent() {
        try (TimingLogger ignored = TimingLogger.start("publishEvent", logger)) {
            simulateWork(3);
        }
    }

    /**
     * Returns a value and declares a checked exception.
     */
    @SuppressWarnings("java:S1130")
    private static String getConnection() throws SQLException {
        try (TimingLogger ignored = TimingLogger.start("getConnection", logger)) {
            simulateWork(10);
            return "Connection#" + System.nanoTime();
        }
    }

    /**
     * Returns void and declares a checked exception.
     */
    @SuppressWarnings("java:S1130")
    private static void closeConnection() throws SQLException {
        try (TimingLogger ignored = TimingLogger.start("closeConnection", logger)) {
            simulateWork(5);
        }
    }

    /**
     * Returns a value, with a slow threshold. Logs at WARN if elapsed time exceeds 5ms.
     */
    @SuppressWarnings("java:S1130")
    private static String fetchWithSlowThreshold() throws SQLException {
        try (TimingLogger ignored = TimingLogger.start("fetchWithSlowThreshold", logger, 5)) {
            simulateWork(20); // deliberately slow — will trigger WARN
            return "Result#" + System.nanoTime();
        }
    }

    /**
     * Method that throws. The exception propagates naturally.
     */
    private static String methodThatThrows() throws SQLException {
        try (TimingLogger ignored = TimingLogger.start("methodThatThrows", logger)) {
            simulateWork(5);
            throw new SQLException("Simulated connection failure");
        }
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
        System.out.println("-".repeat(60));
        System.out.println(title);
        System.out.println("-".repeat(60));
    }
}
