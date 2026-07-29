package com.osm2xp.translators;

import org.apache.commons.lang.StringUtils;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.osm.OsmConstants;
import com.osm2xp.model.facades.SpecialFacadeType;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.utils.geometry.GeomUtils;
import com.osm2xp.utils.osm.OsmUtils;

public class BuildingClassifier {

	private static final String BUILDING_TAG = "building";
	private static final double ASSERTION_RESIDENTIAL_MAX_AREA = 0.5;

	public BuildingType getBuildingType(OsmPolygon polygon) {
		String typeStr = polygon.getTagValue(BUILDING_TAG);
		BuildingType type = BuildingType.fromId(typeStr);
		if (type != null) {
			return type;
		}
		if ("apartments".equals(typeStr)) {
			return BuildingType.RESIDENTIAL;
		}
		if (OsmUtils.isValueInTags("residential", polygon.getTags())
				|| OsmUtils.isValueInTags("house", polygon.getTags())) {
			return BuildingType.RESIDENTIAL;
		}
		if (!StringUtils.stripToEmpty(polygon.getTagValue("shop")).isEmpty()) {
			return BuildingType.COMMERCIAL;
		}
		if (typeStr != null) {
			String landuse = polygon.getTagValue(OsmConstants.LANDUSE_TAG);
			if (!StringUtils.stripToEmpty(landuse).isEmpty()) {
				type = BuildingType.fromId(landuse);
				if (type != null) {
					return type;
				}
			}
			if ("retail".equals(landuse)) {
				return BuildingType.COMMERCIAL;
			}
			if ("allotments".equals(landuse)) {
				return BuildingType.RESIDENTIAL;
			}
			if ("railway".equals(landuse)) {
				return BuildingType.INDUSTRIAL;
			}
			if (polygon.getArea() * 10000000 < ASSERTION_RESIDENTIAL_MAX_AREA
				&& polygon.getHeight() < XPlaneOptionsProvider.getOptions()
						.getResidentialMax()) {
				return BuildingType.RESIDENTIAL;
			}
		}
		if (OsmUtils.isValueInTags("industrial", polygon.getTags())
				|| OsmUtils.isValueInTags("commercial", polygon.getTags())
				|| polygon.getArea() * 10000000 > ASSERTION_RESIDENTIAL_MAX_AREA
				|| polygon.getHeight() > XPlaneOptionsProvider.getOptions()
						.getResidentialMax()) {
			return BuildingType.INDUSTRIAL;
		}
		return BuildingType.RESIDENTIAL;
	}

	public int tryGetHeightByType(OsmPolygon polygon, double levelHeight) {
		SpecialFacadeType specialType = getSpecialBuildingType(polygon);
		if (specialType == SpecialFacadeType.GARAGE) {
			return (int) Math.round(levelHeight);
		} else if (specialType == SpecialFacadeType.TANK) {
			double length = GeomUtils.computeEdgesLength(polygon.getPolyline());
			int diameter = (int) Math.round(length / Math.PI);
			if ("gasometer".equalsIgnoreCase(polygon.getTagValue(OsmConstants.MAN_MADE_TAG))) {
				return diameter * 2;
			}
			return diameter;
		}
		return 0;
	}

	public SpecialFacadeType getSpecialBuildingType(OsmPolygon polygon) {
		if (XPlaneOptionsProvider.getOptions().isGenerateTanks()) {
			String manMade = polygon.getTagValue(OsmConstants.MAN_MADE_TAG);
			if ("storage_tank".equals(manMade) || "fuel_storage_tank".equals(manMade) || "gasometer".equals(manMade)) {
				return SpecialFacadeType.TANK;
			}
		}
		if ("garages".equals(polygon.getTagValue(BUILDING_TAG)) || "garage".equals(polygon.getTagValue(BUILDING_TAG))) {
			return SpecialFacadeType.GARAGE;
		}
		return null;
	}

}
