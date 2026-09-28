package com.osm2xp.translators.airfield.btg;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import com.osm2xp.translators.flightgear.FlightGearTerrainElevationProber;

/**
 * Probes the terrain elevation over the cut footprint, returning the minimum and
 * maximum heights (the flat plate is placed at the maximum).
 */
public final class AirfieldElevationProbe {

	private static final int PROBE_GRID = 8;

	private final GeometryFactory geometryFactory = new GeometryFactory();
	private final LocalGeodeticConverter converter;

	public AirfieldElevationProbe(LocalGeodeticConverter converter) {
		this.converter = converter;
	}

	/**
	 * @return {@code {min, max}} elevation over the footprint, or
	 *         {@code {NaN, NaN}} if nothing could be probed.
	 */
	public double[] probe(FlightGearTerrainElevationProber prober, Polygon cutPolygon) {
		double min = Double.NaN;
		double max = Double.NaN;
		List<double[]> lonLats = new ArrayList<>();
		for (Coordinate coordinate : cutPolygon.getExteriorRing().getCoordinates()) {
			lonLats.add(converter.toGeodetic(coordinate.x, coordinate.y));
		}
		Envelope envelope = cutPolygon.getEnvelopeInternal();
		for (int i = 1; i < PROBE_GRID; i++) {
			for (int j = 1; j < PROBE_GRID; j++) {
				double east = envelope.getMinX() + (envelope.getMaxX() - envelope.getMinX()) * i / PROBE_GRID;
				double north = envelope.getMinY() + (envelope.getMaxY() - envelope.getMinY()) * j / PROBE_GRID;
				Point point = geometryFactory.createPoint(new Coordinate(east, north));
				if (cutPolygon.contains(point)) {
					lonLats.add(converter.toGeodetic(east, north));
				}
			}
		}
		for (double[] lonLat : lonLats) {
			double altitude = probe(prober, lonLat[0], lonLat[1]);
			if (Double.isNaN(altitude)) {
				continue;
			}
			min = Double.isNaN(min) ? altitude : Math.min(min, altitude);
			max = Double.isNaN(max) ? altitude : Math.max(max, altitude);
		}
		return new double[] { min, max };
	}

	/** Terrain height at lon/lat, or {@code NaN} when unavailable. */
	public double probe(FlightGearTerrainElevationProber prober, double lon, double lat) {
		return prober.probeElevation(lon, lat);
	}

	/** Terrain height at a local east/north point, or {@code NaN} when unavailable. */
	public double probeLocal(FlightGearTerrainElevationProber prober, double east, double north) {
		double[] lonLat = converter.toGeodetic(east, north);
		return probe(prober, lonLat[0], lonLat[1]);
	}
}
