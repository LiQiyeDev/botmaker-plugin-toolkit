package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.Region;
import com.botmaker.plugin.api.ValueContext;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Whole editors: hand one a {@link ValueContext} and it is done.
 *
 * <p>This is the layer the toolkit exists for. Everything under it — {@link Pills}, {@link Fields},
 * {@link Modals} — is JavaFX with the host's conventions baked in; these read the value, write it back
 * and reach {@link com.botmaker.plugin.api.StudioServices} for capture and dialogs themselves, so a
 * plugin's editor for a rectangle is a predicate and a method reference:
 *
 * <pre>{@code
 * SlotEditor.of(c -> c.type().is(Rect.class), Editors::region)
 * }</pre>
 *
 * <p>They were extracted from the host's own thirteen pickers rather than designed, which is why there are
 * eight and not sixteen: these are the shapes that recurred. An editor that needs something else builds it
 * from the layer below, and the day a second one wants the same thing it is worth adding here — which is how
 * {@link #boundedPill} and {@link #flag} arrived, out of the SDK, on 2026-08-28.
 *
 * <p><b>Two of them read through {@link Slots} and the other six through {@link ValueContext} directly</b>,
 * and the difference is which places the editor can serve. {@code Slots} spells a value as Java when the
 * context is a slot in a bot's source and as stored text when it is a Parameters row; the plain ones write
 * stored text either way. Reach for {@code Slots} whenever the editor might sit in source.
 *
 * <p><b>Every one of them writes only when the user acts.</b> Building an editor never sets a value — not
 * even to normalise what is already there — because a project that is merely opened and closed must come
 * back byte-identical.
 */
public final class Editors {

    private Editors() {}

    /**
     * A pill over {@code labels.length} whole numbers, edited in a dialog.
     *
     * <p>The shape of {@code Point}, {@code Size} and any other small tuple. The pill shows the numbers
     * comma-separated; the dialog gives each one its own labelled field.
     */
    public static Node numbers(ValueContext ctx, String title, String... labels) {
        int count = labels == null ? 0 : labels.length;
        MenuButton pill = Pills.bare(numbersLabel(ctx, count, title));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Edit values…", () ->
                        Modals.numbers(ctx, title, labels, Values.ints(ctx.value(), count), picked -> {
                            ctx.set(Values.of(picked));
                            pill.setText(numbersLabel(ctx, count, title));
                        }))));
        return pill;
    }

    /**
     * A pill over a screen rectangle: <i>Select on screen…</i>, or the four numbers by hand.
     *
     * <p>The one editor that is worth having even if a plugin ships nothing else, because dragging out a
     * region is a thing a plugin genuinely cannot do for itself — the host owns the overlay.
     */
    public static Node region(ValueContext ctx) {
        MenuButton pill = Pills.bare(regionLabel(ctx));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Select on screen…", () -> ctx.services().capture().selectRegion(r -> {
                    ctx.set(Values.of(r.x(), r.y(), r.width(), r.height()));
                    pill.setText(regionLabel(ctx));
                })),
                Pills.separator(),
                Pills.item("Edit values…", () -> Modals.numbers(ctx, "Region",
                        new String[]{"x", "y", "width", "height"}, Values.ints(ctx.value(), 4), picked -> {
                            ctx.set(Values.of(picked));
                            pill.setText(regionLabel(ctx));
                        }))));
        return pill;
    }

    /**
     * A slider and read-out for a bounded fractional number.
     *
     * <p>Writes continuously as the slider moves — see {@link Fields#bounded}, and the contract's note that
     * calling {@code set} repeatedly is expected.
     */
    public static Node bounded(ValueContext ctx, double min, double max, double step) {
        double current = Values.doubleAt(ctx.value(), 0, min);
        return Fields.bounded(current, min, max, step, v -> ctx.set(trim(v)));
    }

    /** A text field that commits on Enter and on losing focus. */
    public static Node text(ValueContext ctx, String prompt) {
        return Fields.committing(ctx.single(), prompt, ctx::set);
    }

    /**
     * A dropdown over a fixed set.
     *
     * <p>A value the list does not contain is <b>kept and shown</b> rather than corrected: it is what the
     * project file holds, and a plugin whose option set shrank between releases must not silently rewrite
     * every bot that used the option it dropped.
     */
    public static Node choice(ValueContext ctx, List<String> options) {
        List<String> items = new ArrayList<>(options == null ? List.of() : options);
        String current = ctx.single();
        if (!current.isBlank() && !items.contains(current)) items.add(current);

        ComboBox<String> box = Styles.on(new ComboBox<>(), Styles.INSET_FIELD_FLAT);
        box.getItems().setAll(items);
        if (!current.isBlank()) box.setValue(current);
        box.valueProperty().addListener((obs, was, now) -> {
            if (now != null && !now.equals(was)) ctx.set(now);
        });
        return box;
    }

    /**
     * A pill opening a grid of pictures.
     *
     * <p>{@code items} is a {@link Supplier} and is called when the pill is opened, never when it is built:
     * what there is to choose from moves — a template captured a moment ago, an emulator that just started —
     * and a list read at render time is the list as it was when the block first appeared.
     */
    public static Node gallery(ValueContext ctx, String title, Supplier<List<Thumbnail>> items,
                               String emptyMessage) {
        MenuButton pill = Pills.bare(Values.labelOr(ctx.single(), "Choose…"));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Choose…", () -> Modals.chooser(ctx, title,
                        items == null ? List.of() : items.get(), emptyMessage, picked -> {
                            ctx.set(picked.value());
                            pill.setText(Values.labelOr(picked.label(), "Choose…"));
                        })),
                Pills.separator(),
                Pills.item("Clear", () -> {
                    ctx.set(List.of());
                    pill.setText("Choose…");
                })));
        return pill;
    }

    /**
     * A number that has a range, and the words that make the range mean something.
     *
     * <p>Every field here exists because a bare number does not say it. {@code 0.8} means something only once
     * you can see where it sits between 0 and 1; {@code 500} means something only next to the fact that it is
     * milliseconds. That is the whole argument for {@link #boundedPill}: a free-typed literal states neither,
     * and accepts {@code 80} for a fraction — a value the library will clamp and the author will never hear
     * about.
     *
     * @param label    the value's name as a person would say it, not as the method spells it
     * @param prompt   the sentence above the control, which is where the unit and the limit are stated
     * @param unit     appended to the pill's own text, so a delay reads {@code 500 ms} on the block itself
     * @param whole    a count (spinner, typed) rather than a fraction (slider, found)
     * @param fallback what an unreadable or absent value opens on — never 0 unless 0 is genuinely the default
     */
    public record NumberRange(String label, String prompt, String unit, boolean whole,
                              double min, double max, double step, double fallback) {}

    /**
     * A pill over a bounded number, edited in a dialog and committed on <i>OK</i>.
     *
     * <p>Whole counts get a spinner and fractions get a slider, which is {@link Fields}' own division and the
     * reason it draws them differently: 500 milliseconds is a quantity a person types, while 0.8 confidence is
     * a position a person finds. Unlike {@link #bounded}, the value is <b>not</b> written while dragging —
     * this shape is for a slot in a bot's source, and a slider that rewrote the file on every pixel of the
     * drag would fill the undo stack with values nobody chose.
     *
     * <p>Reads and writes through {@link Slots}, so it serves a slot and a Parameters row alike; whether the
     * editor is <em>offered</em> in both is the predicate's business, not this one's.
     */
    public static Node boundedPill(ValueContext ctx, NumberRange range) {
        MenuButton pill = Pills.bare(rangeLabel(ctx, range));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Set " + range.label().toLowerCase() + "…", () -> {
                    double current = rangeCurrent(ctx, range);
                    if (range.whole()) {
                        Spinner<Integer> spinner = Fields.integer((int) Math.round(current),
                                (int) range.min(), (int) range.max());
                        Modals.form(ctx, range.label(), rangeBody(range, spinner), () -> {
                            commitRange(ctx, range, spinner.getValue());
                            pill.setText(rangeLabel(ctx, range));
                        });
                    } else {
                        double[] picked = {current};
                        HBox slider = Fields.bounded(current, range.min(), range.max(), range.step(),
                                value -> picked[0] = value);
                        Modals.form(ctx, range.label(), rangeBody(range, slider), () -> {
                            commitRange(ctx, range, picked[0]);
                            pill.setText(rangeLabel(ctx, range));
                        });
                    }
                })));
        return pill;
    }

    /**
     * A yes/no value, written the moment it is ticked.
     *
     * <p>No modal and no OK: there is nothing to get wrong about a checkbox, and a window asking a person to
     * confirm the tick they just made is a window. It carries {@code label} because {@code enableDebug(true)}
     * beside a bare box reads as though the box is the argument to something else — which, without the label,
     * is exactly what it looks like.
     */
    public static Node flag(ValueContext ctx, String label) {
        CheckBox box = new CheckBox(label);
        box.setSelected(Boolean.parseBoolean(Slots.raw(ctx)));
        box.setOnAction(e -> Slots.write(ctx, Boolean.toString(box.isSelected()),
                Boolean.toString(box.isSelected())));
        return box;
    }

    private static VBox rangeBody(NumberRange range, Node control) {
        return new VBox(6, Styles.on(new Label(range.prompt()), Styles.CAPTION), control);
    }

    /** Writes the number the way a person would have typed it: a count as a count, a fraction as a decimal. */
    private static void commitRange(ValueContext ctx, NumberRange range, double value) {
        double clamped = Math.clamp(value, range.min(), range.max());
        String literal = range.whole()
                ? Long.toString(Math.round(clamped))
                : BigDecimal.valueOf(clamped).setScale(3, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString();
        Slots.write(ctx, literal, literal);
    }

    /**
     * The number in the value, or the range's own default.
     *
     * <p>Falling back to the default rather than to zero matters: opening the editor on a slot holding a
     * variable and pressing OK would otherwise write {@code 0}, which for a confidence means "match anything".
     */
    private static double rangeCurrent(ValueContext ctx, NumberRange range) {
        try {
            String raw = Slots.raw(ctx).replace("_", "").replaceAll("[lLdDfF]$", "");
            return raw.isBlank() ? range.fallback() : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return range.fallback();
        }
    }

    /** What the pill says: the value as written, plus the unit — or the source text when it is not a number. */
    private static String rangeLabel(ValueContext ctx, NumberRange range) {
        String raw = Slots.raw(ctx);
        if (raw.isBlank()) return range.label() + "…";
        try {
            Double.parseDouble(raw.replace("_", "").replaceAll("[lLdDfF]$", ""));
            return raw + range.unit();
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /** {@code 12, 34} — or the placeholder, when nothing has been chosen. */
    private static String numbersLabel(ValueContext ctx, int count, String title) {
        if (Values.isBlank(ctx.value())) return "Set " + title.toLowerCase() + "…";
        int[] v = Values.ints(ctx.value(), count);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < v.length; i++) out.append(i == 0 ? "" : ", ").append(v[i]);
        return out.toString();
    }

    /** {@code 12, 34  800×600} — the reading order of a {@link Region}, position then size. */
    private static String regionLabel(ValueContext ctx) {
        if (Values.isBlank(ctx.value())) return "Choose region…";
        int[] v = Values.ints(ctx.value(), 4);
        return v[0] + ", " + v[1] + "  " + v[2] + "×" + v[3];
    }

    /** A whole number reads as one — {@code "3"}, not {@code "3.0"} — because that is what a bot's source wants. */
    private static String trim(double value) {
        return value == Math.rint(value)
                ? Long.toString(Math.round(value))
                : Double.toString(value);
    }
}
