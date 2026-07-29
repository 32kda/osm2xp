package com.osm2xp.translators.flightgear;

import java.util.Locale;

public class FlightGearBuildingListEntry {

	private final double anchorLat;
	private final double anchorLon;
	private final double elevation;
	private final double streetAngle;
	private final BuildingListType listType;
	private final double width;
	private final double depth;
	private final double facadeHeight;
	private final double roofHeight;
	private final RoofShape roofShape;
	private final int roofOrientation;
	private final int levels;
	private final int wallTextureIndex;
	private final int roofTextureIndex;

	public FlightGearBuildingListEntry(double anchorLat, double anchorLon, double elevation, double streetAngle,
			BuildingListType listType, double width, double depth, double facadeHeight, double roofHeight,
			RoofShape roofShape, int roofOrientation, int levels, int wallTextureIndex, int roofTextureIndex) {
		this.anchorLat = anchorLat;
		this.anchorLon = anchorLon;
		this.elevation = elevation;
		this.streetAngle = streetAngle;
		this.listType = listType;
		this.width = width;
		this.depth = depth;
		this.facadeHeight = facadeHeight;
		this.roofHeight = roofHeight;
		this.roofShape = roofShape;
		this.roofOrientation = roofOrientation;
		this.levels = levels;
		this.wallTextureIndex = wallTextureIndex;
		this.roofTextureIndex = roofTextureIndex;
	}

	public double getAnchorLat() {
		return anchorLat;
	}

	public double getAnchorLon() {
		return anchorLon;
	}

	public double getElevation() {
		return elevation;
	}

	public double getStreetAngle() {
		return streetAngle;
	}

	public BuildingListType getListType() {
		return listType;
	}

	public double getWidth() {
		return width;
	}

	public double getDepth() {
		return depth;
	}

	public double getFacadeHeight() {
		return facadeHeight;
	}

	public double getRoofHeight() {
		return roofHeight;
	}

	public RoofShape getRoofShape() {
		return roofShape;
	}

	public int getRoofOrientation() {
		return roofOrientation;
	}

	public int getLevels() {
		return levels;
	}

	public int getWallTextureIndex() {
		return wallTextureIndex;
	}

	public int getRoofTextureIndex() {
		return roofTextureIndex;
	}

	public String formatDataLine() {
		return String.format(Locale.US, "-%.1f %.1f %.1f %.0f %d %.1f %.1f %.1f %.1f %d %d %d %d %d\n",
				anchorLat, anchorLon, elevation, streetAngle, listType.getValue(),
				width, depth, facadeHeight, roofHeight, roofShape.getValue(),
				roofOrientation, levels, wallTextureIndex, roofTextureIndex);
	}
}
