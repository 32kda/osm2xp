package com.osm2xp.translators.xplane;

import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.writers.IWriter;

public class XPPowerlineTranslator extends XPSimplePathTranslator {

	public XPPowerlineTranslator(IWriter writer, IDRenumbererService idProvider, XPOutputFormat outputFormat) {
		super(writer, outputFormat, idProvider);
	}

	@Override
	protected boolean isGenerationEnabled() {
		return XPlaneOptionsProvider.getOptions().isGeneratePowerlines();
	}

	@Override
	protected String getTagKey() {
		return "power";
	}

	@Override
	protected String getRequiredTagValue() {
		return "line";
	}

	@Override
	protected int getPathType(IHasTags polygon) {
		return XPlaneOptionsProvider.getOptions().getPowerlineType();
	}

	@Override
	protected int getBridgeRampLength() {
		return 0;
	}

	@Override
	public String getId() {
		return "powerline";
	}
}
