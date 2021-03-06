package com.osm2xp.translators.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.osm2xp.core.exceptions.DataSinkException;
import com.osm2xp.core.exceptions.Osm2xpBusinessException;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.datastore.IDataSink;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.model.osm.polygon.OsmPolylineFactory;
import com.osm2xp.translators.ISpecificTranslator;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.utils.geometry.CoordinateNodeIdPreserver;
import com.osm2xp.utils.geometry.GeomUtils;
import com.osm2xp.utils.geometry.NodeCoordinate;
import com.osm2xp.utils.geometry.Osm2XPGeometryFactory;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import math.geom2d.Box2D;
import math.geom2d.Point2D;

public class TileTranslationAdapter implements ISpecificTranslator{

	private GeometryClipper tileClipper;
	private Envelope bounds;
	private IDataSink processor;
	private ITranslator translator; 
	
	public TileTranslationAdapter(Point2D currentTile, IDataSink processor, ITranslator translator) {
		this.processor = processor;
		this.translator = translator;
		bounds = new Envelope(currentTile.x(), currentTile.x() + 1, currentTile.y(), currentTile.y() + 1);
		tileClipper = new GeometryClipper(bounds); //XXX need actual getting tile bounds instead  
	}	
	
	protected List<Geometry> preprocess(List<? extends Geometry> geometries, List<Tag> tags) {
		geometries = boundsFilter(geometries);
		if (geometries.isEmpty()) {
			return Collections.emptyList();
		}
		List<Geometry> clipped = clipToTileSize(geometries);		
		return cutHoles(clipped, tags);
	}
	
	protected List<Geometry> boundsFilter(List<? extends Geometry> geometries) {
		return geometries.stream().filter(geom -> geom.getEnvelopeInternal().intersects(bounds)).collect(Collectors.toList());
	}

	protected List<Geometry> clipToTileSize(List<? extends Geometry> geometries) {
		List<Geometry> resGeomList = new ArrayList<>();
		for (Geometry geometry : geometries) {
			Geometry clipResult = tileClipper.clip(geometry, true);
			resGeomList.addAll(GeomUtils.flatMap(clipResult));
		}
		return resGeomList;
	}
	

	protected Geometry getGeometry(List<Long> nodeIds) {
		if (isClosed(nodeIds)) {
			return getPolygon(nodeIds);
		}
		Coordinate[] points = getCoords(nodeIds);
		if (points != null && points.length >= 2) {
			GeometryFactory factory = Osm2XPGeometryFactory.getInstance();
			return factory.createLineString(points);
		}
		return null;	
	}

	protected Polygon getPolygon(List<Long> polyNodeIds) {
		Coordinate[] points = getCoords(polyNodeIds);
		if (points != null && points.length >= 4) {
			GeometryFactory factory = Osm2XPGeometryFactory.getInstance();
			return factory.createPolygon(points);
		}
		return null;
	}

	protected LinearRing getRing(List<Long> nodeIds) {
		Coordinate[] points = getCoords(nodeIds);
		if (points != null && points.length >= 4) {
			GeometryFactory factory = Osm2XPGeometryFactory.getInstance();
			return factory.createLinearRing(points);
		}
		return null;
	}

	protected Coordinate[] getCoords(List<Long> nodeIds) {
		List<com.osm2xp.core.model.osm.Node> nodes = getNodes(nodeIds);
		NodeCoordinate[] points = nodes.stream().map(node -> new NodeCoordinate(node.getLon(), node.getLat(), node.getId()))
				.toArray(NodeCoordinate[]::new);
		if (points.length < 1) {
			return null;
		}
		return points;
	}
	
	protected boolean isClosed(List<Long> curList) {
		if (curList.size() > 1) {
			return curList.get(0).equals(curList.get(curList.size() - 1));
		}
		return false;
	}
	
	protected List<com.osm2xp.core.model.osm.Node> getNodes(List<Long> polyIds) {
		try {
			return processor.getNodes(polyIds);
		} catch (DataSinkException e) {
			Osm2xpLogger.error(e);
		}
		return null;
	}
	
	protected List<Geometry> cutHoles(List<Geometry> initialGeomtry, List<Tag> tags) {
		int maxHoleCount = translator.getMaxHoleCount(tags);
		if (maxHoleCount == Integer.MAX_VALUE) {
			return initialGeomtry;
		}
		List<Geometry>resultList = new ArrayList<>();
		for (Geometry geometry : initialGeomtry) {
			resultList.addAll(GeomUtils.cutHoles(geometry, maxHoleCount));
		}
		return resultList;
	}

	
	@Override
	public void complete() {
		translator.complete();
	}

	@Override
	public void init() {
		translator.init();
	}

	@Override
	public boolean mustStoreNode(Node node) {
		return translator.mustStoreNode(node);
	}

	@Override
	public boolean mustProcessPolyline(List<Tag> tags) {
		return translator.mustProcessPolyline(tags);
	}

	@Override
	public void processBoundingBox(Box2D bbox) {
		translator.processBoundingBox(bbox);
	}

	@Override
	public void processNode(Node node) throws Osm2xpBusinessException {
		if (bounds.contains(node.getLon(), node.getLat())) {
			translator.processNode(node);
		}
	}

	@Override
	public void processWays(long wayId, List<Tag> tags, Geometry originalGeometry, List<? extends Geometry> fixedGeometries) {
		fixedGeometries = preprocess(fixedGeometries, tags);
		if (fixedGeometries.isEmpty()) {
			return;
		} else if (fixedGeometries.size() == 1 && fixedGeometries.get(0) == originalGeometry) {
            
			List<OsmPolyline> polyline = OsmPolylineFactory.createPolylinesFromJTSGeometry(wayId, tags, originalGeometry, false);                                                           
			try {
				translator.processPolyline(polyline.get(0));
			} catch (Osm2xpBusinessException e) {
				Osm2xpLogger.error(e);
			}                                                         
		} else {
			boolean parts = fixedGeometries.size() > 1; //Should resulting polys/lines be marked as parts of one common line?
			fixedGeometries = CoordinateNodeIdPreserver.preserveNodeIds(Collections.singletonList(originalGeometry), fixedGeometries);
			fixedGeometries.stream()
			.map(poly -> OsmPolylineFactory.createPolylinesFromJTSGeometry(wayId, tags,poly, parts))
			.filter(list -> list != null).flatMap(list -> list.stream()).forEach(polyline -> {
				try {
					translator.processPolyline(polyline);
				} catch (Osm2xpBusinessException e) {
					Osm2xpLogger.error(e);
				}
			});
		}
		
	}


}
