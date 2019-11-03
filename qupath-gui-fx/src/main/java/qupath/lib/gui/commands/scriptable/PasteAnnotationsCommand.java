package qupath.lib.gui.commands.scriptable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.ViewerManager;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.objects.PathObject;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;

public class PasteAnnotationsCommand implements PathCommand {

    private final static Logger logger = LoggerFactory.getLogger(PasteAnnotationsCommand.class);

    final private ViewerManager<?> qupath;

    public PasteAnnotationsCommand(final ViewerManager<?> qupath) {
        super();
        this.qupath = qupath;
    }

    @Override
    public void run() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        try {
            /*DataFlavor flavor = new DataFlavor("application/x-java-serialized-object;class=java.util.ArrayList");
            if (clipboard.isDataFlavorAvailable(flavor)) {
                ArrayList<PathObject> annotations = (ArrayList<PathObject>) clipboard.getData(flavor);

                for (PathObject annotation : annotations) {
                    qupath.getViewer().createAnnotationObject(annotation.getROI());
                    qupath.getViewer().getSelectedObject().setAnswer(annotation.getAnswer());
                    qupath.getViewer().getSelectedObject().setName(annotation.getName());
                    qupath.getViewer().getSelectedObject().setColorRGB(annotation.getColorRGB());
                }
            }*/
        } catch (Exception e) {
            logger.info("Error while pasting annotations", e);
        }
    }
}
