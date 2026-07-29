package com.osm2xp.stats;

import java.util.HashMap;
import java.util.Map;

import math.geom2d.Point2D;

public class InstanceStatsCollector implements StatisticsCollector {

	private Map<String, Integer> globalCounts = new HashMap<>();
	private Map<Point2D, CountStats> tileStats = new HashMap<>();

	@Override
	public void incCount(String id) {
		globalCounts.merge(id, 1, Integer::sum);
	}

	@Override
	public void incCount(Point2D tile, String id) {
		CountStats stats = tileStats.computeIfAbsent(tile, k -> new CountStats());
		stats.incCount(id);
	}

	@Override
	public int getCount(String id) {
		return globalCounts.getOrDefault(id, 0);
	}

	@Override
	public String getSummary(Point2D tile) {
		CountStats stats = tileStats.get(tile);
		return stats != null ? stats.getSummary() : "";
	}

	public CountStats getTileStats(Point2D tile, boolean createIfAbsent) {
		if (createIfAbsent) {
			return tileStats.computeIfAbsent(tile, k -> new CountStats());
		}
		return tileStats.get(tile);
	}

	public void reinit() {
		globalCounts.clear();
		tileStats.clear();
	}
}
