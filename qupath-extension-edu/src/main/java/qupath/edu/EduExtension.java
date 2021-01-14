package qupath.edu;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.gui.*;
import qupath.edu.lib.RemoteOpenslide;
import qupath.edu.lib.RemoteProject;
import qupath.edu.tours.SlideTour;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.panes.PreferencePane;
import qupath.lib.gui.panes.ProjectBrowser;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewerPlus;
import qupath.lib.projects.Project;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static qupath.lib.gui.ActionTools.*;

/**
 * TODO:
 *  - ArrowTool and its respective ROI
 *  - Tons of minor changes
 *  - Figure out why "Save as" syncs changes but not "Save"
 *
 */
public class EduExtension implements QuPathExtension {

    private static final Logger logger = LoggerFactory.getLogger(EduExtension.class);

    private QuPathGUI qupath;

    private static final SimpleBooleanProperty noWriteAccess = new SimpleBooleanProperty(true);
    private static final Browser projectInformation = new Browser();
    private final TabPane tabbedPanel = new TabPane();

    @Override
    public void installExtension(QuPathGUI qupath) {
        this.qupath = qupath;

        if (QuPathGUI.getVersion().getMinor() < 2 || QuPathGUI.getVersion().getPatch() < 3) {
            Dialogs.showErrorMessage("QuPath outdated" , "Education Extension compatible only with QuPath 0.2.? or newer");
            return;
        }

        initializePreferences();

        if (!EduOptions.extensionEnabled().get()) {
            return;
        }

        initializeMenus();

        RemoteOpenslide.setHost(EduOptions.remoteOpenslideHost().get());
        EduOptions.remoteOpenslideHost().addListener(((obs, oldHost, newHost) -> RemoteOpenslide.setHost(newHost)));

        replaceAnnotationsPane();
        replaceViewer();
        replaceProjectBrowserButtons();
        registerSlideTours();

        if (EduOptions.showLoginDialogOnStartup().get()) {
            showWorkspaceOrLoginDialog();
        }

        onProjectChange();
        onSlideChange();

        disableButtons();
    }

    private void onSlideChange() {
        qupath.imageDataProperty().addListener((obs, o, n) -> {
            qupath.getAnalysisPanel().getSelectionModel().select(0);
            tabbedPanel.getSelectionModel().select(1);
        });
    }

    private void onProjectChange() {
        qupath.projectProperty().addListener((obs, oldProject, newProject) -> {
            if (newProject == null) {
                projectInformation.setContent("No project open");
            } else if (newProject instanceof RemoteProject) {
                RemoteProject project = (RemoteProject) newProject;

                Object informationText = project.retrieveMetadataValue(RemoteProject.PROJECT_INFORMATION);

                if (informationText == null) {
                    projectInformation.setContent("No information available for this project");
                } else {
                    projectInformation.setContent((String) informationText);
                }
            } else {
                projectInformation.setContent("No information available for this project");
            }
        });
    }

    @Override
    public String getName() {
        return "QuPath Education";
    }

    @Override
    public String getDescription() {
        return "Use QuPath for studying!";
    }

    private void initializeMenus() {
        qupath.getMenu("File>Project...", false).getItems().add(7,
            createMenuItem(createAction(ProjectDescriptionEditorCommand::openDescriptionEditor, "Edit project information"))
        );

        qupath.getMenu("Remote Slides", true).getItems().addAll(
            createMenuItem(createAction(ExternalSlideManager::showExternalSlideManager, "Manage slides")),
            createMenuItem(createAction(BackupManager::showBackupManagerPane, "Manage backups")),
            createMenuItem(createAction(RemoteUserManager::showManagementDialog, "Manage users")),
            createMenuItem(createAction(OrganizationManager::showOrganizationManager, "Manage organizations")),
            createMenuItem(createAction(EduExtension::showWorkspaceOrLoginDialog, "Show workspaces")),
            createMenuItem(createAction(this::checkSaveChanges, "Sync changes"))
        );
    }

    private void initializePreferences() {
        PreferencePane prefs = QuPathGUI.getInstance().getPreferencePane();

        prefs.addPropertyPreference(EduOptions.extensionEnabled(), Boolean.class,
            "Extension Enabled",
            "Edu",
            "Restart needed for changes to take effect");

        prefs.addPropertyPreference(EduOptions.remoteOpenslideHost(), String.class,
            "Edu Host",
            "Edu",
            "Server used with QuPath Education");

        prefs.addPropertyPreference(EduOptions.showLoginDialogOnStartup(), Boolean.class,
            "Show login dialog on startup",
            "Edu",
            "If enabled, opens the login dialog on startup.");
    }

    public static void setWriteAccess(boolean hasWriteAccess) {
        noWriteAccess.set(!hasWriteAccess);
    }

    public static void setProjectInformation(String information) {
        projectInformation.setContent(information);
    }

    private void replaceAnnotationsPane() {
        SimpleAnnotationPane simpleAnnotationPane = new SimpleAnnotationPane(qupath);

        qupath.getAnalysisPanel().getTabs().get(2).setContent(simpleAnnotationPane.getPane());
    }

    private void replaceViewer() {
        projectInformation.setTextHighlightable(false);
        projectInformation.setOnMouseClicked(event -> {
            Project<BufferedImage> project = qupath.getProject();

            if (event.getClickCount() > 1 && project instanceof RemoteProject) {
                String projectId = ((RemoteProject) project).getId();

                if (RemoteOpenslide.hasPermission(projectId)) {
                    ProjectDescriptionEditorCommand.openDescriptionEditor();
                }
            }
        });

        tabbedPanel.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabbedPanel.getTabs().add(new Tab("Project Information", projectInformation));
        tabbedPanel.getTabs().add(new Tab("Viewer", qupath.getMainViewerPane()));

        qupath.setMainViewerPane(tabbedPanel);

        // Refreshes the pane and makes the tabs visible
        qupath.setAnalysisPaneVisible(false);
        qupath.setAnalysisPaneVisible(true);
    }

    private void replaceProjectBrowserButtons() {
        ProjectBrowser projectBrowser = qupath.getProjectBrowser();

        Button btnOpen = ActionTools.createButton(
            ActionTools.createAction(EduExtension::showWorkspaceOrLoginDialog, "Open project"), false
        );

        Button btnCreate = ActionTools.createButton(
            ActionTools.createAction(EduExtension::showWorkspaceOrLoginDialog, "Create project"), false
        );

        Button btnAdd = ActionTools.createButton(
            ActionTools.createAction(ExternalSlideManager::showExternalSlideManager, "Add images"), false
        );

        btnAdd.disableProperty().bind(qupath.projectProperty().isNull());

        GridPane paneButtons = PaneTools.createColumnGridControls(btnCreate, btnOpen, btnAdd);
        paneButtons.prefWidthProperty().bind(projectBrowser.getPane().widthProperty());
        paneButtons.setPadding(new Insets(5, 5, 5, 5));
        ((BorderPane) projectBrowser.getPane()).setTop(paneButtons);
    }

    private void registerSlideTours() {
        QuPathViewerPlus viewer = qupath.getViewer();

        SlideTour slideTour = new SlideTour(viewer);
        Node slideTourNode = slideTour.getNode();

        viewer.getBasePane().getChildren().add(slideTour.getNode());

        AnchorPane.setTopAnchor(slideTourNode, 10d);
        AnchorPane.setLeftAnchor(slideTourNode, 10d);

        qupath.getViewer().addViewerListener(new SlideTour(qupath.getViewer()));
    }

    private void checkSaveChanges() {
        if (qupath.getProject() instanceof RemoteProject) {
            try {
                qupath.getProject().syncChanges();
            } catch (IOException e) {
                Dialogs.showErrorMessage("Sync error", "Error while syncing project");
            }
        }
    }

    /**
     * Disable various buttons based on users write access.
     *
     * TODO: Add support when user is not connected to any server
     */
    private void disableButtons() {
        qupath.lookupActionByText("Create project").disabledProperty().bind(noWriteAccess);
        qupath.lookupActionByText("Add images").disabledProperty().bind(noWriteAccess);
//        qupath.lookupActionByText("Edit project information").disabledProperty().bind(noWriteAccess);
        qupath.lookupActionByText("Edit project metadata").disabledProperty().bind(noWriteAccess);
        qupath.lookupActionByText("Check project URIs").disabledProperty().bind(noWriteAccess);
        qupath.lookupActionByText("Import images from v0.1.2").disabledProperty().bind(noWriteAccess);
    }

    public static void showWorkspaceOrLoginDialog() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(EduExtension::showWorkspaceOrLoginDialog);
            return;
        }

        try {
            if (RemoteOpenslide.getAuthType() == RemoteOpenslide.AuthType.UNAUTHENTICATED) {
                RemoteServerLoginManager.showLoginDialog();
            } else {
                WorkspaceManager.showWorkspace(QuPathGUI.getInstance());
            }
        } catch (Exception e) {
            Dialogs.showErrorMessage(
                "Error when connecting to server",
                "Check your internet connection and that you're connecting to the right server. See log for more details."
            );

            logger.error("Error when connecting to server", e);

            RemoteOpenslide.logout();
        }
    }
}
