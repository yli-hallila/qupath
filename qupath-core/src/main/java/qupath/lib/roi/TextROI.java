package qupath.lib.roi;

import qupath.lib.geom.Point2;

public class TextROI extends PointsROI {

    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public TextROI(double x, double y, String text) {
        super(x, y, -1, 0, 0);
        this.text = text;
    }

    @Override
    protected void addPoint(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y))
            return;

        if (points.size() == 1)
            return;

        points.add(new Point2(x, y));
    }

    @Override
    public String getROIType() {
        return "Text";
    }

    @Override
    public String toString() {
        return String.format("%s", getROIType());
    }
}
