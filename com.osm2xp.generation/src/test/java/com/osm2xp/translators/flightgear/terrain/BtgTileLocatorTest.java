package com.osm2xp.translators.flightgear.terrain;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/**
 * Unit tests for the BTG tile path resolution.
 */
public class BtgTileLocatorTest {

	@Test
	public void locatesTileUnderTerrainTree() {
		File tile = new BtgTileLocator().tileFileFor(80.05, 52.05, new File("work"));
		assertNotNull(tile);
		String path = tile.getPath().replace('\\', '/');
		assertTrue(path, path.endsWith("/e080n50/e080n52/4268928.btg.gz"));
	}
}
