package com.osm2xp.translators.flightgear.terrain;

import java.io.File;

/**
 * Run-scoped terrain locations for one generation run.
 * <ul>
 * <li>{@code cacheDir} - the {@code source_tiles} cache of original tiles, which
 * are downloaded at most once and reused on later runs;</li>
 * <li>{@code simSceneryRoot} - optional user-provided scenery root (the
 * configured {@code flightGearSceneryPath}) used as a local source before
 * downloading;</li>
 * <li>{@code outputTerrainDir} - the generated scenery's {@code Terrain/}
 * directory, into which every tile of the requested area is copied (patched or
 * not).</li>
 * </ul>
 *
 * @author osm2xp
 */
public class TerrainPaths {

	private final File cacheDir;
	private final File simSceneryRoot;
	private final File outputTerrainDir;

	public TerrainPaths(File cacheDir, File simSceneryRoot, File outputTerrainDir) {
		this.cacheDir = cacheDir;
		this.simSceneryRoot = simSceneryRoot;
		this.outputTerrainDir = outputTerrainDir;
	}

	/** The {@code source_tiles} cache directory (may be {@code null}). */
	public File getCacheDir() {
		return cacheDir;
	}

	/** Optional user scenery root to copy tiles from (may be {@code null}). */
	public File getSimSceneryRoot() {
		return simSceneryRoot;
	}

	/** The generated scenery's {@code Terrain/} directory (may be {@code null}). */
	public File getOutputTerrainDir() {
		return outputTerrainDir;
	}

	/** The {@code Terrain/} directory of the user scenery root, or {@code null}. */
	public File getSimTerrainDir() {
		return simSceneryRoot == null ? null : new File(simSceneryRoot, "Terrain");
	}
}
