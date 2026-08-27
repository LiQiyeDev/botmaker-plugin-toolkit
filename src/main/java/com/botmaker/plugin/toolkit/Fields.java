package com.botmaker.plugin.toolkit;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * The two input controls an editor cannot get right by reaching for the JavaFX one directly.
 *
 * <p>A plain {@link TextField} fires nothing useful: {@code setOnAction} catches Enter and misses the far
 * more common case of the user typing and clicking away, which is how a value gets silently lost. A plain
 * {@link Spinner} accepts anything typed into it, including text, and hands back the last valid value with
 * the invalid text still on screen. Both bugs were in this repository before the widgets that fixed them,
 * and both are the kind a plugin author reproduces exactly once each.
 */
public final class Fields {

    private Fields() {}

    /**
     * A text field that commits on Enter <b>and</b> on losing focus.
     *
     * <p>Both, always. Committing only on Enter loses every edit the user ends by clicking elsewhere, which
     * is most of them; committing only on focus loss makes Enter do nothing in a control that plainly looks
     * like it should accept it.
     */
    public static TextField committing(String initial, String prompt, Consumer<String> onCommit) {
        TextField field = Styles.on(new TextField(initial == null ? "" : initial), Styles.INSET_FIELD);
        field.setPromptText(prompt == null ? "" : prompt);
        field.setOnAction(e -> {
            if (onCommit != null) onCommit.accept(field.getText());
        });
        field.focusedProperty().addListener((obs, had, has) -> {
            if (!has && onCommit != null) onCommit.accept(field.getText());
        });
        return field;
    }

    /**
     * A whole-number spinner clamped to {@code [min, max]}, which refuses text rather than accepting it.
     *
     * <p>Editable, because a bounded range is often wide and dragging to 1920 is not a thing anybody wants
     * to do — but with the value factory's own converter, so what is typed is either a number in range or
     * is reverted on commit.
     */
    public static Spinner<Integer> integer(int value, int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        Spinner<Integer> spinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                lo, hi, Math.clamp(value, lo, hi)));
        spinner.setEditable(true);
        return Styles.on(spinner, Styles.INSET_FIELD_FLAT);
    }

    /**
     * A slider and a read-out for a bounded fractional number — a confidence, a tolerance, a delay.
     *
     * <p>A slider and not a spinner because these are the numbers whose <em>scale</em> is the thing nobody
     * knows: a tolerance of 12 means nothing until you can see where 12 sits between the ends. The read-out
     * is beside it because the exact value still matters once the shape of the range is understood.
     *
     * <p>{@code onChange} fires continuously while dragging. That is deliberate and matches the contract's
     * note that {@link com.botmaker.plugin.api.ValueContext#set(java.util.List)} may be called repeatedly —
     * it is what lets an editor that previews its value track the drag.
     */
    public static HBox bounded(double value, double min, double max, double step, DoubleConsumer onChange) {
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        Slider slider = new Slider(lo, hi, Math.clamp(value, lo, hi));
        if (step > 0) {
            slider.setBlockIncrement(step);
            slider.setMajorTickUnit(step);
            slider.setSnapToTicks(true);
        }
        Label readout = Styles.on(new Label(format(slider.getValue(), step)), Styles.CAPTION_STRONG);
        slider.valueProperty().addListener((obs, was, now) -> {
            readout.setText(format(now.doubleValue(), step));
            if (onChange != null) onChange.accept(now.doubleValue());
        });

        HBox row = new HBox(6, slider, readout);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** A whole number reads as one; anything else keeps two places, which is enough for every real knob. */
    private static String format(double value, double step) {
        boolean whole = step >= 1 && step == Math.rint(step);
        return whole || value == Math.rint(value)
                ? Long.toString(Math.round(value))
                : String.format("%.2f", value);
    }
}
