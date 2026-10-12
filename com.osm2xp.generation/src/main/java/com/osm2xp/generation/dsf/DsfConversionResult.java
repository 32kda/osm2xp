package com.osm2xp.generation.dsf;

/**
 * Summary of a text-to-DSF conversion. Some pathological input (for example a
 * multipolygon with more points than a single DSF polygon command can index)
 * cannot be represented and is skipped rather than failing the whole file.
 */
public final class DsfConversionResult {

	public final int skippedPolygons;
	public final int skippedObjects;
	public final int skippedChains;

	public DsfConversionResult(int skippedPolygons, int skippedObjects, int skippedChains) {
		this.skippedPolygons = skippedPolygons;
		this.skippedObjects = skippedObjects;
		this.skippedChains = skippedChains;
	}

	public int totalSkipped() {
		return skippedPolygons + skippedObjects + skippedChains;
	}
}
