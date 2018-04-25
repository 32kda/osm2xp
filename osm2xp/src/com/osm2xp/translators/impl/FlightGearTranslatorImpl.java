package com.osm2xp.translators.impl;

import java.io.File;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

import org.apache.commons.lang.StringUtils;
import org.openstreetmap.osmosis.osmbinary.Osmformat.HeaderBBox;

import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.model.options.ObjectFile;
import com.osm2xp.model.options.TagsRule;
import com.osm2xp.model.osm.Node;
import com.osm2xp.model.osm.OsmPolygon;
import com.osm2xp.model.osm.Relation;
import com.osm2xp.model.osm.Way;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.utils.FilesUtils;
import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.OsmUtils;
import com.osm2xp.utils.helpers.FlightGearOptionsHelper;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.logging.Osm2xpLogger;

/**
 * FlightGear Translator implementation.
 * 
 * @author Benjamin Blanchet.
 * 
 */
public class FlightGearTranslatorImpl implements ITranslator {
	/**
	 * current lat/long tile.
	 */
	private Point2D currentTile;
	/**
	 * generated file folder path.
	 */
	private String folderPath;
	/**
	 * generated xml file.
	 */
	private File xmlFile;

	private static final String FLIGHT_GEAR_OBJECT_DECLARATION = "OBJECT_SHARED_AGL {0} {1} {2} {3} {4} {5} {6}\n";

	/**
	 * Constuctor.
	 * 
	 * @param currentTile
	 *            current lat/long tile.
	 * @param folderPath
	 *            folder path.
	 */
	public FlightGearTranslatorImpl(Point2D currentTile, String folderPath) {
		super();
		this.currentTile = currentTile;
		this.folderPath = folderPath;
		File file = new File(GuiOptionsHelper.getOptions().getCurrentFilePath());
		String fileName = file.getName().substring(0,
				file.getName().indexOf("."));
		this.xmlFile = new File(this.folderPath + File.separator + fileName
				+ "_" + currentTile.y + "_" + currentTile.x + ".stg");

	}

	@Override
	public void processNode(Node node) throws Osm2xpBusinessException {
	}

	@Override
	public void processPolygon(OsmPolygon osmPolygon)
			throws Osm2xpBusinessException {
		if (osmPolygon != null && osmPolygon.getNodes() != null) {
			// check if the current polygon has some tags this translator wants
			// to use
			List<TagsRule> matchingTags = OsmUtils.getMatchingRules(
					FlightGearOptionsHelper.getOptions().getObjectsRules()
							.getRules(), osmPolygon);
			if (matchingTags != null && !matchingTags.isEmpty()) {

				LinearRing2D polygon = new LinearRing2D();
				// if the processor sent back a complete list of nodes
				// construct a polygon from those nodes
				polygon = GeomUtils.getPolygonFromOsmNodes(osmPolygon
						.getNodes());
				// inject it into the scenery file.
				injectPolygonIntoScenery(polygon, matchingTags);
			}
		}
	}

	private void injectPolygonIntoScenery(LinearRing2D polygon,
			List<TagsRule> matchingTagsRules) {

		// simplify shape until we have a simple rectangle

		LinearRing2D simplifiedPolygon = GeomUtils.simplifyPolygon(polygon);

		// shuffle matching tags rules
		Collections.shuffle(matchingTagsRules);
		TagsRule logicRule = matchingTagsRules.get(0);
		// shuffle objects
		Collections.shuffle(logicRule.getObjectsFiles());
		// select object that will be injected.
		ObjectFile object = logicRule.getObjectsFiles().get(0);

		if (object != null && StringUtils.isNotBlank(object.getPath())) {

			// compute center point of the polygon.
			Point2D centerPoint = GeomUtils.getPolygonCenter(simplifiedPolygon);
			// params : <object-path> <longitude> <latitude>
			// <elevation-offset-m> <heading-deg> <pitch-deg> <roll-deg>
			String objectDeclaration = MessageFormat.format(
					FLIGHT_GEAR_OBJECT_DECLARATION,
					new Object[] { object.getPath(), centerPoint.y,
							centerPoint.x, 0, 1, 0, 0 });
			objectDeclaration = objectDeclaration.replaceAll(",", ".");
			FilesUtils.writeTextToFile(this.xmlFile, objectDeclaration, true);

		}
	}

	@Override
	public void processRelation(Relation relation)
			throws Osm2xpBusinessException {
	}

	@Override
	public void complete() {
		Osm2xpLogger.info("FlightGear file finished.");
	}

	@Override
	public void init() {
		Osm2xpLogger.info("Starting FlightGear file for tile "
				+ this.currentTile.y + "/" + this.currentTile.x + ".");
	}

	@Override
	public Boolean mustStoreNode(Node node) {
		Boolean result = true;
		if (!GuiOptionsHelper.getOptions().isSinglePass()) {
			result = GeomUtils.compareCoordinates(currentTile, node);
		}
		return result;
	}

	@Override
	public Boolean mustStoreWay(Way way) {
		return null;
	}
	
	@Override
	public void processBoundingBox(HeaderBBox bbox) {
		// Do nothing
	}
}
