package com.osm2xp.translators.airfield.btg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgFace;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.core.parsers.btg.BtgVector3;
import com.osm2xp.translators.flightgear.FlightGearCoordinateUtils;

/**
 * Unit tests for the terrain cut geometry (spec &sect;8.1): a synthetic square
 * heightfield is clipped against a footprint, leaving a hole, and the result is
 * verified to round-trip through the BTG writer/parser.
 */
public class BtgCsgConverterTest {

	private static final GeometryFactory GF = new GeometryFactory();

	@Test
	public void clipLeavesHole() throws Exception {
		BtgTile tile = squareHeightfield();
		BtgCsgConverter converter = new BtgCsgConverter(0.0, 0.0);

		BtgCsgConverter.TerrainPartition partition = converter.partition(tile, null);
		assertEquals(tile.getFaceCount(), partition.getInside().size());
		assertTrue(partition.getOutside().isEmpty());

		Polygon footprint = GF.createPolygon(new Coordinate[] {
				new Coordinate(-100, -100), new Coordinate(100, -100),
				new Coordinate(100, 100), new Coordinate(-100, 100),
				new Coordinate(-100, -100) });

		BtgCsgConverter.ClipResult clip = converter.clipTerrain(partition.getInside(), footprint);
		List<BtgCsgConverter.Triangle> triangles = clip.triangles;
		assertFalse("no terrain survived the cut", triangles.isEmpty());

		for (BtgCsgConverter.Triangle triangle : triangles) {
			double cx = (triangle.e0 + triangle.e1 + triangle.e2) / 3.0;
			double cy = (triangle.n0 + triangle.n1 + triangle.n2) / 3.0;
			assertFalse("hole was not cut: triangle centroid inside footprint",
					footprint.contains(GF.createPoint(new Coordinate(cx, cy))));
		}

		BtgTile rebuilt = converter.rebuild(tile, partition.getOutside(), triangles, new ArrayList<>());
		assertTrue(rebuilt.getVertexCount() > 0);
		assertTrue(rebuilt.getFaceCount() > 0);

		File tmp = File.createTempFile("btg-cut", ".btg.gz");
		try {
			Btg.write(rebuilt, tmp);
			BtgTile reread = Btg.read(tmp);
			assertNotNull(reread);
			assertEquals(rebuilt.getFaceCount(), reread.getFaceCount());
			assertEquals(rebuilt.getVertexCount(), reread.getVertexCount());
		} finally {
			tmp.delete();
		}
	}

	@Test
	public void convexHullBufferRingTriangulates() {
		GeometryFactory factory = new GeometryFactory();
		Polygon footprint = factory.createPolygon(new Coordinate[] {
				new Coordinate(0, 0), new Coordinate(400, 0), new Coordinate(400, 200),
				new Coordinate(250, 100), new Coordinate(200, 200), new Coordinate(0, 200),
				new Coordinate(0, 0) });
		Geometry hull = new ConvexHull(footprint).getConvexHull();
		assertTrue(hull instanceof Polygon);

		double buffer = 100.0;
		Geometry buffered = hull.buffer(buffer);
		Geometry ring = buffered.difference(hull);
		assertFalse(ring.isEmpty());

		PolygonTriangulator triangulator = new PolygonTriangulator();
		List<double[]> triangles = new ArrayList<>();
		for (Polygon polygon : com.osm2xp.utils.geometry.GeomUtils.flatMapToPoly(ring)) {
			triangles.addAll(triangulator.triangulate(polygon));
		}
		assertFalse("ring produced no triangles", triangles.isEmpty());
		for (double[] triangle : triangles) {
			assertEquals(6, triangle.length);
		}
	}

	private static BtgTile squareHeightfield() {
		double[] es = { -400, -200, 0, 200, 400 };
		double[] ns = { -400, -200, 0, 200, 400 };
		int n = es.length;

		double[] centerEcef = FlightGearCoordinateUtils.geodeticToEcef(0.0, 0.0, 100.0);
		BtgVector3 center = new BtgVector3(centerEcef[0], centerEcef[1], centerEcef[2]);

		float[] vertices = new float[n * n * 3];
		float maxSquared = 0.0f;
		for (int j = 0; j < n; j++) {
			for (int i = 0; i < n; i++) {
				// gentle, non-flat heightfield (exercises the height interpolation)
				double alt = 100.0 + 10.0 * Math.sin(es[i] / 250.0) * Math.cos(ns[j] / 250.0);
				double[] lonLat = FlightGearCoordinateUtils.localToGeodetic(es[i], ns[j], 0.0, 0.0);
				double[] ecef = FlightGearCoordinateUtils.geodeticToEcef(lonLat[0], lonLat[1], alt);
				int idx = j * n + i;
				float x = (float) (ecef[0] - centerEcef[0]);
				float y = (float) (ecef[1] - centerEcef[1]);
				float z = (float) (ecef[2] - centerEcef[2]);
				vertices[3 * idx] = x;
				vertices[3 * idx + 1] = y;
				vertices[3 * idx + 2] = z;
				maxSquared = Math.max(maxSquared, x * x + y * y + z * z);
			}
		}

		List<BtgFace> faces = new ArrayList<>();
		for (int j = 0; j < n - 1; j++) {
			for (int i = 0; i < n - 1; i++) {
				int v00 = j * n + i;
				int v10 = j * n + i + 1;
				int v01 = (j + 1) * n + i;
				int v11 = (j + 1) * n + i + 1;
				faces.add(new BtgFace(v00, v10, v11, -1, -1, -1, "Grass"));
				faces.add(new BtgFace(v00, v11, v01, -1, -1, -1, "Grass"));
			}
		}

		return new BtgTile(0, 0L, center, (float) Math.sqrt(maxSquared), vertices, new float[0], new float[0],
				faces, java.util.Collections.emptyList());
	}
}
