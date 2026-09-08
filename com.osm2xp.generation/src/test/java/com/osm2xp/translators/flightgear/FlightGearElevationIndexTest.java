package com.osm2xp.translators.flightgear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

class FlightGearElevationIndexTest {

	private static final String TERRAIN_DIR = "d:/Games/FlightGear 2024.1/TerraSync/Terrain";

	@Test
	void nearestNeighborFindsClosest() {
		KdTree2D tree = new KdTree2D(
				new double[] { 0, 10, 0, 10 },
				new double[] { 0, 0, 10, 10 },
				new double[] { 1, 2, 3, 4 });
		assertEquals(1.0, tree.nearestValue(0.1, 0.1), 1e-9);
		assertEquals(2.0, tree.nearestValue(9.9, 0.1), 1e-9);
		assertEquals(3.0, tree.nearestValue(0.1, 9.9), 1e-9);
		assertEquals(4.0, tree.nearestValue(9.9, 9.9), 1e-9);
		assertEquals(2.0, tree.nearestValue(6.0, 0.0), 1e-9);
	}

	@Test
	void emptyTreeReturnsNaN() {
		KdTree2D tree = new KdTree2D(new double[0], new double[0], new double[0]);
		assertTrue(tree.isEmpty());
		assertTrue(Double.isNaN(tree.nearestValue(0.0, 0.0)));
	}

	@Test
	void probesKnownAirport() {
		File terrain = new File(TERRAIN_DIR);
		assumeTrue(terrain.isDirectory(), "TerraSync terrain not found, skipping");
		FlightGearElevationIndex index = new FlightGearElevationIndex(terrain);
		assertFalse(index.isDisabled());
		double elevation = index.probe(82.6507, 55.0123);
		assertFalse(Double.isNaN(elevation), "no terrain tile found for UNNT");
		assertTrue(elevation > 0 && elevation < 500, "unexpected elevation " + elevation);
	}

	@Test
	void missingTileReturnsNaN() {
		File terrain = new File(TERRAIN_DIR);
		assumeTrue(terrain.isDirectory(), "TerraSync terrain not found, skipping");
		FlightGearElevationIndex index = new FlightGearElevationIndex(terrain);
		assertTrue(Double.isNaN(index.probe(180.0, 0.0)));
	}
}
