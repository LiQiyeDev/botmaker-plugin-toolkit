package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.value.ValueCodec;

import java.util.function.Function;

/**
 * {@link ValueCodec}s built from lambdas instead of anonymous classes.
 *
 * <p>A codec is three one-line answers — what stored text means, how to store a value back, how to write it
 * as Java — and spelling that as an anonymous class costs eleven lines of ceremony per type. A plugin
 * registering a dozen types wrote the same eleven lines a dozen times, which is what this replaces:
 *
 * <pre>{@code
 * catalog.add(CHANNEL, Codecs.of(Channel::parse, Channel::name, c -> "Channel." + c.name()));
 * }</pre>
 *
 * <h2>Totality is still the caller's, and this class cannot give it</h2>
 *
 * <p>The contract's rule is that <b>nothing in a codec may throw and nothing may return {@code null}</b>: a
 * project whose file says something impossible must still open, or it is a project nobody can repair through
 * the editor. That rule lives in the {@code parse} function passed here, and nothing in this file can enforce
 * it — a lambda that throws throws. What this class does do is keep the ceremony from hiding it: three
 * lambdas on one line is short enough that "does that parse degrade?" is a question a reader can actually
 * ask. Wrap a partial parser with {@link #or} rather than writing a try/catch per type.
 *
 * <p>There is deliberately no JavaFX here, so a headless build of a plugin — a validator, a test, a bot —
 * may use it without resolving the toolkit's other half.
 */
public final class Codecs {

    private Codecs() {}

    /** A codec from its three answers. Totality is the caller's; see this class's note. */
    public static <T> ValueCodec<T> of(Function<String, T> parse, Function<T, String> store,
                                       Function<T, String> literal) {
        return new ValueCodec<>() {
            @Override
            public T parse(String wire) {
                return parse.apply(wire);
            }

            @Override
            public String store(T value) {
                return store.apply(value);
            }

            @Override
            public String literal(T value) {
                return literal.apply(value);
            }
        };
    }

    /**
     * An enum constant, stored and written as its own name.
     *
     * <p>{@code qualifier} is what precedes the constant in generated source — the enum's simple name, since
     * the import is arranged from the type's {@code importName()}. Passed rather than taken from the class,
     * because a plugin whose enum is nested or aliased knows the spelling it wants and the reflection answer
     * would be wrong for both.
     */
    public static <E extends Enum<E>> ValueCodec<E> ofEnum(Function<String, E> parse, String qualifier) {
        return of(parse, Enum::name, e -> qualifier + "." + e.name());
    }

    /**
     * {@code codec} made total by answering {@code fallback} for any text it throws on or reads as
     * {@code null}.
     *
     * <p>For wrapping a parser that already exists and is partial — {@code Integer::parseInt},
     * {@code LocalDate::parse}, an enum's own {@code valueOf}. Writing the try/catch once here rather than
     * once per type is the point: the rule it keeps is a contract rule, and a rule kept by hand in a dozen
     * places is a rule kept in eleven.
     */
    public static <T> ValueCodec<T> or(ValueCodec<T> codec, T fallback) {
        return new ValueCodec<>() {
            @Override
            public T parse(String wire) {
                try {
                    T parsed = codec.parse(wire);
                    return parsed == null ? fallback : parsed;
                } catch (RuntimeException e) {
                    return fallback;
                }
            }

            @Override
            public String store(T value) {
                return codec.store(value);
            }

            @Override
            public String literal(T value) {
                return codec.literal(value);
            }

            @Override
            public String defaultWire() {
                return codec.defaultWire();
            }
        };
    }

    /**
     * {@code codec} with a different seed for a freshly created value; everything else unchanged.
     *
     * <p>Override the seed only when it is a <em>choice</em> rather than a fallback — the contract's default,
     * {@code store(parse(""))}, is already right for a number that starts at zero or an enum that starts at
     * its first constant. It is wrong when an empty value is one the plugin cannot run on and something else
     * is the sensible starting point, which no amount of parsing {@code ""} discovers.
     *
     * <p>The contract's fixed point still has to hold: {@code store(parse(defaultWire()))} must equal
     * {@code defaultWire()}, or a freshly created value changes the moment it is read back.
     */
    public static <T> ValueCodec<T> seeded(ValueCodec<T> codec, String defaultWire) {
        return new ValueCodec<>() {
            @Override
            public T parse(String wire) {
                return codec.parse(wire);
            }

            @Override
            public String store(T value) {
                return codec.store(value);
            }

            @Override
            public String literal(T value) {
                return codec.literal(value);
            }

            @Override
            public String defaultWire() {
                return defaultWire;
            }
        };
    }
}
