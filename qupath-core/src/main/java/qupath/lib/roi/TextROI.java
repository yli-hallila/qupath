package qupath.lib.roi;

import qupath.lib.geom.Point2;
import qupath.lib.roi.interfaces.ROI;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TextROI extends AbstractPathROI implements Serializable {

    private static final long serialVersionUID = 1L;

    protected Point2 point;
    private String text;

    public TextROI(double x, double y, String text) {
        addPoint(x, y);
        setText(text);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    protected void addPoint(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y))
            return;

        this.point = new Point2(x, y);
    }

    public Point2 getPoint() {
        return point;
    }

    @Override
    public double getCentroidX() {
        return point.getX();
    }

    @Override
    public double getCentroidY() {
        return point.getY();
    }

    @Override
    public double getBoundsX() {
        return point.getX(); // TODO
    }

    @Override
    public double getBoundsY() {
        return point.getY(); // TODO
    }

    @Override
    public double getBoundsWidth() {
        return 32; // TODO
    }

    @Override
    public double getBoundsHeight() {
        return 32; // TODO
    }

    @Override
    public List<Point2> getPolygonPoints() {
        List<Point2> points = new ArrayList<>();
        points.add(point);

        return points;
    }

    @Override
    public ROI duplicate() {
        return new TextROI(point.getX(), point.getY(), getText());
    }

    @Override
    public String getROIType() {
        return "Text";
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", getROIType(), getText());
    }


    private Object writeReplace() {
        return new TextROI.SerializationProxy(this);
    }

    private void readObject(ObjectInputStream stream) throws InvalidObjectException {
        throw new InvalidObjectException("Proxy required for reading");
    }

    private static class SerializationProxy implements Serializable {

        private static final long serialVersionUID = 1L;

        private final double x;
        private final double y;
        private final String name;
        private final String text;

        SerializationProxy(final TextROI roi) {
            this.x = roi.getPoint().getX();
            this.y = roi.getPoint().getY();
            this.name = null; // There used to be names... now there aren't
            this.text = roi.getText();
        }

        private Object readResolve() {
            return new TextROI(x, y, text);
        }
    }
}
