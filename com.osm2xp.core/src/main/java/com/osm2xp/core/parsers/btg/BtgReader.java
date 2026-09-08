package com.osm2xp.core.parsers.btg;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Little-endian reader over a byte buffer. Internal helper that keeps the
 * binary reading logic in one place (DRY) and raises a {@link BtgException}
 * with a consistent message on truncation.
 */
final class BtgReader {

	private final ByteBuffer buffer;

	BtgReader(byte[] data) {
		this(ByteBuffer.wrap(data));
	}

	BtgReader(ByteBuffer buffer) {
		this.buffer = buffer.order(ByteOrder.LITTLE_ENDIAN);
	}

	int remaining() {
		return buffer.remaining();
	}

	boolean hasRemaining(int count) {
		return buffer.remaining() >= count;
	}

	private void require(int count) throws BtgException {
		if (buffer.remaining() < count) {
			throw new BtgException("Unexpected end of BTG data: need " + count + " bytes, "
					+ buffer.remaining() + " remaining");
		}
	}

	int u8() throws BtgException {
		require(1);
		return buffer.get() & 0xFF;
	}

	int u16() throws BtgException {
		require(2);
		return buffer.getShort() & 0xFFFF;
	}

	long u32() throws BtgException {
		require(4);
		return buffer.getInt() & 0xFFFFFFFFL;
	}

	float f32() throws BtgException {
		require(4);
		return buffer.getFloat();
	}

	double f64() throws BtgException {
		require(8);
		return buffer.getDouble();
	}

	byte[] bytes(int count) throws BtgException {
		require(count);
		byte[] data = new byte[count];
		buffer.get(data);
		return data;
	}
}
