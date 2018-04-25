package com.osm2xp.translators.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.openstreetmap.osmosis.osmbinary.Osmformat.HeaderBBox;

import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.gui.Activator;
import com.osm2xp.model.osm.Node;
import com.osm2xp.model.osm.OsmPolygon;
import com.osm2xp.model.osm.Relation;
import com.osm2xp.model.osm.Tag;
import com.osm2xp.model.osm.Way;
import com.osm2xp.model.stats.GenerationStats;
import com.osm2xp.model.xplane.XplaneDsf3DObject;
import com.osm2xp.model.xplane.XplaneDsfObject;
import com.osm2xp.translators.BuildingType;
import com.osm2xp.translators.IPolyHandler;
import com.osm2xp.translators.ITranslationListener;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.translators.xplane.IDRenumbererService;
import com.osm2xp.translators.xplane.XPBarrierTranslator;
import com.osm2xp.translators.xplane.XPPowerlineTranslator;
import com.osm2xp.translators.xplane.XPRailTranslator;
import com.osm2xp.translators.xplane.XPRoadTranslator;
import com.osm2xp.utils.DsfObjectsProvider;
import com.osm2xp.utils.GeomUtils;
import com.osm2xp.utils.MiscUtils;
import com.osm2xp.utils.OsmUtils;
import com.osm2xp.utils.helpers.GuiOptionsHelper;
import com.osm2xp.utils.helpers.StatsHelper;
import com.osm2xp.utils.helpers.XplaneOptionsHelper;
import com.osm2xp.utils.logging.Osm2xpLogger;
import com.osm2xp.writers.IWriter;

import math.geom2d.Box2D;
import math.geom2d.Point2D;

public class XPlaneTranslatorImpl implements ITranslator{

	/**
	 * Residential buildings maximum area.
	 */
	private static final double ASSERTION_RESIDENTIAL_MAX_AREA = 0.5;
	/**
	 * Buildings minimum vectors.
	 */
	protected static final int BUILDING_MIN_VECTORS = 3;
	/**
	 * Buildings maximum vectors.
	 */
	protected static final int BUILDING_MAX_VECTORS = 512;	
	/**
	 * Factor to get bounding box lat/long
	 */
	protected static final double COORD_DIV_FACTOR = 1000000000;
	/**
	 * current lat/long tile.
	 */
	protected Point2D currentTile;
	/**
	 * Line separator
	 */
	public static final String LINE_SEP = System.getProperty("line.separator");
	/**
	 * stats object.
	 */
	protected GenerationStats stats;
	/**
	 * file writer.
	 */
	protected IWriter writer;
	/**
	 * start time.
	 */
	protected Date startTime;
	/**
	 * generated scenery folder path.
	 */
	protected String folderPath;
	/**
	 * dsf object provider.
	 */
	protected DsfObjectsProvider dsfObjectsProvider;
	protected ITranslationListener translationListener;
	/**
	 * Building level height, 3 m by default 
	 */
	private double levelHeight = InstanceScope.INSTANCE.getNode(Activator.PLUGIN_ID).getDouble("levelHeight", 3);
	protected List<IPolyHandler> polyHandlers = new ArrayList<IPolyHandler>();

	public XPlaneTranslatorImpl(GenerationStats stats, IWriter writer,
			Point2D currentTile, String folderPath,
			DsfObjectsProvider dsfObjectsProvider) {
		this.currentTile = currentTile;
		this.stats = stats;
		this.writer = writer;
		this.folderPath = folderPath;
		this.dsfObjectsProvider = dsfObjectsProvider;
		this.startTime = new Date();
		
		IDRenumbererService.reinit();
		
		polyHandlers.add(new XPBarrierTranslator(dsfObjectsProvider, writer));
		polyHandlers.add(new XPRoadTranslator(writer));
		polyHandlers.add(new XPRailTranslator(writer));
		polyHandlers.add(new XPPowerlineTranslator(writer));
	}
	
	@Override
	public void init() {
		// writer initialization
		writer.init(currentTile);

	}
	
	@Override
	public void complete() {
		if (!StatsHelper.isTileEmpty(stats)) {
			Osm2xpLogger.info("stats : " + stats.getBuildingsNumber()
					+ " buildings, " + stats.getForestsNumber() + " forests, "
					+ stats.getStreetlightsNumber() + " street lights, "
					+ stats.getObjectsNumber() + " objects. (generation took "
					+ MiscUtils.getTimeDiff(startTime, new Date()) + ")");
			writer.complete(null);

			// stats
			try {
				if (XplaneOptionsHelper.getOptions().isGenerateXmlStats()
						|| XplaneOptionsHelper.getOptions()
								.isGeneratePdfStats()) {
					StatsHelper.getStatsList().add(stats);

				}
				if (XplaneOptionsHelper.getOptions().isGenerateXmlStats()) {
					StatsHelper.saveStats(folderPath, currentTile, stats);
				}
				if (XplaneOptionsHelper.getOptions().isGeneratePdfStats()) {
					StatsHelper.generatePdfReport(folderPath, stats);
				}
			} catch (Osm2xpBusinessException e) {
				Osm2xpLogger.error("Error saving stats file for tile "
						+ currentTile, e);
			}
		} else if (!GuiOptionsHelper.getOptions().isSinglePass()) {
			Osm2xpLogger.info("Tile " + (int) currentTile.y + "/"
					+ (int) currentTile.x + " is empty, no dsf generated");
		}
	}

	/**
	 * write a building in the dsf file.
	 * 
	 * @param polygon
	 * @param facade
	 * @param size
	 */
	protected void writeBuildingToDsf(OsmPolygon osmPolygon, Integer facade) {
		if (facade != null && osmPolygon.getHeight() != null) {
			StringBuffer sb = new StringBuffer();
			osmPolygon.setPolygon(GeomUtils.setClockwise(osmPolygon
					.getPolygon()));
			if (osmPolygon.getPolygon().getArea() * 100000000 > 0.1
					&& osmPolygon.getPolygon().getVertexNumber() > 3) {
	
				sb.append("# " + getBuildingComment(osmPolygon, facade));
				sb.append(LINE_SEP);
				sb.append("BEGIN_POLYGON " + facade + " "
						+ osmPolygon.getHeight() + " 2");
				sb.append(LINE_SEP);
				sb.append("BEGIN_WINDING");
				sb.append(LINE_SEP);
	
				// on supprime le dernier point pour ne pas boucler
				osmPolygon.getPolygon().removePoint(
						osmPolygon.getPolygon().getLastPoint());
				for (Point2D loc : osmPolygon.getPolygon().getVertices()) {
					sb.append("POLYGON_POINT " + loc.x + " " + loc.y);
					sb.append(LINE_SEP);
				}
				sb.append("END_WINDING");
				sb.append(LINE_SEP);
				sb.append("END_POLYGON");
				sb.append(LINE_SEP);
	
				// stats TODO not working anymore since v2 facades new features.
				if (dsfObjectsProvider.getPolygonsList().get(facade)
						.toLowerCase().contains("building")
						|| dsfObjectsProvider.getPolygonsList().get(facade)
								.toLowerCase().contains("shape")) {
					StatsHelper.addBuildingType("Building", stats);
				} else {
					if (dsfObjectsProvider.getPolygonsList().get(facade)
							.toLowerCase().contains("house")
							|| dsfObjectsProvider.getPolygonsList().get(facade)
									.toLowerCase().contains("common")) {
						StatsHelper.addBuildingType("Residential", stats);
					} else {
						StatsHelper.addBuildingType("Facade rule", stats);
					}
				}
				writer.write(sb.toString(), GeomUtils
						.cleanCoordinatePoint(osmPolygon.getPolygon()
								.getFirstPoint()));
			}
		}
	}

	protected String getBuildingComment(OsmPolygon osmPolygon, Integer facade) {
		String street = osmPolygon.getTagValue("addr:street");
		String name = osmPolygon.getTagValue("name");
		String number = osmPolygon.getTagValue("addr:housenumber");
		StringBuilder builder = new StringBuilder("building");
		
		if (name != null) {
			builder.append(" ");
			builder.append(name);
		}
		if (street != null) {
			builder.append(" ");
			builder.append(street);
		}
		if (number != null) {
			builder.append(" ");
			builder.append(number);
		}
		builder.append(" facade ");
		builder.append(facade);
		return builder.toString();
	}

	/**
	 * Compute the height for this polygon and osm tags.
	 * 
	 * 
	 * @return Integer the height.
	 */
	protected Integer computeBuildingHeight(OsmPolygon polygon) {
		Integer result = null;
		Integer osmHeight = polygon.getHeight();
		if (osmHeight != null) {
			result = osmHeight;
		} else {
			String value = polygon.getTagValue("building:levels");
			if (value != null) {
				try {
					int levels = Integer.parseInt(value);
					return (int) Math.round(levels * levelHeight);
				} catch (NumberFormatException e) {
					//Ignore. Should we log it?
				}
				
			}
			
			if (polygon.getArea() * 10000000 < 0.2) {
				result = XplaneOptionsHelper.getOptions().getResidentialMin();
			} else {
				if (polygon.getArea() * 10000000 > ASSERTION_RESIDENTIAL_MAX_AREA)
					result = MiscUtils.getRandomSize(XplaneOptionsHelper
							.getOptions().getBuildingMin(), XplaneOptionsHelper
							.getOptions().getBuildingMax());
				else if (polygon.getArea() * 10000000 < ASSERTION_RESIDENTIAL_MAX_AREA)
					result = MiscUtils.getRandomSize(XplaneOptionsHelper
							.getOptions().getResidentialMin(),
							XplaneOptionsHelper.getOptions()
									.getResidentialMax());
			}
		}
		return result;
	}	

	protected BuildingType getBuildingType(OsmPolygon polygon) {
		String typeStr = polygon.getTagValue("building");
		BuildingType type = BuildingType.fromId(typeStr);
		if (type != null) {
			return type;
		}
		if ("apartments".equals(typeStr)) {
			return BuildingType.RESIDENTIAL;
		}
		// first, do we are on a residential object?
		// yes if there is residential or house tag
		// or if surface of the polygon is under the max surface for a
		// residential house
		// and height is under max residential height
		if (OsmUtils.isValueinTags("residential", polygon.getTags())
				|| OsmUtils.isValueinTags("house", polygon.getTags())) {
			return BuildingType.RESIDENTIAL;
		}
		if (!StringUtils.stripToEmpty(polygon.getTagValue("shop")).isEmpty()) {
			return BuildingType.COMMERCIAL;
		}
		if (polygon.getArea() * 10000000 < ASSERTION_RESIDENTIAL_MAX_AREA
			&& polygon.getHeight() < XplaneOptionsHelper.getOptions()
					.getResidentialMax()) {
			return BuildingType.RESIDENTIAL;
		}
		// do we are on a building object?
		// yes if there is industrial or Commercial tag
		// or if surface of the polygon is above the max surface for a
		// residential house
		// and height is above max residential height
		if (OsmUtils.isValueinTags("industrial", polygon.getTags())
				|| OsmUtils.isValueinTags("Commercial", polygon.getTags())
				|| polygon.getArea() * 10000000 > ASSERTION_RESIDENTIAL_MAX_AREA
				|| polygon.getHeight() > XplaneOptionsHelper.getOptions()
						.getResidentialMax()) {
			return BuildingType.INDUSTRIAL;
		}
		return BuildingType.RESIDENTIAL;
	}
	
	/**
	 * Compute the facade index for given osm tags, polygon and height.
	 * 
	 * @param tags
	 * @param polygon
	 * @param height
	 * @return Integer the facade index.
	 */
	public Integer computeFacadeIndex(OsmPolygon polygon) {
		Integer result = null;
		// we check if we can use a sloped roof if the user wants them
		BuildingType buildingType = getBuildingType(polygon);
		if (XplaneOptionsHelper.getOptions().isGenerateSlopedRoofs()
				&& polygon.isSimplePolygon()) {
			result = dsfObjectsProvider.computeFacadeDsfIndex(true, buildingType, true,
					polygon);
		}
		// no sloped roof, so we'll use a standard house facade
		else {
	
			// if the polygon is a simple rectangle, we'll use a facade made for
			// simple shaped buildings
			if (polygon.getPolygon().getEdges().size() == 4) {
				result = dsfObjectsProvider.computeFacadeDsfIndex(true, buildingType,
						false, polygon);
			}
			// the building has a complex footprint, so we'll use a facade made
			// for this case
			else {
				result = dsfObjectsProvider.computeFacadeDsfIndex(false, buildingType,
						false, polygon);
			}
		}
		return result;
	}

	/**
	 * write a 3D object in the dsf file
	 * 
	 * @param object
	 *            a xplane dsf object
	 * @throws Osm2xpBusinessException
	 */
	protected void writeObjectToDsf(XplaneDsfObject object) throws Osm2xpBusinessException {
	
		String objectDsfText = object.asObjDsfText();
		writer.write(objectDsfText, GeomUtils.cleanCoordinatePoint(object
				.getOsmPolygon().getCenter()));
		// stats
		StatsHelper.addObjectType(
				dsfObjectsProvider.getObjectsList().get(object.getDsfIndex()),
				stats);
	
	}

	@Override
	public void processBoundingBox(HeaderBBox bbox) {
		if (bbox != null && GuiOptionsHelper.isUseExclusionsFromPBF()) {
			Box2D bboxRect = new Box2D(bbox.getLeft() / COORD_DIV_FACTOR,bbox.getRight() / COORD_DIV_FACTOR, bbox.getBottom() / COORD_DIV_FACTOR, bbox.getTop() / COORD_DIV_FACTOR);
			if (currentTile != null) {
				Box2D tileRect = new Box2D(currentTile, 1,1);
				dsfObjectsProvider.setExclusionBox(tileRect.intersection(bboxRect));
			} else {
				dsfObjectsProvider.setExclusionBox(bboxRect);
			}
		}
	}

	@Override
	public void processNode(Node node) throws Osm2xpBusinessException {
		// process the node if we're on a single pass mode.
		// if not on single pass, only process if the node is on the current
		// lat/long tile
		if (XplaneOptionsHelper.getOptions().isGenerateObj()) {
			if ((!GuiOptionsHelper.getOptions().isSinglePass() && GeomUtils
					.compareCoordinates(currentTile, node))
					|| GuiOptionsHelper.getOptions().isSinglePass()) {
				// write a 3D object in the dsf file if this node is in an
				// object
				// rule
				XplaneDsf3DObject object = dsfObjectsProvider
						.getRandomDsfObjectIndexAndAngle(node.getTag(),
								node.getId());
				if (object != null) {
					List<Node> nodes = new ArrayList<Node>();
					nodes.add(node);
					object.setPolygon(new OsmPolygon(node.getId(), node
							.getTag(), nodes, false));
					writeObjectToDsf(object);
				}
			}
		}
	}

	@Override
	public void processPolygon(OsmPolygon osmPolygon) throws Osm2xpBusinessException {
	
		// polygon is null or empty don't process it
		if (osmPolygon.getNodes() != null && !osmPolygon.getNodes().isEmpty()) {
			// polygon MUST be in clockwise order
			osmPolygon.setPolygon(GeomUtils.forceClockwise(osmPolygon
					.getPolygon()));
			// if we're on a single pass mode
			// here we must check if the polygon is on more than one tile
			// if that's the case , we must split it into several polys
			List<OsmPolygon> polygons = new ArrayList<OsmPolygon>();
			if (GuiOptionsHelper.getOptions().isSinglePass()) {
				polygons.addAll(osmPolygon.splitPolygonAroundTiles());
			}
			// if not on a single pass mode, add this single polygon to the poly
			// list
			else {
				polygons.add(osmPolygon);
			}
			// try to transform those polygons into dsf objects.
			for (OsmPolygon poly : polygons) {
				// look for light rules
				processLightObject(poly);
	
				// try to generate a 3D object
				if (!process3dObject(poly)) {
					// nothing generated? try to generate a facade building.
					if (!processBuilding(poly)) {
						// nothing generated? try to generate a forest.
						if (!processForest(poly)) {
							processOther(poly);
						}
					}
				}
			}
		}
	}
	
	/**
	 * choose and write a 3D object in the dsf file.
	 * 
	 * @param polygon
	 *            osm polygon.
	 * @return true if a 3D object has been written in the dsf file.
	 */
	protected boolean process3dObject(OsmPolygon osmPolygon) {
		Boolean result = false;

		if (osmPolygon.isPartial()) {
			return false;
		}
		
		if (XplaneOptionsHelper.getOptions().isGenerateObj()) {
			// simplify shape if checked and if necessary
			if (GuiOptionsHelper.getOptions().isSimplifyShapes()
					&& !osmPolygon.isSimplePolygon()) {
				osmPolygon.simplifyPolygon();
			}
			XplaneDsfObject object = dsfObjectsProvider
					.getRandomDsfObject(osmPolygon);
			if (object != null) {
				object.setPolygon(osmPolygon);
				try {
					writeObjectToDsf(object);
					result = true;
				} catch (Osm2xpBusinessException e) {
					result = false;
				}

			}
		}
		return result;
	}
	
	/**
	 * Construct and write a facade building in the dsf file.
	 * 
	 * @param osmPolygon
	 *            osm polygon
	 * @return true if a building has been gennerated in the dsf file.
	 */
	protected boolean processBuilding(OsmPolygon osmPolygon) {
		Boolean result = false;
		if (XplaneOptionsHelper.getOptions().isGenerateBuildings()
				&& OsmUtils.isBuilding(osmPolygon.getTags())
				&& !OsmUtils.isExcluded(osmPolygon.getTags(),
						osmPolygon.getId())
				&& !specialExcluded(osmPolygon)
				&& !osmPolygon.isPartial()
				&& osmPolygon.getPolygon().getVertexNumber() > BUILDING_MIN_VECTORS
				&& osmPolygon.getPolygon().getVertexNumber() < BUILDING_MAX_VECTORS) {

			// check that the largest vector of the building
			// and that the area of the osmPolygon.getPolygon() are over the
			// minimum values set by the user
			Double maxVector = osmPolygon.getMaxVectorSize();
			if (maxVector > XplaneOptionsHelper.getOptions()
					.getMinHouseSegment()
					&& maxVector < XplaneOptionsHelper.getOptions()
							.getMaxHouseSegment()
					&& ((osmPolygon.getPolygon().getArea() * 100000) * 100000) > XplaneOptionsHelper
							.getOptions().getMinHouseArea()) {

				// simplify shape if checked and if necessary
				if (GuiOptionsHelper.getOptions().isSimplifyShapes()
						&& !osmPolygon.isSimplePolygon()) {
					osmPolygon.simplifyPolygon();
				}

				// compute height and facade dsf index
				osmPolygon.setHeight(computeBuildingHeight(osmPolygon));
				Integer facade = computeFacadeIndex(osmPolygon);
				// write building in dsf file
				writeBuildingToDsf(osmPolygon, facade);
				result = true;
			}
		}
		return result;
	}

	//Avoid generating a bunch of little garages //TODO rewrite this in more generic way in future
	protected boolean specialExcluded(OsmPolygon osmPolygon) {
		if ("garage".equals(osmPolygon.getTagValue("building")) && GeomUtils.computePerimeter(osmPolygon.getPolygon()) < 30) {
			return true;
		}
		return false;
	}

	protected boolean processOther(OsmPolygon poly) {
		for (IPolyHandler handler : polyHandlers) {
			if (handler.handlePoly(poly)) {
				if (translationListener != null) {
					translationListener.polyProcessed(poly, handler);
				}
				return true;
			}
		}
		return false;
	}

	private void processLightObject(OsmPolygon poly) {
		if (XplaneOptionsHelper.getOptions().isGenerateLights()) {
			XplaneDsfObject object = dsfObjectsProvider
					.getRandomDsfLightObject(poly);
			if (object != null) {
				object.setPolygon(poly);
				try {
					writeObjectToDsf(object);
	
				} catch (Osm2xpBusinessException e) {
				}
			}
		}
	}

	

	/**
	 * @param way
	 * @param polygon
	 * @return
	 */
	protected boolean processForest(OsmPolygon osmPolygon) {
		Boolean result = false;
		if (XplaneOptionsHelper.getOptions().isGenerateFor()) {
			Integer[] forestIndexAndDensity = dsfObjectsProvider
					.getRandomForestIndexAndDensity(osmPolygon.getTags());
			if (forestIndexAndDensity != null) {
				if (translationListener != null) {
					translationListener.processForest(osmPolygon);
				}
				writeForestToDsf(osmPolygon, forestIndexAndDensity);
				result = true;
			}
		}
		return result;
	}

	/**
	 * @param polygon
	 *            the forest polygon
	 * @param forestIndexAndDensity
	 *            index and density of the forest rule
	 */
	private void writeForestToDsf(OsmPolygon osmPolygon, Integer[] forestIndexAndDensity) {
		StringBuffer sb = new StringBuffer();
		sb.append("BEGIN_POLYGON " + forestIndexAndDensity[0] + " "
				+ forestIndexAndDensity[1] + " 2");
		sb.append(LINE_SEP);
		sb.append("BEGIN_WINDING");
		sb.append(LINE_SEP);
		for (Point2D loc : osmPolygon.getPolygon().getVertices()) {
			sb.append("POLYGON_POINT " + loc.x + " " + loc.y);
			sb.append(LINE_SEP);
		}
		sb.append("END_WINDING");
		sb.append(LINE_SEP);
		sb.append("END_POLYGON");
		sb.append(LINE_SEP);
	
		// stats
		StatsHelper.addForestType(
				dsfObjectsProvider.getPolygonsList().get(
						forestIndexAndDensity[0]), stats);
	
		writer.write(sb.toString(), GeomUtils.cleanCoordinatePoint(osmPolygon
				.getPolygon().getFirstPoint()));
	}

	@Override
	public void processRelation(Relation relation) throws Osm2xpBusinessException {
	}

	@Override
	public Boolean mustStoreNode(Node node) {
		Boolean result = true;
		if (!GuiOptionsHelper.getOptions().isSinglePass()) {
			result = GeomUtils.compareCoordinates(currentTile, node);
		}
		return result;
	}

	@Override
	public Boolean mustStoreWay(Way way) {
		List<Tag> tags = way.getTag();
		return (OsmUtils.isBuilding(tags) || OsmUtils.isForest(tags) || OsmUtils
				.isObject(tags) || OsmUtils.isRailway(tags) || OsmUtils.isRoad(tags) || OsmUtils.isPowerline(tags) || OsmUtils.isFence(tags));
	}

	public void setTranslationListener(ITranslationListener translationListener) {
		this.translationListener = translationListener;
	}

}