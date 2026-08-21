package com.osm2xp.translators;

import java.io.File;
import java.util.Collection;

import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.translators.airfield.FlightGearAirfieldTranslationAdapter;

/**
 * FlightGear translator provider, reusing the X-Plane airfield generation
 * workflow (same apt.dat 1050 format) for the FlightGear output path.
 * 
 * @author osm2xp
 */
public class FlightGearTranslatorProvider extends DefaultTranslatorProvider {

	public FlightGearTranslatorProvider(File binaryFile, String folderPath, String outputFomat) {
		super(binaryFile, folderPath, outputFomat);
	}

	@Override
	public Collection<ISpecificTranslator> createAdditinalAdapters() {
		Collection<ISpecificTranslator> adapters = super.createAdditinalAdapters();
		if (FlightGearOptionsProvider.getOptions().isGenerateAirfields()) {
			adapters.add(new FlightGearAirfieldTranslationAdapter(folderPath));
		}
		return adapters;
	}

}
