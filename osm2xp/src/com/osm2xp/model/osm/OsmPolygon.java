package com.osm2xp.model.osm;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import math.geom2d.Point2D;
import math.geom2d.polygon.LinearRing2D;

import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.OsmUtils;

/**
 * OsmPolygon.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class OsmPolygon {

	@Override
	public String toString() {
		return "OsmPolygon [id=" + id + ", tag=" + tags + "]";
	}

	protected Long id;
	protected List<Tag> tags;
	protected List<Node> nodes;
	private Double area;
	private Boolean simplePolygon;
	private LinearRing2D polygon;
	private Point2D center;
	private Integer height;
	/**
	 * Whether only part of nodes is present for this.
	 * Can be OK to use for forest, road or powerline generation, but not OK for buildings or fences
	 */
	private boolean partial;

	public OsmPolygon(long id, List<Tag> tags, List<Node> nodes, boolean partial) {
		super();
		this.id = id;
		this.tags = tags;
		this.nodes = nodes;
		this.partial = partial;
		this.height = OsmUtils.getHeightFromTags(tags);

	}

	private void initPolygon() {
		polygon = GeomUtils.getPolygonFromOsmNodes(nodes);

	}

	public OsmPolygon() {

	}

	/**
	 * Split current polygon along tiles
	 * 
	 * @return
	 */
	public List<OsmPolygon> splitPolygonAroundTiles() {
		List<OsmPolygon> result = new ArrayList<OsmPolygon>();
		// first check on how many tiles is this polygon
		Set<Point2D> tiles = new HashSet<Point2D>();
		for (Node node : nodes) {
			Point2D point2d = new Point2D(node.lon, node.lat);
			point2d = GeomUtils.cleanCoordinatePoint(point2d);
			tiles.add(point2d);
		}

		// if the polygon is on only one tile, return the current polygon
		if (tiles.size() == 1) {
			result.add(this);
		} else {
			// the polygon is on more than one tile, split it.
			if (this.polygon == null) {
				initPolygon();
			}
			Map<Point2D, OsmPolygon> polygons = new HashMap<Point2D, OsmPolygon>();
			for (Point2D point : polygon.getVertices()) {
				Point2D tilePoint = GeomUtils.cleanCoordinatePoint(point);
				if (polygons.get(tilePoint) == null) {
					polygons.put(tilePoint, new OsmPolygon(id, tags,
							new ArrayList<Node>(), false));
				}
				polygons.get(tilePoint).getNodes()
						.add(new Node(null, point.y, point.x, 1));

			}

			for (Map.Entry<Point2D, OsmPolygon> entry : polygons.entrySet()) {
				result.add(entry.getValue());
			}
		}
		return result;

	}

	public void simplifyPolygon() {
		if (this.polygon != null) {
			LinearRing2D result = GeomUtils.simplifyPolygon(this.polygon);
			this.polygon = result;
		}
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public List<Tag> getTags() {
		return tags;
	}
	
	/**
	 * Return tag value for given key, of present. Return <code>null</code> otherwise
	 * @param tagKey tag key
	 * @return value for given key, of present, <code>null</code> otherwise
	 */
	public String getTagValue(String tagKey) {
		Optional<Tag> first = tags.stream().filter(tag -> tagKey.equals(tag.key)).findFirst();
		return first.isPresent() ? first.get().getValue() : null;
	}

	public List<Node> getNodes() {
		return nodes;
	}

	public Double getMinVectorSize() {
		Double result = null;
		if (this.getPolygon() != null) {
			Double[] vectors = GeomUtils.computeExtremeVectors(polygon);
			result = vectors[0];
		}
		return result;

	}

	public Double getMaxVectorSize() {
		Double result = null;
		if (this.getPolygon() != null) {
			Double[] vectors = GeomUtils.computeExtremeVectors(polygon);
			result = vectors[1];
		}
		return result;
	}

	public Double getArea() {
		if (nodes.size() > 2) {
			this.area = polygon.getArea();
		} else {
			this.area = 0D;
		}
		return area;
	}

	public Boolean isSimplePolygon() {
		Boolean result = false;
		if (this.getPolygon() != null) {
			result = (polygon.getEdges().size() == 4 && GeomUtils
					.areParallelsSegmentsIdentics(polygon));
		}

		return result;
	}

	public LinearRing2D getPolygon() {
		if (this.polygon == null) {
			initPolygon();
		}
		return polygon;
	}

	public void setPolygon(LinearRing2D polygon) {
		this.polygon = polygon;
	}

	public Point2D getCenter() {
		if (this.nodes.size() > 1) {
			this.center = GeomUtils.getPolygonCenter(polygon);
		} else {
			this.center = new Point2D(nodes.get(0).lon, nodes.get(0).lat);
		}
		return center;
	}

	public Integer getHeight() {
		return height;
	}

	public Color getRoofColor() {
		return OsmUtils.getRoofColorFromTags(this.tags);
	}

	public void setHeight(Integer height) {
		this.height = height;
	}

	/**
	 * Whether all polygon nodes was read from source file
	 * Can be OK to generate road, railway, powerline or forest piece based on partial data,
	 * but not OK to generate building
	 * @return all polygon nodes was read from source file
	 */
	public boolean isPartial() {
		return partial;
	}

	public void setPartial(boolean partial) {
		this.partial = partial;
	}

}
