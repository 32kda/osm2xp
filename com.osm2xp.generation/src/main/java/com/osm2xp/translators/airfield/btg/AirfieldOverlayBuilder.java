package com.osm2xp.translators.airfield.btg;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.translators.flightgear.FlightGearTerrainElevationProber;
import com.osm2xp.utils.geometry.GeomUtils;

/**
 * Builds the airfield overlay geometry: the flat plate (pavements + clearing)
 * and the sloped transition skirt down to the surrounding terrain.
 */
public final class AirfieldOverlayBuilder {

	/** Terrain height at a local east/north point, used to blend the skirt. */
	@FunctionalInterface
	public interface AltitudeSource {
		double altitudeAt(double east, double north);
	}

	private final GeometryFactory geometryFactory = new GeometryFactory();
	private final PolygonTriangulator triangulator = new PolygonTriangulator();

	/** Flat plate triangles for the given surfaces, all at a single elevation. */
	public List<BtgCsgConverter.Triangle> plate(List<SurfacePolygon> surfaces, double elevation) {
		List<BtgCsgConverter.Triangle> result = new ArrayList<>();
		for (SurfacePolygon surface : surfaces) {
			for (double[] t : triangulate(surface.getPolygon(), "plate")) {
				result.add(new BtgCsgConverter.Triangle(t[0], t[1], elevation, t[2], t[3], elevation, t[4], t[5],
						elevation, surface.getMaterial()));
			}
		}
		return result;
	}

	/**
	 * Cut-mode skirt: the part of each removed terrain triangle outside the plate
	 * (hull), sloping from the plate edge down to the terrain at the rim.
	 */
	public List<BtgCsgConverter.Triangle> cutSkirt(List<BtgCsgConverter.TerrainFace> removedFaces, Polygon hull,
			double skirtWidth, double elevation) {
		List<BtgCsgConverter.Triangle> result = new ArrayList<>();
		for (BtgCsgConverter.TerrainFace removed : removedFaces) {
			Geometry piece = facePolygon(removed).difference(hull);
			for (Polygon p : GeomUtils.flatMapToPoly(piece)) {
				for (double[] t : triangulate(p, "skirt")) {
					result.add(lift(t, hull, skirtWidth, elevation,
							(e, n) -> BtgCsgConverter.altitude(removed, e, n)));
				}
			}
		}
		return result;
	}

	/**
	 * Overlay-mode skirt: a smooth ring between the cut footprint and the plate,
	 * blended to the probed terrain.
	 */
	public List<BtgCsgConverter.Triangle> smoothSkirt(List<Polygon> skirtPieces, Polygon hull, double skirtWidth,
			double elevation, AirfieldElevationProbe probe, FlightGearTerrainElevationProber prober) {
		List<BtgCsgConverter.Triangle> result = new ArrayList<>();
		for (Polygon piece : skirtPieces) {
			for (double[] t : triangulate(piece, "skirt")) {
				result.add(lift(t, hull, skirtWidth, elevation, (e, n) -> probe.probeLocal(prober, e, n)));
			}
		}
		return result;
	}

	private BtgCsgConverter.Triangle lift(double[] triangle, Polygon hull, double skirtWidth, double elevation,
			AltitudeSource altitudeSource) {
		double[] z = new double[3];
		for (int i = 0; i < 3; i++) {
			double east = triangle[2 * i];
			double north = triangle[2 * i + 1];
			double t = clamp(distanceToHull(hull, east, north) / skirtWidth, 0.0, 1.0);
			double altitude = altitudeSource.altitudeAt(east, north);
			z[i] = Double.isNaN(altitude) ? elevation : elevation + t * (altitude - elevation);
		}
		return new BtgCsgConverter.Triangle(triangle[0], triangle[1], z[0], triangle[2], triangle[3], z[1],
				triangle[4], triangle[5], z[2], FlightGearAirfieldMaterials.SKIRT);
	}

	private List<double[]> triangulate(Polygon polygon, String label) {
		List<double[]> result = new ArrayList<>();
		for (double[] t : triangulator.triangulateSimple(polygon, label)) {
			if (t.length != 6) {
				Osm2xpLogger.error(label + " triangulation produced a " + (t.length / 2)
						+ "-vertex polygon instead of a triangle; skipping");
				continue;
			}
			result.add(t);
		}
		return result;
	}

	private Polygon facePolygon(BtgCsgConverter.TerrainFace face) {
		return geometryFactory.createPolygon(new Coordinate[] {
				new Coordinate(face.e0, face.n0), new Coordinate(face.e1, face.n1),
				new Coordinate(face.e2, face.n2), new Coordinate(face.e0, face.n0) });
	}

	private double distanceToHull(Polygon hull, double east, double north) {
		return hull.distance(geometryFactory.createPoint(new Coordinate(east, north)));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
