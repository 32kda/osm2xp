package com.osm2xp.generation.dsf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Splits oversized DSF polygons into several exact sub-polygons so that each
 * one fits within the DSF per-polygon limit of 65535 points (a single DSF
 * polygon references one point pool using 16-bit indices).
 *
 * <p>Splitting picks the axis of larger dispersion (longitude or latitude) and
 * cuts at the edge-length weighted median, then recurses on the pieces. The
 * union of the pieces equals the original polygon and holes are preserved.
 */
final class PolygonSubdivider {

	static final int POINT_LIMIT = 65535;

	private static final int MAX_DEPTH = 40;

	private PolygonSubdivider() {
	}

	/**
	 * @param depth    DSF polygon coordinate depth (only 2, i.e. lon/lat, is split)
	 * @param windings polygon windings (outer ring plus holes, in any orientation)
	 * @return the sub-polygon windings, or {@code null} if the polygon could not
	 *         be split
	 */
	static List<List<List<double[]>>> subdivide(int depth, List<List<double[]>> windings) {
		if (depth != 2) {
			return null;
		}
		GeometryFactory factory = new GeometryFactory();
		List<LinearRing> rings = new ArrayList<>();
		for (List<double[]> ring : windings) {
			Coordinate[] coords = toClosedRing(ring);
			if (coords.length < 4) {
				continue;
			}
			try {
				rings.add(factory.createLinearRing(coords));
			} catch (RuntimeException e) {
				return null;
			}
		}
		List<Polygon> polygons = assemble(factory, rings);
		if (polygons.isEmpty()) {
			return null;
		}
		List<Polygon> pieces = new ArrayList<>();
		for (Polygon polygon : polygons) {
			split(polygon, pieces, MAX_DEPTH, factory);
		}
		if (pieces.isEmpty()) {
			return null;
		}
		List<List<List<double[]>>> result = new ArrayList<>(pieces.size());
		for (Polygon piece : pieces) {
			result.add(toWindings(piece));
		}
		return result;
	}

	/**
	 * Assembles a set of (possibly nested) rings into polygons using containment
	 * depth only, so it is independent of ring winding direction.
	 */
	private static List<Polygon> assemble(GeometryFactory factory, List<LinearRing> rings) {
		int n = rings.size();
		Polygon[] bare = new Polygon[n];
		Point[] interior = new Point[n];
		double[] area = new double[n];
		Envelope[] bounds = new Envelope[n];
		for (int i = 0; i < n; i++) {
			bare[i] = factory.createPolygon(rings.get(i));
			interior[i] = bare[i].getInteriorPoint();
			area[i] = bare[i].getArea();
			bounds[i] = bare[i].getEnvelopeInternal();
		}
		int[] nesting = new int[n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i == j || !bounds[j].contains(bounds[i])) {
					continue;
				}
				if (bare[j].covers(interior[i])) {
					nesting[i]++;
				}
			}
		}
		List<Polygon> polygons = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (nesting[i] % 2 != 0) {
				continue;
			}
			List<LinearRing> holes = new ArrayList<>();
			for (int k = 0; k < n; k++) {
				if (nesting[k] % 2 == 0) {
					continue;
				}
				if (parent(rings, bare, bounds, interior, area, k) == i) {
					holes.add(rings.get(k));
				}
			}
			try {
				polygons.add(factory.createPolygon(rings.get(i), holes.toArray(new LinearRing[0])));
			} catch (RuntimeException e) {
				polygons.add(bare[i]);
			}
		}
		return polygons;
	}

	private static int parent(List<LinearRing> rings, Polygon[] bare, Envelope[] bounds, Point[] interior, double[] area,
			int hole) {
		int best = -1;
		for (int j = 0; j < rings.size(); j++) {
			if (j == hole || !bounds[j].contains(bounds[hole])) {
				continue;
			}
			if (bare[j].covers(interior[hole]) && (best == -1 || area[j] < area[best])) {
				best = j;
			}
		}
		return best;
	}

	private static void split(Polygon polygon, List<Polygon> out, int depth, GeometryFactory factory) {
		if (pointCount(polygon) <= POINT_LIMIT || depth <= 0) {
			out.add(polygon);
			return;
		}
		Envelope envelope = polygon.getEnvelopeInternal();
		boolean xAxis = envelope.getWidth() >= envelope.getHeight();
		double min = xAxis ? envelope.getMinX() : envelope.getMinY();
		double max = xAxis ? envelope.getMaxX() : envelope.getMaxY();
		double cut = edgeWeightedMedian(polygon, xAxis);
		if (!(cut > min && cut < max)) {
			cut = (min + max) / 2.0;
		}
		if (!(cut > min && cut < max)) {
			out.add(polygon);
			return;
		}
		double pad = Math.max(1.0, max - min);
		Envelope lowEnv = new Envelope();
		Envelope highEnv = new Envelope();
		if (xAxis) {
			lowEnv.init(envelope.getMinX() - pad, cut, envelope.getMinY() - pad, envelope.getMaxY() + pad);
			highEnv.init(cut, envelope.getMaxX() + pad, envelope.getMinY() - pad, envelope.getMaxY() + pad);
		} else {
			lowEnv.init(envelope.getMinX() - pad, envelope.getMaxX() + pad, envelope.getMinY() - pad, cut);
			highEnv.init(envelope.getMinX() - pad, envelope.getMaxX() + pad, cut, envelope.getMaxY() + pad);
		}
		List<Polygon> low = new ArrayList<>();
		List<Polygon> high = new ArrayList<>();
		collect(polygon.intersection(factory.toGeometry(lowEnv)), low);
		collect(polygon.intersection(factory.toGeometry(highEnv)), high);
		if (low.isEmpty() || high.isEmpty()) {
			out.add(polygon);
			return;
		}
		if (Math.max(totalPoints(low), totalPoints(high)) >= pointCount(polygon)) {
			out.add(polygon);
			return;
		}
		for (Polygon p : low) {
			split(p, out, depth - 1, factory);
		}
		for (Polygon p : high) {
			split(p, out, depth - 1, factory);
		}
	}

	private static double edgeWeightedMedian(Polygon polygon, boolean xAxis) {
		List<double[]> edges = new ArrayList<>();
		addEdges(polygon.getExteriorRing(), xAxis, edges);
		for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
			addEdges(polygon.getInteriorRingN(i), xAxis, edges);
		}
		if (edges.isEmpty()) {
			return Double.NaN;
		}
		edges.sort(Comparator.comparingDouble(e -> e[0]));
		double total = 0.0;
		for (double[] edge : edges) {
			total += edge[1];
		}
		if (total <= 0.0) {
			return Double.NaN;
		}
		double accumulated = 0.0;
		for (double[] edge : edges) {
			accumulated += edge[1];
			if (accumulated >= total / 2.0) {
				return edge[0];
			}
		}
		return edges.get(edges.size() - 1)[0];
	}

	private static void addEdges(LineString ring, boolean xAxis, List<double[]> edges) {
		Coordinate[] coords = ring.getCoordinates();
		for (int i = 0; i + 1 < coords.length; i++) {
			double a = xAxis ? coords[i].x : coords[i].y;
			double b = xAxis ? coords[i + 1].x : coords[i + 1].y;
			double length = coords[i].distance(coords[i + 1]);
			edges.add(new double[] { (a + b) / 2.0, length });
		}
	}

	private static void collect(Geometry geometry, List<Polygon> out) {
		if (geometry == null || geometry.isEmpty()) {
			return;
		}
		if (geometry instanceof Polygon) {
			out.add((Polygon) geometry);
		} else if (geometry instanceof MultiPolygon) {
			for (int i = 0; i < geometry.getNumGeometries(); i++) {
				collect(geometry.getGeometryN(i), out);
			}
		}
	}

	private static int pointCount(Polygon polygon) {
		return totalPoints(java.util.Collections.singletonList(polygon));
	}

	private static int totalPoints(List<Polygon> polygons) {
		int total = 0;
		for (Polygon polygon : polygons) {
			total += polygon.getExteriorRing().getNumPoints() - 1;
			for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
				total += polygon.getInteriorRingN(i).getNumPoints() - 1;
			}
		}
		return total;
	}

	private static Coordinate[] toClosedRing(List<double[]> ring) {
		int n = ring.size();
		boolean closed = n > 1 && ring.get(0)[0] == ring.get(n - 1)[0] && ring.get(0)[1] == ring.get(n - 1)[1];
		int count = closed ? n : n + 1;
		Coordinate[] coords = new Coordinate[count];
		for (int i = 0; i < n; i++) {
			coords[i] = new Coordinate(ring.get(i)[0], ring.get(i)[1]);
		}
		if (!closed) {
			coords[n] = new Coordinate(ring.get(0)[0], ring.get(0)[1]);
		}
		return coords;
	}

	private static List<List<double[]>> toWindings(Polygon polygon) {
		List<List<double[]>> windings = new ArrayList<>();
		List<double[]> exterior = toPoints(polygon.getExteriorRing());
		if (signedArea(exterior) < 0) {
			java.util.Collections.reverse(exterior);
		}
		windings.add(exterior);
		for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
			List<double[]> hole = toPoints(polygon.getInteriorRingN(i));
			if (signedArea(hole) > 0) {
				java.util.Collections.reverse(hole);
			}
			windings.add(hole);
		}
		return windings;
	}

	private static double signedArea(List<double[]> ring) {
		double area = 0.0;
		for (int i = 0; i < ring.size(); i++) {
			double[] a = ring.get(i);
			double[] b = ring.get((i + 1) % ring.size());
			area += a[0] * b[1] - b[0] * a[1];
		}
		return area / 2.0;
	}

	private static List<double[]> toPoints(LineString ring) {
		Coordinate[] coords = ring.getCoordinates();
		int n = coords.length;
		if (n > 1 && coords[0].equals2D(coords[n - 1])) {
			n--;
		}
		List<double[]> points = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			points.add(new double[] { coords[i].x, coords[i].y });
		}
		return points;
	}
}
