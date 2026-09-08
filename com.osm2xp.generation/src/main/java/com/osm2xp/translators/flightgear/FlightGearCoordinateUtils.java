package com.osm2xp.translators.flightgear;

/**
 * Local Cartesian coordinate conversion for FlightGear scenery, mirroring the
 * flat-earth / round-earth logic used by OSM2City
 * <p>
 * FlightGear building lists expect offsets in meters relative to the STG
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
	 * Inverse of {@link #toLocal(double, double, double, double)}: converts
	 * flat-earth east/north offsets (metres) relative to the anchor back into
	 * global lon/lat. Returns a 2-element array {lon, lat} in degrees.
	 */
	public static double[] localToGeodetic(double east, double north, double anchorLon, double anchorLat) {
		double cosLat = Math.cos(Math.toRadians(anchorLat));
		double sinLat = Math.sin(Math.toRadians(anchorLat));
		double denom = Math.sqrt(1.0 - E2 * sinLat * sinLat);
		double r1 = EQURAD * (1.0 - E2) / Math.pow(denom, 3.0);
		double r2 = EQURAD / denom;
		double lon = anchorLon + Math.toDegrees(east / (r2 * cosLat));
		double lat = anchorLat + Math.toDegrees(north / r1);
		return new double[] { lon, lat };
	}

	/**
	 * Converts WGS84 geodetic coordinates into Earth-Centered Earth-Fixed (ECEF)
	 * Cartesian coordinates. Returns a 3-element array {x, y, z} in metres.
	 */
	public static double[] geodeticToEcef(double lon, double lat, double altM) {
		double lonR = Math.toRadians(lon);
		double latR = Math.toRadians(lat);
		double sinLat = Math.sin(latR);
		double cosLat = Math.cos(latR);
		double denom = Math.sqrt(1.0 - E2 * sinLat * sinLat);
		double n = EQURAD / denom;
		double x = (n + altM) * cosLat * Math.cos(lonR);
		double y = (n + altM) * cosLat * Math.sin(lonR);
		double z = (n * (1.0 - E2) + altM) * sinLat;
		return new double[] { x, y, z };
	}

	/**
	 * Converts ECEF Cartesian coordinates back into WGS84 geodetic coordinates.
	 * Returns a 3-element array {lon, lat, alt} in degrees / metres.
	 */
	public static double[] ecefToGeodetic(double x, double y, double z) {
		double lon = Math.atan2(y, x);
		double p = Math.sqrt(x * x + y * y);
		double lat = Math.atan2(z, p * (1.0 - E2));
		double alt = 0.0;
		for (int i = 0; i < 20; i++) {
			double sinLat = Math.sin(lat);
			double cosLat = Math.cos(lat);
			double n = EQURAD / Math.sqrt(1.0 - E2 * sinLat * sinLat);
			alt = p / cosLat - n;
			lat = Math.atan2(z, p * (1.0 - E2 * n / (n + alt)));
		}
		return new double[] { Math.toDegrees(lon), Math.toDegrees(lat), alt };
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
