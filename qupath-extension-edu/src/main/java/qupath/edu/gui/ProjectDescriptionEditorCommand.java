package qupath.edu.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.projects.Project;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;

public class ProjectDescriptionEditorCommand {

    private static final QuPathGUI qupath = QuPathGUI.getInstance();
    private static final Logger logger = LoggerFactory.getLogger(ProjectDescriptionEditorCommand.class);

    public static void openDescriptionEditor() {
        String initialInput = (String) qupath.getProject().retrieveMetadataValue("Description");
//        Optional<String> result = Dialogs.showWysiwygEditor(initialInput);
        String result = Dialogs.showInputDialog("Description", "", initialInput);

        if (result != null) {
            Project<BufferedImage> project = qupath.getProject();
            project.storeMetadataValue("Description", result);

            try {
                project.syncChanges();
            } catch (IOException e) {
                logger.error("Error while syncing project changes.");
            }
        }
    }
}