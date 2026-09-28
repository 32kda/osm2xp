package com.osm2xp.translators.airfield.btg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import com.osm2xp.translators.flightgear.FlightGearBucket;
import com.osm2xp.utils.geometry.GeomUtils;

import math.geom2d.Point2D;

/**
 * Splits local-frame geometry along the FlightGear sub-bucket tile boundaries,
 * so an airfield spanning several terrain tiles is baked into each of them
 * separately. Shared by the terrain writers.
 */
public final class FlightGearTileSplitter {

	private final GeometryFactory geometryFactory;
	private final LocalGeodeticConverter converter;

	public FlightGearTileSplitter(GeometryFactory geometryFactory, Point2D datum) {
		this.geometryFactory = geometryFactory;
		this.converter = new LocalGeodeticConverter(geometryFactory, datum);
	}

	public LocalGeodeticConverter converter() {
		return converter;
	}

	/** Splits each surface polygon by tile, keeping its material. */
	public Map<FlightGearBucket, List<SurfacePolygon>> splitSurfaces(List<SurfacePolygon> surfaces) {
		Map<FlightGearBucket, List<SurfacePolygon>> result = new HashMap<>();
		for (SurfacePolygon surface : surfaces) {
			for (Map.Entry<FlightGearBucket, List<Polygon>> entry : splitPolygon(surface.getPolygon()).entrySet()) {
				for (Polygon piece : entry.getValue()) {
					result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
							.add(new SurfacePolygon(piece, surface.getMaterial()));
				}
			}
		}
		return result;
	}

	/** Splits an arbitrary local-frame geometry by tile. */
	public Map<FlightGearBucket, List<Polygon>> splitGeometry(Geometry geometry) {
		Map<FlightGearBucket, List<Polygon>> result = new HashMap<>();
		for (Polygon polygon : GeomUtils.flatMapToPoly(geometry)) {
			for (Map.Entry<FlightGearBucket, List<Polygon>> entry : splitPolygon(polygon).entrySet()) {
				result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
			}
		}
		return result;
	}

	/** All buckets overlapping the given local-frame polygon. */
	public Set<FlightGearBucket> bucketsFor(Polygon localPolygon) {
		return new HashSet<>(bucketsIn(converter.toGeodetic(localPolygon).getEnvelopeInternal()));
	}

	private Map<FlightGearBucket, List<Polygon>> splitPolygon(Polygon localPolygon) {
		Map<FlightGearBucket, List<Polygon>> result = new HashMap<>();
		Polygon geodeticPolygon = converter.toGeodetic(localPolygon);
		Envelope envelope = geodeticPolygon.getEnvelopeInternal();
		for (FlightGearBucket bucket : bucketsIn(envelope)) {
			Geometry intersection = geodeticPolygon.intersection(bucketRect(bucket));
			for (Polygon piece : GeomUtils.flatMapToPoly(intersection)) {
				Polygon localPiece = converter.toLocal(piece);
				if (localPiece != null && !localPiece.isEmpty()) {
					result.computeIfAbsent(bucket, k -> new ArrayList<>()).add(localPiece);
				}
			}
		}
		return result;
	}

	private List<FlightGearBucket> bucketsIn(Envelope envelope) {
		List<FlightGearBucket> result = new ArrayList<>();
		int lonStart = (int) Math.floor(envelope.getMinX());
		int lonEnd = (int) Math.floor(envelope.getMaxX());
		int latStart = (int) Math.floor(envelope.getMinY());
		int latEnd = (int) Math.floor(envelope.getMaxY());
		for (int lon = lonStart; lon <= lonEnd; lon++) {
			for (int lat = latStart; lat <= latEnd; lat++) {
				double span = FlightGearBucket.sgBucketSpan(lat + 0.5);
				int cols = span <= 1.0 ? (int) Math.round(1.0 / span) : 1;
				for (int x = 0; x < cols; x++) {
					for (int y = 0; y < 8; y++) {
						double centerLon = lon + x * span + span / 2.0;
						double centerLat = lat + y * FlightGearBucket.SG_BUCKET_SPAN
								+ FlightGearBucket.SG_BUCKET_SPAN / 2.0;
						FlightGearBucket bucket = FlightGearBucket.bucketFor(centerLon, centerLat);
						if (bucket.getMinLon() <= envelope.getMaxX() && bucket.getMaxLon() >= envelope.getMinX()
								&& bucket.getMinLat() <= envelope.getMaxY()
								&& bucket.getMaxLat() >= envelope.getMinY()) {
							result.add(bucket);
						}
					}
				}
			}
		}
		return result;
	}

	private Polygon bucketRect(FlightGearBucket bucket) {
		return geometryFactory.createPolygon(new Coordinate[] { new Coordinate(bucket.getMinLon(), bucket.getMinLat()),
				new Coordinate(bucket.getMaxLon(), bucket.getMinLat()),
				new Coordinate(bucket.getMaxLon(), bucket.getMaxLat()),
				new Coordinate(bucket.getMinLon(), bucket.getMaxLat()),
				new Coordinate(bucket.getMinLon(), bucket.getMinLat()) });
	}
}
