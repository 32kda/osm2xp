package com.osm2xp.xplane.customrules;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
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

	private static Map<PathOptionsType, List<PathRule>> pathRules = new HashMap<>();

	public static List<PathRule> getRulesList(PathOptionsType optionsType) {
		return pathRules.computeIfAbsent(optionsType, type -> loadOptions(type));
	}

	private static List<PathRule> loadOptions(PathOptionsType type) {
		File pathRulesFile = getPathRulesFile(type);
		List<PathRule> pathRulesList = new ArrayList<>();
		try (CSVReader reader = new CSVReader(new FileReader(pathRulesFile), ',', '"', 1)) {
			List<String[]> allRows = reader.readAll();
			for (String[] row : allRows) {				
				if (row.length >= 3) {
					PathRule pathRule = new PathRule(row[0], row[1], Integer.parseInt(row[2]));
					pathRulesList.add(pathRule);
				}
			}				
		} catch (Exception e) {
			Osm2xpLogger.warning("Unable to load X-Plane path rules file at " + pathRulesFile.getAbsolutePath());
		}
		return pathRulesList;
	}

	public static void saveOptions(PathOptionsType optionsType, List<PathRule> pathRules) {
		File pathRulesFile = getPathRulesFile(optionsType);
		pathRulesFile.getParentFile().mkdirs();

		try (CSVWriter writer = new CSVWriter(new FileWriter(pathRulesFile))) {
			writer.writeNext(new String[] {"Name","Rule", "PathType"});
			for (PathRule pathRule : pathRules) {
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
