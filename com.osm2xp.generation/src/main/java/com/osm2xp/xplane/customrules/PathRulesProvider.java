package com.osm2xp.xplane.customrules;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.XmlHelper;
import com.osm2xp.generation.options.XplaneOptions;
import com.osm2xp.generation.paths.PathsService;

public class PathRulesProvider {
	
	public enum PathOptionsType {
		ROADS_CITY,
		ROADS_COUNTRY,
		RAILWAYS,
		ROADS_CITY_EU,
		ROADS_COUNTRY_EU,
		RAILWAYS_EU,
		
	}
	
	private static Map<PathOptionsType, PathRulesList> pathRules = new HashMap<>();
	
	public static PathRulesList getRulesList(PathOptionsType optionsType) {
		return pathRules.computeIfAbsent(optionsType, type -> loadOptions(type));
	}

	private static PathRulesList loadOptions(PathOptionsType type) {
		File pathRulesFile = getPathRulesFile(type);
		try {
			return (PathRulesList) XmlHelper.loadFileFromXml(pathRulesFile, XplaneOptions.class);
		} catch (com.osm2xp.core.exceptions.Osm2xpBusinessException e) {
			Osm2xpLogger.warning("Unable to load X-Plane options file at " + pathRulesFile.getAbsolutePath()); 
		}
		return new PathRulesList();
	}
	
	public static File getPathRulesFile(PathOptionsType optionsType) {
		File installFolder = PathsService.getPathsProvider().getBasicFolder();
		File xPlaneFolder = new File(installFolder.getAbsolutePath(), "xplane");
		File defaultConfig = new File(xPlaneFolder, optionsType.name().toLowerCase() + ".xml");
		return defaultConfig;
	}

}
