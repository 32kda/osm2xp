package com.osm2xp.translators.impl;

import java.text.MessageFormat;

import org.openstreetmap.osmosis.osmbinary.Osmformat.HeaderBBox;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.model.osm.Node;
import com.osm2xp.model.osm.OsmPolygon;
import com.osm2xp.model.osm.Relation;
import com.osm2xp.model.osm.Way;
import com.osm2xp.model.stats.GenerationStats;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.utils.BglUtils;
import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.writers.IWriter;

/**
 * Fsx BGL translator implementation. Generates BGL files of landmarks from osm
 * data (lighthouses , etc).
 * 
 * @author Benjamin Blanchet
 * 
 */
public class FsxBgTranslatorImpl implements ITranslator {
	/**
	 * current lat/long tile.
	 */
	private Point2D currentTile;
	/**
	 * writer object.
	 */
	private IWriter writer;
	/**
	 * xml fsx object string.
	 */
	private static final String FSX_OBJECT_DECLARATION = "<SceneryObject "
			+ "lat=\"{0}\"" + " lon=\"{1}\"" + " alt=\"0\" "
			+ "altitudeIsAgl=\"TRUE\" pitch=\"0\"" + " bank=\"0\" "
			+ " heading=\"0\"" + " imageComplexity=\"NORMAL\"> "
			+ "\n<LibraryObject name=\"{2}\" scale=\"1.0\"/>\n"
			+ "</SceneryObject>";

	/**
	 * Constructor.
	 * 
	 * @param stats
	 *            stats object
	 * @param writer
	 *            file writer
	 * @param currentTile
	 *            current lat/long Tile
	 * @param folderPath
	 *            generated scenery folder Path
	 */
	public FsxBgTranslatorImpl(GenerationStats stats, IWriter writer,
			Point2D currentTile, String folderPath) {
		this.currentTile = currentTile;
		this.writer = writer;
	}

	@Override
	public void processNode(Node node) throws Osm2xpBusinessException {
		// store this node if it is on the current tile
		if (GeomUtils.compareCoordinates(currentTile, node)) {
			// write a 3D object in the bgl file if this node is in an object
			// rule
			String guid = BglUtils
					.getRandomBglGuid(node.getTag(), node.getId());
			if (guid != null) {
				write3dObjectToBgl(new Point2D(node.getLon(), node.getLat()),
						guid);
			}
		}
	}

	/**
	 * write a 3D object to the xml file.
	 * 
	 * @param point2d
	 *            object lat/long location.
	 * @param guid
	 *            object guid.
	 */
	private void write3dObjectToBgl(Point2D point2d, String guid) {
		Double x = point2d.x;
		Double y = point2d.y;
		String fsxObject = MessageFormat.format(FSX_OBJECT_DECLARATION,
				x.toString(), y.toString(), guid);
		writer.write(fsxObject);

	}

	@Override
	public void processPolygon(OsmPolygon osmPolygon)
			throws Osm2xpBusinessException {
		LinearRing2D polygon = GeomUtils.getPolygonFromOsmNodes(osmPolygon
				.getNodes());
		Point2D center = GeomUtils.getPolygonCenter(polygon);
		String guid = BglUtils.getRandomBglGuid(osmPolygon.getTags(),
				osmPolygon.getId());
		if (guid != null) {
			write3dObjectToBgl(center, guid);
		}

	}

	@Override
	public void processRelation(Relation relation)
			throws Osm2xpBusinessException {

	}

	@Override
	public void complete() {
		writer.complete(null);

	}

	@Override
	public void init() {
		writer.init(currentTile);

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
