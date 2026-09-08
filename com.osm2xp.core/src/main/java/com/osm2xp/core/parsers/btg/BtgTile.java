package com.osm2xp.core.parsers.btg;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of parsing a FlightGear BTG terrain tile.
 * <p>
 * The tile geometry is stored relative to {@link #getCenter()} (ECEF, metres):
 * vertices are offsets from the tile center. Vertex coordinates are therefore
 * {@code center + vertex} in the ECEF frame. {@link #getRadius()} bounds all
 * vertices around that center.
 * <p>
 * Vertices are stored as a flat {@code float[]} of {@code [x, y, z]} triplets
 * ({@code getVertices()[3 * i]} is the x coordinate of vertex {@code i}); the
 * optional normals use the same layout and are absent when the array is empty.
 * Texture coordinates are stored as a flat {@code float[]} of {@code [u, v]}
 * pairs.
 */
public final class BtgTile {

	private final int version;
	private final long creationTime;
	private final BtgVector3 center;
	private final float radius;
	private final float[] vertices;
	private final float[] normals;
	private final float[] texCoords;
	private final List<BtgFace> faces;
	private final List<BtgPointGroup> pointGroups;

	public BtgTile(int version, long creationTime, BtgVector3 center, float radius, float[] vertices,
			float[] normals, float[] texCoords, List<BtgFace> faces, List<BtgPointGroup> pointGroups) {
		this.version = version;
		this.creationTime = creationTime;
		this.center = center;
		this.radius = radius;
		this.vertices = vertices == null ? new float[0] : vertices;
		this.normals = normals == null ? new float[0] : normals;
		this.texCoords = texCoords == null ? new float[0] : texCoords;
		this.faces = Collections.unmodifiableList(faces == null ? Collections.emptyList() : faces);
		this.pointGroups = Collections.unmodifiableList(
				pointGroups == null ? Collections.emptyList() : pointGroups);
	}

	/** BTG format version (e.g. 7 or 10). */
	public int getVersion() {
		return version;
	}

	/** File creation time, seconds since the Unix epoch. */
	public long getCreationTime() {
		return creationTime;
	}

	/** Tile center in ECEF coordinates (metres). */
	public BtgVector3 getCenter() {
		return center;
	}

	/** Bounding sphere radius around the center (metres). */
	public float getRadius() {
		return radius;
	}

	/**
	 * Vertex data as a flat array of {@code [x, y, z]} triplets, relative to the
	 * tile center.
	 */
	public float[] getVertices() {
		return vertices;
	}

	/** Number of vertices. */
	public int getVertexCount() {
		return vertices.length / 3;
	}

	/**
	 * Per-vertex normals as a flat array of {@code [x, y, z]} triplets, or an
	 * empty array when the file carries no normals.
	 */
	public float[] getNormals() {
		return normals;
	}

	/** Texture coordinates as a flat array of {@code [u, v]} pairs. */
	public float[] getTexCoords() {
		return texCoords;
	}

	/** Number of texture coordinates. */
	public int getTexCoordCount() {
		return texCoords.length / 2;
	}

	/** Triangles of the tile. */
	public List<BtgFace> getFaces() {
		return faces;
	}

	/** Point groups ({@code SG_POINTS}) of the tile. */
	public List<BtgPointGroup> getPointGroups() {
		return pointGroups;
	}

	/** Number of triangles. */
	public int getFaceCount() {
		return faces.size();
	}

	@Override
	public String toString() {
		return "BtgTile[version=" + version + ", vertices=" + getVertexCount() + ", faces=" + getFaceCount()
				+ ", texCoords=" + getTexCoordCount() + ", pointGroups=" + pointGroups.size() + ", radius="
				+ radius + "]";
	}
}
