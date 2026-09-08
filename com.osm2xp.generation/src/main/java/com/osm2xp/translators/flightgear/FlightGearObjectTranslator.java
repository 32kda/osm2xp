package com.osm2xp.translators.flightgear;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Locale;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.stats.CountStats;
import com.osm2xp.stats.StatsProvider;
import com.osm2xp.translators.FlightGearStgWriterProvider;
import com.osm2xp.translators.IPolyHandler;

public abstract class FlightGearObjectTranslator implements IPolyHandler {
	
	protected static final String STG_PATTERN = "OBJECT_SHARED_AGL %s %.6f %.6f 0 %1.2f 0 0\n";

	protected FlightGearStgWriterProvider stgWriterProvider;

	@Override
	public void setStgWriterProvider(FlightGearStgWriterProvider stgWriterProvider) {
		this.stgWriterProvider = stgWriterProvider;
	}

	protected void writeObject(String modelPath, double lat, double lon, double angle) {
		if (stgWriterProvider == null) {
			Osm2xpLogger.warning("FG object translator: no STG writer provider set");
			return;
		}
		BufferedWriter writer = stgWriterProvider.getStgWriter(lon, lat);
		if (writer == null) {
			return;
		}
		try {
			writer.write(String.format(Locale.ROOT, STG_PATTERN, modelPath, lon, lat, angle));
			CountStats countStats = StatsProvider.getCommonStats();
			if (countStats != null) {
				countStats.incCount("object");
			}
		} catch (IOException e) {
			Osm2xpLogger.error("Error writing FlightGear object declaration", e);
		}
	}
}
