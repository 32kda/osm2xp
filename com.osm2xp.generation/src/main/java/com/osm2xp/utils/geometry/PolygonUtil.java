package com.osm2xp.utils.geometry;

import math.geom2d.Point2D;
import math.geom2d.line.Line2D;

public class PolygonUtil {
	
	public static Line2D getMedianLine(Point2D[] points) {
		Point2D centerpoint = Point2D.centroid(points);
		Point2D[] newPoints = new Point2D[points.length];
		double factor = Math.cos(Math.toRadians(centerpoint.y()));
		for (int i = 0; i < newPoints.length; i++) {
			double x = (points[i].x() - centerpoint.x()) * factor;
			double y = points[i].y() - centerpoint.y();
			newPoints[i] = new Point2D(x, y);
		}
		Line2D line2d = getMedianLineLocal(newPoints);
		Point2D p1 = new Point2D(line2d.p1.x() / factor + centerpoint.x(), line2d.p1.y() + centerpoint.y());
		Point2D p2 = new Point2D(line2d.p2.x() / factor + centerpoint.x(), line2d.p2.y() + centerpoint.y());
		return new Line2D(p1,p2);
	}
	
	public static Line2D getMedianLineLocal(Point2D[] points) {
		double minX = Double.MAX_VALUE;
		double maxX = Double.MIN_VALUE;
		double minY = Double.MAX_VALUE;
		double maxY = Double.MIN_VALUE;
		double maxSqDist = 0;
		Point2D centroid = Point2D.centroid(points);
		
		Point2D[] normalized = new Point2D[points.length - 1];
		for (int i = 1; i < points.length; i++) {
			Point2D p1 = points[i - 1];
			Point2D p2 = points[i];
			double cx = p2.x() - centroid.x();
			double cy = p2.y() - centroid.y();
			double sqDist = cx * cx + cy * cy;
			if (sqDist > maxSqDist) {
				maxSqDist = sqDist;
			}
			
			Point2D norm;
			if (p1.x() < p2.x()) {
				double dx = p2.x() - p1.x();
				double dy = p2.y() - p1.y();
				norm = normalized(dx, dy);
			} else {
				double dx = p1.x() - p2.x();
				double dy = p1.y() - p2.y();
				norm = normalized(dx, dy);
			}
			normalized[i-1] = norm;
			if (norm.x() > maxX) {
				maxX = norm.x();
			}
			if (norm.x() < minX) {
				minX = norm.x();
			}
			if (norm.y() > maxY) {
				maxY = norm.y();
			}
			if (norm.y() < minY) {
				minY = norm.y();
			}
		}
		//Get two most likely directions
		double midX = 0;
		double midY = 0;
		boolean sepByX = maxX - minX > maxY - minY; 
		if (sepByX) {
			midX = maxX / 2 + minX / 2;
		} else {
			midY = maxY / 2 + minY / 2;
		}
		double x1Sum=0, y1Sum=0, x2Sum=0, y2Sum=0;
		int vec1Count = 0;
		for (int i = 0; i < normalized.length; i++) {
			if (sepByX && normalized[i].x() < midX || 
				!sepByX && normalized[i].y() < midY) {
				vec1Count++;
				x1Sum += normalized[i].x();
				y1Sum += normalized[i].y();
			} else {
				x2Sum += normalized[i].x();
				y2Sum += normalized[i].y();
			}
		}
		
		Point2D vec1 = normalized(x1Sum / vec1Count, y1Sum / vec1Count);
		Point2D vec2 = normalized(x2Sum / (normalized.length - vec1Count), y2Sum / (normalized.length - vec1Count));
		
		double maxProjection11 = 0;
		double maxProjection12 = 0;
		double maxProjection21 = 0;
		double maxProjection22 = 0;
		for (int i = 1; i < points.length; i++) {
			Point2D curPoint = points[i];
			double cx = curPoint.x() - centroid.x();
			double cy = curPoint.y() - centroid.y();
			double projection = cx * vec1.x() + cy * vec1.y();
			if (projection > maxProjection11) {
				maxProjection11 = projection;
			}
			if (-projection > maxProjection12) {
				maxProjection12 = -projection;
			}
			projection = cx * vec2.x() + cy * vec2.y();
			if (projection > maxProjection21) {
				maxProjection21 = projection;
			}
			if (-projection > maxProjection22) {
				maxProjection22 = -projection;
			}
		}
		Point2D p1, p2;
		if (maxProjection11 + maxProjection12 > maxProjection21 + maxProjection22) {
			p1 = new Point2D(centroid.x() + vec1.x() * maxProjection11, centroid.y() + vec1.y() * maxProjection11);
			p2 = new Point2D(centroid.x() + vec1.x() * -maxProjection12, centroid.y() + vec1.y() * -maxProjection12);
		} else {
			p1 = new Point2D(centroid.x() + vec2.x() * maxProjection21, centroid.y() + vec2.y() * maxProjection21);
			p2 = new Point2D(centroid.x() + vec2.x() * -maxProjection22, centroid.y() + vec2.y() * -maxProjection22);
		}
		return new Line2D(p1, p2);		
	}
	
	private static Point2D normalized(double x, double y) {
		double hyp = Math.hypot(x,y);
		return new Point2D(x / hyp, y / hyp);
	}
	
}
