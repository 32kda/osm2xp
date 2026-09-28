package com.osm2xp.spatial;

/**
 * Builds a {@link TileSpatialIndex} from a generic, format-agnostic terrain
 * triangle mesh expressed in a local metre frame.
 * <p>
 * Quantises vertex coordinates and heights ({@code short}/{@code byte}),
 * chooses the compact height storage per tile and packs the per-cell triangle
 * references into a CSR layout. It has no knowledge of FlightGear/X-Plane; the
 * format-specific adaption lives in the caller (e.g.
 * {@code FlightGearTileSpatialIndexFactory}).
 *
 * @author osm2xp
 */
public final class TileSpatialIndexFactory {

	private static final int MAX_HEIGHT_BYTE = 255;

	private TileSpatialIndexFactory() {
	}

	/**
	 * @param east   vertex local east offsets in metres
	 * @param north  vertex local north offsets in metres
	 * @param height vertex heights in metres
	 * @param triA   per-triangle first vertex index
	 * @param triB   per-triangle second vertex index
	 * @param triC   per-triangle third vertex index
	 * @param flags  per-triangle generic flag bits
	 * @param grid   grid resolution per axis
	 */
	public static TileSpatialIndex build(double[] east, double[] north, double[] height, int[] triA, int[] triB,
			int[] triC, byte[] flags, int grid) {
		int vertexCount = east.length;
		short[] vx = new short[vertexCount];
		short[] vy = new short[vertexCount];
		double minE = Double.POSITIVE_INFINITY;
		double minN = Double.POSITIVE_INFINITY;
		double maxE = Double.NEGATIVE_INFINITY;
		double maxN = Double.NEGATIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < vertexCount; i++) {
			vx[i] = toShort(east[i] * TileSpatialIndex.POS_SCALE);
			vy[i] = toShort(north[i] * TileSpatialIndex.POS_SCALE);
			minE = Math.min(minE, east[i]);
			minN = Math.min(minN, north[i]);
			maxE = Math.max(maxE, east[i]);
			maxN = Math.max(maxN, north[i]);
			minZ = Math.min(minZ, height[i]);
			maxZ = Math.max(maxZ, height[i]);
		}

		byte[] vzByte = null;
		short[] vzShort = null;
		if (maxZ - minZ <= MAX_HEIGHT_BYTE / TileSpatialIndex.Z_SCALE) {
			vzByte = new byte[vertexCount];
			for (int i = 0; i < vertexCount; i++) {
				vzByte[i] = (byte) clamp((int) Math.round((height[i] - minZ) * TileSpatialIndex.Z_SCALE), 0,
						MAX_HEIGHT_BYTE);
			}
		} else {
			vzShort = new short[vertexCount];
			for (int i = 0; i < vertexCount; i++) {
				vzShort[i] = (short) clamp((int) Math.round((height[i] - minZ) * TileSpatialIndex.Z_SCALE), 0, 65535);
			}
		}

		double width = maxE - minE;
		double hgt = maxN - minN;
		double cellW = width > 0 ? width / grid : 1.0;
		double cellH = hgt > 0 ? hgt / grid : 1.0;

		int faceCount = triA.length;
		int cellCount = grid * grid;
		int[] counts = new int[cellCount];
		for (int f = 0; f < faceCount; f++) {
			countFaces(grid, triA, triB, triC, f, east, north, minE, minN, cellW, cellH, counts, null);
		}
		int[] cellStart = new int[cellCount + 1];
		for (int c = 0; c < cellCount; c++) {
			cellStart[c + 1] = cellStart[c] + counts[c];
		}
		int[] cellTriangles = new int[cellStart[cellCount]];
		int[] cursor = cellStart.clone();
		for (int f = 0; f < faceCount; f++) {
			countFaces(grid, triA, triB, triC, f, east, north, minE, minN, cellW, cellH, cursor, cellTriangles);
		}

		return new TileSpatialIndex(grid, minE, minN, cellW, cellH, vx, vy, vzByte, vzShort, minZ, triA, triB, triC,
				flags, cellStart, cellTriangles);
	}

	/**
	 * Adds face {@code f} to every grid cell overlapping its bounding box. When
	 * {@code fill} is {@code null} the cells are only counted (via {@code counts});
	 * otherwise the face index is written at the cursor position.
	 */
	private static void countFaces(int grid, int[] triA, int[] triB, int[] triC, int f, double[] east,
			double[] north, double minE, double minN, double cellW, double cellH, int[] counts, int[] fill) {
		int a = triA[f];
		int b = triB[f];
		int c = triC[f];
		double minX = Math.min(east[a], Math.min(east[b], east[c]));
		double maxX = Math.max(east[a], Math.max(east[b], east[c]));
		double minY = Math.min(north[a], Math.min(north[b], north[c]));
		double maxY = Math.max(north[a], Math.max(north[b], north[c]));
		int c0 = clamp((int) Math.floor((minX - minE) / cellW), 0, grid - 1);
		int c1 = clamp((int) Math.floor((maxX - minE) / cellW), 0, grid - 1);
		int r0 = clamp((int) Math.floor((minY - minN) / cellH), 0, grid - 1);
		int r1 = clamp((int) Math.floor((maxY - minN) / cellH), 0, grid - 1);
		for (int r = r0; r <= r1; r++) {
			int rowBase = r * grid;
			for (int col = c0; col <= c1; col++) {
				int cell = rowBase + col;
				if (fill == null) {
					counts[cell]++;
				} else {
					fill[counts[cell]++] = f;
				}
			}
		}
	}

	private static short toShort(double scaled) {
		long rounded = Math.round(scaled);
		if (rounded > Short.MAX_VALUE) {
			return Short.MAX_VALUE;
		}
		if (rounded < Short.MIN_VALUE) {
			return Short.MIN_VALUE;
		}
		return (short) rounded;
	}

	private static int clamp(int value, int min, int max) {
		return value < min ? min : (value > max ? max : value);
	}
}
