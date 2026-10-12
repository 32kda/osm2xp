package com.osm2xp.generation.paths;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import com.osm2xp.core.logging.Osm2xpLogger;

public class DefaultPathsProvider implements IPathsProvider {
	
	private static final String ADDITIONS_FOLDER_NAME = "osm2xp_additions";
	public static final String XPLANE_FOLDER_NAME = "xplane";
	public static final String FLIGHTGEAR_FOLDER_NAME = "flightgear";
	public static final String RESOURCES_FOLDER_NAME = "resources";
	private static final String XPLANE_OPTIONS_FILE = XPLANE_FOLDER_NAME + "/xplaneOptions.xml";
	private static final String FLIGHTGEAR_OPTIONS_FILE = FLIGHTGEAR_FOLDER_NAME + "/FlightGearOptions.xml";
	
	protected File basicFolder;
	protected File specFacadesFolder;
	private File specObjectsFolder;
	private File objectsFolder;
	private File flightGearObjectsFolder;
	private File flightGearSpecObjectsFolder;
	private File flightGearResourcesFolder;
	private File forestsFolder;
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
			File defaultFolder = null;
			try {
				defaultFolder = new File(DefaultPathsProvider.class.getProtectionDomain().getCodeSource().getLocation().toURI());
				while (defaultFolder != null && !defaultFolder.isDirectory()) {
					defaultFolder = defaultFolder.getParentFile();
				}
			} catch (URISyntaxException e) { //Shouldn't happen
				Osm2xpLogger.log(e);
			}
			if (defaultFolder == null) {
				defaultFolder = new File(new File("").getAbsolutePath());
			}
			basicFolder = locateBasicFolder(defaultFolder);
		}
		return basicFolder;
	}
	
	public static File locateBasicFolder(File preferred) {
		if (hasResources(preferred)) {
			return preferred;
		}
		List<File> starts = new ArrayList<File>();
		if (preferred != null) {
			starts.add(preferred);
		}
		String userDir = System.getProperty("user.dir");
		if (userDir != null && !userDir.isEmpty()) {
			starts.add(new File(userDir));
		}
		try {
			starts.add(new File(DefaultPathsProvider.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
		} catch (URISyntaxException e) {
			Osm2xpLogger.log(e);
		}
		for (File start : starts) {
			File current = start;
			if (current != null && !current.isDirectory()) {
				current = current.getParentFile();
			}
			while (current != null) {
				File additionsFolder = new File(current, ADDITIONS_FOLDER_NAME);
				if (hasResources(additionsFolder)) {
					return additionsFolder;
				}
				if (hasResources(current)) {
					return current;
				}
				current = current.getParentFile();
			}
		}
		return preferred;
	}
	
	static boolean hasResources(File folder) {
		if (folder == null) {
			return false;
		}
		return new File(folder, XPLANE_OPTIONS_FILE).isFile()
				|| new File(folder, FLIGHTGEAR_OPTIONS_FILE).isFile();
	}
	
	@Override
	public void setBasicFolder(File basicFolder) {
		this.basicFolder = basicFolder;
	}

	@Override
	public File getSpecFacadesFolder() {
		if (specFacadesFolder == null) {
			return new File(getBasicFolder(), XPLANE_FOLDER_NAME + "/specfacades");
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
			return new File(getBasicFolder(), XPLANE_FOLDER_NAME + "/specobjects");
		}
		return specObjectsFolder;
	}

	public void setSpecObjectsFolder(File specObjectsFolder) {
		this.specObjectsFolder = specObjectsFolder;
	}

	@Override
	public File getObjectsFolder() {
		if (objectsFolder == null) {
			return new File(getBasicFolder(), XPLANE_FOLDER_NAME + "/objects");
		}
		return objectsFolder;
	}

	public void setObjectsFolder(File objectsFolder) {
		this.objectsFolder = objectsFolder;
	}

	@Override
	public File getFlightGearObjectsFolder() {
		if (flightGearObjectsFolder == null) {
			return new File(getBasicFolder(), FLIGHTGEAR_FOLDER_NAME + "/objects");
		}
		return flightGearObjectsFolder;
	}

	public void setFlightGearObjectsFolder(File flightGearObjectsFolder) {
		this.flightGearObjectsFolder = flightGearObjectsFolder;
	}

	@Override
	public File getFlightGearSpecObjectsFolder() {
		if (flightGearSpecObjectsFolder == null) {
			return new File(getBasicFolder(), FLIGHTGEAR_FOLDER_NAME + "/specobjects");
		}
		return flightGearSpecObjectsFolder;
	}

	public void setFlightGearSpecObjectsFolder(File flightGearSpecObjectsFolder) {
		this.flightGearSpecObjectsFolder = flightGearSpecObjectsFolder;
	}

	@Override
	public File getFlightGearResourcesFolder() {
		if (flightGearResourcesFolder == null) {
			return new File(getBasicFolder(), FLIGHTGEAR_FOLDER_NAME + "/" + RESOURCES_FOLDER_NAME);
		}
		return flightGearResourcesFolder;
	}

	public void setFlightGearResourcesFolder(File flightGearResourcesFolder) {
		this.flightGearResourcesFolder = flightGearResourcesFolder;
	}

	@Override
	public File getForestsFolder() {
		if (forestsFolder == null) {
			return new File(getBasicFolder(), XPLANE_FOLDER_NAME + "/forests");
		}
		return forestsFolder;
	}

	@Override
	public File getUserResourcesFolder() {
		if (userResourcesFolder == null) {
			return new File(getBasicFolder(), XPLANE_FOLDER_NAME + "/" + RESOURCES_FOLDER_NAME);
		}
		return userResourcesFolder;
	}

	public void setForestsFolder(File forestsFolder) {
		this.forestsFolder = forestsFolder;
	}

	public void setUserResourcesFolder(File userResourcesFolder) {
		this.userResourcesFolder = userResourcesFolder;
	}

}
