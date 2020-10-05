package qupath.edu.gui;

import fi.ylihallila.remote.commons.Roles;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.lib.RemoteOpenslide;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.edu.models.ExternalUser;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RemoteUserManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private BorderPane pane;
    private Dialog<ButtonType> dialog;

    public static void showManagementDialog() {
        RemoteUserManager manager = new RemoteUserManager();

        manager.dialog = Dialogs.builder()
                .title("User Management")
                .content(manager.getPane())
                .buttons(ButtonType.CLOSE)
                .build();

        manager.dialog.show();
    }

    public synchronized BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    private synchronized void initializePane() {
        /* Table */

        TableView<ExternalUser> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Text("No users, none match search criteria or no permissions."));

        TableColumn<ExternalUser, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setReorderable(false);

        TableColumn<ExternalUser, String> emailColumn = new TableColumn<>("Email");
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        emailColumn.setReorderable(false);

        TableColumn<ExternalUser, String> organizationColumn = new TableColumn<>("Organization");
        organizationColumn.setCellValueFactory(new PropertyValueFactory<>("organizationName"));
        organizationColumn.setReorderable(false);

        table.getColumns().addAll(nameColumn, emailColumn, organizationColumn);
        table.getItems().addAll(RemoteOpenslide.getAllUsers());

        /* onClick */
        table.setOnMouseClicked(event -> {
            ExternalUser selectedItem = table.getSelectionModel().getSelectedItem();

            if (event.getClickCount() > 1 && selectedItem != null) {
                editUser(selectedItem);
            }
        });

        pane = new BorderPane();
        pane.setPrefWidth(600);
        pane.setPrefHeight(400);
        pane.setCenter(table);
    }

    private void editUser(ExternalUser user) {
        assert user != null;

        /* GridPane */

        GridPane pane = new GridPane();
        pane.setHgap(10);
        pane.setVgap(10);
        pane.getColumnConstraints().add(new ColumnConstraints(300));

        /* Textfields */

        int row = 0;

        TextField tfName = new TextField();
        tfName.setPromptText(user.getName());
        tfName.setDisable(true);

        TextField tfEmail = new TextField();
        tfEmail.setPromptText(user.getEmail());
        tfEmail.setDisable(true);

        TextField tfOrganization = new TextField();
        tfOrganization.setPromptText(user.getOrganization().getName());
        tfOrganization.setDisable(true);

        pane.add(tfName, 0, ++row);
        pane.add(tfEmail, 0, ++row);
        pane.add(tfOrganization, 0, ++row);

        /* Separator */

        Separator separator = new Separator(Orientation.HORIZONTAL);
        pane.add(separator, 0, ++row);
        GridPane.setColumnSpan(separator, 2);
        GridPane.setColumnSpan(tfName, 2);
        GridPane.setColumnSpan(tfEmail, 2);
        GridPane.setColumnSpan(tfOrganization, 2);

        /* Checkboxes */

        pane.add(new Label("Roles"), 0, ++row);

        Map<Roles, CheckBox> checkboxes = new HashMap<>();
        for (Roles role : Roles.getModifiableRoles()) {
            CheckBox checkbox = new CheckBox();
            checkbox.setSelected(user.getRoles().contains(role.name()));

            Label label = new Label(role.getDescription());
            label.setLabelFor(checkbox);
            label.setAlignment(Pos.BASELINE_RIGHT);

            if (role.equals(Roles.MANAGE_USERS) && user.getId().equals(RemoteOpenslide.getUserId()) ) {
                checkbox.setDisable(true);
                label.setTooltip(new Tooltip("Cannot modify this permission."));
            }

            pane.add(label, 0, ++row);
            pane.add(checkbox, 1, row);

            checkboxes.put(role, checkbox);
        }

        GridPane.setHgrow(tfName, Priority.ALWAYS);

        /* Save Button */

        Optional<ButtonType> result = Dialogs.builder()
                .title("Edit user")
                .buttons(ButtonType.OK, ButtonType.CANCEL)
                .content(pane)
                .showAndWait();

        if (result.isPresent() && result.get().equals(ButtonType.OK)) {
            var confirm = Dialogs.showYesNoDialog(
                "Save changes",
                "Save any changes made to users roles?"
            );

            if (!confirm) {
                return;
            }

            Map<String, Boolean> formData = new HashMap<>();
            for (Map.Entry<Roles, CheckBox> entry : checkboxes.entrySet()) {
                formData.put(entry.getKey().name(), entry.getValue().isSelected());
            }

            if (RemoteOpenslide.editUserRoles(user.getId(), formData)) {
                Dialogs.showInfoNotification("Success", "Successfully edited roles.");
                refresh();
            } else {
                Dialogs.showErrorNotification("Error", "Error while editing roles. See log for possibly more details.");
            }
        }
    }

    private synchronized void refresh() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refresh);
        }

        initializePane();
        dialog.getDialogPane().setContent(pane);
    }
}
