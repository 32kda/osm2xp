package com.osm2xp.core.parsers.btg;

/**
 * Immutable 3D vector of doubles. Used to represent the BTG tile center
 * (ECEF coordinates) and useful for any cartographic math the caller wants to
 * perform on the parsed tile.
 */
public final class BtgVector3 {

	private final double x;
	private final double y;
	private final double z;

	public BtgVector3(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getZ() {
		return z;
	}

	/** Euclidean length of this vector. */
	public double length() {
		return Math.sqrt(x * x + y * y + z * z);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof BtgVector3)) {
			return false;
		}
		BtgVector3 other = (BtgVector3) obj;
		return Double.compare(x, other.x) == 0 && Double.compare(y, other.y) == 0
				&& Double.compare(z, other.z) == 0;
	}

	@Override
	public int hashCode() {
		int result = 1;
		long bits = Double.doubleToLongBits(x);
		result = 31 * result + (int) (bits ^ (bits >>> 32));
		bits = Double.doubleToLongBits(y);
		result = 31 * result + (int) (bits ^ (bits >>> 32));
		bits = Double.doubleToLongBits(z);
		result = 31 * result + (int) (bits ^ (bits >>> 32));
		return result;
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ")";
	}
}
