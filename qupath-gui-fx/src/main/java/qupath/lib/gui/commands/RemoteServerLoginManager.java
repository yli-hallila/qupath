package qupath.lib.gui.commands;

import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.InteractiveRequestParameters;
import com.microsoft.aad.msal4j.PublicClientApplication;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import org.controlsfx.control.StatusBar;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.common.RemoteOpenslide;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.PaneTools;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Set;

// TODO: use something else than host as a indicator of being logged in.
public class RemoteServerLoginManager {

    private static Logger logger = LoggerFactory.getLogger(RemoteServerLoginManager.class);

    private static QuPathGUI qupath = QuPathGUI.getInstance();

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;

    public static void showLoginDialog() {
        RemoteServerLoginManager loginDialog = new RemoteServerLoginManager();

        dialog = Dialogs.builder()
                .title("Login")
                .content(loginDialog.getPane())
                .build();

        dialog.setResult(ButtonType.CLOSE);
        dialog.show();
    }

    public synchronized BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    private synchronized void initializePane() {
        /* Logo */
        
        ImageView logo = new ImageView(new Image("icons/OuluUni.png"));

        /* Buttons */
        
        Button btnLoginGuest = new Button("Continue as guest");
        btnLoginGuest.setOnAction(e -> loginAsGuest());
        
        Button btnLoginUsername = new Button("Login with username");
        btnLoginUsername.setOnAction(e -> showAuthDialog());
        
        Button btnLoginMicrosoft = new Button("Login using Microsoft");
        btnLoginMicrosoft.setOnAction(e -> showMicrosoftAuthDialog());

        GridPane buttons = PaneTools.createRowGridControls(btnLoginGuest, btnLoginUsername, btnLoginMicrosoft);
        buttons.setPadding(new Insets(10));
        buttons.setVgap(10);

        /* Statusbar */
        
        StatusBar statusBar = new StatusBar();
        statusBar.setText("Host " + PathPrefs.remoteOpenslideHost().get());

        /* Borderpane */
        
        pane = new BorderPane();
        pane.setPrefWidth(360);
        pane.setTop(logo);
        pane.setCenter(buttons);
        pane.setBottom(statusBar);
        pane.setPadding(new Insets(0));
    }

    private void loginAsGuest() {
        RemoteOpenslide.setHost(PathPrefs.remoteOpenslideHost().get());
        RemoteOpenslide.setAuthentication("", "");

        dialog.close();
        qupath.showWorkspaceDialog();
    }

    private void showAuthDialog() {
        /* Textfields */

        Label labUsername = new Label("Username");
        TextField tfUsername = new TextField();
        labUsername.setLabelFor(tfUsername);
        tfUsername.setPromptText("Username");
        Platform.runLater(tfUsername::requestFocus);

        Label labPassword = new Label("Password");
        PasswordField tfPassword = new PasswordField();
        labPassword.setLabelFor(tfPassword);
        tfPassword.setPromptText("********");

        /* Constraints */

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setPercentWidth(30);

        ColumnConstraints textFieldColumn = new ColumnConstraints();
        textFieldColumn.setPercentWidth(70);

        /* Pane */

        GridPane loginPane = new GridPane();
        loginPane.getColumnConstraints().addAll(labelColumn, textFieldColumn);
        loginPane.setHgap(5);
        loginPane.setVgap(5);

        int row = 0;
        loginPane.add(labUsername, 0, row);
        loginPane.add(tfUsername, 1, row++);
        loginPane.add(labPassword, 0, row);
        loginPane.add(tfPassword, 1, row);

        /* Dialog */

        Optional<ButtonType> choice = Dialogs.builder()
            .buttons(new ButtonType("Login", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL)
            .title("Authenticate")
            .content(loginPane)
            .build()
            .showAndWait();

        if (choice.isPresent() && choice.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
            RemoteOpenslide.setHost(PathPrefs.remoteOpenslideHost().get());

            if (RemoteOpenslide.login(tfUsername.getText(), tfPassword.getText())) {
                dialog.close();
                qupath.showWorkspaceDialog();
            } else {
                RemoteOpenslide.setHost(null);

                Dialogs.showErrorNotification("Error", "Wrong username, password or host");
                showAuthDialog();
            }
        }
    }

    // TODO: Kill server if retry - different port?
    private void showMicrosoftAuthDialog() {
        try {
            PublicClientApplication app = PublicClientApplication
                .builder("eccc9211-faa5-40d5-9ff9-7a5087dbcadb")
                .authority("https://login.microsoftonline.com/common/")
                .build();

            Set<String> scopes = Set.of("user.read", "openid", "profile", "email");

            InteractiveRequestParameters parameters = InteractiveRequestParameters
                .builder(new URI("http://localhost:12425"))
                .scopes(scopes)
                .build();

            Task<IAuthenticationResult> task = new Task<>() {
                @Override
                protected IAuthenticationResult call() {
                    return app.acquireToken(parameters).join();
                }
            };

            task.setOnSucceeded(event -> {
                RemoteOpenslide.setHost(PathPrefs.remoteOpenslideHost().get());
                IAuthenticationResult result = task.getValue();

                RemoteOpenslide.setToken(result.idToken());
                if (RemoteOpenslide.validate()) {
                    Platform.runLater(() -> {
                        dialog.close();
                        qupath.showWorkspaceDialog();
                    });
                } else {
                    Dialogs.showErrorNotification(
                    "Error",
                    "Error while authenticating with Microsoft."
                    );
                }
            });

            ProgressDialog progress = new ProgressDialog(task);
            progress.setTitle("Logging in ...");
            progress.setContentText("Follow the instructions in your browser");
            progress.initOwner(dialog.getOwner());
            progress.getDialogPane().setGraphic(null);
            progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
            progress.getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, e -> {
                task.cancel();
                progress.setHeaderText("Cancelling...");
                progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);

                e.consume();
            });

            QuPathGUI.getInstance().submitShortTask(task);
            progress.showAndWait();
        } catch (MalformedURLException | URISyntaxException e) {
            Dialogs.showErrorNotification("Error", "Error while logging in. See log for more details");
            logger.error(e.getLocalizedMessage(), e);
        }
    }
}
