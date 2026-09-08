package com.osm2xp.translators.flightgear;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.core.parsers.btg.BtgVector3;

/**
 * In-process terrain elevation index over the local FlightGear scenery, an
 * alternative to shelling out to the {@code fgelev} utility.
 * <p>
 * The scenery is indexed exactly the way FlightGear itself buckets it: a query
 * is first mapped to its {@link FlightGearBucket} by (lon, lat), the
 * corresponding {@code <index>.btg.gz} tile is located under
 * {@code <terrainRoot>/<band>/<cell>/} and its vertices are organised into a
 * 2D {@link KdTree2D}. A probe returns the elevation of the nearest terrain
 * vertex, in metres.
 * <p>
 * Tiles are loaded lazily and kept in a bounded LRU cache so probing a large
 * area does not require reading the whole world into memory.
 * <p>
 * Elevation is the WGS84 ellipsoid height of the terrain vertex, which closely
 * approximates height above mean sea level (the geoid undulation is ignored).
 */
public class FlightGearElevationIndex {

	private final File terrainRoot;
	private final Map<Long, BucketIndex> cache;

	private static final int DEFAULT_CACHE_SIZE = 64;
	private static final BucketIndex EMPTY = new BucketIndex(Double.NaN, Double.NaN,
			new KdTree2D(new double[0], new double[0], new double[0]));

	/**
	 * @param terrainRoot the {@code Terrain} directory of the scenery
	 *            (e.g. {@code .../TerraSync/Terrain}), containing the
	 *            {@code e080n50}-style band folders
	 */
	public FlightGearElevationIndex(File terrainRoot) {
		this(terrainRoot, DEFAULT_CACHE_SIZE);
	}

	public FlightGearElevationIndex(File terrainRoot, int cacheSize) {
		this.terrainRoot = terrainRoot;
		this.cache = new LinkedHashMap<Long, BucketIndex>(16, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, BucketIndex> eldest) {
				return size() > cacheSize;
			}
		};
	}

	public boolean isDisabled() {
		return terrainRoot == null || !terrainRoot.isDirectory();
	}

	/**
	 * Returns the terrain elevation (metres) at the given global lon/lat, or
	 * {@link Double#NaN} if no terrain tile covers the point.
	 */
	public double probe(double lon, double lat) {
		if (isDisabled()) {
			return Double.NaN;
		}
		FlightGearBucket bucket = FlightGearBucket.bucketFor(lon, lat);
		BucketIndex bucketIndex;
		synchronized (this) {
			bucketIndex = cache.get(bucket.getIndex());
			if (bucketIndex == null) {
				bucketIndex = loadBucket(bucket);
				cache.put(bucket.getIndex(), bucketIndex);
			}
		}
		if (bucketIndex == EMPTY) {
			return Double.NaN;
		}
		double[] local = FlightGearCoordinateUtils.toLocal(lon, lat, bucketIndex.anchorLon, bucketIndex.anchorLat);
		return bucketIndex.tree.nearestValue(local[0], local[1]);
	}

	private BucketIndex loadBucket(FlightGearBucket bucket) {
		String cellPath = bucket.genBasePath().replace('/', File.separatorChar);
		File tileFile = new File(terrainRoot, new File(cellPath, bucket.getIndex() + ".btg.gz").getPath());
		if (!tileFile.isFile()) {
			return EMPTY;
		}
		try {
			return buildBucketIndex(Btg.read(tileFile), bucket);
		} catch (IOException | RuntimeException e) {
			return EMPTY;
		}
	}

	private static BucketIndex buildBucketIndex(BtgTile tile, FlightGearBucket bucket) {
		int count = tile.getVertexCount();
		double anchorLon = bucket.getCenterLon();
		double anchorLat = bucket.getCenterLat();
		BtgVector3 center = tile.getCenter();
		float[] vertices = tile.getVertices();
		double cx = center.getX();
		double cy = center.getY();
		double cz = center.getZ();

		double[] east = new double[count];
		double[] north = new double[count];
		double[] elev = new double[count];
		for (int i = 0; i < count; i++) {
			double[] geodetic = FlightGearCoordinateUtils.ecefToGeodetic(
					cx + vertices[3 * i], cy + vertices[3 * i + 1], cz + vertices[3 * i + 2]);
			double[] local = FlightGearCoordinateUtils.toLocal(geodetic[0], geodetic[1], anchorLon, anchorLat);
			east[i] = local[0];
			north[i] = local[1];
			elev[i] = geodetic[2];
		}
		return new BucketIndex(anchorLon, anchorLat, new KdTree2D(east, north, elev));
	}

	private static final class BucketIndex {
		final double anchorLon;
		final double anchorLat;
		final KdTree2D tree;

		BucketIndex(double anchorLon, double anchorLat, KdTree2D tree) {
			this.anchorLon = anchorLon;
			this.anchorLat = anchorLat;
			this.tree = tree;
		}
	}
}
