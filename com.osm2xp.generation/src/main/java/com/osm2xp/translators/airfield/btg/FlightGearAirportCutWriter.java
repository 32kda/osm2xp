package com.osm2xp.translators.airfield.btg;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.translators.airfield.AirfieldData;
import com.osm2xp.translators.flightgear.FlightGearBucket;
import com.osm2xp.translators.flightgear.FlightGearTerrainElevationProber;
import com.osm2xp.translators.flightgear.spatial.TerrainSpatialIndexService;
import com.osm2xp.utils.geometry.GeomUtils;

/**
 * Bakes an airfield into the FlightGear terrain by cutting a hole in the terrain
 * BTG mesh and filling it with a flat airfield plate plus a sloped transition
 * skirt.
 * <p>
 * This class only orchestrates the pipeline; the work is delegated to focused
 * collaborators: {@link AirfieldCutFootprint} (footprint), {@link AirfieldElevationProbe}
 * (elevation), {@link AirfieldTileCutter} (terrain cut), {@link AirfieldOverlayBuilder}
 * (plate + skirt), {@link FlightGearTileSplitter} (tile splitting) and
 * {@link BtgTileWriter} (output).
 */
public class FlightGearAirportCutWriter {

	/** Extra plate height in overlay mode, so it sits just above the terrain. */
	private static final double OVERLAY_ELEVATION_MARGIN_M = 0.3;

	private final AirfieldTileCutter tileCutter = new AirfieldTileCutter();
	private final AirfieldOverlayBuilder overlayBuilder = new AirfieldOverlayBuilder();
	private final BtgTileWriter tileWriter = new BtgTileWriter();

	/** Bakes the airfield, cutting the terrain. */
	public void write(AirfieldData airfield, File sourceTerrainDir, File outputSceneryRoot) {
		write(airfield, sourceTerrainDir, outputSceneryRoot, true);
	}

	/**
	 * Bakes the airfield.
	 *
	 * @param sourceTerrainDir  the {@code Terrain} directory holding the original
	 *                          tiles, used for elevation probing
	 * @param outputSceneryRoot the generated scenery root; the patched tile is
	 *                          written to {@code <root>/Terrain/...}
	 * @param cutTerrain        {@code true} to cut a hole in the terrain and fill it
	 *                          with the plate + skirt; {@code false} to leave the
	 *                          terrain untouched and only overlay the plate + skirt
	 */
	public void write(AirfieldData airfield, File sourceTerrainDir, File outputSceneryRoot, boolean cutTerrain) {
		for (Map.Entry<FlightGearBucket, BtgTile> entry : buildPatchedTiles(airfield, sourceTerrainDir, cutTerrain)
				.entrySet()) {
			tileWriter.write(entry.getValue(), entry.getKey(), outputSceneryRoot, label(airfield), 0.0);
		}
	}

	/**
	 * Computes the patched terrain tiles for the given airfield, keyed by bucket,
	 * without writing them. This is the BTG patch pipeline entry point: the result
	 * is composed with later patch phases (e.g. vegetation) by
	 * {@link com.osm2xp.translators.airfield.btg.AirfieldBtgPatcher}.
	 */
	public Map<FlightGearBucket, BtgTile> buildPatchedTiles(AirfieldData airfield, File sourceTerrainDir,
			boolean cutTerrain) {
		Map<FlightGearBucket, BtgTile> result = new HashMap<>();
		TerrainSpatialIndexService shared = TerrainSpatialIndexService.shared();
		FlightGearTerrainElevationProber prober = shared != null && !shared.isDisabled()
				? new FlightGearTerrainElevationProber(shared)
				: new FlightGearTerrainElevationProber(sourceTerrainDir);
		if (!prober.isAvailable()) {
			return result;
		}

		List<SurfacePolygon> surfaces = new AirfieldGeometryBuilder().build(airfield);
		if (surfaces.isEmpty()) {
			return result;
		}

		AirfieldCutFootprint footprint = AirfieldCutFootprint.of(surfaces);
		if (footprint == null) {
			return result;
		}

		math.geom2d.Point2D datum = airfield.getDatum();
		FlightGearTileSplitter splitter = new FlightGearTileSplitter(new GeometryFactory(), datum);
		AirfieldElevationProbe elevationProbe = new AirfieldElevationProbe(splitter.converter());
		BtgCsgConverter csgConverter = new BtgCsgConverter(datum.x(), datum.y());

		double[] minMax = elevationProbe.probe(prober, footprint.cutPolygon());
		if (Double.isNaN(minMax[0]) || Double.isNaN(minMax[1])) {
			Osm2xpLogger.info("Skipping airfield cut " + label(airfield) + ": no terrain found");
			return result;
		}
		double elevation = minMax[1];
		double plateElevation = cutTerrain ? elevation : elevation + OVERLAY_ELEVATION_MARGIN_M;

		List<SurfacePolygon> plateSurfaces = plateSurfaces(surfaces, footprint.hull());
		Map<FlightGearBucket, List<SurfacePolygon>> plateByTile = splitter.splitSurfaces(plateSurfaces);
		Set<FlightGearBucket> buckets = splitter.bucketsFor(footprint.cutPolygon());

		if (cutTerrain) {
			buildCutTiles(csgConverter, prober, footprint, plateByTile, buckets, elevation, result);
		} else {
			buildOverlayTiles(csgConverter, prober, splitter, elevationProbe, footprint, plateByTile, buckets,
					plateElevation, result);
		}
		return result;
	}

	/** Cut mode: remove the terrain under the airfield and fill it with plate + skirt. */
	private void buildCutTiles(BtgCsgConverter csgConverter, FlightGearTerrainElevationProber prober,
			AirfieldCutFootprint footprint, Map<FlightGearBucket, List<SurfacePolygon>> plateByTile,
			Set<FlightGearBucket> buckets, double elevation, Map<FlightGearBucket, BtgTile> result) {
		Map<FlightGearBucket, AirfieldTileCutter.TileCut> cuts = new HashMap<>();
		for (FlightGearBucket bucket : buckets) {
			BtgTile terrain = prober.getTile(bucket);
			if (terrain == null) {
				continue;
			}
			AirfieldTileCutter.TileCut cut = tileCutter.cut(csgConverter, terrain, footprint.cutPolygon());
			if (cut != null && !cut.removedFaces().isEmpty()) {
				cuts.put(bucket, cut);
			}
		}
		for (Map.Entry<FlightGearBucket, AirfieldTileCutter.TileCut> entry : cuts.entrySet()) {
			FlightGearBucket bucket = entry.getKey();
			AirfieldTileCutter.TileCut cut = entry.getValue();
			List<BtgCsgConverter.Triangle> overlay = new ArrayList<>();
			overlay.addAll(overlayBuilder.plate(plateByTile.getOrDefault(bucket, new ArrayList<>()), elevation));
			overlay.addAll(overlayBuilder.cutSkirt(cut.removedFaces(), footprint.hull(), footprint.skirtWidthM(),
					elevation));
			// Keep the untouched faces; plate + skirt fill the removed hole.
			result.put(bucket,
					csgConverter.rebuild(cut.terrain(), cut.keptFaces(), new ArrayList<>(), overlay));
		}
	}

	/** Overlay mode: keep the terrain and lay the plate + smooth skirt on top. */
	private void buildOverlayTiles(BtgCsgConverter csgConverter, FlightGearTerrainElevationProber prober,
			FlightGearTileSplitter splitter, AirfieldElevationProbe elevationProbe, AirfieldCutFootprint footprint,
			Map<FlightGearBucket, List<SurfacePolygon>> plateByTile, Set<FlightGearBucket> buckets,
			double plateElevation, Map<FlightGearBucket, BtgTile> result) {
		Geometry skirtRing = footprint.cutPolygon().difference(footprint.hull());
		Map<FlightGearBucket, List<Polygon>> skirtByTile = splitter.splitGeometry(skirtRing);
		for (FlightGearBucket bucket : buckets) {
			BtgTile terrain = prober.getTile(bucket);
			if (terrain == null) {
				continue;
			}
			List<BtgCsgConverter.Triangle> overlay = new ArrayList<>();
			overlay.addAll(overlayBuilder.plate(plateByTile.getOrDefault(bucket, new ArrayList<>()), plateElevation));
			overlay.addAll(overlayBuilder.smoothSkirt(skirtByTile.getOrDefault(bucket, new ArrayList<>()),
					footprint.hull(), footprint.skirtWidthM(), plateElevation, elevationProbe, prober));
			result.put(bucket, csgConverter.rebuild(terrain, terrain.getFaces(), new ArrayList<>(), overlay));
		}
	}

	/** The plate = the airfield surfaces plus the hull area they don't cover. */
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

	private static String label(AirfieldData airfield) {
		String icao = airfield.getICAO();
		return icao != null && !icao.isEmpty() ? icao : airfield.getId();
	}
}
