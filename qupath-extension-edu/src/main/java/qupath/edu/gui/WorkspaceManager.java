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

    private final Accordion accordion = new Accordion();
    private final GridView<ExternalProject> gvProjects = new GridView<>();

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

        // GridView adds a fixed 18px for the scrollbar and each cell has padding of 10px left and right.
        manager.getGridViewProjects().cellWidthProperty().bind(manager.getGridViewProjects().widthProperty().subtract(18).divide(2).subtract(20));

        // Disable SplitPane divider
        manager.getSplitPane().lookupAll(".split-pane-divider").stream().forEach(div -> div.setMouseTransparent(true));
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

    public GridView<ExternalProject> getGridViewProjects() {
        return gvProjects;
    }

    private synchronized void initializePane() {
        pane = new BorderPane();

        // TODO: Make this async or make a loading dialog

        List<ExternalWorkspace> workspaces = RemoteOpenslide.getAllWorkspaces();

        /* Change organization Button */

        Button btnChangeOrganization = new Button("Change organization");
        btnChangeOrganization.setFont(new Font(10));
        btnChangeOrganization.setOnAction(a -> changeOrganization());

        /* Open by ID Button */

        Button btnOpenById = new Button("Open ID ...");
        btnOpenById.setFont(new Font(10));
        btnOpenById.setOnAction(this::openById);

        /* Header */

        GridPane header = new GridPane();
        ColumnConstraints constraint = new ColumnConstraints();
        constraint.setPercentWidth(50);

        header.getColumnConstraints().addAll(constraint, constraint);
        header.addRow(0, btnOpenById, btnChangeOrganization);

        GridPane.setHalignment(btnOpenById, HPos.LEFT);
        GridPane.setHalignment(btnChangeOrganization, HPos.RIGHT);

        /* Buttons */

        // TODO: Fix disabled buttons when no workspaces available
        //       Fix not taking MANAGE_PROJECTS into account

        MenuItem miCreateWorkspace = new MenuItem("Workspace");
        miCreateWorkspace.setOnAction(action -> createNewWorkspace());

        MenuItem miCreateSubject = new MenuItem("Subject");
        miCreateSubject.setOnAction(action -> createNewSubject());
        miCreateSubject.disableProperty().bind(hasAccessProperty.not());

        MenuItem miCreateProject = new MenuItem("Project");
        miCreateProject.setOnAction(action -> createNewProject());
        miCreateProject.disableProperty().bind(hasAccessProperty.not());

        MenuButton menuCreate = new MenuButton("Create ...");
        menuCreate.getItems().addAll(miCreateWorkspace, miCreateSubject, miCreateProject);

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

        gvProjects.setCellFactory(f -> new WorkspaceProjectListCell(this));
        gvProjects.setPadding(new Insets(0));
        gvProjects.setHorizontalCellSpacing(10);
        gvProjects.setCellWidth(360); // TODO: Bind to parent width
        gvProjects.setItems(FXCollections.emptyObservableList());

        splitPane = new SplitPane();
        splitPane.setPrefWidth(1000);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(accordion, gvProjects);

        createAccordion(accordion, workspaces);

        accordion.expandedPaneProperty().addListener(onWorkspaceChange());

        selectPreviousWorkspace(accordion);

        pane.setPadding(new Insets(0));
        pane.setPrefHeight(600);
        pane.setTop(header);
        pane.setCenter(splitPane);
        pane.setBottom(footer);

        BorderPane.setMargin(footer, new Insets(10));
        BorderPane.setMargin(header, new Insets(10));
    }

    private void createAccordion(Accordion accordion, List<ExternalWorkspace> workspaces) {
        accordion.getPanes().clear();
        gvProjects.getItems().clear();

        for (ExternalWorkspace workspace : workspaces) {
            if (!workspace.getOwnerId().equals(RemoteOpenslide.getOrganizationId())) {
                return;
            }

            var hasWriteAccess = RemoteOpenslide.isOwner(workspace.getOwnerId());

            ListView<ExternalSubject> lvSubjects = new ListView<>();
            lvSubjects.setCellFactory(f -> new SubjectListCell(this, hasWriteAccess));
            lvSubjects.getItems().addAll(workspace.getSubjects());

            lvSubjects.setOnMouseClicked(e -> {
                ExternalSubject selectedSubject = lvSubjects.getSelectionModel().getSelectedItem();

                if (selectedSubject == null) {
                    return;
                }

                if (e.getButton() == MouseButton.PRIMARY) {
                    currentSubject.set(selectedSubject.getId());
                    gvProjects.setItems(FXCollections.observableArrayList(selectedSubject.getProjects()));
                }
            });

            TitledPane tpWorkspace = new TitledPane(workspace.getName(), lvSubjects);
            tpWorkspace.setUserData(workspace.getId());

            if (hasWriteAccess) {
                MenuItem miRename = new MenuItem("Rename workspace");
                miRename.setOnAction(a -> renameWorkspace(workspace));

                MenuItem miDelete = new MenuItem("Delete workspace");
                miDelete.setOnAction(e -> deleteWorkspace(workspace));

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

    private void changeOrganization() {
        if (RemoteOpenslide.getAuthType().shouldPrompt()) {
            var confirm = Dialogs.showConfirmDialog(
                "Are you sure?",
                "Changing organizations will log you out."
            );

            if (confirm) {
                logout();
            }
        } else {
            logout();
        }
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

    private ChangeListener<TitledPane> onWorkspaceChange() {
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

        String previousWorkspaceId;

        if (accordion.getExpandedPane() == null) {
            previousWorkspaceId = null;
        } else {
            previousWorkspaceId = (String) accordion.getExpandedPane().getUserData();
        }

        List<ExternalWorkspace> workspaces = RemoteOpenslide.getAllWorkspaces();
        createAccordion(accordion, workspaces);

        // Restore the previously open TitlePane

        if (previousWorkspaceId == null) {
            return;
        }

        accordion.getPanes().stream()
                .filter(pane -> previousWorkspaceId.equals(pane.getUserData()))
                .findFirst()
                .ifPresent(accordion::setExpandedPane);

        workspaces.stream()
                .filter(workspace -> workspace.getId().equals(previousWorkspaceId))
                .findFirst()
                .flatMap(workspace -> workspace.findSubject(currentSubject.get()))
                .ifPresent(subject -> gvProjects.getItems().setAll(subject.getProjects()));
    }

    private void createNewProject() {
        String name = Dialogs.showInputDialog("Project name", "", "");

        if (name == null) {
            return;
        }

        Result result = RemoteOpenslide.createProject(currentSubject.get(), name);

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully created project.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when creating project. See log for possibly more details.");
        }
    }

    public void createNewSubject() {
        String name = Dialogs.showInputDialog("Subject name", "", "");

        if (name == null) {
            return;
        }

        Result result = RemoteOpenslide.createSubject(currentWorkspace.get(), name);

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully created subject.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when creating subject. See log for possibly more details.");
        }
    }

    private void createNewWorkspace() {
        String name = Dialogs.showInputDialog("Workspace name", "", "");

        if (name == null) {
            return;
        }

        Result result = RemoteOpenslide.createWorkspace(name);

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully created workspace");
        } else {
            Dialogs.showErrorNotification("Error", "Error when creating workspace. See log for possibly more details.");
        }
    }

    private void renameWorkspace(ExternalWorkspace workspace) {
        String name = Dialogs.showInputDialog("Workspace name", "", workspace.getName());

        if (name == null) {
            return;
        }

        Result result = RemoteOpenslide.renameWorkspace(workspace.getId(), name);

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully renamed workspace.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when renaming workspace. See log for possibly more details.");
        }

    }

    private void deleteWorkspace(ExternalWorkspace workspace) {
        boolean confirm = Dialogs.showConfirmDialog(
            "Are you sure?",
            "Do you wish to delete this workspace?" +
            "\n\n" +
            "This will also delete all the subjects and their projects that belong to this workspace. This action is irreversible."
        );

        if (!confirm) {
            return;
        }

        Result result = RemoteOpenslide.deleteWorkspace(workspace.getId());

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully deleted workspace.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when deleting workspace. See log for possibly more details.");
        }
    }

    public void renameSubject(ExternalSubject subject) {
        String name = Dialogs.showInputDialog("Subject name", "", subject.getName());

        if (name == null) {
            return;
        }

        Result result = RemoteOpenslide.renameSubject(subject.getId(), name);

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully renamed subject.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when renaming subject. See log for possibly more details.");
        }
    }

    public void deleteSubject(ExternalSubject subject) {
        boolean confirm = Dialogs.showConfirmDialog(
            "Are you sure?",
            "Do you wish to delete this subject?" +
            "\n\n" +
            "This will also delete all the projects belonging to this subject. This action is irreversible."
        );

        if (!confirm) {
            return;
        }

        Result result = RemoteOpenslide.deleteSubject(subject.getId());

        if (result == Result.OK) {
            refreshDialog();
            Dialogs.showInfoNotification("Success", "Successfully deleted subject.");
        } else {
            Dialogs.showErrorNotification("Error", "Error when deleting subject. See log for possibly more details.");
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
