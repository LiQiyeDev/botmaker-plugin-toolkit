package com.botmaker.plugin.toolkit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link Source}, whose output is compiled by somebody else's build.
 *
 * <p>Every case here is a character a user can put in a value without seeing it — a pasted tab, a newline
 * out of a text area, a Windows path full of backslashes — and in each one a wrong answer is a compile
 * error in a <em>bot</em>, reported against a line its author did not write. That is why this is worth
 * asserting where "the builder returned non-null" is not.
 */
class SourceTest {

    @Test
    void a_string_literal_escapes_everything_that_cannot_appear_literally() {
        Map<String, String> expected = Map.of(
                "plain", "\"plain\"",
                "with \"quotes\"", "\"with \\\"quotes\\\"\"",
                "C:\\Games\\bot.exe", "\"C:\\\\Games\\\\bot.exe\"",
                "line\nbreak", "\"line\\nbreak\"",
                "tab\there", "\"tab\\there\"",
                "cr\rhere", "\"cr\\rhere\"",
                "", "\"\"");
        expected.forEach((text, literal) -> assertEquals(literal, Source.string(text)));
    }

    /**
     * The one behaviour JavaPoet gets wrong for a slot, pinned so nobody "simplifies" this onto {@code $S}.
     *
     * <p>{@code CodeBlock.of("$S", "a\nb")} emits {@code "a\n" + "b"} across two source lines, which is
     * right for a generated file and wrong for a slot: the host writes the result into the middle of an
     * existing line, and one slot holds one expression.
     */
    @Test
    void a_multi_line_string_stays_one_expression() {
        String literal = Source.string("first\nsecond\nthird");
        assertEquals("\"first\\nsecond\\nthird\"", literal);
        assertFalse(literal.contains("\n"), "a slot expression may not span source lines");
        assertFalse(literal.contains("+"), "a slot expression is not a concatenation");
    }

    @Test
    void a_control_character_becomes_a_unicode_escape() {
        assertEquals("\"bell\\u0007\"", Source.string("bell\u0007"));
    }

    @Test
    void a_null_string_is_the_empty_literal_rather_than_null() {
        assertEquals("\"\"", Source.string(null));
    }

    @Test
    void a_char_literal_escapes_its_own_quote_and_not_the_other_one() {
        assertEquals("'a'", Source.character('a'));
        assertEquals("'\\''", Source.character('\''));
        assertEquals("'\"'", Source.character('"'));
        assertEquals("'\\n'", Source.character('\n'));
        assertEquals("'\\\\'", Source.character('\\'));
    }

    @Test
    void a_whole_number_reads_as_a_count_and_a_fraction_as_a_decimal() {
        assertEquals("3", Source.number(3.0));
        assertEquals("0.75", Source.number(0.75));
        assertEquals("-2", Source.number(-2.0));
        assertEquals("500", Source.number(500L));
    }

    @Test
    void a_constructor_names_its_type_in_full_and_keeps_argument_order() {
        assertEquals("new java.awt.Point(12, 34)", Source.newInstance(java.awt.Point.class, 12, 34));
        assertEquals("new java.lang.String()", Source.newInstance(String.class));
    }

    @Test
    void an_argument_is_source_so_text_has_to_be_quoted_by_the_caller() {
        assertEquals("java.lang.String.valueOf(\"ding\")",
                Source.call(String.class, "valueOf", Source.string("ding")));
        // The other reading, spelled out: an unwrapped argument is an expression, which is a variable here.
        assertEquals("java.lang.String.valueOf(name)", Source.call(String.class, "valueOf", "name"));
    }

    @Test
    void a_nested_type_is_spelled_the_way_source_spells_it() {
        assertEquals("java.util.Map.Entry", Source.type(Map.Entry.class));
    }

    @Test
    void an_enum_constant_is_qualified_by_its_own_declaring_type() {
        assertEquals("java.time.DayOfWeek.MONDAY", Source.enumConstant(java.time.DayOfWeek.MONDAY));
        assertEquals("null", Source.enumConstant(null));
    }

    @Test
    void slots_quote_is_the_same_answer_because_it_is_the_same_code() {
        assertEquals(Source.string("a\tb"), Slots.quote("a\tb"));
    }
}
