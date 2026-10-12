package com.osm2xp.generation.dsf;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Contiguous 16-bit point pool used for objects and polygons. Ported from
 * DSFContiguousPointPool.
 */
final class ContiguousPointPool {

	static final class SubPool {
		DsfTuple offset;
		DsfTuple scale;
		final List<DsfTuple> points = new ArrayList<>();
	}

	private DsfTuple min;
	private DsfTuple max;
	private final List<SubPool> pools = new ArrayList<>();
	private final List<Integer> usageMapping = new ArrayList<>();

	void setRange(DsfTuple newMin, DsfTuple newMax) {
		this.min = newMin;
		this.max = newMax;
	}

	void addPool(DsfTuple minFrac, DsfTuple maxFrac) {
		DsfTuple subMin = min.plus(minFrac.times(max.minus(min)));
		DsfTuple subMax = min.plus(maxFrac.times(max.minus(min)));
		addPoolDirect(subMin, subMax);
	}

	void addPoolDirect(DsfTuple subMin, DsfTuple subMax) {
		SubPool pool = new SubPool();
		pool.offset = subMin;
		pool.scale = subMax.minus(subMin);
		pools.add(pool);
	}

	int[] accumulatePoints(List<DsfTuple> inPoints) {
		for (int p = 0; p < pools.size(); p++) {
			SubPool pool = pools.get(p);
			if ((pool.points.size() + inPoints.size()) > 65535) {
				continue;
			}
			boolean ok = true;
			for (DsfTuple point : inPoints) {
				if (!point.inRange(pool.offset, pool.scale)) {
					ok = false;
					break;
				}
			}
			if (ok) {
				List<DsfTuple> encoded = new ArrayList<>(inPoints.size());
				for (DsfTuple point : inPoints) {
					DsfTuple copy = point.copy();
					if (!copy.encode(pool.offset, pool.scale)) {
						ok = false;
						break;
					}
					encoded.add(copy);
				}
				if (!ok) {
					continue;
				}
				int pos = pool.points.size();
				pool.points.addAll(encoded);
				return new int[] { p, pos };
			}
		}
		return null;
	}

	void processPoints() {
		int newPool = 0;
		for (int i = 0; i < pools.size();) {
			if (pools.get(i).points.isEmpty()) {
				pools.remove(i);
				usageMapping.add(-1);
			} else {
				usageMapping.add(newPool);
				i++;
				newPool++;
			}
		}
	}

	int mapPoolNumber(int n) {
		return usageMapping.get(n);
	}

	int writePoolAtoms(ByteArrayOutputStream out) {
		for (SubPool pool : pools) {
			int[] data = new int[pool.points.size() * pool.scale.size()];
			int index = 0;
			for (DsfTuple point : pool.points) {
				for (int j = 0; j < point.size(); j++) {
					data[index++] = (int) point.get(j);
				}
			}
			byte[] content = DsfWriteSupport.planarShort(pool.scale.size(), pool.points.size(), data);
			byte[] atom = DsfWriteSupport.buildAtom(DsfConstants.ATOM_POOL, content);
			out.write(atom, 0, atom.length);
		}
		return pools.size();
	}

	int writeScaleAtoms(ByteArrayOutputStream out) {
		for (SubPool pool : pools) {
			ByteArrayOutputStream content = new ByteArrayOutputStream();
			for (int d = 0; d < pool.scale.size(); d++) {
				DsfWriteSupport.writeFloat(content, (float) pool.scale.get(d));
				DsfWriteSupport.writeFloat(content, (float) pool.offset.get(d));
			}
			byte[] atom = DsfWriteSupport.buildAtom(DsfConstants.ATOM_SCAL, content.toByteArray());
			out.write(atom, 0, atom.length);
		}
		return pools.size();
	}
}
