package com.osm2xp.translators.flightgear.terrain;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.paths.PathsService;

/**
 * Remembers the last TerraSync mirror that answered, so the next run starts
 * from it. Stored as a small text file under
 * {@code <basicFolder>/flightgear/terrain_mirror.txt}.
 *
 * @author osm2xp
 */
public final class TerrainMirrorStore {

	private static final String FILE_NAME = "terrain_mirror.txt";

	private TerrainMirrorStore() {
	}

	/** The last successful mirror, or {@code null} when unknown. */
	public static String load() {
		try {
			File file = storeFile();
			if (file != null && file.isFile()) {
				String value = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
				return value.isEmpty() ? null : value;
			}
		} catch (IOException | RuntimeException e) {
			// best effort
		}
		return null;
	}

	/** Persists the mirror that was just used successfully. */
	public static void save(String mirror) {
		if (mirror == null || mirror.trim().isEmpty()) {
			return;
		}
		try {
			File file = storeFile();
			if (file != null) {
				file.getParentFile().mkdirs();
				Files.write(file.toPath(), mirror.trim().getBytes(StandardCharsets.UTF_8));
			}
		} catch (IOException | RuntimeException e) {
			Osm2xpLogger.warning("Unable to store last terrain mirror: " + e.getMessage());
		}
	}

	private static File storeFile() {
		File basicFolder = PathsService.getPathsProvider().getBasicFolder();
		return basicFolder == null ? null : new File(new File(basicFolder, "flightgear"), FILE_NAME);
	}
}
