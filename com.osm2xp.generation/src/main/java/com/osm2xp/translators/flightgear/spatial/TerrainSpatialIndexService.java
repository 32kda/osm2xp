package com.osm2xp.translators.flightgear.spatial;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.spatial.TileSpatialIndex;
import com.osm2xp.translators.flightgear.FlightGearBucket;
import com.osm2xp.translators.flightgear.FlightGearCoordinateUtils;
import com.osm2xp.translators.flightgear.FlightGearForestMaterials;

/**
 * Run-scoped, shared provider of per-tile {@link TileSpatialIndex}es (and the
 * parsed {@link BtgTile}s they are built from).
 * <p>
 * A single instance is created per generation run and reused by buildings,
 * airfields and vegetation, so each terrain tile is read, parsed and indexed at
 * most once. A bounded LRU cache keeps memory in check.
 *
 * @author osm2xp
 */
public final class TerrainSpatialIndexService {

	public static final int DEFAULT_CACHE_SIZE = 64;

	private final File terrainRoot;
	private final int cacheSize;
	private final int grid;
	private final Map<Long, Entry> cache;

	private static volatile TerrainSpatialIndexService shared;

	/**
	 * Installs the run-scoped shared service, dropping any previous one. Called by
	 * the FlightGear provider once the terrain root is known.
	 */
	public static synchronized void setShared(File terrainRoot) {
		shared = terrainRoot == null || !terrainRoot.isDirectory() ? null
				: new TerrainSpatialIndexService(terrainRoot);
	}

	/** The run-scoped shared service, or {@code null} if not installed. */
	public static TerrainSpatialIndexService shared() {
		return shared;
	}

	public TerrainSpatialIndexService(File terrainRoot) {
		this(terrainRoot, DEFAULT_CACHE_SIZE, TileSpatialIndex.DEFAULT_GRID);
	}

	public TerrainSpatialIndexService(File terrainRoot, int cacheSize, int grid) {
		this.terrainRoot = terrainRoot;
		this.cacheSize = Math.max(1, cacheSize);
		this.grid = Math.max(1, grid);
		this.cache = new LinkedHashMap<Long, Entry>(16, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
				return size() > TerrainSpatialIndexService.this.cacheSize;
			}
		};
	}

	public boolean isDisabled() {
		return terrainRoot == null || !terrainRoot.isDirectory();
	}

	/** Elevation (metres) at a global lon/lat, or {@code NaN} if unavailable. */
	public double probeElevation(double lon, double lat) {
		return probeElevation(FlightGearBucket.bucketFor(lon, lat), lon, lat);
	}

	public double probeElevation(FlightGearBucket bucket, double lon, double lat) {
		TileSpatialIndex index = indexFor(bucket);
		if (index == null) {
			return Double.NaN;
		}
		double[] local = FlightGearCoordinateUtils.toLocal(lon, lat, bucket.getCenterLon(), bucket.getCenterLat());
		return index.elevationLocal(local[0], local[1]);
	}

	/** Whether the lon/lat is on an existing forest land class. */
	public boolean isForest(double lon, double lat) {
		FlightGearBucket bucket = FlightGearBucket.bucketFor(lon, lat);
		TileSpatialIndex index = indexFor(bucket);
		if (index == null) {
			return false;
		}
		double[] local = FlightGearCoordinateUtils.toLocal(lon, lat, bucket.getCenterLon(), bucket.getCenterLat());
		return index.hasFlagLocal(local[0], local[1], FlightGearForestMaterials.FLAG_FOREST);
	}

	/** Spatial index of the tile containing the bucket, or {@code null} if absent. */
	public synchronized TileSpatialIndex indexFor(FlightGearBucket bucket) {
		Entry entry = entryFor(bucket);
		return entry == null ? null : entry.index;
	}

	/** Parsed tile for the bucket, or {@code null} if absent. */
	public synchronized BtgTile tileFor(FlightGearBucket bucket) {
		Entry entry = entryFor(bucket);
		return entry == null ? null : entry.tile;
	}

	private Entry entryFor(FlightGearBucket bucket) {
		if (isDisabled() || bucket == null) {
			return null;
		}
		Long key = bucket.getIndex();
		Entry cached = cache.get(key);
		if (cached != null) {
			return cached;
		}
		Entry loaded = load(bucket);
		if (loaded != null) {
			cache.put(key, loaded);
		}
		return loaded;
	}

	private Entry load(FlightGearBucket bucket) {
		File tileFile = new File(new File(terrainRoot, bucket.genBasePath()),
				bucket.getIndex() + ".btg.gz");
		if (!tileFile.isFile()) {
			return null;
		}
		try {
			BtgTile tile = Btg.read(tileFile);
			if (tile == null) {
				return null;
			}
			return new Entry(tile, FlightGearTileSpatialIndexFactory.build(tile, bucket, grid));
		} catch (IOException | RuntimeException e) {
			Osm2xpLogger.error("Error building spatial index for " + tileFile, e);
			return null;
		}
	}

	private static final class Entry {
		final BtgTile tile;
		final TileSpatialIndex index;

		Entry(BtgTile tile, TileSpatialIndex index) {
			this.tile = tile;
			this.index = index;
		}
	}
}
