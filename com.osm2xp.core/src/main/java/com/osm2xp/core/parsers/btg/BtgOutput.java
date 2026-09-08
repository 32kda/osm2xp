package com.osm2xp.core.parsers.btg;

import java.io.ByteArrayOutputStream;

/**
 * Little-endian output buffer. Internal helper, symmetric to {@link BtgReader},
 * that keeps the binary writing logic in one place.
 */
final class BtgOutput {

	private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	void writeU8(int value) {
		buffer.write(value & 0xFF);
	}

	void writeU16(int value) {
		buffer.write(value & 0xFF);
		buffer.write((value >>> 8) & 0xFF);
	}

	void writeU32(long value) {
		buffer.write((int) (value & 0xFF));
		buffer.write((int) ((value >>> 8) & 0xFF));
		buffer.write((int) ((value >>> 16) & 0xFF));
		buffer.write((int) ((value >>> 24) & 0xFF));
	}

	void writeF32(float value) {
		writeU32(Float.floatToRawIntBits(value) & 0xFFFFFFFFL);
	}

	void writeF64(double value) {
		long bits = Double.doubleToRawLongBits(value);
		buffer.write((int) (bits & 0xFF));
		buffer.write((int) ((bits >>> 8) & 0xFF));
		buffer.write((int) ((bits >>> 16) & 0xFF));
		buffer.write((int) ((bits >>> 24) & 0xFF));
		buffer.write((int) ((bits >>> 32) & 0xFF));
		buffer.write((int) ((bits >>> 40) & 0xFF));
		buffer.write((int) ((bits >>> 48) & 0xFF));
		buffer.write((int) ((bits >>> 56) & 0xFF));
	}

	void writeBytes(byte[] data) {
		buffer.write(data, 0, data.length);
	}

	byte[] toByteArray() {
		return buffer.toByteArray();
	}
}
