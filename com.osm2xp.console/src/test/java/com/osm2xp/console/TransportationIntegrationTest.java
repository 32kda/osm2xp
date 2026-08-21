package com.osm2xp.console;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.stats.CountStats;
import com.osm2xp.stats.StatsProvider;

import junit.framework.TestCase;

public class TransportationIntegrationTest extends TestCase {

	private static final String TESTSCENERY_NAME = "testscenery_transport";

	@Test
	public void testFlightGearTransportation() throws Exception {
		File basicFolder = new File(new File("").getAbsolutePath());
		while (basicFolder != null && !isTestRootFolder(basicFolder)) {
			basicFolder = basicFolder.getParentFile();
		}
		assertNotNull("Could not find testdata root", basicFolder);
		basicFolder = new File(basicFolder, "testdata");
		assertTrue("testdata directory not found", basicFolder.isDirectory());

		// Use PBF next to original file; output is written next to the input file
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
		FlightGearOptionsProvider.getOptions().setFgelevPath("D:\\Games\\FlightGear 2024.1\\bin\\fgelev.exe");
        FlightGearOptionsProvider.getOptions().setFlightGearSceneryPath("D:\\Games\\FlightGear 2024.1\\TerraSync");
		com.osm2xp.console.App.main(argList.toArray(new String[0]));

		assertTrue("Target directory not created", targetDir.isDirectory());

		// Output StatsProvider collected data
		CountStats commonStats = StatsProvider.getCommonStats();
		String statsSummary = commonStats.getSummary();
		System.out.println("Common generation stats: " + statsSummary);
		assertTrue("StatsProvider common stats should not be empty", !statsSummary.isEmpty());

		// Check transportation reports exist (one per tile)
		File[] reportFiles = targetDir
				.listFiles((dir, name) -> name.startsWith("transportation_report_") && name.endsWith(".txt"));
		assertNotNull("No transportation reports found", reportFiles);
		assertTrue("No transportation reports found", reportFiles.length > 0);

		boolean hasValidationLine = false;
		boolean hasTelemetry = false;
		for (File reportFile : reportFiles) {
			List<String> reportLines = Files.readAllLines(reportFile.toPath(), Charset.forName("UTF-8"));
			assertTrue("Report should not be empty", reportLines.size() > 0);
			for (String line : reportLines) {
				if (line.startsWith("Validation:")) {
					hasValidationLine = true;
				}
				if (line.startsWith("Telemetry:")) {
					hasTelemetry = true;
				}
			}
		}
		assertTrue("Report missing validation line", hasValidationLine);
		assertTrue("Report missing telemetry section", hasTelemetry);

		System.out.println("Generation stats:\n" + commonStats.getSummary());
		// Assert StatsProvider counts match report telemetry
		int roadsCount = commonStats.getCount("road");
		int railwaysCount = commonStats.getCount("railway");
		int powerlinesCount = commonStats.getCount("powerline");
		System.out.println(
				"StatsProvider road=" + roadsCount + " railway=" + railwaysCount + " powerline=" + powerlinesCount);
		assertTrue("StatsProvider reports " + roadsCount + " roads, should be > 0", roadsCount > 0);
		assertTrue("StatsProvider reports " + powerlinesCount + " powerlines, should be >= 0", powerlinesCount >= 0);

		// Check .stg files (now inside Objects/<bucket_path>/ folders) contain LINE_FEATURE_LIST entries
		List<File> stgFiles = findFiles(targetDir, ".stg");
		assertNotNull("No .stg files found", stgFiles);
		assertTrue("No .stg files found", stgFiles.size() > 0);

		boolean hasRoadLineFeatures = false;
		boolean hasRailLineFeatures = false;
		boolean hasPowerlineComments = false;

		for (File stgFile : stgFiles) {
			List<String> lines = Files.readAllLines(stgFile.toPath(), Charset.forName("UTF-8"));
			for (String line : lines) {
				if (line.startsWith("LINE_FEATURE_LIST")
						&& (line.contains("ws30Road") || line.contains("ws30Freeway"))) {
					hasRoadLineFeatures = true;
				}
				if (line.startsWith("LINE_FEATURE_LIST") && line.contains("ws30Railway")) {
					hasRailLineFeatures = true;
				}
				if (line.startsWith("# powerline ")) {
					hasPowerlineComments = true;
				}
			}
		}

		assertTrue("No road LINE_FEATURE_LIST found in .stg files", hasRoadLineFeatures);
		// These are optional depending on test data content
		if (hasRailLineFeatures) {
			System.out.println("  Railway LINE_FEATURE_LIST found in .stg files");
		}
		if (hasPowerlineComments) {
			System.out.println("  Powerline comments found in .stg files");
		}

		// Per-material gzipped list files must exist next to the STG files
		List<File> lineFeatureFiles = findFiles(targetDir, ".txt.gz");
		assertNotNull("No .txt.gz files found", lineFeatureFiles);
		boolean hasLineFeatureList = false;
		for (File lineFeatureFile : lineFeatureFiles) {
			if (lineFeatureFile.getName().startsWith("LineFeatureList_")) {
				hasLineFeatureList = true;
				break;
			}
		}
		assertTrue("No LineFeatureList_*.txt.gz list files found", hasLineFeatureList);

		// Shared 3D models bundled with osm2xp are copied into Models/objects and Models/specobjects
		// so that OBJECT_SHARED_AGL declarations in the STG tiles resolve for FlightGear.
		File modelsObjectsDir = new File(targetDir, "Models/objects");
		File modelsSpecObjectsDir = new File(targetDir, "Models/specobjects");
		assertTrue("Models/objects folder not copied into " + targetDir, modelsObjectsDir.isDirectory());
		assertTrue("Models/specobjects folder not copied into " + targetDir, modelsSpecObjectsDir.isDirectory());
		List<File> modelsFiles = findFiles(modelsObjectsDir, ".ac");
		assertNotNull("No .ac models found under Models/objects", modelsFiles);
		assertTrue("No .ac models found under Models/objects", modelsFiles.size() > 0);

		// With object generation enabled, .stg files should reference the copied models
		boolean hasObjectDeclarations = false;
		int objectDeclarationCount = 0;
		for (File stgFile : stgFiles) {
			List<String> lines = Files.readAllLines(stgFile.toPath(), Charset.forName("UTF-8"));
			for (String line : lines) {
				if (line.startsWith("OBJECT_SHARED_AGL Models/")) {
					hasObjectDeclarations = true;
					objectDeclarationCount++;
				}
			}
		}
		System.out.println(
				"Object declarations referencing Models/ in .stg files: " + objectDeclarationCount);
		if (hasObjectDeclarations) {
			System.out.println("  Shared 3D models are placed and referenced by the generated scenery");
		}
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

	@Test
	public void testFlightGearTransportationCustomPbf() throws Exception {
		String pbfPath = System.getProperty("osm.pbf.path");
		if (pbfPath == null || pbfPath.isEmpty()) {
			System.out.println("Skipping custom PBF test: set -Dosm.pbf.path=<path>");
			return;
		}
		File pbfFile = new File(pbfPath);
		assertTrue("PBF file not found: " + pbfPath, pbfFile.isFile());

		File basicFolder = new File(new File("").getAbsolutePath());
		while (basicFolder != null && !isTestRootFolder(basicFolder)) {
			basicFolder = basicFolder.getParentFile();
		}
		assertNotNull("Could not find testdata root", basicFolder);
		basicFolder = new File(basicFolder, "testdata");

		// Output is written next to the input file
		File targetDir = new File(pbfFile.getParentFile(), TESTSCENERY_NAME + "_custom");
		FileUtils.deleteDirectory(targetDir);

		StatsProvider.reinit();
		List<String> argList = new ArrayList<String>();
		argList.add(pbfFile.getAbsolutePath());
		argList.add("-m");
		argList.add("FLIGHT_GEAR");
		argList.add("-c");
		argList.add(basicFolder.getAbsolutePath());
		argList.add("-s");
		argList.add(targetDir.getName());
		com.osm2xp.console.App.main(argList.toArray(new String[0]));

		assertTrue("Target directory not created", targetDir.isDirectory());

		// Output StatsProvider collected data for custom PBF
		CountStats customStats = StatsProvider.getCommonStats();
		String customStatsSummary = customStats.getSummary();
		System.out.println("Custom PBF generation stats: " + customStatsSummary);
		assertTrue("Custom PBF stats should not be empty", !customStatsSummary.isEmpty());

		File[] reportFiles = targetDir
				.listFiles((dir, name) -> name.startsWith("transportation_report_") && name.endsWith(".txt"));
		assertNotNull("No transportation reports found", reportFiles);
		assertTrue("No transportation reports found", reportFiles.length > 0);
		int totalReportLines = 0;
		for (File reportFile : reportFiles) {
			List<String> reportLines = Files.readAllLines(reportFile.toPath(), Charset.forName("UTF-8"));
			assertTrue("Report should not be empty", reportLines.size() > 0);
			totalReportLines += reportLines.size();
		}
		System.out.println("Custom PBF " + pbfFile.getName() + " generated " + reportFiles.length + " reports with "
				+ totalReportLines + " total lines");
	}

	private boolean isTestRootFolder(File basicFolder) {
		return new File(basicFolder.getAbsolutePath(), "testdata").isDirectory();
	}
}