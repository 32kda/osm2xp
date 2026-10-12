package com.osm2xp.generation.dsf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Round-trip tests against a real OSM2XP generated scenery text corpus.
 */
public class DsfCorpusTest {

	private static final Path CORPUS = Paths.get("H:/tmp/planet_77/Earth nav data");

	@Before
	public void checkCorpus() {
		Assume.assumeTrue("Corpus not available: " + CORPUS, Files.isDirectory(CORPUS));
	}

	@Test
	public void roundTripsCorpusFiles() throws IOException {
		List<Path> samples = DsfModelTestSupport.findCorpusSamples(CORPUS, 6, 2_000_000);
		Assume.assumeFalse("No corpus sample files found under " + CORPUS, samples.isEmpty());

		int compared = 0;
		for (Path source : samples) {
			DsfModel original = DsfModel.fromText(source);
			if (original.polygons.stream().anyMatch(p -> p.pointCount() > 65535)) {
				// Such polygons are not representable in a single DSF command
				// and are intentionally skipped by the writer.
				continue;
			}
			compared++;
			byte[] dsf = DsfConverter.textToBinary(source);
			DsfModelTestSupport.assertEquivalent(source.getFileName() + " text -> dsf", original,
					DsfModel.fromBinary(dsf));

			Path roundTrip = Files.createTempFile("dsf-roundtrip", ".dsf.txt");
			try {
				Files.write(roundTrip, DsfConverter.binaryToText(dsf).getBytes(StandardCharsets.UTF_8));
				DsfModelTestSupport.assertEquivalent(source.getFileName() + " text -> dsf -> text", original,
						DsfModel.fromText(roundTrip));
			} finally {
				Files.deleteIfExists(roundTrip);
			}
		}
		Assume.assumeTrue("All corpus samples contained unrepresentable polygons", compared > 0);
	}
}
