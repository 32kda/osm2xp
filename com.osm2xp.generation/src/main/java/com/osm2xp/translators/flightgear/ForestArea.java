package com.osm2xp.translators.flightgear;

import java.util.List;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

/**
 * One OSM forest polygon prepared for vegetation scattering: its ring, the
 * FlightGear material to use, its bounding box and its area in square metres.
 * <p>
 * The ring vertices are copied into flat {@code double[]} arrays and containment
 * uses a plain even-odd ray cast, which is far cheaper than
 * {@link LinearRing2D#isInside(double, double)} (that one goes through
 * per-edge distance computations with {@link Math#hypot}).
 *
 * @author osm2xp
 */
public final class ForestArea {

	private final LinearRing2D ring;
	private final String material;
	private final double[] xs;
	private final double[] ys;
	private final double minLon;
	private final double minLat;
	private final double maxLon;
	private final double maxLat;
	private final double areaM2;

	public ForestArea(LinearRing2D ring, String material) {
		this.ring = ring;
		this.material = material;

		List<Point2D> vertices = new java.util.ArrayList<>(ring.vertices());
		int count = vertices.size();
		this.xs = new double[count];
		this.ys = new double[count];

		double minLon = Double.POSITIVE_INFINITY;
		double minLat = Double.POSITIVE_INFINITY;
		double maxLon = Double.NEGATIVE_INFINITY;
		double maxLat = Double.NEGATIVE_INFINITY;
		double sumLon = 0.0;
		double sumLat = 0.0;
		for (int i = 0; i < count; i++) {
			double x = vertices.get(i).x();
			double y = vertices.get(i).y();
			xs[i] = x;
			ys[i] = y;
			minLon = Math.min(minLon, x);
			minLat = Math.min(minLat, y);
			maxLon = Math.max(maxLon, x);
			maxLat = Math.max(maxLat, y);
			sumLon += x;
			sumLat += y;
		}
		this.minLon = minLon;
		this.minLat = minLat;
		this.maxLon = maxLon;
		this.maxLat = maxLat;

		double anchorLon = count > 0 ? sumLon / count : minLon;
		double anchorLat = count > 0 ? sumLat / count : minLat;
		this.areaM2 = computeAreaM2(xs, ys, anchorLon, anchorLat);
	}

	public LinearRing2D getRing() {
		return ring;
	}

	/** Number of ring vertices. */
	public int vertexCount() {
		return xs.length;
	}

	/** Longitude of vertex {@code i}. */
	public double x(int i) {
		return xs[i];
	}

	/** Latitude of vertex {@code i}. */
	public double y(int i) {
		return ys[i];
	}

	public String getMaterial() {
		return material;
	}

	public double getMinLon() {
		return minLon;
	}

	public double getMinLat() {
		return minLat;
	}

	public double getMaxLon() {
		return maxLon;
	}

	public double getMaxLat() {
		return maxLat;
	}

	/** Area in square metres (flat-earth approximation around the centroid). */
	public double getAreaM2() {
		return areaM2;
	}

	/** Even-odd point-in-polygon test (cheap: no per-edge distance math). */
	public boolean contains(double lon, double lat) {
		boolean inside = false;
		int n = xs.length;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double xi = xs[i];
			double yi = ys[i];
			double xj = xs[j];
			double yj = ys[j];
			if ((yi > lat) != (yj > lat)) {
				double xCross = xi + (lat - yi) / (yj - yi) * (xj - xi);
				if (lon < xCross) {
					inside = !inside;
				}
			}
		}
		return inside;
	}

	private static double computeAreaM2(double[] xs, double[] ys, double anchorLon, double anchorLat) {
		int n = xs.length;
		if (n < 3) {
			return 0.0;
		}
		double sum = 0.0;
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double[] a = FlightGearCoordinateUtils.toLocal(xs[i], ys[i], anchorLon, anchorLat);
			double[] b = FlightGearCoordinateUtils.toLocal(xs[j], ys[j], anchorLon, anchorLat);
			sum += b[0] * a[1] - a[0] * b[1];
		}
		return Math.abs(sum) / 2.0;
	}
}
