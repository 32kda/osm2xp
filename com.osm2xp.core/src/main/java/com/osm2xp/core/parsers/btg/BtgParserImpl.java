package com.osm2xp.core.parsers.btg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default {@link BtgParser} implementation.
 * <p>
 * The parser loads the whole (decompressed) tile into memory and follows the
 * BTG binary layout of FlightGear / TerraGear: a small header, then a sequence
 * of objects (bounding sphere, vertex/normal/texcoord lists, point lists and
 * triangle primitives), each made of a list of typed properties followed by a
 * list of byte-blob elements.
 */
public class BtgParserImpl implements BtgParser {

	@Override
	public BtgTile parse(InputStream input) throws IOException {
		byte[] data = input.readAllBytes();
		return parse(new BtgReader(data));
	}

	private BtgTile parse(BtgReader reader) throws BtgException {
		if (reader.remaining() < 10) {
			throw new BtgException("BTG file too short to contain a valid header");
		}

		int version = reader.u16();
		int magic = reader.u16();
		if (magic != BtgConstants.MAGIC) {
			throw new BtgException("Invalid BTG magic number 0x" + Integer.toHexString(magic));
		}
		long creationTime = reader.u32();
		long objectCount = version >= 10 ? reader.u32() : reader.u16();

		Accumulator acc = new Accumulator();
		for (long i = 0; i < objectCount; i++) {
			parseObject(reader, version, acc);
		}

		return acc.build(version, creationTime);
	}

	private void parseObject(BtgReader reader, int version, Accumulator acc) throws BtgException {
		int objectTypeCode = reader.u8();
		long propCount = version >= 10 ? reader.u32() : reader.u16();
		long elementCount = version >= 10 ? reader.u32() : reader.u16();
		BtgObjectType objectType = BtgObjectType.fromCode(objectTypeCode);

		String material = "";
		Integer indexTypes = null;
		for (long p = 0; p < propCount; p++) {
			int propType = reader.u8();
			long propSize = reader.u32();
			byte[] propRaw = reader.bytes((int) propSize);
			if (propType == 0) {
				material = new String(propRaw, StandardCharsets.UTF_8);
			} else if (propType == 1 && propRaw.length >= 1) {
				indexTypes = propRaw[0] & 0xFF;
			}
		}

		List<List<Entry>> entriesPerElement = new ArrayList<>();
		for (long e = 0; e < elementCount; e++) {
			long elementSize = reader.u32();
			byte[] elementRaw = reader.bytes((int) elementSize);
			if (objectType == null) {
				continue;
			}
			parseElement(objectType, elementRaw, indexTypes, version, acc, entriesPerElement);
		}

		if (objectType == BtgObjectType.TRIANGLE_LIST || objectType == BtgObjectType.TRIANGLE_STRIP
				|| objectType == BtgObjectType.TRIANGLE_FAN) {
			appendFaces(objectType, entriesPerElement, material, acc.faces);
		} else if (objectType == BtgObjectType.POINT_LIST) {
			appendPointGroup(entriesPerElement, material, acc.pointGroups);
		}
	}

	private void parseElement(BtgObjectType objectType, byte[] raw, Integer indexTypes, int version,
			Accumulator acc, List<List<Entry>> entriesPerElement) throws BtgException {
		switch (objectType) {
		case BOUNDING_SPHERE:
			if (raw.length >= 28) {
				BtgReader elementReader = new BtgReader(raw);
				acc.center = new BtgVector3(elementReader.f64(), elementReader.f64(), elementReader.f64());
				acc.radius = elementReader.f32();
			}
			break;
		case VERTEX_LIST:
			readTriples(raw, acc.vertices);
			break;
		case NORMAL_LIST:
			readNormals(raw, acc.normals);
			break;
		case TEXCOORD_LIST:
			readPairs(raw, acc.texCoords);
			break;
		case COLOR_LIST:
			// Color lists are part of the format but not needed for geometry reading.
			break;
		case POINT_LIST:
		case TRIANGLE_LIST:
		case TRIANGLE_STRIP:
		case TRIANGLE_FAN:
			entriesPerElement.add(parseGeometryEntries(raw, indexTypes, objectType, version));
			break;
		default:
			break;
		}
	}

	private void readTriples(byte[] raw, List<Float> out) throws BtgException {
		BtgReader reader = new BtgReader(raw);
		while (reader.hasRemaining(12)) {
			out.add(reader.f32());
			out.add(reader.f32());
			out.add(reader.f32());
		}
	}

	private void readPairs(byte[] raw, List<Float> out) throws BtgException {
		BtgReader reader = new BtgReader(raw);
		while (reader.hasRemaining(8)) {
			out.add(reader.f32());
			out.add(reader.f32());
		}
	}

	private void readNormals(byte[] raw, List<Float> out) {
		int count = raw.length / 3;
		for (int i = 0; i < count; i++) {
			int base = i * 3;
			out.add(decodeNormalComponent(raw[base] & 0xFF));
			out.add(decodeNormalComponent(raw[base + 1] & 0xFF));
			out.add(decodeNormalComponent(raw[base + 2] & 0xFF));
		}
	}

	private static float decodeNormalComponent(int value) {
		return (float) (value / 127.5 - 1.0);
	}

	private List<Entry> parseGeometryEntries(byte[] raw, Integer indexTypes, BtgObjectType objectType,
			int version) throws BtgException {
		int types = indexTypes != null ? indexTypes
				: (objectType == BtgObjectType.POINT_LIST ? BtgConstants.INDEX_VERTEX
						: BtgConstants.INDEX_VERTEX | BtgConstants.INDEX_TEXCOORD);
		int indexSize = version >= 10 ? 4 : 2;
		int strideCount = Integer.bitCount(types & 0x0F);
		if (strideCount == 0) {
			return Collections.emptyList();
		}
		int stride = strideCount * indexSize;
		int tupleCount = raw.length / stride;

		List<Entry> entries = new ArrayList<>(tupleCount);
		BtgReader reader = new BtgReader(raw);
		for (int i = 0; i < tupleCount; i++) {
			Entry entry = new Entry();
			for (int bit = 0; bit < 4; bit++) {
				if ((types & (1 << bit)) != 0) {
					long index = indexSize == 4 ? reader.u32() : reader.u16();
					if (bit == 0) {
						entry.v = (int) index;
						entry.hasV = true;
					} else if (bit == 1) {
						entry.n = (int) index;
						entry.hasN = true;
					} else if (bit == 3) {
						entry.t = (int) index;
						entry.hasT = true;
					}
				}
			}
			if (entry.hasV) {
				entries.add(entry);
			}
		}
		return entries;
	}

	private void appendFaces(BtgObjectType objectType, List<List<Entry>> entriesPerElement, String material,
			List<BtgFace> faces) {
		if (objectType == BtgObjectType.TRIANGLE_LIST) {
			List<Entry> flattened = new ArrayList<>();
			for (List<Entry> element : entriesPerElement) {
				flattened.addAll(element);
			}
			appendTriangles(flattened, objectType, material, faces);
		} else {
			for (List<Entry> element : entriesPerElement) {
				appendTriangles(element, objectType, material, faces);
			}
		}
	}

	private void appendTriangles(List<Entry> entries, BtgObjectType objectType, String material, List<BtgFace> faces) {
		switch (objectType) {
		case TRIANGLE_LIST:
			for (int i = 0; i + 2 < entries.size(); i += 3) {
				addFace(entries.get(i), entries.get(i + 1), entries.get(i + 2), material, faces);
			}
			break;
		case TRIANGLE_STRIP:
			for (int i = 0; i + 2 < entries.size(); i++) {
				if (i % 2 == 0) {
					addFace(entries.get(i), entries.get(i + 1), entries.get(i + 2), material, faces);
				} else {
					addFace(entries.get(i + 1), entries.get(i), entries.get(i + 2), material, faces);
				}
			}
			break;
		case TRIANGLE_FAN:
			if (entries.size() >= 3) {
				Entry anchor = entries.get(0);
				for (int i = 1; i + 1 < entries.size(); i++) {
					addFace(anchor, entries.get(i), entries.get(i + 1), material, faces);
				}
			}
			break;
		default:
			break;
		}
	}

	private void addFace(Entry e1, Entry e2, Entry e3, String material, List<BtgFace> faces) {
		if (e1.v == e2.v || e1.v == e3.v || e2.v == e3.v) {
			return;
		}
		faces.add(new BtgFace(e1.v, e2.v, e3.v, e1.hasN ? e1.n : -1, e2.hasN ? e2.n : -1, e3.hasN ? e3.n : -1,
				e1.hasT ? e1.t : -1, e2.hasT ? e2.t : -1, e3.hasT ? e3.t : -1, material));
	}

	private void appendPointGroup(List<List<Entry>> entriesPerElement, String material, List<BtgPointGroup> groups) {
		List<Integer> indices = new ArrayList<>();
		for (List<Entry> element : entriesPerElement) {
			for (Entry entry : element) {
				if (entry.hasV) {
					indices.add(entry.v);
				}
			}
		}
		if (!indices.isEmpty()) {
			groups.add(new BtgPointGroup(material, toIntArray(indices)));
		}
	}

	private static boolean inRange(int index, int count) {
		return index >= 0 && index < count;
	}

	private static int[] toIntArray(List<Integer> list) {
		int[] array = new int[list.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = list.get(i);
		}
		return array;
	}

	private static float[] toFloatArray(List<Float> list) {
		float[] array = new float[list.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = list.get(i);
		}
		return array;
	}

	private static final class Entry {
		int v = -1;
		int n = -1;
		int t = -1;
		boolean hasV;
		boolean hasN;
		boolean hasT;
	}

	private static final class Accumulator {
		BtgVector3 center;
		float radius;
		final List<Float> vertices = new ArrayList<>();
		final List<Float> normals = new ArrayList<>();
		final List<Float> texCoords = new ArrayList<>();
		final List<BtgFace> faces = new ArrayList<>();
		final List<BtgPointGroup> pointGroups = new ArrayList<>();

		BtgTile build(int version, long creationTime) {
			int vertexCount = vertices.size() / 3;
			int normalCount = normals.size() / 3;
			int texCoordCount = texCoords.size() / 2;

			List<BtgFace> validFaces = new ArrayList<>(faces.size());
			for (BtgFace face : faces) {
				if (!inRange(face.getA(), vertexCount) || !inRange(face.getB(), vertexCount)
						|| !inRange(face.getC(), vertexCount)) {
					continue;
				}
				int na = face.getNormalA();
				int nb = face.getNormalB();
				int nc = face.getNormalC();
				if (!inRange(na, normalCount) || !inRange(nb, normalCount) || !inRange(nc, normalCount)) {
					na = -1;
					nb = -1;
					nc = -1;
				}
				int ta = face.getTexA();
				int tb = face.getTexB();
				int tc = face.getTexC();
				if (!inRange(ta, texCoordCount) || !inRange(tb, texCoordCount) || !inRange(tc, texCoordCount)) {
					ta = -1;
					tb = -1;
					tc = -1;
				}
				validFaces.add(new BtgFace(face.getA(), face.getB(), face.getC(), na, nb, nc, ta, tb, tc,
						face.getMaterial()));
			}

			List<BtgPointGroup> validGroups = new ArrayList<>();
			for (BtgPointGroup group : pointGroups) {
				List<Integer> validIndices = new ArrayList<>();
				for (int index : group.getIndices()) {
					if (inRange(index, vertexCount)) {
						validIndices.add(index);
					}
				}
				if (!validIndices.isEmpty()) {
					validGroups.add(new BtgPointGroup(group.getMaterial(), toIntArray(validIndices)));
				}
			}

			BtgVector3 center = this.center != null ? this.center : new BtgVector3(0.0, 0.0, 0.0);
			return new BtgTile(version, creationTime, center, radius, toFloatArray(vertices), toFloatArray(normals),
					toFloatArray(texCoords), validFaces, validGroups);
		}
	}
}
