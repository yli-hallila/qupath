package qupath.edu.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.EduExtension;
import qupath.edu.lib.RemoteProject;
import qupath.lib.gui.QuPathGUI;

import java.io.IOException;
import java.util.Optional;

public class ProjectDescriptionEditorCommand {

    private static final QuPathGUI qupath = QuPathGUI.getInstance();
    private static final Logger logger = LoggerFactory.getLogger(ProjectDescriptionEditorCommand.class);

    public static void openDescriptionEditor() {
        if (qupath.getProject() instanceof RemoteProject) {
            RemoteProject project = (RemoteProject) qupath.getProject();

            String initialInput = (String) project.retrieveMetadataValue(RemoteProject.PROJECT_INFORMATION);
            Optional<String> result = CustomDialogs.showWysiwygEditor(initialInput);

            if (result.isPresent()) {
                project.storeMetadataValue(RemoteProject.PROJECT_INFORMATION, result.get());
                EduExtension.setProjectInformation(result.get());

                try {
                    project.syncChanges();
                } catch (IOException e) {
                    logger.error("Error while syncing project changes.");
                }
            }
        }
    }
}