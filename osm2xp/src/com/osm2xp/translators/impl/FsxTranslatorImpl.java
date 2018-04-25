package com.osm2xp.translators.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openstreetmap.osmosis.osmbinary.Osmformat.HeaderBBox;

import math.geom2d.Point2D;

import com.osm2xp.constants.FsxConstants;
import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.model.fsx.FsxObject;
import com.osm2xp.model.geom.Lod13Location;
import com.osm2xp.model.osm.Node;
import com.osm2xp.model.osm.OsmPolygon;
import com.osm2xp.model.osm.Relation;
import com.osm2xp.model.osm.Way;
import com.osm2xp.model.stats.GenerationStats;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.OsmUtils;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.logging.Osm2xpLogger;
import com.osm2xp.writers.IWriter;

/**
 * Work in progress. TODO write an agn generator implementation.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class FsxTranslatorImpl implements ITranslator {

	private Point2D currentTile;
	private GenerationStats stats;
	private IWriter writer;
	private Date startTime;
	private String folderPath;

	public FsxTranslatorImpl(GenerationStats stats, IWriter writer,
			Point2D currentTile, String folderPath) {
		this.currentTile = currentTile;
		this.stats = stats;
		this.writer = writer;

		this.folderPath = folderPath;
		startTime = new Date();
	}

	public FsxTranslatorImpl() {
	}

	@Override
	public void processNode(Node node) throws Osm2xpBusinessException {

	}

	/**
	 * @param fsxObject
	 * @return
	 */
	private List<FsxObject> splitObject(FsxObject fsxObject) {
		List<FsxObject> result = new ArrayList<FsxObject>();
		if (!fsxObject.isOnASingleLod13Square()) {
			Map<String, FsxObject> objectsMap = new HashMap<String, FsxObject>();

			for (Lod13Location location : fsxObject.getLod13Locations()) {
				if (objectsMap.containsKey(location.getLod13String())) {
					objectsMap.get(location.getLod13String())
							.getLod13Locations().add(location);

				} else {
					FsxObject object = new FsxObject(fsxObject.getGuid(),
							fsxObject.getType(), new ArrayList<Node>());
					object.getLod13Locations().add(location);
					objectsMap.put(location.getLod13String(), object);
				}

			}

			List<FsxObject> tempResult = new ArrayList<FsxObject>(
					objectsMap.values());
			for (FsxObject object : tempResult) {
				if (!object
						.getLod13Locations()
						.get(0)
						.equals(object.getLod13Locations().get(
								object.getLod13Locations().size() - 1))) {
					try {
						object.getLod13Locations().add(
								object.getLod13Locations().get(0).clone());

					} catch (CloneNotSupportedException e) {
						Osm2xpLogger
								.error("Error spliting fsx object along lod13 squares.",
										e);
					}

				}
				if (object.getLod13Locations().size() > 4) {
					result.add(object);
				}
			}

		} else {
			if (fsxObject.getLod13Locations().size() > 4) {
				result.add(fsxObject);
			}
		}

		return result;
	}

	@Override
	public void processPolygon(OsmPolygon osmPolygon)
			throws Osm2xpBusinessException {

		if (OsmUtils.isForest(osmPolygon.getTags())) {
			FsxObject fsxObject = new FsxObject(
					"dc5c8dcb-551e-4980-867d-ee6d12695c50",
					FsxConstants.AGN_TYPE_FOREST, osmPolygon.getNodes());
			for (FsxObject object : splitObject(fsxObject)) {
				writer.write(object);
			}

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
		Osm2xpLogger.info("Starting FSX generation of tile "
				+ (int) currentTile.y + "/" + (int) currentTile.x);
		writer.init(null);

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
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public void processBoundingBox(HeaderBBox bbox) {
		// Do nothing
	}
}
