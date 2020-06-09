package qupath.lib.gui.commands;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;

import java.io.IOException;
import java.util.Optional;

public class ProjectDescriptionEditorCommand {

    private static final QuPathGUI qupath = QuPathGUI.getInstance();

    public static void openDescriptionEditor() {
        String initialInput = GeneralTools.nullableString(qupath.getProject().getDescription());
        Optional<String> result = Dialogs.showEditor(initialInput);

        if (result.isPresent()) {
            Project project = qupath.getProject();
            project.setDescription(result.get());

            try {
                project.syncChanges();
            } catch (IOException e) {
                // todo: logger
            }
        }
    }
}