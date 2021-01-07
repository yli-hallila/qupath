package qupath.edu.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import org.controlsfx.glyphfont.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.gui.buttons.IconButtons;
import qupath.edu.lib.RemoteOpenslide;
import qupath.edu.lib.Roles;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.edu.models.ExternalUser;
import qupath.lib.gui.tools.PaneTools;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RemoteUserManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;

    public static void showManagementDialog() {
        RemoteUserManager manager = new RemoteUserManager();

        dialog = Dialogs.builder()
                .title("User Management")
                .content(manager.getPane())
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

        /* Table onClick */

        table.setOnMouseClicked(event -> {
            ExternalUser selectedItem = table.getSelectionModel().getSelectedItem();

            if (event.getClickCount() > 1 && selectedItem != null) {
                editUser(selectedItem);
            }
        });

        /* Buttons */

        Button btnCreate = new Button("Create");
        btnCreate.setDisable(true);

        Button btnEdit = new Button("Edit");
        btnEdit.setOnAction(a -> editUser(table.getSelectionModel().getSelectedItem()));
        btnEdit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Button btnDelete = new Button("Delete");
        btnDelete.setDisable(true);

        GridPane buttons = PaneTools.createColumnGridControls(btnDelete, btnEdit, btnCreate);
        buttons.setHgap(5);

        /* Pane */

        pane = new BorderPane();
        pane.setPrefWidth(600);
        pane.setPrefHeight(400);
        pane.setCenter(table);
        pane.setBottom(buttons);
        pane.setPadding(new Insets(10));
    }

    private void editUser(ExternalUser user) {
        assert user != null;

        /* GridPane */

        GridPane pane = new GridPane();
        pane.setHgap(10);
        pane.setVgap(10);
        pane.getColumnConstraints().add(new ColumnConstraints(300));

        /* Name */

        TextField tfName = new TextField();
        tfName.setText(user.getName());
        tfName.setDisable(true);

        Button btnEditName = IconButtons.createIconButton(FontAwesome.Glyph.PENCIL);
        btnEditName.setOnAction(e -> tfName.setDisable(false));

        /* Email */

        TextField tfEmail = new TextField();
        tfEmail.setText(user.getEmail());
        tfEmail.setDisable(true);

        Button btnEditEmail = IconButtons.createIconButton(FontAwesome.Glyph.PENCIL);
        btnEditEmail.setOnAction(e -> tfEmail.setDisable(false));

        /* Password */

        PasswordField tfPassword = new PasswordField();
        tfPassword.setText("**************");
        tfPassword.setDisable(true);

        Button btnEditPassword = IconButtons.createIconButton(FontAwesome.Glyph.PENCIL);
        btnEditPassword.setOnAction(e -> tfPassword.setDisable(false));
        btnEditPassword.setDisable(!RemoteOpenslide.hasRole(Roles.ADMIN));

        /* Organization */

        TextField tfOrganization = new TextField();
        tfOrganization.setText(user.getOrganization().getName());
        tfOrganization.setDisable(true);

        /* Pane */

        int row = 0;

        pane.add(tfName, 0, ++row);
        pane.add(btnEditName, 1, row);

        pane.add(tfEmail, 0, ++row);
        pane.add(btnEditEmail, 1, row);

        pane.add(tfPassword, 0, ++row);
        pane.add(btnEditPassword, 1, row);

        pane.add(tfOrganization, 0, ++row);

        /* Separator */

        Separator separator = new Separator(Orientation.HORIZONTAL);
        pane.add(separator, 0, ++row);
        GridPane.setColumnSpan(separator, 2);

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

        /* Edit Dialog */

        Optional<ButtonType> result = Dialogs.builder()
                .title("Edit user")
                .buttons(ButtonType.OK, ButtonType.CANCEL)
                .content(pane)
                .showAndWait();

        if (result.isPresent() && result.get().equals(ButtonType.OK)) {
            var confirm = Dialogs.showYesNoDialog(
                "Save changes",
                "Save any changes made to user?"
            );

            if (!confirm) {
                return;
            }

            Map<String, Object> formData = new HashMap<>();
            for (Map.Entry<Roles, CheckBox> entry : checkboxes.entrySet()) {
                formData.put(entry.getKey().name(), entry.getValue().isSelected());
            }

            formData.put("name", tfName.getText());
            formData.put("email", tfEmail.getText());

            if (!tfPassword.isDisabled()) {
                formData.put("password", tfPassword.getText());
            }

            if (RemoteOpenslide.editUser(user.getId(), formData)) {
                refresh();
                Dialogs.showInfoNotification("Success", "Successfully edited user.");
            } else {
                Dialogs.showErrorNotification("Error", "Error while editing user. See log for possibly more details.");
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
