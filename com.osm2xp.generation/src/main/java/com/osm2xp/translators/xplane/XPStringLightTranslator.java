package com.osm2xp.translators.xplane;

import java.util.ArrayList;
import java.util.List;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.options.XplaneOptions;
import com.osm2xp.generation.xplane.resources.DsfObjectsProvider;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.utils.geometry.GeomUtils;
import com.osm2xp.writers.IWriter;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearCurve2D;
import math.geom2d.polygon.Polyline2D;

public class XPStringLightTranslator implements IXPLightTranslator{
	
	private DsfObjectsProvider objectsProvider;
	private XPOutputFormat outputFormat;
	private IWriter writer;

	public XPStringLightTranslator(IWriter writer, DsfObjectsProvider objectsProvider, XPOutputFormat outputFormat) {
		super();
		this.writer = writer;
		this.objectsProvider = objectsProvider;
		this.outputFormat = outputFormat;
	}

	@Override
	public void writeLightStrings(LinearCurve2D baseLine, double distance, boolean doubleSided) {
		XplaneOptions options = XPlaneOptionsProvider.getOptions();
		double length = GeomUtils.computeEdgesLength(baseLine);
		int interval = options.getStreetLightsInterval();
		if (length > interval + distance * 2) { //Ignore length, which is smaller than lights interval
			List<LinearCurve2D> lightStrings = getLightStrings(baseLine, length, distance, doubleSided);
			for (LinearCurve2D lightStr : lightStrings) {
				writer.write(outputFormat.getPolygonString(lightStr, objectsProvider.getStringIndex(options.getLightObjectString()) + "", interval + ""));
			}
		}
	}
	
	@SuppressWarnings("restriction")
	protected List<LinearCurve2D> getLightStrings(LinearCurve2D baseLine, double length, double distance, boolean doubleSided) {		
		List<LinearCurve2D> resList = new ArrayList<>();
		LinearCurve2D shortenedLine = getShortenedLine(baseLine, length, distance);
		LinearCurve2D localCurve = GeomUtils.linearCurve2DToLocal(shortenedLine, shortenedLine.vertex(0));
		double coordsDist = distance / GeomUtils.LATITUDE_TO_M;
		resList.add(GeomUtils.localToLinearCurve2D(localCurve.parallel(-coordsDist), shortenedLine.vertex(0)));
		if (doubleSided) {
			LinearCurve2D curve = GeomUtils.localToLinearCurve2D(localCurve.parallel(coordsDist), shortenedLine.vertex(0));
			curve = (LinearCurve2D) curve.reverse(); //We need this to make the lampposts directed towards the road center
			resList.add(curve);
		}
		return resList;
	}

	protected LinearCurve2D getShortenedLine(LinearCurve2D baseLine, double length, double cutDistance) {
		double sumLen = 0;
		List<Point2D> pts = new ArrayList<Point2D>();
		double endLen = length - cutDistance;
		for (int i = 1; i < baseLine.vertexNumber() && sumLen < endLen; i++) {
			double currentDist = GeomUtils.latLonDistance(baseLine.vertex(i - 1), baseLine.vertex(i));
			if (sumLen > cutDistance) {
				pts.add(baseLine.vertex(i - 1));
			} else if (sumLen + currentDist > cutDistance) {
				double newSegLen = cutDistance - sumLen;
				pts.add(getNewPoint(baseLine, i, currentDist, newSegLen));
			}
			if (sumLen + currentDist > endLen) {
				double newSegLen = endLen - sumLen;
				pts.add(getNewPoint(baseLine, i, currentDist, newSegLen));
				break;
			}
			sumLen += currentDist;
		}
		LinearCurve2D shortenedLine = new Polyline2D(pts);
		return shortenedLine;
	}
	
	/**
	 * Return new point on a specified curve between points i-1 and i
	 * @param baseLine {@link LinearCurve2D} instance
	 * @param i point index
	 * @param segmentLength distance between points i-1 and i
	 * @param newSegmentLength new segment length, should be <= segmentLength
	 * @return new point
	 */
	protected Point2D getNewPoint(LinearCurve2D baseLine, int i, double segmentLength, double newSegmentLength) {
		double k = newSegmentLength / segmentLength;
		if (k < 0 || k > 1) {
			throw new IllegalArgumentException();
		}
		double newX = baseLine.vertex(i - 1).x() * (1-k)  + baseLine.vertex(i).x() * k;
		double newY = baseLine.vertex(i - 1).y() * (1-k)  + baseLine.vertex(i).y() * k;
		return new Point2D(newX, newY);
	}
	

}
