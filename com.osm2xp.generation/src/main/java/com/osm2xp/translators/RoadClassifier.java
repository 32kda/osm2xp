package com.osm2xp.translators;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.generation.options.XplaneOptions;
import com.osm2xp.generation.osm.OsmConstants;

public class RoadClassifier {

	private static final String[] WIDE_ROAD_TYPES = {"motorway", "trunk"};
	private static final String HIGHWAY_TAG = "highway";

	private final XplaneOptions options;
	private final String[] allowedHighwayTypes;
	private final String[] allowedHighwayLinkTypes;
	private final String[] allowedHighwaySurfaceTypes;
	private final String[] disallowedHighwayTags;

	public RoadClassifier(XplaneOptions options) {
		this.options = options;
		this.allowedHighwayTypes = GlobalOptionsProvider.getOptions().getAllowedHighwayTypesArray();
		this.allowedHighwayLinkTypes = GlobalOptionsProvider.getOptions().getAllowedHighwayLinkTypesArray();
		this.allowedHighwaySurfaceTypes = GlobalOptionsProvider.getOptions().getAllowedHighwaySurfaceTypesArray();
		this.disallowedHighwayTags = GlobalOptionsProvider.getOptions().getDisallowedHighwayTagsArray();
	}

	public boolean isRoad(String highwayValue) {
		return ArrayUtils.contains(allowedHighwayTypes, highwayValue)
				|| ArrayUtils.contains(allowedHighwayLinkTypes, highwayValue);
	}

	public boolean isAllowedSurface(String surface) {
		return StringUtils.stripToEmpty(surface).trim().isEmpty()
				|| ArrayUtils.contains(allowedHighwaySurfaceTypes, surface);
	}

	public boolean isDisallowedLine(IHasTags poly) {
		for (String tagName : disallowedHighwayTags) {
			if (!StringUtils.isEmpty(poly.getTagValue(tagName))) {
				return true;
			}
		}
		return false;
	}

	public int getLanesCount(IHasTags roadPoly) {
		String lanes = roadPoly.getTagValue("lanes");
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

	public int getPathType(IHasTags poly) {
		String lanes = poly.getTagValue("lanes");
		String type = poly.getTagValue(HIGHWAY_TAG).toLowerCase();
		boolean highway = ArrayUtils.indexOf(WIDE_ROAD_TYPES, type) >= 0;
		boolean city = isInCity(poly);
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
			return options.getOneLaneRoadType();
		}
		return options.getCountryRoadType();
	}

	public boolean isInCity(IHasTags poly) {
		String landuse = poly.getTagValue(OsmConstants.LANDUSE_TAG);
		return "industrial".equalsIgnoreCase(landuse)
				|| "residential".equalsIgnoreCase(landuse)
				|| "commercial".equalsIgnoreCase(landuse);
	}

	public boolean needsLights(IHasTags poly) {
		String type = StringUtils.stripToEmpty(poly.getTagValue(HIGHWAY_TAG)).toLowerCase();
		boolean highway = ArrayUtils.indexOf(WIDE_ROAD_TYPES, type) >= 0;
		String lit = StringUtils.stripToEmpty(poly.getTagValue("lit")).toLowerCase();
		int lanesCount = getLanesCount(poly);
		return (highway && options.isGenerateHighwayLights())
				|| (!lit.isEmpty() && !"no".equals(lit))
				|| lanesCount >= 3;
	}

	public boolean isWideRoad(String type) {
		return ArrayUtils.indexOf(WIDE_ROAD_TYPES, type) >= 0;
	}

}
