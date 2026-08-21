package com.osm2xp.translators.airfield;

import java.io.File;

/**
 * FlightGear airport data writer.
 * <p>
 * FlightGear's NavCache ({@code NavDataCache::findDatFiles}) scans
 * {@code <scenery>/NavData/apt/*.dat[.gz]} non-recursively, i.e. only files
 * directly inside the {@code NavData/apt} folder are loaded. The X-Plane
 * layout of one {@code apt.dat} per airfield subfolder is therefore ignored by
 * FlightGear, so each airfield is written as a single {@code <ICAO>.dat} file
 * directly under {@code NavData/apt}.
 *
 * @author osm2xp
 */
public class FlightGearAirfieldOutput extends XPAirfieldOutput {

	private static final String NAV_DATA_APT_FOLDER_NAME = "NavData" + File.separator + "apt";

	public FlightGearAirfieldOutput(File baseFolder, boolean writeMainAirfield) {
		super(baseFolder, writeMainAirfield, true);
	}

	@Override
	protected void writeAptData(String aptId, String icao, String[] aptDefinition) {
		if (aptDefinition.length == 0) {
			return;
		}
		File navAptFolder = new File(baseFolder, NAV_DATA_APT_FOLDER_NAME);
		navAptFolder.mkdirs();
		writeAptFile(new File(navAptFolder, icao + ".dat"), aptId, aptDefinition);
	}

}