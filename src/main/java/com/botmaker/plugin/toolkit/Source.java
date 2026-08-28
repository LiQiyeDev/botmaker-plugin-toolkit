package com.botmaker.plugin.toolkit;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;

/**
 * Java source, spelled correctly — the one place a plugin writes an expression a bot will compile.
 *
 * <h2>Why this is the toolkit's problem and not each plugin's</h2>
 *
 * <p>A plugin that registers a value type is emitting Java whether it thinks so or not:
 * {@link com.botmaker.plugin.api.value.ValueCodec#literal} returns Java source, and
 * {@link Slots#write} writes Java into a slot. Before this class there were three hand-rolled copies of
 * string escaping in this project alone — one here, one in the SDK, one in the generated plugin skeleton —
 * which is what a concern with no owner looks like. A wrong escape is not a compile error in <em>this</em>
 * build; it is a compile error in somebody's <em>bot</em>, reported against a line they did not write.
 *
 * <h2>An argument is source, not a value</h2>
 *
 * <p>{@link #newInstance} and {@link #call} take arguments that are already Java: a number, or the text of
 * an expression. To pass a piece of <em>text</em>, wrap it — {@code call(Sound.class, "play",
 * string("ding"))} — because {@code "ding"} and {@code ding} are both valid Java and only the caller knows
 * which was meant. Getting that backwards is the mistake this rule exists to make visible at the call site.
 *
 * <h2>JavaPoet is the implementation and never the interface</h2>
 *
 * <p>Every member here takes and returns {@link String}, so no JavaPoet type reaches a plugin's signature
 * and the library can be replaced without a toolkit release that breaks anybody. A plugin that wants
 * JavaPoet directly is free to declare it.
 */
public final class Source {

    private Source() {}

    /**
     * {@code text} as a Java string literal, quotes included, always as a <b>single</b> expression.
     *
     * <p><b>This is the one member JavaPoet does not implement, and the reason is worth knowing before
     * anybody "fixes" it.</b> JavaPoet's {@code $S} splits a string containing a newline into a
     * concatenation across source lines ({@code "line\n" + "break"}), which is right for a generated file
     * and wrong here: a slot holds one expression, and the host writes it into the middle of somebody
     * else's line. So the escaping is done here, and it is total — every character that cannot appear
     * literally inside a Java string is escaped, including the control characters a user can paste in
     * without ever seeing them.
     */
    public static String string(String text) {
        String s = text == null ? "" : text;
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            out.append(escape(s.charAt(i), '"'));
        }
        return out.append('"').toString();
    }

    /**
     * A Java char literal, quotes included.
     *
     * <p>Separate from {@link #string} rather than a parameter of it because what is legal differs by
     * position: a {@code '} must be escaped here and must not be there, and the reverse holds for {@code "}.
     */
    public static String character(char c) {
        return "'" + escape(c, '\'') + "'";
    }

    /**
     * A number as a person would have typed it: {@code 3} for a whole value, {@code 0.75} otherwise.
     *
     * <p>Never {@code 3.0} for a count. The value goes into a bot's source where somebody reads it, and a
     * trailing {@code .0} on a pixel count reads as a unit that was never meant.
     */
    public static String number(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? Long.toString(Math.round(value))
                : Double.toString(value);
    }

    /** A whole number. */
    public static String number(long value) {
        return Long.toString(value);
    }

    /**
     * An enum constant, fully qualified — {@code com.example.Direction.LEFT}.
     *
     * <p>Fully qualified is the safe half of the pair the contract documents: pass the type's name as the
     * import alongside it and the host shortens what it can, while an expression the host chose not to
     * shorten is still correct on its own.
     */
    public static String enumConstant(Enum<?> constant) {
        if (constant == null) return "null";
        return type(constant.getDeclaringClass()) + "." + constant.name();
    }

    /**
     * {@code new Type(argument, …)}, with the type fully qualified.
     *
     * <p>Each argument is <b>Java source</b>, not a value — see the class javadoc. A {@code null} argument
     * emits the literal {@code null}, which is a real thing to write and not an accident.
     */
    public static String newInstance(Class<?> type, Object... arguments) {
        return CodeBlock.of("new $T($L)", className(type), joined(arguments)).toString();
    }

    /**
     * {@code Type.method(argument, …)} — a static call, the shape a call-site editor writes.
     *
     * <p>Each argument is <b>Java source</b>, exactly as in {@link #newInstance}.
     */
    public static String call(Class<?> type, String method, Object... arguments) {
        return CodeBlock.of("$T.$L($L)", className(type), method, joined(arguments)).toString();
    }

    /** A type's name as it may be written in an expression, fully qualified. */
    public static String type(Class<?> type) {
        return type == null ? "" : className(type).toString();
    }

    // ---- internals ------------------------------------------------------------------------------------

    private static CodeBlock joined(Object... arguments) {
        if (arguments == null || arguments.length == 0) return CodeBlock.of("");
        CodeBlock.Builder out = CodeBlock.builder();
        for (int i = 0; i < arguments.length; i++) {
            Object argument = arguments[i];
            out.add(i == 0 ? "" : ", ");
            // A double is the one shape whose toString() is not what a reader wants (3.0 for a count), so it
            // goes through number(); everything else is already the source the caller chose.
            if (argument instanceof Double d) {
                out.add("$L", number(d));
            } else if (argument == null) {
                out.add("null");
            } else {
                out.add("$L", argument.toString());
            }
        }
        return out.build();
    }

    /**
     * JavaPoet's name for a class, including a nested one.
     *
     * <p>{@code ClassName.get(Class)} spells a nested type {@code Outer.Inner} rather than
     * {@code Outer$Inner}, which is the difference between source and a class file name — and the reason
     * this goes through JavaPoet rather than {@code getName()}.
     */
    private static ClassName className(Class<?> type) {
        return ClassName.get(type);
    }

    /** One character as it may appear inside a literal delimited by {@code quote}. */
    private static String escape(char c, char quote) {
        if (c == quote) return "\\" + quote;
        return switch (c) {
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            default -> c < 0x20 || c == 0x7f ? String.format("\\u%04x", (int) c) : String.valueOf(c);
        };
    }
}
