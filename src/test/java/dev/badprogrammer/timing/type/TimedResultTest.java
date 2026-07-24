package dev.badprogrammer.timing.type;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TimedResult}.
 *
 * <p>Constructs instances directly via {@link TimedResult#of} rather than through {@link
 * dev.badprogrammer.timing.util.StopWatch}, so these tests verify the class's own behavior independently of how
 * {@code StopWatch} happens to use it.</p>
 *
 * <p><b>Why this class has tests despite looking like a plain value type:</b> {@code TimedResult} isn't purely a
 * data holder — {@link TimedResult#getElapsedMillis()} performs a unit conversion with a non-obvious behavior
 * (truncation, not rounding) that a consumer could easily assume works differently. That's real logic worth
 * protecting, not just a field return.</p>
 *
 * <p>Only covers behavior with real logic — unit conversion, truncation, and boundary values. Plain stored-value
 * getters ({@code getResult()}, {@code getElapsedNanos()}) are not separately tested here since they involve no
 * logic beyond returning a constructor argument.</p>
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimedResultTest {

    @Nested
    class MillisConversionTest {

        @Test
        void getElapsedMillis_converts_nanos_to_millis() {
            final var result = TimedResult.of("Value", 5_000_000L);
            assertEquals(5L, result.getElapsedMillis());
        }

        @Test
        void getElapsedMillis_truncates_partial_millis() {
            // 1.5ms worth of nanos — conversion truncates, does not round
            final var result = TimedResult.of("Value", 1_500_000L);
            assertEquals(1L, result.getElapsedMillis());
        }

        @Test
        void getElapsedMillis_returns_zero_for_sub_millisecond_elapsed_time() {
            final var result = TimedResult.of("Value", 999_999L);
            assertEquals(0L, result.getElapsedMillis());
        }
    }

    @Nested
    class ToStringTest {

        @Test
        void toString_contains_elapsed_millis_and_nanos() {
            final var result = TimedResult.of("Value", 5_000_000L);
            final var text   = result.toString();
            assertTrue(text.contains("5ms"));
            assertTrue(text.contains("5000000ns"));
        }
    }
}
