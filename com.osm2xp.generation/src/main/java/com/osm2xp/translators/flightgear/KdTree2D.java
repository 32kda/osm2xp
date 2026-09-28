package com.osm2xp.translators.flightgear;

/**
 * Minimal static 2D kd-tree with nearest-neighbour lookup.
 * <p>
 * Points are stored as three parallel {@code double[]} arrays ({@code xs},
 * {@code ys} and the value returned on lookup). The tree is built once and only
 * supports queries, which keeps the memory footprint of a scenery tile low
 * (three {@code double}s per vertex plus three {@code int}s/bytes per node).
 * <p>
 * Distances are plain Euclidean, so callers are expected to feed metric
 * coordinates (e.g. local east/north metres) rather than raw degrees.
 */
final class KdTree2D {

	private final double[] xs;
	private final double[] ys;
	private final double[] values;
	private final int[] nodePoint;
	private final int[] left;
	private final int[] right;
	private final byte[] axis;
	private final int root;

	KdTree2D(double[] xs, double[] ys, double[] values) {
		this.xs = xs;
		this.ys = ys;
		this.values = values;
		int count = xs.length;
		if (count == 0) {
			nodePoint = new int[0];
			left = new int[0];
			right = new int[0];
			axis = new byte[0];
			root = -1;
			return;
		}
		nodePoint = new int[count];
		left = new int[count];
		right = new int[count];
		axis = new byte[count];
		int[] points = new int[count];
		for (int i = 0; i < count; i++) {
			points[i] = i;
		}
		root = build(points, 0, count - 1, 0);
	}

	private int build(int[] points, int lo, int hi, int depth) {
		if (lo > hi) {
			return -1;
		}
		int ax = depth & 1;
		sort(points, lo, hi, ax);
		int mid = (lo + hi) >>> 1;
		axis[mid] = (byte) ax;
		nodePoint[mid] = points[mid];
		left[mid] = build(points, lo, mid - 1, depth + 1);
		right[mid] = build(points, mid + 1, hi, depth + 1);
		return mid;
	}

	private void sort(int[] points, int lo, int hi, int ax) {
		while (hi - lo > 32) {
			int mid = (lo + hi) >>> 1;
			if (key(ax, points[lo]) > key(ax, points[mid])) {
				swap(points, lo, mid);
			}
			if (key(ax, points[mid]) > key(ax, points[hi])) {
				swap(points, mid, hi);
			}
			if (key(ax, points[lo]) > key(ax, points[mid])) {
				swap(points, lo, mid);
			}
			double pivot = key(ax, points[mid]);
			int i = lo;
			int j = hi;
			while (i <= j) {
				while (key(ax, points[i]) < pivot) {
					i++;
				}
				while (key(ax, points[j]) > pivot) {
					j--;
				}
				if (i <= j) {
					swap(points, i, j);
					i++;
					j--;
				}
			}
			if (j - lo < hi - i) {
				sort(points, lo, j, ax);
				lo = i;
			} else {
				sort(points, i, hi, ax);
				hi = j;
			}
		}
		insertionSort(points, lo, hi, ax);
	}

	private void insertionSort(int[] points, int lo, int hi, int ax) {
		for (int i = lo + 1; i <= hi; i++) {
			int key = points[i];
			double keyValue = key(ax, key);
			int j = i - 1;
			while (j >= lo && key(ax, points[j]) > keyValue) {
				points[j + 1] = points[j];
				j--;
			}
			points[j + 1] = key;
		}
	}

	private double key(int ax, int index) {
		return ax == 0 ? xs[index] : ys[index];
	}

	private static void swap(int[] points, int a, int b) {
		int tmp = points[a];
		points[a] = points[b];
		points[b] = tmp;
	}

	boolean isEmpty() {
		return root < 0;
	}

	/** Value of the nearest point, or {@link Double#NaN} when empty. */
	double nearestValue(double x, double y) {
		if (root < 0) {
			return Double.NaN;
		}
		Search state = new Search();
		search(root, x, y, state);
		return state.bestIndex < 0 ? Double.NaN : values[state.bestIndex];
	}

	/**
	 * Squared distance to the nearest point, or
	 * {@link Double#POSITIVE_INFINITY} when empty. Cheaper than comparing metric
	 * distances when the caller only needs a radius test.
	 */
	double nearestSquaredDistance(double x, double y) {
		if (root < 0) {
			return Double.POSITIVE_INFINITY;
		}
		Search state = new Search();
		search(root, x, y, state);
		return state.bestDist;
	}

	private void search(int node, double x, double y, Search state) {
		if (node < 0) {
			return;
		}
		int point = nodePoint[node];
		double dx = xs[point] - x;
		double dy = ys[point] - y;
		double dist = dx * dx + dy * dy;
		if (dist < state.bestDist) {
			state.bestDist = dist;
			state.bestIndex = point;
		}
		int ax = axis[node];
		double diff = (ax == 0 ? x : y) - (ax == 0 ? xs[point] : ys[point]);
		int near = diff <= 0 ? left[node] : right[node];
		int far = diff <= 0 ? right[node] : left[node];
		search(near, x, y, state);
		if (diff * diff < state.bestDist) {
			search(far, x, y, state);
		}
	}

	private static final class Search {
		double bestDist = Double.POSITIVE_INFINITY;
		int bestIndex = -1;
	}
}
