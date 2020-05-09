package qupath.lib.gui.commands;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.control.StatusBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.common.RemoteOpenslide;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.concurrent.atomic.AtomicBoolean;

public class ConnectToServerCommand implements PathCommand {

    private Logger logger = LoggerFactory.getLogger(ConnectToServerCommand.class);

    private QuPathGUI qupath;

    private boolean guest = true;
    private String username = "";
    private String password = "";

    public ConnectToServerCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    @Override
    public void run() {
        if (RemoteOpenslide.getHost() != null) {
            qupath.showWorkspaceDialog();
            return;
        }

        if (showAuthenticationPanel()) {
            RemoteOpenslide.setHost(PathPrefs.remoteOpenslideHost().get());
            RemoteOpenslide.setAuthentication(username, password);

            // Java HTTPClient sends only credentials when asked.
            if (this.guest || RemoteOpenslide.login()) {
                qupath.showWorkspaceDialog();
            } else {
                RemoteOpenslide.setHost(null);
                RemoteOpenslide.setAuthentication(null, null);
                this.run();
            }
        }
    }

    private boolean showAuthenticationPanel() {
        AtomicBoolean cancelled = new AtomicBoolean(true);
        Stage dialog = new Stage();

        BorderPane borderPane = new BorderPane();
        ImageView logo = new ImageView(new Image("icons/OuluUni.png"));
        VBox buttons = new VBox();
        StatusBar statusBar = new StatusBar();

        Button loginAsGuest = new Button("Login as guest");
        Button loginWithUsername = new Button("Login with username");

        loginAsGuest.prefWidthProperty().bind(borderPane.widthProperty().subtract(20));
        loginWithUsername.prefWidthProperty().bind(borderPane.widthProperty().subtract(20));

        loginAsGuest.setOnAction(action -> {
            cancelled.set(false);
            this.guest = true;

            dialog.close();
        });

        loginWithUsername.setOnAction(action -> showLoginDialog(cancelled, dialog));

        borderPane.setTop(logo);
        borderPane.setCenter(buttons);
        borderPane.setBottom(statusBar);

        buttons.setPadding(new Insets(10));
        buttons.setSpacing(15);
        buttons.getChildren().addAll(loginAsGuest, loginWithUsername);

        statusBar.setText(PathPrefs.remoteOpenslideHost().get());

        dialog.setWidth(360);
        dialog.setHeight(360);
        dialog.setResizable(false);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setScene(new Scene(borderPane));
        dialog.initOwner(qupath.getStage());
        dialog.showAndWait();

        return !cancelled.get();
    }

    private void showLoginDialog(AtomicBoolean cancelled, Stage dialog) {
        GridPane loginPane = new GridPane();

        Label labUsername = new Label("Username");
        TextField tfUsername = new TextField();
        labUsername.setLabelFor(tfUsername);
        tfUsername.setPromptText("Username");

        Label labPassword = new Label("Password");
        PasswordField tfPassword = new PasswordField();
        labPassword.setLabelFor(tfPassword);
        tfPassword.setPromptText("********");

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(35);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(65);
        loginPane.getColumnConstraints().addAll(col1, col2);

        loginPane.setHgap(5);
        loginPane.setVgap(5);

        int row = 0;
        loginPane.add(labUsername, 0, row);
        loginPane.add(tfUsername, 1, row++);
        loginPane.add(labPassword, 0, row);
        loginPane.add(tfPassword, 1, row);

        if (DisplayHelpers.showConfirmDialog("Login", loginPane, false)) {
            cancelled.set(false);
            this.guest = false;
            this.username = tfUsername.getText();
            this.password = tfPassword.getText();

            dialog.close();
        }
    }
}
