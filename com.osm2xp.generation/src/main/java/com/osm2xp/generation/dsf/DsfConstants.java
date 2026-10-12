package com.osm2xp.generation.dsf;

/**
 * Constants of the X-Plane DSF binary format and the DSFTool text format.
 *
 * <p>Ported from Laminar Research's open source XPTools (DSFDefs.h / DSFLib /
 * DSFLibWrite / DSF2Text), MIT/X11 licensed, Copyright (c) Laminar Research.
 */
public final class DsfConstants {

	private DsfConstants() {
	}

	public static final String COOKIE = "XPLNEDSF";
	public static final int MASTER_VERSION = 1;
	public static final int FOOTER_LENGTH = 16;

	public static final int ATOM_HEAD = fourcc("HEAD");
	public static final int ATOM_PROP = fourcc("PROP");
	public static final int ATOM_DEFN = fourcc("DEFN");
	public static final int ATOM_TERT = fourcc("TERT");
	public static final int ATOM_OBJT = fourcc("OBJT");
	public static final int ATOM_POLY = fourcc("POLY");
	public static final int ATOM_NETW = fourcc("NETW");
	public static final int ATOM_DEMN = fourcc("DEMN");
	public static final int ATOM_GEOD = fourcc("GEOD");
	public static final int ATOM_POOL = fourcc("POOL");
	public static final int ATOM_SCAL = fourcc("SCAL");
	public static final int ATOM_PO32 = fourcc("PO32");
	public static final int ATOM_SC32 = fourcc("SC32");
	public static final int ATOM_CMDS = fourcc("CMDS");
	public static final int ATOM_DEMS = fourcc("DEMS");
	public static final int ATOM_DEMI = fourcc("DEMI");
	public static final int ATOM_DEMD = fourcc("DEMD");

	public static final int CMD_RESERVED = 0;
	public static final int CMD_POOL_SELECT = 1;
	public static final int CMD_JUNCTION_OFFSET_SELECT = 2;
	public static final int CMD_SET_DEFINITION8 = 3;
	public static final int CMD_SET_DEFINITION16 = 4;
	public static final int CMD_SET_DEFINITION32 = 5;
	public static final int CMD_SET_ROAD_SUBTYPE8 = 6;

	public static final int CMD_OBJECT = 7;
	public static final int CMD_OBJECT_RANGE = 8;

	public static final int CMD_NETWORK_CHAIN = 9;
	public static final int CMD_NETWORK_CHAIN_RANGE = 10;
	public static final int CMD_NETWORK_CHAIN32 = 11;

	public static final int CMD_POLYGON = 12;
	public static final int CMD_POLYGON_RANGE = 13;
	public static final int CMD_NESTED_POLYGON = 14;
	public static final int CMD_NESTED_POLYGON_RANGE = 15;

	public static final int CMD_TERRAIN_PATCH = 16;
	public static final int CMD_TERRAIN_PATCH_FLAGS = 17;
	public static final int CMD_TERRAIN_PATCH_FLAGS_LOD = 18;

	public static final int CMD_TRIANGLE = 23;
	public static final int CMD_TRIANGLE_CROSS_POOL = 24;
	public static final int CMD_TRIANGLE_RANGE = 25;
	public static final int CMD_TRIANGLE_STRIP = 26;
	public static final int CMD_TRIANGLE_STRIP_CROSS_POOL = 27;
	public static final int CMD_TRIANGLE_STRIP_RANGE = 28;
	public static final int CMD_TRIANGLE_FAN = 29;
	public static final int CMD_TRIANGLE_FAN_CROSS_POOL = 30;
	public static final int CMD_TRIANGLE_FAN_RANGE = 31;

	public static final int CMD_COMMENT8 = 32;
	public static final int CMD_COMMENT16 = 33;
	public static final int CMD_COMMENT32 = 34;

	public static final int COMMENT_FILTER = 1;
	public static final int COMMENT_AGL = 2;

	public static final int FLAG_PHYSICAL = 1;
	public static final int FLAG_OVERLAY = 2;

	public static final int TRI = 0;
	public static final int TRI_STRIP = 1;
	public static final int TRI_FAN = 2;

	public static final int MODE_DRAPED = 0;
	public static final int MODE_MSL = 1;
	public static final int MODE_AGL = 2;

	public static final int ENCODE_RAW = 0;
	public static final int ENCODE_DIFFERENCED = 1;
	public static final int ENCODE_RLE = 2;
	public static final int ENCODE_RLE_DIFFERENCED = 3;

	public static final int RASTER_FORMAT_MASK = 3;
	public static final int RASTER_POST = 4;

	public static int fourcc(String s) {
		if (s.length() != 4) {
			throw new IllegalArgumentException("fourcc must be 4 chars: " + s);
		}
		return ((s.charAt(0) & 0xFF) << 24) | ((s.charAt(1) & 0xFF) << 16)
				| ((s.charAt(2) & 0xFF) << 8) | (s.charAt(3) & 0xFF);
	}
}
