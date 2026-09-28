package com.osm2xp.translators.airfield.btg;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import com.osm2xp.translators.flightgear.FlightGearCoordinateUtils;

import math.geom2d.Point2D;

/**
 * Converts JTS polygons between the local flat-earth frame (east/north metres
 * relative to the airfield datum) and geodetic lon/lat. Shared by the terrain
 * writers.
 */
public final class LocalGeodeticConverter {

	private final GeometryFactory geometryFactory;
	private final double anchorLon;
	private final double anchorLat;

	public LocalGeodeticConverter(GeometryFactory geometryFactory, Point2D datum) {
		this.geometryFactory = geometryFactory;
		this.anchorLon = datum.x();
		this.anchorLat = datum.y();
	}

	/** Local (east/north) polygon to geodetic (lon/lat) polygon. */
	public Polygon toGeodetic(Polygon local) {
		LinearRing shell = toGeodeticRing(local.getExteriorRing());
		LinearRing[] holes = new LinearRing[local.getNumInteriorRing()];
		for (int i = 0; i < holes.length; i++) {
			holes[i] = toGeodeticRing(local.getInteriorRingN(i));
		}
		return geometryFactory.createPolygon(shell, holes);
	}

	/** Geodetic (lon/lat) polygon to local (east/north) polygon. */
	public Polygon toLocal(Polygon geodetic) {
		LinearRing shell = toLocalRing(geodetic.getExteriorRing());
		LinearRing[] holes = new LinearRing[geodetic.getNumInteriorRing()];
		for (int i = 0; i < holes.length; i++) {
			holes[i] = toLocalRing(geodetic.getInteriorRingN(i));
		}
		return geometryFactory.createPolygon(shell, holes);
	}

	/** Local east/north to {lon, lat}. */
	public double[] toGeodetic(double east, double north) {
		return FlightGearCoordinateUtils.localToGeodetic(east, north, anchorLon, anchorLat);
	}

	private LinearRing toGeodeticRing(LineString ring) {
		Coordinate[] coordinates = ring.getCoordinates();
		Coordinate[] geodetic = new Coordinate[coordinates.length];
		for (int i = 0; i < coordinates.length; i++) {
			double[] lonLat = toGeodetic(coordinates[i].x, coordinates[i].y);
			geodetic[i] = new Coordinate(lonLat[0], lonLat[1]);
		}
		return geometryFactory.createLinearRing(geodetic);
	}

	private LinearRing toLocalRing(LineString ring) {
		Coordinate[] coordinates = ring.getCoordinates();
		Coordinate[] local = new Coordinate[coordinates.length];
		for (int i = 0; i < coordinates.length; i++) {
			double[] eastNorth = FlightGearCoordinateUtils.toLocal(coordinates[i].x, coordinates[i].y, anchorLon,
					anchorLat);
			local[i] = new Coordinate(eastNorth[0], eastNorth[1]);
		}
		return geometryFactory.createLinearRing(local);
	}
}
