package qupath.edu.tours;

import qupath.lib.objects.PathObject;

import java.util.ArrayList;
import java.util.Collection;

public class SlideTourEntry {

    /**
     * Text to display for this entry.
     */
    private String text;

    /**
     * Center point of this entry.
     */
    private double x;
    private double y;

    /**
     * Magnification to view at this entry.
     */
    private double magnification;

    /**
     * Any annotations related to this entry.
     */
    private Collection<PathObject> annotations = new ArrayList<>();

    public SlideTourEntry(String text, double x, double y, double magnification, Collection<PathObject> annotations) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.magnification = magnification;
        this.annotations.addAll(annotations);
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setLocation(double x, double y, double magnification) {
        this.x = x;
        this.y = y;
        this.magnification = magnification;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getMagnification() {
        return magnification;
    }

    public void addAnnotation(PathObject annotation) {
        this.annotations.add(annotation);
    }

    public void setAnnotations(Collection<PathObject> annotations) {
        this.annotations = annotations;
    }

    public Collection<PathObject> getAnnotations() {
        return annotations;
    }
}
