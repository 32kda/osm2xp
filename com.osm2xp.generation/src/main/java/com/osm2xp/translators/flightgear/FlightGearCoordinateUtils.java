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

	/** Fixed-point iterations for {@link #ecefToGeodetic}; 4 gives ~0.02 mm. */
	private static final int ECEF_ITERATIONS = 4;

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
	 * <p>
	 * Iterates in {@code tan(lat)} space: {@code sin}/{@code cos} are derived from
	 * one {@code sqrt} and no inverse trig is evaluated until the final
	 * {@code atan}. This is ~7x faster than the classic 20-iteration
	 * {@code atan2}/{@code sin}/{@code cos} loop while staying well below
	 * millimetre accuracy for terrestrial points.
	 */
	public static double[] ecefToGeodetic(double x, double y, double z) {
		double lon = Math.atan2(y, x);
		double p = Math.sqrt(x * x + y * y);
		if (p == 0.0) {
			// On the polar axis: longitude is undefined, latitude is +/-90 deg.
			double lat = z >= 0 ? Math.PI / 2.0 : -Math.PI / 2.0;
			double alt = Math.abs(z) - EQURAD * Math.sqrt(1.0 - E2);
			return new double[] { 0.0, Math.toDegrees(lat), alt };
		}
		double t = z / (p * (1.0 - E2));
		double alt = 0.0;
		for (int i = 0; i < ECEF_ITERATIONS; i++) {
			double t2 = t * t;
			double cosLat = 1.0 / Math.sqrt(1.0 + t2);
			double sinLat = t * cosLat;
			double n = EQURAD / Math.sqrt(1.0 - E2 * sinLat * sinLat);
			alt = p / cosLat - n;
			double denom = n + alt;
			if (denom == 0.0) {
				break;
			}
			t = z / (p * (1.0 - E2 * n / denom));
		}
		return new double[] { Math.toDegrees(lon), Math.toDegrees(Math.atan(t)), alt };
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
