package com.osm2xp.translators.flightgear;

import java.io.File;

import com.osm2xp.spatial.TileSpatialIndex;
import com.osm2xp.translators.flightgear.spatial.TerrainSpatialIndexService;

/**
 * In-process terrain elevation index over the local FlightGear scenery.
 * <p>
 * Thin wrapper around a {@link TerrainSpatialIndexService}: tiles are indexed
 * with a per-tile spatial hash over triangles (see
 * {@code TileSpatialIndex}), which answers elevation queries by barycentric
 * interpolation inside the containing triangle instead of a kd-tree
 * nearest-vertex lookup.
 * <p>
 * Prefer the run-scoped shared service ({@link TerrainSpatialIndexService#shared()})
 * so buildings, airfields and vegetation reuse one index per tile; this class
 * creates its own private service for standalone use (e.g. tests).
 *
 * @author osm2xp
 */
public class FlightGearElevationIndex {

	private final TerrainSpatialIndexService service;

	public FlightGearElevationIndex(File terrainRoot) {
		this(terrainRoot, TerrainSpatialIndexService.DEFAULT_CACHE_SIZE);
	}

	public FlightGearElevationIndex(File terrainRoot, int cacheSize) {
		this.service = new TerrainSpatialIndexService(terrainRoot, cacheSize, TileSpatialIndex.DEFAULT_GRID);
	}

	public FlightGearElevationIndex(TerrainSpatialIndexService service) {
		this.service = service;
	}

	public boolean isDisabled() {
		return service == null || service.isDisabled();
	}

	/**
	 * Returns the terrain elevation (metres) at the given global lon/lat, or
	 * {@link Double#NaN} if no terrain tile covers the point.
	 */
	public double probe(double lon, double lat) {
		return service == null ? Double.NaN : service.probeElevation(lon, lat);
	}

	/** The underlying shared spatial index service. */
	public TerrainSpatialIndexService service() {
		return service;
	}
}
