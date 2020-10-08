package qupath.edu.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.edu.lib.RemoteProject;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import java.io.IOException;

public class ProjectDescriptionEditorCommand {

    private static final QuPathGUI qupath = QuPathGUI.getInstance();
    private static final Logger logger = LoggerFactory.getLogger(ProjectDescriptionEditorCommand.class);

    public static void openDescriptionEditor() {
        if (qupath.getProject() instanceof RemoteProject) {
            RemoteProject project = (RemoteProject) qupath.getProject();

            String initialInput = (String) project.retrieveMetadataValue("Description");
//        Optional<String> result = Dialogs.showWysiwygEditor(initialInput);
            String result = Dialogs.showInputDialog("Description", "", initialInput);

            if (result != null) {
                project.storeMetadataValue("Description", result);

                try {
                    project.syncChanges();
                } catch (IOException e) {
                    logger.error("Error while syncing project changes.");
                }
            }
        }
    }
}