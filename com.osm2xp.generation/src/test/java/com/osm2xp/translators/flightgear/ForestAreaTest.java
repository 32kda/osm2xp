package com.osm2xp.translators.flightgear;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

public class ForestAreaTest {

	@Test
	public void containsMatchesReferenceImplementation() {
		LinearRing2D ring = new LinearRing2D(new Point2D(0, 0), new Point2D(10, 0),
				new Point2D(10, 10), new Point2D(0, 10));
		ForestArea area = new ForestArea(ring, "MixedForest");

		assertTrue(area.contains(5, 5));
		assertFalse(area.contains(-1, 5));
		assertFalse(area.contains(5, -1));
		assertFalse(area.contains(11, 5));
		assertFalse(area.contains(5, 11));

		Random random = new Random(7);
		for (int i = 0; i < 5000; i++) {
			double x = -2 + random.nextDouble() * 14;
			double y = -2 + random.nextDouble() * 14;
			assertEquals("at " + x + "," + y, ring.isInside(x, y), area.contains(x, y));
		}
	}
}
