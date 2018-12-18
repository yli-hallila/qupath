package qupath.lib.gui.commands;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;

import java.util.Optional;

public class AnnotateImageCommand implements PathCommand {

    private final QuPathGUI qupath;

    public AnnotateImageCommand(QuPathGUI qupath) {
        super();
        this.qupath = qupath;
    }

    @Override
    public void run() {
        if (qupath.getImageData() == null || qupath.getProject() == null) // TODO: Support images without projects
            return;

        String input = GeneralTools.nullableString((String) qupath.getImageData().getProperty("Information"));
        Optional<String> result = DisplayHelpers.showEditor(input);

        result.ifPresent(s -> qupath.getImageData().setProperty("Information", s));
    }
}
