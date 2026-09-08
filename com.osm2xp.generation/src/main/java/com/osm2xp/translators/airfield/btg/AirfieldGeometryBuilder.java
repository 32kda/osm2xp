package com.osm2xp.translators.airfield.btg;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.translators.airfield.AirfieldData;
import com.osm2xp.translators.airfield.HelipadData;
import com.osm2xp.translators.airfield.RunwayData;
import com.osm2xp.translators.airfield.TaxiLane;
import com.osm2xp.translators.flightgear.FlightGearCoordinateUtils;
import com.osm2xp.utils.geometry.GeomUtils;

import math.geom2d.Point2D;
import math.geom2d.line.Line2D;
import math.geom2d.polygon.LinearCurve2D;
import math.geom2d.polygon.LinearRing2D;

/**
 * Builds the airport surface polygons (runways, helipads, taxiways, aprons and
 * the grass clearing skirt) from an {@link AirfieldData}.
 * <p>
 * All polygons are produced in flat-earth metres relative to the airport datum
 * ({@code east/north}, see {@link FlightGearCoordinateUtils#toLocal}), which is
 * the same frame used by the BTG assembler for ECEF conversion. The pavement
 * polygons are subtracted from the skirt so that pavement and grass never
 * overlap (no z-fighting, no holes).
 */
public class AirfieldGeometryBuilder {

	/** Distance (metres) the grass skirt extends beyond the pavement edges. */
	private static final double SKIRT_MARGIN_METERS = 60.0;

	private final GeometryFactory geometryFactory = new GeometryFactory();
	private double anchorLon;
	private double anchorLat;

	/**
	 * Builds the airport surfaces for the given airfield.
	 *
	 * @return list of non-overlapping surface polygons (grass skirt + pavements),
	 *         possibly empty if the airfield has no usable geometry
	 */
	public List<SurfacePolygon> build(AirfieldData airfield) {
		Point2D datum = airfield.getDatum();
		this.anchorLon = datum.x();
		this.anchorLat = datum.y();

		List<SurfacePolygon> surfaces = new ArrayList<>();
		List<Geometry> pavements = new ArrayList<>();

		for (RunwayData runway : airfield.getUniqueRunways()) {
			Polygon polygon = buildRunwayPolygon(runway);
			if (polygon != null) {
				pavements.add(polygon);
				surfaces.add(new SurfacePolygon(polygon, FlightGearAirfieldMaterials.runwaySurface(runway)));
			}
		}

		for (HelipadData helipad : airfield.getHelipads()) {
			Polygon polygon = buildHelipadPolygon(helipad);
			if (polygon != null) {
				pavements.add(polygon);
				surfaces.add(new SurfacePolygon(polygon, FlightGearAirfieldMaterials.helipadSurface(airfield.isHard())));
			}
		}

		for (OsmPolygon apron : airfield.getApronAreas()) {
			for (Polygon polygon : buildApronPolygons(apron)) {
				pavements.add(polygon);
				surfaces.add(new SurfacePolygon(polygon, FlightGearAirfieldMaterials.apronSurface(airfield.isHard())));
			}
		}

		double taxiwayWidth = airfield.isHard()
				? XPlaneOptionsProvider.getOptions().getAirfieldOptions().getDefaultHardTaxiwayWidth()
				: XPlaneOptionsProvider.getOptions().getAirfieldOptions().getDefaultGrassTaxiwayWidth();
		for (TaxiLane lane : airfield.getTaxiLanes()) {
			double width = lane.getWidth() > 0 ? lane.getWidth() : taxiwayWidth;
			for (Polygon polygon : toPolygons(bufferPolyline(lane.getLine(), width / 2.0))) {
				pavements.add(polygon);
				surfaces.add(new SurfacePolygon(polygon, FlightGearAirfieldMaterials.taxiwaySurface(airfield.isHard())));
			}
		}

		if (!pavements.isEmpty()) {
			Geometry pavementUnion = union(pavements);
			Geometry skirt = pavementUnion.buffer(SKIRT_MARGIN_METERS);
			Geometry grass = skirt.difference(pavementUnion);
			for (Polygon polygon : toPolygons(grass)) {
				surfaces.add(new SurfacePolygon(polygon, FlightGearAirfieldMaterials.SKIRT));
			}
		}

		return surfaces;
	}

	private Polygon buildRunwayPolygon(RunwayData runway) {
		Line2D line = runway.getRunwayLine();
		double[] p1 = toLocal(line.p1.x(), line.p1.y());
		double[] p2 = toLocal(line.p2.x(), line.p2.y());
		double dx = p2[0] - p1[0];
		double dy = p2[1] - p1[1];
		double length = Math.hypot(dx, dy);
		if (length < 1e-9) {
			return null;
		}
		double nx = -dy / length;
		double ny = dx / length;
		double halfWidth = runway.getWidth() / 2.0;
		return geometryFactory.createPolygon(new Coordinate[] {
				new Coordinate(p1[0] + nx * halfWidth, p1[1] + ny * halfWidth),
				new Coordinate(p2[0] + nx * halfWidth, p2[1] + ny * halfWidth),
				new Coordinate(p2[0] - nx * halfWidth, p2[1] - ny * halfWidth),
				new Coordinate(p1[0] - nx * halfWidth, p1[1] - ny * halfWidth),
				new Coordinate(p1[0] + nx * halfWidth, p1[1] + ny * halfWidth) });
	}

	private Polygon buildHelipadPolygon(HelipadData helipad) {
		double[] center = toLocal(helipad.getLon(), helipad.getLat());
		double heading = helipad.getHeading() >= 0 ? helipad.getHeading() : 0.0;
		double angle = Math.toRadians(heading);
		// Heading is clockwise from north: east component = sin, north = cos.
		double ex = Math.sin(angle);
		double ey = Math.cos(angle);
		double px = -ey;
		double py = ex;
		double halfLength = helipad.getLength() / 2.0;
		double halfWidth = helipad.getWidth() / 2.0;
		return geometryFactory.createPolygon(new Coordinate[] {
				new Coordinate(center[0] - ex * halfLength - px * halfWidth, center[1] - ey * halfLength - py * halfWidth),
				new Coordinate(center[0] + ex * halfLength - px * halfWidth, center[1] + ey * halfLength - py * halfWidth),
				new Coordinate(center[0] + ex * halfLength + px * halfWidth, center[1] + ey * halfLength + py * halfWidth),
				new Coordinate(center[0] - ex * halfLength + px * halfWidth, center[1] - ey * halfLength + py * halfWidth),
				new Coordinate(center[0] - ex * halfLength - px * halfWidth, center[1] - ey * halfLength - py * halfWidth) });
	}

	private List<Polygon> buildApronPolygons(OsmPolygon apron) {
		LinearRing2D ring = apron.getPolygon();
		if (ring == null || ring.vertexNumber() < 3) {
			return new ArrayList<>();
		}
		Coordinate[] coordinates = toClosedCoordinates(ring.vertexArray());
		if (coordinates == null) {
			return new ArrayList<>();
		}
		Geometry repaired = GeomUtils.fix(geometryFactory.createPolygon(coordinates));
		return toPolygons(repaired);
	}

	private Geometry bufferPolyline(LinearCurve2D curve, double distance) {
		Point2D[] points = curve.vertexArray();
		if (points.length < 2) {
			return null;
		}
		Coordinate[] coordinates = new Coordinate[points.length];
		for (int i = 0; i < points.length; i++) {
			double[] local = toLocal(points[i].x(), points[i].y());
			coordinates[i] = new Coordinate(local[0], local[1]);
		}
		LineString line = geometryFactory.createLineString(coordinates);
		return line.buffer(distance);
	}

	private Geometry union(List<Geometry> geometries) {
		if (geometries.size() == 1) {
			return GeomUtils.fix(geometries.get(0));
		}
		CascadedPolygonUnion op = new CascadedPolygonUnion(geometries);
		return GeomUtils.fix(op.union());
	}

	private List<Polygon> toPolygons(Geometry geometry) {
		if (geometry == null || geometry.isEmpty()) {
			return new ArrayList<>();
		}
		return GeomUtils.flatMapToPoly(geometry);
	}

	private double[] toLocal(double lon, double lat) {
		return FlightGearCoordinateUtils.toLocal(lon, lat, anchorLon, anchorLat);
	}

	private Coordinate[] toClosedCoordinates(Point2D[] points) {
		if (points == null || points.length < 3) {
			return null;
		}
		boolean closed = points[0].equals(points[points.length - 1]);
		int length = closed ? points.length : points.length + 1;
		Coordinate[] coordinates = new Coordinate[length];
		for (int i = 0; i < points.length; i++) {
			double[] local = toLocal(points[i].x(), points[i].y());
			coordinates[i] = new Coordinate(local[0], local[1]);
		}
		if (!closed) {
			coordinates[points.length] = coordinates[0];
		}
		return coordinates;
	}
}
