package com.osm2xp.generation.paths;

import java.io.File;
import java.net.URISyntaxException;

import com.osm2xp.core.logging.Osm2xpLogger;

public class DefaultPathsProvider implements IPathsProvider {
	
	protected File basicFolder;
	protected File specFacadesFolder;
	private File specObjectsFolder;
	private File objectsFolder;
	private File flightGearObjectsFolder;
	private File flightGearSpecObjectsFolder;
	private File forestsFolder;
	private File xPlaneToolsFolder;
	private File userResourcesFolder;
	private File roofColorFile = null;

	
	public DefaultPathsProvider() {
		
	}
	
	public DefaultPathsProvider(File basicFolder) {
		this.basicFolder = basicFolder;
	}
	
	@Override
	public File getBasicFolder() {
		if (basicFolder == null) {
			try {
				basicFolder = new File(DefaultPathsProvider.class.getProtectionDomain().getCodeSource().getLocation().toURI());
				while (!basicFolder.isDirectory()) {
					basicFolder = basicFolder.getParentFile();
				}
				if (basicFolder == null) {
					basicFolder = new File(new File("").getAbsolutePath());
				}
			} catch (URISyntaxException e) { //Shouldn't happen
				Osm2xpLogger.log(e);
				return new File(".");
			}
		}
		return basicFolder;
	}
	
	@Override
	public void setBasicFolder(File basicFolder) {
		this.basicFolder = basicFolder;
	}

	@Override
	public File getSpecFacadesFolder() {
		if (specFacadesFolder == null) {
			return new File(getBasicFolder(),"/xplane/specfacades");
		}
		return specFacadesFolder;
	}

	@Override
	public void setSpecFacadesFolder(File specFacadesFolder) {
		this.specFacadesFolder = specFacadesFolder;
	}
	
	@Override
	public void setRoofColorFile(File roofColorFile) {
		this.roofColorFile = roofColorFile;
	}

	@Override
	public File getRoofColorFile() {
		return roofColorFile;
	}

	@Override
	public File getSpecObjectsFolder() {
		if (specObjectsFolder == null) {
			return new File(getBasicFolder(),"/xplane/specobjects");
		}
		return specObjectsFolder;
	}

	public void setSpecObjectsFolder(File specObjectsFolder) {
		this.specObjectsFolder = specObjectsFolder;
	}

	@Override
	public File getObjectsFolder() {
		if (objectsFolder == null) {
			return new File(getBasicFolder(),"/xplane/objects");
		}
		return objectsFolder;
	}

	public void setObjectsFolder(File objectsFolder) {
		this.objectsFolder = objectsFolder;
	}

	@Override
	public File getFlightGearObjectsFolder() {
		if (flightGearObjectsFolder == null) {
			return new File(getBasicFolder(), "/flightgear/objects");
		}
		return flightGearObjectsFolder;
	}

	public void setFlightGearObjectsFolder(File flightGearObjectsFolder) {
		this.flightGearObjectsFolder = flightGearObjectsFolder;
	}

	@Override
	public File getFlightGearSpecObjectsFolder() {
		if (flightGearSpecObjectsFolder == null) {
			return new File(getBasicFolder(), "/flightgear/specobjects");
		}
		return flightGearSpecObjectsFolder;
	}

	public void setFlightGearSpecObjectsFolder(File flightGearSpecObjectsFolder) {
		this.flightGearSpecObjectsFolder = flightGearSpecObjectsFolder;
	}

	@Override
	public File getForestsFolder() {
		if (forestsFolder == null) {
			return new File(getBasicFolder(),"/xplane/forests");
		}
		return forestsFolder;
	}

	@Override
	public File getXPlaneToolsFolder() {
		if (xPlaneToolsFolder == null) {
			return new File(getBasicFolder(),"/xplane/utils");
		}
		return xPlaneToolsFolder;
	}

	@Override
	public File getUserResourcesFolder() {
		if (userResourcesFolder == null) {
			return new File(getBasicFolder(),"/xplane/resources");
		}
		return userResourcesFolder;
	}

	public File getxPlaneToolsFolder() {
		return xPlaneToolsFolder;
	}

	public void setxPlaneToolsFolder(File xPlaneToolsFolder) {
		this.xPlaneToolsFolder = xPlaneToolsFolder;
	}

	public void setForestsFolder(File forestsFolder) {
		this.forestsFolder = forestsFolder;
	}

	public void setUserResourcesFolder(File userResourcesFolder) {
		this.userResourcesFolder = userResourcesFolder;
	}

}
