package com.osm2xp.generation.areas;

import java.util.List;

import org.locationtech.jts.geom.Polygon;

import com.osm2xp.core.model.osm.Tag;

public class MapArea {
	
	public final List<Tag> tags;
	public final Polygon polygon;
	
	public MapArea(List<Tag> tags, Polygon polygon) {
		super();
		this.tags = tags;
		this.polygon = polygon;
	}

	@Override
	public String toString() {
		return "Area:" + tags + ", bounds " + polygon.getEnvelopeInternal();
	}
	
}
