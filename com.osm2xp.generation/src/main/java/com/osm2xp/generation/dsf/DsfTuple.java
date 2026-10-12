package com.osm2xp.generation.dsf;

import java.util.Arrays;

/**
 * An N-dimensional DSF coordinate. Mirrors DSFTuple from XPTools.
 */
public final class DsfTuple {

	public static final int MAX_LEN = 9;

	private final double[] values;
	private final int length;

	public DsfTuple(int planes) {
		if (planes > MAX_LEN) {
			throw new DsfConversionException("Overrun DSF tuple: " + planes);
		}
		this.length = planes;
		this.values = new double[planes];
	}

	public DsfTuple(double[] input, int length) {
		if (length > MAX_LEN) {
			throw new DsfConversionException("Overrun DSF tuple: " + length);
		}
		this.length = length;
		this.values = new double[length];
		System.arraycopy(input, 0, this.values, 0, length);
	}

	private DsfTuple(double[] values, int length, boolean copy) {
		this.length = length;
		this.values = values;
	}

	public DsfTuple copy() {
		return new DsfTuple(values.clone(), length, false);
	}

	public int size() {
		return length;
	}

	public double get(int index) {
		return values[index];
	}

	public void set(int index, double value) {
		values[index] = value;
	}

	public double[] toArray() {
		return values.clone();
	}

	public DsfTuple plus(DsfTuple rhs) {
		DsfTuple result = new DsfTuple(length);
		for (int i = 0; i < length; i++) {
			result.values[i] = values[i] + rhs.values[i];
		}
		return result;
	}

	public DsfTuple minus(DsfTuple rhs) {
		DsfTuple result = new DsfTuple(length);
		for (int i = 0; i < length; i++) {
			result.values[i] = values[i] - rhs.values[i];
		}
		return result;
	}

	public DsfTuple times(DsfTuple rhs) {
		DsfTuple result = new DsfTuple(length);
		for (int i = 0; i < length; i++) {
			result.values[i] = values[i] * rhs.values[i];
		}
		return result;
	}

	public boolean encode(DsfTuple offset, DsfTuple scale) {
		if (size() != offset.size() || size() != scale.size()) {
			return false;
		}
		for (int i = 0; i < length; i++) {
			double k = scale.values[i];
			if (k != 0) {
				values[i] = ((values[i] - offset.values[i]) * 65535.0 / k);
			}
			if (values[i] < 0.0 || values[i] > 65535.0) {
				return false;
			}
		}
		return true;
	}

	public boolean encode32(DsfTuple offset, DsfTuple scale) {
		if (size() != offset.size() || size() != scale.size()) {
			return false;
		}
		for (int i = 0; i < length; i++) {
			double k = scale.values[i];
			if (k != 0) {
				values[i] = ((values[i] - offset.values[i]) * 4294967295.0 / k);
			}
			if (values[i] < 0.0 || values[i] > 4294967295.0) {
				return false;
			}
		}
		return true;
	}

	public boolean inRange(DsfTuple offset, DsfTuple scale) {
		if (size() != offset.size() || size() != scale.size()) {
			return false;
		}
		for (int i = 0; i < length; i++) {
			double v = values[i];
			double o = offset.values[i];
			double k = scale.values[i];
			if (v < o) {
				return false;
			}
			if (k != 0 && v > (o + k)) {
				return false;
			}
			if (k == 0 && v > 65535.0) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof DsfTuple)) {
			return false;
		}
		DsfTuple other = (DsfTuple) obj;
		if (length != other.length) {
			return false;
		}
		for (int i = 0; i < length; i++) {
			if (Double.doubleToLongBits(values[i]) != Double.doubleToLongBits(other.values[i])) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		int result = 1;
		for (int i = 0; i < length; i++) {
			long bits = Double.doubleToLongBits(values[i]);
			result = 31 * result + (int) (bits ^ (bits >>> 32));
		}
		return result;
	}

	@Override
	public String toString() {
		return Arrays.toString(Arrays.copyOf(values, length));
	}
}
