package com.osm2xp.translators.flightgear;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.core.parsers.btg.BtgVector3;
import com.osm2xp.spatial.TileSpatialIndex;
import com.osm2xp.translators.flightgear.spatial.FlightGearTileSpatialIndexFactory;

/**
 * Benchmarks the spatial-hash index against the previous kd-tree elevation
 * lookup on real BTG tiles.
 * <p>
 * Gated on a local terrain folder: set {@code -Dfg.terrain.dir=<Terrain root>}
 * or rely on the default. Prints the measured query times and asserts the
 * spatial hash is faster.
 */
public class SpatialIndexBenchmarkTest {

	private static final String TERRAIN = System.getProperty("fg.terrain.dir",
			"d:/Games/FlightGear 2024.1/TerraSync/Terrain");
	private static final int TILES = 3;
	private static final int QUERIES_PER_TILE = 60000;

	@Test
	public void spatialHashFasterThanKdTree() throws Exception {
		File root = new File(TERRAIN);
		assumeTrue("TerraSync terrain not found, skipping", root.isDirectory());

		List<File> tiles = new ArrayList<>();
		collect(root, tiles, TILES);
		assumeTrue("no BTG tiles found under " + root, !tiles.isEmpty());

		long kdNanos = 0;
		long spatialNanos = 0;
		int compared = 0;
		for (File file : tiles) {
			BtgTile tile = Btg.read(file);
			if (tile == null || tile.getVertexCount() == 0) {
				continue;
			}
			double[] center = FlightGearCoordinateUtils.ecefToGeodetic(tile.getCenter().getX(),
					tile.getCenter().getY(), tile.getCenter().getZ());
			FlightGearBucket bucket = FlightGearBucket.bucketFor(center[0], center[1]);

			double[][] frame = buildFrame(tile, bucket);
			double[] east = frame[0];
			double[] north = frame[1];
			double[] elev = frame[2];
			KdTree2D kd = new KdTree2D(east, north, elev);
			TileSpatialIndex spatial = FlightGearTileSpatialIndexFactory.build(tile, bucket);

			double[] minmax = extent(east, north);
			Random random = new Random(11);
			double[] xs = new double[QUERIES_PER_TILE];
			double[] ys = new double[QUERIES_PER_TILE];
			for (int i = 0; i < QUERIES_PER_TILE; i++) {
				xs[i] = minmax[0] + random.nextDouble() * (minmax[1] - minmax[0]);
				ys[i] = minmax[2] + random.nextDouble() * (minmax[3] - minmax[2]);
			}

			// Warm up.
			double sink = 0;
			for (int i = 0; i < 2000; i++) {
				sink += kd.nearestValue(xs[i], ys[i]);
				sink += spatial.elevationLocal(xs[i], ys[i]);
			}

			long t0 = System.nanoTime();
			for (int i = 0; i < QUERIES_PER_TILE; i++) {
				sink += kd.nearestValue(xs[i], ys[i]);
			}
			long t1 = System.nanoTime();
			for (int i = 0; i < QUERIES_PER_TILE; i++) {
				sink += spatial.elevationLocal(xs[i], ys[i]);
			}
			long t2 = System.nanoTime();
			if (Double.isNaN(sink)) {
				// keep the JIT from eliminating the loop
			}
			kdNanos += t1 - t0;
			spatialNanos += t2 - t1;
			compared++;
		}
		assumeTrue("no indexable tiles", compared > 0);

		double kdMs = kdNanos / 1e6;
		double spatialMs = spatialNanos / 1e6;
		System.out.printf("Spatial hash benchmark: tiles=%d queries=%d -> kd=%.1f ms, spatial=%.1f ms, speedup=%.2fx%n",
				compared, compared * QUERIES_PER_TILE, kdMs, spatialMs, kdMs / spatialMs);
		assertTrue("spatial hash should be faster than kd-tree (" + spatialMs + " ms vs " + kdMs + " ms)",
				spatialNanos < kdNanos);
	}

	private static double[][] buildFrame(BtgTile tile, FlightGearBucket bucket) {
		int count = tile.getVertexCount();
		float[] vertices = tile.getVertices();
		BtgVector3 center = tile.getCenter();
		double[] east = new double[count];
		double[] north = new double[count];
		double[] elev = new double[count];
		for (int i = 0; i < count; i++) {
			double[] geodetic = FlightGearCoordinateUtils.ecefToGeodetic(center.getX() + vertices[3 * i],
					center.getY() + vertices[3 * i + 1], center.getZ() + vertices[3 * i + 2]);
			double[] local = FlightGearCoordinateUtils.toLocal(geodetic[0], geodetic[1], bucket.getCenterLon(),
					bucket.getCenterLat());
			east[i] = local[0];
			north[i] = local[1];
			elev[i] = geodetic[2];
		}
		return new double[][] { east, north, elev };
	}

	private static double[] extent(double[] east, double[] north) {
		double minE = Double.POSITIVE_INFINITY;
		double maxE = Double.NEGATIVE_INFINITY;
		double minN = Double.POSITIVE_INFINITY;
		double maxN = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < east.length; i++) {
			minE = Math.min(minE, east[i]);
			maxE = Math.max(maxE, east[i]);
			minN = Math.min(minN, north[i]);
			maxN = Math.max(maxN, north[i]);
		}
		return new double[] { minE, maxE, minN, maxN };
	}

	private static void collect(File dir, List<File> out, int limit) {
		File[] files = dir.listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (out.size() >= limit) {
				return;
			}
			if (file.isDirectory()) {
				collect(file, out, limit);
			} else if (file.getName().endsWith(".btg.gz")) {
				out.add(file);
			}
		}
	}
}
