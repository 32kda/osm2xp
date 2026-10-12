package com.osm2xp.generation.dsf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.osm2xp.generation.dsf.DsfModel.Obj;
import com.osm2xp.generation.dsf.DsfModel.Poly;
import com.osm2xp.generation.dsf.DsfModel.Seg;

final class DsfModelTestSupport {

	private DsfModelTestSupport() {
	}

	/**
	 * Finds up to {@code max} reasonably small {@code *.dsf.txt} samples under
	 * the corpus, so tests do not depend on a specific tile being present.
	 */
	static List<Path> findCorpusSamples(Path corpus, int max, long maxSize) throws IOException {
		List<Path> all = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(corpus)) {
			stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".dsf.txt"))
					.filter(p -> {
						try {
							return Files.size(p) <= maxSize;
						} catch (IOException e) {
							return false;
						}
					}).forEach(all::add);
		}
		all.sort(Comparator.comparingLong(DsfModelTestSupport::sizeOf));
		if (all.size() > max) {
			return new ArrayList<>(all.subList(0, max));
		}
		return all;
	}

	private static long sizeOf(Path p) {
		try {
			return Files.size(p);
		} catch (IOException e) {
			return Long.MAX_VALUE;
		}
	}

	static void assertEquivalent(String message, DsfModel expected, DsfModel actual) {
		assertEquals(message + " properties", expected.properties, actual.properties);
		assertEquals(message + " terrain defs", expected.terrainDefs, actual.terrainDefs);
		assertEquals(message + " object defs", expected.objectDefs, actual.objectDefs);
		assertEquals(message + " polygon defs", expected.polygonDefs, actual.polygonDefs);
		assertEquals(message + " network defs", expected.networkDefs, actual.networkDefs);

		assertEquals(message + " object count", expected.objects.size(), actual.objects.size());
		assertEquals(message + " polygon count", expected.polygons.size(), actual.polygons.size());
		assertEquals(message + " segment count", expected.segments.size(), actual.segments.size());

		perfectMatch(message + " objects", expected.objects, actual.objects, DsfModelTestSupport::objMatches);
		perfectMatch(message + " polygons", expected.polygons, actual.polygons, DsfModelTestSupport::polyMatches);
		perfectMatch(message + " segments", expected.segments, actual.segments, DsfModelTestSupport::segMatches);
	}

	private interface Matcher<T> {
		boolean matches(T a, T b);
	}

	/**
	 * Finds a perfect matching between expected and actual using bipartite
	 * augmenting paths (Kuhn's algorithm). Only unordered equality matters, so
	 * any perfect matching proves equivalence up to tolerance.
	 */
	private static <T> void perfectMatch(String label, List<T> expected, List<T> actual, Matcher<T> matcher) {
		int n = expected.size();
		int m = actual.size();
		List<List<Integer>> candidates = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			List<Integer> list = new ArrayList<>();
			for (int j = 0; j < m; j++) {
				if (matcher.matches(expected.get(i), actual.get(j))) {
					list.add(j);
				}
			}
			candidates.add(list);
		}
		int[] matchOfActual = new int[m];
		java.util.Arrays.fill(matchOfActual, -1);
		int matched = 0;
		for (int i = 0; i < n; i++) {
			boolean[] visited = new boolean[m];
			if (augment(i, candidates, matchOfActual, visited)) {
				matched++;
			} else {
				assertTrue(label + " has no match for element #" + i, false);
			}
		}
		assertEquals(label + " matched", n, matched);
	}

	private static boolean augment(int expectedIndex, List<List<Integer>> candidates, int[] matchOfActual,
			boolean[] visited) {
		for (int actualIndex : candidates.get(expectedIndex)) {
			if (visited[actualIndex]) {
				continue;
			}
			visited[actualIndex] = true;
			if (matchOfActual[actualIndex] == -1 || augment(matchOfActual[actualIndex], candidates, matchOfActual, visited)) {
				matchOfActual[actualIndex] = expectedIndex;
				return true;
			}
		}
		return false;
	}

	private static boolean objMatches(Obj a, Obj b) {
		if (a.type != b.type || a.mode != b.mode || a.coords.length != b.coords.length) {
			return false;
		}
		for (int i = 0; i < a.coords.length; i++) {
			double tol = i == 2 ? 0.02 : i == 3 ? 1.0 : 1e-3;
			if (Math.abs(a.coords[i] - b.coords[i]) > tol) {
				return false;
			}
		}
		return true;
	}

	private static boolean polyMatches(Poly a, Poly b) {
		if (a.type != b.type || a.param != b.param || a.depth != b.depth || a.windings.size() != b.windings.size()) {
			return false;
		}
		for (int w = 0; w < a.windings.size(); w++) {
			List<double[]> wa = a.windings.get(w);
			List<double[]> wb = b.windings.get(w);
			if (wa.size() != wb.size()) {
				return false;
			}
			for (int i = 0; i < wa.size(); i++) {
				if (!pointsClose(wa.get(i), wb.get(i), true)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean segMatches(Seg a, Seg b) {
		if (a.type != b.type || a.subtype != b.subtype || a.curved != b.curved || a.points.size() != b.points.size()) {
			return false;
		}
		for (int i = 0; i < a.points.size(); i++) {
			if (!pointsClose(a.points.get(i), b.points.get(i), false)) {
				return false;
			}
		}
		return true;
	}

	private static boolean pointsClose(double[] a, double[] b, boolean polygon) {
		if (a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			double tol;
			if (polygon) {
				tol = i < 2 ? 0.02 : 0.1;
			} else {
				tol = a.length > 3 && i == 3 ? 0.5 : 0.02;
			}
			if (Math.abs(a[i] - b[i]) > tol) {
				return false;
			}
		}
		return true;
	}
}
