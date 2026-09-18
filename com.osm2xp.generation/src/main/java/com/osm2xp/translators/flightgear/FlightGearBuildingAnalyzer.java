package com.osm2xp.translators.flightgear;

import java.util.ArrayList;
import java.util.List;
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

		Point2D center = polygon.getCenter();

		LinearCurve2D local = GeomUtils.linearCurve2DToLocal(ring, center);
		PcaRectangle rect = computePcaRectangle(local);

		double width = rect.width * SCALE;
		double depth = rect.depth * SCALE;

		// Area deviation check: reject footprints poorly approximated by a rectangle
		double localArea = computeLocalArea(local);
		if (rect.area <= 0 || (localArea / rect.area) < options.getBuildingListAreaDeviation()) {
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

		// Street angle: BUILDING_LIST expects the front-to-back heading (perpendicular to
		// the street-facing facade), while PCA gives the long-axis bearing. The front
		// facade of a building normally runs along its long side, so the street angle is
		// the short axis = long axis + 90 deg.
		double streetAngle = (rect.angle + 90) % 360;

		// Ground elevation via fgelev probing (outer ring points, take minimum)
		double groundElev = probeGroundElevation(ring);
		if (groundElev == FlightGearElevProber.NO_ELEV) {
			return null;
		}

		// Roof orientation: 0 if ridge parallel to front (long side), 1 if perpendicular
		int roofOrientation = (width >= depth) ? 0 : 1;

		// Texture indices from deterministic pseudo-random
		int wallTexIdx = computeTextureIndex(center.x(), center.y(), 0);
		int roofTexIdx = computeTextureIndex(center.x(), center.y(), 7);

		// BUILDING_LIST origin is the center of the box, so anchor it at the footprint
		// centroid (same convention as the 3D-object translators, which place by center).
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
				return Math.min(4, width / 3.0);
			case HIPPED:
				return Math.min(4, width * 0.2);
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

//		if (levels <= options.getBuildingListLargeMaxLevels()
//				&& minSide >= options.getBuildingListLargeMinSide()
//				&& maxSide >= options.getBuildingListLargeMinSide() * 1.5) {
//			return BuildingListType.LARGE;
//		}

		return BuildingListType.LARGE;
	}

	private PcaRectangle computePcaRectangle(LinearCurve2D curve) {
		List<Point2D> pts = new ArrayList<>();
		for (Point2D p : curve.vertices()) {
			pts.add(p);
		}
		if (pts.size() > 1 && pts.get(0).equals(pts.get(pts.size() - 1))) {
			pts.remove(pts.size() - 1);
		}
		int n = pts.size();
		if (n == 0) {
			return new PcaRectangle(0, 0, 0, 0);
		}
		if (n == 4) {
			return computeRectangle4Verts(pts);
		}

		double mx = 0, my = 0;
		for (Point2D p : pts) {
			mx += p.x();
			my += p.y();
		}
		mx /= n;
		my /= n;

		double sxx = 0, syy = 0, sxy = 0;
		for (Point2D p : pts) {
			double dx = p.x() - mx;
			double dy = p.y() - my;
			sxx += dx * dx;
			syy += dy * dy;
			sxy += dx * dy;
		}

		double trace = sxx + syy;
		double disc = Math.sqrt((sxx - syy) * (sxx - syy) / 4.0 + sxy * sxy);
		double lambda = trace / 2.0 + disc;

		double vx, vy;
		if (Math.abs(sxy) < 1e-12) {
			vx = sxx >= syy ? 1.0 : 0.0;
			vy = sxx >= syy ? 0.0 : 1.0;
		} else {
			vx = sxy;
			vy = lambda - sxx;
			double len = Math.hypot(vx, vy);
			vx /= len;
			vy /= len;
		}

		double minU = Double.MAX_VALUE, maxU = -Double.MAX_VALUE;
		double minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
		for (Point2D p : pts) {
			double u = p.x() * vx + p.y() * vy;
			double v = p.x() * (-vy) + p.y() * vx;
			minU = Math.min(minU, u);
			maxU = Math.max(maxU, u);
			minV = Math.min(minV, v);
			maxV = Math.max(maxV, v);
		}

		double width = maxU - minU;
		double depth = maxV - minV;
		double angle = (Math.toDegrees(Math.atan2(vx, vy)) + 360) % 360;
		if (depth > width) {
			double tmp = width;
			width = depth;
			depth = tmp;
			angle = (angle + 90) % 360;
		}

		return new PcaRectangle(width, depth, width * depth, angle);
	}

	private PcaRectangle computeRectangle4Verts(List<Point2D> pts) {
		Point2D p0 = pts.get(0);
		Point2D p1 = pts.get(1);
		Point2D p2 = pts.get(2);
		Point2D p3 = pts.get(3);

		double d01 = p0.distance(p1);
		double d12 = p1.distance(p2);
		double d23 = p2.distance(p3);
		double d30 = p3.distance(p0);

		double width, depth, angle;
		if (d01 + d23 >= d12 + d30) {
			width = (d01 + d23) / 2.0;
			depth = (d12 + d30) / 2.0;
			angle = Math.toDegrees(Math.atan2(p1.x() - p0.x(), p1.y() - p0.y()));
		} else {
			width = (d12 + d30) / 2.0;
			depth = (d01 + d23) / 2.0;
			angle = Math.toDegrees(Math.atan2(p2.x() - p1.x(), p2.y() - p1.y()));
		}
		angle = (angle + 360) % 360;

		return new PcaRectangle(width, depth, width * depth, angle);
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

	private static final class PcaRectangle {
		final double width;
		final double depth;
		final double area;
		final double angle;

		PcaRectangle(double width, double depth, double area, double angle) {
			this.width = width;
			this.depth = depth;
			this.area = area;
			this.angle = angle;
		}
	}
}
