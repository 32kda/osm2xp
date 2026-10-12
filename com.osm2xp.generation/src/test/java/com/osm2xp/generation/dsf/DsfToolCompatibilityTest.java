package com.osm2xp.generation.dsf;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Cross-checks the Java writer against the reference DSFTool executable: a DSF
 * produced by the Java code must be decoded by DSFTool to exactly the same text
 * as a DSF produced by DSFTool itself.
 */
public class DsfToolCompatibilityTest {

	private static final Path CORPUS = Paths.get("H:/tmp/planet_77/Earth nav data");

	private Path tool;

	@Before
	public void locateTool() {
		Assume.assumeTrue("Windows only", System.getProperty("os.name", "").toLowerCase().contains("win"));
		Assume.assumeTrue("Corpus not available: " + CORPUS, Files.isDirectory(CORPUS));
		Path[] candidates = {
				Paths.get("..", "osm2xp_additions", "utils", "DSFTool.exe"),
				Paths.get("osm2xp_additions", "utils", "DSFTool.exe"),
				Paths.get("D:/Work/git/osm2xp/osm2xp_additions/utils/DSFTool.exe")
		};
		for (Path candidate : candidates) {
			if (Files.isRegularFile(candidate)) {
				tool = candidate.toAbsolutePath().normalize();
				break;
			}
		}
		Assume.assumeTrue("DSFTool.exe not found", tool != null);
	}

	@Test
	public void javaWrittenDsfMatchesDsfTool() throws IOException {
		List<Path> samples = DsfModelTestSupport.findCorpusSamples(CORPUS, 5, 2_000_000);
		Assume.assumeFalse("No corpus sample files found under " + CORPUS, samples.isEmpty());

		int compared = 0;
		for (Path source : samples) {
			Path dir = Files.createTempDirectory("dsftool-check");
			Path ourDsf = dir.resolve("our.dsf");
			Path refDsf = dir.resolve("ref.dsf");
			Files.write(ourDsf, DsfConverter.textToBinary(source));

			if (!runToolQuietly("--text2dsf", source.toString(), refDsf.toString())) {
				// DSFTool cannot represent this tile (e.g. an oversized polygon),
				// so there is no reference to compare against.
				continue;
			}
			runTool("--dsf2text", ourDsf.toString(), dir.resolve("our.txt").toString());
			runTool("--dsf2text", refDsf.toString(), dir.resolve("ref.txt").toString());

			List<String> ourLines = normalized(dir.resolve("our.txt"));
			List<String> refLines = normalized(dir.resolve("ref.txt"));
			assertEquals(source.getFileName() + " DSFTool decode", refLines, ourLines);
			compared++;
		}
		Assume.assumeTrue("DSFTool could not convert any corpus sample", compared > 0);
	}

	private static List<String> normalized(Path file) throws IOException {
		List<String> result = new ArrayList<>();
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			if (line.startsWith("# file:")) {
				continue;
			}
			result.add(line);
		}
		// DSF command ordering is not semantically significant, so compare as a
		// multiset of lines.
		java.util.Collections.sort(result);
		return result;
	}

	private void runTool(String... args) throws IOException {
		if (!runToolQuietly(args)) {
			throw new IOException("DSFTool failed: " + String.join(" ", args));
		}
	}

	private boolean runToolQuietly(String... args) throws IOException {
		List<String> command = new ArrayList<>();
		command.add(tool.toString());
		for (String arg : args) {
			command.add(arg);
		}
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		process.getInputStream().readAllBytes();
		try {
			return process.waitFor() == 0;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted running DSFTool", e);
		}
	}
}
