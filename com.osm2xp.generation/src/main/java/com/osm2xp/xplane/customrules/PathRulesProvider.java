package com.osm2xp.xplane.customrules;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.paths.PathsService;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class PathRulesProvider {

	public enum PathOptionsType {
		ROADS_CITY, ROADS_COUNTRY, RAILWAYS, ROADS_CITY_EU, ROADS_COUNTRY_EU, RAILWAYS_EU,

	}

	private static Map<PathOptionsType, PathRulesList> pathRules = new HashMap<>();

	public static PathRulesList getRulesList(PathOptionsType optionsType) {
		return pathRules.computeIfAbsent(optionsType, type -> loadOptions(type));
	}

	private static PathRulesList loadOptions(PathOptionsType type) {
		File pathRulesFile = getPathRulesFile(type);
		PathRulesList pathRulesList = new PathRulesList();
		try (CSVReader reader = new CSVReader(new FileReader(pathRulesFile), ',', '"', 1)) {
			List<String[]> allRows = reader.readAll();
			for (String[] row : allRows) {				
				PathRule pathRule = new PathRule(row[0], row[1], Integer.parseInt(row[2]));
				pathRulesList.addRule(pathRule);
			}				
		} catch (Exception e) {
			Osm2xpLogger.warning("Unable to load X-Plane path rules file at " + pathRulesFile.getAbsolutePath());
		}
		return pathRulesList;
	}

	public static void saveOptions(PathOptionsType optionsType, PathRulesList pathRulesList) {
		File pathRulesFile = getPathRulesFile(optionsType);
		pathRulesFile.getParentFile().mkdirs();

		try (CSVWriter writer = new CSVWriter(new FileWriter(pathRulesFile))) {
			writer.writeNext(new String[] {"Name","Rule", "PathType"});
			List<PathRule> rules = pathRulesList.getRules();
			for (PathRule pathRule : rules) {
				writer.writeNext(
						new String[] { pathRule.getName(), pathRule.getCondition(), pathRule.getResultValue() + "" });

			}

		} catch (IOException e) {
			Osm2xpLogger.warning("Unable to save X-Plane path rules file at " + pathRulesFile.getAbsolutePath());
		}

	}

	public static File getPathRulesFile(PathOptionsType optionsType) {
		File installFolder = PathsService.getPathsProvider().getBasicFolder();
		File xPlaneFolder = new File(installFolder.getAbsolutePath(), "xplane");
		File defaultConfig = new File(xPlaneFolder, optionsType.name().toLowerCase() + ".csv");
		return defaultConfig;
	}

}
