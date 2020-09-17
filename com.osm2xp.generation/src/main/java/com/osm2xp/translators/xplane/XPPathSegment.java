package com.osm2xp.translators.xplane;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import math.geom2d.Point2D;

public class XPPathSegment {
	
	private int type;
	private long startId;
	private long endId;
	private Point2D[] points;
	private double startHeight = 0;
	private double endHeight = 0;
	
	private String comment;
	private boolean bridge;

	public XPPathSegment(int type, long startId, long endId, Point2D[] points) {
		if (points.length < 2) {
			throw new IllegalArgumentException("Need at least 2 points to generate road segment");
		}
		this.type = type;
		this.startId = startId;
		this.endId = endId;
		this.points = points;
	}
	
	@Override
	public String toString() {
		return "Path segment, type " + type + " start " + startId + " end " + endId + ", " + points.length + " points";
	}
	
	/**
	 * Debug method
	 * @return JTS LINESTRING for this  Path Segment
	 */ 
	public String getJTSString() {
		String coordsStr = Arrays.stream(points).map(point -> String.format(Locale.ROOT, "%.9f %.9f", point.x(), point.y())).collect(Collectors.joining(", "));		
		return "LINESTRING (" + coordsStr + ")";
	}

	public Point2D getPoint(int i) {
		if (points.length <= i) {
			throw new IndexOutOfBoundsException("Point index out of bounds " + i);
		}
		return points[i];
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public double getStartHeight() {
		return startHeight;
	}

	public void setStartHeight(double startHeight) {
		this.startHeight = startHeight;
	}

	public double getEndHeight() {
		return endHeight;
	}

	public void setEndHeight(double endHeight) {
		this.endHeight = endHeight;
	}

	public void setBridge(boolean bridge) {
		this.bridge = bridge;
//		if (bridge) {
//			setStartHeight(1);
//			setEndHeight(1);
//		}
	}

	public boolean isBridge() {
		return bridge;
	}

	public long getStartId() {
		return startId;
	}

	public void setStartId(long startId) {
		this.startId = startId;
	}

	public long getEndId() {
		return endId;
	}

	public void setEndId(long endId) {
		this.endId = endId;
	}

	public Point2D[] getPoints() {
		return points;
	}

	public int getType() {
		return type;
	}

}
