package com.osm2xp.translators.flightgear;

import java.util.Random;

import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.utils.MiscUtils;
import com.osm2xp.utils.geometry.GeomUtils;

import math.geom2d.Point2D;
import math.geom2d.line.LineSegment2D;
import math.geom2d.polygon.LinearCurve2D;
import math.geom2d.polygon.LinearRing2D;

public class FlightGearBuildingAnalyzer {

	private static final double SCALE = 111320.0;

	private final Random random;
	private final FlightGearOptions options;
	private final double levelHeight;
	private final FlightGearElevProber elevProber;

	public FlightGearBuildingAnalyzer(FlightGearOptions options, Random random) {
		this(options, random, null);
	}

	public FlightGearBuildingAnalyzer(FlightGearOptions options, Random random, FlightGearElevProber elevProber) {
		this.options = options;
		this.random = random;
		this.levelHeight = GlobalOptionsProvider.getOptions().getLevelHeight();
		this.elevProber = elevProber;
	}

	public FlightGearBuildingListEntry analyze(OsmPolygon polygon) {
		LinearRing2D ring = polygon.getPolygon();

		if (!isConvex(ring)) {
			return null;
		}

		Point2D center = polygon.getCenter();

		LinearCurve2D local = GeomUtils.linearCurve2DToLocal(ring, center);
		double[] bbox = computeLocalBbox(local);
		double width = (bbox[1] - bbox[0]) * SCALE;
		double depth = (bbox[3] - bbox[2]) * SCALE;
		if (depth > width) {
			double temp = width;
			width = depth;
			depth = temp;
		}

		// Area deviation check
		double localArea = computeLocalArea(local);
		double bboxArea = (bbox[1] - bbox[0]) * (bbox[3] - bbox[2]);
		if (bboxArea <= 0 || (localArea / bboxArea) <= options.getBuildingListAreaDeviation()) {
			return null;
		}

		// Height / levels analysis
		int levels = analyzeLevels(polygon);
		double facadeHeight = levels * levelHeight;

		// Roof shape and height
		RoofShape roofShape = analyzeRoofShape(polygon);
		double roofHeight = computeRoofHeight(roofShape, width);

		// Building list type
		BuildingListType listType = calcBuildingListType(levels, width, depth);
		if (listType == BuildingListType.UNSUITABLE) {
			return null;
		}

		// Street angle
		double streetAngle = calcStreetAngle(ring);

		// Ground elevation via fgelev probing (outer ring points, take minimum)
		double groundElev = probeGroundElevation(ring);
		if (groundElev == FlightGearElevProber.NO_ELEV) {
			return null;
		}

		// Roof orientation: 0 if ridge parallel to street, 1 if perpendicular
		int roofOrientation = (streetAngle < 45 || streetAngle > 135) ? 0 : 1;

		// Texture indices from deterministic pseudo-random
		int wallTexIdx = computeTextureIndex(center.x(), center.y(), 0);
		int roofTexIdx = computeTextureIndex(center.x(), center.y(), 7);

		return new FlightGearBuildingListEntry(
				center.y(), center.x(), groundElev, streetAngle, listType,
				width, depth, facadeHeight, roofHeight, roofShape,
				roofOrientation, levels, wallTexIdx, roofTexIdx);
	}

	private double probeGroundElevation(LinearRing2D ring) {
		if (elevProber == null || elevProber.isDisabled()) {
			return 0.0;
		}
		double minElev = Double.MAX_VALUE;
		for (Point2D point : ring.vertices()) {
			double elev = elevProber.probe(point.x(), point.y());
			if (elev == FlightGearElevProber.NO_ELEV) {
				return FlightGearElevProber.NO_ELEV;
			}
			if (elev < minElev) {
				minElev = elev;
			}
		}
		return minElev;
	}

	private boolean isConvex(LinearRing2D ring) {
		int n = ring.vertexNumber();
		if (n < 3)
			return false;
		boolean positive = false;
		boolean negative = false;
		Point2D[] verts = new Point2D[n];
		int idx = 0;
		for (Point2D p : ring.vertices()) {
			verts[idx++] = p;
		}
		for (int i = 0; i < n; i++) {
			Point2D p0 = verts[i];
			Point2D p1 = verts[(i + 1) % n];
			Point2D p2 = verts[(i + 2) % n];
			double cross = (p1.x() - p0.x()) * (p2.y() - p1.y())
					- (p1.y() - p0.y()) * (p2.x() - p1.x());
			if (cross > 0)
				positive = true;
			if (cross < 0)
				negative = true;
			if (positive && negative)
				return false;
		}
		return true;
	}

	private int analyzeLevels(OsmPolygon polygon) {
		// First try explicit height tag
		String heightStr = polygon.getTagValue("height");
		if (heightStr != null) {
			Double height = MiscUtils.extractNumber(heightStr);
			if (height != null && height > 2 && height < 800) {
				// If building:levels is also present, use it directly
				String levelsStr = polygon.getTagValue("building:levels");
				if (levelsStr != null) {
					try {
						int levels = Integer.parseInt(levelsStr.trim());
						if (levels > 0 && levels < 100) {
							return levels;
						}
					} catch (NumberFormatException e) {
						// fall through
					}
				}
				return Math.max(1, (int) Math.round(height / levelHeight));
			}
		}

		// Try building:levels tag
		String levelsStr = polygon.getTagValue("building:levels");
		if (levelsStr != null) {
			try {
				int levels = Integer.parseInt(levelsStr.trim());
				if (levels > 0 && levels < 100) {
					return levels;
				}
			} catch (NumberFormatException e) {
				// fall through
			}
		}

		// Fall back to distribution
		return BuildingLevelsDistributions.randomLevels(polygon.getTags(), random);
	}

	private RoofShape analyzeRoofShape(OsmPolygon polygon) {
		String roofShapeTag = polygon.getTagValue("roof:shape");
		RoofShape fromTag = RoofShape.fromOsmTag(roofShapeTag);
		if (fromTag != null) {
			return fromTag;
		}

		double r = random.nextDouble();
		if (r < options.getRoofShapeFlatRatio()) {
			return RoofShape.FLAT;
		}
		r -= options.getRoofShapeFlatRatio();
		if (r < options.getRoofShapeGabledRatio()) {
			return RoofShape.GABLED;
		}
		return RoofShape.HIPPED;
	}

	private double computeRoofHeight(RoofShape roofShape, double width) {
		switch (roofShape) {
			case GABLED:
				return width / 3.0;
			case HIPPED:
				return width * 0.2;
			default:
				return 0;
		}
	}

	private BuildingListType calcBuildingListType(int levels, double width, double depth) {
		double minSide = Math.min(width, depth);
		double maxSide = Math.max(width, depth);

		if (levels <= options.getBuildingListSmallMaxLevels()
				&& minSide >= options.getBuildingListSmallMinSide()
				&& maxSide >= options.getBuildingListSmallMinSide() * 1.5) {
			return BuildingListType.SMALL;
		}

		if (levels <= options.getBuildingListMediumMaxLevels()
				&& minSide >= options.getBuildingListMediumMinSide()
				&& maxSide >= options.getBuildingListMediumMinSide() * 1.5) {
			return BuildingListType.MEDIUM;
		}

		if (levels <= options.getBuildingListLargeMaxLevels()
				&& minSide >= options.getBuildingListLargeMinSide()
				&& maxSide >= options.getBuildingListLargeMinSide() * 1.5) {
			return BuildingListType.LARGE;
		}

		return BuildingListType.UNSUITABLE;
	}

	private double calcStreetAngle(LinearRing2D ring) {
		double longestLength = 0;
		Point2D longestStart = null;
		Point2D longestEnd = null;

		for (LineSegment2D segment : ring.edges()) {
			double length = GeomUtils.latLonDistance(
					segment.firstPoint().y(), segment.firstPoint().x(),
					segment.lastPoint().y(), segment.lastPoint().x());
			if (length > longestLength) {
				longestLength = length;
				longestStart = segment.firstPoint();
				longestEnd = segment.lastPoint();
			}
		}

		if (longestStart == null) {
			return 0.0;
		}

		return GeomUtils.getTrueBearing(longestStart, longestEnd);
	}

	private double[] computeLocalBbox(LinearCurve2D curve) {
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
		double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		for (Point2D p : curve.vertices()) {
			if (p.x() < minX)
				minX = p.x();
			if (p.x() > maxX)
				maxX = p.x();
			if (p.y() < minY)
				minY = p.y();
			if (p.y() > maxY)
				maxY = p.y();
		}
		return new double[] { minX, maxX, minY, maxY };
	}

	private double computeLocalArea(LinearCurve2D curve) {
		double area = 0;
		for (LineSegment2D seg : curve.edges()) {
			area += seg.firstPoint().x() * seg.lastPoint().y();
			area -= seg.lastPoint().x() * seg.firstPoint().y();
		}
		return Math.abs(area) / 2.0;
	}

	private int computeTextureIndex(double lon, double lat, int seed) {
		return Math.abs((int) (lon * 1000 + lat * 1000 + seed * 777)) % 32;
	}
}
