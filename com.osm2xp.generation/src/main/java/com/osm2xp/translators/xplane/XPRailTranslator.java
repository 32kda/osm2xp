package com.osm2xp.translators.xplane;

import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.writers.IWriter;

public class XPRailTranslator extends XPSimplePathTranslator {

	public XPRailTranslator(IWriter writer, IDRenumbererService idProvider, XPOutputFormat outputFormat) {
		super(writer, outputFormat, idProvider);
	}

	@Override
	protected boolean isGenerationEnabled() {
		return XPlaneOptionsProvider.getOptions().isGenerateRailways();
	}

	@Override
	protected String getTagKey() {
		return "railway";
	}

	@Override
	protected String getRequiredTagValue() {
		return "rail";
	}

	@Override
	protected int getPathType(IHasTags polygon) {
		return XPlaneOptionsProvider.getOptions().getRailwayType();
	}

	@Override
	protected int getBridgeRampLength() {
		return XPlaneOptionsProvider.getOptions().getRailBridgeRampLen();
	}

	@Override
	public String getId() {
		return "railway";
	}
}
