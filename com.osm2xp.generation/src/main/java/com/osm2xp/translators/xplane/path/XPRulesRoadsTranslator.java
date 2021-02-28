package com.osm2xp.translators.xplane.path;

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
import com.osm2xp.translators.xplane.IDRenumbererService;
import com.osm2xp.translators.xplane.IXPLightTranslator;
import com.osm2xp.translators.xplane.XPStringLightTranslator;
import com.osm2xp.writers.IWriter;
import com.osm2xp.xplane.customrules.PathRulesProvider;
import com.osm2xp.xplane.customrules.PathRulesProvider.PathOptionsType;

public class XPRulesRoadsTranslator extends XPRulesPathTranslator {
	
	private static final String[] WIDE_ROAD_TYPES = {"motorway", "trunk"}; 
	private static final String HIGHWAY_TAG = "highway";

	private PathOptionsType roadsType;
	private IXPLightTranslator lightTranslator;
	private boolean city;

	public XPRulesRoadsTranslator(IWriter writer, DsfObjectsProvider dsfObjectsProvider, XPOutputFormat outputFormat, IDRenumbererService idProvider, boolean city) {
		super(writer, outputFormat, idProvider, PathRulesProvider.getRulesList(city ? PathOptionsType.ROADS_CITY : PathOptionsType.ROADS_COUNTRY));
		this.city = city;
		lightTranslator = new XPStringLightTranslator(writer, dsfObjectsProvider, outputFormat);
		this.roadsType = city ? PathOptionsType.ROADS_CITY : PathOptionsType.ROADS_COUNTRY;
	}
	
	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		if (!XPlaneOptionsProvider.getOptions().isGenerateRoads()) {
			return false;
		}
		if (this.city != isInCity(osmPolyline)) {
			return false;
		}
		int pathType = getPathType(osmPolyline);
		if (pathType > 0) {
			addSegmentsFrom(osmPolyline);
			return true;
		}
		return false;
	}
	
	protected boolean isInCity(IHasTags poly) {
		String landuse = poly.getTagValue(OsmConstants.LANDUSE_TAG);
		return "industrial".equalsIgnoreCase(landuse) || "residential".equalsIgnoreCase(landuse) || "commercial".equalsIgnoreCase(landuse);
	}
	
	@Override
	public String getId() {
		return "Roads, rules-based(" + roadsType + ")";
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

}
