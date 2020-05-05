package com.osm2xp.translators.xplane;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.utils.geometry.GeomUtils;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.writers.IWriter;

import math.geom2d.Point2D;

public abstract class XPPathTranslator extends XPWritingTranslator {

	private Set<Long> pathNodeIds = new HashSet<Long>();
	private Set<Long> pathCrossingIds = new HashSet<Long>();
	private List<OsmPolyline> pathPolys = new ArrayList<>();
	
	private Map<Integer,Integer> bridgeNodeLayers = new HashMap<>();
	private XPOutputFormat outputFormat;
	private IDRenumbererService idProvider;
	
	public XPPathTranslator(IWriter writer, XPOutputFormat outputFormat, IDRenumbererService idProvider) {
		super(writer);
		this.outputFormat = outputFormat;
		this.idProvider = idProvider;
	}

	protected void addSegmentsFrom(OsmPolyline poly) {
		poly.getNodes().stream().map(node -> node.getId()).forEach(id -> {
			if (pathNodeIds.contains(id)) {
				pathCrossingIds.add(id);
			}
			pathNodeIds.add(id);
		});
		pathPolys.add(poly);
	}

	@Override
	public void translationComplete() {
		List<XPPathSegment> segmentList = new ArrayList<XPPathSegment>();
		for (OsmPolyline poly : pathPolys) {
			segmentList.addAll(getSegmentsFor(poly));
		}
		List <XPPathSegment> resultSegmentList = segmentList;
		if (!bridgeNodeLayers.isEmpty()) {
			resultSegmentList = new ArrayList<>(segmentList.size());
			for (XPPathSegment pathSegment : segmentList) {
				pathSegment.setStartHeight(getLayer(Integer.valueOf((int) pathSegment.getStartId())));
				pathSegment.setEndHeight(getLayer(Integer.valueOf((int) pathSegment.getEndId())));
				if (!pathSegment.isBridge() && 
					(pathSegment.getStartHeight() > 0 || pathSegment.getEndHeight() > 0)) {
					resultSegmentList.addAll(splitIfNecessary(pathSegment)); 
				} else {
					resultSegmentList.add(pathSegment);
				}
			}
		}
		for (XPPathSegment pathSegment : resultSegmentList) {
			writer.write(outputFormat.getPathStr(pathSegment));
		}
		
	}
	
	protected int getLayer(Integer nodeId) {
		Integer layer = bridgeNodeLayers.get(nodeId);
		if (layer == null) {
			return 0;
		}
		return layer;
	}

	private Collection<? extends XPPathSegment> splitIfNecessary(XPPathSegment pathSegment) {
		int minLength = getBridgeRampLength();
		if (minLength > 0) {
			Point2D[] points = pathSegment.getPoints();
			double length = GeomUtils.computeLengthInMeters(points);
			boolean needStartEntrance = pathSegment.getStartHeight() > 0;
			boolean needEndEntrance = pathSegment.getEndHeight() > 0;		
			if ((needStartEntrance && needEndEntrance && length > minLength * 2) ||
			   ((needStartEntrance != needEndEntrance) && length > minLength)) {
				XPPathSegment startSegment = null, endSegment = null;
				if (needStartEntrance) {
					double sumLen = 0;
					for (int i = 1; i < points.length; i++) {
						Double curLen = GeomUtils.latLonDistance(points[i-1].y(),
								points[i-1].x(), points[i].y(),points[i].x());
						if (sumLen + curLen > minLength) {
							double newSegLen = minLength - sumLen;
							double k = newSegLen / curLen;
							double newX = points[i-1].x() + (points[i].x()-points[i-1].x())*k;
							double newY = points[i-1].y() + (points[i].y()-points[i-1].y())*k;
							Point2D[] newSegPts = Arrays.copyOf(points, i + 1);
							newSegPts[i] = new Point2D(newX, newY);
							int newPointId = idProvider.getIncrementId();
							startSegment = new XPPathSegment(pathSegment.getType(), pathSegment.getStartId(), newPointId, newSegPts);
							startSegment.setStartHeight(getLayer((int) pathSegment.getStartId()));
							Point2D[] tailPts = Arrays.copyOfRange(points, i, points.length);
							tailPts = (Point2D[]) ArrayUtils.add(tailPts, 0, new Point2D(newX, newY));
							pathSegment = new XPPathSegment(pathSegment.getType(), newPointId, pathSegment.getEndId(), tailPts); //Leave a tail of original segment
							break;
						}
						sumLen += curLen;
					}
				}
				if (needEndEntrance) {
					points = pathSegment.getPoints();
					double sumLen = 0;
					for (int i = points.length - 2; i >= 0; i--) {
						Double curLen = GeomUtils.latLonDistance(points[i+1].y(),
								points[i+1].x(), points[i].y(),points[i].x());
						if (sumLen + curLen > minLength) {
							double newSegLen = minLength - sumLen;
							double k = newSegLen / curLen;
							double newX = points[i+1].x() + (points[i].x()-points[i+1].x())*k;
							double newY = points[i+1].y() + (points[i].y()-points[i+1].y())*k;
							Point2D[] newSegPts = Arrays.copyOf(points, i + 2);
							Point2D newPoint = new Point2D(newX, newY);
							newSegPts[i+1] = newPoint;
							long originalEndId = pathSegment.getEndId();
							int newPointId = idProvider.getIncrementId();
							pathSegment = new XPPathSegment(pathSegment.getType(), pathSegment.getStartId(), newPointId, newSegPts);
							Point2D[] tailPts = Arrays.copyOfRange(points, i+1, points.length); //was i 
							tailPts = (Point2D[]) ArrayUtils.add(tailPts, 0, newPoint);
							endSegment = new XPPathSegment(pathSegment.getType(), newPointId, originalEndId, tailPts); //Leave a tail of original segment
							endSegment.setEndHeight(getLayer((int) originalEndId));
							break;
						}
						sumLen += curLen;
					}
				}
				List<XPPathSegment> resList = new ArrayList<XPPathSegment>();
				if (startSegment != null) {
					resList.add(startSegment);
				}
				resList.add(pathSegment);
				if (endSegment != null) {
					resList.add(endSegment);
				}
				return resList;
			}
		}
		return Collections.singletonList(pathSegment);
	}

	/**
	 * @return Min bridge *entrance* segment length 
	 */
	protected int getBridgeRampLength() {
		return 100;
	}

	private List<XPPathSegment> getSegmentsFor(OsmPolyline poly) {
		List<XPPathSegment> result = new ArrayList<XPPathSegment>();
		List<Node> currentSegment = new ArrayList<Node>();
		boolean bridge = isBridge(poly);
		List<Node> nodes = poly.getNodes();
		if (nodes.size() <= 1) {
			return Collections.emptyList();
		}
		for (int i = 0; i < nodes.size(); i++) {
			Node node = nodes.get(i);
			currentSegment.add(node);
			if ((i == nodes.size() - 1) ||
				(currentSegment.size() > 1 && pathCrossingIds.contains(node.getId()))) {
				int newStartId = idProvider.getNewId(currentSegment.get(0).getId());
				int newEndId = idProvider.getNewId(node.getId());
				if (bridge) {
					int layer = Math.max(1, getLayerFromTags(poly)); //We support bridge layers starting from 1
					bridgeNodeLayers.put(newStartId, layer);
					bridgeNodeLayers.put(newEndId, layer);
				}
				XPPathSegment segment = new XPPathSegment(getPathType(poly), 
						newStartId, 
						newEndId,
						GeomUtils.getPointsFromOsmNodes(currentSegment));
				segment.setComment(getId() + " , way " + poly.getId());
				segment.setBridge(bridge);
				result.add(segment);
				currentSegment.clear();
				if (i < nodes.size() - 1) {
					currentSegment.add(node);
				}
			}
		}		
		return result;
	}

	protected int getLayerFromTags(OsmPolyline poly) {
		String tagValue = poly.getTagValue("layer");
		if (tagValue != null) {			
			try {
				return Math.max(0, Integer.parseInt(tagValue.trim()));
			} catch (NumberFormatException e) {
				//Best effort
			}
		}
		return 0;
	}

	protected boolean isBridge(IHasTags poly) {
		return XPlaneOptionsProvider.getOptions().isGenerateBridges() && !StringUtils.isEmpty(poly.getTagValue("bridge"));
	}

//	private boolean isDifferentTiles(Node node, Node nextNode) {
//		int latDiff = (int) (Math.floor(node.getLat()) - Math.floor(nextNode.getLat()));
//		int lonDiff = (int) (Math.floor(node.getLon()) - Math.floor(nextNode.getLon()));
//		return latDiff != 0 || lonDiff != 0;
//	}

	protected abstract int getPathType(IHasTags polygon); 

}