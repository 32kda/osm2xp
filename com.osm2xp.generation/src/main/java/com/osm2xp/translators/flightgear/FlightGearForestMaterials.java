package com.osm2xp.translators.flightgear;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * FlightGear land-class material names used for OSM-derived vegetation.
 * <p>
 * These are the standard TerraGear/FlightGear vegetation land-class names that
 * TerraSync WS2.0 tiles carry (verified against the WestSiberia scenery: the
 * forest land classes there are {@code MixedForest}, {@code DeciduousForest} and
 * {@code EvergreenForest}). The same names are used both when writing the
 * generated {@code SG_POINTS} point groups and when recognising existing
 * land-class forest for the overlap exclusion.
 *
 * @author osm2xp
 */
public final class FlightGearForestMaterials {

	/** Needle-leaved / evergreen forest land class. */
	public static final String CONIFER = "EvergreenForest";
	/** Broad-leaved / deciduous forest land class. */
	public static final String BROADLEAF = "DeciduousForest";
	/** Mixed forest land class. */
	public static final String MIXED = "MixedForest";

	/** All materials treated as forest for overlap exclusion. */
	public static final Set<String> FOREST_MATERIALS = Collections.unmodifiableSet(
			new HashSet<>(Arrays.asList(CONIFER, BROADLEAF, MIXED)));

	/** Per-triangle flag bit marking a land-class forest triangle. */
	public static final byte FLAG_FOREST = 1;

	private FlightGearForestMaterials() {
	}

	/** Whether the given material is one of the forest land classes. */
	public static boolean isForestMaterial(String material) {
		return material != null && FOREST_MATERIALS.contains(material);
	}
}
