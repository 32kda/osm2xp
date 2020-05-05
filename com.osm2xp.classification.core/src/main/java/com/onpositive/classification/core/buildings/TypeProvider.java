package com.onpositive.classification.core.buildings;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.onpositive.classification.core.util.TagUtil;
import com.osm2xp.core.model.osm.Tag;

public class TypeProvider {
	public static OSMBuildingType getBuildingType(List<Tag> tags) {
		String shop = TagUtil.getValue("shop", tags);
		if ("department_store".equals(shop) || "mall".equals(shop)) {
			return OSMBuildingType.MALL;
		}
		if (!StringUtils.isEmpty(shop)) {
			return OSMBuildingType.SHOP;
		}		
		String building = TagUtil.getValue("building", tags);
		OSMBuildingType type = getFromBuildingTag(tags, building);
		if (type == null) {
			String buildingUse = TagUtil.getValue("building:use", tags);
			type = getFromBuildingTag(tags, buildingUse);
		}
		if (type != null) {
			return type;
		}
		String amenity = TagUtil.getValue("amenity", tags);
		if (amenity != null && amenity.endsWith("school") || "college".equals(amenity)) {
			return OSMBuildingType.SOCIAL;
		}
		if ("university".equals(amenity)) {
			return OSMBuildingType.SOCIAL;
		}
		if ("clinic".equals(amenity) || "hospital".equals(amenity) || 
			"dentist".equals(amenity) || "doctors".equals(amenity)) {
			return OSMBuildingType.SOCIAL;
		}
		if (building != null) {
			String residential = TagUtil.getValue("residential", tags);
			if ("rural".equals(residential)) {
				return OSMBuildingType.HOUSE;
			} else if (residential != null) {
				return OSMBuildingType.BLOCK;
			}
			String landuse = TagUtil.getValue("landuse", tags); 
			if ("allotments".equals(landuse)) {
				return OSMBuildingType.HOUSE;
			}
			if ("industrial".equals(landuse)) {
				return OSMBuildingType.INDUSTRIAL;
			}
			if ("residential".equals(landuse) || "apartments".equals(landuse)) {
				return OSMBuildingType.BLOCK;
			}
			if ("commercial".equals(landuse)) {
				return OSMBuildingType.SHOP;
			}
		}
		return type;
	}

	protected static OSMBuildingType getFromBuildingTag(List<Tag> tags, String building) {
		if ("hospital".equals(building)) {
			return OSMBuildingType.SOCIAL;
		}
		if ("school".equals(building)) {
			return OSMBuildingType.SOCIAL;
		}
		if ("retail".equals(building) || "supermarket".equals(building)) {
			return OSMBuildingType.SHOP;
		}
		if ("university".equals(building)) {
			return OSMBuildingType.SOCIAL;
		}
		if (building != null && building.startsWith("garage")) {
			return OSMBuildingType.GARAGE;
		}
		if ("industrial".equals(building)) {
			return OSMBuildingType.INDUSTRIAL;
		}
		if ("residential".equals(building) || "apartments".equals(building)) {
			return OSMBuildingType.BLOCK;
		}
		if ("house".equals(building) || "detached".equals(building)) {
			return OSMBuildingType.HOUSE;
		}
		if ("commercial".equals(building)) {
			return OSMBuildingType.SHOP;
		}
		return null;
	}
	
	public static boolean isBuilding(List<Tag> tags) {
		return TagUtil.getValue("building", tags) != null;
	}
	
}
