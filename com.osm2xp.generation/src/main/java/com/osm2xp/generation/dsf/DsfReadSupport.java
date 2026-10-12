package com.osm2xp.generation.dsf;

import java.util.ArrayList;
import java.util.List;

/**
 * Low level binary helpers for reading DSF atoms and planar numeric tables.
 */
final class DsfReadSupport {

	private DsfReadSupport() {
	}

	static final class Atom {
		final int id;
		final byte[] data;
		final int start;
		final int length;

		Atom(int id, byte[] data, int start, int length) {
			this.id = id;
			this.data = data;
			this.start = start;
			this.length = length;
		}

		int contentStart() {
			return start + 8;
		}

		int contentLength() {
			return length - 8;
		}

		int end() {
			return start + length;
		}

		List<Atom> children() {
			return parseContainer(data, contentStart(), end());
		}

		boolean hasId(int wanted) {
			return id == wanted;
		}
	}

	static List<Atom> parseContainer(byte[] data, int start, int end) {
		List<Atom> atoms = new ArrayList<>();
		int pos = start;
		while (pos < end) {
			if (pos + 8 > end) {
				throw new DsfConversionException("DSF atom header overruns container");
			}
			int id = readInt(data, pos);
			int length = readInt(data, pos + 4);
			if (length < 8 || pos + length > end) {
				throw new DsfConversionException("DSF atom has invalid length " + length + " at " + pos);
			}
			atoms.add(new Atom(id, data, pos, length));
			pos += length;
		}
		return atoms;
	}

	static Atom find(List<Atom> atoms, int id) {
		for (Atom a : atoms) {
			if (a.id == id) {
				return a;
			}
		}
		return null;
	}

	static List<Atom> all(List<Atom> atoms, int id) {
		List<Atom> result = new ArrayList<>();
		for (Atom a : atoms) {
			if (a.id == id) {
				result.add(a);
			}
		}
		return result;
	}

	static List<String> strings(byte[] data, int start, int end) {
		List<String> result = new ArrayList<>();
		int pos = start;
		while (pos < end) {
			int zero = pos;
			while (zero < end && data[zero] != 0) {
				zero++;
			}
			if (zero >= end) {
				break;
			}
			result.add(new String(data, pos, zero - pos, java.nio.charset.StandardCharsets.UTF_8));
			pos = zero + 1;
		}
		return result;
	}

	static int readInt(byte[] data, int offset) {
		return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8)
				| ((data[offset + 2] & 0xFF) << 16) | ((data[offset + 3] & 0xFF) << 24);
	}

	static final class Cursor {
		final byte[] data;
		int position;
		final int end;

		Cursor(byte[] data, int start, int end) {
			this.data = data;
			this.position = start;
			this.end = end;
		}

		boolean done() {
			return position >= end;
		}

		boolean overrun() {
			return position > end;
		}

		int readU8() {
			check(1);
			return data[position++] & 0xFF;
		}

		int readU16() {
			check(2);
			int v = (data[position] & 0xFF) | ((data[position + 1] & 0xFF) << 8);
			position += 2;
			return v;
		}

		int readS32() {
			return (int) readU32();
		}

		long readU32() {
			check(4);
			long v = (data[position] & 0xFFL) | ((data[position + 1] & 0xFFL) << 8)
					| ((data[position + 2] & 0xFFL) << 16) | ((data[position + 3] & 0xFFL) << 24);
			position += 4;
			return v;
		}

		float readF32() {
			return Float.intBitsToFloat((int) readU32());
		}

		void advance(int bytes) {
			position += bytes;
		}

		private void check(int bytes) {
			if (position + bytes > end) {
				throw new DsfConversionException("DSF read out of bounds");
			}
		}
	}

	static final class PlanarData {
		final int planeCount;
		final int planeSize;
		final double[] values;

		PlanarData(int planeCount, int planeSize, double[] values) {
			this.planeCount = planeCount;
			this.planeSize = planeSize;
			this.values = values;
		}
	}

	static PlanarData decodePlanar(byte[] data, int atomContentStart, int atomEnd, boolean is32,
			double[] scales, double[] offsets, double recip) {
		Cursor cur = new Cursor(data, atomContentStart, atomEnd);
		int planeSize = (int) cur.readU32();
		int planeCount = cur.readU8();
		double[] values = new double[planeSize * planeCount];
		long mask = is32 ? 0xFFFFFFFFL : 0xFFFFL;
		for (int plane = 0; plane < planeCount; plane++) {
			if (cur.done()) {
				break;
			}
			double scale = scales != null && plane < scales.length ? scales[plane] : 0.0;
			double offset = offsets != null && plane < offsets.length ? offsets[plane] : 0.0;
			int mode = cur.readU8();
			long last = 0;
			RleDecoder rleDecoder = new RleDecoder();
			for (int i = 0; i < planeSize; i++) {
				long raw;
				switch (mode) {
				case DsfConstants.ENCODE_RAW:
					raw = readFlat(cur, is32);
					break;
				case DsfConstants.ENCODE_DIFFERENCED:
					raw = (last + readFlat(cur, is32)) & mask;
					last = raw;
					break;
				case DsfConstants.ENCODE_RLE:
					raw = rleDecoder.fetch(cur, is32);
					break;
				case DsfConstants.ENCODE_RLE_DIFFERENCED:
					raw = (last + rleDecoder.fetch(cur, is32)) & mask;
					last = raw;
					break;
				default:
					throw new DsfConversionException("Unknown planar encode mode " + mode);
				}
				values[i * planeCount + plane] = scale != 0.0 ? raw * scale * recip + offset : raw;
			}
		}
		return new PlanarData(planeCount, planeSize, values);
	}

	private static long readFlat(Cursor cur, boolean is32) {
		return is32 ? cur.readU32() : cur.readU16();
	}

	private static final class RleDecoder {
		private boolean isRun;
		private boolean isIndividual;
		private int runLength;

		long fetch(Cursor cur, boolean is32) {
			if (isRun) {
				long v = peek(cur, is32);
				if (runLength == 1) {
					cur.advance(is32 ? 4 : 2);
					isRun = false;
				} else {
					runLength--;
				}
				return v;
			} else if (isIndividual) {
				long v = readFlat(cur, is32);
				if (runLength == 1) {
					isIndividual = false;
				} else {
					runLength--;
				}
				return v;
			} else {
				int code = cur.readU8();
				if ((code & 0x80) != 0) {
					isRun = true;
				} else {
					isIndividual = true;
				}
				runLength = code & 0x7F;
				return fetch(cur, is32);
			}
		}

		private static long peek(Cursor cur, boolean is32) {
			int saved = cur.position;
			long v = readFlat(cur, is32);
			cur.position = saved;
			return v;
		}
	}
}
