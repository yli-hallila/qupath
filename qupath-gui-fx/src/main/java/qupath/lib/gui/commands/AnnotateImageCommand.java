package qupath.lib.gui.commands;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.panels.PathAnnotationPanel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AnnotateImageCommand implements PathCommand {

    static Logger logger = LoggerFactory.getLogger(AnnotateImageCommand.class);

    final private QuPathGUI qupath;

    private String[] extensions = { ".png", ".jpg", ".jpeg", ".bmp", ".gif"};

    public AnnotateImageCommand(QuPathGUI qupath) {
        super();
        this.qupath = qupath;
    }

    @Override
    public void run() {
        if (qupath.getImageData() == null)
            return;

        String initialInput = (String) qupath.getImageData().getProperty("Information");
        initialInput = initialInput == null ? "" : initialInput; // TODO: make method
        String result = null;

        try {
            String HTML = GeneralTools.readInputStreamAsString(QuPathGUI.class.getResourceAsStream("/html/annotation-editor.html"));

            List<String> images = new ArrayList<>();
            Files.list(qupath.getProjectDataDirectory(true).toPath()).forEach(item -> {
                String fileName = item.getName(item.getNameCount() - 1).toString().toLowerCase();

                if (endsWith(fileName, extensions)) {
                    images.add(fileName);
                }
            });

            Gson gson = new Gson();

            HTML = HTML.replace("{{qupath-image-description}}", initialInput);
            HTML = HTML.replace("{{qupath-images-json}}", gson.toJson(images));
            HTML = HTML.replace("{{qupath-project-dir}}", qupath.getProjectDataDirectory(false).toURI().toString());
            result = DisplayHelpers.showHTML(HTML);
        } catch (IOException e) {
            logger.error("Error when opening annotation editor", e);
        }

        if (result != null) {
            result = result.replace(qupath.getProjectDataDirectory(false).toURI().toString(), "qupath://");
            qupath.getImageData().setProperty("Information", result);
        }
    }

    private boolean endsWith(String fileName, String[] extensions) {
        boolean endsWith = false;

        for (String extension : extensions) {
            if (fileName.endsWith(extension)) {
                endsWith = true;
                break;
            }
        }

        return endsWith;
    }
}
