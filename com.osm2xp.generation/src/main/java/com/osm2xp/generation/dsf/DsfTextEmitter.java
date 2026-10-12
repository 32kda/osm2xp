package com.osm2xp.generation.dsf;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Emits the DSFTool text representation of DSF data received from a
 * {@link DsfReader}. Ported from DSF2Text_CreateWriterCallbacks (DSF2Text.cpp).
 */
public final class DsfTextEmitter implements DsfCallbacks {

	private final Writer writer;

	public DsfTextEmitter(Writer writer) {
		this.writer = writer;
	}

	public void writeHeader() {
		append("I\n800\nDSF2TEXT\n\n");
	}

	public void finish() {
		try {
			writer.flush();
		} catch (IOException e) {
			throw new DsfConversionException("Error writing DSF text", e);
		}
	}

	private void append(String text) {
		try {
			writer.write(text);
		} catch (IOException e) {
			throw new DsfConversionException("Error writing DSF text", e);
		}
	}

	private static String f9(double v) {
		return String.format(java.util.Locale.ROOT, "%.9f", v);
	}

	@Override
	public void acceptTerrainDef(String partialPath) {
		append("TERRAIN_DEF " + partialPath + "\n");
	}

	@Override
	public void acceptObjectDef(String partialPath) {
		append("OBJECT_DEF " + partialPath + "\n");
	}

	@Override
	public void acceptPolygonDef(String partialPath) {
		append("POLYGON_DEF " + partialPath + "\n");
	}

	@Override
	public void acceptNetworkDef(String partialPath) {
		append("NETWORK_DEF " + partialPath + "\n");
	}

	@Override
	public void acceptRasterDef(String partialPath) {
		append("RASTER_DEF " + partialPath + "\n");
	}

	@Override
	public void acceptProperty(String name, String value) {
		append("PROPERTY " + name + " " + value + "\n");
	}

	@Override
	public void beginPatch(int terrainType, double nearLOD, double farLOD, int flags, int coordDepth) {
		append(String.format(java.util.Locale.ROOT, "BEGIN_PATCH %d %f %f %d %d\n", terrainType, nearLOD, farLOD,
				flags, coordDepth));
	}

	@Override
	public void beginPrimitive(int type) {
		append("BEGIN_PRIMITIVE " + type + "\n");
	}

	@Override
	public void addPatchVertex(double[] coords) {
		StringBuilder sb = new StringBuilder("PATCH_VERTEX");
		for (double coord : coords) {
			sb.append(' ').append(f9(coord));
		}
		append(sb.append('\n').toString());
	}

	@Override
	public void endPrimitive() {
		append("END_PRIMITIVE\n");
	}

	@Override
	public void endPatch() {
		append("END_PATCH\n");
	}

	@Override
	public void addObject(int objectType, double[] coords, int mode) {
		if (mode == DsfConstants.MODE_AGL) {
			append(String.format(java.util.Locale.ROOT, "OBJECT_AGL %d %s %s %.5f %.3f\n", objectType, f9(coords[0]),
					f9(coords[1]), coords[3], coords[2]));
		} else if (mode == DsfConstants.MODE_MSL) {
			append(String.format(java.util.Locale.ROOT, "OBJECT_MSL %d %s %s %.5f %.3f\n", objectType, f9(coords[0]),
					f9(coords[1]), coords[3], coords[2]));
		} else {
			append(String.format(java.util.Locale.ROOT, "OBJECT %d %s %s %.3f\n", objectType, f9(coords[0]),
					f9(coords[1]), coords[2]));
		}
	}

	@Override
	public void beginSegment(int networkType, int networkSubtype, double[] coords, boolean curved) {
		if (!curved) {
			append(String.format(java.util.Locale.ROOT, "BEGIN_SEGMENT %d %d %d %s %s %s\n", networkType,
					networkSubtype, (int) coords[3], f9(coords[0]), f9(coords[1]), f9(coords[2])));
		} else {
			append(String.format(java.util.Locale.ROOT, "BEGIN_SEGMENT_CURVED %d %d %d %s %s %s %s %s %s\n",
					networkType, networkSubtype, (int) coords[3], f9(coords[0]), f9(coords[1]), f9(coords[2]),
					f9(coords[4]), f9(coords[5]), f9(coords[6])));
		}
	}

	@Override
	public void addSegmentShapePoint(double[] coords, boolean curved) {
		if (!curved) {
			append("SHAPE_POINT " + f9(coords[0]) + " " + f9(coords[1]) + " " + f9(coords[2]) + "\n");
		} else {
			append("SHAPE_POINT_CURVED " + f9(coords[0]) + " " + f9(coords[1]) + " " + f9(coords[2]) + " "
					+ f9(coords[3]) + " " + f9(coords[4]) + " " + f9(coords[5]) + "\n");
		}
	}

	@Override
	public void endSegment(double[] coords, boolean curved) {
		if (!curved) {
			append(String.format(java.util.Locale.ROOT, "END_SEGMENT %d %s %s %s\n", (int) coords[3], f9(coords[0]),
					f9(coords[1]), f9(coords[2])));
		} else {
			append(String.format(java.util.Locale.ROOT, "END_SEGMENT_CURVED %d %s %s %s %s %s %s\n", (int) coords[3],
					f9(coords[0]), f9(coords[1]), f9(coords[2]), f9(coords[4]), f9(coords[5]), f9(coords[6])));
		}
	}

	@Override
	public void beginPolygon(int polygonType, int param, int coordDepth) {
		append("BEGIN_POLYGON " + polygonType + " " + param + " " + coordDepth + "\n");
	}

	@Override
	public void beginPolygonWinding() {
		append("BEGIN_WINDING\n");
	}

	@Override
	public void addPolygonPoint(double[] coords) {
		StringBuilder sb = new StringBuilder("POLYGON_POINT");
		for (double coord : coords) {
			sb.append(' ').append(f9(coord));
		}
		append(sb.append('\n').toString());
	}

	@Override
	public void endPolygonWinding() {
		append("END_WINDING\n");
	}

	@Override
	public void endPolygon() {
		append("END_POLYGON\n");
	}

	@Override
	public void addRasterData(DsfRasterHeader header, byte[] data) {
		append(String.format(java.util.Locale.ROOT,
				"RASTER_DATA version=%d bpp=%d flags=%d width=%d height=%d scale=%f offset=%f raster.raw\n",
				header.version, header.bytesPerPixel, header.flags, header.width, header.height, header.scale,
				header.offset));
	}

	@Override
	public void setFilter(int filterIndex) {
		append("FILTER " + filterIndex + "\n");
	}

	@Override
	public void pointPoolInfo(int divisions, double hgtScale, double hgtOffset, List<String> info) {
		for (int i = 0; i < info.size(); i++) {
			append(String.format(java.util.Locale.ROOT, "# pool %2d: %s\n", i, info.get(i)));
		}
	}
}
