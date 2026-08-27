package com.botmaker.plugin.toolkit;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The module's only behaviour that can be asserted without a JavaFX toolkit — and the only behaviour worth
 * asserting anyway. Everything else here builds a {@code Node}, and a test that a builder returned non-null
 * proves nothing a compile did not.
 *
 * <p>What these hold is the one promise the whole toolkit rests on: <b>a value that cannot be read still
 * opens an editor</b>. Every case below is a real state a project file reaches — a half-written tuple, a
 * value from a newer plugin, a null in a list — and in every one the answer is a default rather than an
 * exception, because an editor that throws while building leaves a row of the Parameters window empty.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ValuesTest {

    @Test
    void a_missing_item_reads_as_the_empty_string_never_null() {
        assertEquals("", Values.at(null, 0));
        assertEquals("", Values.at(List.of(), 0));
        assertEquals("", Values.at(List.of("a"), 4));
        assertEquals("", Values.at(List.of("a"), -1));
        assertEquals("", Values.at(Arrays.asList("a", null), 1));
    }

    @Test
    void a_number_that_is_not_a_number_falls_back_rather_than_throwing() {
        assertEquals(7, Values.intAt(List.of("nonsense"), 0, 7));
        assertEquals(7, Values.intAt(List.of(), 0, 7));
        assertEquals(7, Values.intAt(List.of("2.5"), 0, 7));
        assertEquals(12, Values.intAt(List.of("  12  "), 0, 7));
        assertEquals(0.5, Values.doubleAt(List.of("0.5"), 0, 9));
        assertEquals(9, Values.doubleAt(List.of("nonsense"), 0, 9));
    }

    @Test
    void a_half_written_tuple_still_yields_a_full_one() {
        assertArrayEquals(new int[]{1, 2, 0, 0}, Values.ints(List.of("1", "2"), 4));
        assertArrayEquals(new int[]{0, 0}, Values.ints(null, 2));
        assertArrayEquals(new int[]{1, 0, 3}, Values.ints(List.of("1", "x", "3"), 3));
        assertArrayEquals(new int[]{}, Values.ints(List.of("1"), 0));
        assertArrayEquals(new int[]{}, Values.ints(List.of("1"), -3));
    }

    @Test
    void numbers_round_trip_through_the_wire_form() {
        assertEquals(List.of("1", "2", "3", "4"), Values.of(1, 2, 3, 4));
        assertEquals(List.of(), Values.of());
        assertArrayEquals(new int[]{-5, 0}, Values.ints(Values.of(-5, 0), 2));
    }

    @Test
    void blank_is_about_every_item_not_only_the_first() {
        assertTrue(Values.isBlank(null));
        assertTrue(Values.isBlank(List.of()));
        assertTrue(Values.isBlank(List.of("", "   ")));
        assertTrue(Values.isBlank(Arrays.asList(null, null)));
        assertFalse(Values.isBlank(List.of("", "x")));
    }

    @Test
    void a_label_falls_back_only_when_it_says_nothing() {
        assertEquals("Choose…", Values.labelOr(null, "Choose…"));
        assertEquals("Choose…", Values.labelOr("   ", "Choose…"));
        assertEquals("0", Values.labelOr("0", "Choose…"));
    }

    @Test
    void a_thumbnail_with_no_label_shows_its_value() {
        assertEquals("mine.png", new Thumbnail("mine.png", null, null).label());
        assertEquals("mine.png", new Thumbnail("mine.png", "  ", null).label());
        assertEquals("", Thumbnail.of(null, null).value());
    }
}
