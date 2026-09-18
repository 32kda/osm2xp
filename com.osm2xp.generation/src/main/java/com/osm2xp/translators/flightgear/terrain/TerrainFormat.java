package com.osm2xp.translators.flightgear.terrain;

import java.util.Locale;

/**
 * FlightGear terrain tile format.
 * <p>
 * <ul>
 * <li>{@link #BTG} - legacy World Scenery 2.0 terrain: one compressed BTG tile
 * per sub-bucket, distributed under the {@code Terrain/} tree.</li>
 * <li>{@link #VPB} - World Scenery 3.0 terrain: OSGB tiles distributed as one
 * {@code vpb/<band>/<cell>.zip} archive per 1-degree cell (not implemented yet).</li>
 * </ul>
 *
 * @author osm2xp
 */
public enum TerrainFormat {

	/** Legacy WS2.0 BTG tiles. */
	BTG,

	/** WS3.0 "VPB" OSGB tiles (reserved - not implemented yet). */
	VPB;

	/** Parses a format name, defaulting to {@link #BTG} for unknown values. */
	public static TerrainFormat fromString(String value) {
		if (value == null || value.trim().isEmpty()) {
			return BTG;
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return BTG;
		}
	}
}
