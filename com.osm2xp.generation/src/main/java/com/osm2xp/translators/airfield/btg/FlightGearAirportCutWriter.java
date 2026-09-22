package com.osm2xp.translators.airfield.btg;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgFace;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.core.parsers.btg.BtgVector3;
import com.osm2xp.translators.airfield.AirfieldData;
import com.osm2xp.translators.flightgear.FlightGearBucket;
import com.osm2xp.translators.flightgear.FlightGearCoordinateUtils;
import com.osm2xp.translators.flightgear.FlightGearTerrainElevationProber;
import com.osm2xp.utils.geometry.GeomUtils;

/**
 * Bakes an airfield into the FlightGear terrain by <em>cutting a hole</em> in
 * the terrain BTG mesh and filling it with a flat airfield plate at a single
 * elevation, plus a sloped transition skirt down to the surrounding terrain.
 * <p>
 * This is the experimental alternative to {@link FlightGearAirportBtgWriter}'s
 * merge-on-top approach. The cut removes the terrain inside the buffered
 * airfield footprint (a vertical prism, so it is a robust 2D polygon
 * difference); the elevation is the maximum probed terrain height inside the
 * buffered airfield
 * footprint.
 */
public class FlightGearAirportCutWriter {

	private static final double BUFFER_MIN_M = 100.0;
	private static final double BUFFER_DIAMETER_RATIO = 0.10;
	private static final int PROBE_GRID = 8;
	/** Extra terrain kept around the cut so the cut boundary is well inside the region. */
	private static final double REGION_MARGIN_M = 200.0;
	/** Upper bound on the number of terrain faces processed per tile, to avoid pathological tiles. */
	private static final int MAX_CUT_FACES = 60000;
	/** Two seam vertices closer than this are treated as coincident and collapsed. */
	private static final double SEAM_EPSILON_M = 0.01;
	/**
	 * Extra height added to the plate in no-cut overlay mode, so the flat airfield
	 * sits just above the (kept) terrain instead of z-fighting with it.
	 */
	private static final double NO_CUT_ELEVATION_MARGIN_M = 0.3;
	/**
	 * TEMP diagnostic (airfield BTG crash bisection): when {@code false} the
	 * sloped transition skirt is not generated, leaving only the clipped terrain
	 * plus the flat plate (and therefore a hole between the plate edge and the
	 * cut boundary). Defaults to {@code false} for the current bisection run; set
	 * {@code -Dosm2xp.airfield.btg.skirt=true} to restore the normal behaviour.
	 */
	private static final boolean GENERATE_SKIRT = Boolean
			.parseBoolean(System.getProperty("osm2xp.airfield.btg.skirt", "true"));
	/**
	 * TEMP diagnostic (airfield BTG crash bisection): when {@code false} the flat
	 * airfield plate is not generated, leaving only the clipped terrain (and thus
	 * a hole where the airfield was). Defaults to {@code true}; set
	 * {@code -Dosm2xp.airfield.btg.plate=false} for the clip-only run.
	 */
	private static final boolean GENERATE_PLATE = Boolean
			.parseBoolean(System.getProperty("osm2xp.airfield.btg.plate", "true"));

	private final GeometryFactory geometryFactory = new GeometryFactory();
	private final PolygonTriangulator triangulator = new PolygonTriangulator();

	/**
	 * Bakes the airfield into the terrain.
	 *
	 * @param sourceTerrainDir the {@code Terrain} directory holding the original
	 *            tiles (the {@code source_tiles} cache), used for elevation probing
	 * @param outputSceneryRoot the generated scenery root; the patched tile is
	 *            written to {@code <outputSceneryRoot>/Terrain/...}
	 * @param cutTerrain when {@code true} the buffered footprint is cut out of the
	 *                   terrain and filled with the flat plate + sloped skirt;
	 *                   when {@code false} the terrain is left untouched and only
	 *                   the plate + skirt are overlaid on top of it (a diagnostic
	 *                   mode to isolate the effect of the cut)
	 */
	public void write(AirfieldData airfield, File sourceTerrainDir, File outputSceneryRoot, boolean cutTerrain) {
		FlightGearTerrainElevationProber prober = new FlightGearTerrainElevationProber(sourceTerrainDir);
		if (!prober.isAvailable()) {
			return;
		}

		math.geom2d.Point2D datum = airfield.getDatum();
		double datumLon = datum.x();
		double datumLat = datum.y();

		List<SurfacePolygon> surfaces = new AirfieldGeometryBuilder().build(airfield);
		if (surfaces.isEmpty()) {
			return;
		}

		Polygon hull = convexHull(surfaces);
		if (hull == null || hull.isEmpty()) {
			return;
		}

		double buffer = Math.max(BUFFER_MIN_M, BUFFER_DIAMETER_RATIO * diameterOf(hull));
		Polygon cutPolygon = (Polygon) hull.buffer(5); //FIXME debug

		double[] minMax = probeElevation(prober, cutPolygon, datumLon, datumLat);
		if (Double.isNaN(minMax[0]) || Double.isNaN(minMax[1])) {
			Osm2xpLogger.info("Skipping airfield cut " + label(airfield) + ": no terrain found");
			return;
		}
		double elevation = minMax[1];
		double plateElevation = cutTerrain ? elevation : elevation + NO_CUT_ELEVATION_MARGIN_M;

		BtgCsgConverter converter = new BtgCsgConverter(datumLon, datumLat);

		List<SurfacePolygon> plateSurfaces = plateSurfaces(surfaces, hull);
		Map<FlightGearBucket, List<SurfacePolygon>> plateByTile = splitSurfacesByTile(plateSurfaces, datum);

		Set<FlightGearBucket> buckets = bucketsFor(cutPolygon, datum);

		// Pass 1 (cut mode only): partition each tile into untouched faces and
		// removed faces (the hole). No triangle is clipped or re-triangulated, so
		// the kept terrain stays conforming with the hole boundary.
		Map<FlightGearBucket, TileCut> cuts = new HashMap<>();
		if (cutTerrain) {
			for (FlightGearBucket bucket : buckets) {
				BtgTile terrain = prober.getTile(bucket);
				if (terrain == null) {
					continue;
				}
				TileCut cut = partitionTile(converter, terrain, cutPolygon);
				if (cut != null && !cut.removedFaces.isEmpty()) {
					cuts.put(bucket, cut);
				}
			}
			if (cuts.isEmpty()) {
				return;
			}
		}

		// Overlay mode: the terrain is untouched, so the skirt is the smooth ring
		// between the cut footprint and the plate.
		Map<FlightGearBucket, List<Polygon>> skirtByTile = new HashMap<>();
		if (!cutTerrain && GENERATE_SKIRT) {
			Geometry skirtRing = cutPolygon.difference(hull);
			skirtByTile = splitGeometryByTile(skirtRing, datum);
		}

		// Pass 2: assemble the overlay (plate + skirt) and write.
		if (cutTerrain) {
			for (Map.Entry<FlightGearBucket, TileCut> entry : cuts.entrySet()) {
				FlightGearBucket bucket = entry.getKey();
				TileCut cut = entry.getValue();
				List<BtgCsgConverter.Triangle> overlay = buildOverlay(bucket, plateElevation, plateByTile,
						cut.removedFaces, skirtByTile, hull, buffer, prober, datumLon, datumLat);
				// Keep the untouched faces; plate + skirt fill the removed hole.
				BtgTile merged = converter.rebuild(cut.terrain, cut.keptFaces, new ArrayList<>(), overlay);
				writeTile(merged, bucket, outputSceneryRoot, airfield, elevation);
			}
		} else {
			for (FlightGearBucket bucket : buckets) {
				BtgTile terrain = prober.getTile(bucket);
				if (terrain == null) {
					continue;
				}
				List<BtgCsgConverter.Triangle> overlay = buildOverlay(bucket, plateElevation, plateByTile,
						null, skirtByTile, hull, buffer, prober, datumLon, datumLat);
				// Keep every original terrain face and overlay the airfield on top.
				BtgTile merged = converter.rebuild(terrain, terrain.getFaces(), new ArrayList<>(), overlay);
				writeTile(merged, bucket, outputSceneryRoot, airfield, plateElevation);
			}
		}
	}

	/** Builds the flat plate + sloped skirt overlay triangles for one tile. */
	private List<BtgCsgConverter.Triangle> buildOverlay(FlightGearBucket bucket, double elevation,
			Map<FlightGearBucket, List<SurfacePolygon>> plateByTile,
			List<BtgCsgConverter.TerrainFace> removedFaces, Map<FlightGearBucket, List<Polygon>> skirtByTile,
			Polygon hull, double buffer, FlightGearTerrainElevationProber prober, double datumLon, double datumLat) {
		List<BtgCsgConverter.Triangle> overlay = new ArrayList<>();
		if (GENERATE_PLATE) {
			for (SurfacePolygon surface : plateByTile.getOrDefault(bucket, new ArrayList<>())) {
				for (double[] triangle : triangulator.triangulateSimple(surface.getPolygon(), "plate")) {
					if (triangle.length != 6) {
						Osm2xpLogger.error("Plate triangulation produced a " + (triangle.length / 2)
								+ "-vertex polygon instead of a triangle; skipping");
						continue;
					}
					overlay.add(new BtgCsgConverter.Triangle(triangle[0], triangle[1], elevation, triangle[2],
							triangle[3], elevation, triangle[4], triangle[5], elevation, surface.getMaterial()));
				}
			}
		}
		if (GENERATE_SKIRT) {
			if (removedFaces != null && !removedFaces.isEmpty()) {
				// Cut mode: the skirt is the part of each removed terrain triangle
				// outside the plate (hull). Its outer rim keeps the removed triangle's
				// own edges (welds to the untouched terrain); its inner rim is the hull
				// (welds to the plate).
				for (BtgCsgConverter.TerrainFace removed : removedFaces) {
					Geometry piece = facePolygon(removed).difference(hull);
					for (Polygon p : GeomUtils.flatMapToPoly(piece)) {
						for (double[] triangle : triangulator.triangulateSimple(p, "skirt")) {
							if (triangle.length != 6) {
								Osm2xpLogger.error("Skirt triangulation produced a " + (triangle.length / 2)
										+ "-vertex polygon instead of a triangle; skipping");
								continue;
							}
							overlay.add(lift(triangle, elevation, hull, buffer, removed));
						}
					}
				}
			} else {
				// Overlay mode: smooth ring between the footprint and the plate.
				for (Polygon piece : skirtByTile.getOrDefault(bucket, new ArrayList<>())) {
					for (double[] triangle : triangulator.triangulateSimple(piece, "skirt")) {
						if (triangle.length != 6) {
							Osm2xpLogger.error("Skirt triangulation produced a " + (triangle.length / 2)
									+ "-vertex polygon instead of a triangle; skipping");
							continue;
						}
						overlay.add(liftSmooth(triangle, elevation, hull, buffer, prober, datumLon, datumLat));
					}
				}
			}
		}
		return overlay;
	}

	private Polygon facePolygon(BtgCsgConverter.TerrainFace face) {
		return geometryFactory.createPolygon(new Coordinate[] {
				new Coordinate(face.e0, face.n0), new Coordinate(face.e1, face.n1),
				new Coordinate(face.e2, face.n2), new Coordinate(face.e0, face.n0) });
	}

	/** One tile's cell-based cut: the untouched faces and the removed (hole) faces. */
	private static final class TileCut {
		final BtgTile terrain;
		final List<BtgFace> keptFaces;
		final List<BtgCsgConverter.TerrainFace> removedFaces;

		TileCut(BtgTile terrain, List<BtgFace> keptFaces, List<BtgCsgConverter.TerrainFace> removedFaces) {
			this.terrain = terrain;
			this.keptFaces = keptFaces;
			this.removedFaces = removedFaces;
		}
	}

	/** Removes the terrain faces intersecting the cut footprint (no clipping). */
	private TileCut partitionTile(BtgCsgConverter converter, BtgTile terrain, Polygon cutPolygon) {
		try {
			BtgCsgConverter.CellCut cut = converter.cellCut(terrain, cutPolygon);
			if (cut.removedFaces.size() > MAX_CUT_FACES) {
				Osm2xpLogger.warning("Skipping terrain cut: " + cut.removedFaces.size() + " faces (limit "
						+ MAX_CUT_FACES + ")");
				return null;
			}
			return new TileCut(terrain, cut.keptFaces, cut.removedFaces);
		} catch (Throwable t) {
			Osm2xpLogger.error("Error partitioning terrain tile, leaving it untouched", t);
			return null;
		}
	}

	/** Inserts the seam vertices into the cut footprint's boundary edges. */
	private Polygon densify(Polygon polygon, List<double[]> points) {
		if (points.isEmpty()) {
			return polygon;
		}
		Coordinate[] ring = polygon.getExteriorRing().getCoordinates();
		List<Coordinate> densified = new ArrayList<>();
		int skipped = 0;
		for (int i = 0; i < ring.length - 1; i++) {
			Coordinate a = ring[i];
			Coordinate b = ring[i + 1];
			densified.add(a);
			List<double[]> onEdge = new ArrayList<>();
			for (double[] p : points) {
				if (pointOnSegment(p[0], p[1], a.x, a.y, b.x, b.y)) {
					onEdge.add(p);
				}
			}
			onEdge.sort((p, q) -> Double.compare(distance(a.x, a.y, p[0], p[1]),
					distance(a.x, a.y, q[0], q[1])));
			double lastX = a.x;
			double lastY = a.y;
			for (double[] p : onEdge) {
				if (distance(p[0], p[1], lastX, lastY) < SEAM_EPSILON_M
						|| distance(p[0], p[1], b.x, b.y) < SEAM_EPSILON_M) {
					skipped++;
					continue;
				}
				densified.add(new Coordinate(p[0], p[1]));
				lastX = p[0];
				lastY = p[1];
			}
		}
		densified.add(densified.get(0));
		if (skipped > 0) {
			Osm2xpLogger.warning("Seam densify: skipped " + skipped
					+ " degenerate seam vertices (coincident with an edge endpoint or a neighbour)");
		}
		return geometryFactory.createPolygon(densified.toArray(new Coordinate[0]));
	}

	private static boolean pointOnSegment(double px, double py, double ax, double ay, double bx, double by) {
		double dx = bx - ax;
		double dy = by - ay;
		double lengthSquared = dx * dx + dy * dy;
		if (lengthSquared < 1e-12) {
			return false;
		}
		double t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
		if (t < 0.0 || t > 1.0) {
			return false;
		}
		double projX = ax + t * dx;
		double projY = ay + t * dy;
		return distance(px, py, projX, projY) < 0.05;
	}

	private static double distance(double ax, double ay, double bx, double by) {
		return Math.hypot(ax - bx, ay - by);
	}

	private Set<FlightGearBucket> bucketsFor(Polygon localPolygon, math.geom2d.Point2D datum) {
		Polygon geodetic = toGeodeticPolygon(localPolygon, datum);
		return new HashSet<>(bucketsIn(geodetic.getEnvelopeInternal()));
	}

	private List<SurfacePolygon> plateSurfaces(List<SurfacePolygon> surfaces, Polygon hull) {
		List<SurfacePolygon> result = new ArrayList<>(surfaces);
		Geometry covered = union(surfaces);
		if (covered == null || covered.isEmpty()) {
			return result;
		}
		Geometry gaps = hull.difference(covered);
		for (Polygon gap : GeomUtils.flatMapToPoly(gaps)) {
			if (!gap.isEmpty()) {
				result.add(new SurfacePolygon(gap, FlightGearAirfieldMaterials.SKIRT));
			}
		}
		return result;
	}

	private Geometry union(List<SurfacePolygon> surfaces) {
		List<Geometry> geometries = new ArrayList<>();
		for (SurfacePolygon surface : surfaces) {
			geometries.add(surface.getPolygon());
		}
		if (geometries.isEmpty()) {
			return null;
		}
		if (geometries.size() == 1) {
			return geometries.get(0);
		}
		return new org.locationtech.jts.operation.union.CascadedPolygonUnion(geometries).union();
	}

	private Polygon convexHull(List<SurfacePolygon> surfaces) {
		List<Coordinate> coordinates = new ArrayList<>();
		for (SurfacePolygon surface : surfaces) {
			for (Coordinate coordinate : surface.getPolygon().getExteriorRing().getCoordinates()) {
				coordinates.add(coordinate);
			}
		}
		if (coordinates.size() < 3) {
			return null;
		}
		org.locationtech.jts.geom.MultiPoint points = geometryFactory
				.createMultiPointFromCoords(coordinates.toArray(new Coordinate[0]));
		ConvexHull convexHull = new ConvexHull(points);
		Geometry hull = convexHull.getConvexHull();
		if (hull instanceof Polygon) {
			return (Polygon) hull;
		}
		return null;
	}

	private double diameterOf(Polygon hull) {
		Coordinate[] coordinates = hull.getExteriorRing().getCoordinates();
		double max = 0.0;
		for (int i = 0; i < coordinates.length; i++) {
			for (int j = i + 1; j < coordinates.length; j++) {
				max = Math.max(max, coordinates[i].distance(coordinates[j]));
			}
		}
		return max;
	}

	private double[] probeElevation(FlightGearTerrainElevationProber prober, Polygon cutPolygon, double datumLon,
			double datumLat) {
		double min = Double.NaN;
		double max = Double.NaN;
		List<double[]> lonLats = new ArrayList<>();
		for (Coordinate coordinate : cutPolygon.getExteriorRing().getCoordinates()) {
			lonLats.add(FlightGearCoordinateUtils.localToGeodetic(coordinate.x, coordinate.y, datumLon, datumLat));
		}
		Envelope envelope = cutPolygon.getEnvelopeInternal();
		for (int i = 1; i < PROBE_GRID; i++) {
			for (int j = 1; j < PROBE_GRID; j++) {
				double east = envelope.getMinX() + (envelope.getMaxX() - envelope.getMinX()) * i / PROBE_GRID;
				double north = envelope.getMinY() + (envelope.getMaxY() - envelope.getMinY()) * j / PROBE_GRID;
				Point point = geometryFactory.createPoint(new Coordinate(east, north));
				if (cutPolygon.contains(point)) {
					lonLats.add(FlightGearCoordinateUtils.localToGeodetic(east, north, datumLon, datumLat));
				}
			}
		}
		for (double[] lonLat : lonLats) {
			double altitude = probe(prober, lonLat[0], lonLat[1]);
			if (Double.isNaN(altitude)) {
				continue;
			}
			min = Double.isNaN(min) ? altitude : Math.min(min, altitude);
			max = Double.isNaN(max) ? altitude : Math.max(max, altitude);
		}
		return new double[] { min, max };
	}

	private double probe(FlightGearTerrainElevationProber prober, double lon, double lat) {
		FlightGearBucket bucket = FlightGearBucket.bucketFor(lon, lat);
		return FlightGearTerrainElevationProber.probe(prober.getTile(bucket), lon, lat);
	}

	private BtgCsgConverter.Triangle lift(double[] triangle, double elevation, Polygon hull, double buffer,
			BtgCsgConverter.TerrainFace face) {
		double[] z = new double[3];
		for (int i = 0; i < 3; i++) {
			double east = triangle[2 * i];
			double north = triangle[2 * i + 1];
			double t = clamp(distanceToHull(hull, east, north) / buffer, 0.0, 1.0);
			// Use the source terrain face's own altitude so the skirt rim welds to the
			// untouched terrain (no probe round-trip error).
			double altitude = BtgCsgConverter.altitude(face, east, north);
			z[i] = Double.isNaN(altitude) ? elevation : elevation + t * (altitude - elevation);
		}
		return new BtgCsgConverter.Triangle(triangle[0], triangle[1], z[0], triangle[2], triangle[3], z[1],
				triangle[4], triangle[5], z[2], FlightGearAirfieldMaterials.SKIRT);
	}

	/** Smooth skirt (overlay mode): blends from the plate to the probed terrain. */
	private BtgCsgConverter.Triangle liftSmooth(double[] triangle, double elevation, Polygon hull, double buffer,
			FlightGearTerrainElevationProber prober, double datumLon, double datumLat) {
		double[] z = new double[3];
		for (int i = 0; i < 3; i++) {
			double east = triangle[2 * i];
			double north = triangle[2 * i + 1];
			double t = clamp(distanceToHull(hull, east, north) / buffer, 0.0, 1.0);
			double[] lonLat = FlightGearCoordinateUtils.localToGeodetic(east, north, datumLon, datumLat);
			double altitude = probe(prober, lonLat[0], lonLat[1]);
			z[i] = Double.isNaN(altitude) ? elevation : elevation + t * (altitude - elevation);
		}
		return new BtgCsgConverter.Triangle(triangle[0], triangle[1], z[0], triangle[2], triangle[3], z[1],
				triangle[4], triangle[5], z[2], FlightGearAirfieldMaterials.SKIRT);
	}

	private double distanceToHull(Polygon hull, double east, double north) {
		return hull.distance(geometryFactory.createPoint(new Coordinate(east, north)));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private Map<FlightGearBucket, List<SurfacePolygon>> splitSurfacesByTile(List<SurfacePolygon> surfaces,
			math.geom2d.Point2D datum) {
		Map<FlightGearBucket, List<SurfacePolygon>> result = new HashMap<>();
		for (SurfacePolygon surface : surfaces) {
			for (Map.Entry<FlightGearBucket, List<Polygon>> entry : splitPolygonByTile(surface.getPolygon(), datum)
					.entrySet()) {
				for (Polygon piece : entry.getValue()) {
					result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
							.add(new SurfacePolygon(piece, surface.getMaterial()));
				}
			}
		}
		return result;
	}

	private Map<FlightGearBucket, List<Polygon>> splitGeometryByTile(Geometry geometry, math.geom2d.Point2D datum) {
		Map<FlightGearBucket, List<Polygon>> result = new HashMap<>();
		for (Polygon polygon : GeomUtils.flatMapToPoly(geometry)) {
			for (Map.Entry<FlightGearBucket, List<Polygon>> entry : splitPolygonByTile(polygon, datum).entrySet()) {
				result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
			}
		}
		return result;
	}

	private Map<FlightGearBucket, List<Polygon>> splitPolygonByTile(Polygon localPolygon, math.geom2d.Point2D datum) {
		Map<FlightGearBucket, List<Polygon>> result = new HashMap<>();
		Polygon geodeticPolygon = toGeodeticPolygon(localPolygon, datum);
		Envelope envelope = geodeticPolygon.getEnvelopeInternal();
		for (FlightGearBucket bucket : bucketsIn(envelope)) {
			Geometry intersection = geodeticPolygon.intersection(bucketRect(bucket));
			for (Polygon piece : GeomUtils.flatMapToPoly(intersection)) {
				Polygon localPiece = toLocalPolygon(piece, datum);
				if (localPiece != null && !localPiece.isEmpty()) {
					result.computeIfAbsent(bucket, k -> new ArrayList<>()).add(localPiece);
				}
			}
		}
		return result;
	}

	private List<FlightGearBucket> bucketsIn(Envelope envelope) {
		List<FlightGearBucket> result = new ArrayList<>();
		int lonStart = (int) Math.floor(envelope.getMinX());
		int lonEnd = (int) Math.floor(envelope.getMaxX());
		int latStart = (int) Math.floor(envelope.getMinY());
		int latEnd = (int) Math.floor(envelope.getMaxY());
		for (int lon = lonStart; lon <= lonEnd; lon++) {
			for (int lat = latStart; lat <= latEnd; lat++) {
				double span = FlightGearBucket.sgBucketSpan(lat + 0.5);
				int cols = span <= 1.0 ? (int) Math.round(1.0 / span) : 1;
				for (int x = 0; x < cols; x++) {
					for (int y = 0; y < 8; y++) {
						double centerLon = lon + x * span + span / 2.0;
						double centerLat = lat + y * FlightGearBucket.SG_BUCKET_SPAN
								+ FlightGearBucket.SG_BUCKET_SPAN / 2.0;
						FlightGearBucket bucket = FlightGearBucket.bucketFor(centerLon, centerLat);
						if (bucket.getMinLon() <= envelope.getMaxX() && bucket.getMaxLon() >= envelope.getMinX()
								&& bucket.getMinLat() <= envelope.getMaxY()
								&& bucket.getMaxLat() >= envelope.getMinY()) {
							result.add(bucket);
						}
					}
				}
			}
		}
		return result;
	}

	private Polygon bucketRect(FlightGearBucket bucket) {
		return geometryFactory.createPolygon(new Coordinate[] { new Coordinate(bucket.getMinLon(), bucket.getMinLat()),
				new Coordinate(bucket.getMaxLon(), bucket.getMinLat()),
				new Coordinate(bucket.getMaxLon(), bucket.getMaxLat()),
				new Coordinate(bucket.getMinLon(), bucket.getMaxLat()),
				new Coordinate(bucket.getMinLon(), bucket.getMinLat()) });
	}

	private Polygon toGeodeticPolygon(Polygon local, math.geom2d.Point2D datum) {
		LinearRing shell = toGeodeticRing(local.getExteriorRing(), datum);
		LinearRing[] holes = new LinearRing[local.getNumInteriorRing()];
		for (int i = 0; i < holes.length; i++) {
			holes[i] = toGeodeticRing(local.getInteriorRingN(i), datum);
		}
		return geometryFactory.createPolygon(shell, holes);
	}

	private LinearRing toGeodeticRing(LineString ring, math.geom2d.Point2D datum) {
		Coordinate[] coordinates = ring.getCoordinates();
		Coordinate[] geodetic = new Coordinate[coordinates.length];
		for (int i = 0; i < coordinates.length; i++) {
			double[] lonLat = FlightGearCoordinateUtils.localToGeodetic(coordinates[i].x, coordinates[i].y, datum.x(),
					datum.y());
			geodetic[i] = new Coordinate(lonLat[0], lonLat[1]);
		}
		return geometryFactory.createLinearRing(geodetic);
	}

	private Polygon toLocalPolygon(Polygon geodetic, math.geom2d.Point2D datum) {
		LinearRing shell = toLocalRing(geodetic.getExteriorRing(), datum);
		LinearRing[] holes = new LinearRing[geodetic.getNumInteriorRing()];
		for (int i = 0; i < holes.length; i++) {
			holes[i] = toLocalRing(geodetic.getInteriorRingN(i), datum);
		}
		return geometryFactory.createPolygon(shell, holes);
	}

	private LinearRing toLocalRing(LineString ring, math.geom2d.Point2D datum) {
		Coordinate[] coordinates = ring.getCoordinates();
		Coordinate[] local = new Coordinate[coordinates.length];
		for (int i = 0; i < coordinates.length; i++) {
			double[] eastNorth = FlightGearCoordinateUtils.toLocal(coordinates[i].x, coordinates[i].y, datum.x(),
					datum.y());
			local[i] = new Coordinate(eastNorth[0], eastNorth[1]);
		}
		return geometryFactory.createLinearRing(local);
	}

	private BtgVector3 center(double lon, double lat, double elevation) {
		double[] ecef = FlightGearCoordinateUtils.geodeticToEcef(lon, lat, elevation);
		return new BtgVector3(ecef[0], ecef[1], ecef[2]);
	}

	private void writeTile(BtgTile merged, FlightGearBucket bucket, File outputSceneryRoot, AirfieldData airfield,
			double elevation) {
		File terrainFolder = new File(new File(outputSceneryRoot, "Terrain"), bucket.genBasePath());
		terrainFolder.mkdirs();
		File outFile = new File(terrainFolder, bucket.getIndex() + ".btg.gz");
		try {
			Btg.write(merged, outFile);
			writeTerrainStg(terrainFolder, bucket.getIndex());
			Osm2xpLogger.info("Generated airfield cut BTG for " + label(airfield) + " into tile " + bucket.getIndex()
					+ " (" + bucket.genBasePath() + "), elevation " + Math.round(elevation) + "m, "
					+ merged.getFaceCount() + " faces");
		} catch (IOException e) {
			Osm2xpLogger.error("Error writing cut terrain BTG " + outFile, e);
		}
	}

	private static void writeTerrainStg(File terrainFolder, long index) throws IOException {
		File stgFile = new File(terrainFolder, index + ".stg");
		Files.write(stgFile.toPath(), ("OBJECT_BASE " + index + ".btg\n").getBytes(StandardCharsets.UTF_8));
	}

	private static String label(AirfieldData airfield) {
		String icao = airfield.getICAO();
		return icao != null && !icao.isEmpty() ? icao : airfield.getId();
	}
}
