package qupath.edu;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.gui.*;
import qupath.edu.lib.RemoteOpenslide;
import qupath.edu.lib.RemoteProject;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.panes.PreferencePane;

import java.io.IOException;

import static qupath.lib.gui.ActionTools.*;

/**
 * TODO:
 *  - Automatically update host path when editing preferences
 *  - ArrowTool and its respective ROI
 *  - Saving & syncing to server
 *  - Annotation properties
 *  - Tons of minor changes
 *
 */
public class EduExtension implements QuPathExtension {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private QuPathGUI qupath;

    private static final SimpleBooleanProperty noWriteAccess = new SimpleBooleanProperty(true);

    @Override
    public void installExtension(QuPathGUI qupath) {
        this.qupath = qupath;

        if (QuPathGUI.getVersion().getMinor() < 2 || QuPathGUI.getVersion().getPatch() < 3) {
            Dialogs.showErrorMessage("QuPath outdated" , "Education Extension compatible only with QuPath 0.2.? or newer");
            return;
        }

        if (!EduOptions.extensionEnabled().get()) {
            return;
        }

        initializePreferences();
        initializeMenus();

        RemoteOpenslide.setHost(EduOptions.remoteOpenslideHost().get());

        disableButtons();

        replaceAnnotationsPane();
        replaceViewer();

        initializeTools(qupath);

        if (EduOptions.showLoginDialogOnStartup().get()) {
            showWorkspaceOrLoginDialog();
        }
    }

    private void initializeTools(QuPathGUI qupath) {
        // TODO: Add support for Arrow ROI
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
            createMenuItem(createAction(() -> WorkspaceManager.showWorkspace(qupath), "Show workspaces")),
            createMenuItem(createAction(this::checkSaveChanges, "Sync changes"))
        );
    }

    private void initializePreferences() {
        PreferencePane prefs = QuPathGUI.getInstance().getPreferencePane();

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

    private void replaceAnnotationsPane() {
        SimpleAnnotationPane simpleAnnotationPane = new SimpleAnnotationPane(qupath);

        qupath.getAnalysisPanel().getTabs().get(2).setContent(simpleAnnotationPane.getPane());
    }

    private void replaceViewer() {
        Browser projectInformation = new Browser();
        projectInformation.setTextHighlightable(false);
        projectInformation.setOnMouseClicked(event -> {
            if (event.getClickCount() > 1 && qupath.getProject() != null) {
                String projectId = qupath.getProject().getPath().getParent().getFileName().toString();

                if (RemoteOpenslide.hasPermission(projectId)) {
                    ProjectDescriptionEditorCommand.openDescriptionEditor();
                }
            }
        });

        // TODO: Hook this up to update the text when loading a project

        TabPane tabbedPanel = new TabPane();
        tabbedPanel.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabbedPanel.getTabs().add(new Tab("Project Information", projectInformation));
        tabbedPanel.getTabs().add(new Tab("Viewer", qupath.getMainViewerPane()));

        qupath.setMainViewerPane(tabbedPanel);

        // Refreshes the pane and makes the tabs visible
        qupath.setAnalysisPaneVisible(false);
        qupath.setAnalysisPaneVisible(true);
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

//            logger.error("Error when connecting to server", e);

            RemoteOpenslide.logout();
        }
    }
}
