package qupath.lib.gui.commands;

import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.InteractiveRequestParameters;
import com.microsoft.aad.msal4j.PublicClientApplication;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
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
import org.controlsfx.control.StatusBar;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.common.RemoteOpenslide;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConnectToServerCommand implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(ConnectToServerCommand.class);

    private static QuPathGUI qupath = QuPathGUI.getInstance();

    private static boolean guest = true;
    private static String username = "";
    private static String password = "";

    @Override
    public void run() {
        openDialog();
    }

    public static void openDialog() {
        if (RemoteOpenslide.getHost() != null) {
            qupath.showWorkspaceDialog();
            return;
        }

        if (showAuthenticationPanel()) {
            RemoteOpenslide.setHost(PathPrefs.remoteOpenslideHost().get());
            RemoteOpenslide.setAuthentication(username, password);

            if (guest || RemoteOpenslide.login()) {
                qupath.showWorkspaceDialog();
            } else {
                Dialogs.showErrorNotification(
                "Login error",
                "Check your credentials and that you're connecting to the right host."
                );

                RemoteOpenslide.logout();
                openDialog();
            }
        }
    }

    private static boolean showAuthenticationPanel() {
        AtomicBoolean cancelled = new AtomicBoolean(true);
        Stage dialog = new Stage();

        BorderPane borderPane = new BorderPane();
        ImageView logo = new ImageView(new Image("icons/OuluUni.png"));
        VBox buttons = new VBox();
        StatusBar statusBar = new StatusBar();

        Button loginAsGuest = new Button("Continue as guest");
        Button loginWithUsername = new Button("Login with username");

        loginAsGuest.prefWidthProperty().bind(borderPane.widthProperty().subtract(20));
        loginWithUsername.prefWidthProperty().bind(borderPane.widthProperty().subtract(20));

        loginAsGuest.setOnAction(action -> {
            cancelled.set(false);
            guest = true;

            dialog.close();
        });

        loginWithUsername.setOnAction(action -> showLoginDialog(cancelled, dialog));

        borderPane.setTop(logo);
        borderPane.setCenter(buttons);
        borderPane.setBottom(statusBar);

        buttons.setPadding(new Insets(10));
        buttons.setSpacing(15);
        buttons.getChildren().addAll(loginAsGuest, loginWithUsername);

        statusBar.setText("Host " + PathPrefs.remoteOpenslideHost().get());

        dialog.setWidth(360);
        dialog.setHeight(360);
        dialog.setResizable(false);
        dialog.setScene(new Scene(borderPane));
        dialog.initOwner(qupath.getStage());
        dialog.showAndWait();

        return !cancelled.get();
    }

    private static void showLoginDialog(AtomicBoolean cancelled, Stage dialog) {
        GridPane loginPane = new GridPane();

        Label labUsername = new Label("Username");
        TextField tfUsername = new TextField();
        labUsername.setLabelFor(tfUsername);
        tfUsername.setPromptText("Username");
        Platform.runLater(tfUsername::requestFocus);

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

        if (Dialogs.showConfirmDialog("Login", loginPane)) {
            cancelled.set(false);
            guest = false;
            username = tfUsername.getText();
            password = tfPassword.getText();

            dialog.close();
        }
    }
}
