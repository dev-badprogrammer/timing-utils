package dev.badprogrammer.timing.util;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

/**
 * A non-invasive timing utility that measures the elapsed time of a method body and writes it to the log automatically
 * when the block exits.
 *
 * <p>Designed to live permanently in production code. Wrap the method body in a try-with-resources block, and the
 * wrapped code is timed automatically on exit — no changes to return statements or catch blocks are required.</p>
 *
 * <p><b>No side effects</b> — this class does nothing beyond timing. It does not retry, cache, modify, or intercept
 * the wrapped code in any way. Any side effects observed are the wrapped code's own.</p>
 *
 * <p>Normal code:
 * <blockquote>
 * {@snippet lang = "java":
 * public Connection getConnection() throws SQLException {
 *     return dbUtils.getConnection();
 * }
 *}
 * </blockquote>
 *
 * <p>Timed code:
 * <blockquote>
 * {@snippet lang = "java":
 * public Connection getConnection() throws SQLException {
 *     try (TimingLogger ignored = TimingLogger.start("getConnection", logger)) {
 *         return dbUtils.getConnection();
 *     }
 * }
 *}
 * </blockquote>
 *
 * <p>Timed code with slow call detection:
 * <blockquote>
 * {@snippet lang = "java":
 * public Connection getConnection() throws SQLException {
 *     try (TimingLogger ignored = TimingLogger.start("getConnection", logger, 1_000)) {
 *         return dbUtils.getConnection();
 *     }
 * }
 *}
 * </blockquote>
 *
 * <p>When the try block exits — normally or via exception — {@code close()} fires automatically and emits a log line
 * like:</p>
 * <pre>
 * 00:00:00.000 [main] DEBUG dev.badprogrammer.timing.util.examples.TimingLoggerDemo -- TIMED | getConnection |
 * Elapsed = 15ms (15318058ns)
 * </pre>
 *
 * <h2><b>Log Levels</b></h2>
 * <ul>
 *   <li>Normal execution logs at {@code DEBUG}. At production log level ({@code INFO} or {@code WARN}), timing lines
 *   are invisible unless you need them.</li>
 *   <li>If a slow threshold is supplied and elapsed time exceeds it, the line escalates to {@code WARN} automatically,
 *   surfacing in production logs without any additional code.</li>
 * </ul>
 *
 * <h2><b>Why the caller's logger is mandatory</b></h2>
 *
 * <p>Passing your own logger ensures timing lines appear under your class name in the logs — alongside the class's
 * other log statements, filterable by package, and routable by your existing logging configuration. A shared internal
 * logger would make all timing lines appear under {@code dev.badprogrammer.timing.util.TimingLogger}, losing all class
 * context.</p>
 *
 * <h2><b>Success and Failure</b></h2>
 *
 * <p>{@code close()} always logs elapsed time regardless of how the block exits. If the method throws, the exception
 * surfaces through your application's existing exception handling and logging — {@code TimingLogger} does not
 * duplicate it. This keeps the utility truly non-invasive: wrap the method body, nothing else touched.</p>
 *
 * <h2><b>Thread Safety</b></h2>
 *
 * <p>Each {@code TimingLogger} instance is created per method invocation and never shared across threads. Instances
 * are inherently thread-safe.</p>
 *
 * @see StopWatch
 */
public final class TimingLogger implements AutoCloseable {

    /**
     * Sentinel value meaning no slow threshold is configured.
     */
    private static final long NO_THRESHOLD = 0L;

    /**
     * The caller-supplied description of what is being timed.
     */
    private final String label;

    /**
     * The caller's own logger — timing lines appear under the calling class name, not {@code TimingLogger}'s.
     */
    private final Logger logger;

    /**
     * Elapsed millis beyond which the log line escalates from DEBUG to WARN. Zero means no threshold — always log at
     * DEBUG.
     */
    private final long slowThresholdMillis;

    /**
     * Captured once at construction — when the try block is entered.
     */
    private final long startNanos;

    /**
     * Constructs a {@code TimingLogger}, capturing the start time as the very last step.
     *
     * @param label               a short description of what is being timed, typically the method name
     * @param logger              the caller's own logger
     * @param slowThresholdMillis the elapsed time (in milliseconds) above which the log line escalates to {@code WARN};
     *                            use {@link #NO_THRESHOLD} to disable slow-call detection
     */
    private TimingLogger(final String label, final Logger logger, final long slowThresholdMillis) {
        this.label               = label;
        this.logger              = logger;
        this.slowThresholdMillis = slowThresholdMillis;

        // Start the clock at the last possible moment before the method body executes.
        this.startNanos = System.nanoTime();
    }

    /**
     * Starts timing a method body. The elapsed time is logged when the try-with-resources block exits.
     *
     * <p>Logs at {@code DEBUG} on exit. Use {@link #start(String, Logger, long)} if you want slow-call detection.</p>
     *
     * @param label  a short description of what is being timed, typically the method name (e.g.,
     *               {@code "getConnection"}) or a custom label describing the operation (e.g.,
     *               {@code "connection-pool-lookup"}); must not be {@code null}, empty, or whitespace-only
     * @param logger the caller's own logger; timing lines appear under the calling class name in the log output
     *
     * @return a {@code TimingLogger} to be used in a try-with-resources block
     *
     * @throws IllegalArgumentException if {@code label} is {@code null}, empty, or whitespace-only, or if
     *                                  {@code logger} is {@code null}
     */
    public static TimingLogger start(final String label, final Logger logger) {
        return start(label, logger, NO_THRESHOLD);
    }

    /**
     * Starts timing a method body with slow-call detection.
     *
     * <p>If the elapsed time exceeds {@code slowThresholdMillis}, the log line escalates from {@code DEBUG} to
     * {@code WARN} automatically — surfacing in production logs without any code change.</p>
     *
     * <p>Warn if connection acquisition takes longer than 1 second
     * <blockquote>
     * {@snippet lang = "java":
     * public Connection getConnection() throws SQLException {
     *     try (TimingLogger ignored = TimingLogger.start("getConnection", logger, 1_000)) {
     *         return dbUtils.getConnection();
     *     }
     * }
     *}
     * </blockquote>
     *
     * @param label               a short description of what is being timed, typically the method name (e.g.,
     *                            {@code "getConnection"}) or a custom label describing the operation (e.g.,
     *                            {@code "connection-pool-lookup"}); must not be {@code null}, empty, or whitespace-only
     * @param logger              the caller's own logger; timing lines appear under the calling class name in the log
     *                            output
     * @param slowThresholdMillis elapsed time in milliseconds beyond which the log line automatically escalates to
     *                            {@code WARN}; pass {@code 0} to disable threshold checking, equivalent to calling
     *                            {@link #start(String, Logger)}
     *
     * @return a {@code TimingLogger} to be used in a try-with-resources block
     *
     * @throws IllegalArgumentException if {@code label} is {@code null}, empty, or whitespace-only, or
     *                                  if {@code logger} is {@code null}, or
     *                                  if {@code slowThresholdMillis} is {@code < 0}
     */
    public static TimingLogger start(final String label, final Logger logger, final long slowThresholdMillis) {
        validateArguments(label, logger, slowThresholdMillis);
        return new TimingLogger(label, logger, slowThresholdMillis);
    }

    /**
     * Fires automatically when the try-with-resources block exits — whether normally or via exception — and writes the
     * elapsed time to the log.
     *
     * <p>This method <em>never throws a checked or unchecked exception</em>. A {@code close()} that throws inside
     * try-with-resources suppresses the original exception from the method body, which would silently swallow real
     * errors. Any unexpected {@code Exception} inside this method is caught and logged separately at {@code ERROR}
     * level.</p>
     */
    @Override
    public void close() {
        try {
            final var elapsedNanos   = System.nanoTime() - startNanos;
            final var elapsedMillis  = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
            final var elapsedDisplay = "Elapsed = %dms (%dns)".formatted(elapsedMillis, elapsedNanos);

            if (exceedsSlowThreshold(elapsedMillis)) {
                // elapsedMillis exceeded slowThresholdMillis — escalate to WARN, so this surfaces in production logs
                logger.warn("TIMED | {} | {} | SLOW", label, elapsedDisplay);
            } else {
                // No threshold configured, or elapsedMillis did not exceed slowThresholdMillis — stays at DEBUG, so
                // this is invisible in production logs
                logger.debug("TIMED | {} | {}", label, elapsedDisplay);
            }
        } catch (Exception e) {
            // Last-resort safety net — close() must never throw under any circumstance.
            logger.error("TimingLogger failed to record elapsed time for: {}", label, e);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // ------------------------------------------------ Private Helpers ------------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Validates arguments shared by all {@code start} variants.
     *
     * @throws IllegalArgumentException if {@code label} is {@code null}, empty, or whitespace-only, or
     *                                  if {@code logger} is {@code null}, or
     *                                  if {@code slowThresholdMillis} is {@code < 0}
     */
    private static void validateArguments(final String label, final Logger logger, final long slowThresholdMillis) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be null, empty, or whitespace-only");
        }
        if (logger == null) {
            throw new IllegalArgumentException("logger must not be null");
        }
        if (slowThresholdMillis < 0) {
            throw new IllegalArgumentException(
                    "slowThresholdMillis must be >= 0, but was %d".formatted(slowThresholdMillis));
        }
    }

    /**
     * Returns whether the given elapsed time exceeds the configured slow threshold.
     *
     * <p>Always {@code false} when {@code slowThresholdMillis} is {@code 0} (the {@link #NO_THRESHOLD} sentinel) — a
     * disabled threshold never counts as exceeded, regardless of how long the invocation took.</p>
     *
     * @param elapsedMillis the elapsed time of the just-completed invocation, in milliseconds
     *
     * @return {@code true} if a threshold is configured and {@code elapsedMillis} exceeds it; {@code false} otherwise
     */
    private boolean exceedsSlowThreshold(final long elapsedMillis) {
        return slowThresholdMillis > 0 && elapsedMillis > slowThresholdMillis;
    }
}
