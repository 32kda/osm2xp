package com.osm2xp.translators.airfield;

import java.io.File;

/**
 * FlightGear airfield translation adapter, reusing the X-Plane airfield
 * binding/generation workflow but emitting the airport data in the FlightGear
 * layout ({@link FlightGearAirfieldOutput}: one {@code NavData/apt/<ICAO>.dat}
 * file per airfield).
 *
 * @author osm2xp
 */
public class FlightGearAirfieldTranslationAdapter extends XPAirfieldTranslationAdapter {

	public FlightGearAirfieldTranslationAdapter(String outputFolder) {
		super(outputFolder, true);
	}

	@Override
	protected XPAirfieldOutput createAirfieldOutput(File airfieldOutputFolder, boolean writeAsMainAirfield) {
		return new FlightGearAirfieldOutput(airfieldOutputFolder, writeAsMainAirfield);
	}

}