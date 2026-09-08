package com.osm2xp.translators.airfield.btg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.osm2xp.core.parsers.btg.BtgFace;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.core.parsers.btg.BtgVector3;
import com.osm2xp.translators.flightgear.FlightGearCoordinateUtils;

/**
 * Assembles the airport surface polygons into a {@link BtgTile}.
 * <p>
 * Vertices are deduplicated (quantized to 1 cm) and converted from the local
 * east/north metre frame through lon/lat into ECEF, then stored as offsets
 * from the tile centre. Per-vertex texture coordinates are derived from the
 * local position divided by the material's tile size, so standard FlightGear
 * materials tile/clamp gracefully.
 */
public class AirfieldBtgAssembler {

	private static final double QUANTIZATION_METERS = 0.01;

	private final PolygonTriangulator triangulator = new PolygonTriangulator();

	public BtgTile assemble(List<SurfacePolygon> surfaces, double elevationM, BtgVector3 centerEcef, double anchorLon,
			double anchorLat) {
		List<Vertex> vertices = new ArrayList<>();
		List<float[]> texCoords = new ArrayList<>();
		List<BtgFace> faces = new ArrayList<>();
		Map<Long, Integer> vertexIndex = new HashMap<>();
		Map<Long, Integer> texCoordIndex = new HashMap<>();

		for (SurfacePolygon surface : surfaces) {
			double uvScale = FlightGearAirfieldMaterials.uvScale(surface.getMaterial());
			for (double[] triangle : triangulator.triangulate(surface.getPolygon())) {
				int v0 = addVertex(triangle[0], triangle[1], vertices, vertexIndex);
				int v1 = addVertex(triangle[2], triangle[3], vertices, vertexIndex);
				int v2 = addVertex(triangle[4], triangle[5], vertices, vertexIndex);
				int t0 = addTexCoord(triangle[0], triangle[1], uvScale, texCoords, texCoordIndex);
				int t1 = addTexCoord(triangle[2], triangle[3], uvScale, texCoords, texCoordIndex);
				int t2 = addTexCoord(triangle[4], triangle[5], uvScale, texCoords, texCoordIndex);
				faces.add(new BtgFace(v0, v1, v2, v0, v1, v2, t0, t1, t2, surface.getMaterial()));
			}
		}

		if (vertices.isEmpty()) {
			return null;
		}

		float[] vertexArray = new float[vertices.size() * 3];
		float[] normalArray = new float[vertices.size() * 3];
		float maxSquared = 0.0f;
		for (int i = 0; i < vertices.size(); i++) {
			Vertex vertex = vertices.get(i);
			double[] lonLat = FlightGearCoordinateUtils.localToGeodetic(vertex.east, vertex.north, anchorLon, anchorLat);
			double[] ecef = FlightGearCoordinateUtils.geodeticToEcef(lonLat[0], lonLat[1], elevationM);
			float x = (float) (ecef[0] - centerEcef.getX());
			float y = (float) (ecef[1] - centerEcef.getY());
			float z = (float) (ecef[2] - centerEcef.getZ());
			vertexArray[3 * i] = x;
			vertexArray[3 * i + 1] = y;
			vertexArray[3 * i + 2] = z;
			maxSquared = Math.max(maxSquared, x * x + y * y + z * z);

			double lonR = Math.toRadians(lonLat[0]);
			double latR = Math.toRadians(lonLat[1]);
			normalArray[3 * i] = (float) (Math.cos(latR) * Math.cos(lonR));
			normalArray[3 * i + 1] = (float) (Math.cos(latR) * Math.sin(lonR));
			normalArray[3 * i + 2] = (float) Math.sin(latR);
		}

		float[] texCoordArray = new float[texCoords.size() * 2];
		for (int i = 0; i < texCoords.size(); i++) {
			texCoordArray[2 * i] = texCoords.get(i)[0];
			texCoordArray[2 * i + 1] = texCoords.get(i)[1];
		}

		return new BtgTile(0, 0L, centerEcef, (float) Math.sqrt(maxSquared), vertexArray, normalArray,
				texCoordArray, faces, java.util.Collections.emptyList());
	}

	private int addVertex(double east, double north, List<Vertex> vertices, Map<Long, Integer> index) {
		long key = key(east, north);
		Integer existing = index.get(key);
		if (existing != null) {
			return existing;
		}
		int id = vertices.size();
		vertices.add(new Vertex(east, north));
		index.put(key, id);
		return id;
	}

	private int addTexCoord(double east, double north, double scale, List<float[]> texCoords, Map<Long, Integer> index) {
		long key = key(east, north) ^ Double.doubleToLongBits(scale);
		Integer existing = index.get(key);
		if (existing != null) {
			return existing;
		}
		int id = texCoords.size();
		texCoords.add(new float[] { (float) (east / scale), (float) (north / scale) });
		index.put(key, id);
		return id;
	}

	private long key(double east, double north) {
		long a = Math.round(east / QUANTIZATION_METERS);
		long b = Math.round(north / QUANTIZATION_METERS);
		return (a << 32) ^ (b & 0xFFFFFFFFL);
	}

	private static final class Vertex {
		final double east;
		final double north;

		Vertex(double east, double north) {
			this.east = east;
			this.north = north;
		}
	}
}
