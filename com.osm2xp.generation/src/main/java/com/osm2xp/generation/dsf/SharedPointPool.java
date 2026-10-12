package com.osm2xp.generation.dsf;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared 16-bit point pool used for terrain patches. Ported from
 * DSFSharedPointPool.
 */
final class SharedPointPool {

	private static final class SubPool {
		DsfTuple offset;
		DsfTuple scale;
		final List<DsfTuple> points = new ArrayList<>();
		final Map<DsfTuple, Integer> pointsIndex = new HashMap<>();
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

	boolean canBeContiguous(List<DsfTuple> inPoints) {
		for (SubPool pool : pools) {
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
				return true;
			}
		}
		return false;
	}

	int countShared(List<DsfTuple> inPoints) {
		int count = 0;
		for (DsfTuple point : inPoints) {
			for (SubPool pool : pools) {
				DsfTuple encoded = point.copy();
				if (encoded.encode(pool.offset, pool.scale) && pool.pointsIndex.containsKey(encoded)) {
					count++;
				}
			}
		}
		return count;
	}

	int[] acceptContiguous(List<DsfTuple> inPoints) {
		int firstOkPool = -1;
		SubPool found = null;
		for (int p = 0; p < pools.size(); p++) {
			SubPool pool = pools.get(p);
			if ((pool.points.size() + inPoints.size()) > 65535) {
				continue;
			}
			boolean ok = true;
			List<DsfTuple> encoded = new ArrayList<>(inPoints.size());
			for (DsfTuple point : inPoints) {
				DsfTuple copy = point.copy();
				if (!copy.encode(pool.offset, pool.scale)) {
					ok = false;
					break;
				}
				encoded.add(copy);
			}
			if (ok) {
				for (DsfTuple e : encoded) {
					if (pool.pointsIndex.containsKey(e)) {
						return null;
					}
				}
				found = pool;
				firstOkPool = p;
			}
		}
		if (firstOkPool != -1) {
			return acceptContiguousPool(firstOkPool, found, inPoints);
		}
		return null;
	}

	private int[] acceptContiguousPool(int p, SubPool pool, List<DsfTuple> inPoints) {
		int[] result = new int[] { p, pool.points.size() };
		for (DsfTuple point : inPoints) {
			DsfTuple encoded = point.copy();
			encoded.encode(pool.offset, pool.scale);
			pool.pointsIndex.put(encoded, pool.points.size());
			pool.points.add(encoded);
		}
		return result;
	}

	int[] acceptShared(DsfTuple inPoint) {
		int p = 0;
		for (SubPool pool : pools) {
			DsfTuple encoded = inPoint.copy();
			if (encoded.encode(pool.offset, pool.scale)) {
				Integer existing = pool.pointsIndex.get(encoded);
				if (existing != null) {
					return new int[] { p, existing };
				}
			}
			p++;
		}
		p = 0;
		SubPool exemplar = null;
		for (SubPool pool : pools) {
			DsfTuple encoded = inPoint.copy();
			if (encoded.encode(pool.offset, pool.scale)) {
				if (pool.points.size() < 65535) {
					int pos = pool.points.size();
					pool.points.add(encoded);
					pool.pointsIndex.put(encoded, pos);
					return new int[] { p, pos };
				} else if (exemplar == null) {
					exemplar = pool;
				}
			}
			p++;
		}
		if (exemplar != null) {
			DsfTuple encoded = inPoint.copy();
			if (!encoded.encode(exemplar.offset, exemplar.scale)) {
				throw new DsfConversionException("Failed to re-encode into copied pool");
			}
			SubPool clone = new SubPool();
			clone.offset = exemplar.offset;
			clone.scale = exemplar.scale;
			pools.add(clone);
			int pos = clone.points.size();
			clone.points.add(encoded);
			clone.pointsIndex.put(encoded, pos);
			return new int[] { pools.size() - 1, pos };
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

	int count() {
		int total = 0;
		for (SubPool pool : pools) {
			total += pool.points.size();
		}
		return total;
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
