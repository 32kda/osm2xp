package com.osm2xp.translators.flightgear;

import java.io.File;
import java.io.IOException;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.paths.PathsService;
import com.osm2xp.utils.FilesUtils;

/**
 * Copies the FlightGear objects bundled with osm2xp (from the <code>flightgear/objects</code> and
 * <code>flightgear/specobjects</code> resource folders) into the FlightGear scenery output root under
 * <code>Models/objects</code> and <code>Models/specobjects</code>.
 *
 * <p>
 * FlightGear resolves <code>OBJECT_SHARED/OBJECT_SHARED_AGL Models/...</code> model paths relative
 * to the scenery root that contains the STG file, so both the generated <code>Objects/</code> and
 * the copied <code>Models/</code> folders must be placed together in the target scenery directory
 * (alongside <code>Terrain/</code>).
 *
 * @author osm2xp
 */
public final class FlightGearModelsProvider {

	/** STG/ model-path prefix for objects copied from the bundle objects folder. */
	public static final String OBJECTS_MODEL_PREFIX = "Models/objects/";

	/** STG/ model-path prefix for objects copied from the bundle specobjects folder. */
	public static final String SPEC_OBJECTS_MODEL_PREFIX = "Models/specobjects/";

	private static final Object COPY_LOCK = new Object();

	private FlightGearModelsProvider() {
	}

	/**
	 * Copies the bundled FlightGear objects and specobjects resource folders into
	 * <code>&lt;sceneryRoot&gt;/Models/objects</code> and
	 * <code>&lt;sceneryRoot&gt;/Models/specobjects</code> if they are not already present. Calling
	 * this repeatedly (e.g. once per tile) is safe: existing folders are left untouched.
	 *
	 * @param sceneryRoot the FlightGear scenery output root
	 */
	public static void ensureModelsCopied(File sceneryRoot) {
		if (sceneryRoot == null) {
			return;
		}
		synchronized (COPY_LOCK) {
			copyIfMissing(PathsService.getPathsProvider().getFlightGearObjectsFolder(),
					new File(sceneryRoot, "Models/objects"));
			copyIfMissing(PathsService.getPathsProvider().getFlightGearSpecObjectsFolder(),
					new File(sceneryRoot, "Models/specobjects"));
		}
	}

	private static void copyIfMissing(File source, File target) {
		if (!source.isDirectory()) {
			Osm2xpLogger.error("FlightGear objects folder not present in resources dir: "
					+ source + " - objects will not be copied");
			return;
		}
		if (target.isDirectory()) {
			return;
		}
		try {
			target.getParentFile().mkdirs();
			target.mkdirs();
			FilesUtils.copyDirectory(source, target, false);
			Osm2xpLogger.info("Copied FlightGear objects to " + target);
		} catch (IOException e) {
			Osm2xpLogger.error("Error copying FlightGear objects into " + target, e);
		}
	}
}