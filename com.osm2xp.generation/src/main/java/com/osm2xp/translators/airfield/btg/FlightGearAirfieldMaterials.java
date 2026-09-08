package com.osm2xp.translators.airfield.btg;

import com.osm2xp.translators.airfield.RunwayData;

/**
 * Maps OSM airfield surface types to the standard FlightGear material names
 * defined in the bundled {@code Materials/default/global.xml} (and
 * {@code Materials/regions/global.xml}).
 * <p>
 * Using only these names guarantees the generated scenery resolves against the
 * stock material library, so no custom {@code materials.xml} or texture files
 * have to be shipped with the scenery.
 */
public final class FlightGearAirfieldMaterials {

	/** Grass clearing ("skirt") material. */
	public static final String SKIRT = "Grass";
	/** Asphalt runway surface. */
	public static final String ASPHALT_RUNWAY = "pa_rest";
	/** Concrete runway surface. */
	public static final String CONCRETE_RUNWAY = "pc_rest";
	/** Asphalt taxiway surface. */
	public static final String ASPHALT_TAXIWAY = "pa_taxiway";
	/** Concrete taxiway surface. */
	public static final String CONCRETE_TAXIWAY = "pc_taxiway";
	/** Asphalt apron / tiedown surface. */
	public static final String ASPHALT_APRON = "pa_tiedown";
	/** Concrete apron / tiedown surface. */
	public static final String CONCRETE_APRON = "pc_tiedown";
	/** Asphalt helipad surface. */
	public static final String ASPHALT_HELIPAD = "pa_heli";
	/** Concrete helipad surface. */
	public static final String CONCRETE_HELIPAD = "pc_heli";
	/** Grass runway / taxiway surface. */
	public static final String GRASS_RUNWAY = "grass_rwy";
	/** Dirt runway / taxiway surface. */
	public static final String DIRT_RUNWAY = "dirt_rwy";
	/** Gravel surface. */
	public static final String GRAVEL = "Gravel";

	/** Texture-coordinate tile size for the grass skirt, in metres. */
	private static final double GRASS_UV_SCALE = 1000.0;
	/** Texture-coordinate tile size for pavement surfaces, in metres. */
	private static final double PAVEMENT_UV_SCALE = 100.0;

	private FlightGearAirfieldMaterials() {
	}

	/** Resolves the runway surface material for the given runway. */
	public static String runwaySurface(RunwayData runway) {
		String surface = runway.getSurface();
		if (runway.isHard()) {
			return isConcrete(surface) ? CONCRETE_RUNWAY : ASPHALT_RUNWAY;
		}
		if ("earth".equals(surface) || "dirt".equals(surface) || "mud".equals(surface) || "sand".equals(surface)) {
			return DIRT_RUNWAY;
		}
		if ("gravel".equals(surface) || "fine_gravel".equals(surface)) {
			return GRAVEL;
		}
		return GRASS_RUNWAY;
	}

	public static String taxiwaySurface(boolean hard) {
		return hard ? ASPHALT_TAXIWAY : GRASS_RUNWAY;
	}

	public static String apronSurface(boolean hard) {
		return hard ? ASPHALT_APRON : SKIRT;
	}

	public static String helipadSurface(boolean hard) {
		return hard ? ASPHALT_HELIPAD : GRASS_RUNWAY;
	}

	/** Texture-coordinate tile size (metres) appropriate for the given material. */
	public static double uvScale(String material) {
		return SKIRT.equals(material) ? GRASS_UV_SCALE : PAVEMENT_UV_SCALE;
	}

	private static boolean isConcrete(String surface) {
		return "concrete".equals(surface) || "paved".equals(surface);
	}
}
