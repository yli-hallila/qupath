package qupath.edu.gui;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import org.controlsfx.control.GridView;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.EduExtension;
import qupath.edu.EduOptions;
import qupath.edu.lib.RemoteOpenslide;
import qupath.edu.lib.RemoteProject;
import qupath.edu.models.ExternalProject;
import qupath.edu.models.ExternalSlide;
import qupath.edu.models.ExternalSubject;
import qupath.edu.models.ExternalWorkspace;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static qupath.edu.lib.RemoteOpenslide.Result;

public class WorkspaceManager {

    private final static Logger logger = LoggerFactory.getLogger(WorkspaceManager.class);

    private final SimpleBooleanProperty hasAccessProperty = new SimpleBooleanProperty(false);
    private final SimpleStringProperty  currentWorkspace  = new SimpleStringProperty();
    private final SimpleStringProperty  currentSubject    = new SimpleStringProperty();

    private static Dialog<ButtonType> dialog;
    private final QuPathGUI qupath;

    private BorderPane pane;
    private SplitPane splitPane;
    private boolean filterWorkspaces = true;

    private final Accordion accordion = new Accordion();
    private final GridView<ExternalProject> projectsGridView = new GridView<>();

    public static void showWorkspace(QuPathGUI qupath) {
        WorkspaceManager manager = new WorkspaceManager(qupath);

        dialog = Dialogs.builder()
                .title("Select project")
                .content(manager.getPane())
                .size(1000, 600)
                .build();

        dialog.getDialogPane().getStylesheets().add(RemoteServerLoginManager.class.getClassLoader().getResource("css/remove_buttonbar.css").toExternalForm());
        dialog.setResult(ButtonType.CLOSE);
        dialog.show();

        manager.getSplitPane().setDividerPositions(0.2);
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

    public Accordion getAccordion() {
        return accordion;
    }

    public SplitPane getSplitPane() {
        return splitPane;
    }

    private synchronized void initializePane() {
        pane = new BorderPane();

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

        /* Header */

        GridPane header = new GridPane();
        ColumnConstraints constraint = new ColumnConstraints();
        constraint.setPercentWidth(50);

        header.getColumnConstraints().addAll(constraint, constraint);
        header.addRow(0, btnOpenById, cbFilter);

        GridPane.setHalignment(btnOpenById, HPos.LEFT);
        GridPane.setHalignment(cbFilter,    HPos.RIGHT);

        /* Buttons */

        MenuItem miCreateWorkspace = new MenuItem("Workspace");
        miCreateWorkspace.setOnAction(action -> createNewWorkspace());

        MenuItem miCreateSubject = new MenuItem("Subject");
        miCreateSubject.setOnAction(action -> createNewSubject());

        MenuItem miCreateProject = new MenuItem("Project");
        miCreateProject.setOnAction(action -> createNewProject());

        MenuButton menuCreate = new MenuButton("Create ...");
        menuCreate.getItems().addAll(miCreateWorkspace, miCreateSubject, miCreateProject);
        menuCreate.disableProperty().bind(hasAccessProperty.not());

        Button btnLogout = new Button("Logout");
        btnLogout.setOnAction(action -> logout());

        Button btnClose = new Button("Close");
        btnClose.setOnAction(action -> closeDialog());

        /* Footer */

        ButtonBar.setButtonData(menuCreate, ButtonBar.ButtonData.LEFT);
        ButtonBar.setButtonData(btnLogout, ButtonBar.ButtonData.LEFT);
        ButtonBar.setButtonData(btnClose, ButtonBar.ButtonData.RIGHT);
        ButtonBar.setButtonUniformSize(btnLogout, false);
        ButtonBar.setButtonUniformSize(btnClose, false);

        ButtonBar footer = new ButtonBar();
        footer.getButtons().addAll(menuCreate, btnLogout, btnClose);

        /* Content */

        projectsGridView.setCellFactory(f -> new WorkspaceProjectListCell(this));
        projectsGridView.setCellWidth(350);
        projectsGridView.setItems(FXCollections.emptyObservableList());

        splitPane = new SplitPane();
        splitPane.setPrefWidth(1000);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(accordion, projectsGridView);

        createTabs(accordion, workspaces);

        accordion.expandedPaneProperty().addListener(onWorkspaceChange(workspaces));

        selectPreviousWorkspace(accordion);

        pane.setPadding(new Insets(0));
        pane.setPrefHeight(600);
        pane.setTop(header);
        pane.setCenter(splitPane);
        pane.setBottom(footer);

        BorderPane.setMargin(footer, new Insets(10));
        BorderPane.setMargin(header, new Insets(10));
    }

    private void createTabs(Accordion accordion, List<ExternalWorkspace> workspaces) {
        accordion.getPanes().clear();
        projectsGridView.getItems().clear();

        for (ExternalWorkspace workspace : workspaces) {
            if (filterWorkspaces && !workspace.getOwner().equals(RemoteOpenslide.getOrganizationId())
                    && !workspace.getName().equals("My Projects")) {
                return;
            }

            ListView<ExternalSubject> lvSubjects = new ListView<>();
            lvSubjects.setCellFactory(f -> new SubjectListCell());
            lvSubjects.getItems().addAll(workspace.getSubjects());

            lvSubjects.setOnMouseClicked(e -> {
                ExternalSubject selectedSubject = lvSubjects.getSelectionModel().getSelectedItem();

                if (selectedSubject == null) {
                    return;
                }

                if (e.getButton() == MouseButton.PRIMARY) {
                    currentSubject.set(selectedSubject.getId());
                    projectsGridView.setItems(FXCollections.observableArrayList(selectedSubject.getProjects()));
                }
            });

            TitledPane tpWorkspace = new TitledPane(workspace.getName(), lvSubjects);
            tpWorkspace.setUserData(workspace.getId());

            var hasWriteAccess = RemoteOpenslide.isOwner(workspace.getOwner());

            if (hasWriteAccess) {
                MenuItem miRename = new MenuItem("Rename workspace");
                miRename.setOnAction(a -> renameWorkspace(workspace));

                MenuItem miDelete = new MenuItem("Delete workspace");
                miDelete.setOnAction(e -> deleteWorkspace(workspace));

                // TODO: ContextMenu only on TitledPane header
                tpWorkspace.setContextMenu(new ContextMenu(miRename, miDelete));
            }

            accordion.getPanes().add(tpWorkspace);
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

        List<ExternalSlide> slides = RemoteOpenslide.getAllSlides();
        Optional<ExternalSlide> slide = slides.stream().filter(s -> s.getId().equalsIgnoreCase(id.strip())).findFirst();
        if (slide.isPresent()) {
            ExternalSlideManager.openSlide(slide.get());
        } else {
            ExternalProject project = new ExternalProject();
            project.setId(id.strip());
            project.setName(id.strip());

            WorkspaceManager.loadProject(project);
        }

        closeDialog();
    }

    private void selectPreviousWorkspace(Accordion accordion) {
        if (EduOptions.previousWorkspace().get() != null) {
            accordion.getPanes().forEach(workspace -> {
                String id = (String) workspace.getUserData();

                if (id != null && id.equals(EduOptions.previousWorkspace().get())) {
                    accordion.setExpandedPane(workspace);
                }
            });
        }
    }

    private ChangeListener<TitledPane> onWorkspaceChange(List<ExternalWorkspace> workspaces) {
        return (obs, oldWorkspace, newWorkspace) -> {
            if (newWorkspace == null) {
                return;
            }

            String workspaceId = (String) newWorkspace.getUserData();

            if (workspaceId != null) {
                currentWorkspace.set(workspaceId);
                hasAccessProperty.set(RemoteOpenslide.hasPermission(workspaceId));
            }
        };
    }

    public void refreshDialog() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshDialog);
        }

        String expandedWorkspaceId = (String) accordion.getExpandedPane().getUserData();

        List<ExternalWorkspace> workspaces = RemoteOpenslide.getAllWorkspaces();
        createTabs(accordion, workspaces);

        // Restore the previously open TitlePane

        for (TitledPane titledPane : accordion.getPanes()) {
            if (expandedWorkspaceId.equalsIgnoreCase((String) titledPane.getUserData())) {
                accordion.setExpandedPane(titledPane);

                for (ExternalWorkspace workspace : workspaces) {
                    if (workspace.getId().equalsIgnoreCase((String) titledPane.getUserData())) {
                        for (ExternalSubject subject : workspace.getSubjects()) {
                            if (subject.getId().equals(currentSubject.get())) {
                                projectsGridView.getItems().setAll(subject.getProjects());

                                break;
                            }
                        }

                        break;
                    }
                }
                // TODO: Refresh projects

                return;
            }
        }
    }

    private void createNewProject() {
        String projectName = Dialogs.showInputDialog("Project name", "", "");

        if (projectName == null) {
            return;
        }

        Result result = RemoteOpenslide.createProject(currentSubject.get(), projectName);

        if (result == Result.OK) {
            refreshDialog();
        } else {
            Dialogs.showErrorNotification(
            "Error when creating project",
            "See log for possibly more details."
            );
        }
    }

    public void createNewSubject() {
        String subjectName = Dialogs.showInputDialog("Subject name", "", "");

        if (subjectName == null) {
            return;
        }

        Result result = RemoteOpenslide.createSubject(currentWorkspace.get(), subjectName);

        if (result == Result.OK) {
            refreshDialog();
        } else {
            Dialogs.showErrorNotification(
                "Error when creating subject",
                "See log for possibly more details."
            );
        }
    }

    private void createNewWorkspace() {
        String workspaceName = Dialogs.showInputDialog("Workspace name", "", "");

        if (workspaceName == null) {
            return;
        }

        Result result = RemoteOpenslide.createWorkspace(workspaceName);

        if (result == Result.OK) {
            refreshDialog();
            accordion.setExpandedPane(accordion.getPanes().get(accordion.getPanes().size() - 1));
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
                "See log for possibly more details."
            );
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

    private void renameSubject(ExternalSubject subject) {
        String newName = Dialogs.showInputDialog("Subject name", "", subject.getName());

        if (newName == null) {
            return;
        }

        Result result = RemoteOpenslide.renameSubject(subject.getId(), newName);

        if (result == Result.OK) {
            refreshDialog();
        } else {
            Dialogs.showErrorNotification(
                "Error when renaming subject",
                "See log for possibly more details."
            );
        }
    }

    private void deleteSubject(ExternalSubject subject) {
        boolean confirm = Dialogs.showConfirmDialog(
            "Delete subject",
            "Are you sure you wish to delete this subject?" +
            "\n\n" +
            "This will also delete all the projects belonging to this subject.This is irreversible."
        );

        if (!confirm) {
            return;
        }

        Result result = RemoteOpenslide.deleteSubject(subject.getId());

        if (result == Result.FAIL) {
            Dialogs.showErrorNotification(
                "Error when deleting subject",
                "See log for possibly more details.."
            );
        } else {
            refreshDialog();
        }
    }

    public void logout() {
        qupath.getViewer().setImageData(null);
        qupath.setProject(null);
        RemoteOpenslide.logout();
        closeDialog();

        EduExtension.showWorkspaceOrLoginDialog();
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
            // Confirm that the ID is a valid UUID
            UUID.fromString(extProject.getId());
        } catch (IllegalArgumentException e) {
            Dialogs.showErrorNotification("Error", "Provided ID was formatted incorrectly.");
            EduExtension.showWorkspaceOrLoginDialog();
            return;
        }

        try {
            Task<Optional<String>> worker = new Task<>() {
                @Override
                protected Optional<String> call() {
                    updateMessage("Downloading project");
                    Optional<String> projectData = RemoteOpenslide.downloadProject(extProject.getIdWithTimestamp());

                    if (projectData.isEmpty()) {
                        updateMessage("Error when downloading project, see log.");
                        return Optional.empty();
                    }

                    updateMessage("Downloaded. Opening project...");

                    return projectData;
                }
            };

            ProgressDialog progress = new ProgressDialog(worker);
            progress.setTitle("Project import");
            qupath.submitShortTask(worker);
            progress.showAndWait();

            var projectData = worker.getValue();

            if (projectData.isPresent()) {
                RemoteProject project = new RemoteProject(projectData.get());
                project.setName(extProject.getName());

                if (manager != null) {
                    manager.closeDialog();
                }

                Platform.runLater(() -> {
// TODO:            qupath.getTabbedPanel().getSelectionModel().select(0);
                    qupath.setProject(project);
                });
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
