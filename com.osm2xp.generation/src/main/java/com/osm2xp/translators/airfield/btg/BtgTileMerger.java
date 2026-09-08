package com.osm2xp.translators.airfield.btg;

import java.util.ArrayList;
import java.util.List;

import com.osm2xp.core.parsers.btg.BtgFace;
import com.osm2xp.core.parsers.btg.BtgTile;

/**
 * Merges an overlay airport tile into a base terrain tile, producing a single
 * combined tile. The overlay vertices are converted from the overlay's ECEF
 * frame into the base tile's frame; vertices, normals, texture coordinates and
 * faces are concatenated with their per-face normal/texcoord indices preserved,
 * so the terrain keeps its original lighting and the round-trip is lossless.
 */
public final class BtgTileMerger {

	private BtgTileMerger() {
	}

	public static BtgTile merge(BtgTile base, BtgTile overlay) {
		int baseVertices = base.getVertexCount();
		int baseNormals = base.getNormals().length / 3;
		int baseTexCoords = base.getTexCoordCount();

		float[] vertices = new float[(baseVertices + overlay.getVertexCount()) * 3];
		System.arraycopy(base.getVertices(), 0, vertices, 0, base.getVertices().length);

		double bcx = base.getCenter().getX();
		double bcy = base.getCenter().getY();
		double bcz = base.getCenter().getZ();
		double ocx = overlay.getCenter().getX();
		double ocy = overlay.getCenter().getY();
		double ocz = overlay.getCenter().getZ();

		float[] overlayVertices = overlay.getVertices();
		for (int i = 0; i < overlay.getVertexCount(); i++) {
			vertices[(baseVertices + i) * 3] = (float) (overlayVertices[3 * i] + ocx - bcx);
			vertices[(baseVertices + i) * 3 + 1] = (float) (overlayVertices[3 * i + 1] + ocy - bcy);
			vertices[(baseVertices + i) * 3 + 2] = (float) (overlayVertices[3 * i + 2] + ocz - bcz);
		}

		float[] normals = concat(base.getNormals(), overlay.getNormals());
		float[] texCoords = concat(base.getTexCoords(), overlay.getTexCoords());

		List<BtgFace> faces = new ArrayList<>(base.getFaces());
		for (BtgFace face : overlay.getFaces()) {
			faces.add(new BtgFace(face.getA() + baseVertices, face.getB() + baseVertices,
					face.getC() + baseVertices, offset(face.getNormalA(), baseNormals),
					offset(face.getNormalB(), baseNormals), offset(face.getNormalC(), baseNormals),
					offset(face.getTexA(), baseTexCoords), offset(face.getTexB(), baseTexCoords),
					offset(face.getTexC(), baseTexCoords), face.getMaterial()));
		}

		float radius = computeRadius(vertices);

		return new BtgTile(base.getVersion(), base.getCreationTime(), base.getCenter(), radius, vertices,
				normals, texCoords, faces, base.getPointGroups());
	}

	private static float[] concat(float[] first, float[] second) {
		float[] result = new float[first.length + second.length];
		System.arraycopy(first, 0, result, 0, first.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	private static int offset(int index, int offset) {
		return index < 0 ? -1 : index + offset;
	}

	private static float computeRadius(float[] vertices) {
		double maxSquared = 0.0;
		for (int i = 0; i + 2 < vertices.length; i += 3) {
			double squared = vertices[i] * vertices[i] + vertices[i + 1] * vertices[i + 1]
					+ vertices[i + 2] * vertices[i + 2];
			if (squared > maxSquared) {
				maxSquared = squared;
			}
		}
		return (float) Math.sqrt(maxSquared);
	}
}
