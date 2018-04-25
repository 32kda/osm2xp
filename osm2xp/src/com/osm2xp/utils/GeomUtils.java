package com.osm2xp.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import math.geom2d.Angle2D;
import math.geom2d.Box2D;
import math.geom2d.Point2D;
import math.geom2d.line.Line2D;
import math.geom2d.line.LineSegment2D;
import math.geom2d.polygon.LinearRing2D;
import math.geom2d.polygon.Rectangle2D;

import org.opencarto.algo.ShortEdgesDeletion;

import SGImplify.SGImplify;
import Tuple.Tuple2f;

import com.osm2xp.model.geom.Lod13Location;
import com.osm2xp.model.osm.Node;
import com.vividsolutions.jts.algorithm.CentroidArea;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequenceFactory;
import com.vividsolutions.jts.geom.util.Angle;
import com.vividsolutions.jts.geom.util.CGAlgorithms;

/**
 * GeomUtils.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class GeomUtils {
	public static Double getPolygonAngle2(LinearRing2D polygon) {
		Double result = null;
		LineSegment2D resultSegment = null;

		for (LineSegment2D segment : polygon.getEdges()) {

			if (resultSegment == null
					|| resultSegment.getLength() > segment.getLength())
				resultSegment = segment;

		}

		result = bearing(resultSegment.getFirstPoint().x,
				resultSegment.getFirstPoint().y,
				resultSegment.getLastPoint().x, resultSegment.getLastPoint().y);
		if (result - 7 > 0) {
			result = result - 7;
		}
		return result;
	}

	public static Double getPolygonMaxVectorAngle(LinearRing2D polygon) {
		Double result = null;
		LineSegment2D resultSegment = null;

		for (LineSegment2D segment : polygon.getEdges()) {

			if (resultSegment == null
					|| resultSegment.getLength() > segment.getLength())
				resultSegment = segment;

		}
		Coordinate c1 = new Coordinate(resultSegment.getFirstPoint().x,
				resultSegment.getFirstPoint().y);
		Coordinate c2 = new Coordinate(resultSegment.getLastPoint().x,
				resultSegment.getLastPoint().y);
		result = Math.toDegrees(Angle.angle(c1, c2));
		if (result < 0) {
			result = Math.toDegrees(Angle.angle(c2, c1));
		}
		return result;
	}

	public static Double getPolygonMinVectorAngle(LinearRing2D polygon) {
		Double result = null;
		LineSegment2D resultSegment = null;

		for (LineSegment2D segment : polygon.getEdges()) {

			if (resultSegment == null
					|| resultSegment.getLength() < segment.getLength())
				resultSegment = segment;

		}
		Coordinate c1 = new Coordinate(resultSegment.getFirstPoint().x,
				resultSegment.getFirstPoint().y);
		Coordinate c2 = new Coordinate(resultSegment.getLastPoint().x,
				resultSegment.getLastPoint().y);
		result = Math.toDegrees(Angle.angle(c1, c2));
		if (result < 0) {
			result = Math.toDegrees(Angle.angle(c2, c1));
		}
		return result;
	}

	public static double bearing(double lat1, double Lng1, double lat2,
			double Lng2) {
		double deltaLong = Math.toRadians(Lng2 - Lng1);

		double latitude1 = Math.toRadians(lat1);
		double latitude2 = Math.toRadians(lat2);

		double y = Math.sin(deltaLong) * Math.cos(lat2);
		double x = Math.cos(latitude1) * Math.sin(latitude2)
				- Math.sin(latitude1) * Math.cos(latitude2)
				* Math.cos(deltaLong);
		double result = Math.toDegrees(Math.atan2(y, x));
		return (result + 360.0) % 360.0;
	}

	/**
	 * compute distance beetween two lat/long points
	 * 
	 * @param lat1
	 * @param lon1
	 * @param lat2
	 * @param lon2
	 * @return
	 */
	private static double latLongDistanceOld(double lat1, double lon1,
			double lat2, double lon2) {
		double theta = lon1 - lon2;
		double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2))
				+ Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2))
				* Math.cos(deg2rad(theta));
		dist = Math.acos(dist);
		dist = rad2deg(dist);
		dist = dist * 60 * 1.1515;
		dist = dist * 1.609344;
		return dist * 1000;
	}

	/**
	 * Check if the object fits the polygon.
	 * 
	 * @param xMaxLength
	 *            object x max length.
	 * @param yMaxLength
	 *            object y max length.
	 * @param xMinLength
	 *            bject x min length.
	 * @param yMinLength
	 *            bject y min length.
	 * @param poly
	 *            osm polygon.
	 * @return true if the object can be used for this polygon.
	 */
	public static boolean isRectangleBigEnoughForObject(int xMaxLength,
			int yMaxLength, int xMinLength, int yMinLength, LinearRing2D poly) {
		Boolean result = false;
		if (poly.getVertices().size() == 5) {
			double segment1 = latLongDistance(poly.getVertex(0).y,
					poly.getVertex(0).x, poly.getVertex(1).y,
					poly.getVertex(1).x);
			double segment2 = latLongDistance(poly.getVertex(1).y,
					poly.getVertex(1).x, poly.getVertex(2).y,
					poly.getVertex(2).x);
			result = segment1 < xMaxLength && segment1 > xMinLength
					&& segment2 < yMaxLength && segment2 > yMinLength
					|| segment1 < yMaxLength && segment1 > yMinLength
					&& segment2 < xMaxLength && segment2 > xMinLength;

		}
		return result;
	}

	public static Polygon linearRing2DToJtsPolygon(LinearRing2D ring2d) {
		List<Coordinate> coords = new ArrayList<Coordinate>();
		for (Point2D point : ring2d.getVertices()) {
			coords.add(new Coordinate(point.x, point.y));
		}
		Coordinate[] points = (Coordinate[]) coords
				.toArray(new Coordinate[coords.size()]);
		CoordinateSequence coordSeq = CoordinateArraySequenceFactory.instance()
				.create(points);
		GeometryFactory geometryFactory = new GeometryFactory();
		LinearRing linearRing = geometryFactory.createLinearRing(coordSeq);
		Polygon jtsPolygon = geometryFactory.createPolygon(linearRing, null);
		return jtsPolygon;
	}

	public static Double latLongDistance(double lat1, double lon1, double lat2,
			double lon2) {
		double earthRadius = 3958.75;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lon2 - lon1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2)
				* Math.sin(dLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double dist = earthRadius * c;

		int meterConversion = 1609;

		return new Double(dist * meterConversion);
	}

	private static double deg2rad(double deg) {
		return (deg * Math.PI / 180.0);
	}

	private static double rad2deg(double rad) {
		return (rad * 180.0 / Math.PI);
	}

	/**
	 * compute min and max vectors for a polygon
	 * 
	 * @param polygon
	 * @return double[]
	 */
	public static Double[] computeExtremeVectors(LinearRing2D polygon) {

		Double minVector = null;
		Double maxVector = null;

		for (LineSegment2D segment : polygon.getEdges()) {
			Double distance = latLongDistance(segment.getFirstPoint().y,
					segment.getFirstPoint().x, segment.getLastPoint().y,
					segment.getLastPoint().x);
			if (minVector == null || minVector > distance)
				minVector = new Double(distance);
			if (maxVector == null || maxVector < distance)
				maxVector = new Double(distance);
		}
		return new Double[] { minVector, maxVector };
	}

	/**
	 * compute min and max vectors for a polygon
	 * 
	 * @param polygon
	 * @return double[]
	 */
	public static Double[] computeExtremeVectorsBad(LinearRing2D polygon) {

		Double minVector = null;
		Double maxVector = null;

		for (LineSegment2D segment : polygon.getEdges()) {
			LineSegment lineSegment = new LineSegment(
					segment.getFirstPoint().x, segment.getFirstPoint().y,
					segment.getLastPoint().x, segment.getLastPoint().y);
			double distance = lineSegment.getLength() * 100000;
			if (minVector == null || minVector > distance)
				minVector = new Double(distance);
			if (maxVector == null || maxVector < distance)
				maxVector = new Double(distance);
		}
		return new Double[] { minVector, maxVector };
	}

	/**
	 * Compute the smallest vector of the polygon in meters.
	 * 
	 * @param polygon
	 * @return Double
	 */
	public static Double computeMinVector(LinearRing2D polygon) {
		Double[] vectors = computeExtremeVectors(polygon);
		return vectors[0];
	}
	
	/**
	 * Compute perimeter of the polygon in meters.
	 * 
	 * @param polygon
	 * @return Double - perimeter value
	 */
	public static Double computePerimeter(LinearRing2D polygon) {
		Double[] vectors = computeExtremeVectors(polygon);
		double sum = 0;
		for (Double current : vectors) {
			sum += current;
		}
		return sum;
	}

	/**
	 * Compute the laregest vector of the polygon in meters.
	 * 
	 * @param polygon
	 * @return Double
	 */
	public static Double computeMaxVector(LinearRing2D polygon) {
		Double[] vectors = computeExtremeVectors(polygon);
		return vectors[1];
	}

	/**
	 * @param pointA
	 * @param pointB
	 * @return
	 */
	public static boolean compareCoordinates(Point2D pointA, Point2D pointB) {
		return ((int) Math.floor(pointA.x) == (int) Math.floor(pointB.x) && (int) Math
				.floor(pointA.y) == (int) Math.floor(pointB.y));
	}

	/**
	 * @param tile
	 * @param nodes
	 * @return
	 */
	public static boolean isListOfNodesOnTile(Point2D tile, List<Node> nodes) {
		for (Node node : nodes) {
			if (!compareCoordinates(tile, node)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @param linearRing2D
	 * @return
	 */
	public static LineSegment2D getLargestVector(LinearRing2D linearRing2D) {
		LineSegment2D result = null;
		for (LineSegment2D loc : linearRing2D.getEdges()) {
			if (result == null || loc.getLength() > result.getLength()) {
				result = loc;
			}
		}
		return result;
	}

	public static Point2D cleanCoordinatePoint(Point2D basePoint) {
		int longi = (int) Math.floor(basePoint.x);
		int lati = (int) Math.floor(basePoint.y);
		Point2D cleanedLoc = new Point2D(longi, lati);
		return cleanedLoc;
	}

	/**
	 * remove some vertex to simplify polygon
	 * 
	 * @param polygon
	 * @return
	 */
	public static LinearRing2D simplifyLine2D(LinearRing2D polygon) {
		try {
			List<Tuple2f> tuples = new ArrayList<Tuple2f>();
			for (Point2D pt : polygon.getVertices()) {
				Tuple2f tuple2f = new Tuple2f((float) pt.x, (float) pt.y);
				tuples.add(tuple2f);
			}

			Tuple2f[] tuplesTab = (Tuple2f[]) tuples.toArray(new Tuple2f[tuples
					.size()]);
			Tuple2f[] tuplesmod = SGImplify.simplifyLine2D(0.00003F, tuplesTab);

			List<Point2D> cleanedPoints = new ArrayList<Point2D>();
			for (int i = 0; i < tuplesmod.length; i++) {
				Point2D point2d = new Point2D(tuplesmod[i].x, tuplesmod[i].y);
				cleanedPoints.add(point2d);
			}

			LinearRing2D result = new LinearRing2D(cleanedPoints);

			// we check if the simplification hasn't moved one point to another
			// tile
			boolean isOnSingleTile = isLinearRingOnASingleTile(result);
			// we check if the result is a simple footprint
			boolean isASimpleFootprint = result.getVertexNumber() == 5;
			// we check if the result hasn't made too much simplification
			boolean isAreaChangeMinimal = linearRing2DToPolygon(result)
					.getArea() > (linearRing2DToPolygon(polygon).getArea() / 1.5);

			if (isOnSingleTile && isASimpleFootprint && isAreaChangeMinimal) {
				return result;
			} else {
				return null;
			}

		} catch (Exception e) {
			return null;
		}

	}

	/**
	 * @param linearRing2D
	 * @return
	 */
	private static Polygon linearRing2DToPolygon(LinearRing2D linearRing2D) {
		List<Coordinate> coordinates = new ArrayList<Coordinate>();
		for (Point2D pt : linearRing2D.getVertices()) {
			Coordinate coordinate = new Coordinate(pt.x, pt.y, 1);
			coordinates.add(coordinate);
		}

		Coordinate[] coordinatesTab = (Coordinate[]) coordinates
				.toArray(new Coordinate[coordinates.size()]);

		CoordinateArraySequence coordinateArraySequence = new CoordinateArraySequence(
				coordinatesTab);
		LinearRing linearRing = new LinearRing(coordinateArraySequence,
				new GeometryFactory());
		Polygon jtsPoly = new Polygon(linearRing, null, new GeometryFactory());
		return jtsPoly;
	}

	/**
	 * @param polygon
	 * @return
	 */
	private static LinearRing2D polygonToLinearRing2D(Geometry polygon) {

		List<Point2D> points = new ArrayList<Point2D>();

		for (Coordinate coordinate : polygon.getCoordinates()) {
			Point2D point2d = new Point2D(coordinate.x, coordinate.y);
			points.add(point2d);
		}

		return new LinearRing2D(points);
	}

	/**
	 * @param linearRing2D
	 * @return
	 */
	private static Boolean isLinearRingOnASingleTile(LinearRing2D linearRing2D) {
		for (Point2D point : linearRing2D.getVertices()) {
			if (!compareCoordinates(linearRing2D.getFirstPoint(), point)) {
				return false;
			}
		}
		return true;
	}

	public static boolean areParallelsSegmentsIdentics(LinearRing2D linearRing2D) {
		if (linearRing2D.getVertexNumber() == 5) {

			LineSegment2D line1 = new LineSegment2D(linearRing2D.getVertex(0),
					linearRing2D.getVertex(1));
			LineSegment2D line2 = new LineSegment2D(linearRing2D.getVertex(1),
					linearRing2D.getVertex(2));
			LineSegment2D line3 = new LineSegment2D(linearRing2D.getVertex(2),
					linearRing2D.getVertex(3));
			LineSegment2D line4 = new LineSegment2D(linearRing2D.getVertex(3),
					linearRing2D.getVertex(4));

			double length1 = line1.getLength();
			double length2 = line2.getLength();
			double length3 = line3.getLength();
			double length4 = line4.getLength();

			double diff1 = Math.abs(length1 - length3);
			double diff2 = Math.abs(length2 - length4);

			boolean sameLength1 = diff1 < (line1.getLength() / 10);
			boolean sameLength2 = diff2 < (line2.getLength() / 10);
			if (sameLength1 && sameLength2) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @param sourceFootprint
	 * @return
	 */
	public static LinearRing2D simplifyPolygon(LinearRing2D sourceFootprint) {
		LinearRing2D result = sourceFootprint;
		if (sourceFootprint.getVertexNumber() > 5) {
			result = ShortEdgesDeletion(sourceFootprint);
			if (result == null) {
				result = sourceFootprint;
			}
		}
		return result;

	}

	/**
	 * remove some vertex to simplify polygon
	 * 
	 * @param polygon
	 * @return
	 */
	public static LinearRing2D ShortEdgesDeletion(LinearRing2D sourceFootprint) {
		try {

			// we create a jts polygon from the linear ring
			Polygon sourcePoly = linearRing2DToPolygon(sourceFootprint);

			// we create a simplified polygon
			Geometry cleanPoly = ShortEdgesDeletion.get(sourcePoly, 5);

			// we create a linearRing2D from the modified polygon
			LinearRing2D result = polygonToLinearRing2D(cleanPoly);

			// we check if the simplification hasn't moved one point to another
			// tile
			boolean isOnSingleTile = isLinearRingOnASingleTile(result);
			// we check if the result is a simple footprint
			boolean isASimpleFootprint = result.getVertexNumber() == 5;
			// we check if the result hasn't made too much simplification
			boolean isAreaChangeMinimal = linearRing2DToPolygon(result)
					.getArea() > (sourcePoly.getArea() / 1.5);

			if (isOnSingleTile && isASimpleFootprint && isAreaChangeMinimal) {
				return result;
			} else {
				return null;
			}

		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * @param polygon
	 * @return
	 */
	public static Double[] getExtremesAngle(LinearRing2D polygon) {
		Double minAngle = null;
		Double maxAngle = null;
		for (int i = 0; i < polygon.getVertexNumber() - 2; i++) {
			Double angle = Math.toDegrees(Angle2D.getAbsoluteAngle(
					polygon.getVertex(i), polygon.getVertex(i + 1),
					polygon.getVertex(i + 2)));
			if (minAngle == null || angle < minAngle) {
				minAngle = angle;
			}

			if (maxAngle == null || angle > maxAngle) {
				maxAngle = angle;
			}
		}
		Double lastAngle = Math.toDegrees(Angle2D.getAbsoluteAngle(
				polygon.getVertex(polygon.getVertexNumber() - 2),
				polygon.getVertex(0), polygon.getVertex(1)));
		if (minAngle == null || lastAngle < minAngle) {
			minAngle = lastAngle;
		}
		if (maxAngle == null || lastAngle > maxAngle) {
			maxAngle = lastAngle;
		}

		return new Double[] { minAngle, maxAngle };
	}

	/**
	 * @param nodes
	 * @return
	 */
	public static LinearRing2D getPolygonFromOsmNodes(List<Node> nodes) {
		LinearRing2D result = new LinearRing2D();
		for (Node node : nodes) {
			result.addPoint(new Point2D(node.getLon(), node.getLat()));
		}
		return result;
	}
	
	public static Point2D[] getPointsFromOsmNodes(List<Node> nodes) {
		Point2D[] result = new Point2D[nodes.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = new Point2D(nodes.get(i).getLon(), nodes.get(i).getLat());
		}
		return result;
	}

	/**
	 * @param nodes
	 * @return
	 */
	public static Point2D getNodesCenter(List<Node> nodes) {
		Point2D center = null;
		if (nodes.size() > 3) {
			LinearRing2D polygon = new LinearRing2D();

			for (Node node : nodes) {
				polygon.addPoint(new Point2D(node.getLon(), node.getLat()));
			}
			center = getPolygonCenter(polygon);
		} else {
			center = new Point2D(nodes.get(0).getLon(), nodes.get(0).getLat());
		}
		return center;

	}

	/**
	 * @param tile
	 * @param node
	 * @return
	 */
	public static boolean compareCoordinates(Point2D tile, Node node) {

		return ((int) Math.floor(tile.x) == (int) Math.floor(node.getLon()) && (int) Math
				.floor(tile.y) == (int) Math.floor(node.getLat()));

	}

	/**
	 * @param linearRing2D
	 * @return
	 */
	public static LinearRing2D setClockwise(LinearRing2D linearRing2D) {
		double clockwise = 0;
		for (int i = 0; i < linearRing2D.getVertices().size() - 1; i++) {
			double a = (linearRing2D.getVertex(i + 1).x - linearRing2D
					.getVertex(i).x);
			double b = (linearRing2D.getVertex(i + 1).y + linearRing2D
					.getVertex(i).y);
			clockwise = clockwise + (a * b);
		}
		if (clockwise < 0) {
			Collection<Point2D> clockwiseVectors = linearRing2D
					.getReverseCurve().getVertices();
			linearRing2D.clearVertices();
			linearRing2D.getVertices().addAll(clockwiseVectors);
		}
		return linearRing2D;
	}

	/**
	 * check if a linear ring is clockwise.
	 * 
	 * @param ring2d
	 * @return
	 */
	public static LinearRing2D forceClockwise(LinearRing2D ring2d) {
		LinearRing2D result = null;
		if (ring2d.getVertices().size() > 4) {

			Coordinate[] coords = new Coordinate[ring2d.getVertices().size()];
			for (int i = 0; i < ring2d.getVertices().size(); i++) {
				coords[i] = new Coordinate(ring2d.getVertex(i).x,
						ring2d.getVertex(i).y);
			}

			if (CGAlgorithms.isCCW(coords)) {
				result = ring2d;
			} else {
				Collection<Point2D> clockwiseVectors = new ArrayList<Point2D>();
				for (int i = ring2d.getVertices().size() - 1; i > -1; i--) {
					clockwiseVectors.add(ring2d.getVertex(i));
				}

				result = new LinearRing2D(clockwiseVectors);
			}
		} else {
			result = ring2d;
		}

		return result;
	}

	/**
	 * @param polygon
	 * @return
	 */
	public static Point2D getPolygonCenter(LinearRing2D polygon) {
		Point2D center = null;
		if (polygon.getVertices().size() > 3) {
			CentroidArea centroidArea = new CentroidArea();
			List<Coordinate> ring = new ArrayList<Coordinate>();
			for (Point2D pt : polygon.getVertices()) {
				ring.add(new Coordinate(pt.x, pt.y));
			}
			Coordinate[] coordinates = new Coordinate[ring.size()];
			centroidArea.add(ring.toArray(coordinates));
			center = new Point2D(centroidArea.getCentroid().x,
					centroidArea.getCentroid().y);
		} else {
			center = polygon.getFirstPoint();
		}
		return center;
	}

	public static Point2D getRotationPoint(Point2D origin, Point2D ptX,
			Point2D ptY, Point2D lastPoint, int xCoord, int yCoord) {

		Point2D result = null;

		LineSegment segmentX = new LineSegment(new Coordinate(origin.x,
				origin.y), new Coordinate(ptX.x, ptX.y));
		LineSegment segmentY = new LineSegment(new Coordinate(origin.x,
				origin.y), new Coordinate(ptY.x, ptY.y));
		LineSegment segment2X = new LineSegment(new Coordinate(ptY.x, ptY.y),
				new Coordinate(lastPoint.x, lastPoint.y));
		LineSegment segment2Y = new LineSegment(new Coordinate(ptX.x, ptX.y),
				new Coordinate(lastPoint.x, lastPoint.y));

		// compute the X point wanted by the user
		float xFragment = (float) xCoord / 100;
		Coordinate xUserPoint = segmentX.pointAlong(xFragment);
		Coordinate x2UserPoint = segment2X.pointAlong(xFragment);
		// compute the Y point wanted by the user
		float yFragment = (float) yCoord / 100;
		Coordinate yUserPoint = segmentY.pointAlong(yFragment);
		Coordinate y2UserPoint = segment2Y.pointAlong(yFragment);

		Line2D xSeg = new Line2D(xUserPoint.x, xUserPoint.y, x2UserPoint.x,
				x2UserPoint.y);
		Line2D ySeg = new Line2D(yUserPoint.x, yUserPoint.y, y2UserPoint.x,
				y2UserPoint.y);
		result = xSeg.getIntersection(ySeg);

		return result;
	}

	public static double RadToDeg(double radians) {
		return radians * (180 / Math.PI);
	}

	public static double DegToRad(double degrees) {
		return degrees * (Math.PI / 180);
	}

	public static double getBearing(double lat1, double long1, double lat2,
			double long2) {
		// Convert input values to radians
		lat1 = DegToRad(lat1);
		long1 = DegToRad(long1);
		lat2 = DegToRad(lat2);
		long2 = DegToRad(long2);

		double deltaLong = long2 - long1;

		double x = Math.sin(deltaLong) * Math.cos(lat2);
		double y = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1)
				* Math.cos(lat2) * Math.cos(deltaLong);
		double bearing = Math.atan2(x, y);
		return ConvertToBearing(RadToDeg(bearing));
	}

	public static double ConvertToBearing(double deg) {
		return (deg + 360) % 360;
	}

	/**
	 * lod13 algo by Jeema
	 * 
	 * @param latitude
	 * @param longitude
	 * @return Lod13Location
	 */
	public static Lod13Location getLod13Location(double latitude,
			double longitude) {
		StringBuilder lod13String = new StringBuilder();

		double leftLong, rightLong, topLat, bottomLat, centerLat, centerLong;
		double newLeftLong, newRightLong, newTopLat, newBottomLat;
		double xOffset, yOffset;
		int i, quadNumber;

		leftLong = -180.0;
		rightLong = 300.0;
		topLat = 90.0;
		bottomLat = -270.0;

		// ----- Iterate through the 15 level of detail levels to compute the
		// LOD13 location value
		for (i = 0; i < 15; i++) {
			// ----- Get the lat/long values for the center of the current
			// lat/long bounds
			centerLong = (leftLong + rightLong) / 2;
			centerLat = (topLat + bottomLat) / 2;

			// ----- Is the target longitude to the left of the current center
			// longitude?
			if (longitude < centerLong) {
				// ----- Then the new quad is on the left
				quadNumber = 0;
				newLeftLong = leftLong;
				newRightLong = centerLong;
			} else {
				// ----- Otherwise the new quad is on the right
				quadNumber = 1;
				newLeftLong = centerLong;
				newRightLong = rightLong;
			}

			// ----- Is the target latitude above the current center latitude?
			if (latitude > centerLat) {
				// ----- Then the new quad is on the top
				newTopLat = topLat;
				newBottomLat = centerLat;
			} else {
				// ----- Otherwise the new quad is on the bottom
				quadNumber += 2;
				newTopLat = centerLat;
				newBottomLat = bottomLat;
			}

			// ----- Concactenate the quad number onto the LOD13 string
			lod13String.append(quadNumber);

			// ----- Update the left/right/top/bottom bounds for the next
			// iteration
			leftLong = newLeftLong;
			rightLong = newRightLong;
			topLat = newTopLat;
			bottomLat = newBottomLat;
		}

		// ----- Now calculate the X and Y offsets within the LOD13 square
		xOffset = (longitude - leftLong) / (rightLong - leftLong);
		yOffset = (topLat - latitude) / (topLat - bottomLat);

		// ----- Build the Lod13Location object and return it to the caller
		Lod13Location location = new Lod13Location();
		location.setLod13String(lod13String.toString());

		location.setxOffset(xOffset);
		location.setyOffset(yOffset);

		return location;
	}

	public static boolean boxContainsAnotherBox(Box2D box1, Box2D box2) {
		// poly inside another?
		Rectangle2D rect1 = new Rectangle2D(box1.getAsAWTRectangle2D());
		Rectangle2D rect2 = new Rectangle2D(box2.getAsAWTRectangle2D());
		if (box1.containsBounds(rect2)) {
			return true;
		}
		if (box2.containsBounds(rect1)) {
			return true;
		}

		// check intersections
		// for (LinearShape2D line : box1.getEdges()) {
		// for (LinearShape2D line2 : box2.getEdges()) {
		// if (line.getIntersection(line2) != null) {
		// return true;
		// }
		// }
		// }

		return false;
	}
}
