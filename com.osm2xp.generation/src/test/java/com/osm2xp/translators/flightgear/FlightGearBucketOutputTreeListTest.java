package com.osm2xp.translators.flightgear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;

import org.junit.Test;

/**
 * Verifies the {@code TREE_LIST} output plumbing: the gzipped tree-coordinate
 * file is created next to the STG with the expected name and content.
 */
public class FlightGearBucketOutputTreeListTest {

	@Test
	public void writesGzippedTreeListFile() throws Exception {
		File root = Files.createTempDirectory("osm2xp-tree-test").toFile();
		FlightGearBucket bucket = FlightGearBucket.bucketFor(80.0, 50.0);
		FlightGearBucketOutput output = new FlightGearBucketOutput(root, bucket, false);

		BufferedWriter writer = output.getTreeListWriter(FlightGearForestMaterials.MIXED);
		writer.write("123.45 -67.89 250.00\n");
		writer.write("-1000.00 2000.00 100.00\n");
		output.close();

		File listFile = new File(output.getFolder(), output.getTreeListFileName(FlightGearForestMaterials.MIXED));
		assertTrue("tree list file should exist: " + listFile, listFile.isFile());
		assertTrue(output.getTreeListFileName(FlightGearForestMaterials.MIXED)
				.startsWith("TreeList_MixedForest_"));

		StringBuilder content = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new GZIPInputStream(Files.newInputStream(listFile.toPath())),
						StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line).append('\n');
			}
		}
		assertEquals("123.45 -67.89 250.00\n-1000.00 2000.00 100.00\n", content.toString());
	}
}
