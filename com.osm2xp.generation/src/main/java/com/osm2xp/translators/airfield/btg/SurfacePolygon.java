package com.osm2xp.translators.airfield.btg;

import org.locationtech.jts.geom.Polygon;

/**
 * A single airport surface (runway, taxiway, apron, helipad or the grass
 * skirt). The polygon is expressed in flat-earth metres relative to the
 * airport datum (east/north), ready for triangulation and conversion to ECEF.
 */
public final class SurfacePolygon {

	private final Polygon polygon;
	private final String material;

	public SurfacePolygon(Polygon polygon, String material) {
		this.polygon = polygon;
		this.material = material;
	}

	public Polygon getPolygon() {
		return polygon;
	}

	public String getMaterial() {
		return material;
	}
}
