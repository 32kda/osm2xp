package com.osm2xp.translators.airfield.btg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;

import com.osm2xp.core.logging.Osm2xpLogger;

/**
 * Triangulates a JTS {@link Polygon} (possibly with holes) into triangles.
 * <p>
 * Uses a constrained Delaunay triangulation (which respects the ring edges,
 * including hole boundaries) and keeps only the triangles whose centroid lies
 * inside the polygon. On failure it falls back to a plain Delaunay
 * triangulation of the vertices, which may bridge holes but still produces a
 * complete, non-overlapping covering of the polygon.
 * <p>
 * Triangles are returned as flat {@code {e0,n0,e1,n1,e2,n2}} arrays in the same
 * east/north metre frame as the input polygon.
 */
public class PolygonTriangulator {

	private final GeometryFactory geometryFactory = new GeometryFactory();

	public List<double[]> triangulate(Polygon polygon) {
		List<double[]> result = new ArrayList<>();
		if (polygon == null || polygon.isEmpty()) {
			return result;
		}

		List<Coordinate> sites = new ArrayList<>();
		List<LineString> constraints = new ArrayList<>();
		addRing(polygon.getExteriorRing(), sites, constraints);
		for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
			addRing(polygon.getInteriorRingN(i), sites, constraints);
		}
		if (sites.size() < 3) {
			return result;
		}

		PreparedGeometry prepared = PreparedGeometryFactory.prepare(polygon);
		try {
			ConformingDelaunayTriangulationBuilder builder = new ConformingDelaunayTriangulationBuilder();
			builder.setSites(geometryFactory.createMultiPointFromCoords(sites.toArray(new Coordinate[0])));
			builder.setConstraints(geometryFactory.createMultiLineString(constraints.toArray(new LineString[0])));
			collectInside(builder.getTriangles(geometryFactory), prepared, result);
		} catch (Exception e) {
			Osm2xpLogger.warning("Conforming Delaunay triangulation failed (" + e
					+ "); falling back to plain Delaunay (may bridge concavities/holes)");
			DelaunayTriangulationBuilder fallback = new DelaunayTriangulationBuilder();
			fallback.setSites(sites);
			collectInside(fallback.getTriangles(geometryFactory), prepared, result);
		}
		return result;
	}

	/**
	 * Triangulates a polygon by ear clipping, without inserting Steiner points,
	 * which keeps clipped terrain pieces and seams from exploding into slivers.
	 * Holes are bridged into the shell first; degenerate input falls back to
	 * {@link #triangulate(Polygon)}.
	 */
	public List<double[]> triangulateSimple(Polygon polygon) {
		return triangulateSimple(polygon, "polygon");
	}

	/**
	 * Same as {@link #triangulateSimple(Polygon)} but tags warnings with the
	 * caller-supplied {@code label} (e.g. "clip", "plate", "skirt") and verifies
	 * that the output triangles cover the polygon's area.
	 */
	public List<double[]> triangulateSimple(Polygon polygon, String label) {
		if (polygon == null || polygon.isEmpty()) {
			return new ArrayList<>();
		}
		List<Coordinate> vertices = ringVertices(polygon.getExteriorRing(), true);
		if (vertices.size() < 3) {
			return new ArrayList<>();
		}
		for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
			List<Coordinate> hole = ringVertices(polygon.getInteriorRingN(i), false);
			if (hole.size() >= 3) {
				vertices = bridge(vertices, hole);
			}
		}
		for (Coordinate vertex : vertices) {
			if (Double.isNaN(vertex.x) || Double.isNaN(vertex.y) || Double.isInfinite(vertex.x)
					|| Double.isInfinite(vertex.y)) {
				Osm2xpLogger.error("triangulateSimple[" + label + "]: non-finite polygon vertex " + vertex
						+ "; skipping polygon");
				return new ArrayList<>();
			}
		}
		List<double[]> result = new ArrayList<>();
		if (!earClip(vertices, result)) {
			Osm2xpLogger.warning("triangulateSimple[" + label + "]: ear clipping failed for a polygon with "
					+ vertices.size() + " vertices (valid=" + polygon.isValid() + ", area=" + polygon.getArea()
					+ ", holes=" + polygon.getNumInteriorRing()
					+ "); falling back to Conforming Delaunay (Steiner points)");
			result = triangulate(polygon);
		}
		checkArea(polygon, result, label);
		return result;
	}

	/** Warns when the triangulation's total area does not match the polygon area. */
	private static void checkArea(Polygon polygon, List<double[]> triangles, String label) {
		double expected = polygon.getArea();
		double actual = 0.0;
		for (double[] t : triangles) {
			if (t.length >= 6) {
				double cross = (t[2] - t[0]) * (t[5] - t[1]) - (t[3] - t[1]) * (t[4] - t[0]);
				actual += Math.abs(cross) * 0.5;
			}
		}
		double tolerance = Math.max(1.0, expected * 1e-3);
		if (Math.abs(actual - expected) > tolerance) {
			Osm2xpLogger.warning("triangulateSimple[" + label + "]: area mismatch expected=" + expected + " actual="
					+ actual + " (triangles=" + triangles.size() + ", holes=" + polygon.getNumInteriorRing() + ")");
		}
	}

	private static List<Coordinate> ringVertices(LineString ring, boolean ccw) {
		Coordinate[] coordinates = ring.getCoordinates();
		List<Coordinate> vertices = new ArrayList<>(Math.max(0, coordinates.length - 1));
		for (int i = 0; i < coordinates.length - 1; i++) {
			vertices.add(new Coordinate(coordinates[i]));
		}
		if (Orientation.isCCW(coordinates) != ccw) {
			Collections.reverse(vertices);
		}
		return vertices;
	}

	/**
	 * Merges a hole (oriented CW) into the shell (CCW) with a bridge from the
	 * hole's rightmost vertex to the shell's rightmost vertex. Valid because the
	 * shell is convex in the cases we use this for.
	 */
	private static List<Coordinate> bridge(List<Coordinate> shell, List<Coordinate> hole) {
		int hi = 0;
		for (int i = 1; i < hole.size(); i++) {
			if (hole.get(i).x > hole.get(hi).x) {
				hi = i;
			}
		}
		int ui = 0;
		for (int i = 1; i < shell.size(); i++) {
			if (shell.get(i).x > shell.get(ui).x) {
				ui = i;
			}
		}
		List<Coordinate> merged = new ArrayList<>();
		for (int i = 0; i <= ui; i++) {
			merged.add(shell.get(i));
		}
		for (int k = 0; k < hole.size(); k++) {
			merged.add(hole.get((hi + k) % hole.size()));
		}
		merged.add(hole.get(hi));
		merged.add(shell.get(ui));
		for (int i = ui + 1; i < shell.size(); i++) {
			merged.add(shell.get(i));
		}
		return merged;
	}

	private boolean earClip(List<Coordinate> vertices, List<double[]> result) {
		List<Coordinate> remaining = new ArrayList<>(vertices);
		int guard = remaining.size() * remaining.size() + 16;
		while (remaining.size() > 3 && guard-- > 0) {
			boolean clipped = false;
			for (int i = 0; i < remaining.size(); i++) {
				Coordinate a = remaining.get((i - 1 + remaining.size()) % remaining.size());
				Coordinate b = remaining.get(i);
				Coordinate c = remaining.get((i + 1) % remaining.size());
				if (isConvex(a, b, c) && !containsOtherVertex(remaining, i, a, b, c)) {
					result.add(new double[] { a.x, a.y, b.x, b.y, c.x, c.y });
					remaining.remove(i);
					clipped = true;
					break;
				}
			}
			if (!clipped) {
				return false;
			}
		}
		if (remaining.size() == 3) {
			Coordinate a = remaining.get(0);
			Coordinate b = remaining.get(1);
			Coordinate c = remaining.get(2);
			if (Math.abs(area2(a, b, c)) > 1e-12) {
				result.add(new double[] { a.x, a.y, b.x, b.y, c.x, c.y });
			}
			return true;
		}
		return false;
	}

	private static boolean isConvex(Coordinate a, Coordinate b, Coordinate c) {
		return area2(a, b, c) > 0.0;
	}

	private static double area2(Coordinate a, Coordinate b, Coordinate c) {
		return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
	}

	private static boolean containsOtherVertex(List<Coordinate> vertices, int skipIndex, Coordinate a, Coordinate b,
			Coordinate c) {
		for (int i = 0; i < vertices.size(); i++) {
			if (i == skipIndex || i == (skipIndex - 1 + vertices.size()) % vertices.size()
					|| i == (skipIndex + 1) % vertices.size()) {
				continue;
			}
			if (pointInTriangle(vertices.get(i), a, b, c)) {
				return true;
			}
		}
		return false;
	}

	private static boolean pointInTriangle(Coordinate p, Coordinate a, Coordinate b, Coordinate c) {
		double d1 = area2(a, b, p);
		double d2 = area2(b, c, p);
		double d3 = area2(c, a, p);
		boolean hasNeg = d1 < 0.0 || d2 < 0.0 || d3 < 0.0;
		boolean hasPos = d1 > 0.0 || d2 > 0.0 || d3 > 0.0;
		return !(hasNeg && hasPos);
	}

	private void collectInside(Geometry triangles, PreparedGeometry prepared, List<double[]> result) {
		for (int i = 0; i < triangles.getNumGeometries(); i++) {
			Polygon triangle = (Polygon) triangles.getGeometryN(i);
			if (prepared.contains(triangle.getCentroid())) {
				result.add(toFlat(triangle));
			}
		}
	}

	private void addRing(LineString ring, List<Coordinate> sites, List<LineString> constraints) {
		Coordinate[] coordinates = ring.getCoordinates();
		// The ring is closed (first == last); add every vertex except the duplicate.
		for (int i = 0; i < coordinates.length - 1; i++) {
			sites.add(coordinates[i]);
		}
		constraints.add(geometryFactory.createLineString(coordinates));
	}

	private double[] toFlat(Polygon triangle) {
		Coordinate[] coordinates = triangle.getExteriorRing().getCoordinates();
		if (coordinates.length != 4) {
			Osm2xpLogger.warning("Delaunay fallback returned a " + (coordinates.length - 1)
					+ "-vertex polygon instead of a triangle; only the first 3 vertices are used");
		}
		return new double[] { coordinates[0].x, coordinates[0].y, coordinates[1].x, coordinates[1].y,
				coordinates[2].x, coordinates[2].y };
	}
}
