package qupath.lib.geom;

import java.awt.*;
import java.awt.geom.*;

public class Arrow2D extends Line2D {

	private double x1, y1, x2, y2;

	public Arrow2D() {}

	public Arrow2D(double x1, double y1, double x2, double y2) {
		setLine(x1, y1, x2, y2);
	}

	@Override
	public double getX1() {
		return x1;
	}

	@Override
	public double getY1() {
		return y1;
	}

	@Override
	public Point2D getP1() {
		return new Point2D.Double(x1, y1);
	}

	@Override
	public double getX2() {
		return x2;
	}

	@Override
	public double getY2() {
		return y2;
	}

	@Override
	public Point2D getP2() {
		return new Point2D.Double(x2, y2);
	}

	@Override
	public void setLine(double x1, double y1, double x2, double y2) {
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
	}

	@Override
	public Rectangle2D getBounds2D() { // todo: calculate proper bounds for arrow
		double x, y, w, h;
		if (x1 < x2) {
			x = x1;
			w = x2 - x1;
		} else {
			x = x2;
			w = x1 - x2;
		}
		if (y1 < y2) {
			y = y1;
			h = y2 - y1;
		} else {
			y = y2;
			h = y1 - y2;
		}

		return new Rectangle2D.Float((float) x, (float) y, (float) w, (float) h);
	}

	public Shape createShape(double x1, double y1, double x2, double y2, double downsample) {
		double angle = Math.atan2(y2 - y1, x2 - x1);
		AffineTransform tx = new AffineTransform();
		tx.translate(x1, y1);
		tx.rotate(angle);

		downsample = Math.max(downsample, 1);
		downsample = Math.min(downsample, 20);

		double len = Math.hypot(x2 - x1, y2 - y1);
		Path2D shape = new Path2D.Double();
		shape.moveTo(0, 0);
		shape.lineTo(len, 0);
		shape.lineTo(len, -10 * downsample);
		shape.lineTo(len + (12 * downsample), 0);
		shape.lineTo(len, 10 * downsample);
		shape.lineTo(len, 0);

		return shape.createTransformedShape(tx);
	}
}