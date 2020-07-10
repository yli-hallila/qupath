package qupath.lib.gui.commands;

import com.google.common.collect.Lists;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.RemoteOpenslide;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.panes.WorkspaceProjectListCell;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.io.ZipUtil;
import qupath.lib.objects.remoteopenslide.ExternalProject;
import qupath.lib.objects.remoteopenslide.ExternalSlide;
import qupath.lib.objects.remoteopenslide.ExternalWorkspace;
import qupath.lib.projects.ProjectIO;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static qupath.lib.common.RemoteOpenslide.*;

public class WorkspaceManager {

    private final static Logger logger = LoggerFactory.getLogger(WorkspaceManager.class);

    private SimpleBooleanProperty hasAccessProperty = new SimpleBooleanProperty(false);

    private static Dialog<ButtonType> dialog;
    private final QuPathGUI qupath;

    private BorderPane pane;
    private TabPane tabPane;
    private boolean filterWorkspaces = true;

    public static void showWorkspace(QuPathGUI qupath) {
        WorkspaceManager manager = new WorkspaceManager(qupath);

        dialog = Dialogs.builder()
                .title("Select project")
                .content(manager.getPane())
                .size(400, 500)
                .build();

        dialog.getDialogPane().getStylesheets().add(RemoteServerLoginManager.class.getClassLoader().getResource("css/remove_buttonbar.css").toExternalForm());
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
        List<ExternalWorkspace> workspaces = RemoteOpenslide.getAllWorkspaces();

        /* Filter Checkbox */

        CheckBox cbFilter = new CheckBox("Filter workspaces");
        cbFilter.setSelected(filterWorkspaces);
        cbFilter.setFont(new Font(10));
        cbFilter.setTooltip(new Tooltip("Filters out all workspaces which doesn't belong to your organization."));
        cbFilter.setOnAction(e -> {
            this.filterWorkspaces = !filterWorkspaces;
            refreshDialog();
        });

        /* Open by ID Button */

        Button btnOpenById = new Button("Open ID ...");
        btnOpenById.setFont(new Font(10));
        btnOpenById.setOnAction(this::openById);

        /* Tabs */

        tabPane = new TabPane();
        tabPane.setPrefWidth(360);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.SELECTED_TAB);
        tabPane.getStyleClass().add("floating");
        tabPane.getSelectionModel().selectedItemProperty().addListener(onTabChange());

        createTabs(tabPane, workspaces);
        selectPreviousWorkspace(tabPane);

        /* Buttons */

        Button btnCreate = new Button("Create project");
        btnCreate.setOnAction(action -> createNewProject());
        btnCreate.disableProperty().bind(hasAccessProperty.not());

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

        /* Pane */

        BorderPane.setMargin(buttonBar, new Insets(10, 0, 0, 0));

        GridPane top = new GridPane();
        ColumnConstraints constraint = new ColumnConstraints();
        constraint.setPercentWidth(50);

        top.getColumnConstraints().addAll(constraint, constraint);
        top.addRow(0, btnOpenById, cbFilter);

        GridPane.setHalignment(btnOpenById, HPos.LEFT);
        GridPane.setHalignment(cbFilter,    HPos.RIGHT);

        pane = new BorderPane();
        pane.setTop(top);
        pane.setCenter(tabPane);
        pane.setBottom(buttonBar);
    }

    private void createTabs(TabPane tabPane, List<ExternalWorkspace> workspaces) {
        workspaces.forEach(workspace -> {
            if (filterWorkspaces && !workspace.getOwner().equals(RemoteOpenslide.getOrganizationId())
                    && !workspace.getName().equals("My Projects")) {
                return;
            }

            ListView<ExternalProject> listView = new ListView<>();
            listView.getStyleClass().clear();
            listView.prefWidthProperty().bind(tabPane.widthProperty());
            listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            listView.setCellFactory(f -> new WorkspaceProjectListCell(this));
            listView.getItems().addAll(workspace.getProjects());

            Tab tab = new Tab(workspace.getName(), listView);
            tab.setClosable(false);

            if (!workspace.getName().equals("My Projects")) {
                var hasWriteAccess = RemoteOpenslide.isOwner(workspace.getOwner());

                if (hasWriteAccess) {
                    MenuItem miRename = new MenuItem("Rename");
                    miRename.setOnAction(a -> renameWorkspace(workspace));

                    MenuItem miDelete = new MenuItem("Delete");
                    miDelete.setOnAction(e -> deleteWorkspace(workspace));

                    tab.setContextMenu(new ContextMenu(miRename, miDelete));
                }
            }

            tab.setUserData(workspace.getId());

            tabPane.getTabs().add(tab);
        });

        if (RemoteOpenslide.hasRole("MANAGE_PROJECTS")) {
            Tab newWorkspaceTab = new Tab("+");
            newWorkspaceTab.setClosable(false);
            tabPane.getTabs().add(newWorkspaceTab);
        }
    }

    private void openById(ActionEvent actionEvent) {
        String id = Dialogs.showInputDialog(
        "Project or slide ID",
        "All IDs are similar to 6ce7a026-e023-47b5-9b2e-0fc5eb523e49",
        ""
        );

        if (id == null) {
            return;
        }

        List<ExternalSlide> slides = RemoteOpenslide.getSlidesV1();
        Optional<ExternalSlide> slide = slides.stream().filter(s -> s.getId().equalsIgnoreCase(id)).findFirst();
        if (slide.isPresent()) {
            ExternalSlideManager.openSlide(slide.get());
        } else {
            ExternalProject project = new ExternalProject();
            project.setId(id);
            project.setName(id);

            WorkspaceManager.loadProject(project);
        }

        closeDialog();
    }

    private void selectPreviousWorkspace(TabPane tabPane) {
        if (PathPrefs.previousWorkspace().get() != null) {
            tabPane.getTabs().forEach(workspace -> {
                String id = (String) workspace.getUserData();

                if (id != null && id.equals(PathPrefs.previousWorkspace().get())) {
                    tabPane.getSelectionModel().select(workspace);
                }
            });
        }
    }

    private ChangeListener<Tab> onTabChange() {
        return (ov, oldTab, newTab) -> {
            if (newTab.getText().equals("+")) {
                tabPane.getSelectionModel().select(oldTab);
                createNewWorkspace();
            } else if (newTab.getText().equals("My Projects")) {
                hasAccessProperty.set(true);
            } else {
                String workspaceId = (String) newTab.getUserData();
                hasAccessProperty.set(RemoteOpenslide.hasPermission(workspaceId));

                // onTabChange() is fired when the TabPane is drawn and the first tab is selected.
                // This check avoids setting the previous workspace to always the first workspace.
                if (oldTab != null) {
                    PathPrefs.previousWorkspace().set(workspaceId);
                }
            }
        };
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

        Result result = RemoteOpenslide.createProject(workspaceId, projectName);

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

    private void createNewWorkspace() {
        String workspaceName = Dialogs.showInputDialog("Workspace name", "", "");

        if (workspaceName == null) {
            return;
        }

        Result result = RemoteOpenslide.createNewWorkspace(workspaceName);

        if (result == Result.OK) {
            refreshDialog();
            tabPane.getSelectionModel().select(tabPane.getTabs().size() - 3);
        } else {
            Dialogs.showErrorNotification(
            "Error when creating workspace",
            "See log for possibly more details."
            );
        }
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

    private void deleteWorkspace(ExternalWorkspace workspace) {
        boolean confirm = Dialogs.showConfirmDialog(
        "Delete workspace",
        "Are you sure you wish to delete this workspace? This is irreversible."
        );

        if (!confirm) {
            return;
        }

        Result result = RemoteOpenslide.deleteWorkspace(workspace.getId());

        if (result == Result.FAIL) {
            Dialogs.showErrorNotification(
            "Error when deleting workspace",
            "See log for possibly more details.."
            );
        } else {
            refreshDialog();
        }
    }

    public void logout() {
        qupath.setProject(null);
        RemoteOpenslide.logout();
        closeDialog();

        qupath.showLoginDialog();
    }

    public void closeDialog() {
        dialog.close();
    }

    /**
     * Loads an external project based on ExternalProject. If manager is defined, it tries to close the workspace
     * manager dialog, but this method can be used without it to load projects manually.
     * @param extProject External project to load, requires at least UUID and name defined.
     * @param manager WorkspaceManager to close, can be null.
     */
    public static void loadProject(ExternalProject extProject, WorkspaceManager manager) {
        QuPathGUI qupath = QuPathGUI.getInstance();

        try {
            Path tempPath = Path.of(System.getProperty("java.io.tmpdir"), "qupath-ext-project");
            String tempPathStr = tempPath.toAbsolutePath().toString();
            Files.createDirectories(tempPath);

            try {
                MoreFiles.deleteDirectoryContents(Path.of(tempPathStr, extProject.getId()), RecursiveDeleteOption.ALLOW_INSECURE);
            } catch (NoSuchFileException ignored) {}

            Task<Boolean> worker = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    Optional<InputStream> projectInputStream = RemoteOpenslide.downloadProject(extProject.getIdWithTimestamp());

                    if (projectInputStream.isEmpty()) {
                        updateMessage("Error when downloading project, see log.");
                        return false;
                    }

                    updateMessage("Downloading project");
                    ZipUtil.unzip(projectInputStream.get(), tempPathStr);
                    updateMessage("Downloaded. Opening project...");

                    projectInputStream.get().close();

                    return true;
                }
            };

            ProgressDialog progress = new ProgressDialog(worker);
            progress.setTitle("Project import");
            qupath.submitShortTask(worker);
            progress.showAndWait();

            var success = worker.getValue();

            if (success) {
                File projectFile = new File(tempPathStr + "/" + extProject.getId() + "/project.qpproj");
                qupath.lib.projects.Project<BufferedImage> project = ProjectIO.loadProject(projectFile, BufferedImage.class);
                project.setName(extProject.getName());

                if (manager != null) {
                    manager.closeDialog();
                }

                qupath.getTabbedPanel().getSelectionModel().select(0);
                qupath.setProject(project);
            }
        } catch (IOException e) {
            Dialogs.showErrorMessage(
            "Error when trying to load project. ",
            "See log for more information."
            );

            logger.error("Error when loading external project", e);
        }
    }

    public static void loadProject(String id, String name) {
        ExternalProject project = new ExternalProject();
        project.setId(id);
        project.setName(name);

        loadProject(project);
    }

    public static void loadProject(ExternalProject project) {
        loadProject(project, null);
    }
}
