package com.osm2xp.translators.airfield.btg;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Polygon;

/**
 * The airfield's cut footprint: the convex hull of its surfaces, the polygon
 * actually cut out of the terrain, and the width of the transition skirt.
 * <p>
 * The cut polygon is the hull expanded by {@link #CUT_MARGIN_M} (a small margin
 * so the hull edge is safely inside the cut). The skirt width is how far the
 * transition slopes down to the surrounding terrain.
 */
public final class AirfieldCutFootprint {

	/** Small margin added to the hull to form the cut polygon. */
	public static final double CUT_MARGIN_M = 5.0;
	/** Minimum transition-skirt width, metres. */
	public static final double SKIRT_MIN_WIDTH_M = 100.0;
	/** Skirt width as a fraction of the hull diameter. */
	public static final double SKIRT_DIAMETER_RATIO = 0.10;

	private final Polygon hull;
	private final Polygon cutPolygon;
	private final double skirtWidthM;

	private AirfieldCutFootprint(Polygon hull, Polygon cutPolygon, double skirtWidthM) {
		this.hull = hull;
		this.cutPolygon = cutPolygon;
		this.skirtWidthM = skirtWidthM;
	}

	/** Builds the footprint from the airfield surfaces, or {@code null} if empty. */
	public static AirfieldCutFootprint of(List<SurfacePolygon> surfaces) {
		GeometryFactory geometryFactory = new GeometryFactory();
		Polygon hull = convexHull(geometryFactory, surfaces);
		if (hull == null || hull.isEmpty()) {
			return null;
		}
		Polygon cutPolygon = (Polygon) hull.buffer(CUT_MARGIN_M);
		double skirtWidth = Math.max(SKIRT_MIN_WIDTH_M, SKIRT_DIAMETER_RATIO * diameterOf(hull));
		return new AirfieldCutFootprint(hull, cutPolygon, skirtWidth);
	}

	public Polygon hull() {
		return hull;
	}

	public Polygon cutPolygon() {
		return cutPolygon;
	}

	public double skirtWidthM() {
		return skirtWidthM;
	}

	private static Polygon convexHull(GeometryFactory geometryFactory, List<SurfacePolygon> surfaces) {
		List<Coordinate> coordinates = new ArrayList<>();
		for (SurfacePolygon surface : surfaces) {
			for (Coordinate coordinate : surface.getPolygon().getExteriorRing().getCoordinates()) {
				coordinates.add(coordinate);
			}
		}
		if (coordinates.size() < 3) {
			return null;
		}
		MultiPoint points = geometryFactory.createMultiPointFromCoords(coordinates.toArray(new Coordinate[0]));
		Geometry hull = new ConvexHull(points).getConvexHull();
		return (hull instanceof Polygon) ? (Polygon) hull : null;
	}

	private static double diameterOf(Polygon hull) {
		Coordinate[] coordinates = hull.getExteriorRing().getCoordinates();
		double max = 0.0;
		for (int i = 0; i < coordinates.length; i++) {
			for (int j = i + 1; j < coordinates.length; j++) {
				max = Math.max(max, coordinates[i].distance(coordinates[j]));
			}
		}
		return max;
	}
}
