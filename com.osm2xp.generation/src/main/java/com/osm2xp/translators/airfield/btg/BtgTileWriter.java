package com.osm2xp.translators.airfield.btg;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.translators.flightgear.FlightGearBucket;

/**
 * Writes a rebuilt terrain tile as {@code .btg.gz} plus its {@code OBJECT_BASE}
 * {@code .stg}.
 */
public final class BtgTileWriter {

	/**
	 * Writes the tile into {@code <outputSceneryRoot>/Terrain/<bucket path>} and
	 * logs the result.
	 */
	public void write(BtgTile tile, FlightGearBucket bucket, File outputSceneryRoot, String label, double elevation) {
		File terrainFolder = new File(new File(outputSceneryRoot, "Terrain"), bucket.genBasePath());
		terrainFolder.mkdirs();
		File outFile = new File(terrainFolder, bucket.getIndex() + ".btg.gz");
		try {
			Btg.write(tile, outFile);
			writeTerrainStg(terrainFolder, bucket.getIndex());
			Osm2xpLogger.info("Generated airfield cut BTG for " + label + " into tile " + bucket.getIndex()
					+ " (" + bucket.genBasePath() + "), elevation " + Math.round(elevation) + "m, "
					+ tile.getFaceCount() + " faces");
		} catch (IOException e) {
			Osm2xpLogger.error("Error writing cut terrain BTG " + outFile, e);
		}
	}

	private static void writeTerrainStg(File terrainFolder, long index) throws IOException {
		File stgFile = new File(terrainFolder, index + ".stg");
		Files.write(stgFile.toPath(), ("OBJECT_BASE " + index + ".btg\n").getBytes(StandardCharsets.UTF_8));
	}
}
