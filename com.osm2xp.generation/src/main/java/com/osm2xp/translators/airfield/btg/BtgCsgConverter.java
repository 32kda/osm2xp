package com.osm2xp.translators.airfield.btg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.parsers.btg.BtgFace;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.core.parsers.btg.BtgVector3;
import com.osm2xp.translators.flightgear.FlightGearCoordinateUtils;
import com.osm2xp.utils.geometry.GeomUtils;

/**
 * Converts a {@link BtgTile} terrain heightfield into a local flat-earth frame
 * ({@code x=east}, {@code y=north} metres, {@code z=altitude} metres) and back,
 * and cuts a hole in it.
 * <p>
 * The cut is a vertical prism, so it is performed as a robust 2D polygon
 * difference in the east/north plane (JTS): each terrain triangle near the
 * airfield is clipped against the (convex) cut footprint and the surviving
 * pieces are lifted back to their interpolated terrain heights. This avoids the
 * deep, unstable BSP recursion that a 3D CSG boolean suffers on an open
 * heightfield.
 */
public class BtgCsgConverter {

	private static final double QUANTIZATION_METERS = 0.01;

	private final GeometryFactory geometryFactory = new GeometryFactory();
	private final PolygonTriangulator triangulator = new PolygonTriangulator();

	private final double datumLon;
	private final double datumLat;

	public BtgCsgConverter(double datumLon, double datumLat) {
		this.datumLon = datumLon;
		this.datumLat = datumLat;
	}

	/**
	 * One terrain triangle expressed in the local frame, remembering its original
	 * vertex indices (for boundary-edge detection) and material.
	 */
	public static final class TerrainFace {
		public final int a;
		public final int b;
		public final int c;
		public final double e0;
		public final double n0;
		public final double a0;
		public final double e1;
		public final double n1;
		public final double a1;
		public final double e2;
		public final double n2;
		public final double a2;
		public final String material;

		TerrainFace(int a, int b, int c, double[] p0, double[] p1, double[] p2, String material) {
			this.a = a;
			this.b = b;
			this.c = c;
			this.e0 = p0[0];
			this.n0 = p0[1];
			this.a0 = p0[2];
			this.e1 = p1[0];
			this.n1 = p1[1];
			this.a1 = p1[2];
			this.e2 = p2[0];
			this.n2 = p2[1];
			this.a2 = p2[2];
			this.material = material;
		}
	}

	/**
	 * The result of splitting a tile into faces that take part in the cut and the
	 * rest.
	 */
	public static final class TerrainPartition {
		final List<TerrainFace> inside;
		final List<BtgFace> outside;

		TerrainPartition(List<TerrainFace> inside, List<BtgFace> outside) {
			this.inside = inside;
			this.outside = outside;
		}

		public List<TerrainFace> getInside() {
			return inside;
		}

		public List<BtgFace> getOutside() {
			return outside;
		}
	}

	/** A terrain triangle in the local frame, ready to be assembled into a tile. */
	public static final class Triangle {
		public final double e0;
		public final double n0;
		public final double a0;
		public final double e1;
		public final double n1;
		public final double a1;
		public final double e2;
		public final double n2;
		public final double a2;
		public final String material;

		public Triangle(double e0, double n0, double a0, double e1, double n1, double a1, double e2, double n2,
				double a2, String material) {
			this.e0 = e0;
			this.n0 = n0;
			this.a0 = a0;
			this.e1 = e1;
			this.n1 = n1;
			this.a1 = a1;
			this.e2 = e2;
			this.n2 = n2;
			this.a2 = a2;
			this.material = material;
		}
	}

	/**
	 * Result of clipping one terrain tile: the surviving triangles plus the
	 * vertices that lie on the cut boundary (the "seam"), which must be reused by
	 * the skirt so the two meshes are stitched (share vertices) rather than merely
	 * coincident.
	 */
	public static final class ClipResult {
		public final List<Triangle> triangles;
		public final List<double[]> crossings;

		ClipResult(List<Triangle> triangles, List<double[]> crossings) {
			this.triangles = triangles;
			this.crossings = crossings;
		}
	}

	/**
	 * Splits the tile's triangles by their 2D envelope. Faces intersecting the
	 * given region (or all faces when {@code region} is {@code null}) take part in
	 * the cut; the remaining faces are returned untouched.
	 */
	public TerrainPartition partition(BtgTile tile, Envelope region) {
		float[] vertices = tile.getVertices();
		BtgVector3 center = tile.getCenter();
		double cx = center.getX();
		double cy = center.getY();
		double cz = center.getZ();

		List<TerrainFace> inside = new ArrayList<>();
		List<BtgFace> outside = new ArrayList<>();
		for (BtgFace face : tile.getFaces()) {
			double[] p0 = toLocal(vertices, cx, cy, cz, face.getA());
			double[] p1 = toLocal(vertices, cx, cy, cz, face.getB());
			double[] p2 = toLocal(vertices, cx, cy, cz, face.getC());
			if (region != null && !intersectsRegion(region, p0, p1, p2)) {
				outside.add(face);
				continue;
			}
			inside.add(new TerrainFace(face.getA(), face.getB(), face.getC(), p0, p1, p2, face.getMaterial()));
		}
		return new TerrainPartition(inside, outside);
	}

	/**
	 * Clips the given terrain faces against the (convex) cut footprint, returning
	 * the parts that lie outside it, lifted to their interpolated terrain heights,
	 * together with the seam vertices (points lying on the cut boundary).
	 */
	public ClipResult clipTerrain(List<TerrainFace> faces, Polygon cutPolygon) {
		List<Triangle> result = new ArrayList<>();
		List<double[]> crossings = new ArrayList<>();
		Geometry boundary = cutPolygon.getBoundary();
		int sourceCcw = 0;
		int sourceCw = 0;
		int outputCcw = 0;
		int outputCw = 0;
		for (TerrainFace face : faces) {
			double sourceArea = signedArea2(face.e0, face.n0, face.e1, face.n1, face.e2, face.n2);
			if (sourceArea > 0) {
				sourceCcw++;
			} else if (sourceArea < 0) {
				sourceCw++;
			}
			Polygon triangle = geometryFactory.createPolygon(
					new Coordinate[] { new Coordinate(face.e0, face.n0), new Coordinate(face.e1, face.n1),
							new Coordinate(face.e2, face.n2), new Coordinate(face.e0, face.n0) });
			if (triangle.isEmpty()) {
				continue;
			}
			Geometry outside;
			try {
				outside = triangle.difference(cutPolygon);
			} catch (RuntimeException e) {
				// numerical issue - keep the whole triangle rather than drop terrain
				outside = triangle;
			}
			for (Polygon piece : GeomUtils.flatMapToPoly(outside)) {
				for (double[] t : triangulator.triangulateSimple(piece, "clip")) {
					if (t.length != 6) {
						Osm2xpLogger.error("Terrain clip produced a " + (t.length / 2)
								+ "-vertex polygon instead of a triangle; skipping");
						continue;
					}
					double outArea = signedArea2(t[0], t[1], t[2], t[3], t[4], t[5]);
					if (outArea > 0) {
						outputCcw++;
					} else if (outArea < 0) {
						outputCw++;
					}
					result.add(new Triangle(t[0], t[1], interpolateAltitude(face, t[0], t[1]), t[2], t[3],
							interpolateAltitude(face, t[2], t[3]), t[4], t[5], interpolateAltitude(face, t[4], t[5]),
							face.material));
					for (int i = 0; i < 3; i++) {
						double east = t[2 * i];
						double north = t[2 * i + 1];
						if (boundary.distance(geometryFactory.createPoint(new Coordinate(east, north))) < 0.01) {
							crossings.add(new double[] { east, north });
						}
					}
				}
			}
		}
		if (sourceCw > 0) {
			Osm2xpLogger.warning("Terrain clip winding: " + sourceCw + "/" + (sourceCcw + sourceCw)
					+ " source faces are CW while clipped pieces are forced CCW -> " + sourceCw + " flipped faces");
		}
		Osm2xpLogger.info("Terrain clip winding: source +" + sourceCcw + "/-" + sourceCw + " -> output +" + outputCcw
				+ "/-" + outputCw);
		List<double[]> uniqueCrossings = dedupe(crossings, 0.01);
		if (uniqueCrossings.size() != crossings.size()) {
			Osm2xpLogger.info("Terrain clip seam: " + crossings.size() + " boundary hits, " + uniqueCrossings.size()
					+ " unique (" + (crossings.size() - uniqueCrossings.size()) + " duplicates removed)");
		}
		return new ClipResult(result, uniqueCrossings);
	}

	/**
	 * Twice the signed area of a triangle in the east/north plane; positive = CCW.
	 */
	private static double signedArea2(double e0, double n0, double e1, double n1, double e2, double n2) {
		return (e1 - e0) * (n2 - n0) - (n1 - n0) * (e2 - e0);
	}

	/** Removes points whose quantized position matches an already-kept point. */
	private static List<double[]> dedupe(List<double[]> points, double tolerance) {
		Set<String> seen = new HashSet<>();
		List<double[]> unique = new ArrayList<>();
		for (double[] p : points) {
			String key = Math.round(p[0] / tolerance) + "," + Math.round(p[1] / tolerance);
			if (seen.add(key)) {
				unique.add(p);
			}
		}
		return unique;
	}

	/**
	 * Diagnostic: counts non-manifold and open edges across the rebuilt tile's faces
	 * (untouched + new), so welding can be verified (shared edges should not appear
	 * as open).
	 */
	private static void reportEdgeTopology(List<BtgFace> faces) {
		Map<Long, Integer> edges = new HashMap<>();
		for (BtgFace face : faces) {
			addEdge(edges, face.getA(), face.getB());
			addEdge(edges, face.getB(), face.getC());
			addEdge(edges, face.getC(), face.getA());
		}
		int nonManifold = 0;
		int open = 0;
		for (int use : edges.values()) {
			if (use > 2) {
				nonManifold++;
			} else if (use == 1) {
				open++;
			}
		}
		if (nonManifold > 0) {
			Osm2xpLogger.warning("Rebuilt tile: " + nonManifold + " non-manifold edges among " + faces.size()
					+ " faces (" + open + " open/boundary edges)");
		} else {
			Osm2xpLogger.info("Rebuilt tile: " + faces.size() + " faces, 0 non-manifold, " + open
					+ " open/boundary edges");
		}
	}

	private static void addEdge(Map<Long, Integer> edges, int a, int b) {
		int lo = Math.min(a, b);
		int hi = Math.max(a, b);
		long key = (((long) lo) << 32) | (hi & 0xFFFFFFFFL);
		edges.merge(key, 1, Integer::sum);
	}

	/**
	 * Rebuilds a {@link BtgTile} from the untouched faces plus the cut terrain
	 * triangles and the airfield overlay (plate + skirt) triangles.
	 * <p>
	 * All new triangles share a vertex pool that is <em>seeded with the original
	 * tile vertices</em>, so a re-triangulated face reuses the untouched
	 * neighbours' vertex indices wherever the positions coincide instead of
	 * creating coincident duplicates. This mirrors TerraGear's global unique node
	 * set and keeps the region boundary (and the cut seam) conforming rather than
	 * leaving unwelded/T-junctioned edges.
	 */
	public BtgTile rebuild(BtgTile original, List<BtgFace> outsideFaces, List<Triangle> cutTriangles,
			List<Triangle> overlayTriangles) {
		int baseVertexCount = original.getVertexCount();
		int baseNormalCount = original.getNormals().length / 3;
		int baseTexCount = original.getTexCoordCount();

		BtgVector3 center = original.getCenter();

		List<double[]> newVertices = new ArrayList<>();
		List<float[]> texCoords = new ArrayList<>();
		Map<String, Integer> vertexIndex = new HashMap<>();
		Map<String, Integer> texIndex = new HashMap<>();
		List<int[]> faceIndices = new ArrayList<>();
		List<String> triangleMaterials = new ArrayList<>();

		// Shared node pool: seed with the original tile vertices.
		seedOriginalVertices(original, center, vertexIndex);

		addTriangles(cutTriangles, newVertices, texCoords, vertexIndex, texIndex, faceIndices, triangleMaterials,
				center, baseVertexCount);
		addTriangles(overlayTriangles, newVertices, texCoords, vertexIndex, texIndex, faceIndices, triangleMaterials,
				center, baseVertexCount);

		float[] newVertexArray = new float[newVertices.size() * 3];
		for (int i = 0; i < newVertices.size(); i++) {
			double[] v = newVertices.get(i);
			newVertexArray[3 * i] = (float) v[0];
			newVertexArray[3 * i + 1] = (float) v[1];
			newVertexArray[3 * i + 2] = (float) v[2];
		}
		float[] allVertices = concat(original.getVertices(), newVertexArray);

		float[] texArray = new float[texCoords.size() * 2];
		for (int i = 0; i < texCoords.size(); i++) {
			float[] t = texCoords.get(i);
			texArray[2 * i] = t[0];
			texArray[2 * i + 1] = t[1];
		}

		// Per-vertex normals for the new faces, keyed by absolute vertex index.
		double[] normalAccum = new double[allVertices.length];
		Set<Integer> usedVertices = new LinkedHashSet<>();
		for (int[] tri : faceIndices) {
			accumulateNormal(normalAccum, allVertices, tri[0], tri[1], tri[2]);
			usedVertices.add(tri[0]);
			usedVertices.add(tri[1]);
			usedVertices.add(tri[2]);
		}
		Map<Integer, Integer> normalIndex = new HashMap<>();
		List<float[]> newNormals = new ArrayList<>();
		for (int vertex : usedVertices) {
			double nx = normalAccum[3 * vertex];
			double ny = normalAccum[3 * vertex + 1];
			double nz = normalAccum[3 * vertex + 2];
			double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (length < 1e-9) {
				double[] geodetic = FlightGearCoordinateUtils.ecefToGeodetic(allVertices[3 * vertex] + center.getX(),
						allVertices[3 * vertex + 1] + center.getY(), allVertices[3 * vertex + 2] + center.getZ());
				double lonR = Math.toRadians(geodetic[0]);
				double latR = Math.toRadians(geodetic[1]);
				nx = Math.cos(latR) * Math.cos(lonR);
				ny = Math.cos(latR) * Math.sin(lonR);
				nz = Math.sin(latR);
			} else {
				nx /= length;
				ny /= length;
				nz /= length;
			}
			normalIndex.put(vertex, newNormals.size());
			newNormals.add(new float[] { (float) nx, (float) ny, (float) nz });
		}
		float[] normalArray = new float[newNormals.size() * 3];
		for (int i = 0; i < newNormals.size(); i++) {
			float[] n = newNormals.get(i);
			normalArray[3 * i] = n[0];
			normalArray[3 * i + 1] = n[1];
			normalArray[3 * i + 2] = n[2];
		}

		List<BtgFace> newFaces = new ArrayList<>(outsideFaces);
		int weldedCorners = 0;
		for (int t = 0; t < faceIndices.size(); t++) {
			int[] tri = faceIndices.get(t);
			for (int i = 0; i < 3; i++) {
				if (tri[i] < baseVertexCount) {
					weldedCorners++;
				}
			}
			newFaces.add(new BtgFace(tri[0], tri[1], tri[2], baseNormalCount + normalIndex.get(tri[0]),
					baseNormalCount + normalIndex.get(tri[1]), baseNormalCount + normalIndex.get(tri[2]),
					baseTexCount + tri[3], baseTexCount + tri[4], baseTexCount + tri[5], triangleMaterials.get(t)));
		}
		Osm2xpLogger.info("Rebuilt tile: " + weldedCorners + "/" + (faceIndices.size() * 3)
				+ " new-face corners welded to original vertices");
		reportEdgeTopology(newFaces);

		float[] allNormals = concat(original.getNormals(), normalArray);
		float[] allTexCoords = concat(original.getTexCoords(), texArray);

		float radius = original.getRadius();
		if (newVertexArray.length > 0) {
			double maxSquared = 0.0;
			for (int i = 0; i + 2 < allVertices.length; i += 3) {
				double dx = allVertices[i];
				double dy = allVertices[i + 1];
				double dz = allVertices[i + 2];
				double squared = dx * dx + dy * dy + dz * dz;
				if (squared > maxSquared) {
					maxSquared = squared;
				}
			}
			radius = (float) Math.sqrt(maxSquared);
		}

		return new BtgTile(original.getVersion(), original.getCreationTime(), center, radius, allVertices, allNormals,
				allTexCoords, newFaces, original.getPointGroups());
	}

	/**
	 * Seeds the shared vertex index with the original tile's vertices (position key
	 * to original index), so new geometry welds to the untouched terrain.
	 */
	private void seedOriginalVertices(BtgTile original, BtgVector3 center, Map<String, Integer> index) {
		float[] vertices = original.getVertices();
		int count = original.getVertexCount();
		for (int i = 0; i < count; i++) {
			double[] local = toLocal(vertices, center.getX(), center.getY(), center.getZ(), i);
			index.putIfAbsent(key(local[0], local[1], local[2]), i);
		}
	}

	private void addTriangles(List<Triangle> triangles, List<double[]> vertices, List<float[]> texCoords,
			Map<String, Integer> vertexIndex, Map<String, Integer> texIndex, List<int[]> faceIndices,
			List<String> triangleMaterials, BtgVector3 center, int baseVertexCount) {
		for (Triangle triangle : triangles) {
			if (samePoint(triangle.e0, triangle.n0, triangle.a0, triangle.e1, triangle.n1, triangle.a1)
					|| samePoint(triangle.e1, triangle.n1, triangle.a1, triangle.e2, triangle.n2, triangle.a2)
					|| samePoint(triangle.e0, triangle.n0, triangle.a0, triangle.e2, triangle.n2, triangle.a2)) {
				continue;
			}
			double uvScale = FlightGearAirfieldMaterials.uvScale(triangle.material);
			int v0 = addVertex(vertices, vertexIndex, center, triangle.e0, triangle.n0, triangle.a0, baseVertexCount);
			int v1 = addVertex(vertices, vertexIndex, center, triangle.e1, triangle.n1, triangle.a1, baseVertexCount);
			int v2 = addVertex(vertices, vertexIndex, center, triangle.e2, triangle.n2, triangle.a2, baseVertexCount);
			int t0 = addTexCoord(texCoords, texIndex, triangle.e0, triangle.n0, uvScale);
			int t1 = addTexCoord(texCoords, texIndex, triangle.e1, triangle.n1, uvScale);
			int t2 = addTexCoord(texCoords, texIndex, triangle.e2, triangle.n2, uvScale);
			faceIndices.add(new int[] { v0, v1, v2, t0, t1, t2 });
			triangleMaterials.add(triangle.material);
		}
	}

	/**
	 * Altitude at (east, north) by barycentric interpolation over the source
	 * triangle.
	 */
	private static double interpolateAltitude(TerrainFace face, double east, double north) {
		double denom = (face.n1 - face.n2) * (face.e0 - face.e2) + (face.e2 - face.e1) * (face.n0 - face.n2);
		if (Math.abs(denom) < 1e-12) {
			return face.a0;
		}
		double w0 = ((face.n1 - face.n2) * (east - face.e2) + (face.e2 - face.e1) * (north - face.n2)) / denom;
		double w1 = ((face.n2 - face.n0) * (east - face.e2) + (face.e0 - face.e2) * (north - face.n2)) / denom;
		double w2 = 1.0 - w0 - w1;
		return w0 * face.a0 + w1 * face.a1 + w2 * face.a2;
	}

	private static float[] concat(float[] first, float[] second) {
		float[] result = new float[first.length + second.length];
		System.arraycopy(first, 0, result, 0, first.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	private void accumulateNormal(double[] accum, float[] vertices, int a, int b, int c) {
		double ax = vertices[3 * a];
		double ay = vertices[3 * a + 1];
		double az = vertices[3 * a + 2];
		double bx = vertices[3 * b];
		double by = vertices[3 * b + 1];
		double bz = vertices[3 * b + 2];
		double cx = vertices[3 * c];
		double cy = vertices[3 * c + 1];
		double cz = vertices[3 * c + 2];
		double ux = bx - ax;
		double uy = by - ay;
		double uz = bz - az;
		double vx = cx - ax;
		double vy = cy - ay;
		double vz = cz - az;
		double nx = uy * vz - uz * vy;
		double ny = uz * vx - ux * vz;
		double nz = ux * vy - uy * vx;
		accum[3 * a] += nx;
		accum[3 * a + 1] += ny;
		accum[3 * a + 2] += nz;
		accum[3 * b] += nx;
		accum[3 * b + 1] += ny;
		accum[3 * b + 2] += nz;
		accum[3 * c] += nx;
		accum[3 * c + 1] += ny;
		accum[3 * c + 2] += nz;
	}

	private int addVertex(List<double[]> vertices, Map<String, Integer> index, BtgVector3 center, double east,
			double north, double alt, int baseVertexCount) {
		String key = key(east, north, alt);
		Integer existing = index.get(key);
		if (existing != null) {
			return existing;
		}
		double[] lonLat = FlightGearCoordinateUtils.localToGeodetic(east, north, datumLon, datumLat);
		double[] ecef = FlightGearCoordinateUtils.geodeticToEcef(lonLat[0], lonLat[1], alt);
		int id = baseVertexCount + vertices.size();
		vertices.add(new double[] { ecef[0] - center.getX(), ecef[1] - center.getY(), ecef[2] - center.getZ() });
		index.put(key, id);
		return id;
	}

	private int addTexCoord(List<float[]> texCoords, Map<String, Integer> index, double east, double north,
			double scale) {
		String key = key(east, north) + "|" + Double.doubleToLongBits(scale);
		Integer existing = index.get(key);
		if (existing != null) {
			return existing;
		}
		int id = texCoords.size();
		texCoords.add(new float[] { (float) (east / scale), (float) (north / scale) });
		index.put(key, id);
		return id;
	}

	private static String key(double east, double north) {
		return Math.round(east / QUANTIZATION_METERS) + "," + Math.round(north / QUANTIZATION_METERS);
	}

	private static String key(double east, double north, double alt) {
		return key(east, north) + "," + Math.round(alt / QUANTIZATION_METERS);
	}

	private static boolean samePoint(double e0, double n0, double a0, double e1, double n1, double a1) {
		return key(e0, n0, a0).equals(key(e1, n1, a1));
	}

	private double[] toLocal(float[] vertices, double cx, double cy, double cz, int index) {
		double[] geodetic = FlightGearCoordinateUtils.ecefToGeodetic(vertices[3 * index] + cx,
				vertices[3 * index + 1] + cy, vertices[3 * index + 2] + cz);
		double[] local = FlightGearCoordinateUtils.toLocal(geodetic[0], geodetic[1], datumLon, datumLat);
		return new double[] { local[0], local[1], geodetic[2] };
	}

	private boolean intersectsRegion(Envelope region, double[] p0, double[] p1, double[] p2) {
		double minX = Math.min(p0[0], Math.min(p1[0], p2[0]));
		double maxX = Math.max(p0[0], Math.max(p1[0], p2[0]));
		double minY = Math.min(p0[1], Math.min(p1[1], p2[1]));
		double maxY = Math.max(p0[1], Math.max(p1[1], p2[1]));
		return maxX >= region.getMinX() && minX <= region.getMaxX() && maxY >= region.getMinY()
				&& minY <= region.getMaxY();
	}
}
