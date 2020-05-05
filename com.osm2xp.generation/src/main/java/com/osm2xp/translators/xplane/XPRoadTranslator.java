package com.osm2xp.translators.xplane;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.options.XplaneOptions;
import com.osm2xp.generation.osm.OsmConstants;
import com.osm2xp.generation.xplane.resources.DsfObjectsProvider;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.writers.IWriter;

public class XPRoadTranslator extends XPPathTranslator {
	
//	private static final String[] WIDE_ROAD_TYPES = {"motorway", "trunk", "primary", "secondary"}; 
	private static final String[] WIDE_ROAD_TYPES = {"motorway", "trunk"}; 
	private static final String HIGHWAY_TAG = "highway";
	private String[] allowedHighwayTypes = GlobalOptionsProvider.getOptions().getAllowedHighwayTypesArray();
	private String[] allowedHighwayLinkTypes = GlobalOptionsProvider.getOptions().getAllowedHighwayLinkTypesArray();
	private String[] allowedHighwaySurfaceTypes = GlobalOptionsProvider.getOptions().getAllowedHighwaySurfaceTypesArray();
	private String[] disallowedHighwayTags = GlobalOptionsProvider.getOptions().getDisallowedHighwayTagsArray();
	private IXPLightTranslator lightTranslator;
	
	public XPRoadTranslator(IWriter writer, DsfObjectsProvider dsfObjectsProvider, IDRenumbererService idProvider, XPOutputFormat outputFormat) {
		super(writer, outputFormat, idProvider);
		lightTranslator = new XPStringLightTranslator(writer, dsfObjectsProvider, outputFormat);
	}

	@Override
	public boolean handlePoly(OsmPolyline poly) {
		XplaneOptions options = XPlaneOptionsProvider.getOptions();
		if (!options.isGenerateRoads()) {
			return false;
		}
		String highwayValue = poly.getTagValue(HIGHWAY_TAG);
		if ((ArrayUtils.contains(allowedHighwayTypes, highwayValue) || ArrayUtils.contains(allowedHighwayLinkTypes, highwayValue)) && !disallowedLine(poly) ) {
			String surface = poly.getTagValue("surface"); //Generate if surface type is either missing or among allowed values
			if (StringUtils.stripToEmpty(surface).trim().isEmpty() || ArrayUtils.contains(allowedHighwaySurfaceTypes, surface)) {
				addSegmentsFrom(poly);
				if (options.isGenerateStreetLights()) {
					processLights(poly);
				}
				return true;
			}
		}
		return false;
	}

	protected void processLights(OsmPolyline poly) {
		String type = StringUtils.stripToEmpty(poly.getTagValue(HIGHWAY_TAG)).toLowerCase(); //If no lane count - guess from the highway type
		boolean highway = (ArrayUtils.indexOf(WIDE_ROAD_TYPES, type) >= 0);
		String lit = StringUtils.stripToEmpty(poly.getTagValue("lit")).toLowerCase();
		int lanesCount = getLanesCount(poly);
		XplaneOptions options = XPlaneOptionsProvider.getOptions();
		boolean hasLight = highway && options.isGenerateHighwayLights() ||
						   !lit.isEmpty() && !"no".equals(lit) ||
						   lanesCount >= 3;
		if (hasLight) {
			boolean doubleSided = (highway && lanesCount >= 2) || lanesCount >= 3;
			lightTranslator.writeLightStrings(poly.getPolyline(), lanesCount * GlobalOptionsProvider.getOptions().getRoadLaneWidth() / 2 * 1.1, doubleSided); //TODO 1.1 is manually selected coef
		}
	}

	protected boolean disallowedLine(OsmPolyline poly) {
		for (String tagName : disallowedHighwayTags) {
			if (!StringUtils.isEmpty(poly.getTagValue(tagName))) {
				return true;
			}
		}
		return false;
	}
	
	protected int getLanesCount(IHasTags roadPoly) {
		String lanes = roadPoly.getTagValue("lanes"); //Try to get lane count directly first
		if (lanes != null) {
			try {
				int value = Integer.parseInt(lanes.trim());
				return Math.max(0, Math.min(value, 4));
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		if ("yes".equalsIgnoreCase(roadPoly.getTagValue("oneWay"))) {
			return 1;
		}
		return 2;		
	}

	/**
	 * Return X-Plane type constant for given road. Written in "hard" way to make some match between OSM tags and X-Plane type constants
	 * From https://forums.x-plane.org/index.php?/files/file/19074-roads-tutorial/ :
	 */
	protected int getPathType(IHasTags poly) {
		String lanes = poly.getTagValue("lanes"); //Try to get lane count directly first
		String type = poly.getTagValue(HIGHWAY_TAG).toLowerCase(); //If no lane count - guess from the highway type
		boolean highway = (ArrayUtils.indexOf(WIDE_ROAD_TYPES, type) >= 0);
		boolean city = isInCity(poly);
		XplaneOptions options = XPlaneOptionsProvider.getOptions();
		if (lanes != null) {
			try {
				int value = Integer.parseInt(lanes.trim());
				if (value >= 3) {
					return city ? options.getCity3LaneHighwayRoadType() : options.getCountry3LaneHighwayRoadType();
				} else if (value == 2 && highway) {
					return city ? options.getCity2LaneHighwayRoadType() : options.getCountry2LaneHighwayRoadType();
				} else {
					return city ? options.getCityRoadType() : options.getCountryRoadType();
				}
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		if (highway) {
			return city ? options.getCity2LaneHighwayRoadType() : options.getCountry2LaneHighwayRoadType();
		}
		if ("yes".equalsIgnoreCase(poly.getTagValue("oneWay"))) {
			return options.getOneLaneRoadType(); //One-way road is treated as one-lane, if lane count is unspecified, and we doesn't have "wide" road	
		}
		return options.getCountryRoadType(); //Use 2 lanes road by default
	}
	
	protected boolean isInCity(IHasTags poly) {
		String landuse = poly.getTagValue(OsmConstants.LANDUSE_TAG);
		return "industrial".equalsIgnoreCase(landuse) || "residential".equalsIgnoreCase(landuse) || "commercial".equalsIgnoreCase(landuse);
	}

	@Override
	protected int getBridgeRampLength() {
		return XPlaneOptionsProvider.getOptions().getRoadBridgeRampLen();
	}
	
	@Override
	public String getId() {
		return "road";
	}
}
