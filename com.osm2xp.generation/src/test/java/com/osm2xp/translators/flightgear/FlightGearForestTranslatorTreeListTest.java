package com.osm2xp.translators.flightgear;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.Test;

import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgFace;
import com.osm2xp.core.parsers.btg.BtgPointGroup;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.core.parsers.btg.BtgVector3;
import com.osm2xp.translators.flightgear.spatial.TerrainSpatialIndexService;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

/**
 * End-to-end check that a forest polygon produces the {@code TREE_LIST} STG
 * directive plus a gzipped tree-coordinate file next to it.
 */
public class FlightGearForestTranslatorTreeListTest {

	@Test
	public void writesTreeListForForestPolygon() throws Exception {
		File terrain = Files.createTempDirectory("osm2xp-terrain").toFile();
		File output = Files.createTempDirectory("osm2xp-output").toFile();
		FlightGearBucket bucket = FlightGearBucket.bucketFor(80.0, 50.0);
		writeTile(terrain, bucket);

		TerrainSpatialIndexService.setShared(terrain);
		try {
			FlightGearForestTranslator translator = new FlightGearForestTranslator();
			FlightGearBucketOutput out = new FlightGearBucketOutput(output, bucket, false);
			translator.setBucketOutputProvider((lon, lat) -> out);

			double centerLon = bucket.getCenterLon();
			double centerLat = bucket.getCenterLat();
			LinearRing2D ring = new LinearRing2D(
					new Point2D(centerLon - 0.005, centerLat - 0.005),
					new Point2D(centerLon + 0.005, centerLat - 0.005),
					new Point2D(centerLon + 0.005, centerLat + 0.005),
					new Point2D(centerLon - 0.005, centerLat + 0.005));
			translator.contribute(ring, FlightGearForestMaterials.MIXED);
			translator.translationComplete();
			out.close();

			File stg = new File(new File(new File(output, "Objects"), bucket.genBasePath()),
					bucket.getIndex() + ".stg");
			assertTrue("STG should exist: " + stg, stg.isFile());
			String stgContent = new String(Files.readAllBytes(stg.toPath()), StandardCharsets.UTF_8);
			assertTrue("STG should contain TREE_LIST, was:\n" + stgContent, stgContent.contains("TREE_LIST"));
			assertTrue("STG should reference MixedForest", stgContent.contains("MixedForest"));

			File list = new File(stg.getParentFile(),
					"TreeList_" + FlightGearForestMaterials.MIXED + "_" + bucket.getIndex() + ".txt.gz");
			assertTrue("tree list should exist: " + list, list.isFile());
			assertTrue("tree list should contain trees", countLines(list) > 0);
		} finally {
			TerrainSpatialIndexService.setShared(null);
		}
	}

	private static int countLines(File gzip) throws Exception {
		int count = 0;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new GZIPInputStream(Files.newInputStream(gzip.toPath())),
						StandardCharsets.UTF_8))) {
			while (reader.readLine() != null) {
				count++;
			}
		}
		return count;
	}

	private static void writeTile(File terrainRoot, FlightGearBucket bucket) throws Exception {
		double centerLon = bucket.getCenterLon();
		double centerLat = bucket.getCenterLat();
		double[] center = FlightGearCoordinateUtils.geodeticToEcef(centerLon, centerLat, 0.0);
		double[][] lonLats = {
				{ centerLon - 0.005, centerLat - 0.005 }, { centerLon + 0.005, centerLat - 0.005 },
				{ centerLon + 0.005, centerLat + 0.005 }, { centerLon - 0.005, centerLat + 0.005 } };
		float[] vertices = new float[lonLats.length * 3];
		for (int i = 0; i < lonLats.length; i++) {
			double[] ecef = FlightGearCoordinateUtils.geodeticToEcef(lonLats[i][0], lonLats[i][1], 100.0);
			vertices[3 * i] = (float) (ecef[0] - center[0]);
			vertices[3 * i + 1] = (float) (ecef[1] - center[1]);
			vertices[3 * i + 2] = (float) (ecef[2] - center[2]);
		}
		List<BtgFace> faces = new ArrayList<>(Arrays.asList(
				face(0, 1, 2, "Grassland"), face(0, 2, 3, "Grassland")));
		BtgTile tile = new BtgTile(7, 0L, new BtgVector3(center[0], center[1], center[2]), 0.0f, vertices,
				new float[0], new float[0], faces, new ArrayList<BtgPointGroup>());

		File dir = new File(terrainRoot, bucket.genBasePath());
		dir.mkdirs();
		Btg.write(tile, new File(dir, bucket.getIndex() + ".btg.gz"));
	}

	private static BtgFace face(int a, int b, int c, String material) {
		return new BtgFace(a, b, c, -1, -1, -1, -1, -1, -1, material);
	}
}
