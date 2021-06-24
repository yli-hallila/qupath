package qupath.edu.gui.dialogs;

import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import qupath.edu.EduExtension;
import qupath.lib.gui.dialogs.Dialogs;

public class HelpDialog {

    private HelpDialog() {}

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;

    public static void showHelpDialog() {
        HelpDialog manager = new HelpDialog();

        dialog = Dialogs.builder()
                .title("About QuPath Edu")
                .content(manager.getPane())
                .width(400)
                .height(300)
                .build();

        dialog.setResult(ButtonType.CLOSE);
        dialog.show();
    }

    public BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    private synchronized void initializePane() {
        pane = new BorderPane();

        /* QuPath Edu Logo */

        ImageView logo = new ImageView(new Image(getClass().getResourceAsStream("/img/QuPath Edu.png")));

        /* Text */

        Text version = new Text("Version " + EduExtension.getExtensionVersion().toString());
        version.setTextAlignment(TextAlignment.CENTER);

        /* Sponsors */

        Text sponsorsText = new Text("QuPath Edu development funded by");

        ImageView uniOulu = new ImageView(new Image(getClass().getResourceAsStream("/img/University of Oulu.png")));
        uniOulu.setFitWidth(400);
        uniOulu.setPreserveRatio(true);

        ImageView pathSoc = new ImageView(new Image(getClass().getResourceAsStream("/img/Pathological Society.png")));
        pathSoc.setFitWidth(400);
        pathSoc.setPreserveRatio(true);

        ImageView uniEdin = new ImageView(new Image(getClass().getResourceAsStream("/img/University of Edinburgh.png")));
        uniEdin.setFitWidth(400);
        uniEdin.setPreserveRatio(true);

        VBox sponsors = new VBox(uniOulu, pathSoc, uniEdin);
        sponsors.setAlignment(Pos.CENTER);

        VBox sponsorBox = new VBox(sponsorsText, sponsors);
        sponsorBox.setSpacing(10);
        sponsorBox.setAlignment(Pos.CENTER);

        /* Pane */

        pane.setTop(logo);
        pane.setCenter(version);
        pane.setBottom(sponsorBox);
        pane.setPrefWidth(400);
        pane.setPrefHeight(300);

        BorderPane.setAlignment(logo, Pos.CENTER);
    }
}
