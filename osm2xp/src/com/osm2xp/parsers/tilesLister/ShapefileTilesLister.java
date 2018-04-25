package com.osm2xp.parsers.tilesLister;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import math.geom2d.Point2D;

import com.bbn.openmap.layer.shape.ESRIPoly.ESRIFloatPoly;
import com.bbn.openmap.layer.shape.ESRIPolygonRecord;
import com.bbn.openmap.layer.shape.ShapeFile;
import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.model.osm.Node;
import com.osm2xp.utils.logging.Osm2xpLogger;

/**
 * ShapefileTilesLister.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class ShapefileTilesLister implements TilesLister {
	private ShapeFile shapeFile;
	private Set<Point2D> tilesList = new HashSet<Point2D>();

	public ShapefileTilesLister(File file) {
		try {
			shapeFile = new ShapeFile(file);
		} catch (IOException e) {
			Osm2xpLogger.error("Error loading shapefile file", e);
		}
	}

	@Override
	public void process() throws Osm2xpBusinessException {
		try {
			ESRIPolygonRecord esriPolygonRecord = (ESRIPolygonRecord) shapeFile
					.getNextRecord();

			while (esriPolygonRecord != null) {
				ESRIFloatPoly poly = (ESRIFloatPoly) esriPolygonRecord.polygons[0];
				Node node = new Node();
				for (double point : poly.getRadians()) {
					if (node.getLat() == 0) {
						node.setLat(Math.toDegrees(point));
					} else if (node.getLat() != 0 && node.getLon() == 0) {
						node.setLon(Math.toDegrees(point));

						Point2D loc = new Point2D(node.getLon(), node.getLat());
						int lat = (int) Math.floor(loc.y);
						int lon = (int) Math.floor(loc.x);
						Point2D cleanedLoc = new Point2D(lon, lat);
						tilesList.add(cleanedLoc);

						node = new Node();
					}
				}

				esriPolygonRecord = (ESRIPolygonRecord) shapeFile
						.getNextRecord();
			}

		} catch (IOException e) {
			throw new Osm2xpBusinessException(e.getMessage());
		}

	}

	@Override
	public Set<Point2D> getTilesList() {
		return tilesList;
	}

}
