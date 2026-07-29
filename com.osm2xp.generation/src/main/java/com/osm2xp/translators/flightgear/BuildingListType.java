package com.osm2xp.translators.flightgear;

public enum BuildingListType {
	SMALL(0),
	MEDIUM(1),
	LARGE(2),
	UNSUITABLE(-1);

	private final int value;

	BuildingListType(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}
}
