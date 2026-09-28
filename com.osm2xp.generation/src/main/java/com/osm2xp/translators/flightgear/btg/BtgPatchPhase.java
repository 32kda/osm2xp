package com.osm2xp.translators.flightgear.btg;

/**
 * Ordering of FlightGear BTG tile patch stages. Stages are applied to a tile in
 * ascending {@link #getOrder()} order, so later phases observe the result of
 * earlier ones (e.g. vegetation is placed on the terrain shape produced by the
 * airfield stage).
 *
 * @author osm2xp
 */
public enum BtgPatchPhase {

	/** Terrain shape changes: airfield cut/fill, plate and skirt. */
	TERRAIN_SHAPE(100),

	/** Adds vegetation point groups ({@code SG_POINTS}) to the terrain. */
	VEGETATION(200),

	/** Reserved for future stages that must run after vegetation. */
	POST_PROCESS(1000);

	private final int order;

	BtgPatchPhase(int order) {
		this.order = order;
	}

	public int getOrder() {
		return order;
	}
}
