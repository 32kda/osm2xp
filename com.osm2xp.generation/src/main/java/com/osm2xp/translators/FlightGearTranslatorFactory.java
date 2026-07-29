package com.osm2xp.translators;

import java.io.File;

import com.osm2xp.translators.impl.FlightGearTranslatorImpl;

import math.geom2d.Point2D;

public class FlightGearTranslatorFactory implements ITileTranslatorFactory, ITranslatorProviderFactory {

	@Override
	public ITranslator getTranslator(File currentFile, Point2D currentTile, String folderPath) {
		return new FlightGearTranslatorImpl(currentTile, folderPath);
	}

	@Override
	public ITranslatorProvider getTranslatorProvider(File currentFile, String folderPath) {
		return new DefaultTranslatorProvider(currentFile, folderPath, getOutputMode());
	}

	@Override
	public String getOutputMode() {
		return "FLIGHT_GEAR";
	}
	
	@Override
	public boolean isFileWriting() {
		return true;
	}

}
