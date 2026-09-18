package com.osm2xp.translators.flightgear.terrain;

import java.io.File;

import com.osm2xp.translators.flightgear.FlightGearBucket;

/**
 * BTG (WS2.0) tile locator: {@code <terrainDir>/<band>/<cell>/<bucketIndex>.btg.gz}
 * (the TerraSync on-disk layout, with {@code terrainDir} being a {@code Terrain}
 * directory - either the {@code source_tiles} cache or the generated scenery's
 * {@code Terrain/}).
 *
 * @author osm2xp
 */
public class BtgTileLocator implements TerrainTileLocator {

	@Override
	public File tileFileFor(double lon, double lat, File terrainDir) {
		return tileFileFor(FlightGearBucket.bucketFor(lon, lat), terrainDir);
	}

	/** Locates the tile file for an already resolved bucket. */
	public File tileFileFor(FlightGearBucket bucket, File terrainDir) {
		if (terrainDir == null) {
			return null;
		}
		String cellPath = bucket.genBasePath().replace('/', File.separatorChar);
		return new File(new File(terrainDir, cellPath), bucket.getIndex() + ".btg.gz");
	}
}
