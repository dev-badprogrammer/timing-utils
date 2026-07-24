package dev.badprogrammer.timing.util;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link TimingLogger}.
 *
 * <p>Uses Mockito to inject a mock {@link Logger}, allowing assertions on exactly which log method ({@code debug},
 * {@code warn}, or {@code error}) was called and with what arguments — without coupling to any
 * specific logging implementation.</p>
 *
 * <p><b>Slow threshold tests</b> — use {@code Thread.sleep(100ms)} inside the timed block against a {@code 50ms}
 * threshold to reliably trigger the slow path across environments.</p>
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimingLoggerTest {

    private static final String LABEL             = "testMethod";
    private static final long   THRESHOLD_MILLIS  = 50L;
    private static final int    SLOW_SLEEP_MILLIS = 100;

    private final Logger mockLogger = mock(Logger.class);

    // Intentional — Thread.sleep() here generates real elapsed time to measure, not a substitute for thread
    // synchronization (which this rule is meant to catch). There's nothing to synchronize with.
    @SuppressWarnings("java:S2925")
    private static void simulateSlowWork() {
        try {
            Thread.sleep(SLOW_SLEEP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread()
                  .interrupt();
        }
    }

    @Nested
    class StartValidationTest {

        // The below tests verify that TimingLogger.start() throws IllegalArgumentException before a TimingLogger
        // instance is ever created. The exception is thrown inside the start() factory method during argument
        // validation. Since the exception is thrown before new TimingLogger(...) is reached, no TimingLogger
        // instance exists. There is nothing to close. close() is never involved. So there is no need to wrap
        // TimingLogger.start(...) in a try-with-resources for these tests.

        @Test
        void throws_when_label_is_null() {
            assertThrows(IllegalArgumentException.class, () -> TimingLogger.start(null, mockLogger));
        }

        @Test
        void throws_when_label_is_empty() {
            assertThrows(IllegalArgumentException.class, () -> TimingLogger.start("", mockLogger));
        }

        @Test
        void throws_when_label_is_whitespace_only() {
            assertThrows(IllegalArgumentException.class, () -> TimingLogger.start("   ", mockLogger));
        }

        @Test
        void throws_when_logger_is_null() {
            assertThrows(IllegalArgumentException.class, () -> TimingLogger.start(LABEL, null));
        }

        @Test
        void throws_when_threshold_is_negative() {
            assertThrows(IllegalArgumentException.class, () -> TimingLogger.start(LABEL, mockLogger, -1));
        }

        @Test
        void accepts_zero_threshold() {
            assertDoesNotThrow(() -> TimingLogger.start(LABEL, mockLogger, 0)
                                                 .close());
        }

        @Test
        void exception_message_contains_invalid_threshold_value() {
            final var ex = assertThrows(IllegalArgumentException.class,
                                        () -> TimingLogger.start(LABEL, mockLogger, -100));
            assertTrue(ex.getMessage()
                         .contains("-100"));
        }

        @Test
        void start_without_threshold_returns_non_null() {
            assertNotNull(TimingLogger.start(LABEL, mockLogger));
        }

        @Test
        void start_with_threshold_returns_non_null() {
            assertNotNull(TimingLogger.start(LABEL, mockLogger, THRESHOLD_MILLIS));
        }
    }

    @Nested
    class CloseLogLevelTest {

        @Test
        void logs_at_debug_level_with_no_threshold() {
            try (var ignored = TimingLogger.start(LABEL, mockLogger)) {
                // method body — intentionally empty
            }
            verify(mockLogger).debug(anyString(), eq(LABEL), anyString());
            verify(mockLogger, never()).warn(anyString(), any(), any());
        }

        @Test
        void logs_at_debug_level_when_below_threshold() {
            // Threshold is 50ms — method completes almost instantly, well below threshold
            try (var ignored = TimingLogger.start(LABEL, mockLogger, THRESHOLD_MILLIS)) {
                // no sleep — completes in microseconds
            }
            verify(mockLogger).debug(anyString(), eq(LABEL), anyString());
            verify(mockLogger, never()).warn(anyString(), any(), any());
        }

        @Test
        void logs_at_debug_level_when_threshold_is_zero() {
            try (var ignored = TimingLogger.start(LABEL, mockLogger, 0)) {
                // no sleep
            }
            verify(mockLogger).debug(anyString(), eq(LABEL), anyString());
            verify(mockLogger, never()).warn(anyString(), any(), any());
        }

        @Test
        void logs_at_warn_level_when_threshold_breached() {
            // Threshold is 50ms — sleep 100ms to reliably exceed it
            try (var ignored = TimingLogger.start(LABEL, mockLogger, THRESHOLD_MILLIS)) {
                simulateSlowWork();
            }
            verify(mockLogger).warn(anyString(), eq(LABEL), anyString());
            verify(mockLogger, never()).debug(anyString(), any(), any());
        }
    }

    @Nested
    class CloseLogContentTest {

        @Test
        void log_message_contains_timed_prefix() {
            try (var ignored = TimingLogger.start(LABEL, mockLogger)) {
                // empty body
            }
            verify(mockLogger).debug(contains("TIMED"), eq(LABEL), anyString());
        }

        @Test
        void log_message_contains_label() {
            try (var ignored = TimingLogger.start(LABEL, mockLogger)) {
                // empty body
            }
            verify(mockLogger).debug(anyString(), eq(LABEL), anyString());
        }

        @Test
        void warn_message_contains_slow_marker() {
            try (var ignored = TimingLogger.start(LABEL, mockLogger, THRESHOLD_MILLIS)) {
                simulateSlowWork();
            }
            verify(mockLogger).warn(contains("SLOW"), eq(LABEL), anyString());
        }
    }

    @Nested
    class CloseExceptionSafetyTest {

        @Test
        void original_exception_propagates_from_try_block() {
            final var ex = assertThrows(SQLException.class, () -> {
                try (var ignored = TimingLogger.start(LABEL, mockLogger)) {
                    throw new SQLException("The original exception");
                }
            });
            assertInstanceOf(SQLException.class, ex);
            assertEquals("The original exception", ex.getMessage());
        }

        @Test
        void close_does_not_throw_when_debug_logging_fails() {
            // Make the logger throw to simulate an unexpected internal failure on the normal (DEBUG) path
            doThrow(new RuntimeException("A logger failure")).when(mockLogger)
                                                             .debug(anyString(), any(), any());

            assertDoesNotThrow(() -> {
                try (var ignored = TimingLogger.start(LABEL, mockLogger)) {
                    // empty body
                }
            });
        }

        @Test
        void close_does_not_throw_when_warn_logging_fails() {
            // Same safety net, but on the slow-threshold (WARN) path
            doThrow(new RuntimeException("A logger failure")).when(mockLogger)
                                                             .warn(anyString(), any(), any());

            assertDoesNotThrow(() -> {
                try (var ignored = TimingLogger.start(LABEL, mockLogger, THRESHOLD_MILLIS)) {
                    simulateSlowWork();
                }
            });
        }

        @Test
        void close_logs_error_with_original_exception_on_debug_failure() {
            final var loggingFailure = new RuntimeException("A logger failure");
            doThrow(loggingFailure).when(mockLogger)
                                   .debug(anyString(), any(), any());

            try (var ignored = TimingLogger.start(LABEL, mockLogger)) {
                // empty body
            }

            // After debug() throws, close() must log the SAME exception instance to error() as a safety net —
            // not a new, unrelated one
            verify(mockLogger).error(anyString(), eq(LABEL), eq(loggingFailure));
        }

        @Test
        void close_logs_elapsed_time_even_when_try_block_throws() {
            var timer = TimingLogger.start(LABEL, mockLogger);
            assertThrows(RuntimeException.class, () -> {
                try (timer) {
                    throw new RuntimeException("A block failure");
                }
            });
            // close() must have fired and logged despite the exception
            verify(mockLogger).debug(anyString(), eq(LABEL), anyString());
        }
    }

    @Nested
    class AutoCloseableBehaviourTest {

        @Test
        void close_called_exactly_once() {
            try (var ignored = TimingLogger.start(LABEL, mockLogger)) {
                // empty body
            }
            // debug() is called inside close() — if called once, close() ran once
            verify(mockLogger, times(1)).debug(anyString(), any(), any());
        }

        @Test
        void implements_auto_closeable() {
            final var timer = TimingLogger.start(LABEL, mockLogger);
            assertInstanceOf(AutoCloseable.class, timer);
            timer.close();
        }

        @Test
        void successfully_times_value_returning_method_body() {
            final String result;
            try (var ignored = TimingLogger.start(LABEL, mockLogger)) {
                result = "Connection";
            }
            assertEquals("Connection", result);
            verify(mockLogger).debug(anyString(), eq(LABEL), anyString());
        }

        @Test
        void successfully_times_void_method_body() {
            try (var ignored = TimingLogger.start(LABEL, mockLogger)) {
                // simulates a void method — no return value
            }
            verify(mockLogger).debug(anyString(), eq(LABEL), anyString());
        }
    }
}
