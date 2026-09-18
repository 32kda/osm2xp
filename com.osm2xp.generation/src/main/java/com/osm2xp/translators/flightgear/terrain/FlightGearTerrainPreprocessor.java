package com.osm2xp.translators.flightgear.terrain;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.core.model.osm.Relation;
import com.osm2xp.core.model.osm.Way;
import com.osm2xp.core.parsers.IOSMDataVisitor;

import math.geom2d.Box2D;

/**
 * Pre-pass that makes sure the FlightGear terrain covering the input file is
 * available before the main generation pass runs (buildings probe the terrain
 * for ground elevation, and airfield BTG patching reads it). The terrain is
 * cached under {@code source_tiles} and copied into the generated scenery's
 * {@code Terrain/} directory.
 * <p>
 * The area is taken from the source bounding box when the file provides one
 * (visited before any nodes, and the work starts immediately so it overlaps the
 * rest of the pre-pass scan). Files without a bounding box fall back to the
 * min/max lon/lat of the visited nodes, and the work runs in
 * {@link #complete()}.
 *
 * @author osm2xp
 */
public class FlightGearTerrainPreprocessor implements IOSMDataVisitor {

	private final AbstractTerrainDownloader downloader;
	private final TerrainPaths paths;

	private double minLon = Double.POSITIVE_INFINITY;
	private double minLat = Double.POSITIVE_INFINITY;
	private double maxLon = Double.NEGATIVE_INFINITY;
	private double maxLat = Double.NEGATIVE_INFINITY;
	private boolean headerExtent;
	private boolean hasPoints;
	private boolean workStarted;

	public FlightGearTerrainPreprocessor(AbstractTerrainDownloader downloader, TerrainPaths paths) {
		this.downloader = downloader;
		this.paths = paths;
	}

	@Override
	public void visit(Box2D box) {
		if (headerExtent || box == null || !isValid(box)) {
			return;
		}
		minLon = box.getMinX();
		minLat = box.getMinY();
		maxLon = box.getMaxX();
		maxLat = box.getMaxY();
		headerExtent = true;
		prepareTerrain();
	}

	@Override
	public void visit(Node node) {
		if (headerExtent || node == null) {
			return;
		}
		minLon = Math.min(minLon, node.getLon());
		minLat = Math.min(minLat, node.getLat());
		maxLon = Math.max(maxLon, node.getLon());
		maxLat = Math.max(maxLat, node.getLat());
		hasPoints = true;
	}

	@Override
	public void visit(Way way) {
	}

	@Override
	public void visit(Relation relation) {
	}

	@Override
	public void complete() {
		if (!headerExtent && hasPoints) {
			prepareTerrain();
		}
	}

	private void prepareTerrain() {
		if (workStarted || paths == null || paths.getCacheDir() == null) {
			return;
		}
		workStarted = true;
		Osm2xpLogger.info("Preparing FlightGear terrain for area " + minLon + "," + minLat + " .. " + maxLon + ","
				+ maxLat + " (cache " + paths.getCacheDir() + ")");
		try {
			downloader.download(minLon, minLat, maxLon, maxLat, paths);
		} catch (Throwable t) {
			Osm2xpLogger.error("Error preparing FlightGear terrain", t);
		}
	}

	/** A bounding box is usable only when it has a positive extent (rejects the all-zero PBF default). */
	private static boolean isValid(Box2D box) {
		return box.getMinX() < box.getMaxX() && box.getMinY() < box.getMaxY();
	}
}
