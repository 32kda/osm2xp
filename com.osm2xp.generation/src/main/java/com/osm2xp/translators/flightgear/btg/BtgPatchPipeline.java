package com.osm2xp.translators.flightgear.btg;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.translators.flightgear.FlightGearBucket;

/**
 * Applies an ordered set of {@link BtgTilePatcher}s to the terrain tiles of a
 * generation run.
 * <p>
 * For every affected bucket the tile is read from {@code terrainRoot} once, each
 * applicable patcher is applied in {@link BtgPatchPhase} order, and the result
 * is written back once. Reading and writing a single authoritative directory (the
 * generated scenery's {@code Terrain/}) is what prevents two stages from deriving
 * their changes from the pristine source and clobbering each other.
 *
 * @author osm2xp
 */
public final class BtgPatchPipeline {

	private final File terrainRoot;
	private final List<BtgTilePatcher> patchers = new ArrayList<>();

	public BtgPatchPipeline(File terrainRoot) {
		this.terrainRoot = terrainRoot;
	}

	public void addPatcher(BtgTilePatcher patcher) {
		if (patcher != null) {
			patchers.add(patcher);
		}
	}

	/** Runs the pipeline over every bucket any patcher has work for. */
	public void run() {
		if (terrainRoot == null) {
			return;
		}
		List<BtgTilePatcher> ordered = new ArrayList<>(patchers);
		ordered.sort(Comparator.comparingInt(p -> p.phase().getOrder()));

		Set<FlightGearBucket> buckets = new LinkedHashSet<>();
		for (BtgTilePatcher patcher : ordered) {
			buckets.addAll(patcher.affectedBuckets());
		}
		if (buckets.isEmpty()) {
			return;
		}

		int patched = 0;
		for (FlightGearBucket bucket : buckets) {
			File tileFile = tileFile(bucket);
			if (!tileFile.isFile()) {
				Osm2xpLogger.warning("BTG patch pipeline: no terrain tile for " + bucket);
				continue;
			}
			try {
				BtgTilePayload payload = new FileBtgTilePayload(bucket, tileFile);
				for (BtgTilePatcher patcher : ordered) {
					if (patcher.hasWorkFor(bucket)) {
						patcher.apply(payload);
					}
				}
				if (payload.isDirty()) {
					payload.writeTo(tileFile);
					ensureTerrainStg(bucket);
					patched++;
				}
			} catch (Throwable t) {
				Osm2xpLogger.error("BTG patch pipeline: error patching tile " + bucket, t);
			}
		}
		Osm2xpLogger.info("BTG patch pipeline: patched " + patched + " of " + buckets.size()
				+ " candidate tile(s)");
	}

	private File tileFile(FlightGearBucket bucket) {
		return new File(new File(terrainRoot, bucket.genBasePath()), bucket.getIndex() + ".btg.gz");
	}

	/**
	 * Makes sure the {@code OBJECT_BASE} STG that references the rewritten tile
	 * exists next to it, mirroring {@code BtgTileWriter}.
	 */
	private void ensureTerrainStg(FlightGearBucket bucket) {
		File tileFile = tileFile(bucket);
		File stgFile = new File(tileFile.getParentFile(), bucket.getIndex() + ".stg");
		try {
			Files.write(stgFile.toPath(),
					("OBJECT_BASE " + bucket.getIndex() + ".btg\n").getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			Osm2xpLogger.error("BTG patch pipeline: error writing terrain STG " + stgFile, e);
		}
	}
}
