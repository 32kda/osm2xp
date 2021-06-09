package com.osm2xp.translators.xplane.path;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.translators.xplane.IDRenumbererService;
import com.osm2xp.writers.IWriter;
import com.osm2xp.xplane.customrules.PathRulesProvider;
import com.osm2xp.xplane.customrules.PathRulesProvider.PathOptionsType;

public class XPRulesRailwayTranslator extends XPRulesPathTranslator {

	public XPRulesRailwayTranslator(IWriter writer, IDRenumbererService idProvider, XPOutputFormat outputFormat) {
		super(writer, outputFormat, idProvider, PathRulesProvider.getRulesList(PathOptionsType.RAILWAYS));
	}
	
	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		if (!XPlaneOptionsProvider.getOptions().isGenerateRailways() || osmPolyline.getTagValue("railway") == null) {
			return false;
		}		
		int pathType = getPathType(osmPolyline);
		if (pathType > 0) {
			addSegmentsFrom(osmPolyline);
			return true;
		}
		return false;
	}
	
	@Override
	public String getId() {
		return "Railway, rules-based";
	}
	

}
