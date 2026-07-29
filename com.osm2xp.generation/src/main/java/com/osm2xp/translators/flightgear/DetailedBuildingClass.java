package com.osm2xp.translators.flightgear;

import java.util.List;
import com.osm2xp.core.model.osm.Tag;

public enum DetailedBuildingClass {
	RESIDENTIAL,
	RESIDENTIAL_SMALL,
	APARTMENTS,
	INDUSTRIAL,
	WAREHOUSE,
	COMMERCIAL,
	OTHER;

	public static DetailedBuildingClass classify(List<Tag> tags) {
		String building = null;
		String landuse = null;
		for (Tag tag : tags) {
			if ("building".equalsIgnoreCase(tag.getKey())) {
				building = tag.getValue();
			} else if ("landuse".equalsIgnoreCase(tag.getKey())) {
				landuse = tag.getValue();
			}
		}

		if (building != null) {
			switch (building.toLowerCase()) {
				case "apartments":
					return APARTMENTS;
				case "residential":
					return RESIDENTIAL;
				case "house":
				case "detached":
				case "semidetached_house":
				case "terrace":
					return RESIDENTIAL_SMALL;
				case "industrial":
				case "manufacturing":
					return INDUSTRIAL;
				case "warehouse":
					return WAREHOUSE;
				case "commercial":
				case "retail":
				case "public":
				case "hotel":
				case "school":
				case "university":
				case "hospital":
				case "civic":
					return COMMERCIAL;
			}
		}

		if (landuse != null) {
			switch (landuse.toLowerCase()) {
				case "industrial":
				case "railway":
					return INDUSTRIAL;
				case "commercial":
				case "retail":
					return COMMERCIAL;
				case "residential":
					return RESIDENTIAL;
			}
		}

		return OTHER;
	}
}
