package com.osm2xp.generation.dsf;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 32-bit contiguous point pool used for network vectors. Ported from
 * DSF32BitPointPool.
 */
final class Pool32 {

	private DsfTuple offset = new DsfTuple(0);
	private DsfTuple scale = new DsfTuple(0);
	private final List<DsfTuple> points = new ArrayList<>();
	private final Map<DsfTuple, Integer> pointsIndex = new HashMap<>();

	void setRange(DsfTuple min, DsfTuple max) {
		this.offset = min;
		this.scale = max.minus(min);
	}

	int countShared(List<DsfTuple> inPoints) {
		int count = 0;
		for (DsfTuple point : inPoints) {
			DsfTuple encoded = point.copy();
			if (!encoded.encode32(offset, scale)) {
				return -1;
			}
			if (pointsIndex.containsKey(encoded)) {
				count++;
			}
		}
		return count;
	}

	int[] acceptContiguous(List<DsfTuple> inPoints) {
		int[] result = new int[] { 0, points.size() };
		for (DsfTuple point : inPoints) {
			DsfTuple encoded = point.copy();
			if (!encoded.encode32(offset, scale)) {
				return null;
			}
			pointsIndex.put(encoded, points.size());
			points.add(encoded);
		}
		return result;
	}

	int[] acceptShared(DsfTuple inPoint) {
		DsfTuple encoded = inPoint.copy();
		if (!encoded.encode32(offset, scale)) {
			return null;
		}
		Integer existing = pointsIndex.get(encoded);
		if (existing != null) {
			return new int[] { 0, existing };
		}
		int[] result = new int[] { 0, points.size() };
		pointsIndex.put(encoded, points.size());
		points.add(encoded);
		return result;
	}

	int writePoolAtoms(ByteArrayOutputStream out) {
		long[] data = new long[points.size() * scale.size()];
		int index = 0;
		for (DsfTuple point : points) {
			for (int j = 0; j < point.size(); j++) {
				data[index++] = (long) point.get(j) & 0xFFFFFFFFL;
			}
		}
		byte[] content = DsfWriteSupport.planarInt(scale.size(), points.size(), data);
		byte[] atom = DsfWriteSupport.buildAtom(DsfConstants.ATOM_PO32, content);
		out.write(atom, 0, atom.length);
		return 1;
	}

	int writeScaleAtoms(ByteArrayOutputStream out) {
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		for (int d = 0; d < scale.size(); d++) {
			DsfWriteSupport.writeFloat(content, (float) scale.get(d));
			DsfWriteSupport.writeFloat(content, (float) offset.get(d));
		}
		byte[] atom = DsfWriteSupport.buildAtom(DsfConstants.ATOM_SC32, content.toByteArray());
		out.write(atom, 0, atom.length);
		return 1;
	}
}
