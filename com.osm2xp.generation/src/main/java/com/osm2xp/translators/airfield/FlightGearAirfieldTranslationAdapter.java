package com.osm2xp.translators.airfield;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.translators.airfield.btg.FlightGearAirportCutWriter;
import com.osm2xp.translators.flightgear.FlightGearExistingAirfields;

/**
 * FlightGear airfield translation adapter, reusing the X-Plane airfield
 * binding/generation workflow but emitting the airport data in the FlightGear
 * layout ({@link FlightGearAirfieldOutput}: one {@code NavData/apt/<ICAO>.dat}
 * file per airfield).
 * <p>
 * In addition it bakes the visual airport geometry (runways, taxiways, aprons,
 * helipads and the grass clearing) into the matching terrain BTG tile, reusing
 * the same {@link AirfieldData} that the apt.dat writer consumes. Two modes are
 * supported by {@link FlightGearAirportCutWriter}: cut-and-fill (a hole in the
 * terrain) when {@code generateAirfieldsBtgCut} is on, otherwise the plate +
 * skirt are overlaid on the untouched terrain.
 * <p>
 * The terrain is prepared up-front by
 * {@code FlightGearTerrainPreprocessor} (via
 * {@code FlightGearTranslatorProvider.createPreprocessors}); this adapter no
 * longer downloads it.
 *
 * @author osm2xp
 */
public class FlightGearAirfieldTranslationAdapter extends XPAirfieldTranslationAdapter {

	private final FlightGearAirportCutWriter cutWriter = new FlightGearAirportCutWriter();

	private final File terrainCacheDir;
	private Set<String> existingAirfields;

	public FlightGearAirfieldTranslationAdapter(String outputFolder, File terrainCacheDir) {
		super(outputFolder, true);
		this.terrainCacheDir = terrainCacheDir;
	}

	@Override
	protected XPAirfieldOutput createAirfieldOutput(File airfieldOutputFolder, boolean writeAsMainAirfield) {
		return new FlightGearAirfieldOutput(airfieldOutputFolder, writeAsMainAirfield);
	}

	@Override
	protected boolean isAirfieldIgnored(AirfieldData airfieldData) {
		if (super.isAirfieldIgnored(airfieldData)) {
			return true;
		}
		String icao = airfieldData.getICAO();
		if (icao == null) {
			return false;
		}
		if (FlightGearOptionsProvider.getOptions().getIgnoredAirfields().contains(icao)) {
			return true;
		}
		return getExistingAirfields().contains(icao.toUpperCase(Locale.ROOT));
	}

	private Set<String> getExistingAirfields() {
		if (existingAirfields == null) {
			FlightGearOptions options = FlightGearOptionsProvider.getOptions();
			if (options.isIgnoreExistingAirfields()) {
				existingAirfields = FlightGearExistingAirfields
						.findExisting(new File(options.getFlightGearSceneryPath()));
			} else {
				existingAirfields = Collections.emptySet();
			}
		}
		return existingAirfields;
	}

	@Override
	public void complete() {
		super.complete();
		if (!FlightGearOptionsProvider.getOptions().isGenerateAirfieldsBtg()) {
			return;
		}
		boolean cut = FlightGearOptionsProvider.getOptions().isGenerateAirfieldsBtgCut();
		for (AirfieldData airfield : getAirfieldList()) {
			if (isAirfieldIgnored(airfield)) {
				continue;
			}
			try {
				cutWriter.write(airfield, terrainCacheDir, getWorkFolder(), cut);
			} catch (Throwable t) {
				Osm2xpLogger.error("Error generating FlightGear airfield BTG for " + airfield.getId(), t);
			}
		}
	}

}
