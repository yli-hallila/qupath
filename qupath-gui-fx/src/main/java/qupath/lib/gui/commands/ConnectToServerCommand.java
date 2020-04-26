package qupath.lib.gui.commands;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.common.RemoteOpenslide;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.Base64;

public class ConnectToServerCommand implements PathCommand {

    private Logger logger = LoggerFactory.getLogger(ConnectToServerCommand.class);

    private QuPathGUI qupath;

    private String host;
    private String username;
    private String password;

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
            RemoteOpenslide.setHost(host);
            RemoteOpenslide.setAuthentication(username, password);

            // Java HTTPClient sends only credentials when asked.
            if ((username.isBlank() && password.isBlank()) || RemoteOpenslide.login()) {
                PathPrefs.remoteOpenslideHost().setValue(host);

                qupath.showWorkspaceDialog();
            } else {
                RemoteOpenslide.setHost(null);
                RemoteOpenslide.setAuthentication(null, null);
                this.run();
            }
        }
    }

    private boolean showAuthenticationPanel() {
        GridPane pane = new GridPane();
        pane.setPrefSize(640, 210);

        ImageView logo = new ImageView(new Image("icons/OuluUni.png"));

        Label labUsername = new Label("Username");
        TextField tfUsername = new TextField();
        labUsername.setLabelFor(tfUsername);
        tfUsername.setPromptText("Guest");

        Label labPassword = new Label("Password");
        PasswordField tfPassword = new PasswordField();
        labPassword.setLabelFor(tfPassword);
        tfPassword.setPromptText("********");

        Label labHost = new Label("Host");
        TextField tfHost = new TextField();
        labHost.setLabelFor(tfHost);
        tfHost.setText(PathPrefs.remoteOpenslideHost().get());

        int row = 0;
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(12.5);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(87.5);
        pane.getColumnConstraints().addAll(col1, col2);

        pane.add(logo, 1, row++);
        pane.add(labUsername, 0, row);
        pane.add(tfUsername, 1, row++);
        pane.add(labPassword, 0, row);
        pane.add(tfPassword, 1, row++);
        pane.add(labHost, 0, row);
        pane.add(tfHost, 1, row++);

        pane.setHgap(5);
        pane.setVgap(5);

        if (!DisplayHelpers.showConfirmDialog("Login", pane, false)) {
            return false;
        }

        this.host = tfHost.getText();
        this.username = tfUsername.getText();
        this.password = tfPassword.getText();

        return true;
    }
}
