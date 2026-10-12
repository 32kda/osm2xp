package com.osm2xp.generation.dsf;

import java.util.ArrayList;
import java.util.List;

import com.osm2xp.generation.dsf.DsfReadSupport.Atom;
import com.osm2xp.generation.dsf.DsfReadSupport.Cursor;
import com.osm2xp.generation.dsf.DsfReadSupport.PlanarData;

/**
 * Reads a binary DSF file and feeds a {@link DsfCallbacks}. Ported from
 * DSFReadMem (DSFLib.cpp).
 */
public final class DsfReader {

	private static final double RECIP_65535 = 1.0 / 65535.0;
	private static final double RECIP_4294967295 = 1.0 / 4294967295.0;

	private final byte[] data;

	public DsfReader(byte[] data) {
		this.data = data;
	}

	public void read(DsfCallbacks callbacks) {
		if (data.length < 12 + 16) {
			throw new DsfConversionException("DSF file is too small");
		}
		String cookie = new String(data, 0, 8, java.nio.charset.StandardCharsets.US_ASCII);
		if (!DsfConstants.COOKIE.equals(cookie)) {
			throw new DsfConversionException("Bad DSF cookie: " + cookie);
		}
		int version = DsfReadSupport.readInt(data, 8);
		if (version != DsfConstants.MASTER_VERSION) {
			throw new DsfConversionException("Unsupported DSF version: " + version);
		}

		List<Atom> container = DsfReadSupport.parseContainer(data, 12, data.length - 16);
		Atom headAtom = require(container, DsfConstants.ATOM_HEAD, "metadata");
		Atom defnAtom = require(container, DsfConstants.ATOM_DEFN, "definitions");
		Atom geodAtom = require(container, DsfConstants.ATOM_GEOD, "geodata");
		Atom cmdsAtom = require(container, DsfConstants.ATOM_CMDS, "commands");

		Atom propAtom = require(headAtom.children(), DsfConstants.ATOM_PROP, "properties");

		List<Atom> defnChildren = defnAtom.children();
		Atom tertAtom = require(defnChildren, DsfConstants.ATOM_TERT, "terrain types");
		Atom objtAtom = require(defnChildren, DsfConstants.ATOM_OBJT, "object defs");
		Atom polyAtom = require(defnChildren, DsfConstants.ATOM_POLY, "polygon defs");
		Atom netwAtom = require(defnChildren, DsfConstants.ATOM_NETW, "networks");
		Atom demnAtom = DsfReadSupport.find(defnChildren, DsfConstants.ATOM_DEMN);

		List<Atom> geodChildren = geodAtom.children();
		List<double[]> scales = readScaleAtoms(geodChildren, DsfConstants.ATOM_SCAL);
		List<double[]> offsets = readOffsetAtoms(geodChildren, DsfConstants.ATOM_SCAL);
		List<double[]> scales32 = readScaleAtoms(geodChildren, DsfConstants.ATOM_SC32);
		List<double[]> offsets32 = readOffsetAtoms(geodChildren, DsfConstants.ATOM_SC32);

		List<PlanarData> planarData = new ArrayList<>();
		List<Integer> depths = new ArrayList<>();
		int poolIndex = 0;
		for (Atom atom : DsfReadSupport.all(geodChildren, DsfConstants.ATOM_POOL)) {
			double[] sc = poolIndex < scales.size() ? scales.get(poolIndex) : null;
			double[] of = poolIndex < offsets.size() ? offsets.get(poolIndex) : null;
			PlanarData pd = DsfReadSupport.decodePlanar(data, atom.contentStart(), atom.end(), false, sc, of,
					RECIP_65535);
			planarData.add(pd);
			depths.add(pd.planeCount);
			poolIndex++;
		}
		List<PlanarData> planarData32 = new ArrayList<>();
		List<Integer> depths32 = new ArrayList<>();
		poolIndex = 0;
		for (Atom atom : DsfReadSupport.all(geodChildren, DsfConstants.ATOM_PO32)) {
			double[] sc = poolIndex < scales32.size() ? scales32.get(poolIndex) : null;
			double[] of = poolIndex < offsets32.size() ? offsets32.get(poolIndex) : null;
			PlanarData pd = DsfReadSupport.decodePlanar(data, atom.contentStart(), atom.end(), true, sc, of,
					RECIP_4294967295);
			planarData32.add(pd);
			depths32.add(pd.planeCount);
			poolIndex++;
		}

		List<String> propStrings = DsfReadSupport.strings(data, propAtom.contentStart(), propAtom.end());
		for (int i = 0; i + 1 < propStrings.size(); i += 2) {
			callbacks.acceptProperty(propStrings.get(i), propStrings.get(i + 1));
		}

		emitDefs(callbacks, tertAtom, objtAtom, polyAtom, netwAtom, demnAtom);
		readRasters(callbacks);
		readCommands(callbacks, cmdsAtom, planarData, depths, planarData32, depths32);
	}

	private static List<double[]> readScaleAtoms(List<Atom> geodChildren, int id) {
		List<double[]> result = new ArrayList<>();
		for (Atom atom : DsfReadSupport.all(geodChildren, id)) {
			int count = atom.contentLength() / 8;
			double[] values = new double[count];
			Cursor cur = new Cursor(atom.data, atom.contentStart(), atom.end());
			for (int i = 0; i < count; i++) {
				values[i] = cur.readF32();
				cur.readF32();
			}
			result.add(values);
		}
		return result;
	}

	private static List<double[]> readOffsetAtoms(List<Atom> geodChildren, int id) {
		List<double[]> result = new ArrayList<>();
		for (Atom atom : DsfReadSupport.all(geodChildren, id)) {
			int count = atom.contentLength() / 8;
			double[] values = new double[count];
			Cursor cur = new Cursor(atom.data, atom.contentStart(), atom.end());
			for (int i = 0; i < count; i++) {
				cur.readF32();
				values[i] = cur.readF32();
			}
			result.add(values);
		}
		return result;
	}

	private void readCommands(DsfCallbacks callbacks, Atom cmdsAtom, List<PlanarData> planarData,
			List<Integer> depths, List<PlanarData> planarData32, List<Integer> depths32) {
		Cursor cur = new Cursor(data, cmdsAtom.contentStart(), cmdsAtom.end());
		long currentDefinition = 0xFFFFFFFFL;
		long roadSubtype = 0xFFFFFFFFL;
		int currentPool = 0xFFFF;
		long junctionOffset = 0xFFFFFFFFL;
		double patchLODNear = -1.0;
		double patchLODFar = -1.0;
		int patchFlags = 0xFF;
		boolean patchOpen = false;
		int[] curObjMode = { DsfConstants.MODE_MSL };
		double[] currentPoolPtr = null;
		double[] currentPoolPtr32 = null;
		int currentDepth = -1;
		int currentDepth32 = -1;

		while (!cur.done()) {
			int cmd = cur.readU8();
			switch (cmd) {
			case DsfConstants.CMD_RESERVED:
				throw new DsfConversionException("Reserved DSF command encountered");
			case DsfConstants.CMD_POOL_SELECT:
				currentPool = cur.readU16();
				if (currentPool >= planarData.size() && currentPool >= planarData32.size()) {
					throw new DsfConversionException("Pool out of range: " + currentPool);
				}
				if (currentPool < planarData.size()) {
					currentPoolPtr = planarData.get(currentPool).values;
					currentDepth = depths.get(currentPool);
				} else {
					currentPoolPtr = null;
				}
				if (currentPool < planarData32.size()) {
					currentPoolPtr32 = planarData32.get(currentPool).values;
					currentDepth32 = depths32.get(currentPool);
				} else {
					currentPoolPtr32 = null;
				}
				break;
			case DsfConstants.CMD_JUNCTION_OFFSET_SELECT:
				junctionOffset = cur.readU32();
				break;
			case DsfConstants.CMD_SET_DEFINITION8:
				currentDefinition = cur.readU8();
				break;
			case DsfConstants.CMD_SET_DEFINITION16:
				currentDefinition = cur.readU16();
				break;
			case DsfConstants.CMD_SET_DEFINITION32:
				currentDefinition = cur.readU32();
				break;
			case DsfConstants.CMD_SET_ROAD_SUBTYPE8:
				roadSubtype = cur.readU8();
				break;
			case DsfConstants.CMD_OBJECT:
			case DsfConstants.CMD_OBJECT_RANGE: {
				int index = cur.readU16();
				int index2 = cmd == DsfConstants.CMD_OBJECT ? index + 1 : cur.readU16();
				int mode = currentDepth == 4 ? curObjMode[0] : DsfConstants.MODE_DRAPED;
				for (int i = index; i < index2; i++) {
					callbacks.addObject((int) currentDefinition, tuple(currentPoolPtr, currentDepth, i), mode);
				}
				break;
			}
			case DsfConstants.CMD_NETWORK_CHAIN: {
				int count = cur.readU8();
				boolean hasCurve = currentDepth32 >= 7;
				for (int counter = 0; counter < count; counter++) {
					long index = junctionOffset + cur.readU16();
					double[] seg = tuple(currentPoolPtr32, currentDepth32, (int) index);
					if (isNode(seg)) {
						if (counter > 0) {
							callbacks.endSegment(seg, hasCurve);
						}
						if (counter < count - 1) {
							callbacks.beginSegment((int) currentDefinition, (int) roadSubtype, seg, hasCurve);
						}
					} else {
						callbacks.addSegmentShapePoint(seg, hasCurve);
					}
				}
				break;
			}
			case DsfConstants.CMD_NETWORK_CHAIN_RANGE: {
				long index1 = junctionOffset + cur.readU16();
				long index2 = junctionOffset + cur.readU16();
				boolean hasCurve = currentDepth32 >= 7;
				for (long index = index1; index < index2; index++) {
					double[] seg = tuple(currentPoolPtr32, currentDepth32, (int) index);
					if (isNode(seg)) {
						if (index != index1) {
							callbacks.endSegment(seg, hasCurve);
						}
						if (index != index2 - 1) {
							callbacks.beginSegment((int) currentDefinition, (int) roadSubtype, seg, hasCurve);
						}
					} else {
						callbacks.addSegmentShapePoint(seg, hasCurve);
					}
				}
				break;
			}
			case DsfConstants.CMD_NETWORK_CHAIN32: {
				int count = cur.readU8();
				boolean hasCurve = currentDepth32 >= 7;
				for (int counter = 0; counter < count; counter++) {
					long index = cur.readU32();
					double[] seg = tuple(currentPoolPtr32, currentDepth32, (int) index);
					if (isNode(seg)) {
						if (counter > 0) {
							callbacks.endSegment(seg, hasCurve);
						}
						if (counter < count - 1) {
							callbacks.beginSegment((int) currentDefinition, (int) roadSubtype, seg, hasCurve);
						}
					} else {
						callbacks.addSegmentShapePoint(seg, hasCurve);
					}
				}
				break;
			}
			case DsfConstants.CMD_POLYGON: {
				int polyParam = cur.readU16();
				int count = cur.readU8();
				callbacks.beginPolygon((int) currentDefinition, polyParam, currentDepth);
				callbacks.beginPolygonWinding();
				for (int i = 0; i < count; i++) {
					int index = cur.readU16();
					callbacks.addPolygonPoint(tuple(currentPoolPtr, currentDepth, index));
				}
				callbacks.endPolygonWinding();
				callbacks.endPolygon();
				break;
			}
			case DsfConstants.CMD_POLYGON_RANGE: {
				int polyParam = cur.readU16();
				long index1 = cur.readU16();
				long index2 = cur.readU16();
				callbacks.beginPolygon((int) currentDefinition, polyParam, currentDepth);
				callbacks.beginPolygonWinding();
				for (long index = index1; index < index2; index++) {
					callbacks.addPolygonPoint(tuple(currentPoolPtr, currentDepth, (int) index));
				}
				callbacks.endPolygonWinding();
				callbacks.endPolygon();
				break;
			}
			case DsfConstants.CMD_NESTED_POLYGON: {
				int polyParam = cur.readU16();
				int count = cur.readU8();
				callbacks.beginPolygon((int) currentDefinition, polyParam, currentDepth);
				for (int i = 0; i < count; i++) {
					callbacks.beginPolygonWinding();
					int counter = cur.readU8();
					for (int c = 0; c < counter; c++) {
						int index = cur.readU16();
						callbacks.addPolygonPoint(tuple(currentPoolPtr, currentDepth, index));
					}
					callbacks.endPolygonWinding();
				}
				callbacks.endPolygon();
				break;
			}
			case DsfConstants.CMD_NESTED_POLYGON_RANGE: {
				int polyParam = cur.readU16();
				int count = cur.readU8();
				long index1 = cur.readU16();
				callbacks.beginPolygon((int) currentDefinition, polyParam, currentDepth);
				for (int i = 0; i < count; i++) {
					callbacks.beginPolygonWinding();
					long index2 = cur.readU16();
					for (long index = index1; index < index2; index++) {
						callbacks.addPolygonPoint(tuple(currentPoolPtr, currentDepth, (int) index));
					}
					callbacks.endPolygonWinding();
					index1 = index2;
				}
				callbacks.endPolygon();
				break;
			}
			case DsfConstants.CMD_TERRAIN_PATCH:
				if (patchOpen) {
					callbacks.endPatch();
				}
				callbacks.beginPatch((int) currentDefinition, patchLODNear, patchLODFar, patchFlags, currentDepth);
				patchOpen = true;
				break;
			case DsfConstants.CMD_TERRAIN_PATCH_FLAGS:
				if (patchOpen) {
					callbacks.endPatch();
				}
				patchFlags = cur.readU8();
				callbacks.beginPatch((int) currentDefinition, patchLODNear, patchLODFar, patchFlags, currentDepth);
				patchOpen = true;
				break;
			case DsfConstants.CMD_TERRAIN_PATCH_FLAGS_LOD:
				if (patchOpen) {
					callbacks.endPatch();
				}
				patchFlags = cur.readU8();
				patchLODNear = cur.readF32();
				patchLODFar = cur.readF32();
				callbacks.beginPatch((int) currentDefinition, patchLODNear, patchLODFar, patchFlags, currentDepth);
				patchOpen = true;
				break;
			case DsfConstants.CMD_TRIANGLE:
			case DsfConstants.CMD_TRIANGLE_STRIP:
			case DsfConstants.CMD_TRIANGLE_FAN:
				callbacks.beginPrimitive(triType(cmd));
				readExplicitVertices(cur, callbacks, currentPoolPtr, currentDepth);
				callbacks.endPrimitive();
				break;
			case DsfConstants.CMD_TRIANGLE_CROSS_POOL:
			case DsfConstants.CMD_TRIANGLE_STRIP_CROSS_POOL:
			case DsfConstants.CMD_TRIANGLE_FAN_CROSS_POOL:
				callbacks.beginPrimitive(triType(cmd));
				readCrossPoolVertices(cur, callbacks, planarData, depths);
				callbacks.endPrimitive();
				break;
			case DsfConstants.CMD_TRIANGLE_RANGE:
			case DsfConstants.CMD_TRIANGLE_STRIP_RANGE:
			case DsfConstants.CMD_TRIANGLE_FAN_RANGE: {
				long index1 = cur.readU16();
				long index2 = cur.readU16();
				callbacks.beginPrimitive(triType(cmd));
				for (long index = index1; index < index2; index++) {
					callbacks.addPatchVertex(tuple(currentPoolPtr, currentDepth, (int) index));
				}
				callbacks.endPrimitive();
				break;
			}
			case DsfConstants.CMD_COMMENT8:
				readComment(cur, callbacks, 1, curObjMode);
				break;
			case DsfConstants.CMD_COMMENT16:
				readComment(cur, callbacks, 2, curObjMode);
				break;
			case DsfConstants.CMD_COMMENT32:
				readComment(cur, callbacks, 4, curObjMode);
				break;
			default:
				throw new DsfConversionException("Unknown DSF command: " + cmd);
			}
		}
		if (patchOpen) {
			callbacks.endPatch();
		}
		if (cur.overrun()) {
			throw new DsfConversionException("Overran the DSF command atom");
		}
	}

	private void readComment(Cursor cur, DsfCallbacks callbacks, int lengthBytes, int[] curObjMode) {
		long commentLen = lengthBytes == 1 ? cur.readU8() : lengthBytes == 2 ? cur.readU16() : cur.readU32();
		if (commentLen > 1) {
			int ctype = cur.readU16();
			commentLen -= 2;
			if (ctype == DsfConstants.COMMENT_FILTER && commentLen == 4) {
				int filter = cur.readS32();
				commentLen -= 4;
				callbacks.setFilter(filter);
			}
			if (ctype == DsfConstants.COMMENT_AGL && commentLen == 4) {
				int wantAgl = cur.readS32();
				commentLen -= 4;
				curObjMode[0] = wantAgl != 0 ? DsfConstants.MODE_AGL : DsfConstants.MODE_MSL;
			}
		}
		cur.advance((int) commentLen);
	}

	private void readExplicitVertices(Cursor cur, DsfCallbacks callbacks, double[] poolPtr, int depth) {
		int count = cur.readU8();
		for (int i = 0; i < count; i++) {
			int index = cur.readU16();
			callbacks.addPatchVertex(tuple(poolPtr, depth, index));
		}
	}

	private void readCrossPoolVertices(Cursor cur, DsfCallbacks callbacks, List<PlanarData> planarData,
			List<Integer> depths) {
		int count = cur.readU8();
		for (int i = 0; i < count; i++) {
			int pool = cur.readU16();
			int index = cur.readU16();
			if (pool >= planarData.size()) {
				throw new DsfConversionException("Cross pool out of range: " + pool);
			}
			callbacks.addPatchVertex(tuple(planarData.get(pool).values, depths.get(pool), index));
		}
	}

	private void readRasters(DsfCallbacks callbacks) {
		List<Atom> container = DsfReadSupport.parseContainer(data, 12, data.length - 16);
		Atom demsAtom = DsfReadSupport.find(container, DsfConstants.ATOM_DEMS);
		if (demsAtom == null) {
			return;
		}
		List<Atom> children = demsAtom.children();
		List<Atom> infos = DsfReadSupport.all(children, DsfConstants.ATOM_DEMI);
		List<Atom> datas = DsfReadSupport.all(children, DsfConstants.ATOM_DEMD);
		for (int i = 0; i < infos.size() && i < datas.size(); i++) {
			Cursor cur = new Cursor(data, infos.get(i).contentStart(), infos.get(i).end());
			int version = cur.readU8();
			int bpp = cur.readU8();
			int flags = cur.readU16();
			long width = cur.readU32();
			long height = cur.readU32();
			float scale = cur.readF32();
			float offset = cur.readF32();
			Atom dataAtom = datas.get(i);
			byte[] raster = new byte[dataAtom.contentLength()];
			System.arraycopy(data, dataAtom.contentStart(), raster, 0, raster.length);
			callbacks.addRasterData(new DsfRasterHeader(version, bpp, flags, width, height, scale, offset), raster);
		}
	}

	private static int triType(int cmd) {
		if (cmd == DsfConstants.CMD_TRIANGLE || cmd == DsfConstants.CMD_TRIANGLE_CROSS_POOL
				|| cmd == DsfConstants.CMD_TRIANGLE_RANGE) {
			return DsfConstants.TRI;
		}
		if (cmd == DsfConstants.CMD_TRIANGLE_STRIP || cmd == DsfConstants.CMD_TRIANGLE_STRIP_CROSS_POOL
				|| cmd == DsfConstants.CMD_TRIANGLE_STRIP_RANGE) {
			return DsfConstants.TRI_STRIP;
		}
		return DsfConstants.TRI_FAN;
	}

	private static boolean isNode(double[] seg) {
		return seg.length > 3 && seg[3] != 0.0;
	}

	private static double[] tuple(double[] values, int depth, int index) {
		if (values == null || depth < 0) {
			throw new DsfConversionException("No point pool selected");
		}
		double[] result = new double[depth];
		int base = index * depth;
		for (int p = 0; p < depth; p++) {
			result[p] = values[base + p];
		}
		return result;
	}

	private static void emitDefs(DsfCallbacks callbacks, Atom tertAtom, Atom objtAtom, Atom polyAtom, Atom netwAtom,
			Atom demnAtom) {
		for (String s : DsfReadSupport.strings(tertAtom.data, tertAtom.contentStart(), tertAtom.end())) {
			callbacks.acceptTerrainDef(s);
		}
		for (String s : DsfReadSupport.strings(objtAtom.data, objtAtom.contentStart(), objtAtom.end())) {
			callbacks.acceptObjectDef(s);
		}
		for (String s : DsfReadSupport.strings(polyAtom.data, polyAtom.contentStart(), polyAtom.end())) {
			callbacks.acceptPolygonDef(s);
		}
		for (String s : DsfReadSupport.strings(netwAtom.data, netwAtom.contentStart(), netwAtom.end())) {
			callbacks.acceptNetworkDef(s);
		}
		if (demnAtom != null) {
			for (String s : DsfReadSupport.strings(demnAtom.data, demnAtom.contentStart(), demnAtom.end())) {
				callbacks.acceptRasterDef(s);
			}
		}
	}

	private static Atom require(List<Atom> atoms, int id, String name) {
		Atom atom = DsfReadSupport.find(atoms, id);
		if (atom == null) {
			throw new DsfConversionException("DSF is missing the required " + name + " atom");
		}
		return atom;
	}
}
