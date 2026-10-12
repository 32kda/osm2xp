package com.osm2xp.generation.areas;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.strtree.STRtree;

import com.osm2xp.utils.geometry.Osm2XPGeometryFactory;

public class AreaProvider {
	
	private static AreaProvider instance;
	
	/**
	 * Areas collected while parsing. The STRtree is built lazily (bulk load)
	 * from this list before the first query and invalidated whenever a new
	 * area is added.
	 */
	private final List<MapArea> areas = new ArrayList<>();
	private STRtree tree;
	
	public static synchronized AreaProvider getInstance() {
		if (instance == null) {
			instance = new AreaProvider();
		}
		return instance;
	}

	private AreaProvider() {
		
	}
	
	public void clear() {
		if (!areas.isEmpty() || tree != null) {
			areas.clear();
			tree = null;
		}
	}

	public void addArea(MapArea area) {
		areas.add(area);
		tree = null;
	}
	
	@SuppressWarnings("unchecked")
	public List<MapArea> queryContainingAreas(Coordinate point) {
		STRtree index = tree;
		if (index == null) {
			index = buildIndex();
		}
		List<MapArea> result = index.query(new Envelope(point));
		Point pt = Osm2XPGeometryFactory.getInstance().createPoint(point);
		for (Iterator<MapArea> iterator = result.iterator(); iterator.hasNext();) {
			MapArea mapArea = (MapArea) iterator.next();
			// The spatial index query already discards areas whose envelope
			// doesn't contain the point, so only the (indexed) polygon test is
			// needed here.
			if (!mapArea.prepared.contains(pt)) {
				iterator.remove();
			}
		}
		return result;
	}

	private STRtree buildIndex() {
		STRtree index = new STRtree();
		for (MapArea area : areas) {
			index.insert(area.polygon.getEnvelopeInternal(), area);
		}
		index.build();
		tree = index;
		return index;
	}
}
