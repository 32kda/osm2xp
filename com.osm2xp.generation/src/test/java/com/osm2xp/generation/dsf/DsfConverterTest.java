package com.osm2xp.generation.dsf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DsfConverterTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private static final String SAMPLE = "I\n" //
			+ "800\n" //
			+ "DSF2TEXT\n\n" //
			+ "PROPERTY sim/planet earth\n" //
			+ "PROPERTY sim/overlay 1\n" //
			+ "PROPERTY sim/west 0\n" //
			+ "PROPERTY sim/east 1\n" //
			+ "PROPERTY sim/north 1\n" //
			+ "PROPERTY sim/south 0\n" //
			+ "POLYGON_DEF foo/bar.pol\n" //
			+ "OBJECT_DEF foo/bar.obj\n" //
			+ "NETWORK_DEF lib/g10/roads.net\n" //
			+ "BEGIN_POLYGON 0 255 2\n" //
			+ "BEGIN_WINDING\n" //
			+ "POLYGON_POINT 0.100000000 0.100000000\n" //
			+ "POLYGON_POINT 0.200000000 0.100000000\n" //
			+ "POLYGON_POINT 0.200000000 0.200000000\n" //
			+ "END_WINDING\n" //
			+ "END_POLYGON\n" //
			+ "OBJECT 0 0.300000000 0.300000000 45.00\n" //
			+ "BEGIN_SEGMENT 0 220 1 0.500000000 0.500000000 0.000000000\n" //
			+ "SHAPE_POINT 0.600000000 0.600000000 0.000000000\n" //
			+ "END_SEGMENT 2 0.700000000 0.700000000 0.000000000\n";

	private Path writeSample() throws IOException {
		Path text = folder.newFile("sample.dsf.txt").toPath();
		Files.write(text, SAMPLE.getBytes(StandardCharsets.UTF_8));
		return text;
	}

	@Test
	public void roundTripsSampleText() throws IOException {
		Path source = writeSample();
		DsfModel original = DsfModel.fromText(source);

		byte[] dsf = DsfConverter.textToBinary(source);
		DsfModel fromBinary = DsfModel.fromBinary(dsf);
		DsfModelTestSupport.assertEquivalent("original -> dsf", original, fromBinary);

		Path roundTrip = folder.newFile("roundtrip.dsf.txt").toPath();
		Files.write(roundTrip, DsfConverter.binaryToText(dsf).getBytes(StandardCharsets.UTF_8));
		DsfModel reparsed = DsfModel.fromText(roundTrip);
		DsfModelTestSupport.assertEquivalent("original -> dsf -> text", original, reparsed);

		byte[] dsf2 = DsfConverter.textToBinary(roundTrip);
		DsfModel fromBinary2 = DsfModel.fromBinary(dsf2);
		DsfModelTestSupport.assertEquivalent("original -> dsf -> text -> dsf", original, fromBinary2);
	}

	@Test
	public void writesValidDsfHeaderAndFooter() throws IOException {
		byte[] dsf = DsfConverter.textToBinary(writeSample());
		assertEquals("XPLNEDSF", new String(dsf, 0, 8, StandardCharsets.US_ASCII));
		assertEquals(1, DsfReadSupport.readInt(dsf, 8));
		new DsfReader(dsf).read(new DsfModel());
	}

	@Test
	public void failsOnMissingDimensions() throws IOException {
		Path text = folder.newFile("broken.dsf.txt").toPath();
		Files.write(text, "PROPERTY sim/planet earth\n".getBytes(StandardCharsets.UTF_8));
		assertThrows(DsfConversionException.class, () -> DsfConverter.textToBinary(text));
	}

	@Test
	public void failsOnCorruptDsf() {
		byte[] bad = new byte[64];
		assertThrows(DsfConversionException.class, () -> DsfConverter.binaryToText(bad));
	}

	@Test
	public void subdividesOversizedPolygon() throws IOException {
		final int count = 70000;
		final double radius = 0.4;
		StringBuilder sb = new StringBuilder();
		sb.append("I\n800\nDSF2TEXT\n\n");
		sb.append("PROPERTY sim/planet earth\nPROPERTY sim/overlay 1\n");
		sb.append("PROPERTY sim/west 0\nPROPERTY sim/east 1\nPROPERTY sim/north 1\nPROPERTY sim/south 0\n");
		sb.append("POLYGON_DEF huge.for\n");
		sb.append("BEGIN_POLYGON 0 255 2\nBEGIN_WINDING\n");
		for (int i = 0; i < count; i++) {
			double angle = 2.0 * Math.PI * i / count;
			double x = 0.5 + radius * Math.cos(angle);
			double y = 0.5 + radius * Math.sin(angle);
			sb.append("POLYGON_POINT ").append(String.format(java.util.Locale.ROOT, "%.9f %.9f", x, y)).append('\n');
		}
		sb.append("END_WINDING\nEND_POLYGON\n");
		Path text = folder.newFile("huge.dsf.txt").toPath();
		Files.write(text, sb.toString().getBytes(StandardCharsets.UTF_8));
		Path dsf = folder.newFile("huge.dsf").toPath();

		DsfConversionResult result = DsfConverter.textToBinary(text, dsf);
		assertEquals(0, result.skippedPolygons);
		assertTrue(Files.size(dsf) > 0);

		DsfModel decoded = DsfModel.fromBinary(Files.readAllBytes(dsf));
		assertTrue("expected the polygon to be split", decoded.polygons.size() >= 2);
		double decodedArea = 0;
		for (DsfModel.Poly poly : decoded.polygons) {
			assertTrue("piece exceeds DSF limit: " + poly.pointCount(), poly.pointCount() <= 65535);
			decodedArea += Math.abs(netArea(poly));
		}
		double expectedArea = Math.PI * radius * radius;
		assertTrue("area " + decodedArea + " expected " + expectedArea,
				Math.abs(decodedArea - expectedArea) / expectedArea < 0.01);
	}

	private static double netArea(DsfModel.Poly poly) {
		double area = 0;
		for (List<double[]> ring : poly.windings) {
			for (int i = 0; i < ring.size(); i++) {
				double[] a = ring.get(i);
				double[] b = ring.get((i + 1) % ring.size());
				area += a[0] * b[1] - b[0] * a[1];
			}
		}
		return area / 2.0;
	}

	@Test
	public void subdividesPolygonWithHoles() throws IOException {
		final int outerCount = 40000;
		final int holeCount = 30000;
		final double outerRadius = 0.4;
		final double holeRadius = 0.1;
		StringBuilder sb = new StringBuilder();
		sb.append("I\n800\nDSF2TEXT\n\n");
		sb.append("PROPERTY sim/planet earth\nPROPERTY sim/overlay 1\n");
		sb.append("PROPERTY sim/west 0\nPROPERTY sim/east 1\nPROPERTY sim/north 1\nPROPERTY sim/south 0\n");
		sb.append("POLYGON_DEF forests/outer.for\nPOLYGON_DEF forests/holed.for\n");
		sb.append("BEGIN_POLYGON 1 255 2\nBEGIN_WINDING\n");
		for (int i = 0; i < outerCount; i++) {
			double angle = 2.0 * Math.PI * i / outerCount;
			sb.append(point(0.5 + outerRadius * Math.cos(angle), 0.5 + outerRadius * Math.sin(angle)));
		}
		sb.append("END_WINDING\nBEGIN_WINDING\n");
		for (int i = 0; i < holeCount; i++) {
			double angle = -2.0 * Math.PI * i / holeCount;
			sb.append(point(0.5 + holeRadius * Math.cos(angle), 0.5 + holeRadius * Math.sin(angle)));
		}
		sb.append("END_WINDING\nEND_POLYGON\n");
		Path text = folder.newFile("holed.dsf.txt").toPath();
		Files.write(text, sb.toString().getBytes(StandardCharsets.UTF_8));
		Path dsf = folder.newFile("holed.dsf").toPath();

		DsfConversionResult result = DsfConverter.textToBinary(text, dsf);
		assertEquals(0, result.skippedPolygons);
		DsfModel decoded = DsfModel.fromBinary(Files.readAllBytes(dsf));
		assertTrue(decoded.polygons.size() >= 2);
		double decodedArea = 0;
		for (DsfModel.Poly poly : decoded.polygons) {
			assertTrue("piece exceeds DSF limit: " + poly.pointCount(), poly.pointCount() <= 65535);
			decodedArea += netArea(poly);
		}
		double expectedArea = Math.PI * (outerRadius * outerRadius - holeRadius * holeRadius);
		assertTrue("area " + decodedArea + " expected " + expectedArea,
				Math.abs(decodedArea - expectedArea) / expectedArea < 0.01);
	}

	private static String point(double x, double y) {
		return "POLYGON_POINT " + String.format(java.util.Locale.ROOT, "%.9f %.9f", x, y) + "\n";
	}
}
