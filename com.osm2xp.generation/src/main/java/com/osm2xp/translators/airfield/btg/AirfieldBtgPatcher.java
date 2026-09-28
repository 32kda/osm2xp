package com.osm2xp.translators.airfield.btg;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.translators.airfield.AirfieldData;
import com.osm2xp.translators.flightgear.FlightGearBucket;
import com.osm2xp.translators.flightgear.btg.BtgPatchPhase;
import com.osm2xp.translators.flightgear.btg.BtgTilePatcher;
import com.osm2xp.translators.flightgear.btg.BtgTilePayload;

/**
 * {@link BtgTilePatcher} for the {@link BtgPatchPhase#TERRAIN_SHAPE} phase:
 * contributes the cut/filled airfield terrain tiles computed by
 * {@link FlightGearAirportCutWriter} and applies them to the pipeline payload.
 * <p>
 * Airfields are contributed by {@code FlightGearAirfieldTranslationAdapter} once
 * all airport geometry is known; the actual tile write happens later, in
 * {@code BtgPatchPipeline}, so that vegetation (a later phase) composes on top.
 *
 * @author osm2xp
 */
public final class AirfieldBtgPatcher implements BtgTilePatcher {

	private final Map<FlightGearBucket, BtgTile> tiles = new HashMap<>();

	/** Registers the patched tiles of one airfield (idempotent per bucket). */
	public void contribute(AirfieldData airfield, File sourceTerrainDir, boolean cutTerrain) {
		tiles.putAll(new FlightGearAirportCutWriter().buildPatchedTiles(airfield, sourceTerrainDir, cutTerrain));
	}

	@Override
	public BtgPatchPhase phase() {
		return BtgPatchPhase.TERRAIN_SHAPE;
	}

	@Override
	public Set<FlightGearBucket> affectedBuckets() {
		return tiles.keySet();
	}

	@Override
	public boolean hasWorkFor(FlightGearBucket bucket) {
		return tiles.containsKey(bucket);
	}

	@Override
	public void apply(BtgTilePayload payload) {
		BtgTile tile = tiles.get(payload.bucket());
		if (tile != null) {
			payload.setModel(tile);
		}
	}
}
