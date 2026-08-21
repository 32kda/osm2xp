package com.osm2xp.generation.paths;

import java.io.File;

public interface IPathsProvider {
	public File getBasicFolder();

	void setBasicFolder(File basicFolder);

	File getSpecFacadesFolder();

	void setSpecFacadesFolder(File specFacadesFolder);

	File getRoofColorFile();

	void setRoofColorFile(File roofColorFile);

	public File getObjectsFolder();
	
	public File getSpecObjectsFolder();

	File getFlightGearObjectsFolder();

	File getFlightGearSpecObjectsFolder();

	public File getForestsFolder();
	
	public File getXPlaneToolsFolder();
	
	public File getUserResourcesFolder();
}
