package com.osm2xp.core.parsers.btg;

import java.util.Arrays;

/**
 * Immutable group of points (FlightGear {@code SG_POINTS}) sharing one
 * material. The indices reference {@link BtgTile#getVertices()}.
 */
public final class BtgPointGroup {

	private final String material;
	private final int[] indices;

	public BtgPointGroup(String material, int[] indices) {
		this.material = material == null ? "" : material;
		this.indices = indices == null ? new int[0] : indices.clone();
	}

	/** Material name for this point group (never {@code null}, may be empty). */
	public String getMaterial() {
		return material;
	}

	/** Vertex indices belonging to this group. */
	public int[] getIndices() {
		return indices.clone();
	}

	/** Number of points in this group. */
	public int getPointCount() {
		return indices.length;
	}

	@Override
	public String toString() {
		return "BtgPointGroup[material=" + material + ", points=" + indices.length + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof BtgPointGroup)) {
			return false;
		}
		BtgPointGroup other = (BtgPointGroup) obj;
		return material.equals(other.material) && Arrays.equals(indices, other.indices);
	}

	@Override
	public int hashCode() {
		int result = material.hashCode();
		result = 31 * result + Arrays.hashCode(indices);
		return result;
	}
}
