package com.osm2xp.stats;

import math.geom2d.Point2D;

public interface StatisticsCollector {

	void incCount(String id);

	void incCount(Point2D tile, String id);

	int getCount(String id);

	String getSummary(Point2D tile);
}
