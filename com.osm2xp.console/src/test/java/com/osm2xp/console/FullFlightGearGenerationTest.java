package com.osm2xp.console;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.osm2xp.stats.CountStats;
import com.osm2xp.stats.StatsProvider;

import junit.framework.TestCase;

/**
 * End-to-end FlightGear generation over the big PBF extract with every feature
 * enabled: rule-based 3D objects, building-size 3D models, transportation
 * (roads/railways), power lines and airfields.
 *
 * <p>
 * Mirrors {@link TransportationIntegrationTest} (same PBF, same console mode,
 * same output location next to the input file) but asserts the full output
 * surface: airfield apt.dat files under NavData/apt, BuildingList files, copied
 * Models folders and the OBJECT_SHARED_AGL declarations for rule-based objects
 * ({@code Models/specobjects/...}) and size-based building models
 * ({@code Models/objects/house/...}, {@code Models/objects/industrial/...}).
 *
 * @author osm2xp
 */
public class FullFlightGearGenerationTest extends TestCase {

	private static final String TESTSCENERY_NAME = "testscenery_full";

	@Test
	public void testFlightGearFullGeneration() throws Exception {
		File basicFolder = new File(new File("").getAbsolutePath());
		while (basicFolder != null && !isTestRootFolder(basicFolder)) {
			basicFolder = basicFolder.getParentFile();
		}
		assertNotNull("Could not find testdata root", basicFolder);
		basicFolder = new File(basicFolder, "testdata");
		assertTrue("testdata directory not found", basicFolder.isDirectory());

		File pbfFile = new File("H:\\tmp\\planet_77.418,51.277_88.406,56.055.osm.pbf");
		assertTrue("PBF file not found: " + pbfFile, pbfFile.isFile());
		File targetDir = new File(pbfFile.getParentFile(), TESTSCENERY_NAME);
		FileUtils.deleteDirectory(targetDir);
		targetDir.mkdirs();
		StatsProvider.reinit();
		List<String> argList = new ArrayList<String>();
		argList.add(pbfFile.getAbsolutePath());
		argList.add("-m");
		argList.add("FLIGHT_GEAR");
		argList.add("-c");
		argList.add(basicFolder.getAbsolutePath());
		argList.add("-s");
		argList.add(TESTSCENERY_NAME);
		com.osm2xp.console.App.main(argList.toArray(new String[0]));

		assertTrue("Target directory not created", targetDir.isDirectory());

		CountStats commonStats = StatsProvider.getCommonStats();
		String statsSummary = commonStats.getSummary();
		System.out.println("Common generation stats: " + statsSummary);
		assertTrue("StatsProvider common stats should not be empty", !statsSummary.isEmpty());

		// ---- Airfields ----------------------------------------------------
		// One per-airport <ICAO>.dat file under NavData/apt/
		List<File> aptDatFiles = findFiles(new File(targetDir, "NavData/apt"), ".dat");
		assertTrue("No NavData/apt/<ICAO>/apt.dat airfield files found", aptDatFiles.size() > 0);
		int airfieldsCount = commonStats.getCount("Airfields");
		System.out.println("Airfields generated: " + airfieldsCount + ", apt.dat files: " + aptDatFiles.size());
		assertTrue("StatsProvider reports " + airfieldsCount + " airfields, should be > 0", airfieldsCount > 0);
		for (File aptDat : aptDatFiles) {
			List<String> aptLines = Files.readAllLines(aptDat.toPath(), Charset.forName("UTF-8"));
			assertTrue("apt.dat should not be empty: " + aptDat, aptLines.size() > 0);
			boolean hasRunways = false;
			boolean hasHelipads = false;
			boolean hasAirportHeader = false;
			for (String line : aptLines) {
				if (line.startsWith("1 ")) {
					hasAirportHeader = true;
				}
				if (line.startsWith("100 ")) {
					hasRunways = true;
				}
				if (line.startsWith("102 ")) {
					hasHelipads = true;
				}
			}
			assertTrue("apt.dat missing airport header row: " + aptDat, hasAirportHeader);
			assertTrue("apt.dat missing runway (100) or helipad (102) rows: " + aptDat,
					hasRunways || hasHelipads);
		}

		// ---- Building lists ----------------------------------------------
		List<File> buildingListFiles = new ArrayList<>();
		for (File candidate : findFiles(targetDir, ".txt.gz")) {
			if (candidate.getName().startsWith("BuildingList_")) {
				buildingListFiles.add(candidate);
			}
		}
		assertTrue("No BuildingList txt.gz files found", buildingListFiles.size() > 0);
		System.out.println("BuildingList files: " + buildingListFiles.size());
		for (File buildingListFile : buildingListFiles) {
			assertTrue("BuildingList.txt.gz should have building entries", buildingListFile.length() > 20);
			List<String> buildingLines = readGzipLines(buildingListFile);
			assertTrue("No building entries in " + buildingListFile.getName(), buildingLines.size() > 0);
			for (String line : buildingLines) {
				String[] parts = line.trim().split("\\s+");
				assertEquals("Expected 14 fields in building entry: " + line, 14, parts.length);
			}
		}
		List<File> stgFiles = findFiles(targetDir, ".stg");
		assertNotNull("No .stg files found", stgFiles);
		assertTrue("No .stg files found", stgFiles.size() > 0);

		// ---- Shared Models folders copied --------------------------------
		File modelsObjectsDir = new File(targetDir, "Models/objects");
		File modelsSpecObjectsDir = new File(targetDir, "Models/specobjects");
		assertTrue("Models/objects folder not copied into " + targetDir, modelsObjectsDir.isDirectory());
		assertTrue("Models/specobjects folder not copied into " + targetDir, modelsSpecObjectsDir.isDirectory());
		List<File> modelFiles = findFiles(modelsObjectsDir, ".ac");
		assertTrue("No .ac models found under Models/objects", modelFiles.size() > 0);
		List<File> specObjectFiles = findFiles(modelsSpecObjectsDir, ".ac");
		assertTrue("No .ac models found under Models/specobjects", specObjectFiles.size() > 0);
		System.out.println("Models/objects .ac count: " + modelFiles.size()
				+ ", Models/specobjects .ac count: " + specObjectFiles.size());

		// ---- Transportation reports --------------------------------------
		File[] reportFiles = targetDir
				.listFiles((dir, name) -> name.startsWith("transportation_report_") && name.endsWith(".txt"));
		assertTrue("No transportation reports found", reportFiles != null && reportFiles.length > 0);
		System.out.println("Transportation reports: " + reportFiles.length);

		// ---- Per-feature STG declarations --------------------------------
		int roadLineFeatures = 0;
		int railLineFeatures = 0;
		int powerlineComments = 0;
		int craneDecls = 0;
		int buildingListHeaders = 0;
		int specObjectDecls = 0;
		int houseDecls = 0;
		int industrialDecls = 0;
		int waterTowerDecls = 0;
		int windTurbineDecls = 0;
		Set<String> chimneyModels = new HashSet<>();
		Set<String> coolingTowerModels = new HashSet<>();
		for (File stgFile : stgFiles) {
			List<String> lines = Files.readAllLines(stgFile.toPath(), Charset.forName("UTF-8"));
			for (String line : lines) {
				if (line.startsWith("LINE_FEATURE_LIST") && line.contains("ws30Railway")) {
					railLineFeatures++;
				}
				if (line.startsWith("LINE_FEATURE_LIST")
						&& (line.contains("ws30Road") || line.contains("ws30Freeway"))) {
					roadLineFeatures++;
				}
				if (line.startsWith("OBJECT_SHARED_AGL Models/Power/")) {
					powerlineComments++;
				}
				if (line.startsWith("BUILDING_LIST")) {
					buildingListHeaders++;
				}
				if (line.startsWith("OBJECT_SHARED_AGL Models/specobjects/")) {
					specObjectDecls++;
					String modelName = specModelName(line);
					if (modelName.startsWith("chimney")) {
						chimneyModels.add(modelName);
					}
					if (modelName.startsWith("cooling_tower")) {
						coolingTowerModels.add(modelName);
					}
				}
				if (line.startsWith("OBJECT_SHARED_AGL Models/objects/house/")) {
					houseDecls++;
				}
				if (line.startsWith("OBJECT_SHARED_AGL Models/objects/industrial/")) {
					industrialDecls++;
				}
				if (line.startsWith("OBJECT_SHARED_AGL Models/objects/watertower-3")) {
					waterTowerDecls++;
				}
				if (line.startsWith("OBJECT_SHARED_AGL Models/objects/wind_turbine")) {
					windTurbineDecls++;
				}
				if (line.contains("OBJECT_SHARED_AGL Models/objects/crane")) {
					craneDecls++;
				}
			}
		}

		System.out.println("STG declarations -> road LINE_FEATURE_LIST: " + roadLineFeatures
				+ ", rail LINE_FEATURE_LIST: " + railLineFeatures + ", powerline pylons: " + powerlineComments
				+ ", BUILDING_LIST headers: " + buildingListHeaders);
		System.out.println("STG object declarations -> specobjects(size): " + specObjectDecls
				+ ", chimney sizes: " + chimneyModels.size() + ", cooling_tower sizes: " + coolingTowerModels.size()
				+ ", house(size): " + houseDecls + ", industrial(size): " + industrialDecls
				+ ", water_tower(rule): " + waterTowerDecls + ", wind_turbine(rule): " + windTurbineDecls
				+ ", crane(rule): " + craneDecls);
		// validate the cfg by asserting an airfield/transport tile exists
		// (scenery tiles have content in this region)

		// Roads
		assertTrue("No road LINE_FEATURE_LIST found in .stg files", roadLineFeatures > 0);
		// Power lines
		assertTrue("No pylon OBJECT_SHARED_AGL declarations found in .stg files", powerlineComments > 0);
		// Building list headers referencing the BuildingList files
		assertTrue("No BUILDING_LIST found in .stg files", buildingListHeaders > 0);
		// Rule-based 3D objects (water towers + wind turbines + cranes)
		assertTrue("No rule-based specobject declarations (chimney/cooling_tower) found",
				specObjectDecls > 0);
		assertTrue("No chimney OBJECT_SHARED_AGL declarations found", chimneyModels.size() > 0);
		assertTrue("No cooling_tower OBJECT_SHARED_AGL declarations found", coolingTowerModels.size() > 0);
		assertTrue("Expected multiple chimney sizes to be selected, found: " + chimneyModels,
				chimneyModels.size() > 1);
		assertTrue("No water_tower OBJECT_SHARED_AGL declarations found", waterTowerDecls > 0);
		assertTrue("No wind_turbine OBJECT_SHARED_AGL declarations found", windTurbineDecls > 0);
		assertTrue("No crane OBJECT_SHARED_AGL declarations found", craneDecls > 0);
		// Building-size 3D models (house + industrial)
		assertTrue("No house building-size OBJECT_SHARED_AGL declarations found", houseDecls > 0);
		assertTrue("No industrial building-size OBJECT_SHARED_AGL declarations found", industrialDecls > 0);
		// Stats counters for transported features (road/railway/powerline bulk counts are
		// tracked per-tile; the whole-region road count is large)
		int roadsCount = commonStats.getCount("road");
		int powerlinesCount = commonStats.getCount("powerline");
		System.out.println("StatsProvider road=" + roadsCount + " powerline=" + powerlinesCount);
		assertTrue("StatsProvider reports " + roadsCount + " roads, should be > 0", roadsCount > 0);
		if (powerlinesCount > 0) {
			System.out.println("  Powerlines counted in StatsProvider: " + powerlinesCount);
		}
	}

	private String specModelName(String line) {
		String prefix = "Models/specobjects/";
		int start = line.indexOf(prefix) + prefix.length();
		int end = line.indexOf(".ac", start) + ".ac".length();
		return line.substring(start, end);
	}

	private List<File> findFiles(File dir, String suffix) {
		List<File> result = new ArrayList<>();
		if (dir == null || !dir.isDirectory()) {
			return result;
		}
		File[] children = dir.listFiles();
		if (children == null) {
			return result;
		}
		for (File child : children) {
			if (child.isDirectory()) {
				result.addAll(findFiles(child, suffix));
			} else if (child.getName().endsWith(suffix)) {
				result.add(child);
			}
		}
		return result;
	}

	private List<String> readGzipLines(File gzFile) throws IOException {
		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new GZIPInputStream(new FileInputStream(gzFile)), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		}
		return lines;
	}

	private boolean isTestRootFolder(File basicFolder) {
		return new File(basicFolder.getAbsolutePath(), "testdata").isDirectory();
	}
}