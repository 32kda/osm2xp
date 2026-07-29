package com.osm2xp.translators.flightgear;

public enum RoofShape {
	FLAT(0),
	GABLED(1),
	HIPPED(2);

	private final int value;

	RoofShape(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static RoofShape fromOsmTag(String tagValue) {
		if (tagValue == null) return null;
		switch (tagValue.toLowerCase()) {
			case "flat":
			case "skillion":
				return FLAT;
			case "gabled":
			case "gambrel":
				return GABLED;
			case "hipped":
			case "half-hipped":
				return HIPPED;
			default:
				return null;
		}
	}
}
