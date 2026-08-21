package com.osm2xp.translators.flightgear;

/**
 * Local Cartesian coordinate conversion for FlightGear scenery, mirroring the
 * flat-earth / round-earth logic used by OSM2City
 * (example/osm2city/utils/coordinates.py).
 * <p>
 * FlightGear building lists expect offsets in metres relative to the STG
 * BUILDING_LIST anchor in a Z-up frame where X points south and Y points east.
 */
public final class FlightGearCoordinateUtils {

	public static final double EQURAD = 6378137.0;
	public static final double FLATTENING = 298.257223563;
	public static final double E2 = (1.0 / FLATTENING) * (2.0 - (1.0 / FLATTENING));

	private FlightGearCoordinateUtils() {
	}

	/**
	 * Converts a global lon/lat into flat-earth east/north offsets (metres)
	 * relative to the given anchor. Returns a 2-element array {east, north}.
	 */
	public static double[] toLocal(double lon, double lat, double anchorLon, double anchorLat) {
		double cosLat = Math.cos(Math.toRadians(anchorLat));
		double sinLat = Math.sin(Math.toRadians(anchorLat));
		double denom = Math.sqrt(1.0 - E2 * sinLat * sinLat);
		double r1 = EQURAD * (1.0 - E2) / Math.pow(denom, 3.0);
		double r2 = EQURAD / denom;
		double east = r2 * Math.toRadians(lon - anchorLon) * cosLat;
		double north = r1 * Math.toRadians(lat - anchorLat);
		return new double[] { east, north };
	}

	/**
	 * Correction to add to a building's ground elevation so that it drapes over
	 * the round Earth inside the flat ac-mesh (see OSM2City
	 * calc_horizon_elev_local). x/y are the local flat-earth offsets in metres.
	 */
	public static double calcHorizonElevLocal(double x, double y) {
		double horizontalDistSquare = x * x + y * y;
		double shorter = Math.sqrt(EQURAD * EQURAD - horizontalDistSquare);
		return EQURAD - shorter;
	}
}
