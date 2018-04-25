package com.osm2xp.translators.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import math.geom2d.Point2D;

import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.model.osm.OsmPolygon;
import com.osm2xp.model.stats.GenerationStats;
import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.OsmUtils;
import com.osm2xp.utils.DsfObjectsProvider;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.helpers.StatsHelper;
import com.osm2xp.utils.helpers.XplaneOptionsHelper;
import com.osm2xp.writers.IWriter;

/**
 * Xplane 9 translator implementation. Generates Xplane scenery from osm data.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class Xplane9TranslatorImpl extends XPlaneTranslatorImpl {

	/**
	 * current lat/long tile.
	 */
	private Point2D currentTile;
	/**
	 * stats object.
	 */
	private GenerationStats stats;
	/**
	 * file writer.
	 */
	private IWriter writer;
	/**
	 * dsf object provider.
	 */
	private DsfObjectsProvider dsfObjectsProvider;

	static int merde;

	/**
	 * Constructor.
	 * 
	 * @param stats
	 *            stats object.
	 * @param writer
	 *            file writer.
	 * @param currentTile
	 *            current lat/long tile.
	 * @param folderPath
	 *            generated scenery folder path.
	 * @param dsfObjectsProvider
	 *            dsf object provider.
	 */
	public Xplane9TranslatorImpl(GenerationStats stats, IWriter writer,
			Point2D currentTile, String folderPath,
			DsfObjectsProvider dsfObjectsProvider) {
		super(stats, writer, currentTile, folderPath, dsfObjectsProvider);
	}

	

	/**
	 * Write streetlight objects in dsf file.
	 * 
	 * @param osmPolygon
	 *            osm road polygon
	 */
	public void writeStreetLightToDsf(OsmPolygon osmPolygon) {
		// init d'un entier pour modulo densité street lights
		Integer densityIndex = 0;
		if (XplaneOptionsHelper.getOptions().getLightsDensity() == 0) {
			densityIndex = 10;
		} else {
			if (XplaneOptionsHelper.getOptions().getLightsDensity() == 1) {
				densityIndex = 5;
			} else {
				if (XplaneOptionsHelper.getOptions().getLightsDensity() == 2)
					densityIndex = 3;
			}
		}
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < osmPolygon.getPolygon().getVertices().size(); i++) {
			if ((i % densityIndex) == 0) {
				Point2D lightLoc = osmPolygon.getPolygon().getVertex(i);
				lightLoc.x = lightLoc.x + 0.0001;
				lightLoc.y = lightLoc.y + 0.0001;
				if (GeomUtils.compareCoordinates(lightLoc, currentTile)) {
					Random randomGenerator = new Random();
					int orientation = randomGenerator.nextInt(360);
					sb.append("OBJECT "
							+ dsfObjectsProvider.getRandomStreetLightObject()
							+ " " + (lightLoc.x) + " " + (lightLoc.y) + " "
							+ orientation);
					sb.append(System.getProperty("line.separator"));
					// stats
					StatsHelper.addStreetLight(stats);
				}
			}
		}

		writer.write(sb.toString());
	}

	@Override
	public void processPolygon(OsmPolygon osmPolygon)
			throws Osm2xpBusinessException {

		// polygon is null or empty don't process it
		if (osmPolygon.getNodes() != null && !osmPolygon.getNodes().isEmpty()) {
			// polygon MUST be in clockwise order
			osmPolygon.setPolygon(GeomUtils.forceClockwise(osmPolygon
					.getPolygon()));
			// if we're on a single pass mode
			// here we must check if the polygon is on more than one tile
			// if that's the case , we must split it into several polys
			List<OsmPolygon> polygons = new ArrayList<OsmPolygon>();
			if (GuiOptionsHelper.getOptions().isSinglePass()) {
				polygons.addAll(osmPolygon.splitPolygonAroundTiles());
			}
			// if not on a single pass mode, add this single polygon to the poly
			// list
			else {
				polygons.add(osmPolygon);
			}
			// try to transform those polygons into dsf objects.
			for (OsmPolygon poly : polygons) {
				// try to generate a 3D object
				if (!process3dObject(poly)) {
					// nothing generated? try to generate a facade building.
					if (!processBuilding(poly)) {
						// nothing generated? try to generate a forest.
						if (!processForest(poly)) {
							// still nothing? try to generate a streetlight.
							if (!processStreetLights(poly)) {
								processOther(poly);
							}
						}
					}
				}
			}
		}
	}

	
	/**
	 * send a streetLight in the dsf file.
	 * 
	 * @param osmPolygon
	 *            osm polygon
	 * @return true if a streetlight has been written in the dsf file.
	 */
	private boolean processStreetLights(OsmPolygon osmPolygon) {
		Boolean result = false;
		if (XplaneOptionsHelper.getOptions().isGenerateStreetLights()
				&& OsmUtils.isTagInTagsList("highway", "residential",
						osmPolygon.getTags())) {
			writeStreetLightToDsf(osmPolygon);
			result = true;
		}
		return result;
	}

}
