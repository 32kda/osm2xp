package com.osm2xp.translators;

import java.io.BufferedWriter;

/**
 * Resolves the FlightGear STG writer for the sub-bucket containing a given
 * coordinate. Bucket writers are created lazily, so a provider may return
 * <code>null</code> if the bucket file could not be opened.
 *
 * @author osm2xp
 */
public interface FlightGearStgWriterProvider {

	/**
	 * @param lon longitude of a point, in degrees
	 * @param lat latitude of a point, in degrees
	 * @return the STG writer of the sub-bucket containing the point, or
	 *         <code>null</code> if it could not be created
	 */
	BufferedWriter getStgWriter(double lon, double lat);
}
