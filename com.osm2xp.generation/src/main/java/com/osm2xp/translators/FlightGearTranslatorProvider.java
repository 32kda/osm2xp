package com.osm2xp.translators;

import java.io.File;
import java.util.Collection;

import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.translators.airfield.FlightGearAirfieldTranslationAdapter;
import com.osm2xp.translators.flightgear.FlightGearBucketOutputRegistry;
import com.osm2xp.translators.impl.FlightGearTranslatorImpl;

import math.geom2d.Point2D;

/**
 * FlightGear translator provider, reusing the X-Plane airfield generation
 * workflow (same apt.dat 1050 format) for the FlightGear output path.
 * <p>
 * Holds a run-scoped {@link FlightGearBucketOutputRegistry} so that the airfield
 * BTG writer and the per-tile translators share the same STG writers.
 * 
 * @author osm2xp
 */
public class FlightGearTranslatorProvider extends DefaultTranslatorProvider {

	private final FlightGearBucketOutputRegistry bucketOutputRegistry;

	public FlightGearTranslatorProvider(File binaryFile, String folderPath, String outputFomat) {
		super(binaryFile, folderPath, outputFomat);
		this.bucketOutputRegistry = new FlightGearBucketOutputRegistry(new File(folderPath),
				FlightGearOptionsProvider.getOptions().isGenerateBuildings());
		// Defensive: make sure buffered STG/list writers are flushed even if the run
		// is aborted before ITranslatorProvider.close() is reached (otherwise the
		// 8 KB-buffered writers leave truncated files that FlightGear cannot parse).
		Runtime.getRuntime().addShutdownHook(new Thread(bucketOutputRegistry::closeAll, "osm2xp-stg-flush"));
	}

	@Override
	public Collection<ISpecificTranslator> createAdditinalAdapters() {
		Collection<ISpecificTranslator> adapters = super.createAdditinalAdapters();
		if (FlightGearOptionsProvider.getOptions().isGenerateAirfields()) {
			adapters.add(new FlightGearAirfieldTranslationAdapter(folderPath));
		}
		return adapters;
	}

	@Override
	public ITranslator getTranslator(Point2D currentTile) {
		return new FlightGearTranslatorImpl(currentTile, folderPath, bucketOutputRegistry);
	}

	@Override
	public void close() {
		bucketOutputRegistry.closeAll();
	}

}
