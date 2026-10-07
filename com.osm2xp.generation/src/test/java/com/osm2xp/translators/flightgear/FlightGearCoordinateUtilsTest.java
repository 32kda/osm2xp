package com.osm2xp.translators.flightgear;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Round-trip checks for {@link FlightGearCoordinateUtils#ecefToGeodetic}, which
 * now uses a fast tan-space iteration instead of the classic atan2 loop.
 */
public class FlightGearCoordinateUtilsTest {

	@Test
	public void ecefRoundTrip() {
		double[] lons = { -179.9, -90, 0, 45.5, 82.65, 179.9 };
		double[] lats = { -89, -60, -0.1, 0, 30, 55.01, 89 };
		double[] alts = { -400, 0, 100, 1000, 8848 };
		for (double lon : lons) {
			for (double lat : lats) {
				for (double alt : alts) {
					double[] ecef = FlightGearCoordinateUtils.geodeticToEcef(lon, lat, alt);
					double[] geodetic = FlightGearCoordinateUtils.ecefToGeodetic(ecef[0], ecef[1], ecef[2]);
					String label = " lon=" + lon + " lat=" + lat + " alt=" + alt;
					assertEquals("lon" + label, lon, geodetic[0], 1e-7);
					assertEquals("lat" + label, lat, geodetic[1], 1e-7);
					assertEquals("alt" + label, alt, geodetic[2], 1e-3);
				}
			}
		}
	}

	@Test
	public void polarAxis() {
		double[] north = FlightGearCoordinateUtils.ecefToGeodetic(0.0, 0.0, 6356752.314245);
		assertEquals(90.0, north[1], 1e-9);
		double[] south = FlightGearCoordinateUtils.ecefToGeodetic(0.0, 0.0, -6356752.314245);
		assertEquals(-90.0, south[1], 1e-9);
	}
}
