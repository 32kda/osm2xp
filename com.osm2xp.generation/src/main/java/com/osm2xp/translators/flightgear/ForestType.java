package com.osm2xp.translators.flightgear;

import java.util.List;

import com.osm2xp.core.model.osm.Tag;

/**
 * Vegetation class derived from OSM tags, mapped to a FlightGear land-class
 * material.
 *
 * @author osm2xp
 */
public enum ForestType {

	CONIFER(FlightGearForestMaterials.CONIFER),
	BROADLEAF(FlightGearForestMaterials.BROADLEAF),
	MIXED(FlightGearForestMaterials.MIXED);

	private final String material;

	ForestType(String material) {
		this.material = material;
	}

	public String getMaterial() {
		return material;
	}

	/** Derives the forest type from {@code leaf_type}/{@code wood}/{@code type} tags. */
	public static ForestType fromTags(List<Tag> tags) {
		String leafType = tagValue(tags, "leaf_type");
		if (leafType != null) {
			if (leafType.contains("needle")) {
				return CONIFER;
			}
			if (leafType.contains("broad")) {
				return BROADLEAF;
			}
			if (leafType.contains("mixed")) {
				return MIXED;
			}
		}
		String wood = tagValue(tags, "wood");
		if (wood != null) {
			if (wood.contains("conifer") || wood.contains("needle")) {
				return CONIFER;
			}
			if (wood.contains("deciduous") || wood.contains("broad")) {
				return BROADLEAF;
			}
			if (wood.contains("mixed")) {
				return MIXED;
			}
		}
		return MIXED;
	}

	private static String tagValue(List<Tag> tags, String key) {
		if (tags == null) {
			return null;
		}
		for (Tag tag : tags) {
			if (key.equalsIgnoreCase(tag.getKey())) {
				String value = tag.getValue();
				return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
			}
		}
		return null;
	}
}
