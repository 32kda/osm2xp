package com.osm2xp.spatial;

/**
 * Format-agnostic spatial hash over a triangulated terrain tile.
 * <p>
 * The tile bounding box is divided into a uniform {@code N x N} grid and each
 * cell stores the indices of the triangles that overlap it (a triangle that
 * fully covers a cell is found even though the cell may contain no vertex). The
 * index answers terrain queries (barycentric height inside the containing
 * triangle, arbitrary per-triangle flags) without any FlightGear/X-Plane
 * knowledge; building it from a concrete terrain format is the job of a
 * dedicated factory (see {@link TileSpatialIndexFactory}).
 * <p>
 * Memory is kept low:
 * <ul>
 * <li>vertex east/north offsets relative to the tile centre are stored as
 * {@code short} in 0.5 m units;</li>
 * <li>vertex heights are stored as {@code byte} (0.5 m units) when the tile
 * height range fits in 127.5 m, otherwise as {@code short};</li>
 * <li>flat primitive arrays are used for cache locality and to avoid per-point
 * object overhead;</li>
 * <li>cell triangles use a compressed row (CSR) layout: {@code cellStart} plus
 * {@code cellTriangles}.</li>
 * </ul>
 *
 * @author osm2xp
 */
public final class TileSpatialIndex {

	/** Default grid resolution per tile. */
	public static final int DEFAULT_GRID = 100;

	/** Coordinate scale: stored value = round(metres * POS_SCALE), i.e. 0.5 m. */
	static final double POS_SCALE = 2.0;
	/** Height scale: 0.5 m. */
	static final double Z_SCALE = 2.0;

	private final int grid;
	private final double originE;
	private final double originN;
	private final double cellW;
	private final double cellH;

	private final short[] vx;
	private final short[] vy;
	private final byte[] vzByte;
	private final short[] vzShort;
	private final double zBase;

	private final int[] triA;
	private final int[] triB;
	private final int[] triC;
	private final byte[] triFlags;

	private final int[] cellStart;
	private final int[] cellTriangles;

	TileSpatialIndex(int grid, double originE, double originN, double cellW, double cellH, short[] vx, short[] vy,
			byte[] vzByte, short[] vzShort, double zBase, int[] triA, int[] triB, int[] triC, byte[] triFlags,
			int[] cellStart, int[] cellTriangles) {
		this.grid = grid;
		this.originE = originE;
		this.originN = originN;
		this.cellW = cellW;
		this.cellH = cellH;
		this.vx = vx;
		this.vy = vy;
		this.vzByte = vzByte;
		this.vzShort = vzShort;
		this.zBase = zBase;
		this.triA = triA;
		this.triB = triB;
		this.triC = triC;
		this.triFlags = triFlags;
		this.cellStart = cellStart;
		this.cellTriangles = cellTriangles;
	}

	/** Height (metres) at a local point, extrapolated/clamped from the nearest triangle. */
	public double elevationLocal(double east, double north) {
		return interpolate(triangleAtLocal(east, north), east, north);
	}

	/** Whether the nearest triangle carries the given flag bit. */
	public boolean hasFlagLocal(double east, double north, byte flag) {
		int triangle = triangleAtLocal(east, north);
		return triangle >= 0 && (triFlags[triangle] & flag) != 0;
	}

	/** Flags of the nearest triangle, or {@code 0} when none. */
	public byte triangleFlagLocal(double east, double north) {
		int triangle = triangleAtLocal(east, north);
		return triangle < 0 ? 0 : triFlags[triangle];
	}

	/**
	 * Index of the triangle containing the local point. If no triangle actually
	 * contains it (a point in a tiny mesh gap, or just outside the grid), the
	 * nearest triangle of the cell — then of the 3x3 neighbourhood — is returned,
	 * so a value is always available for a point inside the tile.
	 */
	public int triangleAtLocal(double east, double north) {
		int col = clamp((int) Math.floor((east - originE) / cellW), 0, grid - 1);
		int row = clamp((int) Math.floor((north - originN) / cellH), 0, grid - 1);
		int containing = containingInCell(col, row, east, north);
		if (containing >= 0) {
			return containing;
		}
		int nearest = nearestInCell(col, row, east, north);
		if (nearest >= 0) {
			return nearest;
		}
		return nearestInNeighbors(col, row, east, north);
	}

	private int containingInCell(int col, int row, double east, double north) {
		int cell = row * grid + col;
		int end = cellStart[cell + 1];
		for (int k = cellStart[cell]; k < end; k++) {
			int t = cellTriangles[k];
			if (inside(t, east, north)) {
				return t;
			}
		}
		return -1;
	}

	private int nearestInCell(int col, int row, double east, double north) {
		int cell = row * grid + col;
		int end = cellStart[cell + 1];
		int best = -1;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (int k = cellStart[cell]; k < end; k++) {
			int t = cellTriangles[k];
			double distance = distanceSquared(t, east, north);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = t;
			}
		}
		return best;
	}

	private int nearestInNeighbors(int col, int row, double east, double north) {
		int best = -1;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (int dr = -1; dr <= 1; dr++) {
			int r = row + dr;
			if (r < 0 || r >= grid) {
				continue;
			}
			for (int dc = -1; dc <= 1; dc++) {
				int c = col + dc;
				if (c < 0 || c >= grid) {
					continue;
				}
				int cell = r * grid + c;
				int end = cellStart[cell + 1];
				for (int k = cellStart[cell]; k < end; k++) {
					int t = cellTriangles[k];
					double distance = distanceSquared(t, east, north);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = t;
					}
				}
			}
		}
		return best;
	}

	private boolean inside(int t, double px, double py) {
		double ax = vx[triA[t]] / POS_SCALE;
		double ay = vy[triA[t]] / POS_SCALE;
		double bx = vx[triB[t]] / POS_SCALE;
		double by = vy[triB[t]] / POS_SCALE;
		double cx = vx[triC[t]] / POS_SCALE;
		double cy = vy[triC[t]] / POS_SCALE;
		double d1 = orientation(px, py, ax, ay, bx, by);
		double d2 = orientation(px, py, bx, by, cx, cy);
		double d3 = orientation(px, py, cx, cy, ax, ay);
		boolean hasNeg = d1 < 0 || d2 < 0 || d3 < 0;
		boolean hasPos = d1 > 0 || d2 > 0 || d3 > 0;
		return !(hasNeg && hasPos);
	}

	private double interpolate(int t, double px, double py) {
		if (t < 0) {
			return Double.NaN;
		}
		double ax = vx[triA[t]] / POS_SCALE;
		double ay = vy[triA[t]] / POS_SCALE;
		double bx = vx[triB[t]] / POS_SCALE;
		double by = vy[triB[t]] / POS_SCALE;
		double cx = vx[triC[t]] / POS_SCALE;
		double cy = vy[triC[t]] / POS_SCALE;
		double denom = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
		if (Math.abs(denom) < 1e-12) {
			return height(triA[t]);
		}
		double w1 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / denom;
		double w2 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / denom;
		double w3 = 1.0 - w1 - w2;
		if (w1 < 0.0 || w2 < 0.0 || w3 < 0.0) {
			// Fallback point outside the triangle: clamp to the triangle so the
			// result is the nearest-edge height instead of an extrapolation.
			w1 = Math.max(w1, 0.0);
			w2 = Math.max(w2, 0.0);
			w3 = Math.max(w3, 0.0);
			double sum = w1 + w2 + w3;
			if (sum <= 0.0) {
				return height(triA[t]);
			}
			w1 /= sum;
			w2 /= sum;
			w3 /= sum;
		}
		return w1 * height(triA[t]) + w2 * height(triB[t]) + w3 * height(triC[t]);
	}

	private double distanceSquared(int t, double px, double py) {
		double ax = vx[triA[t]] / POS_SCALE;
		double ay = vy[triA[t]] / POS_SCALE;
		double bx = vx[triB[t]] / POS_SCALE;
		double by = vy[triB[t]] / POS_SCALE;
		double cx = vx[triC[t]] / POS_SCALE;
		double cy = vy[triC[t]] / POS_SCALE;
		double d1 = segmentDistanceSquared(px, py, ax, ay, bx, by);
		double d2 = segmentDistanceSquared(px, py, bx, by, cx, cy);
		double d3 = segmentDistanceSquared(px, py, cx, cy, ax, ay);
		return Math.min(d1, Math.min(d2, d3));
	}

	private static double segmentDistanceSquared(double px, double py, double ax, double ay, double bx, double by) {
		double dx = bx - ax;
		double dy = by - ay;
		double lengthSquared = dx * dx + dy * dy;
		double t = lengthSquared <= 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
		t = t < 0 ? 0 : (t > 1 ? 1 : t);
		double qx = ax + t * dx;
		double qy = ay + t * dy;
		double ex = px - qx;
		double ey = py - qy;
		return ex * ex + ey * ey;
	}

	private double height(int vertex) {
		return zBase + (vzByte != null ? (vzByte[vertex] & 0xFF) : (vzShort[vertex] & 0xFFFF)) / Z_SCALE;
	}

	private static double orientation(double px, double py, double ax, double ay, double bx, double by) {
		return (px - bx) * (ay - by) - (ax - bx) * (py - by);
	}

	private static int clamp(int value, int min, int max) {
		return value < min ? min : (value > max ? max : value);
	}

	public int getGrid() {
		return grid;
	}

	public int vertexCount() {
		return vx.length;
	}

	public int triangleCount() {
		return triA.length;
	}

	/** Number of (cell, triangle) references, useful for memory diagnostics. */
	public int cellTriangleReferences() {
		return cellTriangles.length;
	}
}
