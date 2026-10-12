package com.osm2xp.gui;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.Platform;

import com.osm2xp.generation.paths.DefaultPathsProvider;

public class RCPPathsProvider extends DefaultPathsProvider {

	@Override
	public File getBasicFolder() {
		if (basicFolder == null) {
			basicFolder = locateBasicFolder(FileUtils.toFile(Platform.getInstallLocation().getURL()));
		}
		return basicFolder;
	}
	
}
