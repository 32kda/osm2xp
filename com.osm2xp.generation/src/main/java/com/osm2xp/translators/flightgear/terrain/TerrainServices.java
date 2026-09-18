package com.osm2xp.translators.flightgear.terrain;

import java.util.List;

/**
 * Factory for the terrain-format-specific services (tile locator and downloader).
 * <p>
 * BTG is fully implemented. VPB is a reserved extension point: adding WS3.0
 * support only requires a {@code VpbTileLocator} and a {@code VpbTerrainDownloader}
 * and wiring them into the two factory methods below.
 *
 * @author osm2xp
 */
public final class TerrainServices {

	private TerrainServices() {
	}

	/** Creates the tile locator for the given format. */
	public static TerrainTileLocator createLocator(TerrainFormat format) {
		TerrainFormat effective = format == null ? TerrainFormat.BTG : format;
		switch (effective) {
		case VPB:
			// TODO VPB: vpb/<band>/<cell>/ws_<cell>.osgb (SGBucket::gen_vpb_base)
			throw new UnsupportedOperationException("FlightGear VPB (WS3.0) terrain is not supported yet");
		case BTG:
		default:
			return new BtgTileLocator();
		}
	}

	/** Creates the terrain downloader for the given format. */
	public static AbstractTerrainDownloader createDownloader(TerrainFormat format) {
		return createDownloader(format, null);
	}

	/**
	 * Creates the terrain downloader for the given format.
	 *
	 * @param baseUrls optional mirror list; when {@code null}/empty the built-in
	 *            mirror list with automatic fallback is used
	 */
	public static AbstractTerrainDownloader createDownloader(TerrainFormat format, List<String> baseUrls) {
		TerrainFormat effective = format == null ? TerrainFormat.BTG : format;
		switch (effective) {
		case VPB:
			// TODO VPB: download ws3/vpb/<band>/<cell>.zip and extract into vpb/<band>/<cell>/
			throw new UnsupportedOperationException("FlightGear VPB (WS3.0) terrain download is not supported yet");
		case BTG:
		default:
			if (baseUrls != null && !baseUrls.isEmpty()) {
				return new BtgTerrainDownloader(baseUrls, AbstractTerrainDownloader.DEFAULT_CONCURRENCY);
			}
			return new BtgTerrainDownloader();
		}
	}
}
