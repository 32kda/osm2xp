package com.osm2xp.generation.dsf;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a DSFTool text file ("DSF2TEXT") and builds a {@link DsfWriter}.
 * Ported from Text2DSFWithWriterAny (DSF2Text.cpp).
 */
public final class DsfTextParser {

	private DsfTextParser() {
	}

	private static final class Property {
		final String name;
		final String value;

		Property(String name, String value) {
			this.name = name;
			this.value = value;
		}
	}

	public static DsfWriter read(Path path) {
		List<Property> properties = scanProperties(path);
		DivisionsHeights dh = scanDivisionsHeights(path);

		float west = 999.0f;
		float south = 999.0f;
		float north = 999.0f;
		float east = 999.0f;
		for (Property property : properties) {
			if ("sim/west".equals(property.name)) {
				west = parseFloat(property.value, west);
			} else if ("sim/east".equals(property.name)) {
				east = parseFloat(property.value, east);
			} else if ("sim/north".equals(property.name)) {
				north = parseFloat(property.value, north);
			} else if ("sim/south".equals(property.name)) {
				south = parseFloat(property.value, south);
			}
		}

		if (west >= 180.0f || west < -180.0f || east > 180.0f || east <= -180.0f || south >= 90.0f || south < -90.0f
				|| north > 90.0f || north <= -90.0f) {
			throw new DsfConversionException(
					"DSF boundaries are out of range - missing or corrupt sim/dimension properties");
		}

		DsfWriter writer = new DsfWriter(west, south, east, north, dh.hgtOffset,
				dh.hgtOffset + dh.hgtScale * 65535.0, dh.divisions);
		for (Property property : properties) {
			writer.acceptProperty(property.name, property.value);
		}

		feedDefinitions(path, writer);
		feedCommands(path, writer);
		return writer;
	}

	/**
	 * Parses a text file and feeds the supplied callbacks (properties,
	 * definitions and commands).
	 */
	public static void parse(Path path, DsfCallbacks callbacks) {
		for (Property property : scanProperties(path)) {
			callbacks.acceptProperty(property.name, property.value);
		}
		feedDefinitions(path, callbacks);
		feedCommands(path, callbacks);
	}

	private static final class DivisionsHeights {
		int divisions = 8;
		double hgtScale = 1.0;
		double hgtOffset = 0.0;
	}

	private static List<Property> scanProperties(Path path) {
		List<Property> properties = new ArrayList<>();
		forEachLine(path, line -> {
			Property property = parseProperty(line);
			if (property != null) {
				properties.add(property);
			}
		});
		return properties;
	}

	private static DivisionsHeights scanDivisionsHeights(Path path) {
		DivisionsHeights result = new DivisionsHeights();
		forEachLine(path, line -> {
			if (line.startsWith("DIVISIONS")) {
				result.divisions = parseInt(afterKeyword(line, "DIVISIONS"), result.divisions);
			} else if (line.startsWith("HEIGHTS")) {
				String[] tokens = afterKeyword(line, "HEIGHTS").trim().split("\\s+");
				if (tokens.length >= 1 && !tokens[0].isEmpty()) {
					result.hgtScale = Double.parseDouble(tokens[0]);
				}
				if (tokens.length >= 2) {
					result.hgtOffset = Double.parseDouble(tokens[1]);
				}
			}
		});
		return result;
	}

	private static void feedDefinitions(Path path, DsfCallbacks writer) {
		forEachLine(path, line -> {
			String value = afterKeyword(line, "TERRAIN_DEF");
			if (value != null) {
				writer.acceptTerrainDef(value);
				return;
			}
			value = afterKeyword(line, "OBJECT_DEF");
			if (value != null) {
				writer.acceptObjectDef(value);
				return;
			}
			value = afterKeyword(line, "POLYGON_DEF");
			if (value != null) {
				writer.acceptPolygonDef(value);
				return;
			}
			value = afterKeyword(line, "NETWORK_DEF");
			if (value != null) {
				writer.acceptNetworkDef(value);
				return;
			}
			value = afterKeyword(line, "RASTER_DEF");
			if (value != null) {
				writer.acceptRasterDef(value);
			}
		});
	}

	private static void feedCommands(Path path, DsfCallbacks writer) {
		final int[] depth = { 99 };
		forEachLine(path, line -> {
			String[] tokens = line.split("\\s+");
			if (tokens.length == 0 || tokens[0].isEmpty()) {
				return;
			}
			String keyword = tokens[0];
			switch (keyword) {
			case "PATCH_VERTEX": {
				double[] coords = parseDoubles(tokens, 1, Math.min(tokens.length - 1, 10));
				if (coords.length == depth[0]) {
					writer.addPatchVertex(coords);
				}
				break;
			}
			case "OBJECT": {
				if (tokens.length >= 5) {
					writer.addObject(parseInt(tokens[1], 0),
							new double[] { toDouble(tokens[2]), toDouble(tokens[3]), toDouble(tokens[4]) },
							DsfConstants.MODE_DRAPED);
				}
				break;
			}
			case "OBJECT_MSL":
			case "OBJECT_AGL": {
				if (tokens.length >= 6) {
					writer.addObject(parseInt(tokens[1], 0),
							new double[] { toDouble(tokens[2]), toDouble(tokens[3]), toDouble(tokens[5]),
									toDouble(tokens[4]) },
							"OBJECT_MSL".equals(keyword) ? DsfConstants.MODE_MSL : DsfConstants.MODE_AGL);
				}
				break;
			}
			case "BEGIN_SEGMENT": {
				if (tokens.length >= 7) {
					writer.beginSegment(parseInt(tokens[1], 0), parseInt(tokens[2], 0),
							new double[] { toDouble(tokens[4]), toDouble(tokens[5]), toDouble(tokens[6]),
									toDouble(tokens[3]) },
							false);
				}
				break;
			}
			case "SHAPE_POINT": {
				if (tokens.length >= 4) {
					writer.addSegmentShapePoint(new double[] { toDouble(tokens[1]), toDouble(tokens[2]),
							toDouble(tokens[3]), 0.0 }, false);
				}
				break;
			}
			case "END_SEGMENT": {
				if (tokens.length >= 5) {
					writer.endSegment(
							new double[] { toDouble(tokens[2]), toDouble(tokens[3]), toDouble(tokens[4]),
									toDouble(tokens[1]) },
							false);
				}
				break;
			}
			case "BEGIN_PRIMITIVE": {
				if (tokens.length >= 2) {
					writer.beginPrimitive(parseInt(tokens[1], 0));
				}
				break;
			}
			case "END_PRIMITIVE":
				writer.endPrimitive();
				break;
			case "BEGIN_PATCH": {
				if (tokens.length >= 6) {
					depth[0] = parseInt(tokens[5], 2);
					writer.beginPatch(parseInt(tokens[1], 0), toDouble(tokens[2]), toDouble(tokens[3]),
							parseInt(tokens[4], 0), depth[0]);
				}
				break;
			}
			case "END_PATCH":
				writer.endPatch();
				depth[0] = 99;
				break;
			case "POLYGON_POINT": {
				double[] coords = parseDoubles(tokens, 1, Math.min(tokens.length - 1, 8));
				if (coords.length == depth[0]) {
					writer.addPolygonPoint(coords);
				}
				break;
			}
			case "BEGIN_WINDING":
				writer.beginPolygonWinding();
				break;
			case "END_WINDING":
				writer.endPolygonWinding();
				break;
			case "BEGIN_POLYGON": {
				if (tokens.length >= 3) {
					depth[0] = tokens.length >= 4 ? parseInt(tokens[3], 2) : 2;
					writer.beginPolygon(parseInt(tokens[1], 0), parseInt(tokens[2], 0), depth[0]);
				}
				break;
			}
			case "END_POLYGON":
				writer.endPolygon();
				break;
			case "BEGIN_SEGMENT_CURVED": {
				if (tokens.length >= 10) {
					writer.beginSegment(parseInt(tokens[1], 0), parseInt(tokens[2], 0),
							new double[] { toDouble(tokens[4]), toDouble(tokens[5]), toDouble(tokens[6]),
									toDouble(tokens[3]), toDouble(tokens[7]), toDouble(tokens[8]), toDouble(tokens[9]) },
							true);
				}
				break;
			}
			case "SHAPE_POINT_CURVED": {
				if (tokens.length >= 7) {
					writer.addSegmentShapePoint(new double[] { toDouble(tokens[1]), toDouble(tokens[2]),
							toDouble(tokens[3]), toDouble(tokens[4]), toDouble(tokens[5]), toDouble(tokens[6]) }, true);
				}
				break;
			}
			case "END_SEGMENT_CURVED": {
				if (tokens.length >= 8) {
					writer.endSegment(new double[] { toDouble(tokens[2]), toDouble(tokens[3]), toDouble(tokens[4]),
							toDouble(tokens[1]), toDouble(tokens[5]), toDouble(tokens[6]), toDouble(tokens[7]) }, true);
				}
				break;
			}
			case "FILTER": {
				if (tokens.length >= 2) {
					writer.setFilter(parseInt(tokens[1], 0));
				}
				break;
			}
			default:
				break;
			}
		});
	}

	private interface LineConsumer {
		void accept(String line);
	}

	private static void forEachLine(Path path, LineConsumer consumer) {
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String raw;
			while ((raw = reader.readLine()) != null) {
				String line = strip(raw);
				if (!line.isEmpty()) {
					consumer.accept(line);
				}
			}
		} catch (IOException e) {
			throw new DsfConversionException("Error reading text file " + path, e);
		}
	}

	static String strip(String raw) {
		int start = 0;
		while (start < raw.length() && (raw.charAt(start) == ' ' || raw.charAt(start) == '\t')) {
			start++;
		}
		int end = start;
		while (end < raw.length() && raw.charAt(end) != '#' && raw.charAt(end) != '\r' && raw.charAt(end) != '\n') {
			end++;
		}
		return raw.substring(start, end);
	}

	private static Property parseProperty(String line) {
		if (!line.startsWith("PROPERTY")) {
			return null;
		}
		int pos = "PROPERTY".length();
		if (pos >= line.length() || (line.charAt(pos) != ' ' && line.charAt(pos) != '\t')) {
			return null;
		}
		while (pos < line.length() && (line.charAt(pos) == ' ' || line.charAt(pos) == '\t')) {
			pos++;
		}
		int nameEnd = pos;
		while (nameEnd < line.length() && line.charAt(nameEnd) != ' ' && line.charAt(nameEnd) != '\t') {
			nameEnd++;
		}
		String name = line.substring(pos, nameEnd);
		int valueStart = nameEnd;
		while (valueStart < line.length() && (line.charAt(valueStart) == ' ' || line.charAt(valueStart) == '\t')) {
			valueStart++;
		}
		if (valueStart >= line.length()) {
			return null;
		}
		return new Property(name, line.substring(valueStart));
	}

	private static String afterKeyword(String line, String keyword) {
		if (!line.startsWith(keyword)) {
			return null;
		}
		if (line.length() > keyword.length()) {
			char next = line.charAt(keyword.length());
			if (next != ' ' && next != '\t') {
				return null;
			}
		}
		return line.substring(keyword.length()).trim();
	}

	private static double[] parseDoubles(String[] tokens, int from, int count) {
		double[] result = new double[count];
		for (int i = 0; i < count; i++) {
			result[i] = toDouble(tokens[from + i]);
		}
		return result;
	}

	private static double toDouble(String token) {
		try {
			return Double.parseDouble(token);
		} catch (NumberFormatException e) {
			throw new DsfConversionException("Invalid number in DSF text: " + token, e);
		}
	}

	private static float parseFloat(String token, float defaultValue) {
		try {
			return (float) Double.parseDouble(token);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static int parseInt(String token, int defaultValue) {
		if (token == null || token.isEmpty()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(token.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
}
