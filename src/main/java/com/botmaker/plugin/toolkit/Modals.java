package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.ValueContext;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Consumer;

/**
 * The two windows an editor opens, themed and owned correctly without the plugin knowing how either is done.
 *
 * <p>A plugin <b>must not</b> build its own {@link Stage} and show it. Two things go wrong when it does, and
 * neither is visible to the author who did it: the window gets none of the host's stylesheet, so it is a
 * slab of default JavaFX in the middle of a dark application; and with no owner it is a top-level window, so
 * it falls behind the editor instead of over it and does not close with the project. Both are handled here,
 * through {@link com.botmaker.plugin.api.Theme} and {@link com.botmaker.plugin.api.Dialogs#owner()}, which is
 * the whole reason those two are on the contract.
 *
 * <p><b>Cancelling calls nothing.</b> Every callback here is invoked only on a real choice — the same rule
 * {@link com.botmaker.plugin.api.Capture} states, and for the same reason: an editor that writes a default
 * when the user backs out has just destroyed a value nobody asked it to touch.
 */
public final class Modals {

    private Modals() {}

    /**
     * A row of labelled whole-number fields — {@code x}, {@code y}, {@code width}, {@code height} — with
     * <i>OK</i> and <i>Cancel</i>.
     *
     * <p>{@code onCommit} receives one value per label, in the same order, with anything unparsable read as
     * {@code 0}. It is not called if the user cancels or closes the window.
     */
    public static void numbers(ValueContext ctx, String title, String[] labels, int[] initial,
                               Consumer<int[]> onCommit) {
        String[] names = labels == null ? new String[0] : labels;
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        TextField[] fields = new TextField[names.length];
        for (int i = 0; i < names.length; i++) {
            fields[i] = Styles.on(new TextField(Integer.toString(
                    initial != null && i < initial.length ? initial[i] : 0)), Styles.INSET_FIELD);
            fields[i].setPrefColumnCount(5);
            VBox cell = new VBox(2, Styles.on(new Label(names[i]), Styles.CAPTION), fields[i]);
            row.getChildren().add(cell);
        }

        Stage stage = new Stage();
        Runnable commit = () -> {
            int[] out = new int[fields.length];
            for (int i = 0; i < fields.length; i++) out[i] = parseInt(fields[i].getText());
            stage.close();
            if (onCommit != null) onCommit.accept(out);
        };

        VBox root = Styles.on(new VBox(10, Styles.on(new Label(title), Styles.DIALOG_HEADING), row,
                buttons(commit, stage::close)), Styles.DIALOG_COMPACT);
        show(ctx, stage, title, root);
    }

    /**
     * A scrolling grid of pictures, one of which the user picks.
     *
     * <p>{@code emptyMessage} is shown in place of the grid when there is nothing to choose — which is a
     * normal state (no templates captured yet, no games found) and reads as a broken window if left blank.
     */
    public static void chooser(ValueContext ctx, String title, List<Thumbnail> items, String emptyMessage,
                               Consumer<Thumbnail> onChosen) {
        Stage stage = new Stage();
        Parent body;
        if (items == null || items.isEmpty()) {
            body = Styles.on(new Label(Values.labelOr(emptyMessage, "Nothing to choose from yet.")),
                    Styles.DIALOG_HINT);
        } else {
            FlowPane grid = new FlowPane(10, 10);
            for (Thumbnail item : items) {
                grid.getChildren().add(tile(item, () -> {
                    stage.close();
                    if (onChosen != null) onChosen.accept(item);
                }));
            }
            ScrollPane scroll = new ScrollPane(grid);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(360);
            body = scroll;
        }

        VBox root = Styles.on(new VBox(10, Styles.on(new Label(title), Styles.DIALOG_HEADING), body,
                buttons(null, stage::close)), Styles.DIALOG_COMPACT);
        show(ctx, stage, title, root);
    }

    /** One picture-and-caption cell. A thumbnail with no image is its caption alone, never a blank box. */
    private static Button tile(Thumbnail item, Runnable onPicked) {
        VBox content = new VBox(4);
        content.setAlignment(Pos.CENTER);
        if (item.image() != null) {
            ImageView view = new ImageView(item.image());
            view.setPreserveRatio(true);
            view.setFitWidth(96);
            view.setFitHeight(96);
            content.getChildren().add(view);
        }
        content.getChildren().add(Styles.on(new Label(item.label()), Styles.TILE_NAME));

        Button tile = Styles.on(new Button(), Styles.TILE);
        tile.setGraphic(content);
        tile.setOnAction(e -> onPicked.run());
        return tile;
    }

    /** <i>Cancel</i> on the left of <i>OK</i>, right-aligned; {@code onOk} null means there is no OK. */
    private static HBox buttons(Runnable onOk, Runnable onCancel) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox bar = new HBox(8, spacer);
        Button cancel = new Button(onOk == null ? "Close" : "Cancel");
        cancel.setOnAction(e -> onCancel.run());
        bar.getChildren().add(cancel);
        if (onOk != null) {
            Button ok = Styles.on(new Button("OK"), Styles.PRIMARY_BUTTON);
            ok.setDefaultButton(true);
            ok.setOnAction(e -> onOk.run());
            bar.getChildren().add(ok);
        }
        bar.setAlignment(Pos.CENTER_RIGHT);
        return bar;
    }

    /** Themed, owned by the editor's window, modal to it, and shown without blocking the caller. */
    private static void show(ValueContext ctx, Stage stage, String title, Parent root) {
        StudioServices services = ctx == null ? null : ctx.services();
        stage.setTitle(title);
        stage.setScene(services == null ? new javafx.scene.Scene(root) : services.theme().scene(root));
        if (services != null) {
            javafx.stage.Window owner = services.dialogs().owner();
            if (owner != null) {
                stage.initOwner(owner);
                stage.initModality(Modality.WINDOW_MODAL);
            }
            services.theme().apply(stage);
        }
        stage.show();
    }

    /** Whatever was typed as a whole number, or {@code 0} — see {@link Values} on why nothing throws here. */
    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
