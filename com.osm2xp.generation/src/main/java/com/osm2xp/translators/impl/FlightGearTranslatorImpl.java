package com.osm2xp.translators.impl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang.StringUtils;

import com.osm2xp.core.exceptions.Osm2xpBusinessException;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.generation.options.ObjectFile;
import com.osm2xp.generation.options.rules.TagsRule;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.translators.flightgear.FlightGearBuildingAnalyzer;
import com.osm2xp.translators.flightgear.FlightGearBuildingListEntry;
import com.osm2xp.utils.FilesUtils;
import com.osm2xp.utils.geometry.GeomUtils;
import com.osm2xp.utils.osm.OsmUtils;

import math.geom2d.Box2D;
import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

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

	private BufferedWriter buildingListWriter;
	private boolean buildingListHeaderWritten;
	private final Random random = new Random();

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
		File file = new File(GlobalOptionsProvider.getOptions().getCurrentFilePath());
		String fileName = file.getName().substring(0,
				file.getName().indexOf("."));
		this.xmlFile = new File(this.folderPath + File.separator + fileName
				+ "_" + currentTile.y() + "_" + currentTile.x() + ".stg");

	}

	@Override
	public void processNode(Node node) throws Osm2xpBusinessException {
	}

	@Override
	public void processPolyline(OsmPolyline osmPolyline)
			throws Osm2xpBusinessException {
		if (osmPolyline != null && osmPolyline.getNodes() != null) {
			FlightGearOptions options = FlightGearOptionsProvider.getOptions();

			// Existing OBJECT_SHARED_AGL handling for rule-matched objects
			List<TagsRule> matchingTags = OsmUtils.getMatchingRules(
					options.getObjectsRules().getRules(), osmPolyline);
			if (matchingTags != null && !matchingTags.isEmpty()) {
				LinearRing2D polygon = GeomUtils.getPolygonFromOsmNodes(osmPolyline.getNodes());
				injectPolygonIntoScenery(polygon, matchingTags);
				return;
			}

			// Building analysis for BUILDING_LIST
			if (options.isGenerateBuildings() && osmPolyline instanceof OsmPolygon
					&& OsmUtils.isBuilding(osmPolyline.getTags())) {
				processBuilding((OsmPolygon) osmPolyline);
			}
		}
	}

	private void processBuilding(OsmPolygon polygon) throws Osm2xpBusinessException {
		FlightGearOptions options = FlightGearOptionsProvider.getOptions();
		FlightGearBuildingAnalyzer analyzer = new FlightGearBuildingAnalyzer(options, random);
		FlightGearBuildingListEntry entry = analyzer.analyze(polygon);
		if (entry != null) {
			try {
				if (buildingListWriter != null) {
					buildingListWriter.write(entry.formatDataLine());
				}
				if (!buildingListHeaderWritten) {
					double tileCenterLon = currentTile.x() + 0.5;
					double tileCenterLat = currentTile.y() + 0.5;
					String header = String.format(Locale.US,
							"BUILDING_LIST BuildingList.txt.gz OSMBuildings %.6f %.6f 0.00\n",
							tileCenterLon, tileCenterLat);
					FilesUtils.writeTextToFile(xmlFile, header, true);
					buildingListHeaderWritten = true;
				}
			} catch (Exception e) {
				throw new Osm2xpBusinessException("Error writing building list entry", e);
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
			Point2D centerPoint = GeomUtils.getPolylineCenter(simplifiedPolygon);
			// params : <object-path> <longitude> <latitude>
			// <elevation-offset-m> <heading-deg> <pitch-deg> <roll-deg>
			String objectDeclaration = MessageFormat.format(
					FLIGHT_GEAR_OBJECT_DECLARATION,
					new Object[] { object.getPath(), centerPoint.y(),
							centerPoint.x(), 0, 1, 0, 0 });
			objectDeclaration = objectDeclaration.replaceAll(",", ".");
			FilesUtils.writeTextToFile(this.xmlFile, objectDeclaration, true);

		}
	}

	@Override
	public void complete() {
		if (buildingListWriter != null) {
			try {
				buildingListWriter.close();
			} catch (Exception e) {
				Osm2xpLogger.error("Error closing building list file", e);
			}
		}
		Osm2xpLogger.info("FlightGear file finished.");
	}

	@Override
	public void init() {
		FlightGearOptions options = FlightGearOptionsProvider.getOptions();
		if (options.isGenerateBuildings()) {
			try {
				File buildingListFile = new File(folderPath, "BuildingList.txt.gz");
				if (!new File(folderPath).exists()) {
					new File(folderPath).mkdirs();
				}
				GZIPOutputStream gzipOut = new GZIPOutputStream(new FileOutputStream(buildingListFile));
				buildingListWriter = new BufferedWriter(new OutputStreamWriter(gzipOut, StandardCharsets.UTF_8));
			} catch (Exception e) {
				Osm2xpLogger.error("Error initializing building list file", e);
			}
		}
		Osm2xpLogger.info("Starting FlightGear file for tile "
				+ this.currentTile.y() + "/" + this.currentTile.x() + ".");
	}

	@Override
	public boolean mustStoreNode(Node node) {
		return GeomUtils.compareCoordinates(currentTile, node);
	}

	@Override
	public boolean mustProcessPolyline(List<Tag> tags) {
		return true;
	}

	
	@Override
	public void processBoundingBox(Box2D bbox) {
		// Do nothing
	}
	
	@Override
	public int getMaxHoleCount(List<Tag> tags) {
		return Integer.MAX_VALUE; //TODO is this supported for FlightGear ?
	}

}
