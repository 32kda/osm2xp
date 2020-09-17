package com.osm2xp.translators.xplane;

import java.io.File;
import java.util.Collection;

import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.translators.ISpecificTranslator;
import com.osm2xp.translators.airfield.XPAirfieldTranslationAdapter;
import com.osm2xp.writers.IHeaderedWriter;

import math.geom2d.Point2D;

public class XPlane10TranslatorProvider extends XPlaneTranslatorProvider {

	public XPlane10TranslatorProvider(File binaryFile, String folderPath) {
		super(binaryFile, folderPath);
	}

	@Override
	protected XPlaneTranslatorImpl createTranslator(Point2D currentTile, IHeaderedWriter writer) {
		return new Xplane10TranslatorImpl(writer, currentTile, folderPath, dsfObjectsProvider);
	}
	
	@Override
	public Collection<ISpecificTranslator> createAdditinalAdapters() {
		Collection<ISpecificTranslator> adapters = super.createAdditinalAdapters();
		if (XPlaneOptionsProvider.getOptions().getAirfieldOptions().isGenerateAirfields()) {
			adapters.add(new XPAirfieldTranslationAdapter(folderPath));
		}
		return adapters;
	}

	
}
