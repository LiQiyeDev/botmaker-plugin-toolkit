package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.Region;
import com.botmaker.plugin.api.ValueContext;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Whole editors: hand one a {@link ValueContext} and it is done.
 *
 * <p>This is the layer the toolkit exists for. Everything under it — {@link Pills}, {@link Fields},
 * {@link Modals} — is JavaFX with the host's conventions baked in; these six read the value, write it back
 * and reach {@link com.botmaker.plugin.api.StudioServices} for capture and dialogs themselves, so a
 * plugin's editor for a rectangle is a predicate and a method reference:
 *
 * <pre>{@code
 * SlotEditor.of(c -> c.type().is(Rect.class), Editors::region)
 * }</pre>
 *
 * <p>They were extracted from the host's own thirteen pickers rather than designed, which is why there are
 * six and not sixteen: these are the shapes that recurred. An editor that needs something else builds it
 * from the layer below, and the day a second one wants the same thing it is worth adding here.
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
