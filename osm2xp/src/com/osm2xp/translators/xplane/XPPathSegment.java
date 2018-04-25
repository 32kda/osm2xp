package com.osm2xp.translators.xplane;

import math.geom2d.Point2D;
import static com.osm2xp.translators.impl.XPlaneTranslatorImpl.LINE_SEP;

public class XPPathSegment {
	
	private int type;
	private long startId;
	private long endId;
	private Point2D[] points;
	
	private String comment;

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
		StringBuilder builder = new StringBuilder();
		if (getComment() != null) {
			builder.append("#");
			builder.append(getComment());
			builder.append(LINE_SEP);
		}
		builder.append(String.format("BEGIN_SEGMENT 0 %d %d %3.9f %4.9f 0.000000000", type, startId, points[0].x, points[0].y));
		builder.append(LINE_SEP);
		for (int i = 1; i < points.length - 1; i++) {
			builder.append(String.format("SHAPE_POINT %1.9f %2.9f 0.000000000",points[i].x, points[i].y));
			builder.append(LINE_SEP);
		}
		builder.append(String.format("END_SEGMENT %d %2.9f %3.9f 0.000000000", endId, points[points.length - 1].x, points[points.length - 1].y));
		builder.append(LINE_SEP);
		return builder.toString();
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

}
