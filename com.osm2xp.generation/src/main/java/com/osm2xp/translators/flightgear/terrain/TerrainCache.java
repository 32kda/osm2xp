package com.osm2xp.translators.flightgear.terrain;

import java.io.File;

import com.osm2xp.core.logging.Osm2xpLogger;

/**
 * Resolves the {@code source_tiles} cache directory. The cache holds the
 * original terrain tiles so they are downloaded at most once; preferred
 * locations are, in order:
 * <ol>
 * <li>{@code <workingFolder>/source_tiles},</li>
 * <li>{@code <sourceFileFolder>/source_tiles},</li>
 * <li>{@code <OS temp>/osm2xp_source_tiles}.</li>
 * </ol>
 *
 * @author osm2xp
 */
public final class TerrainCache {

	public static final String CACHE_DIR_NAME = "source_tiles";

	private TerrainCache() {
	}

	/**
	 * Resolves a writable cache directory.
	 *
	 * @param workingFolder the generation output folder
	 * @param sourceFileFolder the folder containing the input OSM file (may be
	 *            {@code null})
	 */
	public static File resolveCacheDir(File workingFolder, File sourceFileFolder) {
		File primary = workingFolder == null ? null : new File(workingFolder, CACHE_DIR_NAME);
		if (isWritableDir(primary)) {
			return primary;
		}
		File secondary = sourceFileFolder == null ? null : new File(sourceFileFolder, CACHE_DIR_NAME);
		if (isWritableDir(secondary)) {
			Osm2xpLogger.info("Working folder is not writable, using terrain cache " + secondary);
			return secondary;
		}
		File temp = new File(System.getProperty("java.io.tmpdir"), "osm2xp_" + CACHE_DIR_NAME);
		if (isWritableDir(temp)) {
			Osm2xpLogger.info("Using temporary terrain cache " + temp);
			return temp;
		}
		Osm2xpLogger.warning("No writable terrain cache directory found, falling back to " + primary);
		return primary;
	}

	private static boolean isWritableDir(File dir) {
		if (dir == null) {
			return false;
		}
		if (!dir.exists() && !dir.mkdirs()) {
			return false;
		}
		return dir.isDirectory() && dir.canWrite();
	}
}
