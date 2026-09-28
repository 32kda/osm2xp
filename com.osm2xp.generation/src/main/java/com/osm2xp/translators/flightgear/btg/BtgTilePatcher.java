package com.osm2xp.translators.flightgear.btg;

import java.io.IOException;
import java.util.Set;

import com.osm2xp.translators.flightgear.FlightGearBucket;

/**
 * A single stage of the {@link BtgPatchPipeline}. Implementations declare the
 * buckets they have work for and mutate the shared {@link BtgTilePayload} in
 * place; the pipeline orders stages by {@link BtgPatchPhase} and writes each
 * tile at most once.
 *
 * @author osm2xp
 */
public interface BtgTilePatcher {

	/** The phase this patcher runs in. */
	BtgPatchPhase phase();

	/** All buckets this patcher wants to touch. */
	Set<FlightGearBucket> affectedBuckets();

	/** Whether this patcher has anything to do for the given bucket. */
	boolean hasWorkFor(FlightGearBucket bucket);

	/** Applies this patcher's changes to the tile payload. */
	void apply(BtgTilePayload payload) throws IOException;
}
