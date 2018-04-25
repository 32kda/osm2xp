package com.osm2xp.translators.impl;

import java.util.ArrayList;
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
import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.OsmUtils;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.logging.Osm2xpLogger;
import com.osm2xp.writers.IWriter;

/**
 * OSM Translator implementation. Generates .osm files. Usefull to split a .pbf
 * file into multiples .osm files, it will also generate small files as only
 * building/forests/objects will be exported .
 * 
 * @author Benjamin Blanchet
 * 
 */
public class OsmTranslatorImpl implements ITranslator {
	/**
	 * file writer.
	 */
	private IWriter writer;
	/**
	 * current lat/long tile.
	 */
	private Point2D currentTile;
	/**
	 * osm node index.
	 */
	private int nodeIndex = 1;

	/**
	 * Constructor.
	 * 
	 * @param writer
	 *            file writer.
	 * @param currentTile
	 *            current lat/long tile.
	 */
	public OsmTranslatorImpl(IWriter writer, Point2D currentTile) {
		this.writer = writer;
		this.currentTile = currentTile;

	}

	/**
	 * write a node in the osm file.
	 * 
	 * @param node
	 *            osm node.
	 */
	private void writeNode(Node node) {

		if (node.getTag().isEmpty()) {
			writer.write("<node id=\"" + node.getId() + "\" lat=\""
					+ node.getLat() + "\" lon=\"" + node.getLon()
					+ "\" version=\"1\" />\n");
		} else {
			writer.write("<node id=\"" + node.getId() + "\" lat=\""
					+ node.getLat() + "\" lon=\"" + node.getLon()
					+ "\" version=\"1\" >\n");
			for (Tag tag : node.getTag()) {
				String normalizedTag = OsmUtils.getNormalizedTagText(tag);
				if (normalizedTag != null) {
					writer.write(normalizedTag);
				}
			}
			writer.write("</node>\n");
		}
	}

	@Override
	public void init() {
		Osm2xpLogger.info("Starting OpenStreetMap xml generation of tile "
				+ (int) currentTile.y + "/" + (int) currentTile.x);
		writer.init(currentTile);
	}

	/**
	 * write a way (polygon) in the osm file.
	 * 
	 * @param osmPolygon
	 *            osm polygon.
	 */
	private void writeWay(OsmPolygon osmPolygon) {
		// we create a polygon from the way nodes
		LinearRing2D poly = GeomUtils.getPolygonFromOsmNodes(osmPolygon
				.getNodes());

		// simplify shape if checked and if necessary
		if (GuiOptionsHelper.getOptions().isSimplifyShapes()
				&& !osmPolygon.isSimplePolygon()) {
			poly = GeomUtils.simplifyPolygon(poly);
		}
		List<Node> nodeList = new ArrayList<Node>();
		for (Point2D point : poly.getVertices()) {
			Node node = new Node(null, point.y, point.x, nodeIndex);
			writeNode(node);
			nodeList.add(node);
			nodeIndex++;
		}
		writer.write("<way id=\"" + osmPolygon.getId()
				+ "\" visible=\"true\" version=\"2\" >\n");

		for (Node node : nodeList) {
			writer.write("<nd ref=\"" + node.getId() + "\"/>\n");
		}

		for (Tag tag : osmPolygon.getTags()) {
			String normalizedTag = OsmUtils.getNormalizedTagText(tag);
			if (normalizedTag != null) {
				writer.write(normalizedTag);
			}

		}
		writer.write("</way>\n");

	}

	@Override
	public void complete() {
		writer.complete(null);
		Osm2xpLogger.info("Osm file complete");

	}

	@Override
	public void processNode(Node node) throws Osm2xpBusinessException {
	}

	@Override
	public void processPolygon(OsmPolygon osmPolygon)
			throws Osm2xpBusinessException {

		if (OsmUtils.isBuilding(osmPolygon.getTags())
				&& !OsmUtils.isExcluded(osmPolygon.getTags(),
						osmPolygon.getId())
				|| OsmUtils.isForest(osmPolygon.getTags())
				|| OsmUtils.isObject(osmPolygon.getTags())) {
			writeWay(osmPolygon);

		}

	}

	@Override
	public void processRelation(Relation relation)
			throws Osm2xpBusinessException {
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
