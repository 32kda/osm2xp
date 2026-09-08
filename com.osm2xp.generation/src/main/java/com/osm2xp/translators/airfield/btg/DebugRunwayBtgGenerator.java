package com.osm2xp.translators.airfield.btg;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgFace;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.core.parsers.btg.BtgVector3;
import com.osm2xp.translators.flightgear.FlightGearCoordinateUtils;

/**
 * Debug generator that bakes a single runway rectangle into an existing terrain
 * tile (instead of emitting a separate OBJECT_BASE tile, which replaces the
 * terrain). The runway elevation is probed from the terrain mesh itself.
 *
 * <p>
 * Usage: {@code DebugRunwayBtgGenerator <terrain.btg.gz> <output.btg.gz> [material]}.
 */
public class DebugRunwayBtgGenerator {

	private static String material = "pa_rest";

	private static final double LON = 82.5;
	private static final double LAT = 54.0;
	private static final double LENGTH_M = 1000.0;
	private static final double WIDTH_M = 45.0;
	private static final double HEADING_DEG = 0.0;

	public static void main(String[] args) throws Exception {
		File terrainFile = new File(args.length > 0 ? args[0]
				: "d:/Games/FlightGear 2024.1/TerraSync/Terrain/e080n50/e082n54/4301826.btg.gz");
		File outFile = new File(args.length > 1 ? args[1]
				: "C:/Users/root/AppData/Local/Temp/opencode/merged_runway.btg.gz");
		if (args.length > 2) {
			material = args[2];
		}

		BtgTile terrain = Btg.read(terrainFile);
		double elevation = probeElevation(terrain, LON, LAT);

		BtgTile overlay = buildRunway(elevation);
		BtgTile merged = BtgTileMerger.merge(terrain, overlay);
		Btg.write(merged, outFile);

		System.out.println("terrain verts=" + terrain.getVertexCount() + " faces=" + terrain.getFaceCount());
		System.out.println("probed elevation=" + elevation + "m material=" + material);
		System.out.println("merged verts=" + merged.getVertexCount() + " faces=" + merged.getFaceCount()
				+ " normals=" + merged.getNormals().length / 3);
		System.out.println("Wrote " + outFile.getAbsolutePath());
	}

	public static BtgTile buildRunway(double elevation) {
		double[] centerEcef = FlightGearCoordinateUtils.geodeticToEcef(LON, LAT, elevation);
		BtgVector3 center = new BtgVector3(centerEcef[0], centerEcef[1], centerEcef[2]);

		double heading = Math.toRadians(HEADING_DEG);
		double ex = Math.sin(heading);
		double ey = Math.cos(heading);
		double px = -ey;
		double py = ex;
		double halfLength = LENGTH_M / 2.0;
		double halfWidth = WIDTH_M / 2.0;

		double[][] corners = {
				{ -ex * halfLength - px * halfWidth, -ey * halfLength - py * halfWidth },
				{ ex * halfLength - px * halfWidth, ey * halfLength - py * halfWidth },
				{ ex * halfLength + px * halfWidth, ey * halfLength + py * halfWidth },
				{ -ex * halfLength + px * halfWidth, -ey * halfLength + py * halfWidth } };

		float[] vertices = new float[12];
		float[] normals = new float[12];
		float maxSquared = 0.0f;
		for (int i = 0; i < 4; i++) {
			double[] lonLat = FlightGearCoordinateUtils.localToGeodetic(corners[i][0], corners[i][1], LON, LAT);
			double[] ecef = FlightGearCoordinateUtils.geodeticToEcef(lonLat[0], lonLat[1], elevation);
			float x = (float) (ecef[0] - centerEcef[0]);
			float y = (float) (ecef[1] - centerEcef[1]);
			float z = (float) (ecef[2] - centerEcef[2]);
			vertices[3 * i] = x;
			vertices[3 * i + 1] = y;
			vertices[3 * i + 2] = z;
			maxSquared = Math.max(maxSquared, x * x + y * y + z * z);

			double lonR = Math.toRadians(lonLat[0]);
			double latR = Math.toRadians(lonLat[1]);
			normals[3 * i] = (float) (Math.cos(latR) * Math.cos(lonR));
			normals[3 * i + 1] = (float) (Math.cos(latR) * Math.sin(lonR));
			normals[3 * i + 2] = (float) Math.sin(latR);
		}

		float[] texCoords = { 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f };

		List<BtgFace> faces = Arrays.asList(
				new BtgFace(0, 1, 2, 0, 1, 2, 0, 1, 2, material),
				new BtgFace(0, 2, 3, 0, 2, 3, 0, 2, 3, material));

		return new BtgTile(0, 0L, center, (float) Math.sqrt(maxSquared), vertices, normals, texCoords, faces,
				Collections.emptyList());
	}

	public static double probeElevation(BtgTile terrain, double lon, double lat) {
		float[] verts = terrain.getVertices();
		double cx = terrain.getCenter().getX();
		double cy = terrain.getCenter().getY();
		double cz = terrain.getCenter().getZ();
		int count = verts.length / 3;
		double[] vlon = new double[count];
		double[] vlat = new double[count];
		double[] valt = new double[count];
		for (int i = 0; i < count; i++) {
			double[] g = FlightGearCoordinateUtils.ecefToGeodetic(verts[3 * i] + cx, verts[3 * i + 1] + cy,
					verts[3 * i + 2] + cz);
			vlon[i] = g[0];
			vlat[i] = g[1];
			valt[i] = g[2];
		}
		for (BtgFace face : terrain.getFaces()) {
			double alt = interpolate(lon, lat, face.getA(), face.getB(), face.getC(), vlon, vlat, valt);
			if (!Double.isNaN(alt)) {
				return alt;
			}
		}
		return nearestAltitude(lon, lat, vlon, vlat, valt);
	}

	private static double interpolate(double lon, double lat, int a, int b, int c, double[] vlon, double[] vlat,
			double[] valt) {
		double denom = (vlat[b] - vlat[c]) * (vlon[a] - vlon[c]) + (vlon[c] - vlon[b]) * (vlat[a] - vlat[c]);
		if (Math.abs(denom) < 1e-12) {
			return Double.NaN;
		}
		double w1 = ((vlat[b] - vlat[c]) * (lon - vlon[c]) + (vlon[c] - vlon[b]) * (lat - vlat[c])) / denom;
		double w2 = ((vlat[c] - vlat[a]) * (lon - vlon[c]) + (vlon[a] - vlon[c]) * (lat - vlat[c])) / denom;
		double w3 = 1.0 - w1 - w2;
		if (w1 >= -1e-9 && w2 >= -1e-9 && w3 >= -1e-9) {
			return w1 * valt[a] + w2 * valt[b] + w3 * valt[c];
		}
		return Double.NaN;
	}

	private static double nearestAltitude(double lon, double lat, double[] vlon, double[] vlat, double[] valt) {
		double best = Double.MAX_VALUE;
		double bestAlt = 0.0;
		for (int i = 0; i < vlon.length; i++) {
			double dlon = vlon[i] - lon;
			double dlat = vlat[i] - lat;
			double d = dlon * dlon + dlat * dlat;
			if (d < best) {
				best = d;
				bestAlt = valt[i];
			}
		}
		return bestAlt;
	}
}
