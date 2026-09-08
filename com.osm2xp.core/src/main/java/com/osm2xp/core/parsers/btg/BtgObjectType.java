package com.osm2xp.core.parsers.btg;

/**
 * BTG object type codes as used by the FlightGear / TerraGear binary scenery
 * format.
 */
public enum BtgObjectType {

	/** Tile bounding sphere (center + radius). */
	BOUNDING_SPHERE(0),
	/** Vertex list. */
	VERTEX_LIST(1),
	/** Packed normal list (3 bytes per normal). */
	NORMAL_LIST(2),
	/** Texture coordinate list. */
	TEXCOORD_LIST(3),
	/** Color list. */
	COLOR_LIST(4),
	/** Point list ({@code SG_POINTS}). */
	POINT_LIST(9),
	/** Triangle list. */
	TRIANGLE_LIST(10),
	/** Triangle strip. */
	TRIANGLE_STRIP(11),
	/** Triangle fan. */
	TRIANGLE_FAN(12);

	private final int code;

	BtgObjectType(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	/** Resolves an object type by its numeric code, or {@code null} if unknown. */
	public static BtgObjectType fromCode(int code) {
		for (BtgObjectType type : values()) {
			if (type.code == code) {
				return type;
			}
		}
		return null;
	}
}
