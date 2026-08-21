package com.osm2xp.translators.flightgear;

/**
 * Resolves the {@link FlightGearBucketOutput} of the sub-bucket containing a
 * given coordinate. Used by poly handlers to write both the STG header and the
 * per-material <code>LINE_FEATURE_LIST</code> list files.
 *
 * @author osm2xp
 */
public interface FlightGearBucketOutputProvider {

	/**
	 * @param lon longitude of a point, in degrees
	 * @param lat latitude of a point, in degrees
	 * @return the bucket output of the sub-bucket containing the point, or
	 *         <code>null</code> if it could not be created
	 */
	FlightGearBucketOutput getBucketOutput(double lon, double lat);
}
