package com.osm2xp.translators.flightgear;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.osm2xp.core.logging.Osm2xpLogger;

/**
 * Detects airfields that are already baked into downloaded FlightGear terrain.
 * <p>
 * TerraSync (and other terrain providers) store an airport's terrain as a
 * per-airport BTG tile named after the airport identifier, e.g.
 * {@code Terrain/e080n50/e080n55/UNNT.btg.gz} or {@code UNNT.btg}. This scanner
 * walks a scenery root and collects those identifiers, so generation can skip
 * airfields that are already present.
 *
 * @author osm2xp
 */
public final class FlightGearExistingAirfields {

	private static final Pattern AIRPORT_BTG = Pattern.compile("^([A-Za-z0-9]{4})\\.btg(\\.gz)?$");

	private FlightGearExistingAirfields() {
	}

	/**
	 * Scans the given scenery root for airport BTG tiles and returns the set of
	 * upper-cased airport identifiers already present. Only the {@code Terrain}
	 * subtree is scanned when it exists, otherwise the whole root is walked.
	 */
	public static Set<String> findExisting(File sceneryRoot) {
		if (sceneryRoot == null || !sceneryRoot.isDirectory()) {
			return Collections.emptySet();
		}
		File terrainRoot = new File(sceneryRoot, "Terrain");
		File scanRoot = terrainRoot.isDirectory() ? terrainRoot : sceneryRoot;
		Set<String> idents = new TreeSet<>();
		collect(scanRoot, idents);
		if (!idents.isEmpty()) {
			Osm2xpLogger.info("Found " + idents.size()
					+ " airfield(s) already present in downloaded terrain: " + idents);
		}
		return idents;
	}

	private static void collect(File folder, Set<String> idents) {
		File[] children = folder.listFiles();
		if (children == null) {
			return;
		}
		for (File child : children) {
			if (child.isDirectory()) {
				collect(child, idents);
			} else {
				Matcher matcher = AIRPORT_BTG.matcher(child.getName());
				if (matcher.matches()) {
					idents.add(matcher.group(1).toUpperCase(Locale.ROOT));
				}
			}
		}
	}
}
