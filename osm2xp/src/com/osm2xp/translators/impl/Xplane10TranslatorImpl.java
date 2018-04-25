package com.osm2xp.translators.impl;

import java.util.Date;
import java.util.Random;

import math.geom2d.Point2D;

import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.model.osm.OsmPolygon;
import com.osm2xp.model.stats.GenerationStats;
import com.osm2xp.translators.IPolyHandler;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.MiscUtils;
import com.osm2xp.utils.OsmUtils;
import com.osm2xp.utils.DsfObjectsProvider;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.helpers.StatsHelper;
import com.osm2xp.utils.helpers.XplaneExclusionsHelper;
import com.osm2xp.utils.helpers.XplaneOptionsHelper;
import com.osm2xp.utils.logging.Osm2xpLogger;
import com.osm2xp.writers.IWriter;

/**
 * Xplane 10 translator implementation. Generates Xplane scenery from osm data.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class Xplane10TranslatorImpl extends XPlaneTranslatorImpl implements ITranslator {

	/**
	 * Smart exclusions helper.
	 */
	XplaneExclusionsHelper exclusionsHelper = new XplaneExclusionsHelper();
	
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
	public Xplane10TranslatorImpl(GenerationStats stats, IWriter writer,
			Point2D currentTile, String folderPath,
			DsfObjectsProvider dsfObjectsProvider) {
		super(stats, writer, currentTile, folderPath, dsfObjectsProvider);
	}

	@Override
	public void complete() {
		
		for (IPolyHandler polyHandler : polyHandlers) {
			polyHandler.translationComplete();
		}

		// if smart exclusions enabled and tile is not empty, send them to
		// writer
		if (!StatsHelper.isTileEmpty(stats)
				&& XplaneOptionsHelper.getOptions().isSmartExclusions()) {
			String exclusions = exclusionsHelper.exportExclusions();
			writer.complete(exclusions);

		} else {
			writer.complete(null);
		}

		if (currentTile != null && !StatsHelper.isTileEmpty(stats)) {
			Osm2xpLogger.info("Tile " + (int) currentTile.y + "/"
					+ (int) currentTile.x + " stats : "
					+ stats.getBuildingsNumber() + " buildings, "
					+ stats.getForestsNumber() + " forests, "
					+ stats.getStreetlightsNumber() + " street lights, "
					+ stats.getObjectsNumber() + " objects. (generation took "
					+ MiscUtils.getTimeDiff(startTime, new Date()) + ")");

			// stats
			try {
				if (XplaneOptionsHelper.getOptions().isGenerateXmlStats()
						|| XplaneOptionsHelper.getOptions()
								.isGeneratePdfStats()) {
					StatsHelper.getStatsList().add(stats);

				}
				if (XplaneOptionsHelper.getOptions().isGenerateXmlStats()) {
					StatsHelper.saveStats(folderPath, currentTile, stats);
				}
				if (XplaneOptionsHelper.getOptions().isGeneratePdfStats()) {
					StatsHelper.generatePdfReport(folderPath, stats);
				}
			} catch (Osm2xpBusinessException e) {
				Osm2xpLogger.error("Error saving stats file for tile "
						+ currentTile, e);
			}
		} else if (!GuiOptionsHelper.getOptions().isSinglePass()) {
			Osm2xpLogger.info("Tile " + (int) currentTile.y + "/"
					+ (int) currentTile.x + " is empty, no dsf generated");
		}
		if (translationListener != null) {
			translationListener.complete();
		}
	}

	@Override
	public void init() {
		// writer initialization
		writer.init(currentTile);
		// exclusionHelper
		if (XplaneOptionsHelper.getOptions().isSmartExclusions()) {
			exclusionsHelper.run();
		}

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
							+ " " + (lightLoc.y) + " " + (lightLoc.x) + " "
							+ orientation);
					sb.append(LINE_SEP);
					// stats
					StatsHelper.addStreetLight(stats);
				}
			}
		}

		writer.write(sb.toString());
	}

	
	/**
	 * Construct and write a facade building in the dsf file.
	 * 
	 * @param osmPolygon
	 *            osm polygon
	 * @return true if a building has been gennerated in the dsf file.
	 */
	protected boolean processBuilding(OsmPolygon osmPolygon) {
		Boolean result = false;
		if (XplaneOptionsHelper.getOptions().isGenerateBuildings()
				&& OsmUtils.isBuilding(osmPolygon.getTags())
				&& !OsmUtils.isExcluded(osmPolygon.getTags(),
						osmPolygon.getId())
				&& !specialExcluded(osmPolygon)
				&& !osmPolygon.isPartial()
				&& osmPolygon.getPolygon().getVertexNumber() > BUILDING_MIN_VECTORS
				&& osmPolygon.getPolygon().getVertexNumber() < BUILDING_MAX_VECTORS) { 
	
			// check that the largest vector of the building
			// and that the area of the osmPolygon.getPolygon() are over the
			// minimum values set by the user
			Double maxVector = osmPolygon.getMaxVectorSize();
			if (maxVector > XplaneOptionsHelper.getOptions()
					.getMinHouseSegment()
					&& maxVector < XplaneOptionsHelper.getOptions()
							.getMaxHouseSegment()
					&& ((osmPolygon.getPolygon().getArea() * 100000) * 100000) > XplaneOptionsHelper
							.getOptions().getMinHouseArea()) {
	
				// simplify shape if checked and if necessary
				if (GuiOptionsHelper.getOptions().isSimplifyShapes()
						&& !osmPolygon.isSimplePolygon()) {
					osmPolygon.simplifyPolygon();
				}
	
				// compute height and facade dsf index
				osmPolygon.setHeight(computeBuildingHeight(osmPolygon));
				Integer facade = computeFacadeIndex(osmPolygon);
				if (translationListener != null) {
					translationListener.processBuilding(osmPolygon, facade);
				}
				
				// write building in dsf file
				writeBuildingToDsf(osmPolygon, facade);
				// Smart exclusions
				if (XplaneOptionsHelper.getOptions().isSmartExclusions()) {
					exclusionsHelper.addTodoPolygon(osmPolygon);
					exclusionsHelper.run();
				}
				result = true;
			}
		}
		return result;
	}
}
