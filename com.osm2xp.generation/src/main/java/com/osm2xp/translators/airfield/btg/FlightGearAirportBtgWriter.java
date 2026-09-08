package com.osm2xp.translators.airfield.btg;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.locationtech.jts.geom.Envelope;

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

import math.geom2d.Point2D;
import math.geom2d.line.Line2D;

/**
 * Bakes a single airfield's visual geometry into the matching terrain BTG tile
 * (runways, taxiways, aprons, helipads and the grass skirt). Unlike the earlier
 * {@code OBJECT_BASE} approach, this merges the airport into the terrain mesh so
 * the surrounding ground is preserved.
 * <p>
 * Only airports fully contained in one sub-bucket tile are processed; airports
 * spanning multiple tiles are skipped for now. The airport is raised to
 * {@link #ELEVATION_MARGIN_M} metres above the highest probed terrain point
 * among the runway corners, centre and mid-long-side points, so it stays
 * guaranteed above ground.
 */
public class FlightGearAirportBtgWriter {

	private static final double ELEVATION_MARGIN_M = 3.0;

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

		FlightGearBucket bucket = singleBucket(surfaces, datum);
		if (bucket == null) {
			Osm2xpLogger.info("Skipping airfield " + label(airfield) + ": spans multiple terrain tiles");
			return;
		}

		BtgTile terrain = prober.getTile(bucket);
		if (terrain == null) {
			Osm2xpLogger.info("Skipping airfield " + label(airfield) + ": no terrain tile " + bucket.getIndex());
			return;
		}

		double elevation = probeAirportElevation(airfield, prober, terrain);

		double[] centerEcef = FlightGearCoordinateUtils.geodeticToEcef(datum.x(), datum.y(), elevation);
		BtgVector3 center = new BtgVector3(centerEcef[0], centerEcef[1], centerEcef[2]);
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

	private FlightGearBucket singleBucket(List<SurfacePolygon> surfaces, Point2D datum) {
		Envelope envelope = new Envelope();
		for (SurfacePolygon surface : surfaces) {
			envelope.expandToInclude(surface.getPolygon().getEnvelopeInternal());
		}
		double[][] corners = { { envelope.getMinX(), envelope.getMinY() },
				{ envelope.getMaxX(), envelope.getMinY() }, { envelope.getMaxX(), envelope.getMaxY() },
				{ envelope.getMinX(), envelope.getMaxY() } };
		long index = -1;
		for (double[] corner : corners) {
			double[] lonLat = FlightGearCoordinateUtils.localToGeodetic(corner[0], corner[1], datum.x(), datum.y());
			FlightGearBucket bucket = FlightGearBucket.bucketFor(lonLat[0], lonLat[1]);
			if (index == -1) {
				index = bucket.getIndex();
			} else if (index != bucket.getIndex()) {
				return null;
			}
		}
		return FlightGearBucket.bucketFor(datum.x(), datum.y());
	}

	private double probeAirportElevation(AirfieldData airfield, FlightGearTerrainElevationProber prober, BtgTile terrain) {
		Point2D datum = airfield.getDatum();
		double max = Double.NaN;
		for (RunwayData runway : airfield.getUniqueRunways()) {
			for (double[] point : runwayProbePoints(runway)) {
				double elevation = FlightGearTerrainElevationProber.probe(terrain, point[0], point[1]);
				if (!Double.isNaN(elevation)) {
					max = Double.isNaN(max) ? elevation : Math.max(max, elevation);
				}
			}
		}
		if (Double.isNaN(max)) {
			max = FlightGearTerrainElevationProber.probe(terrain, datum.x(), datum.y());
			if (Double.isNaN(max)) {
				max = airfield.getElevation();
			}
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
