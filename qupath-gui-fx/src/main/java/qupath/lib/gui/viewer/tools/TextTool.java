package qupath.lib.gui.viewer.tools;

import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.RoiEditor;
import qupath.lib.roi.TextROI;
import qupath.lib.roi.interfaces.ROI;

import java.awt.geom.Point2D;
import java.util.Collections;

public class TextTool extends AbstractPathTool {

    public TextTool(ModeWrapper modes) {
        super(modes);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        super.mouseDragged(e);

        if (!e.isPrimaryButtonDown()) {
            return;
        }

        ROI currentROI = viewer.getCurrentROI() instanceof ROI ? (ROI) viewer.getCurrentROI() : null;
        RoiEditor editor = viewer.getROIEditor();

        if (currentROI != null && editor.getROI() == currentROI && editor.hasActiveHandle()) {
            PathObject pathObject = viewer.getSelectedObject();
            Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, true);
            ROI roiUpdated = editor.setActiveHandlePosition(p.getX(), p.getY(), 0.25, e.isShiftDown());
            if (roiUpdated != currentROI) {
                ((PathROIObject)pathObject).setROI(roiUpdated);
                viewer.repaint();
            }

            viewer.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(pathObject), true);
//			editor.setActiveHandlePosition(x, y, minDisplacement, shiftDown)
//			currentROI.updateAdjustment(p.getX(), p.getY(), e.isShiftDown());
            viewer.repaint();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        super.mousePressed(e);
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

        // PathPoints is effectively ready from the start - don't need to finalize
        String text = DisplayHelpers.showInputDialog("Text", "", "");
        if (text == null || text.isEmpty())
            return;

        TextROI textPoint = new TextROI(xx, yy, text);
        viewer.createAnnotationObject(textPoint);
        editor.setROI(textPoint);
        editor.grabHandle(xx, yy, PathPrefs.getDefaultPointRadius(), e.isShiftDown());
        //viewer.getSelectedObject().setName(text);

        viewer.repaint();

        if (PathPrefs.getReturnToMoveMode())
            modes.setMode(QuPathGUI.Modes.MOVE);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        super.mouseMoved(e);
        ensureCursorType(Cursor.CROSSHAIR);
    }
}
