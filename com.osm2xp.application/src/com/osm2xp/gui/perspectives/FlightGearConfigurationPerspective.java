package com.osm2xp.gui.perspectives;

/**
 * Flight Gear configuration perspective.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class FlightGearConfigurationPerspective extends GenerationPerspective implements IGenerationModeProvider {

	@Override
	public String getGenerationMode() {
		return "FLIGHT_GEAR";
	}

}
