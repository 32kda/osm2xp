package com.osm2xp.generation.dsf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Accumulates DSF data and writes a valid binary DSF file. Ported from
 * DSFFileWriterImp (DSFLibWrite.cpp).
 */
public final class DsfWriter implements DsfCallbacks {

	static final class ObjectSpec {
		int type;
		int pool;
		int location;
		int filter;

		int compareTo(ObjectSpec rhs) {
			if (filter != rhs.filter) {
				return filter < rhs.filter ? -1 : 1;
			}
			if (type != rhs.type) {
				return type < rhs.type ? -1 : 1;
			}
			if (pool != rhs.pool) {
				return pool < rhs.pool ? -1 : 1;
			}
			return Integer.compare(location, rhs.location);
		}
	}

	static final class PolygonSpec {
		int type;
		int pool;
		int param;
		boolean isForest;
		int depth;
		int hashDepth;
		int filter;
		final List<Integer> intervals = new ArrayList<>();

		int compareTo(PolygonSpec rhs) {
			if (filter != rhs.filter) {
				return filter < rhs.filter ? -1 : 1;
			}
			if (hashDepth != rhs.hashDepth) {
				return hashDepth < rhs.hashDepth ? -1 : 1;
			}
			if (depth != rhs.depth) {
				return depth < rhs.depth ? -1 : 1;
			}
			if (type != rhs.type) {
				return type < rhs.type ? -1 : 1;
			}
			if (pool != rhs.pool) {
				return pool < rhs.pool ? -1 : 1;
			}
			return Integer.compare(param, rhs.param);
		}
	}

	static final class TriPrimitive {
		int type;
		boolean isRange;
		boolean isCrossPool;
		final List<DsfTuple> vertices = new ArrayList<>();
		final List<int[]> indices = new ArrayList<>();
	}

	static final class PatchSpec {
		double nearLOD;
		double farLOD;
		int type;
		int flags;
		int depth;
		final List<TriPrimitive> primitives = new ArrayList<>();
	}

	static final class ChainSpec {
		int startNode;
		int endNode;
		int type;
		int subType;
		boolean curved;
		boolean contiguous;
		final List<DsfTuple> path = new ArrayList<>();
		final List<int[]> indices = new ArrayList<>();
		int lowestIndex;
		int highestIndex;
		int filter;

		int compareTo(ChainSpec rhs) {
			if (filter != rhs.filter) {
				return filter < rhs.filter ? -1 : 1;
			}
			if (curved != rhs.curved) {
				return curved ? -1 : 1;
			}
			if (type != rhs.type) {
				return type < rhs.type ? -1 : 1;
			}
			if (subType != rhs.subType) {
				return subType < rhs.subType ? -1 : 1;
			}
			if (lowestIndex != rhs.lowestIndex) {
				return lowestIndex < rhs.lowestIndex ? -1 : 1;
			}
			return Integer.compare(highestIndex, rhs.highestIndex);
		}
	}

	private static final class RasterEntry {
		DsfRasterHeader header;
		byte[] data;
	}

	private final int divisions;
	private final double west;
	private final double south;
	private final double east;
	private final double north;
	private final double elevMin;
	private final double elevMax;

	private int currentFilter = -1;
	private int skippedPolygons;
	private int skippedObjects;
	private int skippedChains;

	public int getSkippedPolygons() {
		return skippedPolygons;
	}

	public int getSkippedObjects() {
		return skippedObjects;
	}

	public int getSkippedChains() {
		return skippedChains;
	}

	private final List<String> terrainDefs = new ArrayList<>();
	private final List<String> objectDefs = new ArrayList<>();
	private final List<String> polygonDefs = new ArrayList<>();
	private final List<String> networkDefs = new ArrayList<>();
	private final List<String> rasterDefs = new ArrayList<>();
	private final List<String> properties = new ArrayList<>();

	private final ContiguousPointPool objectPool = new ContiguousPointPool();
	private final ContiguousPointPool objectPool3d = new ContiguousPointPool();
	private final List<ObjectSpec> objects = new ArrayList<>();
	@SuppressWarnings("unchecked")
	private final List<ObjectSpec>[] objects3d = new List[] { new ArrayList<ObjectSpec>(), new ArrayList<ObjectSpec>() };

	private final Map<Integer, ContiguousPointPool> polygonPools = new TreeMap<>();
	private final List<PolygonSpec> polygons = new ArrayList<>();
	private PolygonSpec accumPoly;
	private final List<List<DsfTuple>> accumPolyWinding = new ArrayList<>();

	private final Map<Integer, SharedPointPool> terrainPool = new TreeMap<>();
	private final List<PatchSpec> patches = new ArrayList<>();
	private PatchSpec accumPatch;
	private TriPrimitive accumPrimitive;

	private final Pool32 vectorPool = new Pool32();
	private final Pool32 vectorPoolCurved = new Pool32();
	private final List<ChainSpec> chainSpecs = new ArrayList<>();
	private ChainSpec accumChain;

	private final List<RasterEntry> rasters = new ArrayList<>();

	public DsfWriter(double west, double south, double east, double north, double elevMin, double elevMax, int divisions) {
		this.divisions = divisions;
		this.west = west;
		this.south = south;
		this.east = east;
		this.north = north;
		this.elevMin = elevMin;
		this.elevMax = elevMax;

		DsfTuple vecMin = new DsfTuple(new double[] { west, south, -32768.0, 0.0 }, 4);
		DsfTuple vecMax = new DsfTuple(new double[] { east, north, 32767.0, 0.0 }, 4);
		vectorPool.setRange(vecMin, vecMax);

		DsfTuple objMin = new DsfTuple(new double[] { west, south, 0.0 }, 3);
		DsfTuple objMax = new DsfTuple(new double[] { east, north, 360.0 }, 3);
		objectPool.setRange(objMin, objMax);
		for (int i = 0; i < divisions; i++) {
			for (int j = 0; j < divisions; j++) {
				DsfTuple fracMin = new DsfTuple(new double[] { (double) i / divisions, (double) j / divisions, 0.0 }, 3);
				DsfTuple fracMax = new DsfTuple(
						new double[] { (double) (i + 1) / divisions, (double) (j + 1) / divisions, 1.0 }, 3);
				for (int k = 0; k < 8; k++) {
					objectPool.addPool(fracMin, fracMax);
				}
			}
		}

		DsfTuple obj3Min = new DsfTuple(new double[] { west, south, 0.0, elevMin }, 4);
		DsfTuple obj3Max = new DsfTuple(new double[] { east, north, 360.0, elevMax }, 4);
		objectPool3d.setRange(obj3Min, obj3Max);
		for (int i = 0; i < divisions; i++) {
			for (int j = 0; j < divisions; j++) {
				DsfTuple fracMin = new DsfTuple(
						new double[] { (double) i / divisions, (double) j / divisions, 0.0, 0.0 }, 4);
				DsfTuple fracMax = new DsfTuple(
						new double[] { (double) (i + 1) / divisions, (double) (j + 1) / divisions, 1.0, 1.0 }, 4);
				for (int k = 0; k < 4; k++) {
					objectPool3d.addPool(fracMin, fracMax);
				}
			}
		}
	}

	@Override
	public void acceptTerrainDef(String partialPath) {
		terrainDefs.add(partialPath);
	}

	@Override
	public void acceptObjectDef(String partialPath) {
		objectDefs.add(partialPath);
	}

	@Override
	public void acceptPolygonDef(String partialPath) {
		polygonDefs.add(partialPath);
	}

	@Override
	public void acceptNetworkDef(String partialPath) {
		networkDefs.add(partialPath);
	}

	@Override
	public void acceptRasterDef(String partialPath) {
		rasterDefs.add(partialPath);
	}

	@Override
	public void acceptProperty(String name, String value) {
		properties.add(name);
		properties.add(value);
	}

	@Override
	public void beginPatch(int terrainType, double nearLOD, double farLOD, int flags, int coordDepth) {
		if (!terrainPool.containsKey(coordDepth)) {
			SharedPointPool pool = new SharedPointPool();
			DsfTuple rangeMin = new DsfTuple(coordDepth);
			DsfTuple rangeMax = new DsfTuple(coordDepth);
			rangeMin.set(0, west);
			rangeMin.set(1, south);
			rangeMin.set(2, elevMin);
			rangeMin.set(3, -1.0);
			rangeMin.set(4, -1.0);
			rangeMax.set(0, east);
			rangeMax.set(1, north);
			rangeMax.set(2, elevMax);
			rangeMax.set(3, 1.0);
			rangeMax.set(4, 1.0);
			for (int i = 0; i < divisions; i++) {
				for (int j = 0; j < divisions; j++) {
					DsfTuple fracMin = new DsfTuple(coordDepth);
					DsfTuple fracMax = new DsfTuple(coordDepth);
					fracMin.set(0, (double) i / divisions);
					fracMin.set(1, (double) j / divisions);
					fracMax.set(0, (double) (i + 1) / divisions);
					fracMax.set(1, (double) (j + 1) / divisions);
					for (int k = 2; k < coordDepth; k++) {
						fracMin.set(k, 0.0);
						fracMax.set(k, 1.0);
					}
					pool.addPool(fracMin, fracMax);
				}
			}
			terrainPool.put(coordDepth, pool);
		}
		PatchSpec patch = new PatchSpec();
		patch.nearLOD = nearLOD;
		patch.farLOD = farLOD;
		patch.type = terrainType;
		patch.flags = flags;
		patch.depth = coordDepth;
		patches.add(patch);
		accumPatch = patch;
	}

	@Override
	public void beginPrimitive(int type) {
		TriPrimitive primitive = new TriPrimitive();
		primitive.type = type;
		accumPatch.primitives.add(primitive);
		accumPrimitive = primitive;
	}

	@Override
	public void addPatchVertex(double[] coords) {
		accumPrimitive.vertices.add(new DsfTuple(coords, accumPatch.depth));
	}

	@Override
	public void endPrimitive() {
		if (accumPrimitive.vertices.isEmpty()) {
			accumPatch.primitives.remove(accumPatch.primitives.size() - 1);
		}
	}

	@Override
	public void endPatch() {
		if (accumPatch.primitives.isEmpty()) {
			patches.remove(patches.size() - 1);
		} else {
			optimizePrimitives(accumPatch.primitives);
		}
	}

	@Override
	public void addObject(int objectType, double[] coords, int mode) {
		int depth = mode == DsfConstants.MODE_DRAPED ? 3 : 4;
		DsfTuple tuple = new DsfTuple(coords, depth);
		int[] loc = depth == 4 ? objectPool3d.accumulatePoints(Collections.singletonList(tuple))
				: objectPool.accumulatePoints(Collections.singletonList(tuple));
		if (loc == null) {
			skippedObjects++;
			return;
		}
		ObjectSpec spec = new ObjectSpec();
		spec.filter = currentFilter;
		spec.type = objectType;
		spec.pool = loc[0];
		spec.location = loc[1];
		if (mode == DsfConstants.MODE_AGL) {
			objects3d[1].add(spec);
		} else if (mode == DsfConstants.MODE_MSL) {
			objects3d[0].add(spec);
		} else {
			objects.add(spec);
		}
	}

	@Override
	public void beginSegment(int networkType, int networkSubtype, double[] coords, boolean curved) {
		ChainSpec chain = new ChainSpec();
		chain.type = networkType;
		chain.filter = currentFilter;
		chain.subType = networkSubtype;
		chain.curved = curved;
		chain.contiguous = false;
		chain.startNode = (int) coords[3];
		chain.path.add(new DsfTuple(coords, curved ? 7 : 4));
		chainSpecs.add(chain);
		accumChain = chain;
	}

	@Override
	public void addSegmentShapePoint(double[] coords, boolean curved) {
		double[] data = new double[4];
		data[0] = coords[0];
		data[1] = coords[1];
		data[2] = coords[2];
		data[3] = 0.0;
		accumChain.path.add(new DsfTuple(data, 4));
	}

	@Override
	public void endSegment(double[] coords, boolean curved) {
		accumChain.path.add(new DsfTuple(coords, curved ? 7 : 4));
		accumChain.endNode = (int) coords[3];
	}

	@Override
	public void beginPolygon(int polygonType, int param, int coordDepth) {
		boolean hasBezier = (coordDepth == 4 && param < 65535) || coordDepth == 8;
		boolean hasSt = (coordDepth == 4 && param == 65535) || coordDepth == 8;

		PolygonSpec spec = new PolygonSpec();
		spec.isForest = coordDepth > 10;
		int depth = coordDepth;
		if (depth > 10) {
			depth -= 10;
		}
		spec.type = polygonType;
		spec.depth = depth;
		spec.hashDepth = depth + (hasBezier ? 100 : 0) + (hasSt ? 200 : 0);
		spec.param = param;
		spec.filter = currentFilter;
		polygons.add(spec);
		accumPoly = spec;
		accumPolyWinding.clear();
	}

	@Override
	public void beginPolygonWinding() {
		accumPolyWinding.add(new ArrayList<DsfTuple>());
	}

	@Override
	public void addPolygonPoint(double[] coords) {
		accumPolyWinding.get(accumPolyWinding.size() - 1).add(new DsfTuple(coords, accumPoly.depth));
	}

	@Override
	public void endPolygonWinding() {
		List<DsfTuple> winding = accumPolyWinding.get(accumPolyWinding.size() - 1);
		if (winding.isEmpty()) {
			accumPolyWinding.remove(accumPolyWinding.size() - 1);
		}
	}

	@Override
	public void endPolygon() {
		if (accumPolyWinding.isEmpty()) {
			polygons.remove(polygons.size() - 1);
			return;
		}
		int total = 0;
		for (List<DsfTuple> winding : accumPolyWinding) {
			total += winding.size();
		}
		if (total <= PolygonSubdivider.POINT_LIMIT) {
			finishPolygon(accumPoly, accumPolyWinding);
			return;
		}
		PolygonSpec base = accumPoly;
		polygons.remove(polygons.size() - 1);
		List<List<List<double[]>>> pieces = PolygonSubdivider.subdivide(base.depth,
				toDoubleWindings(accumPolyWinding));
		if (pieces == null) {
			skippedPolygons++;
			return;
		}
		for (List<List<double[]>> piece : pieces) {
			List<List<DsfTuple>> windings = toTupleWindings(piece, base.depth);
			int piecePoints = 0;
			for (List<DsfTuple> winding : windings) {
				piecePoints += winding.size();
			}
			if (piecePoints > PolygonSubdivider.POINT_LIMIT) {
				skippedPolygons++;
				continue;
			}
			polygons.add(copyOf(base));
			finishPolygon(polygons.get(polygons.size() - 1), windings);
		}
	}

	private static PolygonSpec copyOf(PolygonSpec base) {
		PolygonSpec spec = new PolygonSpec();
		spec.type = base.type;
		spec.param = base.param;
		spec.isForest = base.isForest;
		spec.depth = base.depth;
		spec.hashDepth = base.hashDepth;
		spec.filter = base.filter;
		return spec;
	}

	private static List<List<double[]>> toDoubleWindings(List<List<DsfTuple>> windings) {
		List<List<double[]>> result = new ArrayList<>(windings.size());
		for (List<DsfTuple> winding : windings) {
			List<double[]> ring = new ArrayList<>(winding.size());
			for (DsfTuple tuple : winding) {
				ring.add(new double[] { tuple.get(0), tuple.get(1) });
			}
			result.add(ring);
		}
		return result;
	}

	private static List<List<DsfTuple>> toTupleWindings(List<List<double[]>> windings, int depth) {
		List<List<DsfTuple>> result = new ArrayList<>(windings.size());
		for (List<double[]> ring : windings) {
			List<DsfTuple> tuples = new ArrayList<>(ring.size());
			for (double[] point : ring) {
				tuples.add(new DsfTuple(point, depth));
			}
			result.add(tuples);
		}
		return result;
	}

	private void finishPolygon(PolygonSpec accumPoly, List<List<DsfTuple>> accumPolyWinding) {
		if (accumPolyWinding.isEmpty()) {
			polygons.remove(polygons.size() - 1);
			return;
		}
		List<DsfTuple> points = new ArrayList<>();
		for (List<DsfTuple> winding : accumPolyWinding) {
			points.addAll(winding);
		}
		ContiguousPointPool pool = polygonPools.computeIfAbsent(accumPoly.hashDepth, k -> new ContiguousPointPool());
		int[] loc = pool.accumulatePoints(points);
		if (loc == null) {
			double[] llExtent = { 180.0, 90.0, -180.0, -90.0 };
			double[] stExtent = { 0.0, 0.0, 1.0, 1.0 };
			for (DsfTuple p : points) {
				extendBox(llExtent, p.get(0), p.get(1));
				switch (accumPoly.depth) {
				case 4:
					if (accumPoly.isForest) {
						break;
					} else if (isBezier(accumPoly.param)) {
						extendBox(llExtent, p.get(2), p.get(3));
					} else {
						extendBox(stExtent, p.get(2), p.get(3));
					}
					break;
				case 5:
					extendBox(llExtent, p.get(3), p.get(4));
					break;
				case 8:
					extendBox(llExtent, p.get(2), p.get(3));
					extendBox(stExtent, p.get(4), p.get(5));
					extendBox(stExtent, p.get(6), p.get(7));
					break;
				default:
					break;
				}
			}
			float n = divisions;
			for (; n > 1.0; n /= 2.0) {
				float dim = 1.0f / n;
				if (dim >= (llExtent[2] - llExtent[0]) && dim >= llExtent[3] - llExtent[1]) {
					break;
				}
			}
			llExtent[0] = Math.floor(llExtent[0] * n) / n;
			llExtent[2] = Math.ceil(llExtent[2] * n) / n;
			llExtent[1] = Math.floor(llExtent[1] * n) / n;
			llExtent[3] = Math.ceil(llExtent[3] * n) / n;
			stExtent[0] = Math.floor(stExtent[0]);
			stExtent[1] = Math.floor(stExtent[1]);
			stExtent[2] = Math.ceil(stExtent[2]);
			stExtent[3] = Math.ceil(stExtent[3]);
			if (llExtent[0] == llExtent[2]) {
				llExtent[2] += 1.0 / n;
			}
			if (llExtent[1] == llExtent[3]) {
				llExtent[3] += 1.0 / n;
			}
			DsfTuple rangeMin = new DsfTuple(accumPoly.depth);
			DsfTuple rangeMax = new DsfTuple(accumPoly.depth);
			rangeMin.set(0, llExtent[0]);
			rangeMax.set(0, llExtent[2]);
			rangeMin.set(1, llExtent[1]);
			rangeMax.set(1, llExtent[3]);
			switch (accumPoly.depth) {
			case 2:
				break;
			case 3:
				rangeMin.set(2, 0.0);
				rangeMax.set(2, 0.0);
				break;
			case 4:
				if (accumPoly.isForest) {
					rangeMin.set(2, 0.0);
					rangeMax.set(2, 65535.0 / 8.0);
					rangeMin.set(3, -1000.0);
					rangeMax.set(3, 65535.0 / 8.0 - 1000.0);
				} else if (isBezier(accumPoly.param)) {
					rangeMin.set(2, llExtent[0]);
					rangeMax.set(2, llExtent[2]);
					rangeMin.set(3, llExtent[1]);
					rangeMax.set(3, llExtent[3]);
				} else {
					rangeMin.set(2, stExtent[0]);
					rangeMax.set(2, stExtent[2]);
					rangeMin.set(3, stExtent[1]);
					rangeMax.set(3, stExtent[3]);
				}
				break;
			case 5:
				rangeMin.set(2, 0.0);
				rangeMax.set(2, 0.0);
				rangeMin.set(3, llExtent[0]);
				rangeMax.set(3, llExtent[2]);
				rangeMin.set(4, llExtent[1]);
				rangeMax.set(4, llExtent[3]);
				break;
			case 6:
				rangeMin.set(2, elevMin);
				rangeMax.set(2, elevMax);
				rangeMin.set(3, -1.0);
				rangeMax.set(3, 1.0);
				rangeMin.set(4, -1.0);
				rangeMax.set(4, 1.0);
				rangeMin.set(5, 0.0);
				rangeMax.set(5, 0.0);
				break;
			case 8:
				rangeMin.set(2, llExtent[0]);
				rangeMax.set(2, llExtent[2]);
				rangeMin.set(3, llExtent[1]);
				rangeMax.set(3, llExtent[3]);
				rangeMin.set(4, stExtent[0]);
				rangeMax.set(4, stExtent[2]);
				rangeMin.set(5, stExtent[1]);
				rangeMax.set(5, stExtent[3]);
				rangeMin.set(6, stExtent[0]);
				rangeMax.set(6, stExtent[2]);
				rangeMin.set(7, stExtent[1]);
				rangeMax.set(7, stExtent[3]);
				break;
			default:
				break;
			}
			pool.addPoolDirect(rangeMin, rangeMax);
			loc = pool.accumulatePoints(points);
			if (loc == null) {
				// A single DSF polygon command indexes points with 16 bits, so
				// polygons with more than 65535 points cannot be represented.
				// Skip it instead of aborting the whole DSF.
				polygons.remove(polygons.size() - 1);
				skippedPolygons++;
				return;
			}
		}
		accumPoly.pool = loc[0];
		accumPoly.intervals.add(loc[1]);
		for (List<DsfTuple> winding : accumPolyWinding) {
			accumPoly.intervals.add(accumPoly.intervals.get(accumPoly.intervals.size() - 1) + winding.size());
		}
	}

	private static boolean isBezier(int param) {
		return param != 65535;
	}

	private static void extendBox(double[] box, double x, double y) {
		box[0] = Math.min(box[0], x);
		box[1] = Math.min(box[1], y);
		box[2] = Math.max(box[2], x);
		box[3] = Math.max(box[3], y);
	}

	@Override
	public void addRasterData(DsfRasterHeader header, byte[] data) {
		RasterEntry entry = new RasterEntry();
		entry.header = header;
		entry.data = data;
		rasters.add(entry);
	}

	@Override
	public void setFilter(int filterIndex) {
		currentFilter = filterIndex;
	}

	private void optimizePrimitives(List<TriPrimitive> primitives) {
		List<TriPrimitive> output = new ArrayList<>();
		List<DsfTuple> vertices = new ArrayList<>();
		Map<DsfTuple, Integer> index = new java.util.HashMap<>();
		List<Integer> indices = new ArrayList<>();
		for (TriPrimitive primitive : primitives) {
			if (primitive.type == DsfConstants.TRI) {
				for (DsfTuple vertex : primitive.vertices) {
					Integer existing = index.get(vertex);
					if (existing == null) {
						indices.add(vertices.size());
						vertices.add(vertex);
						index.put(vertex, vertices.size() - 1);
					} else {
						indices.add(existing);
					}
				}
			} else {
				output.add(primitive);
			}
		}
		int offset = 0;
		while (offset < indices.size()) {
			int count = Math.min(indices.size() - offset, 255);
			TriPrimitive primitive = new TriPrimitive();
			primitive.type = DsfConstants.TRI;
			for (int i = 0; i < count; i++) {
				primitive.vertices.add(vertices.get(indices.get(offset + i)));
			}
			offset += count;
			output.add(primitive);
		}
		primitives.clear();
		primitives.addAll(output);
	}

	public void writeTo(OutputStream outputStream) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			preprocess();

			out.write(DsfConstants.COOKIE.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
			writeU32(out, DsfConstants.MASTER_VERSION);

			ByteArrayOutputStream head = new ByteArrayOutputStream();
			head.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_PROP, DsfWriteSupport.stringTable(properties)));
			out.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_HEAD, head.toByteArray()));

			ByteArrayOutputStream defn = new ByteArrayOutputStream();
			defn.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_TERT, DsfWriteSupport.stringTable(terrainDefs)));
			defn.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_OBJT, DsfWriteSupport.stringTable(objectDefs)));
			defn.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_POLY, DsfWriteSupport.stringTable(polygonDefs)));
			defn.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_NETW, DsfWriteSupport.stringTable(networkDefs)));
			defn.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_DEMN, DsfWriteSupport.stringTable(rasterDefs)));
			out.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_DEFN, defn.toByteArray()));

			ByteArrayOutputStream geod = new ByteArrayOutputStream();
			int lastPoolOffset = objectPool.writePoolAtoms(geod);
			objectPool.writeScaleAtoms(geod);
			int offsetTo3dObjs = lastPoolOffset;
			lastPoolOffset += objectPool3d.writePoolAtoms(geod);
			objectPool3d.writeScaleAtoms(geod);

			Map<Integer, Integer> offsetToTerrainPool = new TreeMap<>();
			for (Map.Entry<Integer, SharedPointPool> entry : terrainPool.entrySet()) {
				offsetToTerrainPool.put(entry.getKey(), lastPoolOffset);
				lastPoolOffset += entry.getValue().writePoolAtoms(geod);
				entry.getValue().writeScaleAtoms(geod);
			}
			Map<Integer, Integer> offsetToPolyPool = new TreeMap<>();
			for (Map.Entry<Integer, ContiguousPointPool> entry : polygonPools.entrySet()) {
				offsetToPolyPool.put(entry.getKey(), lastPoolOffset);
				lastPoolOffset += entry.getValue().writePoolAtoms(geod);
				entry.getValue().writeScaleAtoms(geod);
			}
			vectorPool.writePoolAtoms(geod);
			vectorPool.writeScaleAtoms(geod);
			vectorPoolCurved.writePoolAtoms(geod);
			vectorPoolCurved.writeScaleAtoms(geod);
			out.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_GEOD, geod.toByteArray()));

			writeCommands(out, offsetTo3dObjs, offsetToTerrainPool, offsetToPolyPool);

			if (!rasters.isEmpty()) {
				ByteArrayOutputStream dems = new ByteArrayOutputStream();
				for (RasterEntry entry : rasters) {
					ByteArrayOutputStream info = new ByteArrayOutputStream();
					writeU8(info, entry.header.version);
					writeU8(info, entry.header.bytesPerPixel);
					writeU16(info, entry.header.flags);
					writeU32(info, entry.header.width);
					writeU32(info, entry.header.height);
					DsfWriteSupport.writeFloat(info, entry.header.scale);
					DsfWriteSupport.writeFloat(info, entry.header.offset);
					dems.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_DEMI, info.toByteArray()));
					dems.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_DEMD, entry.data));
				}
				out.write(DsfWriteSupport.buildAtom(DsfConstants.ATOM_DEMS, dems.toByteArray()));
			}

			byte[] content = out.toByteArray();
			byte[] digest = MessageDigest.getInstance("MD5").digest(content);
			outputStream.write(content);
			outputStream.write(digest);
			outputStream.flush();
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new DsfConversionException("Error writing DSF", e);
		}
	}

	private void preprocess() {
		objectPool.processPoints();
		objectPool3d.processPoints();
		for (ContiguousPointPool pool : polygonPools.values()) {
			pool.processPoints();
		}

		Map<Integer, List<TriPrimitive>> allPrimitives = new TreeMap<>();
		for (PatchSpec patch : patches) {
			for (TriPrimitive primitive : patch.primitives) {
				primitive.isRange = false;
				allPrimitives.computeIfAbsent(patch.depth, k -> new ArrayList<>()).add(primitive);
			}
		}
		for (Map.Entry<Integer, List<TriPrimitive>> entry : allPrimitives.entrySet()) {
			for (TriPrimitive primitive : entry.getValue()) {
				if (primitive.indices.isEmpty()) {
					for (DsfTuple vertex : primitive.vertices) {
						int[] loc = terrainPool.get(entry.getKey()).acceptShared(vertex);
						if (loc == null) {
							throw new DsfConversionException("Could not sink patch vertex " + vertex);
						}
						primitive.indices.add(loc);
					}
				}
			}
		}
		for (SharedPointPool pool : terrainPool.values()) {
			pool.processPoints();
		}
		for (PatchSpec patch : patches) {
			for (TriPrimitive primitive : patch.primitives) {
				for (int[] loc : primitive.indices) {
					loc[0] = terrainPool.get(patch.depth).mapPoolNumber(loc[0]);
				}
			}
		}

		Collections.sort(objects, (a, b) -> a.compareTo(b));
		for (ObjectSpec object : objects) {
			object.pool = objectPool.mapPoolNumber(object.pool);
		}
		Collections.sort(objects3d[0], (a, b) -> a.compareTo(b));
		Collections.sort(objects3d[1], (a, b) -> a.compareTo(b));
		for (int i = 0; i < 2; i++) {
			for (ObjectSpec object : objects3d[i]) {
				object.pool = objectPool3d.mapPoolNumber(object.pool);
			}
		}

		Collections.sort(polygons, (a, b) -> a.compareTo(b));
		for (Map.Entry<Integer, ContiguousPointPool> entry : polygonPools.entrySet()) {
			entry.getValue().processPoints();
			for (PolygonSpec polygon : polygons) {
				if (entry.getKey().equals(polygon.hashDepth)) {
					polygon.pool = entry.getValue().mapPoolNumber(polygon.pool);
				}
			}
		}

		chainSpecs.sort((a, b) -> Integer.compare(b.path.size(), a.path.size()));
		chainSpecs.sort((a, b) -> Integer.compare(b.path.size(), a.path.size()));
		for (ChainSpec chain : chainSpecs) {
			if (chain.path.isEmpty()) {
				continue;
			}
			int[] loc = vectorPool.acceptContiguous(chain.path);
			if (loc == null) {
				chain.path.clear();
				skippedChains++;
				continue;
			}
			chain.contiguous = true;
			chain.lowestIndex = loc[1];
			chain.highestIndex = loc[1] + chain.path.size();
			for (int i = 0; i < chain.path.size(); i++) {
				chain.indices.add(new int[] { loc[0], loc[1] + i });
			}
		}
	}

	private void writeCommands(ByteArrayOutputStream out, int offsetTo3dObjs,
			Map<Integer, Integer> offsetToTerrainPool, Map<Integer, Integer> offsetToPolyPool) {
		ByteArrayOutputStream cmds = new ByteArrayOutputStream();
		int[] state = { -1, -1, -1 };

		writeU8(cmds, DsfConstants.CMD_JUNCTION_OFFSET_SELECT);
		writeU32(cmds, 0);

		for (int index = 0; index < objects.size(); index++) {
			ObjectSpec object = objects.get(index);
			int firstLoc = object.location;
			int lastLoc = object.location;
			int j = index;
			while (j < objects.size() && object.pool == objects.get(j).pool && object.type == objects.get(j).type) {
				lastLoc = objects.get(j).location;
				j++;
			}
			updatePoolState(cmds, object.type, object.pool, object.filter, state);
			writeObjectCommand(cmds, firstLoc, lastLoc);
		}

		for (int i = 0; i < 2; i++) {
			for (int index = 0; index < objects3d[i].size(); index++) {
				ObjectSpec object = objects3d[i].get(index);
				int firstLoc = object.location;
				int lastLoc = object.location;
				int j = index;
				while (j < objects3d[i].size() && object.pool == objects3d[i].get(j).pool
						&& object.type == objects3d[i].get(j).type) {
					lastLoc = objects3d[i].get(j).location;
					j++;
				}
				updatePoolState(cmds, object.type, object.pool + offsetTo3dObjs, object.filter, state);
				writeObjectCommand(cmds, firstLoc, lastLoc);
			}
			if (i == 0 && !objects3d[1].isEmpty()) {
				writeU8(cmds, DsfConstants.CMD_COMMENT8);
				writeU8(cmds, 6);
				writeU16(cmds, DsfConstants.COMMENT_AGL);
				writeU32(cmds, 1);
			}
		}

		for (PolygonSpec polygon : polygons) {
			int poolOffset = offsetToPolyPool.get(polygon.hashDepth);
			updatePoolState(cmds, polygon.type, polygon.pool + poolOffset, polygon.filter, state);
			if (polygon.intervals.size() <= 2) {
				writeU8(cmds, DsfConstants.CMD_POLYGON_RANGE);
				writeU16(cmds, polygon.param);
				writeU16(cmds, polygon.intervals.get(0));
				writeU16(cmds, polygon.intervals.get(1));
			} else {
				writeU8(cmds, DsfConstants.CMD_NESTED_POLYGON_RANGE);
				writeU16(cmds, polygon.param);
				writeU8(cmds, polygon.intervals.size() - 1);
				for (Integer interval : polygon.intervals) {
					writeU16(cmds, interval);
				}
			}
		}

		writePatches(cmds, offsetToTerrainPool, state);

		chainSpecs.sort((a, b) -> a.compareTo(b));
		int juncOff = 0;
		int curSubDef = -1;
		for (ChainSpec chain : chainSpecs) {
			if (chain.path.isEmpty()) {
				continue;
			}
			updatePoolState(cmds, chain.type, chain.curved ? 1 : 0, chain.filter, state);
			if (chain.subType != curSubDef) {
				curSubDef = chain.subType;
				writeU8(cmds, DsfConstants.CMD_SET_ROAD_SUBTYPE8);
				writeU8(cmds, curSubDef);
			}
			if ((chain.highestIndex - chain.lowestIndex) > 65535) {
				writeU8(cmds, DsfConstants.CMD_NETWORK_CHAIN32);
				writeU8(cmds, chain.indices.size());
				for (int[] loc : chain.indices) {
					writeU32(cmds, loc[1]);
				}
			} else {
				if (((juncOff + 65535) < chain.highestIndex) || (juncOff > chain.lowestIndex)) {
					juncOff = chain.lowestIndex;
					writeU8(cmds, DsfConstants.CMD_JUNCTION_OFFSET_SELECT);
					writeU32(cmds, juncOff);
				}
				if (chain.contiguous) {
					writeU8(cmds, DsfConstants.CMD_NETWORK_CHAIN_RANGE);
					writeU16(cmds, chain.lowestIndex - juncOff);
					writeU16(cmds, chain.highestIndex - juncOff);
				} else {
					writeU8(cmds, DsfConstants.CMD_NETWORK_CHAIN);
					writeU8(cmds, chain.indices.size());
					for (int[] loc : chain.indices) {
						writeU16(cmds, loc[1] - juncOff);
					}
				}
			}
		}

		out.writeBytes(DsfWriteSupport.buildAtom(DsfConstants.ATOM_CMDS, cmds.toByteArray()));
	}

	private void writeObjectCommand(ByteArrayOutputStream cmds, int firstLoc, int lastLoc) {
		if (firstLoc != lastLoc) {
			writeU8(cmds, DsfConstants.CMD_OBJECT);
			writeU16(cmds, firstLoc);
		} else {
			writeU8(cmds, DsfConstants.CMD_OBJECT_RANGE);
			writeU16(cmds, firstLoc);
			writeU16(cmds, lastLoc + 1);
		}
	}

	private void writePatches(ByteArrayOutputStream cmds, Map<Integer, Integer> offsetToTerrainPool, int[] state) {
		double lastLODNear = -1.0;
		double lastLODFar = -1.0;
		int lastFlags = 0xFF;
		for (PatchSpec patch : patches) {
			if (patch.primitives.isEmpty() || patch.primitives.get(0).indices.isEmpty()) {
				continue;
			}
			updatePoolState(cmds, patch.type,
					patch.primitives.get(0).indices.get(0)[0] + offsetToTerrainPool.get(patch.depth), state[2], state);
			if (lastLODNear != patch.nearLOD || lastLODFar != patch.farLOD) {
				writeU8(cmds, DsfConstants.CMD_TERRAIN_PATCH_FLAGS_LOD);
				writeU8(cmds, patch.flags);
				DsfWriteSupport.writeFloat(cmds, (float) patch.nearLOD);
				DsfWriteSupport.writeFloat(cmds, (float) patch.farLOD);
				lastLODNear = patch.nearLOD;
				lastLODFar = patch.farLOD;
				lastFlags = patch.flags;
			} else if (lastFlags != patch.flags) {
				writeU8(cmds, DsfConstants.CMD_TERRAIN_PATCH_FLAGS);
				writeU8(cmds, patch.flags);
				lastFlags = patch.flags;
			} else {
				writeU8(cmds, DsfConstants.CMD_TERRAIN_PATCH);
			}
			for (TriPrimitive primitive : patch.primitives) {
				if (primitive.indices.isEmpty()) {
					continue;
				}
				updatePoolState(cmds, patch.type,
						primitive.indices.get(0)[0] + offsetToTerrainPool.get(patch.depth), state[2], state);
				int command;
				if (primitive.isRange) {
					command = primitive.type == DsfConstants.TRI ? DsfConstants.CMD_TRIANGLE_RANGE
							: primitive.type == DsfConstants.TRI_STRIP ? DsfConstants.CMD_TRIANGLE_STRIP_RANGE
									: DsfConstants.CMD_TRIANGLE_FAN_RANGE;
					writeU8(cmds, command);
					writeU16(cmds, primitive.indices.get(0)[1]);
					writeU16(cmds, primitive.indices.get(0)[1] + primitive.indices.size());
				} else {
					command = primitive.type == DsfConstants.TRI ? DsfConstants.CMD_TRIANGLE
							: primitive.type == DsfConstants.TRI_STRIP ? DsfConstants.CMD_TRIANGLE_STRIP
									: DsfConstants.CMD_TRIANGLE_FAN;
					writeU8(cmds, command);
					writeU8(cmds, primitive.indices.size());
					for (int[] loc : primitive.indices) {
						writeU16(cmds, loc[1]);
					}
				}
			}
		}
	}

	private static void updatePoolState(ByteArrayOutputStream cmds, int newType, int newPool, int newFilter,
			int[] state) {
		int curType = state[0];
		int curPool = state[1];
		int curFilter = state[2];
		if (newFilter != curFilter) {
			writeU8(cmds, DsfConstants.CMD_COMMENT8);
			writeU8(cmds, 6);
			writeU16(cmds, DsfConstants.COMMENT_FILTER);
			writeU32(cmds, newFilter);
			state[2] = newFilter;
		}
		if (newType != curType) {
			state[0] = newType;
			if (newType > 65535) {
				writeU8(cmds, DsfConstants.CMD_SET_DEFINITION32);
				writeU32(cmds, newType);
			} else if (newType > 255) {
				writeU8(cmds, DsfConstants.CMD_SET_DEFINITION16);
				writeU16(cmds, newType);
			} else {
				writeU8(cmds, DsfConstants.CMD_SET_DEFINITION8);
				writeU8(cmds, newType);
			}
		}
		if (curPool != newPool) {
			state[1] = newPool;
			writeU8(cmds, DsfConstants.CMD_POOL_SELECT);
			writeU16(cmds, newPool);
		}
	}

	static void writeU8(ByteArrayOutputStream out, int value) {
		out.write(value & 0xFF);
	}

	static void writeU16(ByteArrayOutputStream out, int value) {
		out.write(value & 0xFF);
		out.write((value >>> 8) & 0xFF);
	}

	static void writeU32(ByteArrayOutputStream out, long value) {
		out.write((int) (value & 0xFF));
		out.write((int) ((value >>> 8) & 0xFF));
		out.write((int) ((value >>> 16) & 0xFF));
		out.write((int) ((value >>> 24) & 0xFF));
	}
}
