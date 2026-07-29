package com.osm2xp.console;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.junit.Test;

import com.google.common.io.Files;
import com.osm2xp.stats.StatsProvider;

import junit.framework.TestCase;

public class ConsoleAppIntegrationTest extends TestCase {
	
	private static final String TESTSCENERY_NAME = "testscenery";

	@Test
	public void testGeneration() throws Exception {
		File basicFolder = new File(new File("").getAbsolutePath()); 
		while (basicFolder != null && !isTestRootFolder(basicFolder)) {
			basicFolder = basicFolder.getParentFile();
		}
		assertNotNull(basicFolder);
		basicFolder = new File(basicFolder, "testdata");
		assertTrue(basicFolder.isDirectory());
		File targetDir = new File(basicFolder,TESTSCENERY_NAME);
		FileUtils.deleteDirectory(targetDir);
		StatsProvider.reinit();
		List<String> argList = new ArrayList<String>();
		argList.add(new File(basicFolder, "volchikha.osm.pbf").getAbsolutePath());
		argList.add("-m");
		argList.add("xplane10");
		argList.add("-c");
		argList.add(basicFolder.getAbsolutePath());
		argList.add("-s");
		argList.add(TESTSCENERY_NAME);
		com.osm2xp.console.App.main(argList.toArray(new String[0]));
		assertTrue(targetDir.isDirectory());
		String[] subdirs = targetDir.list();
		assertTrue(ArrayUtils.contains(subdirs, "facades"));
		assertTrue(ArrayUtils.contains(subdirs, "forests"));
		assertTrue(ArrayUtils.contains(subdirs, "objects"));
		assertTrue(ArrayUtils.contains(subdirs, "specobjects"));
		File aptFile = new File(targetDir, "Earth nav data/apt.dat");
		checkApt(aptFile);
		File dsfFile = new File(targetDir, "Earth nav data/+50+080/+52+080.dsf.txt");
		checkDsf(dsfFile);
		//TODO check result folder structure and file contents
	}

	private void checkApt(File aptFile) throws IOException {
		assertTrue(aptFile.isFile());
		List<String> lines = Files.readLines(aptFile, Charset.forName("UTF-8"));
		assertTrue(lines.contains("1 669 0 0 xx00 Volchikha Airport")); 
		assertTrue(lines.contains("1302 flatten 1"));
		assertTrue(lines.contains("1302 datum_lat 52.025411"));
		assertTrue(lines.contains("1302 datum_lon 80.337624"));
		assertTrue(lines.contains("100 40.00 3 0 0.25 0 0 0 03 52.02056120 80.33193670 0 0  0 0 0 0 21 52.03026100 80.34331140 0 0  0 0 0 0 # length 1329m"));
		assertTrue(lines.contains("102 H1 52.02050385 80.33587750 123.28 29.49 27.35 3 0 0 0.3 0"));
		assertTrue(lines.contains("102 H2 52.02000245 80.33527335 123.28 29.50 27.35 3 0 0 0.3 0"));
	}
	private void checkDsf(File dsfFile) throws IOException {
		assertTrue(dsfFile.isFile());
		List<String> lines = Files.readLines(dsfFile, Charset.forName("UTF-8"));
		assertTrue(lines.contains("PROPERTY sim/planet earth"));
		assertTrue(lines.contains("PROPERTY sim/overlay 1"));
		assertTrue(lines.contains("PROPERTY sim/require_object 1/0"));
		assertTrue(lines.contains("PROPERTY sim/require_facade 3/0"));
		assertTrue(lines.contains("PROPERTY sim/exclude_obj 80.327000000/52.017000000/80.348000000/52.034000000"));
		assertTrue(lines.contains("PROPERTY sim/exclude_for 80.327000000/52.017000000/80.348000000/52.034000000"));
		assertTrue(lines.contains("PROPERTY sim/west 80"));
		assertTrue(lines.contains("PROPERTY sim/east 81"));
		assertTrue(lines.contains("PROPERTY sim/north 53"));
		assertTrue(lines.contains("PROPERTY sim/south 52"));
		assertTrue(lines.contains("NETWORK_DEF lib/g10/roads.net"));
//		assertTrue(lines.contains("OBJECT 2 80.3357702 52.019886500000005 191.0"));
		
		//Basic structure check is below
		int polDefCount = 0;
		int objDefCount = 0;
		int stack = 0;
		boolean hasWindings = false;
		boolean hasSegments = false;
		boolean hasObjects = false;
		for (String line : lines) {
			if (line.startsWith("POLYGON_DEF")) {
				polDefCount++;
			} else if (line.startsWith("OBJECT_DEF")) {
				objDefCount++;
			} else if (line.startsWith("BEGIN_")) {
				if (line.startsWith("BEGIN_WINDING")) {
					hasWindings = true;
				}
				if (line.startsWith("BEGIN_SEGMENT")) {
					hasSegments = true;
				}
				stack++;
			} else if (line.startsWith("END_")){
				stack--;
			} else if (line.startsWith("OBJECT ")){ //TODO was OBJECT 2
				hasObjects = true;
			}
		}
		assertTrue(hasWindings);
		assertTrue(hasSegments);
		assertTrue(hasObjects);
		assertEquals(26, polDefCount);
		assertEquals(6, objDefCount);
		assertEquals(0, stack);
	}

	@Test
	public void testFlightGearGeneration() throws Exception {
		File basicFolder = new File(new File("").getAbsolutePath());
		while (basicFolder != null && !isTestRootFolder(basicFolder)) {
			basicFolder = basicFolder.getParentFile();
		}
		assertNotNull(basicFolder);
		basicFolder = new File(basicFolder, "testdata");
		assertTrue(basicFolder.isDirectory());
		File targetDir = new File(basicFolder, TESTSCENERY_NAME);
		FileUtils.deleteDirectory(targetDir);
		StatsProvider.reinit();
		List<String> argList = new ArrayList<String>();
		argList.add(new File(basicFolder, "volchikha.osm.pbf").getAbsolutePath());
		argList.add("-m");
		argList.add("FLIGHT_GEAR");
		argList.add("-c");
		argList.add(basicFolder.getAbsolutePath());
		argList.add("-s");
		argList.add(TESTSCENERY_NAME);
		com.osm2xp.console.App.main(argList.toArray(new String[0]));
		assertTrue(targetDir.isDirectory());
		// Check BuildingList.txt.gz exists (even if empty) when generateBuildings=true
		File buildingListFile = new File(targetDir, "BuildingList.txt.gz");
		if (!buildingListFile.isFile()) {
			File[] gzFiles = targetDir.listFiles((dir, name) -> name.equals("BuildingList.txt.gz"));
			if (gzFiles != null && gzFiles.length > 0) {
				buildingListFile = gzFiles[0];
			}
		}
		assertTrue("BuildingList.txt.gz not found", buildingListFile.isFile());
		// Check for .stg files with BUILDING_LIST header
		File[] stgFiles = targetDir.listFiles((dir, name) -> name.endsWith(".stg"));
		assertNotNull("No .stg files found", stgFiles);
		assertTrue("No .stg files found", stgFiles.length > 0);
		boolean foundBuildingList = false;
		for (File stgFile : stgFiles) {
			List<String> lines = java.nio.file.Files.readAllLines(stgFile.toPath(), Charset.forName("UTF-8"));
			for (String line : lines) {
				if (line.startsWith("BUILDING_LIST")) {
					foundBuildingList = true;
					break;
				}
			}
		}
		assertTrue("No BUILDING_LIST found in .stg files", foundBuildingList);
		// BuildingList.txt.gz should have entries when BUILDING_LIST header present
		assertTrue("BuildingList.txt.gz should have building entries", buildingListFile.length() > 20);
		// Validate each building entry
		List<String> buildingLines = readGzipLines(buildingListFile);
		assertTrue("No building entries in BuildingList.txt.gz", buildingLines.size() > 0);
		for (String line : buildingLines) {
			String[] parts = line.trim().split("\\s+");
			assertEquals("Expected 14 fields in building entry: " + line, 14, parts.length);
			double anchorLat = Double.parseDouble(parts[0]);
			double anchorLon = Double.parseDouble(parts[1]);
			double elevation = Double.parseDouble(parts[2]);
			double streetAngle = Double.parseDouble(parts[3]);
			int listType = Integer.parseInt(parts[4]);
			double width = Double.parseDouble(parts[5]);
			double depth = Double.parseDouble(parts[6]);
			double facadeHeight = Double.parseDouble(parts[7]);
			double roofHeight = Double.parseDouble(parts[8]);
			int roofShape = Integer.parseInt(parts[9]);
			int roofOrientation = Integer.parseInt(parts[10]);
			int levels = Integer.parseInt(parts[11]);
			// Coordinate sanity
			assertTrue("Latitude out of range: " + anchorLat, anchorLat >= -90 && anchorLat <= 90);
			assertTrue("Longitude out of range: " + anchorLon, anchorLon >= -180 && anchorLon <= 180);
			// Height checks: facadeHeight > 1m and < 1000m
			assertTrue("Facade height too low: " + facadeHeight, facadeHeight > 1.0);
			assertTrue("Facade height too high: " + facadeHeight, facadeHeight < 1000.0);
			assertTrue("Total height too high: " + (facadeHeight + roofHeight), facadeHeight + roofHeight < 1000.0);
			// Perimeter: > 5m and < 5km
			double perimeter = 2.0 * (width + depth);
			assertTrue("Perimeter too small: " + perimeter, perimeter > 5.0);
			assertTrue("Perimeter too large: " + perimeter, perimeter < 5000.0);
			// Each side < 5km
			assertTrue("Width too large: " + width, width < 5000.0);
			assertTrue("Depth too large: " + depth, depth < 5000.0);
			// Street angle in [0, 360)
			assertTrue("Street angle out of range: " + streetAngle, streetAngle >= 0 && streetAngle < 360);
			// Roof orientation is 0 or 1
			assertTrue("Invalid roofOrientation: " + roofOrientation, roofOrientation == 0 || roofOrientation == 1);
			// Levels >= 1
			assertTrue("Levels < 1: " + levels, levels >= 1);
		}
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
		return new File(basicFolder.getAbsolutePath(),"testdata").isDirectory();
	}
	
}
