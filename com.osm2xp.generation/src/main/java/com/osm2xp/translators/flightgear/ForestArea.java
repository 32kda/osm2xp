package com.osm2xp.translators.flightgear;

import java.util.ArrayList;
import java.util.List;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

/**
 * One OSM forest polygon prepared for vegetation scattering: its outer ring, any
 * hole (inner) rings, the FlightGear material to use, its bounding box and its
 * area in square metres.
 * <p>
 * Ring vertices are copied into flat {@code double[]} arrays and containment
 * uses a plain even-odd ray cast over the outer ring plus all holes (so holes
 * are correctly excluded), which is far cheaper than
 * {@link LinearRing2D#isInside(double, double)}.
 *
 * @author osm2xp
 */
public final class ForestArea {

	private final String material;
	private final double[] xs;
	private final double[] ys;
	private final List<double[]> holeXs;
	private final List<double[]> holeYs;
	private final double minLon;
	private final double minLat;
	private final double maxLon;
	private final double maxLat;
	private final double areaM2;

	public ForestArea(LinearRing2D ring, String material) {
		this(ring, null, material);
	}

	public ForestArea(LinearRing2D ring, List<LinearRing2D> holes, String material) {
		this.material = material;

		List<Point2D> vertices = new ArrayList<>(ring.vertices());
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

		this.holeXs = new ArrayList<>();
		this.holeYs = new ArrayList<>();
		if (holes != null) {
			for (LinearRing2D hole : holes) {
				if (hole == null) {
					continue;
				}
				List<Point2D> holeVertices = new ArrayList<>(hole.vertices());
				if (holeVertices.size() < 3) {
					continue;
				}
				double[] hx = new double[holeVertices.size()];
				double[] hy = new double[holeVertices.size()];
				for (int i = 0; i < holeVertices.size(); i++) {
					hx[i] = holeVertices.get(i).x();
					hy[i] = holeVertices.get(i).y();
				}
				holeXs.add(hx);
				holeYs.add(hy);
			}
		}

		double anchorLon = count > 0 ? sumLon / count : minLon;
		double anchorLat = count > 0 ? sumLat / count : minLat;
		this.areaM2 = computeAreaM2(xs, ys, holeXs, holeYs, anchorLon, anchorLat);
	}

	/** Number of hole (inner) rings. */
	public int holeCount() {
		return holeXs.size();
	}

	/** Longitudes of hole ring {@code h}. */
	public double[] holeX(int h) {
		return holeXs.get(h);
	}

	/** Latitudes of hole ring {@code h}. */
	public double[] holeY(int h) {
		return holeYs.get(h);
	}

	/** Number of outer ring vertices. */
	public int vertexCount() {
		return xs.length;
	}

	/** Longitude of outer vertex {@code i}. */
	public double x(int i) {
		return xs[i];
	}

	/** Latitude of outer vertex {@code i}. */
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

	/** Area in square metres (outer minus holes, flat-earth around the centroid). */
	public double getAreaM2() {
		return areaM2;
	}

	/** Even-odd point-in-polygon test (outer minus holes; no per-edge distance math). */
	public boolean contains(double lon, double lat) {
		boolean inside = false;
		inside ^= crosses(xs, ys, lon, lat);
		for (int h = 0; h < holeXs.size(); h++) {
			inside ^= crosses(holeXs.get(h), holeYs.get(h), lon, lat);
		}
		return inside;
	}

	private static boolean crosses(double[] xs, double[] ys, double lon, double lat) {
		boolean inside = false;
		for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
			double yi = ys[i];
			double yj = ys[j];
			if ((yi > lat) != (yj > lat)) {
				double xCross = xs[i] + (lat - yi) / (yj - yi) * (xs[j] - xs[i]);
				if (lon < xCross) {
					inside = !inside;
				}
			}
		}
		return inside;
	}

	private static double computeAreaM2(double[] xs, double[] ys, List<double[]> holeXs, List<double[]> holeYs,
			double anchorLon, double anchorLat) {
		double area = ringAreaM2(xs, ys, anchorLon, anchorLat);
		for (int h = 0; h < holeXs.size(); h++) {
			area -= ringAreaM2(holeXs.get(h), holeYs.get(h), anchorLon, anchorLat);
		}
		return Math.max(area, 0.0);
	}

	private static double ringAreaM2(double[] xs, double[] ys, double anchorLon, double anchorLat) {
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
