package com.osm2xp.generation.dsf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Low level binary helpers for writing DSF atoms and planar numeric tables.
 */
final class DsfWriteSupport {

	private DsfWriteSupport() {
	}

	static byte[] buildAtom(int id, byte[] content) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(content.length + 8);
		writeInt32(out, id);
		writeInt32(out, content.length + 8);
		out.write(content, 0, content.length);
		return out.toByteArray();
	}

	static byte[] stringTable(List<String> strings) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (String s : strings) {
			byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
			out.write(bytes, 0, bytes.length);
			out.write(0);
		}
		return out.toByteArray();
	}

	static byte[] planarShort(int numberOfPlanes, int planeSize, int[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeInt32(out, planeSize);
		out.write(numberOfPlanes & 0xFF);
		long[] plane = new long[planeSize];
		for (int p = 0; p < numberOfPlanes; p++) {
			out.write(DsfConstants.ENCODE_RLE_DIFFERENCED);
			long last = 0;
			for (int i = 0; i < planeSize; i++) {
				long value = data[i * numberOfPlanes + p] & 0xFFFFL;
				plane[i] = (short) (value - last);
				last = value;
			}
			rleEncode(out, plane, planeSize, 2);
		}
		return out.toByteArray();
	}

	static byte[] planarInt(int numberOfPlanes, int planeSize, long[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeInt32(out, planeSize);
		out.write(numberOfPlanes & 0xFF);
		long[] plane = new long[planeSize];
		for (int p = 0; p < numberOfPlanes; p++) {
			out.write(DsfConstants.ENCODE_RLE_DIFFERENCED);
			long last = 0;
			for (int i = 0; i < planeSize; i++) {
				long value = data[i * numberOfPlanes + p] & 0xFFFFFFFFL;
				plane[i] = (int) (value - last);
				last = value;
			}
			rleEncode(out, plane, planeSize, 4);
		}
		return out.toByteArray();
	}

	private static void rleEncode(ByteArrayOutputStream out, long[] values, int count, int width) {
		List<Long> run = new ArrayList<>();
		boolean isRun = false;
		boolean isIndividual = false;
		int runLength = 0;
		for (int i = 0; i < count; i++) {
			long value = values[i];
			if (isRun) {
				if (value == run.get(run.size() - 1)) {
					if (runLength == 127) {
						emitRun(out, run.get(0), runLength, width);
						isRun = false;
						run.clear();
						run.add(value);
					} else {
						runLength++;
					}
				} else {
					emitRun(out, run.get(0), runLength, width);
					isRun = false;
					run.clear();
					run.add(value);
				}
			} else if (isIndividual) {
				if (value != run.get(run.size() - 1)) {
					if (run.size() == 127) {
						emitIndividual(out, run, width);
						isIndividual = false;
						run.clear();
						run.add(value);
					} else {
						run.add(value);
					}
				} else {
					run.remove(run.size() - 1);
					emitIndividual(out, run, width);
					isIndividual = false;
					isRun = true;
					run.clear();
					run.add(value);
					runLength = 2;
				}
			} else {
				if (!run.isEmpty()) {
					if (run.get(run.size() - 1) == value) {
						isRun = true;
						runLength = 2;
					} else {
						run.add(value);
						isIndividual = true;
					}
				} else {
					run.add(value);
				}
			}
		}
		if (isRun) {
			emitRun(out, run.get(0), runLength, width);
		} else if (isIndividual) {
			emitIndividual(out, run, width);
		} else if (!run.isEmpty()) {
			emitIndividual(out, run, width);
		}
	}

	private static void emitRun(ByteArrayOutputStream out, long value, int length, int width) {
		out.write(0x80 | (length & 0x7F));
		writeValue(out, value, width);
	}

	private static void emitIndividual(ByteArrayOutputStream out, List<Long> run, int width) {
		out.write(run.size() & 0x7F);
		for (Long value : run) {
			writeValue(out, value, width);
		}
	}

	static void writeValue(ByteArrayOutputStream out, long value, int width) {
		for (int i = 0; i < width; i++) {
			out.write((int) ((value >>> (8 * i)) & 0xFF));
		}
	}

	static void writeInt32(ByteArrayOutputStream out, long value) {
		writeValue(out, value, 4);
	}

	static void writeFloat(ByteArrayOutputStream out, float value) {
		writeValue(out, Float.floatToIntBits(value) & 0xFFFFFFFFL, 4);
	}
}
