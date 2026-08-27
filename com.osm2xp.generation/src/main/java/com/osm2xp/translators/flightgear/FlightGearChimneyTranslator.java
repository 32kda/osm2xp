package com.osm2xp.translators.flightgear;

import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.generation.osm.OsmConstants;
import com.osm2xp.model.osm.polygon.OsmPolygon;

public class FlightGearChimneyTranslator extends FlightGearSpecObjectTranslator {

	@Override
	protected boolean canProcess(OsmPolygon osmPolygon) {
		return FlightGearOptionsProvider.getOptions().isGenerateChimneys() &&
				"chimney".equalsIgnoreCase(osmPolygon.getTagValue(OsmConstants.MAN_MADE_TAG));
	}

	@Override
	protected int getObjectSize(OsmPolygon osmPolygon) {
		int height = osmPolygon.getHeight();
		if (height == 0) {
			return 50; //Default value
		}
		return height;
	}

	@Override
	protected String getObjectFilePreffix() {
		return "chimney";
	}

	@Override
	protected boolean generationEnabled() {
		return FlightGearOptionsProvider.getOptions().isGenerateChimneys();
	}

	@Override
	public String getId() {
		return "chimney";
	}

}
