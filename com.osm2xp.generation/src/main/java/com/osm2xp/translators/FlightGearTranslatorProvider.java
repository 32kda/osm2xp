package com.osm2xp.translators;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.parsers.IOSMDataVisitor;
import com.osm2xp.datastore.IDataSink;
import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.translators.airfield.ElevationProvidingService;
import com.osm2xp.translators.airfield.FlightGearAirfieldTranslationAdapter;
import com.osm2xp.translators.airfield.btg.AirfieldBtgPatcher;
import com.osm2xp.translators.flightgear.FlightGearBucketOutputRegistry;
import com.osm2xp.translators.flightgear.btg.BtgPatchPipeline;
import com.osm2xp.translators.flightgear.terrain.AbstractTerrainDownloader;
import com.osm2xp.translators.flightgear.terrain.FlightGearTerrainPreprocessor;
import com.osm2xp.translators.flightgear.terrain.TerrainCache;
import com.osm2xp.translators.flightgear.terrain.TerrainFormat;
import com.osm2xp.translators.flightgear.terrain.TerrainPaths;
import com.osm2xp.translators.flightgear.terrain.TerrainServices;
import com.osm2xp.translators.impl.FlightGearTranslatorImpl;

import math.geom2d.Point2D;

/**
 * FlightGear translator provider, reusing the X-Plane airfield generation
 * workflow (same apt.dat 1050 format) for the FlightGear output path.
 * <p>
 * Holds a run-scoped {@link FlightGearBucketOutputRegistry} so that the airfield
 * BTG writer and the per-tile translators share the same STG writers.
 * <p>
 * Implements {@link IPreprocessorProvider} so the terrain covering the input
 * file is prepared <em>before</em> the main pass (and therefore before building
 * generation). Original tiles are cached under {@code source_tiles} (downloaded
 * at most once, preferring the user's scenery root), and every tile of the area
 * is copied into the generated scenery's {@code Terrain/} directory.
 *
 * @author osm2xp
 */
public class FlightGearTranslatorProvider extends DefaultTranslatorProvider implements IPreprocessorProvider {

	private final FlightGearBucketOutputRegistry bucketOutputRegistry;
	private final TerrainPaths terrainPaths;
	private final BtgPatchPipeline btgPatchPipeline;
	private final AirfieldBtgPatcher airfieldBtgPatcher;
	private boolean closed;

	public FlightGearTranslatorProvider(File binaryFile, String folderPath, String outputFomat) {
		super(binaryFile, folderPath, outputFomat);
		this.bucketOutputRegistry = new FlightGearBucketOutputRegistry(new File(folderPath),
				FlightGearOptionsProvider.getOptions().isGenerateBuildings());

		FlightGearOptions options = FlightGearOptionsProvider.getOptions();
		File workingFolder = new File(folderPath);
		File sourceFileFolder = binaryFile == null ? null : binaryFile.getParentFile();
		File cacheDir = TerrainCache.resolveCacheDir(workingFolder, sourceFileFolder);
		this.terrainPaths = new TerrainPaths(cacheDir, toFileOrNull(options.getFlightGearSceneryPath()),
				new File(workingFolder, "Terrain"));

		// Ordered BTG patching (terrain-shape stages; trees are written as STG TREE_LIST).
		this.btgPatchPipeline = new BtgPatchPipeline(terrainPaths.getOutputTerrainDir());
		this.airfieldBtgPatcher = new AirfieldBtgPatcher();
		this.btgPatchPipeline.addPatcher(airfieldBtgPatcher);

		// Elevation probing (buildings and airfields) reads the cached source tiles.
		ElevationProvidingService.setTerrainRoot(cacheDir);

		// Defensive: make sure buffered STG/list writers are flushed even if the run
		// is aborted before ITranslatorProvider.close() is reached (otherwise the
		// 8KB-buffered writers leave truncated files that FlightGear cannot parse).
		Runtime.getRuntime().addShutdownHook(new Thread(bucketOutputRegistry::closeAll, "osm2xp-stg-flush"));
	}

	@Override
	public Collection<ISpecificTranslator> createAdditinalAdapters() {
		Collection<ISpecificTranslator> adapters = super.createAdditinalAdapters();
		if (FlightGearOptionsProvider.getOptions().isGenerateAirfields()) {
			adapters.add(new FlightGearAirfieldTranslationAdapter(folderPath, terrainPaths.getCacheDir(),
					airfieldBtgPatcher));
		}
		return adapters;
	}

	@Override
	public ITranslator getTranslator(Point2D currentTile) {
		return new FlightGearTranslatorImpl(currentTile, folderPath, bucketOutputRegistry, terrainPaths.getCacheDir());
	}

	@Override
	public Collection<IOSMDataVisitor> createPreprocessors(IDataSink dataSink) {
		FlightGearOptions options = FlightGearOptionsProvider.getOptions();
		if (!needsTerrain(options)) {
			return Collections.emptyList();
		}
		AbstractTerrainDownloader downloader;
		try {
			downloader = TerrainServices.createDownloader(TerrainFormat.fromString(options.getTerrainFormat()),
					options.getTerrainMirrors());
		} catch (UnsupportedOperationException e) {
			Osm2xpLogger.warning(e.getMessage());
			return Collections.emptyList();
		}
		return Collections.singletonList(new FlightGearTerrainPreprocessor(downloader, terrainPaths));
	}

	/**
	 * Tiles are needed to place buildings via the BUILDING_LIST shader (elevation
	 * probing) and to generate airfields (BTG patching). Runs that only generate
	 * 3D objects do not need any terrain work.
	 */
	private static boolean needsTerrain(FlightGearOptions options) {
		return (options.isGenerateBuildings() && options.isGenerateBuildingsElevation())
				|| (options.isGenerateAirfields() && options.isGenerateAirfieldsBtg())
				|| options.isGenerateForests();
	}

	private static File toFileOrNull(String path) {
		return path == null || path.trim().isEmpty() ? null : new File(path.trim());
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		try {
			btgPatchPipeline.run();
		} catch (Throwable t) {
			Osm2xpLogger.error("Error running FlightGear BTG patch pipeline", t);
		}
		bucketOutputRegistry.closeAll();
	}

}
