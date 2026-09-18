package com.osm2xp.translators.flightgear;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgFace;
import com.osm2xp.core.parsers.btg.BtgTile;

/**
 * Probes terrain elevation from FlightGear terrain BTG files (the same format
 * TerraSync uses), without spawning external processes.
 * <p>
 * A tile is resolved with {@link FlightGearBucket} at
 * {@code <terrainDir>/<band>/<cell>/<index>.btg.gz} and cached for the duration
 * of the generation run. {@code terrainDir} is the {@code source_tiles} cache or
 * a generated {@code Terrain/} directory.
 */
public class FlightGearTerrainElevationProber {

	private final File terrainDir;
	private final Map<Long, BtgTile> tileCache = new HashMap<>();

	public FlightGearTerrainElevationProber(File terrainDir) {
		this.terrainDir = terrainDir;
	}

	public boolean isAvailable() {
		return terrainDir != null && terrainDir.isDirectory();
	}

	/** Loads (and caches) the terrain tile for the given bucket, or {@code null}. */
	public BtgTile getTile(FlightGearBucket bucket) {
		BtgTile cached = tileCache.get(bucket.getIndex());
		if (cached != null) {
			return cached;
		}
		File tileFile = new File(terrainDir,
				bucket.genBasePath() + File.separator + bucket.getIndex() + ".btg.gz");
		BtgTile tile = null;
		if (tileFile.isFile()) {
			try {
				tile = Btg.read(tileFile);
			} catch (IOException e) {
				Osm2xpLogger.error("Error reading terrain BTG " + tileFile, e);
			}
		}
		tileCache.put(bucket.getIndex(), tile);
		return tile;
	}

	/**
	 * Probes the terrain elevation at the given lon/lat within the given tile.
	 * Uses triangle point-location with barycentric interpolation, falling back to
	 * the nearest vertex. Returns {@link Double#NaN} when no terrain is found.
	 */
	public static double probe(BtgTile tile, double lon, double lat) {
		if (tile == null || tile.getFaceCount() == 0) {
			return Double.NaN;
		}
		float[] verts = tile.getVertices();
		double cx = tile.getCenter().getX();
		double cy = tile.getCenter().getY();
		double cz = tile.getCenter().getZ();
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
		for (BtgFace face : tile.getFaces()) {
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
