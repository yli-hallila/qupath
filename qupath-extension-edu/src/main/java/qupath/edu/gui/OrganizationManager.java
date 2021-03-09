package qupath.edu.gui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.lib.RemoteOpenslide;
import qupath.edu.lib.Roles;
import qupath.edu.models.ExternalOrganization;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

public class OrganizationManager {

    private final static Logger logger = LoggerFactory.getLogger(OrganizationManager.class);

    private BorderPane pane;
    private static Dialog<ButtonType> dialog;
    private TableView<ExternalOrganization> table;

    private ReadOnlyObjectProperty<ExternalOrganization> selected;
    private SimpleBooleanProperty hasPermission = new SimpleBooleanProperty(false);

    public static void showOrganizationManager() {
        OrganizationManager manager = new OrganizationManager();

        dialog = Dialogs.builder()
                .title("Organization management")
                .content(manager.getPane())
                .size(1000, 600)
                .build();

        dialog.getDialogPane().getStylesheets().add(OrganizationManager.class.getClassLoader().getResource("css/remove_buttonbar.css").toExternalForm());
        dialog.setResult(ButtonType.CLOSE);
        dialog.show();
    }

    private synchronized void refresh() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refresh);
        }

        initializePane();
        dialog.getDialogPane().setContent(pane);
    }

    private OrganizationManager() {}

    public BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    private synchronized void initializePane() {
        hasPermission.set(RemoteOpenslide.hasRole(Roles.ADMIN));

        /* Table */

        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Text("No organizations or no permissions."));

        TableColumn<ExternalOrganization, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setReorderable(false);

        TableColumn<ExternalOrganization, String> uuidColumn = new TableColumn<>("UUID");
        uuidColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        uuidColumn.setReorderable(false);

        TableColumn<ExternalOrganization, String> logoUrlColumn = new TableColumn<>("Logo URL");
        logoUrlColumn.setCellValueFactory(new PropertyValueFactory<>("logoUrl"));
        logoUrlColumn.setReorderable(false);

        table.getColumns().addAll(nameColumn, uuidColumn, logoUrlColumn);

        table.setItems(FXCollections.observableArrayList(RemoteOpenslide.getAllOrganizations().orElse(Collections.emptyList())));

        selected = table.getSelectionModel().selectedItemProperty();

        /* Buttons */

        Button btnCreate = new Button("Create new");
        btnCreate.setOnAction(e -> createOrganization());
        btnCreate.disableProperty().bind(hasPermission.not());

        Button btnEdit = new Button("Edit");
        btnEdit.setOnAction(e -> editOrganization());
        btnEdit.disableProperty().bind(selected.isNull().or(hasPermission.not()));

        Button btnDelete = new Button("Delete");
        btnDelete.setOnAction(e -> deleteOrganization());
        btnDelete.disableProperty().bind(selected.isNull().or(hasPermission.not()));

        GridPane buttons = PaneTools.createColumnGridControls(btnCreate, btnEdit, btnDelete);
        buttons.setHgap(5);

        /* Pane */

        BorderPane.setMargin(table, new Insets(10, 0, 10, 0));

        pane = new BorderPane();
        pane.setPrefWidth(800);
        pane.setPrefHeight(400);
        pane.setTop(new Text("Only administrators may change these settings."));
        pane.setCenter(table);
        pane.setBottom(buttons);
        pane.setPadding(new Insets(10));
    }

    private void createOrganization() {
        var name = Dialogs.showInputDialog("Organization name", "", "");

        if (name == null) {
            return;
        }

        Optional<ExternalOrganization> result = RemoteOpenslide.createOrganization(name);

        if (result.isPresent()) {
            refresh();
            Dialogs.showInfoNotification("Success", "Organization successfully created.");
        } else {
            Dialogs.showErrorNotification(
                "Error when creating organization",
                "See log for possibly more details."
            );
        }
    }

    private void editOrganization() {
        ExternalOrganization organization = selected.get();

        var name = Dialogs.showInputDialog("Organization name", "", organization.getName());

        if (name == null) {
            return;
        }

        RemoteOpenslide.Result result = RemoteOpenslide.editOrganization(organization.getId(), name);

        if (result == RemoteOpenslide.Result.OK) {
            refresh();
            Dialogs.showInfoNotification("Success", "Organization successfully edited.");
        } else {
            Dialogs.showErrorNotification(
                "Error when editing organization",
                "See log for possibly more details."
            );
        }
    }

    private void deleteOrganization() {
        var confirm = Dialogs.showInputDialog(
            "WARNING!",
            "Deleting this organization will also delete *all* workspaces, users, slides and projects belonging to this organization. This action is irreversible." +
            "\n\n" +
            "If you wish to continue, type DELETE in the box below and continue.",
            ""
        );

        if (!Objects.equals(confirm, "DELETE")) {
            Dialogs.showInfoNotification("Cancelled", "Operation cancelled.");
            return;
        }

        ExternalOrganization organization = selected.get();
        RemoteOpenslide.Result result = RemoteOpenslide.deleteOrganization(organization.getId());

        if (result == RemoteOpenslide.Result.OK) {
            refresh();
            Dialogs.showInfoNotification("Success", "Organization successfully deleted.");
        } else {
            Dialogs.showErrorNotification(
                "Error when editing organization",
                "See log for possibly more details."
            );
        }
    }
}