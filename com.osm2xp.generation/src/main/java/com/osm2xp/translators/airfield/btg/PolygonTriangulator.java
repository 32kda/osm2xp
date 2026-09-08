package com.osm2xp.translators.airfield.btg;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder;

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
			DelaunayTriangulationBuilder fallback = new DelaunayTriangulationBuilder();
			fallback.setSites(sites);
			collectInside(fallback.getTriangles(geometryFactory), prepared, result);
		}
		return result;
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
		return new double[] { coordinates[0].x, coordinates[0].y, coordinates[1].x, coordinates[1].y,
				coordinates[2].x, coordinates[2].y };
	}
}
