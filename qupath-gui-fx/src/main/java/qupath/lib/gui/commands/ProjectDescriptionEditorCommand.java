package qupath.lib.gui.commands;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;

import java.io.IOException;
import java.util.Optional;

public class ProjectDescriptionEditorCommand implements PathCommand {

    private final QuPathGUI qupath;

    public ProjectDescriptionEditorCommand(QuPathGUI qupath) {
        super();
        this.qupath = qupath;
    }

    @Override
    public void run() {
        String initialInput = GeneralTools.nullableString(qupath.getProject().getDescription());
        Optional<String> result = DisplayHelpers.showEditor(initialInput);

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