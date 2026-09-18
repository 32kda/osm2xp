package com.osm2xp.translators.flightgear.terrain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.osm2xp.core.model.osm.Node;

import math.geom2d.Box2D;

/**
 * Unit tests for the terrain pre-pass extent detection and preparation triggering.
 */
public class FlightGearTerrainPreprocessorTest {

	private static TerrainPaths paths() {
		return new TerrainPaths(new File("cache"), null, new File("out/Terrain"));
	}

	@Test
	public void headerBoundingBoxStartsImmediately() {
		RecordingDownloader downloader = new RecordingDownloader();
		FlightGearTerrainPreprocessor preprocessor = new FlightGearTerrainPreprocessor(downloader, paths());

		preprocessor.visit(new Box2D(10.0, 12.0, 50.0, 52.0));

		assertEquals("header bbox should trigger the work before nodes", 1, downloader.calls.size());
		assertArrayEquals(new double[] { 10.0, 50.0, 12.0, 52.0 }, downloader.calls.get(0), 1e-9);

		preprocessor.complete();
		assertEquals("work must not run twice", 1, downloader.calls.size());
	}

	@Test
	public void missingBoundingBoxFallsBackToNodes() {
		RecordingDownloader downloader = new RecordingDownloader();
		FlightGearTerrainPreprocessor preprocessor = new FlightGearTerrainPreprocessor(downloader, paths());

		preprocessor.visit(node(10.0, 50.0));
		preprocessor.visit(node(12.0, 51.0));
		preprocessor.visit(node(11.0, 52.0));

		assertEquals("no work before the pass completes", 0, downloader.calls.size());

		preprocessor.complete();

		assertEquals(1, downloader.calls.size());
		assertArrayEquals(new double[] { 10.0, 50.0, 12.0, 52.0 }, downloader.calls.get(0), 1e-9);
	}

	@Test
	public void zeroBoundingBoxIsIgnored() {
		RecordingDownloader downloader = new RecordingDownloader();
		FlightGearTerrainPreprocessor preprocessor = new FlightGearTerrainPreprocessor(downloader, paths());

		preprocessor.visit(new Box2D(0.0, 0.0, 0.0, 0.0));
		preprocessor.visit(node(5.0, 6.0));
		preprocessor.complete();

		assertEquals(1, downloader.calls.size());
		assertArrayEquals(new double[] { 5.0, 6.0, 5.0, 6.0 }, downloader.calls.get(0), 1e-9);
	}

	private static Node node(double lon, double lat) {
		Node node = new Node();
		node.setLon(lon);
		node.setLat(lat);
		return node;
	}

	private static final class RecordingDownloader extends AbstractTerrainDownloader {
		final List<double[]> calls = new ArrayList<>();

		@Override
		public int download(double minLon, double minLat, double maxLon, double maxLat, TerrainPaths paths) {
			calls.add(new double[] { minLon, minLat, maxLon, maxLat });
			return 0;
		}
	}
}
