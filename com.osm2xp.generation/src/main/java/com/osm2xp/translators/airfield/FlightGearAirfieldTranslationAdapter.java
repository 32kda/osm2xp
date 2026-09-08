package com.osm2xp.translators.airfield;

import java.io.File;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.translators.airfield.btg.FlightGearAirportBtgWriter;

/**
 * FlightGear airfield translation adapter, reusing the X-Plane airfield
 * binding/generation workflow but emitting the airport data in the FlightGear
 * layout ({@link FlightGearAirfieldOutput}: one {@code NavData/apt/<ICAO>.dat}
 * file per airfield).
 * <p>
 * In addition it bakes the visual airport geometry (runways, taxiways, aprons,
 * helipads and the grass clearing) into the matching terrain BTG tile, reusing
 * the same {@link AirfieldData} that the apt.dat writer consumes.
 *
 * @author osm2xp
 */
public class FlightGearAirfieldTranslationAdapter extends XPAirfieldTranslationAdapter {

	private final FlightGearAirportBtgWriter btgWriter = new FlightGearAirportBtgWriter();

	public FlightGearAirfieldTranslationAdapter(String outputFolder) {
		super(outputFolder, true);
	}

	@Override
	protected XPAirfieldOutput createAirfieldOutput(File airfieldOutputFolder, boolean writeAsMainAirfield) {
		return new FlightGearAirfieldOutput(airfieldOutputFolder, writeAsMainAirfield);
	}

	@Override
	public void complete() {
		super.complete();
		if (!FlightGearOptionsProvider.getOptions().isGenerateAirfieldsBtg()) {
			return;
		}
		for (AirfieldData airfield : getAirfieldList()) {
			String icao = airfield.getICAO();
			if (icao != null
					&& XPlaneOptionsProvider.getOptions().getAirfieldOptions().getIgnoredAirfields().contains(icao)) {
				continue;
			}
			try {
				btgWriter.write(airfield, getWorkFolder());
			} catch (Exception e) {
				Osm2xpLogger.error("Error generating FlightGear airfield BTG for " + airfield.getId(), e);
			}
		}
	}

}
