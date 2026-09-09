package com.osm2xp.translators.airfield.btg;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.core.parsers.btg.BtgVector3;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.translators.airfield.AirfieldData;
import com.osm2xp.translators.airfield.RunwayData;
import com.osm2xp.translators.flightgear.FlightGearBucket;
import com.osm2xp.translators.flightgear.FlightGearCoordinateUtils;
import com.osm2xp.translators.flightgear.FlightGearTerrainElevationProber;
import com.osm2xp.utils.geometry.GeomUtils;

import math.geom2d.Point2D;
import math.geom2d.line.Line2D;

/**
 * Bakes a single airfield's visual geometry into the matching terrain BTG tile
 * (runways, taxiways, aprons, helipads and the grass skirt). Unlike the earlier
 * {@code OBJECT_BASE} approach, this merges the airport into the terrain mesh so
 * the surrounding ground is preserved.
 * <p>
 * The airport surfaces are split along the FlightGear sub-bucket tile boundaries
 * (using JTS intersection) so an airport spanning multiple terrain tiles is baked
 * into each of them separately. The airport is raised to {@link #ELEVATION_MARGIN_M}
 * metres above the highest probed terrain point among the runway corners, centre
 * and mid-long-side points, so it stays guaranteed above ground.
 */
public class FlightGearAirportBtgWriter {

	private static final double ELEVATION_MARGIN_M = 0.3;

	private final GeometryFactory geometryFactory = new GeometryFactory();

	public void write(AirfieldData airfield, File sceneryRoot) {
		String terrainRoot = FlightGearOptionsProvider.getOptions().getFlightGearSceneryPath();
		FlightGearTerrainElevationProber prober = new FlightGearTerrainElevationProber(
				StringUtils.isNotBlank(terrainRoot) ? new File(terrainRoot) : null);
		if (!prober.isAvailable()) {
			return;
		}

		Point2D datum = airfield.getDatum();

		List<SurfacePolygon> surfaces = new AirfieldGeometryBuilder().build(airfield);
		if (surfaces.isEmpty()) {
			return;
		}

		Map<FlightGearBucket, List<SurfacePolygon>> byTile = splitByTile(surfaces, datum);

		double elevation = computeElevation(airfield, prober, datum);

		double[] centerEcef = FlightGearCoordinateUtils.geodeticToEcef(datum.x(), datum.y(), elevation);
		BtgVector3 center = new BtgVector3(centerEcef[0], centerEcef[1], centerEcef[2]);

		for (Map.Entry<FlightGearBucket, List<SurfacePolygon>> entry : byTile.entrySet()) {
			writeTile(airfield, sceneryRoot, prober, datum, elevation, center, entry.getKey(), entry.getValue());
		}
	}

	/**
	 * Splits each surface polygon along the FlightGear sub-bucket tile boundaries and
	 * groups the resulting pieces by tile. The pieces stay in the datum's local
	 * east/north metre frame, so they can be fed directly to the BTG assembler.
	 */
	private Map<FlightGearBucket, List<SurfacePolygon>> splitByTile(List<SurfacePolygon> surfaces, Point2D datum) {
		Map<FlightGearBucket, List<SurfacePolygon>> byTile = new HashMap<>();
		for (SurfacePolygon surface : surfaces) {
			Polygon geodeticPolygon = toGeodeticPolygon(surface.getPolygon(), datum);
			Envelope envelope = geodeticPolygon.getEnvelopeInternal();
			for (FlightGearBucket bucket : bucketsIn(envelope)) {
				Geometry intersection = geodeticPolygon.intersection(bucketRect(bucket));
				for (Polygon piece : GeomUtils.flatMapToPoly(intersection)) {
					Polygon localPiece = toLocalPolygon(piece, datum);
					if (localPiece != null && !localPiece.isEmpty()) {
						byTile.computeIfAbsent(bucket, k -> new ArrayList<>())
								.add(new SurfacePolygon(localPiece, surface.getMaterial()));
					}
				}
			}
		}
		return byTile;
	}

	/** Enumerates the FlightGear sub-buckets overlapping the given envelope. */
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
								&& bucket.getMinLat() <= envelope.getMaxY() && bucket.getMaxLat() >= envelope.getMinY()) {
							result.add(bucket);
						}
					}
				}
			}
		}
		return result;
	}

	/** Tile bounds as a rectangle in planar lon/lat space (x=lon, y=lat). */
	private Polygon bucketRect(FlightGearBucket bucket) {
		return geometryFactory.createPolygon(new Coordinate[] {
				new Coordinate(bucket.getMinLon(), bucket.getMinLat()),
				new Coordinate(bucket.getMaxLon(), bucket.getMinLat()),
				new Coordinate(bucket.getMaxLon(), bucket.getMaxLat()),
				new Coordinate(bucket.getMinLon(), bucket.getMaxLat()),
				new Coordinate(bucket.getMinLon(), bucket.getMinLat()) });
	}

	private Polygon toGeodeticPolygon(Polygon local, Point2D datum) {
		LinearRing shell = toGeodeticRing(local.getExteriorRing(), datum);
		LinearRing[] holes = new LinearRing[local.getNumInteriorRing()];
		for (int i = 0; i < holes.length; i++) {
			holes[i] = toGeodeticRing(local.getInteriorRingN(i), datum);
		}
		return geometryFactory.createPolygon(shell, holes);
	}

	private LinearRing toGeodeticRing(LineString ring, Point2D datum) {
		Coordinate[] coords = ring.getCoordinates();
		Coordinate[] geodetic = new Coordinate[coords.length];
		for (int i = 0; i < coords.length; i++) {
			double[] lonLat = FlightGearCoordinateUtils.localToGeodetic(coords[i].x, coords[i].y, datum.x(), datum.y());
			geodetic[i] = new Coordinate(lonLat[0], lonLat[1]);
		}
		return geometryFactory.createLinearRing(geodetic);
	}

	private Polygon toLocalPolygon(Polygon geodetic, Point2D datum) {
		LinearRing shell = toLocalRing(geodetic.getExteriorRing(), datum);
		LinearRing[] holes = new LinearRing[geodetic.getNumInteriorRing()];
		for (int i = 0; i < holes.length; i++) {
			holes[i] = toLocalRing(geodetic.getInteriorRingN(i), datum);
		}
		return geometryFactory.createPolygon(shell, holes);
	}

	private LinearRing toLocalRing(LineString ring, Point2D datum) {
		Coordinate[] coords = ring.getCoordinates();
		Coordinate[] local = new Coordinate[coords.length];
		for (int i = 0; i < coords.length; i++) {
			double[] eastNorth = FlightGearCoordinateUtils.toLocal(coords[i].x, coords[i].y, datum.x(), datum.y());
			local[i] = new Coordinate(eastNorth[0], eastNorth[1]);
		}
		return geometryFactory.createLinearRing(local);
	}

	private void writeTile(AirfieldData airfield, File sceneryRoot, FlightGearTerrainElevationProber prober,
			Point2D datum, double elevation, BtgVector3 center, FlightGearBucket bucket,
			List<SurfacePolygon> surfaces) {
		BtgTile terrain = prober.getTile(bucket);
		if (terrain == null) {
			Osm2xpLogger.info("Skipping airfield " + label(airfield) + ": no terrain tile " + bucket.getIndex());
			return;
		}

		BtgTile overlay = new AirfieldBtgAssembler().assemble(surfaces, elevation, center, datum.x(), datum.y());
		if (overlay == null) {
			return;
		}

		BtgTile merged = BtgTileMerger.merge(terrain, overlay);

		File terrainFolder = new File(new File(sceneryRoot, "Terrain"), bucket.genBasePath());
		terrainFolder.mkdirs();
		File outFile = new File(terrainFolder, bucket.getIndex() + ".btg.gz");
		try {
			Btg.write(merged, outFile);
			writeTerrainStg(terrainFolder, bucket.getIndex());
			Osm2xpLogger.info("Generated airfield BTG for " + label(airfield) + " into tile " + bucket.getIndex()
					+ " (" + bucket.genBasePath() + "), elevation " + Math.round(elevation) + "m, "
					+ merged.getFaceCount() + " faces");
		} catch (IOException e) {
			Osm2xpLogger.error("Error writing merged terrain BTG " + outFile, e);
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

	/**
	 * Computes the single airport elevation (max terrain height + margin) across all
	 * runway probe points, each probed against the terrain tile that contains it.
	 */
	private double computeElevation(AirfieldData airfield, FlightGearTerrainElevationProber prober, Point2D datum) {
		double max = Double.NaN;
		for (RunwayData runway : airfield.getUniqueRunways()) {
			for (double[] point : runwayProbePoints(runway)) {
				FlightGearBucket bucket = FlightGearBucket.bucketFor(point[0], point[1]);
				double elevation = FlightGearTerrainElevationProber.probe(prober.getTile(bucket), point[0], point[1]);
				if (!Double.isNaN(elevation)) {
					max = Double.isNaN(max) ? elevation : Math.max(max, elevation);
				}
			}
		}
		if (Double.isNaN(max)) {
			FlightGearBucket datumBucket = FlightGearBucket.bucketFor(datum.x(), datum.y());
			double elevation = FlightGearTerrainElevationProber.probe(prober.getTile(datumBucket), datum.x(), datum.y());
			max = Double.isNaN(elevation) ? airfield.getElevation() : elevation;
		}
		return max + ELEVATION_MARGIN_M;
	}

	private List<double[]> runwayProbePoints(RunwayData runway) {
		List<double[]> result = new ArrayList<>();
		Line2D line = runway.getRunwayLine();
		double anchorLon = (line.p1.x() + line.p2.x()) / 2.0;
		double anchorLat = (line.p1.y() + line.p2.y()) / 2.0;
		double[] p1 = FlightGearCoordinateUtils.toLocal(line.p1.x(), line.p1.y(), anchorLon, anchorLat);
		double[] p2 = FlightGearCoordinateUtils.toLocal(line.p2.x(), line.p2.y(), anchorLon, anchorLat);
		double dx = p2[0] - p1[0];
		double dy = p2[1] - p1[1];
		double length = Math.hypot(dx, dy);
		if (length < 1e-9) {
			return result;
		}
		double nx = -dy / length;
		double ny = dx / length;
		double halfWidth = runway.getWidth() / 2.0;
		double cx = (p1[0] + p2[0]) / 2.0;
		double cy = (p1[1] + p2[1]) / 2.0;
		double[][] local = {
				{ p1[0] + nx * halfWidth, p1[1] + ny * halfWidth },
				{ p1[0] - nx * halfWidth, p1[1] - ny * halfWidth },
				{ p2[0] + nx * halfWidth, p2[1] + ny * halfWidth },
				{ p2[0] - nx * halfWidth, p2[1] - ny * halfWidth },
				{ cx, cy },
				{ cx + nx * halfWidth, cy + ny * halfWidth },
				{ cx - nx * halfWidth, cy - ny * halfWidth } };
		for (double[] point : local) {
			result.add(FlightGearCoordinateUtils.localToGeodetic(point[0], point[1], anchorLon, anchorLat));
		}
		return result;
	}
}
