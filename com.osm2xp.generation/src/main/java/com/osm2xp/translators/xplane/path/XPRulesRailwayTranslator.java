package com.osm2xp.translators.xplane.path;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.translators.xplane.IDRenumbererService;
import com.osm2xp.writers.IWriter;
import com.osm2xp.xplane.customrules.PathRulesProvider;
import com.osm2xp.xplane.customrules.PathRulesProvider.PathOptionsType;

public class XPRulesRailwayTranslator extends XPRulesPathTranslator {

	private PathOptionsType railwayType;

	public XPRulesRailwayTranslator(IWriter writer, XPOutputFormat outputFormat, IDRenumbererService idProvider, PathOptionsType railwayType) {
		super(writer, outputFormat, idProvider, PathRulesProvider.getRulesList(railwayType));
		this.railwayType = railwayType;
	}
	
	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		if (!XPlaneOptionsProvider.getOptions().isGenerateRailways()) {
			return false;
		}
		int pathType = getPathType(osmPolyline);
		if (pathType > 0) {
			addSegmentsFrom(osmPolyline);
		}
		return false;
	}
	
	@Override
	public String getId() {
		return "Railway, rules-based(" + railwayType + ")";
	}
	

}
