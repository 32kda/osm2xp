package com.osm2xp.translators.flightgear;

import java.util.Locale;

/**
 * FlightGear sub-bucket ("tile") math, mirroring SimGear's
 * <code>SGBucket</code> (simgear/bucket/newbucket.cxx). FlightGear tiles are
 * not 1-degree cells: each degree of latitude is split into 8 rows
 * ({@value #SG_BUCKET_SPAN} degree each) and the longitude span depends on the
 * latitude band (see {@link #sgBucketSpan(double)}).
 *
 * @author osm2xp
 */
public class FlightGearBucket {

	/** Latitude extent of one tile, in degrees. */
	public static final double SG_BUCKET_SPAN = 0.125;

	/**
	 * Boundary-epsilon for flooring. Must be far below the granularity of OSM
	 * coordinates (~1e-7 degrees) so a point just <em>below</em> a tile boundary is not
	 * floored into the neighbouring tile. A coarse value (e.g. 1e-5, ~1.1 m) makes
	 * <code>bucketFor</code> disagree with the tile assignment in
	 * <code>MultiTileDataConverter</code>, causing two tiles to open the same STG file
	 * (second <code>FileOutputStream</code> truncates the first), which corrupts the STG
	 * with NUL-padded holes.
	 */
	private static final double SG_EPSILON = 1e-9;

	private final int lon;
	private final int lat;
	private final int x;
	private final int y;
	private final double span;

	private FlightGearBucket(int lon, int lat, int x, int y, double span) {
		this.lon = lon;
		this.lat = lat;
		this.x = x;
		this.y = y;
		this.span = span;
	}

	/**
	 * Longitude extent of a tile at the given latitude, in degrees
	 * (SimGear <code>SGBucket::sg_bucket_span</code>).
	 */
	public static double sgBucketSpan(double l) {
		if (l >= 89.0) {
			return 12.0;
		} else if (l >= 86.0) {
			return 4.0;
		} else if (l >= 83.0) {
			return 2.0;
		} else if (l >= 76.0) {
			return 1.0;
		} else if (l >= 62.0) {
			return 0.5;
		} else if (l >= 22.0) {
			return 0.25;
		} else if (l >= -22.0) {
			return 0.125;
		} else if (l >= -62.0) {
			return 0.25;
		} else if (l >= -76.0) {
			return 0.5;
		} else if (l >= -83.0) {
			return 1.0;
		} else if (l >= -86.0) {
			return 2.0;
		} else if (l >= -89.0) {
			return 4.0;
		}
		return 12.0;
	}

	private static int floorWithEpsilon(double a) {
		return (int) Math.floor(a + SG_EPSILON);
	}

	/**
	 * Resolve the tile containing the given WGS84 coordinates
	 * (SimGear <code>SGBucket::innerSet</code>).
	 */
	public static FlightGearBucket bucketFor(double dlon, double dlat) {
		while (dlon < -180.0) {
			dlon += 360.0;
		}
		while (dlon >= 180.0) {
			dlon -= 360.0;
		}
		if (dlat > 90.0) {
			dlat = 90.0;
		}
		if (dlat < -90.0) {
			dlat = -90.0;
		}

		double span = sgBucketSpan(dlat);
		int lon = floorWithEpsilon(dlon);
		int x;
		if (span <= 1.0) {
			x = floorWithEpsilon((dlon - lon) / span);
		} else {
			lon = (int) Math.floor(lon / span) * (int) span;
			x = 0;
		}

		int lat = floorWithEpsilon(dlat);
		int y;
		if (lat == 90) {
			lat = 89;
			y = 7;
		} else {
			y = floorWithEpsilon((dlat - lat) * 8);
		}
		return new FlightGearBucket(lon, lat, x, y, span);
	}

	/**
	 * Unique scenery tile index (SimGear <code>SGBucket::gen_index_str</code>),
	 * used as the STG/BuildingList file name, e.g. 4268928 for the bottom-left
	 * sub-tile of the 80..81E, 52..53N degree cell.
	 */
	public long getIndex() {
		return (((long) lon + 180) << 14) + ((lat + 90) << 6) + (y << 3) + x;
	}

	/**
	 * Bucket folder path below <code>Objects/</code>
	 * (SimGear <code>SGBucket::gen_base_path</code>), e.g.
	 * <code>e080n50/e080n52</code> for the 80E/52N tile.
	 */
	public String genBasePath() {
		int topLon = lon / 10;
		int mainLon = lon;
		if ((lon < 0) && (topLon * 10 != lon)) {
			topLon -= 1;
		}
		topLon *= 10;
		char hem;
		if (topLon >= 0) {
			hem = 'e';
		} else {
			hem = 'w';
			topLon *= -1;
		}
		if (mainLon < 0) {
			mainLon *= -1;
		}

		int topLat = lat / 10;
		int mainLat = lat;
		if ((lat < 0) && (topLat * 10 != lat)) {
			topLat -= 1;
		}
		topLat *= 10;
		char pole;
		if (topLat >= 0) {
			pole = 'n';
		} else {
			pole = 's';
			topLat *= -1;
		}
		if (mainLat < 0) {
			mainLat *= -1;
		}

		return String.format(Locale.ROOT, "%c%03d%c%02d/%c%03d%c%02d",
				hem, topLon, pole, topLat, hem, mainLon, pole, mainLat);
	}

	/** Longitude of the tile center (SimGear <code>get_center_lon</code>). */
	public double getCenterLon() {
		return lon + x * span + span / 2;
	}

	/** Latitude of the tile center (SimGear <code>get_center_lat</code>). */
	public double getCenterLat() {
		return lat + y / 8.0 + SG_BUCKET_SPAN / 2;
	}

	/** Longitude extent of the tile, in degrees. */
	public double getSpan() {
		return span;
	}

	public int getLon() {
		return lon;
	}

	public int getLat() {
		return lat;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	@Override
	public String toString() {
		return getIndex() + " (" + genBasePath() + ")";
	}
}
