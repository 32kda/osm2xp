package com.osm2xp.core.parsers.btg;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default {@link BtgWriter} implementation.
 * <p>
 * Mirrors the TerraGear writer layout: a bounding sphere object, vertex /
 * normal / texcoord list objects, an empty color list object, one triangle
 * list object per (material, use-normal, use-uv) combination and one point list
 * object per material.
 * <p>
 * Vertex coordinates written are the tile vertices, which are already relative
 * to {@link BtgTile#getCenter()}; the writer therefore stores the tile center as
 * the bounding sphere center and keeps the vertices as-is.
 */
public class BtgWriterImpl implements BtgWriter {

	private static final int MAX_INDEX_16 = 0xFFFF;
	private static final int MAX_GROUP_SIZE_16 = 0x7FFF;

	@Override
	public void write(BtgTile tile, OutputStream out) throws IOException {
		BtgOutput output = new BtgOutput();
		writeTo(tile, output);
		out.write(output.toByteArray());
	}

	private void writeTo(BtgTile tile, BtgOutput output) {
		int version = resolveVersion(tile);
		int normalCount = tile.getNormals().length / 3;
		int texCoordCount = tile.getTexCoordCount();

		boolean hasNormals = normalCount > 0;
		boolean hasTexCoords = texCoordCount > 0;

		Map<GroupKey, List<BtgFace>> triangleGroups = groupFaces(tile.getFaces(), hasNormals, hasTexCoords);
		List<BtgPointGroup> pointGroups = mergePointGroups(tile.getPointGroups());

		long creationTime = tile.getCreationTime() != 0 ? tile.getCreationTime()
				: System.currentTimeMillis() / 1000L;
		int objectCount = 5 + triangleGroups.size() + pointGroups.size();

		output.writeU16(version);
		output.writeU16(BtgConstants.MAGIC);
		output.writeU32(creationTime);
		if (version >= BtgConstants.VERSION_10) {
			output.writeU32(objectCount);
		} else {
			output.writeU16(objectCount);
		}

		// Object 0: bounding sphere.
		BtgOutput sphere = new BtgOutput();
		BtgVector3 center = tile.getCenter();
		sphere.writeF64(center.getX());
		sphere.writeF64(center.getY());
		sphere.writeF64(center.getZ());
		sphere.writeF32(tile.getRadius() > 0.0f ? tile.getRadius() : computeRadius(tile));
		writeObject(output, version, BtgObjectType.BOUNDING_SPHERE, emptyList(), singletonList(sphere.toByteArray()));

		// Object 1: vertex list.
		writeObject(output, version, BtgObjectType.VERTEX_LIST, emptyList(),
				singletonList(encodeFloats(tile.getVertices())));

		// Object 4: empty color list (present for format compatibility).
		writeObject(output, version, BtgObjectType.COLOR_LIST, emptyList(), singletonList(new byte[0]));

		// Object 2: normal list.
		writeObject(output, version, BtgObjectType.NORMAL_LIST, emptyList(),
				singletonList(encodeNormals(tile.getNormals())));

		// Object 3: texcoord list.
		writeObject(output, version, BtgObjectType.TEXCOORD_LIST, emptyList(),
				singletonList(encodeFloats(tile.getTexCoords())));

		// Triangle list objects.
		for (Map.Entry<GroupKey, List<BtgFace>> entry : triangleGroups.entrySet()) {
			GroupKey key = entry.getKey();
			List<byte[]> properties = new ArrayList<>();
			if (!key.material.isEmpty()) {
				properties.add(property(0, key.material.getBytes(StandardCharsets.UTF_8)));
			}
			int indexTypes = BtgConstants.INDEX_VERTEX | (key.useNormals ? BtgConstants.INDEX_NORMAL : 0)
					| (key.useUv ? BtgConstants.INDEX_TEXCOORD : 0);
			properties.add(property(1, new byte[] { (byte) indexTypes }));

			List<byte[]> elements = new ArrayList<>();
			for (BtgFace face : entry.getValue()) {
				elements.add(encodeTriangle(face, version, key.useNormals, key.useUv));
			}
			writeObject(output, version, BtgObjectType.TRIANGLE_LIST, properties, elements);
		}

		// Point list objects.
		for (BtgPointGroup group : pointGroups) {
			List<byte[]> properties = new ArrayList<>();
			if (!group.getMaterial().isEmpty()) {
				properties.add(property(0, group.getMaterial().getBytes(StandardCharsets.UTF_8)));
			}
			properties.add(property(1, new byte[] { (byte) BtgConstants.INDEX_VERTEX }));

			BtgOutput points = new BtgOutput();
			for (int index : group.getIndices()) {
				writeIndex(points, version, index);
			}
			writeObject(output, version, BtgObjectType.POINT_LIST, properties,
					singletonList(points.toByteArray()));
		}
	}

	private void writeObject(BtgOutput out, int version, BtgObjectType type, List<byte[]> properties,
			List<byte[]> elements) {
		out.writeU8(type.getCode());
		if (version >= BtgConstants.VERSION_10) {
			out.writeU32(properties.size());
			out.writeU32(elements.size());
		} else {
			out.writeU16(properties.size());
			out.writeU16(elements.size());
		}
		for (byte[] property : properties) {
			out.writeBytes(property);
		}
		for (byte[] element : elements) {
			out.writeU32(element.length);
			out.writeBytes(element);
		}
	}

	private static byte[] property(int type, byte[] data) {
		BtgOutput output = new BtgOutput();
		output.writeU8(type);
		output.writeU32(data.length);
		output.writeBytes(data);
		return output.toByteArray();
	}

	private static byte[] encodeFloats(float[] values) {
		BtgOutput output = new BtgOutput();
		for (float value : values) {
			output.writeF32(value);
		}
		return output.toByteArray();
	}

	private static byte[] encodeNormals(float[] normals) {
		BtgOutput output = new BtgOutput();
		for (int i = 0; i + 2 < normals.length; i += 3) {
			output.writeU8(encodeNormalComponent(normals[i]));
			output.writeU8(encodeNormalComponent(normals[i + 1]));
			output.writeU8(encodeNormalComponent(normals[i + 2]));
		}
		return output.toByteArray();
	}

	private static int encodeNormalComponent(float value) {
		float clamped = Math.max(-1.0f, Math.min(1.0f, value));
		int encoded = Math.round((clamped + 1.0f) * 127.5f);
		return Math.max(0, Math.min(255, encoded));
	}

	private byte[] encodeTriangle(BtgFace face, int version, boolean useNormals, boolean useUv) {
		BtgOutput output = new BtgOutput();
		int a = face.getA();
		int b = face.getB();
		int c = face.getC();
		if (useUv) {
			if (useNormals) {
				writeIndex(output, version, a);
				writeIndex(output, version, face.getNormalA());
				writeIndex(output, version, face.getTexA());
				writeIndex(output, version, b);
				writeIndex(output, version, face.getNormalB());
				writeIndex(output, version, face.getTexB());
				writeIndex(output, version, c);
				writeIndex(output, version, face.getNormalC());
				writeIndex(output, version, face.getTexC());
			} else {
				writeIndex(output, version, a);
				writeIndex(output, version, face.getTexA());
				writeIndex(output, version, b);
				writeIndex(output, version, face.getTexB());
				writeIndex(output, version, c);
				writeIndex(output, version, face.getTexC());
			}
		} else if (useNormals) {
			writeIndex(output, version, a);
			writeIndex(output, version, face.getNormalA());
			writeIndex(output, version, b);
			writeIndex(output, version, face.getNormalB());
			writeIndex(output, version, c);
			writeIndex(output, version, face.getNormalC());
		} else {
			writeIndex(output, version, a);
			writeIndex(output, version, b);
			writeIndex(output, version, c);
		}
		return output.toByteArray();
	}

	private void writeIndex(BtgOutput output, int version, int index) {
		if (version >= BtgConstants.VERSION_10) {
			output.writeU32(index & 0xFFFFFFFFL);
		} else {
			output.writeU16(index & 0xFFFF);
		}
	}

	private Map<GroupKey, List<BtgFace>> groupFaces(List<BtgFace> faces, boolean hasNormals, boolean hasTexCoords) {
		Map<GroupKey, List<BtgFace>> groups = new LinkedHashMap<>();
		for (BtgFace face : faces) {
			boolean useUv = hasTexCoords && face.hasTexCoords();
			boolean useNormals = hasNormals && face.hasNormals();
			GroupKey key = new GroupKey(face.getMaterial(), useUv, useNormals);
			groups.computeIfAbsent(key, k -> new ArrayList<>()).add(face);
		}
		return groups;
	}

	private List<BtgPointGroup> mergePointGroups(List<BtgPointGroup> pointGroups) {
		Map<String, List<Integer>> byMaterial = new LinkedHashMap<>();
		for (BtgPointGroup group : pointGroups) {
			List<Integer> indices = byMaterial.computeIfAbsent(group.getMaterial(), k -> new ArrayList<>());
			for (int index : group.getIndices()) {
				indices.add(index);
			}
		}
		List<BtgPointGroup> merged = new ArrayList<>();
		for (Map.Entry<String, List<Integer>> entry : byMaterial.entrySet()) {
			List<Integer> unique = new ArrayList<>();
			Set<Integer> seen = new HashSet<>();
			for (int index : entry.getValue()) {
				if (seen.add(index)) {
					unique.add(index);
				}
			}
			if (!unique.isEmpty()) {
				merged.add(new BtgPointGroup(entry.getKey(), toIntArray(unique)));
			}
		}
		return merged;
	}

	private int resolveVersion(BtgTile tile) {
		int version = tile.getVersion();
		if (version == BtgConstants.VERSION_7 || version == BtgConstants.VERSION_10) {
			return version;
		}
		return fitsVersion7(tile) ? BtgConstants.VERSION_7 : BtgConstants.VERSION_10;
	}

	private boolean fitsVersion7(BtgTile tile) {
		if (tile.getVertexCount() >= MAX_INDEX_16) {
			return false;
		}
		if (tile.getNormals().length / 3 >= MAX_INDEX_16) {
			return false;
		}
		if (tile.getTexCoordCount() >= MAX_INDEX_16) {
			return false;
		}
		return maxGroupSizeByMaterial(tile) < MAX_GROUP_SIZE_16;
	}

	private int maxGroupSizeByMaterial(BtgTile tile) {
		Map<String, Integer> counts = new HashMap<>();
		for (BtgFace face : tile.getFaces()) {
			counts.merge(face.getMaterial(), 1, Integer::sum);
		}
		int max = 0;
		for (int count : counts.values()) {
			max = Math.max(max, count);
		}
		return max;
	}

	private float computeRadius(BtgTile tile) {
		float[] vertices = tile.getVertices();
		double maxSquared = 0.0;
		for (int i = 0; i + 2 < vertices.length; i += 3) {
			double dx = vertices[i];
			double dy = vertices[i + 1];
			double dz = vertices[i + 2];
			double squared = dx * dx + dy * dy + dz * dz;
			if (squared > maxSquared) {
				maxSquared = squared;
			}
		}
		return (float) Math.sqrt(maxSquared);
	}

	private static int[] toIntArray(List<Integer> list) {
		int[] array = new int[list.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = list.get(i);
		}
		return array;
	}

	private static <T> List<T> emptyList() {
		return java.util.Collections.emptyList();
	}

	private static List<byte[]> singletonList(byte[] element) {
		List<byte[]> list = new ArrayList<>(1);
		list.add(element);
		return list;
	}

	private static final class GroupKey {
		final String material;
		final boolean useUv;
		final boolean useNormals;

		GroupKey(String material, boolean useUv, boolean useNormals) {
			this.material = material == null ? "" : material;
			this.useUv = useUv;
			this.useNormals = useNormals;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof GroupKey)) {
				return false;
			}
			GroupKey other = (GroupKey) obj;
			return useUv == other.useUv && useNormals == other.useNormals && material.equals(other.material);
		}

		@Override
		public int hashCode() {
			int result = material.hashCode();
			result = 31 * result + (useUv ? 1 : 0);
			result = 31 * result + (useNormals ? 1 : 0);
			return result;
		}
	}
}
