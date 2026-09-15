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
