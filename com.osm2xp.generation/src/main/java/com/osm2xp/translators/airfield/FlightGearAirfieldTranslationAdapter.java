package com.osm2xp.translators.airfield;

import java.io.File;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.translators.airfield.btg.FlightGearAirportCutWriter;
import com.osm2xp.translators.flightgear.FlightGearTerrainDownloader;

import math.geom2d.Box2D;

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
 *
 * @author osm2xp
 */
public class FlightGearAirfieldTranslationAdapter extends XPAirfieldTranslationAdapter {

	private final FlightGearAirportCutWriter cutWriter = new FlightGearAirportCutWriter();

	private Box2D bbox;

	public FlightGearAirfieldTranslationAdapter(String outputFolder) {
		super(outputFolder, true);
	}

	@Override
	protected XPAirfieldOutput createAirfieldOutput(File airfieldOutputFolder, boolean writeAsMainAirfield) {
		return new FlightGearAirfieldOutput(airfieldOutputFolder, writeAsMainAirfield);
	}

	@Override
	public void processBoundingBox(Box2D bbox) {
		super.processBoundingBox(bbox);
		this.bbox = bbox;
	}

	@Override
	public void complete() {
		super.complete();
		if (!FlightGearOptionsProvider.getOptions().isGenerateAirfieldsBtg()) {
			return;
		}
		boolean cut = FlightGearOptionsProvider.getOptions().isGenerateAirfieldsBtgCut();
		try {
			downloadTerrain();
		} catch (Throwable t) {
			Osm2xpLogger.error("Error downloading FlightGear terrain for airfield BTG", t);
		}
		for (AirfieldData airfield : getAirfieldList()) {
			String icao = airfield.getICAO();
			if (icao != null
					&& XPlaneOptionsProvider.getOptions().getAirfieldOptions().getIgnoredAirfields().contains(icao)) {
				continue;
			}
			try {
				cutWriter.write(airfield, getWorkFolder(), cut);
			} catch (Throwable t) {
				Osm2xpLogger.error("Error generating FlightGear airfield BTG for " + airfield.getId(), t);
			}
		}
	}

	private void downloadTerrain() {
		if (bbox == null) {
			return;
		}
		String sceneryPath = FlightGearOptionsProvider.getOptions().getFlightGearSceneryPath();
		if (sceneryPath == null || sceneryPath.trim().isEmpty()) {
			return;
		}
		java.io.File sceneryRoot = new java.io.File(sceneryPath);
		if (!sceneryRoot.isDirectory()) {
			return;
		}
		new FlightGearTerrainDownloader().download(bbox.getMinX(), bbox.getMinY(), bbox.getMaxX(), bbox.getMaxY(),
				sceneryRoot);
	}

}
