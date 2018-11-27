package qupath.lib.gui.commands;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AnnotateImageCommand implements PathCommand {

    private final static Logger logger = LoggerFactory.getLogger(AnnotateImageCommand.class);

    private final QuPathGUI qupath;

    private final String[] imageExtensions = { ".png", ".jpg", ".jpeg", ".bmp", ".gif" };

    public AnnotateImageCommand(QuPathGUI qupath) {
        super();
        this.qupath = qupath;
    }

    @Override
    public void run() {
        if (qupath.getImageData() == null)
            return;

        String dataFolderURI = qupath.getProjectDataDirectory(true).toURI().toString();
        String initialInput = GeneralTools.stringOrEmptyString((String) qupath.getImageData().getProperty("Information"));
        String result = null;

        try {
            String HTML = GeneralTools.readInputStreamAsString(QuPathGUI.class.getResourceAsStream("/html/annotation-editor.html"));

            List<String> images = new ArrayList<>();
            Files.list(qupath.getProjectDataDirectory(true).toPath()).forEach(item -> {
                String fileName = item.getName(item.getNameCount() - 1).toString().toLowerCase();

                if (GeneralTools.checkExtensions(fileName, imageExtensions)) {
                    images.add(fileName);
                }
            });

            HTML = HTML.replace("{{qupath-image-description}}", initialInput)
                       .replace("{{qupath-images-json}}", new Gson().toJson(images))
                       .replace("{{qupath-project-dir}}", dataFolderURI);
            result = DisplayHelpers.showHTML(HTML);
        } catch (IOException e) {
            logger.error("Error when opening annotation editor", e);
        }

        if (result != null) {
            result = result.replace(dataFolderURI, "qupath://");
            qupath.getImageData().setProperty("Information", result);
        }
    }
}
