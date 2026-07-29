package com.osm2xp.model.osm.polygon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.utils.geometry.GeomUtils;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearCurve2D;
import math.geom2d.polygon.Polyline2D;

public class OsmPolyline implements IHasTags{

	protected long id;
	protected List<Tag> tags;
	protected List<Node> nodes;
	protected int height = 0;
	protected LinearCurve2D polyline;
	protected Point2D center;
	/**
	 * Indicates whether current polygon is a part of some complex polygon
	 */
	protected boolean part;
	protected Boolean valid = null;
	protected Map<String, String> tagValueCache;

	public OsmPolyline(long id, List<Tag> tags, List<Node> nodes, boolean part) {
		super();
		this.id = id;
		this.tags = tags;
		this.nodes = nodes;
		this.part = part;
	}

	public boolean isOnOneTile() {
		if (nodes == null || nodes.size() == 0) {
			return true;
		}
		Point2D original = GeomUtils.cleanCoordinatePoint(nodes.get(0).getLat(), nodes.get(0).getLon());
		for (int i = 1; i < nodes.size(); i++) {
			if (!original.equals(GeomUtils.cleanCoordinatePoint(nodes.get(i).getLat(), nodes.get(i).getLon()))) {
				return false;
			}
		}
		return true;
	}
	
	protected void initCurve() {
		 this.polyline = GeomUtils.getPolylineFromOsmNodes(nodes);
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	@Override
	public List<Tag> getTags() {
		return tags;
	}

	/**
	 * Return tag value for given key, of present. Return <code>null</code> otherwise
	 * @param tagKey tag key
	 * @return value for given key, of present, <code>null</code> otherwise
	 */
	@Override
	public String getTagValue(String tagKey) {
		if (tagValueCache == null || tagValueCache.size() != tags.size()) {
			tagValueCache = new HashMap<>();
			for (Tag tag : tags) {
				tagValueCache.put(tag.getKey(), tag.getValue());
			}
		}
		return tagValueCache.get(tagKey);
	}

	public List<Node> getNodes() {
		return nodes;
	}
	
	public boolean hasTag(Tag tag) {
		for (Tag curTag : tags) {
			if (curTag.getKey().equalsIgnoreCase(tag.getKey())) {
				if (tag.getValue().length() == 0 || "*".equals(tag.getValue())) {
					return true;
				}
				return curTag.getValue().equalsIgnoreCase(tag.getValue());
			}
		}
		return false;
	}

	public Double getMinVectorSize() {
		Double result = null;
		if (this.getPolyline() != null) {
			Double[] vectors = GeomUtils.computeExtremeVectors(polyline);
			result = vectors[0];
		}
		return result;
	
	}

	public Double getMaxVectorSize() {
		Double result = null;
		if (this.getPolyline() != null) {
			Double[] vectors = GeomUtils.computeExtremeVectors(polyline);
			result = vectors[1];
		}
		return result;
	}

	public Point2D getCenter() {
		getPolyline();
		if (this.nodes.size() > 1) {
			this.center = GeomUtils.getPolylineCenter(polyline);
		} else {
			this.center = new Point2D(nodes.get(0).getLon(), nodes.get(0).getLat());
		}
		return center;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	/**
	 * Whether current polygon is a part of some complex polygon
	 * In this case special logics can be applied for it - e.g. no simplification, using same facade/forest type for all parts etc. 
	 * @return <code>true</code> current polygon is a part of some complex polygon, <code>false</code> otherwise
	 */
	public boolean isPart() {
		return part;
	}

	public void setPart(boolean part) {
		this.part = part;
	}

	@Override
	public String toString() {
		return "OsmPolyline [id=" + id + ", tag=" + tags + "]";
	}

	public boolean isValid() {
		if (valid == null) {
			valid = GeomUtils.isValid(getPolyline());
		}
		return valid;
	}

	public LinearCurve2D getPolyline() {
		if (polyline == null) {
			initCurve();
		}
		return polyline;
	}

	public void setPolyline(Polyline2D polyline) {
		this.polyline = polyline;
	}

}