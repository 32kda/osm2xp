package com.osm2xp.translators.xplane;

import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.writers.IWriter;

public abstract class XPSimplePathTranslator extends XPPathTranslator {

	public XPSimplePathTranslator(IWriter writer, XPOutputFormat outputFormat, IDRenumbererService idProvider) {
		super(writer, outputFormat, idProvider);
	}

	protected abstract boolean isGenerationEnabled();

	protected abstract String getTagKey();

	protected abstract String getRequiredTagValue();

	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		if (!isGenerationEnabled()) {
			return false;
		}
		if (getRequiredTagValue().equals(osmPolyline.getTagValue(getTagKey()))) {
			addSegmentsFrom(osmPolyline);
			return true;
		}
		return false;
	}

}
