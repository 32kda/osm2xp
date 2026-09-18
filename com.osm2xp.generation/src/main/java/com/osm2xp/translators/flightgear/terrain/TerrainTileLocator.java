package com.osm2xp.translators.flightgear.terrain;

import java.io.File;

/**
 * Resolves the terrain tile file that covers a coordinate, for a given terrain
 * root (a scenery root that contains the format-specific terrain subtree, e.g.
 * {@code Terrain/} for BTG or {@code vpb/} for VPB).
 * <p>
 * This is the format-specific seam between the elevation prober and the terrain
 * layout: BTG locates one file per sub-bucket, while the future VPB
 * implementation will locate the OSGB root tile of the 1-degree cell.
 *
 * @author osm2xp
 */
public interface TerrainTileLocator {

	/**
	 * Returns the terrain tile file covering the given WGS84 coordinate below
	 * {@code terrainRoot}, or {@code null} when the coordinate cannot be mapped.
	 * The returned file is not guaranteed to exist.
	 */
	File tileFileFor(double lon, double lat, File terrainRoot);
}
