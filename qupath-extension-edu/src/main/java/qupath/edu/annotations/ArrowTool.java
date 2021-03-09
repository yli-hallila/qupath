package qupath.edu.annotations;

import javafx.scene.input.MouseEvent;
import qupath.lib.gui.viewer.tools.LineTool;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

public class ArrowTool extends LineTool {

    @Override
    protected ROI createNewROI(MouseEvent e, double x, double y, ImagePlane plane) {
        throw new UnsupportedOperationException();
    }
}
