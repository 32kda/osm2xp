package com.osm2xp.generation.areas;

import java.util.List;

import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import com.osm2xp.core.model.osm.Tag;

public class MapArea {
	
	public final List<Tag> tags;
	public final Polygon polygon;
	/**
	 * Spatial index over the polygon edges, built once so that repeated
	 * point-in-polygon tests don't rebuild it on every query.
	 */
	public final PreparedGeometry prepared;
	
	public MapArea(List<Tag> tags, Polygon polygon) {
		super();
		this.tags = tags;
		this.polygon = polygon;
		this.prepared = PreparedGeometryFactory.prepare(polygon);
	}

	@Override
	public String toString() {
		return "Area:" + tags + ", bounds " + polygon.getEnvelopeInternal();
	}
	
}
