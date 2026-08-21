package com.osm2xp.translators.flightgear;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.generation.options.ObjectFile;
import com.osm2xp.generation.options.rules.FlightGearObjectTagRule;
import com.osm2xp.generation.options.rules.RulesUtil;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.stats.CountStats;
import com.osm2xp.stats.StatsProvider;
import com.osm2xp.translators.FlightGearStgWriterProvider;
import com.osm2xp.translators.IPolyHandler;
import com.osm2xp.utils.MiscUtils;
import com.osm2xp.utils.geometry.GeomUtils;
import com.osm2xp.utils.osm.OsmUtils;

import math.geom2d.Point2D;
import math.geom2d.line.LineSegment2D;
import math.geom2d.polygon.LinearRing2D;

/**
 * FlightGear counterpart of the X-Plane {@code XP3DObjectByRuleTranslator}. Selects the right
 * bundled FlightGear {@code .ac} model for a polygon by matching the first applicable
 * {@link com.osm2xp.generation.options.rules.FlightGearObjectTagRule} (tag, area, size, shape,
 * height-aware object files) and writes an {@code OBJECT_SHARED_AGL Models/...} line into the
 * sub-bucket STG.
 *
 * @author osm2xp
 */
public class FGRulesObjectTranslator implements IPolyHandler {

	private static final String OBJ_EXT = ".ac";
	private static final String STG_PATTERN = "OBJECT_SHARED_AGL %s %.6f %.6f 0 %1.2f 0 0\n";

	private FlightGearStgWriterProvider stgWriterProvider;
	private final Random random = new Random();

	@Override
	public void setStgWriterProvider(FlightGearStgWriterProvider stgWriterProvider) {
		this.stgWriterProvider = stgWriterProvider;
	}

	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		FlightGearOptions options = FlightGearOptionsProvider.getOptions();
		if (!options.isGenerateObjects()) {
			return false;
		}
		if (!(osmPolyline instanceof OsmPolygon) || osmPolyline.isPart()
				|| !((OsmPolygon) osmPolyline).getPolygon().isClosed()) {
			return false;
		}
		return select3DObject((OsmPolygon) osmPolyline);		
	}

	/**
	 * Select a 3D object (pair of polygon + chosen model path) for the given polygon. Returns the
	 * polygon with the chosen model {@code path} stored in a side result via
	 * {@link #select3DObject}, or {@code null} if no rule matches.
	 */
	private boolean select3DObject(OsmPolygon osmPolygon) {
		FlightGearObjectTagRule matchingRule = selectMatchingRule(osmPolygon);
		if (matchingRule == null) {
			return false;
		}
		Point2D origin = GeomUtils.getPolylineCenter(osmPolygon.getPolygon());
		double angle = matchingRule.getAngle();
		if (matchingRule.isUsePolygonAngle()) {
			angle = calculateAngle(osmPolygon.getPolygon(), matchingRule);
		} else if (matchingRule.isRandomAngle()) {
			angle = Double.valueOf(MiscUtils.getRandomInt(0, 360));
		}
		ObjectFile chosen = getObjectFromRule(matchingRule, osmPolygon);
		if (chosen == null) {
			return false;
		}
		writeObject(chosen.getPath(), origin, angle);
		return true;
	}

	protected FlightGearObjectTagRule selectMatchingRule(OsmPolygon osmPolygon) {
		LinearRing2D polygon = osmPolygon.getPolygon();
		List<FlightGearObjectTagRule> rules = FlightGearOptionsProvider.getOptions()
				.getObjectsRules().getRules();
		List<FlightGearObjectTagRule> matchingRules = new ArrayList<>();
		int height = osmPolygon.getHeight();
		for (FlightGearObjectTagRule rule : rules) {
			if (rule == null || !RulesUtil.areaTypeMatches(rule, osmPolygon.getTags())) {
				continue;
			}
			for (Tag tag : osmPolygon.getTags()) {
				if (rule.getTag().getKey().equalsIgnoreCase("id")
						&& rule.getTag().getValue().equalsIgnoreCase(String.valueOf(osmPolygon.getId()))) {
					return rule;
				} else if (OsmUtils.compareTags(rule.getTag(), tag)) {
					Boolean areaOK = !rule.isAreaCheck() || (rule.isAreaCheck()
							&& (osmPolygon.getArea() > rule.getMinArea()
									&& osmPolygon.getArea() < rule.getMaxArea()));
					Boolean sizeOK = !rule.isSizeCheck() || GeomUtils.isRectangleBigEnoughForObject(
							rule.getxVectorMinLength(), rule.getxVectorMaxLength(),
							rule.getyVectorMinLength(), rule.getyVectorMaxLength(), polygon);
					Boolean checkSimplePoly = !rule.isSimplePolygonOnly()
							|| (rule.isSimplePolygonOnly() && osmPolygon.isSimplePolygon());
					if (areaOK && sizeOK && checkSimplePoly) {
						if (height > 0 && isMultiHeight(rule)) {
							return rule;
						} else {
							matchingRules.add(rule);
						}
					}
				}
			}
		}
		if (!matchingRules.isEmpty()) {
			return matchingRules.get(random.nextInt(matchingRules.size()));
		}
		return null;
	}

	protected boolean isMultiHeight(FlightGearObjectTagRule objectTagRule) {
		List<ObjectFile> objectsFiles = objectTagRule.getObjectsFiles();
		if (objectsFiles.size() < 2) {
			return false;
		}
		int heightCnt = 0;
		for (ObjectFile objectFile : objectsFiles) {
			if (extractHeight(objectFile.getPath()) > -1) {
				heightCnt++;
			}
		}
		return heightCnt >= 2;
	}

	protected double extractHeight(String fileName) {
		if (!fileName.endsWith(OBJ_EXT)) {
			return -1;
		}
		fileName = fileName.substring(0, fileName.length() - OBJ_EXT.length());
		if (!fileName.endsWith("m")) {
			return -1;
		}
		int idx = Math.max(fileName.lastIndexOf('-'), fileName.lastIndexOf('_'));
		if (idx > 0) {
			String numStr = fileName.substring(idx + 1, fileName.length() - 1);
			try {
				return Double.parseDouble(numStr);
			} catch (NumberFormatException e) {
				// Best effort
			}
		}
		return -1;
	}

	/**
	 * Handles a standalone node that matches a rule-based 3D object (e.g. cooling towers, chimneys,
	 * lighthouses, water towers which OSM maps as nodes). Returns {@code true} if a rule matched and
	 * an {@code OBJECT_SHARED_AGL} declaration was written.
	 */
	public boolean handleNode(Node node) {
		FlightGearOptions options = FlightGearOptionsProvider.getOptions();
		if (!options.isGenerateObjects() || node == null || node.getTags() == null
				|| node.getTags().isEmpty()) {
			return false;
		}
		List<FlightGearObjectTagRule> matchingRules = new ArrayList<>();
		for (FlightGearObjectTagRule rule : options.getObjectsRules().getRules()) {
			if (rule == null) {
				continue;
			}
			for (Tag tag : node.getTags()) {
				if (OsmUtils.compareTags(rule.getTag(), tag)) {
					matchingRules.add(rule);
					break;
				}
			}
		}
		if (matchingRules.isEmpty()) {
			return false;
		}
		FlightGearObjectTagRule rule = matchingRules.get(random.nextInt(matchingRules.size()));
		ObjectFile chosen = getObjectFromRule(rule, null);
		if (chosen == null) {
			return false;
		}
		double angle = rule.getAngle();
		if (rule.isRandomAngle()) {
			angle = Double.valueOf(MiscUtils.getRandomInt(0, 360));
		}
		writeObject(chosen.getPath(), new Point2D(node.getLon(), node.getLat()), angle);
		return true;
	}

	protected ObjectFile getObjectFromRule(FlightGearObjectTagRule rule, OsmPolygon osmPolygon) {
		int height = osmPolygon == null ? 0 : osmPolygon.getHeight();
		if (isMultiHeight(rule)) {
			if (height > 0) {
				double minDelta = Double.MAX_VALUE;
				ObjectFile best = null;
				for (ObjectFile objectFile : rule.getObjectsFiles()) {
					double delta = Math.abs(extractHeight(objectFile.getPath()) - height);
					if (delta < minDelta) {
						minDelta = delta;
						best = objectFile;
					}
				}
				return best;
			}
			return rule.getObjectsFiles().get(0);
		}
		List<ObjectFile> objectsFiles = rule.getObjectsFiles();
		return objectsFiles.get(random.nextInt(objectsFiles.size()));
	}

	private void writeObject(String modelPath, Point2D origin, double angle) {
		if (stgWriterProvider == null) {
			Osm2xpLogger.warning("FG object translator: no STG writer provider set, skipping object");
			return;
		}
		BufferedWriter writer = stgWriterProvider.getStgWriter(origin.x(), origin.y());
		if (writer == null) {
			return;
		}
		try {
			writer.write(String.format(Locale.ROOT, STG_PATTERN, modelPath, origin.x(), origin.y(), angle));
			CountStats countStats = StatsProvider.getCommonStats();
			if (countStats != null) {
				countStats.incCount("object");
			}
		} catch (IOException e) {
			Osm2xpLogger.error("Error writing FlightGear object declaration", e);
		}
	}

	private double calculateAngle(LinearRing2D polygon, FlightGearObjectTagRule rule) {
		if (polygon.edges().size() == 4) {
			if (rule.getxVectorMaxLength() > 0 && rule.getyVectorMaxLength() == 0) {
				int edgeIdx = random.nextInt(polygon.edgeNumber());
				LineSegment2D edge = polygon.edge(edgeIdx);
				return GeomUtils.getTrueBearing(edge.lastPoint(), edge.firstPoint());
			}
			for (int i = 0; i < polygon.vertices().size() - 2; i++) {
				Point2D ptX = polygon.vertex(i);
				Point2D ptOrigin = polygon.vertex(i + 1);
				Point2D ptY = polygon.vertex(i + 2);
				double segmentX = GeomUtils.latLonDistance(ptX.y(), ptX.x(), ptOrigin.y(), ptOrigin.x());
				double segmentY = GeomUtils.latLonDistance(ptOrigin.y(), ptOrigin.x(), ptY.y(), ptY.x());
				boolean xVectorCheck = segmentX > rule.getxVectorMinLength()
						&& segmentX < rule.getxVectorMaxLength();
				boolean yVectorCheck = segmentY > rule.getyVectorMinLength()
						&& segmentY < rule.getyVectorMaxLength();
				if (xVectorCheck && yVectorCheck) {
					return GeomUtils.getTrueBearing(ptOrigin, ptY);
				}
			}
		}
		double maxLength = 0;
		LineSegment2D maxEdge = null;
		Collection<LineSegment2D> edges = polygon.edges();
		for (LineSegment2D lineSegment2D : edges) {
			double length = GeomUtils.computeLengthInMeters(lineSegment2D);
			if (length > maxLength) {
				maxLength = length;
				maxEdge = lineSegment2D;
			}
		}
		if (maxEdge != null) {
			return (GeomUtils.getTrueBearing(maxEdge.firstPoint(), maxEdge.lastPoint()) + 90) % 360;
		}
		return 0;
	}

	@Override
	public void translationComplete() {
		// Nothing to flush - STG lines are written eagerly
	}

	@Override
	public String getId() {
		return "object";
	}

	@Override
	public boolean isTerminating() {
		return false;
	}
}