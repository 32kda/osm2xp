package com.osm2xp.translators.xplane;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.options.XplaneOptions;
import com.osm2xp.generation.xplane.resources.DsfObjectsProvider;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.translators.RoadClassifier;
import com.osm2xp.writers.IWriter;

public class XPRoadTranslator extends XPPathTranslator {

	private static final String HIGHWAY_TAG = "highway";
	private static final String[] WIDE_ROAD_TYPES = {"motorway", "trunk"};
	private IXPLightTranslator lightTranslator;
	private RoadClassifier roadClassifier;

	public XPRoadTranslator(IWriter writer, DsfObjectsProvider dsfObjectsProvider,
			IDRenumbererService idProvider, XPOutputFormat outputFormat) {
		this(writer, dsfObjectsProvider, idProvider, outputFormat, XPlaneOptionsProvider.getOptions());
	}

	public XPRoadTranslator(IWriter writer, DsfObjectsProvider dsfObjectsProvider,
			IDRenumbererService idProvider, XPOutputFormat outputFormat, XplaneOptions options) {
		super(writer, outputFormat, idProvider);
		this.roadClassifier = new RoadClassifier(options);
		this.lightTranslator = new XPStringLightTranslator(writer, dsfObjectsProvider, outputFormat);
	}

	@Override
	public boolean handlePoly(OsmPolyline poly) {
		XplaneOptions options = XPlaneOptionsProvider.getOptions();
		if (!options.isGenerateRoads()) {
			return false;
		}
		String highwayValue = poly.getTagValue(HIGHWAY_TAG);
		if (roadClassifier.isRoad(highwayValue) && !roadClassifier.isDisallowedLine(poly)) {
			String surface = poly.getTagValue("surface");
			if (roadClassifier.isAllowedSurface(surface)) {
				addSegmentsFrom(poly);
				if (options.isGenerateStreetLights()) {
					processLights(poly);
				}
				return true;
			}
		}
		return false;
	}

	private void processLights(OsmPolyline poly) {
		String type = StringUtils.stripToEmpty(poly.getTagValue(HIGHWAY_TAG)).toLowerCase();
		String lit = StringUtils.stripToEmpty(poly.getTagValue("lit")).toLowerCase();
		int lanesCount = roadClassifier.getLanesCount(poly);
		if (roadClassifier.needsLights(poly)) {
			boolean doubleSided = (roadClassifier.isWideRoad(type) && lanesCount >= 2) || lanesCount >= 3;
			lightTranslator.writeLightStrings(poly.getPolyline(),
					lanesCount * GlobalOptionsProvider.getOptions().getRoadLaneWidth() / 2 * 1.1, doubleSided);
		}
	}

	@Override
	protected int getPathType(IHasTags poly) {
		return roadClassifier.getPathType(poly);
	}

	@Override
	protected int getBridgeRampLength() {
		return XPlaneOptionsProvider.getOptions().getRoadBridgeRampLen();
	}

	@Override
	public String getId() {
		return "road";
	}
}
