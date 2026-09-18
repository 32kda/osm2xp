package com.osm2xp.console;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import static org.junit.Assume.assumeTrue;

import com.osm2xp.core.parsers.btg.Btg;
import com.osm2xp.core.parsers.btg.BtgFace;
import com.osm2xp.core.parsers.btg.BtgTile;
import com.osm2xp.generation.options.FlightGearOptionsProvider;

import junit.framework.TestCase;

/**
 * End-to-end integration test for the experimental airfield terrain cut (spec
 * &sect;8.3), modeled on {@link TransportationIntegrationTest}.
 * <p>
 * Requires the scenario PBF and a TerraSync root that already has the matching
 * terrain tiles; both are skipped via {@code assumeTrue} when absent.
 */
public class AirfieldBtgCutIntegrationTest extends TestCase {

	private static final String TESTSCENERY_NAME = "testscenery_btgcut";
	private static final String DEFAULT_PBF = "H:\\tmp\\planet_77.418,51.277_88.406,56.055.osm.pbf";
	private static final String DEFAULT_TERRASYNC = "D:\\Games\\FlightGear 2024.1\\TerraSync";

	@Test
	public void testFlightGearAirfieldBtgCut() throws Exception {
		File repoRoot = new File(new File("").getAbsolutePath());
		while (repoRoot != null && !isTestRootFolder(repoRoot)) {
			repoRoot = repoRoot.getParentFile();
		}
		assumeTrue("Could not find testdata root", repoRoot != null);
		File basicFolder = new File(repoRoot, "testdata");
		assumeTrue("testdata directory not found", basicFolder.isDirectory());

		String pbfPath = System.getProperty("osm.pbf.path", DEFAULT_PBF);
		File pbfFile = new File(pbfPath);
		assumeTrue("PBF file not found: " + pbfPath, pbfFile.isFile());

		String terrasyncPath = System.getProperty("flightgear.terrasync.path", DEFAULT_TERRASYNC);
		File terrasync = new File(terrasyncPath);
		assumeTrue("TerraSync terrain not found: " + terrasyncPath, terrasync.isDirectory());

		File targetDir = new File(pbfFile.getParentFile(), TESTSCENERY_NAME);
		FileUtils.deleteDirectory(targetDir);
		targetDir.mkdirs();

		FlightGearOptionsProvider.getOptions().setGenerateAirfieldsBtg(true);
		FlightGearOptionsProvider.getOptions().setGenerateAirfieldsBtgCut(true);
		FlightGearOptionsProvider.getOptions().setFlightGearSceneryPath(terrasync.getAbsolutePath());
		FlightGearOptionsProvider.getOptions().setFgelevPath("D:\\Games\\FlightGear 2024.1\\bin\\fgelev.exe");
//		FlightGearOptionsProvider.getOptions().setFlightGearSceneryPath("D:\\Games\\FlightGear 2024.1\\TerraSync");

		List<String> argList = new ArrayList<>();
		argList.add(pbfFile.getAbsolutePath());
		argList.add("-m");
		argList.add("FLIGHT_GEAR");
		argList.add("-c");
		argList.add(basicFolder.getAbsolutePath());
		argList.add("-s");
		argList.add(TESTSCENERY_NAME);
		com.osm2xp.console.App.main(argList.toArray(new String[0]));

		assertTrue("Target directory not created", targetDir.isDirectory());

		List<File> btgFiles = findFiles(new File(targetDir, "Terrain"), ".btg.gz");
		assertNotNull("No terrain BTG files found", btgFiles);
		assertTrue("No terrain BTG files produced", btgFiles.size() > 0);

		int readable = 0;
		int withAirfieldMaterial = 0;
		for (File btgFile : btgFiles) {
			BtgTile tile = Btg.read(btgFile);
			assertNotNull("Failed to parse " + btgFile, tile);
			assertTrue("Tile has no vertices: " + btgFile, tile.getVertexCount() > 0);
			assertTrue("Tile has no faces: " + btgFile, tile.getFaceCount() > 0);
			if (hasAirfieldMaterial(tile)) {
				withAirfieldMaterial++;
			}
			readable++;
		}
		assertTrue("No readable terrain BTG files", readable > 0);
		System.out.println("Tiles with airfield plate material: " + withAirfieldMaterial + " / " + readable);

//		assertReadableByPython(repoRoot, btgFiles);
	}

//	private void assertReadableByPython(File repoRoot, List<File> btgFiles) throws Exception {
//		File srcDir = new File(repoRoot, "example/Blender-Flightgear-BTG-Import-Export-main/src");
//		String python = findPython();
//		assumeTrue("No Python interpreter available, skipping Python readability check", python != null);
//		assumeTrue("Blender BTG module not found at " + srcDir, srcDir.isDirectory());
//
//		String script = "import sys, gzip, tempfile, os\n" + "sys.path.insert(0, sys.argv[1])\n"
//				+ "import fg_btg_btgio\n" + "failures = []\n" + "count = 0\n" + "for gz in sys.argv[2:]:\n"
//				+ "    with open(gz, 'rb') as f:\n" + "        raw = f.read()\n" + "    if raw[:2] == b'\\x1f\\x8b':\n"
//				+ "        raw = gzip.decompress(raw)\n" + "    fd, tmp = tempfile.mkstemp(suffix='.btg')\n"
//				+ "    with os.fdopen(fd, 'wb') as f:\n" + "        f.write(raw)\n" + "    try:\n"
//				+ "        data = fg_btg_btgio.parse_btg(tmp)\n" + "        if not data.vertices or not data.faces:\n"
//				+ "            failures.append(gz)\n" + "        else:\n" + "            count += 1\n"
//				+ "    finally:\n" + "        os.remove(tmp)\n" + "if failures:\n" + "    print('FAIL', failures)\n"
//				+ "    sys.exit(1)\n" + "print('OK', count)\n";
//
//		List<String> command = new ArrayList<>();
//		command.add(python);
//		command.add("-c");
//		command.add(script);
//		command.add(srcDir.getAbsolutePath());
//		for (File btgFile : btgFiles) {
//			command.add(btgFile.getAbsolutePath());
//		}
//
//		ProcessBuilder processBuilder = new ProcessBuilder(command);
//		processBuilder.redirectErrorStream(true);
//		Process process = processBuilder.start();
//		java.io.BufferedReader reader = new java.io.BufferedReader(
//				new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
//		StringBuilder output = new StringBuilder();
//		String line;
//		while ((line = reader.readLine()) != null) {
//			output.append(line).append('\n');
//		}
//		int exitCode = process.waitFor();
//		assertTrue("Python BTG parse failed (exit " + exitCode + "):\n" + output, exitCode == 0);
//	}

	private String findPython() {
		for (String candidate : new String[] { "python3", "python" }) {
			try {
				Process process = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
				process.getInputStream().close();
				int exit = process.waitFor();
				if (exit == 0) {
					return candidate;
				}
			} catch (Exception ignored) {
				// try next candidate
			}
		}
		return null;
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

	private boolean isTestRootFolder(File folder) {
		return new File(folder.getAbsolutePath(), "testdata").isDirectory();
	}

	private static boolean hasAirfieldMaterial(BtgTile tile) {
		for (BtgFace face : tile.getFaces()) {
			String material = face.getMaterial();
			if (material != null && (material.startsWith("pa_") || material.startsWith("pc_")
					|| material.startsWith("grass_rwy") || material.startsWith("dirt_rwy"))) {
				return true;
			}
		}
		return false;
	}
}
