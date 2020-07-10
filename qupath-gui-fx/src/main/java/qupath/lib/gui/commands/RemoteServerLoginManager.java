package qupath.lib.gui.commands;

import com.microsoft.aad.msal4j.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
import qupath.lib.objects.remoteopenslide.ExternalOrganization;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class RemoteServerLoginManager {

    private static Logger logger = LoggerFactory.getLogger(RemoteServerLoginManager.class);

    private static QuPathGUI qupath = QuPathGUI.getInstance();

    private final StringProperty selectedOrganizationProperty = new SimpleStringProperty();

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;

    public static void showLoginDialog() {
        RemoteServerLoginManager loginDialog = new RemoteServerLoginManager();

        dialog = Dialogs.builder()
                .title("Login")
                .content(loginDialog.getPane())
                .build();

        dialog.getDialogPane().getStylesheets().add(RemoteServerLoginManager.class.getClassLoader().getResource("css/remove_buttonbar.css").toExternalForm());
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
        pane = new BorderPane();

        /* Logos */
        
        ComboBox<ExternalOrganization> cbLogos = new ComboBox<>();
        cbLogos.prefWidthProperty().bind(pane.widthProperty());
        cbLogos.setButtonCell(new ImageViewListCell(false));
        cbLogos.setCellFactory(f -> new ImageViewListCell(true));
        cbLogos.getItems().addAll(RemoteOpenslide.getOrganizations().orElse(Collections.emptyList()));

        selectPreviousOrganization(cbLogos);

        selectedOrganizationProperty.bind(Bindings.createStringBinding(
            () -> cbLogos.getSelectionModel().getSelectedItem().getId(),
            cbLogos.getSelectionModel().selectedItemProperty()
        ));

        PathPrefs.previousOrganization().bind(selectedOrganizationProperty);


        /* Buttons */
        
        Button btnLoginGuest = new Button("Continue as guest");
        btnLoginGuest.setOnAction(e -> loginAsGuest());
        
        Button btnLoginUsername = new Button("Login with username");
        btnLoginUsername.setOnAction(e -> showAuthDialog());
        
        Button btnLoginMicrosoft = new Button("Login using Microsoft");
        btnLoginMicrosoft.setOnAction(e -> showMicrosoftAuthDialog());

        GridPane buttons = PaneTools.createRowGridControls(btnLoginGuest, new Separator(), btnLoginUsername, btnLoginMicrosoft);
        buttons.setPadding(new Insets(10));
        buttons.setVgap(10);

        /* Statusbar */
        
        StatusBar statusBar = new StatusBar();
        statusBar.setText("Host " + PathPrefs.remoteOpenslideHost().get());

        /* Borderpane */

        BorderPane.setMargin(cbLogos, new Insets(10));

        pane.setPrefWidth(360);
        pane.setTop(cbLogos);
        pane.setCenter(buttons);
        pane.setBottom(statusBar);
        pane.setPadding(new Insets(0));
    }

    private void selectPreviousOrganization(ComboBox<ExternalOrganization> cbLogos) {
        if (PathPrefs.previousOrganization().get() != null) {
            cbLogos.getItems().forEach(organization -> {
                if (organization.getId().equals(PathPrefs.previousOrganization().get())) {
                    cbLogos.getSelectionModel().select(organization);
                }
            });
        } else {
            cbLogos.getSelectionModel().select(0);
        }
    }

    private void loginAsGuest() {
        RemoteOpenslide.setAuthType(RemoteOpenslide.AuthType.GUEST);
        RemoteOpenslide.setOrganizationId(selectedOrganizationProperty.get());

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
            if (RemoteOpenslide.login(tfUsername.getText(), tfPassword.getText())) {
                dialog.close();
                qupath.showWorkspaceDialog();
            } else {
                Dialogs.showErrorNotification("Error", "Wrong username, password or host");
                showAuthDialog();
            }
        }
    }

    // TODO: Kill server if retry - different port?
    private void showMicrosoftAuthDialog() {
        try {
            StringProperty cache = PathPrefs.createPersistentPreference("MSAL4J_TOKEN", "");

            ITokenCacheAccessAspect persistenceAspect = new TokenPersistence(cache);

            PublicClientApplication app = PublicClientApplication
                .builder("eccc9211-faa5-40d5-9ff9-7a5087dbcadb")
                .setTokenCacheAccessAspect(persistenceAspect)
                .authority("https://login.microsoftonline.com/common/")
                .build();

            Set<String> scopes = Set.of("user.read", "openid", "profile", "email");

            InteractiveRequestParameters parameters = InteractiveRequestParameters
                .builder(new URI("http://localhost:51820"))
                .scopes(scopes)
                .build();

            Task<IAuthenticationResult> task = new Task<>() {
                @Override
                protected IAuthenticationResult call() {
                    return app.acquireToken(parameters).join();
                }
            };

            task.setOnSucceeded(event -> {
                IAuthenticationResult result = task.getValue();

                if (RemoteOpenslide.validate(result.idToken())) {
                    String[] split = result.account().homeAccountId().split("\\.");
                    RemoteOpenslide.setUserId(split[0]);
                    RemoteOpenslide.setOrganizationId(split[1]);

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

    public static void closeDialog() {
        dialog.close();
    }

    private class ImageViewListCell extends ListCell<ExternalOrganization> {

        /**
         * Flag to indicate whether this is the factory for the button or cells.
         */
        private boolean buttonCell;

        public ImageViewListCell(boolean buttonCell) {
            this.buttonCell = buttonCell;
        }

        {
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(ExternalOrganization item, boolean empty) {
            super.updateItem(item, empty);

            if (item == null || empty) {
                setGraphic(null);
            } else {
                Image image = new Image(RemoteOpenslide.getHost() + item.getLogoUrl());
                ImageView imageView = new ImageView(image);

                if (buttonCell) {
                    imageView.setFitWidth(330);
                } else {
                    imageView.setFitWidth(310);
                }

                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.setCache(true);

                setGraphic(imageView);
            }
        }
    }

    /* DEBUG ONLY */
    static class TokenPersistence implements ITokenCacheAccessAspect {
        StringProperty data;

        TokenPersistence(StringProperty data) {
            this.data = data;
        }

        @Override
        public void beforeCacheAccess(ITokenCacheAccessContext iTokenCacheAccessContext) {
            logger.debug("Reading cache");
            logger.debug(data.get());
            iTokenCacheAccessContext.tokenCache().deserialize(data.get());
        }

        @Override
        public void afterCacheAccess(ITokenCacheAccessContext iTokenCacheAccessContext) {
            logger.debug("Saving cache");
            data.set(iTokenCacheAccessContext.tokenCache().serialize());
            logger.debug(data.get());
        }
    }
}
