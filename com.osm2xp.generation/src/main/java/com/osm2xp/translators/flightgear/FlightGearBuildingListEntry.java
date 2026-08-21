package com.osm2xp.translators.flightgear;

import java.util.Locale;

public class FlightGearBuildingListEntry {

	private final double lat;
	private final double lon;
	private final double groundElev;
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

	public FlightGearBuildingListEntry(double lat, double lon, double groundElev, double streetAngle,
			BuildingListType listType, double width, double depth, double facadeHeight, double roofHeight,
			RoofShape roofShape, int roofOrientation, int levels, int wallTextureIndex, int roofTextureIndex) {
		this.lat = lat;
		this.lon = lon;
		this.groundElev = groundElev;
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

	public double getLat() {
		return lat;
	}

	public double getLon() {
		return lon;
	}

	public double getGroundElev() {
		return groundElev;
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

	/**
	 * Formats the building list data line. FlightGear expects offsets in local
	 * Cartesian metres relative to the STG BUILDING_LIST anchor, in a Z-up frame
	 * where X points south and Y points east. The elevation is the ground
	 * elevation at this building corrected for the round-Earth sagitta, so that
	 * the building drapes over the flat terrain mesh.
	 */
	public String formatDataLine(double anchorLon, double anchorLat) {
		double[] local = FlightGearCoordinateUtils.toLocal(lon, lat, anchorLon, anchorLat);
		double east = local[0];
		double north = local[1];
		double z = groundElev - FlightGearCoordinateUtils.calcHorizonElevLocal(east, north);
		return String.format(Locale.US, "%.1f %.1f %.1f %.0f %d %.1f %.1f %.1f %.1f %d %d %d %d %d\n",
				-north, east, z, streetAngle, listType.getValue(),
				width, depth, facadeHeight, roofHeight, roofShape.getValue(),
				roofOrientation, levels, wallTextureIndex, roofTextureIndex);
	}
}
