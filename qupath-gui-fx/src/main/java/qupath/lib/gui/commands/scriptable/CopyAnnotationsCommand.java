package qupath.lib.gui.commands.scriptable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.ViewerManager;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.SerialTransferable;
import qupath.lib.objects.PathObject;

import java.awt.*;
import java.awt.datatransfer.*;
import java.util.ArrayList;

public class CopyAnnotationsCommand implements PathCommand {

    private final static Logger logger = LoggerFactory.getLogger(CopyAnnotationsCommand.class);

    final private ViewerManager<?> qupath;

    public CopyAnnotationsCommand(final ViewerManager<?> qupath) {
        super();
        this.qupath = qupath;
    }

    @Override
    public void run() {
        ArrayList<PathObject> annotations = new ArrayList<>(qupath.getViewer().getAllSelectedObjects());

        if (annotations.size() > 0) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            SerialTransferable selection = new SerialTransferable(annotations);
            clipboard.setContents(selection, null);
        }
    }
}
