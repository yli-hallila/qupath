package qupath.lib.gui.commands;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import java.util.Optional;

public class AnnotateImageCommand {

    private static final QuPathGUI qupath = QuPathGUI.getInstance();

    public static void openDialog() {
        if (qupath.getImageData() == null || qupath.getProject() == null) // TODO: Support images without projects
            return;

        String input = GeneralTools.nullableString((String) qupath.getImageData().getProperty("Information"));
        Optional<String> result = Dialogs.showEditor(input);

        result.ifPresent(s -> qupath.getImageData().setProperty("Information", s));
    }
}
