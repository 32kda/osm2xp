package com.osm2xp.translators.impl;

import java.io.File;
import java.util.List;

import org.openstreetmap.osmosis.osmbinary.Osmformat.HeaderBBox;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.model.osm.Node;
import com.osm2xp.model.osm.OsmPolygon;
import com.osm2xp.model.osm.Relation;
import com.osm2xp.model.osm.Tag;
import com.osm2xp.model.osm.Way;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.utils.FilesUtils;
import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.OsmUtils;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.logging.Osm2xpLogger;

/**
 * G2XPL translator implementation. Generates a text file , that G2XPl will use
 * to store roof color for each building.
 * 
 * Osm2xp will then use this file to inject roof color into osm data. It's still
 * a work in progress translator.
 * 
 * @author Benjamin Blanchet following Rob (g2xpl) specifications.
 * 
 */
public class G2xplTranslatorImpl implements ITranslator {
	/**
	 * current lat/long tile.
	 */
	private Point2D currentTile;
	/**
	 * generated file folder path.
	 */
	private String folderPath;
	/**
	 * generated file.
	 */
	private File txtFile;

	/**
	 * Constructor.
	 * 
	 * @param currentTile
	 *            current lat/long tile.
	 * @param folderPath
	 *            generated file folder path.
	 */
	public G2xplTranslatorImpl(Point2D currentTile, String folderPath) {
		super();
		this.currentTile = currentTile;
		this.folderPath = folderPath;
		this.txtFile = new File(this.folderPath + File.separator + "g2xpl_"
				+ currentTile.y + "_" + currentTile.x + ".txt");
	}

	@Override
	public void processNode(Node node) throws Osm2xpBusinessException {
	}

	@Override
	public void processPolygon(OsmPolygon osmPolygon)
			throws Osm2xpBusinessException {
		LinearRing2D polygon = new LinearRing2D();
		if (OsmUtils.isBuilding(osmPolygon.getTags())) {
			polygon = GeomUtils.getPolygonFromOsmNodes(osmPolygon.getNodes());
			polygon = GeomUtils.setClockwise(polygon);
			StringBuilder wayText = new StringBuilder();
			wayText.append(osmPolygon.getId() + ":");
			for (Point2D point : polygon.getVertices()) {
				wayText.append(point.y + "," + point.x + ",");
			}
			wayText.replace(wayText.lastIndexOf(","), wayText.length(), "");
			wayText.append("\n");
			FilesUtils.writeTextToFile(this.txtFile, wayText.toString(), true);
		}

	}

	@Override
	public void processRelation(Relation relation)
			throws Osm2xpBusinessException {
	}

	@Override
	public void complete() {
		Osm2xpLogger.info("G2xpl binding file finished.");
	}

	@Override
	public void init() {
		Osm2xpLogger.info("Starting G2xpl binding file for tile "
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
		List<Tag> tags = way.getTag();
		return (OsmUtils.isBuilding(tags));
	}
	
	@Override
	public void processBoundingBox(HeaderBBox bbox) {
		// Do nothing
	}
}
