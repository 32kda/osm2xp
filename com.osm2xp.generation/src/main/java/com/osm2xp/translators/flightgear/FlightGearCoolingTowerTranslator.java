package com.osm2xp.translators.flightgear;

import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.generation.osm.OsmConstants;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.utils.geometry.GeomUtils;

public class FlightGearCoolingTowerTranslator extends FlightGearSpecObjectTranslator {

	private static final int MIN_TOWER_DIAMETER = 30;

	@Override
	protected boolean canProcess(OsmPolygon osmPolygon) {
		return FlightGearOptionsProvider.getOptions().isGenerateCoolingTowers() &&
				("cooling_tower".equalsIgnoreCase(osmPolygon.getTagValue(OsmConstants.MAN_MADE_TAG))
				|| "cooling".equalsIgnoreCase(osmPolygon.getTagValue("tower:type")));
	}

	@Override
	protected int getObjectSize(OsmPolygon osmPolygon) {
		double length = GeomUtils.computeEdgesLength(osmPolygon.getPolygon());
		int diameter = (int) Math.round(length / Math.PI);
		if (diameter < MIN_TOWER_DIAMETER) {
			return -1;
		}
		return diameter;
	}

	@Override
	protected String getObjectFilePreffix() {
		return "cooling_tower";
	}

	@Override
	protected boolean generationEnabled() {
		return FlightGearOptionsProvider.getOptions().isGenerateCoolingTowers();
	}

	@Override
	public String getId() {
		return "cooling_tower";
	}

}
