package qupath.lib.roi;

import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.interfaces.TranslatableROI;

public class ArrowROI extends LineROI {

    public ArrowROI(double x, double y, double x2, double y2, int c, int z, int t) {
        super(x, y, x2, y2, c, z, t);
    }

    @Override
    public String getROIType() {
        return "Arrow";
    }

    @Override
    public ROI duplicate() {
        return new ArrowROI(x, y, x2, y2, getC(), getZ(), getT());
    }

    @Override
    public TranslatableROI translate(double dx, double dy) {
        if (dx == 0 && dy == 0)
            return this;
        // Shift the bounds
        return new ArrowROI(x+dx, y+dy, x2+dx, y2+dy, getC(), getZ(), getT());
    }
}
