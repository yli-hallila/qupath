package qupath.lib.gui.viewer.tools;

import javafx.scene.input.MouseEvent;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.TextROI;

import java.awt.geom.Point2D;

public class TextTool extends PointsTool {

    public TextTool(ModeWrapper modes) {
        super(modes);
    }

    @Override
    public void mousePressed(MouseEvent e) {
       // super.mousePressed(e);
        if (!e.isPrimaryButtonDown() || e.isConsumed()) {
            return;
        }

        // Get a server, if we can
        ImageServer<?> server = viewer.getServer();
        if (server == null)
            return;

        // Find out the coordinates in the image domain
        Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, false);
        double xx = p.getX();
        double yy = p.getY();

        // If we are outside the image, ignore click
        if (xx < 0 || yy < 0 || xx >= server.getWidth() || yy >= server.getHeight())
            return;

        RoiEditor editor = viewer.getROIEditor();
        double radius = PathPrefs.getDefaultPointRadius();

        // PathPoints is effectively ready from the start - don't need to finalize
        String text = DisplayHelpers.showInputDialog("Text", "", "");
        if (text == null || text.isEmpty())
            return;

        TextROI textPoint = new TextROI(xx, yy, text);
        viewer.createAnnotationObject(textPoint);
        editor.setROI(textPoint);
        editor.grabHandle(xx, yy, radius, e.isShiftDown());
        viewer.getSelectedObject().setName(text);

        viewer.repaint();
    }
}
