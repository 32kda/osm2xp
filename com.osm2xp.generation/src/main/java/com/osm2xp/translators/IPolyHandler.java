package com.osm2xp.translators;

import java.io.BufferedWriter;

import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.translators.flightgear.FlightGearBucketOutputProvider;

public interface IPolyHandler {
	public boolean handlePoly(OsmPolyline osmPolyline);
	
	public void translationComplete();
	
	public String getId();
	
	public boolean isTerminating();
	
	default void setStgWriter(BufferedWriter stgWriter) {
	}
	
	default void setStgWriterProvider(FlightGearStgWriterProvider stgWriterProvider) {
	}
	
	default void setBucketOutputProvider(FlightGearBucketOutputProvider bucketOutputProvider) {
	}
}
