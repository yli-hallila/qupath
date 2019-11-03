package qupath.lib.roi;

import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

import java.awt.*;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;

public class ArrowROI extends LineROI implements Serializable {

    public ArrowROI(double x, double y, double x2, double y2, ImagePlane plane) {
        super(x, y, x2, y2, plane);
    }

    @Override
    public String getRoiName() {
        return "Arrow";
    }

    @Override
    public ROI duplicate() {
        return new ArrowROI(x, y, x2, y2, getImagePlane());
    }

    @Override
    public ROI translate(double dx, double dy) {
        if (dx == 0 && dy == 0)
            return this;
        // Shift the bounds
        return new ArrowROI(x+dx, y+dy, x2+dx, y2+dy, getImagePlane());
    }

    private Object writeReplace() {
        return new ArrowROI.SerializationProxy(this);
    }

    private void readObject(ObjectInputStream stream) throws InvalidObjectException {
        throw new InvalidObjectException("Proxy required for reading");
    }

    private static class SerializationProxy implements Serializable {

        private static final long serialVersionUID = 1L;

        private final double x, x2, y, y2;
        private final String name;
        private final int c, z, t;

        SerializationProxy(final ArrowROI roi) {
            this.x =  roi.x;
            this.x2 =  roi.x2;
            this.y =  roi.y;
            this.y2 =  roi.y2;
            this.name = null; // There used to be names... now there aren't
            this.c = roi.c;
            this.z = roi.z;
            this.t = roi.t;
        }

        private Object readResolve() {
            return new ArrowROI(x, y, x2, y2, ImagePlane.getPlaneWithChannel(c, z, t));
        }
    }
}
