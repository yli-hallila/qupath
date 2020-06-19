package qupath.lib.gui.commands;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import qupath.lib.common.RemoteOpenslide;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.panes.ProjectListCell;
import qupath.lib.objects.remoteopenslide.ExternalProject;
import qupath.lib.objects.remoteopenslide.ExternalWorkspace;

import java.util.ArrayList;

import static qupath.lib.common.RemoteOpenslide.*;

public class WorkspaceManager {

    private static Dialog<ButtonType> dialog;
    private final QuPathGUI qupath;

    private BorderPane pane;
    private TabPane tabPane;

    public static void showWorkspace(QuPathGUI qupath) {
        WorkspaceManager manager = new WorkspaceManager(qupath);

        dialog = Dialogs.builder()
                .title("Select workspace")
                .content(manager.getPane())
                .size(400, 500)
                .build();

        dialog.setResult(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private WorkspaceManager(QuPathGUI qupath) {
        this.qupath = qupath;
    }

    public BorderPane getPane() {
        if (pane == null) {
            initializePane();
        }

        return pane;
    }

    public TabPane getTabPane() {
        return tabPane;
    }

    private synchronized void initializePane() {
        tabPane = new TabPane();
        tabPane.setPrefWidth(360);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        tabPane.getStyleClass().add("floating");

        ArrayList<ExternalWorkspace> workspaces = Lists.newArrayList(new Gson().fromJson(
            RemoteOpenslide.getWorkspace().orElseThrow(),
            ExternalWorkspace[].class
        ));

        workspaces.forEach(workspace -> {
            ListView<ExternalProject> listView = new ListView<>();
            listView.getStyleClass().clear();
            listView.prefWidthProperty().bind(tabPane.widthProperty());
            listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            listView.setCellFactory(f -> new ProjectListCell(this));
            listView.getItems().addAll(workspace.getProjects());

            Tab tab = new Tab(workspace.getName(), listView);
            tab.setClosable(RemoteOpenslide.hasWriteAccess());
            tab.setUserData(workspace.getId());

            if (RemoteOpenslide.hasWriteAccess()) {
                MenuItem miRename = new MenuItem("Rename");
                miRename.setOnAction(a -> renameWorkspace(workspace));

                tab.setContextMenu(new ContextMenu(miRename));
            }

            tabPane.getTabs().add(tab);
        });

        tabPane.getTabs().add(getCreateNewWorkspaceTab());
        tabPane.getTabs().forEach(tab -> tab.setOnCloseRequest(this::deleteWorkspace));

        Button btnCreate = new Button("Create project");
        btnCreate.setOnAction(action -> createNewProject());
        btnCreate.setDisable(!RemoteOpenslide.hasWriteAccess());

        Button btnLogout = new Button("Logout");
        btnLogout.setOnAction(action -> logout());

        Button btnClose = new Button("Close");
        btnClose.setOnAction(action -> closeDialog());

        ButtonBar.setButtonData(btnCreate, ButtonBar.ButtonData.LEFT);
        ButtonBar.setButtonData(btnLogout, ButtonBar.ButtonData.LEFT);
        ButtonBar.setButtonData(btnClose, ButtonBar.ButtonData.RIGHT);
        ButtonBar.setButtonUniformSize(btnLogout, false);
        ButtonBar.setButtonUniformSize(btnClose, false);

        ButtonBar buttonBar = new ButtonBar();
        buttonBar.getButtons().addAll(btnCreate, btnLogout, btnClose);

        BorderPane.setMargin(buttonBar, new Insets(10, 0, 0, 0));

        pane = new BorderPane();
        pane.setCenter(tabPane);
        pane.setBottom(buttonBar);
    }

    public void refreshDialog() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshDialog);
        }

        initializePane();
        dialog.getDialogPane().setContent(pane);
    }

    private void createNewProject() {
        String projectName = Dialogs.showInputDialog("Project name", "", "");
        String workspaceId = (String) tabPane.getSelectionModel().getSelectedItem().getUserData();
        int tabIndex = tabPane.getSelectionModel().getSelectedIndex();

        if (projectName == null) {
            return;
        }

        Result result = RemoteOpenslide.createNewProject(workspaceId, projectName);

        if (result == Result.OK) {
            refreshDialog();
            tabPane.getSelectionModel().select(tabIndex);
        } else {
            Dialogs.showErrorNotification(
            "Error when creating project",
            "See log for possibly more details."
            );
        }
    }

    private Tab getCreateNewWorkspaceTab() {
        Tab tab = new Tab("+");
        tab.setClosable(false);
        tab.setDisable(!RemoteOpenslide.hasWriteAccess());

        tab.setOnSelectionChanged(event -> {
            event.consume();
            tabPane.getSelectionModel().selectPrevious();

            String workspaceName = Dialogs.showInputDialog("Workspace name", "", "");

            if (workspaceName == null) {
                return;
            }

            Result result = RemoteOpenslide.createNewWorkspace(workspaceName);

            if (result == Result.OK) {
                refreshDialog();
                tabPane.getSelectionModel().select(tabPane.getTabs().size() - 2);
            } else {
                Dialogs.showErrorNotification(
                "Error when creating workspace",
                "See log for possibly more details."
                );
            }
        });

        return tab;
    }

    private void renameWorkspace(ExternalWorkspace workspace) {
        String newName = Dialogs.showInputDialog("Workspace name", "", workspace.getName());

        if (newName == null) {
            return;
        }

        Result result = RemoteOpenslide.renameWorkspace(workspace.getId(), newName);

        if (result == Result.OK) {
            refreshDialog();
        } else {
            Dialogs.showErrorNotification(
            "Error when renaming workspace",
            "See log for possibly more details.");
        }

    }

    private void deleteWorkspace(Event event) {
        boolean confirm = Dialogs.showConfirmDialog(
        "Delete workspace",
        "Are you sure you wish to delete this workspace? This is irreversible."
        );

        if (!confirm) {
            event.consume();
            return;
        }

        Tab tab = (Tab) event.getSource();
        Result result = RemoteOpenslide.deleteWorkspace((String) tab.getUserData());

        if (result == Result.FAIL) {
            event.consume();
            Dialogs.showErrorNotification(
            "Error when deleting workspace",
            "See log for possibly more details.."
            );
        }
    }

    public void logout() {
        RemoteOpenslide.logout();
        qupath.setProject(null);
        closeDialog();
    }

    public void closeDialog() {
        dialog.close();
    }
}
