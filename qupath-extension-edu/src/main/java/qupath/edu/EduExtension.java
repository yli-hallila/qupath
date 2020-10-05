package qupath.edu;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.annotations.ArrowTool;
import qupath.edu.exceptions.HttpException;
import qupath.edu.gui.*;
import qupath.edu.lib.FileUtil;
import qupath.edu.lib.RemoteOpenslide;
import qupath.edu.lib.ZipUtil;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.panes.PreferencePane;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.io.PathIO;
import qupath.lib.projects.DefaultProject;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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
        var tool = PathTools.createTool(new ArrowTool(), "Arrow Tool",
                IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.ZOOM_IN));

        Platform.runLater(() -> {
            qupath.installTool(tool, null);
            qupath.getToolAction(tool).setLongText(
                "Click and drag to draw with a wand tool. "
              + "Adjust brightness/contrast or wand preferences to customize the sensitivity and behavior."
            );
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
        qupath.getMenu("Remote Slides", true).getItems().addAll(
            createMenuItem(createAction(ExternalSlideManager::showExternalSlideManager, "Manage slides")),
            createMenuItem(createAction(BackupManager::showBackupManagerPane, "Manage backups")),
            createMenuItem(createAction(RemoteUserManager::showManagementDialog, "Manage users")),
            createMenuItem(createAction(() -> WorkspaceManager.showWorkspace(qupath), "Show workspaces")),
            createMenuItem(createAction(() -> checkSaveChanges(qupath.getImageData()), "Sync changes"))
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

    private boolean askedToLogin = false;

    private boolean checkSaveChanges(ImageData<BufferedImage> imageData) {
        if (!imageData.isChanged())
            return true;

        ProjectImageEntry<BufferedImage> entry = qupath.getProjectImageEntry(imageData);
        String name = entry == null ? ServerTools.getDisplayableImageName(imageData.getServer()) : entry.getImageName();
        var makeCopy = false;

        var hasWriteAccess = false;

        if (entry != null) {
            String projectId = qupath.getProject().getPath().getParent().getFileName().toString();

            try {
                hasWriteAccess = RemoteOpenslide.hasPermission(projectId);
            } catch (HttpException e) {
                logger.error("Error while syncing project.", e);

                return Dialogs.showYesNoDialog(
                "Sync error",
                "Error while syncing changes to server. Do you wish to discard your changes?"
                );
            }
        }

        if (RemoteOpenslide.hasRole("MANAGE_PERSONAL_PROJECTS") && !hasWriteAccess) {
            var response = Dialogs.showYesNoDialog("Save changes",
            "You've made changes to this project but you don't have the required permissions to save these changes." +
                "\n\n" +
                "Do you want to make a personal copy of this project which you can edit?");

            if (response) {
                makeCopy = true;
            } else {
                return true;
            }
        } else if (hasWriteAccess) {
            var response = Dialogs.showYesNoCancelDialog("Save changes", "Save changes to " + name + "?");
            if (response == Dialogs.DialogButton.CANCEL)
                return false;
            if (response == Dialogs.DialogButton.NO)
                return true;
        } else if (!askedToLogin) {
            var login = Dialogs.showYesNoDialog("Save changes",
                    "You've made changes to this project but you're not logged in." +
                            "\n\n" +
                            "Do you wish to login?");

            askedToLogin = true;

            if (login) {
                RemoteOpenslide.logout();
                showWorkspaceOrLoginDialog();
                return false;
            }

            return true;
        } else {
            return true;
        }

        try {
            if (entry == null) {
                String lastPath = imageData.getLastSavedPath();
                File lastFile = lastPath == null ? null : new File(lastPath);
                File dirBase = lastFile == null ? null : lastFile.getParentFile();
                String defaultName = lastFile == null ? null : lastFile.getName();
                File file = Dialogs.promptToSaveFile("Save data", dirBase, defaultName, "QuPath data files", PathPrefs.getSerializationExtension());
                if (file == null)
                    return false;
                PathIO.writeImageData(file, imageData);
            } else {
                entry.saveImageData(imageData);
                var project = qupath.getProject();
                if (project != null && !makeCopy)
                    project.syncChanges();
            }

            if (makeCopy) {
                var project = qupath.getProject();
                Optional<String> query = RemoteOpenslide.createPersonalProject(project.getName());

                try {
                    String projectId = query.orElseThrow(IOException::new);

                    Path dest = Path.of(System.getProperty("java.io.tmpdir"), "qupath-ext-project", projectId, File.separator);
                    Path src = project.getPath().getParent();

                    Files.createDirectory(dest.toAbsolutePath());

                    FileUtil.copy(src.toFile(), dest.toFile());

                    Path projectZipFile = Files.createTempFile("qupath-project-", ".zip");

                    ZipUtil.zip(dest, projectZipFile);

                    RemoteOpenslide.uploadProject(projectId, projectZipFile.toFile());
                    Files.delete(projectZipFile);

                    Platform.runLater(() -> {
//                        ProjectImageEntry<BufferedImage> previousImage = getProjectImageEntry(imageData);

                        WorkspaceManager.loadProject(projectId, "Copy of " + project.getName());

//                        Platform.runLater(() -> query.openImageEntry(previousImage));
                    });
                } catch (IOException e) {
                    logger.error("Error while creating personal project", e);
                }
            } else {
                logger.debug("Uploading project to server");

                DefaultProject project = (DefaultProject) qupath.getProject();

                File projectFolder = project.getFile().getParentFile();
                Path projectZipFile = Files.createTempFile("qupath-project-", ".zip");

                ZipUtil.zip(projectFolder.toPath(), projectZipFile);

                RemoteOpenslide.uploadProject(project.getBaseDirectory().getName(), projectZipFile.toFile());
                Files.delete(projectZipFile);

                logger.debug("Uploaded to server");
            }

            return true;
        } catch (IOException e) {
            Dialogs.showErrorMessage("Save ImageData", e);
            return false;
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
