package com.osm2xp.generation.options;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import com.osm2xp.generation.options.rules.FlightGearObjectsRulesList;
import com.osm2xp.generation.options.rules.ObjectsRulesList;

/**
 * FlightGearOptions.
 * 
 * @author Benjamin Blanchet
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "objectsRules", "generateBuildings",
		"generateObjects", "generateBuildings3D", "useBuildingList",
		"generateTransportation", "generateRoads", "generateRailways",
		"generatePowerLines", "generateAirfields",
		"generateAirfieldsBtg", "generateAirfieldsBtgCut",
		"ignoreExistingAirfields", "ignoredAirfields",
		"generateChimneys",
		"generateCoolingTowers", "generateSilos", "generateStorageTanks",
		"generateBuildingsElevation", "fgelevPath",
		"flightGearSceneryPath", "terrainFormat", "terrainMirrors",
		"buildingListSmallMinSide",
		"buildingListMediumMinSide", "buildingListLargeMinSide",
		"buildingListSmallMaxLevels", "buildingListMediumMaxLevels",
		"buildingListLargeMaxLevels", "buildingListAllowNeighbours",
		"buildingListAreaDeviation", "buildingListDistDeviation",
		"buildingTextureGroupRadius", "roofShapeFlatRatio",
		"roofShapeGabledRatio", "roofShapeHippedRatio" })
@XmlRootElement(name = "FlightGearOptions")
public class FlightGearOptions {

	/**
	 * Default TerraSync mirrors serving the WS2.0 {@code Terrain/} tree, tried in
	 * order. The FlightGear CDN is not reachable from every network, so the
	 * official SourceForge master and the Gdańsk mirror are fallbacks. All expose
	 * the same layout ({@code <base>/Terrain/<band>/<cell>/...}).
	 */
	public static final List<String> DEFAULT_TERRAIN_MIRRORS = Collections.unmodifiableList(Arrays.asList(
			"https://terrasync.b-cdn.net",
			"https://flightgear.sourceforge.net/scenery",
			"https://terrasync.eti.pg.gda.pl/ws2"));

	@XmlElement(name = "ObjectsRules", required = true)
	protected FlightGearObjectsRulesList objectsRules;

	protected boolean generateBuildings = true;
	protected boolean generateObjects = true;
	protected boolean generateBuildings3D = true;
	protected boolean useBuildingList = true;
	protected boolean generateTransportation = true;
	protected boolean generateRoads = false;
	protected boolean generateRailways = false;
	protected boolean generatePowerLines = true;
	protected boolean generateAirfields = true;
	protected boolean generateAirfieldsBtg = true;
	protected boolean generateAirfieldsBtgCut = false;
	protected boolean ignoreExistingAirfields = false;
	protected List<String> ignoredAirfields;
	protected boolean generateChimneys = true;
	protected boolean generateCoolingTowers = true;
	protected boolean generateSilos = true;
	protected boolean generateStorageTanks = true;
	protected boolean generateBuildingsElevation = true;
	protected String fgelevPath = "";
	protected String flightGearSceneryPath = "";
	protected String terrainFormat = "BTG";
	protected List<String> terrainMirrors = new ArrayList<>(DEFAULT_TERRAIN_MIRRORS);
	protected double buildingListSmallMinSide = 3.0;
	protected double buildingListMediumMinSide = 7.0;
	protected double buildingListLargeMinSide = 9.0;
	protected int buildingListSmallMaxLevels = 3;
	protected int buildingListMediumMaxLevels = 9;
	protected int buildingListLargeMaxLevels = 50;
	protected boolean buildingListAllowNeighbours = true;
	protected double buildingListAreaDeviation = 0.85;
	protected double buildingListDistDeviation = 0.8;
	protected int buildingTextureGroupRadius = 0;
	protected double roofShapeFlatRatio = 0.1;
	protected double roofShapeGabledRatio = 0.8;
	protected double roofShapeHippedRatio = 0.1;

	/**
	 * Default no-arg constructor
	 * 
	 */
	public FlightGearOptions() {
		super();
	}

	/**
	 * Fully-initialising value constructor
	 * 
	 */
	public FlightGearOptions(final FlightGearObjectsRulesList objectsRules) {

		this.objectsRules = objectsRules;

	}

	/**
	 * Gets the value of the objectsRules property.
	 * 
	 * @return possible object is {@link ObjectsRulesList }
	 * 
	 */
	public FlightGearObjectsRulesList getObjectsRules() {
		return objectsRules;
	}

	/**
	 * Sets the value of the objectsRules property.
	 * 
	 * @param value
	 *            allowed object is {@link ObjectsRulesList }
	 * 
	 */
	public void setObjectsRules(FlightGearObjectsRulesList value) {
		this.objectsRules = value;
	}

	public boolean isGenerateBuildings() {
		return generateBuildings;
	}

	public void setGenerateBuildings(boolean generateBuildings) {
		this.generateBuildings = generateBuildings;
	}

	public boolean isGenerateObjects() {
		return generateObjects;
	}

	public void setGenerateObjects(boolean generateObjects) {
		this.generateObjects = generateObjects;
	}

	public boolean isGenerateBuildings3D() {
		return generateBuildings3D;
	}

	public void setGenerateBuildings3D(boolean generateBuildings3D) {
		this.generateBuildings3D = generateBuildings3D;
	}

	public boolean isUseBuildingList() {
		return useBuildingList;
	}

	public void setUseBuildingList(boolean useBuildingList) {
		this.useBuildingList = useBuildingList;
	}

	public boolean isGenerateTransportation() {
		return generateTransportation;
	}

	public void setGenerateTransportation(boolean generateTransportation) {
		this.generateTransportation = generateTransportation;
	}

	public boolean isGenerateRoads() {
		return generateRoads;
	}

	public void setGenerateRoads(boolean generateRoads) {
		this.generateRoads = generateRoads;
	}

	public boolean isGenerateRailways() {
		return generateRailways;
	}

	public void setGenerateRailways(boolean generateRailways) {
		this.generateRailways = generateRailways;
	}

	public boolean isGeneratePowerLines() {
		return generatePowerLines;
	}

	public void setGeneratePowerLines(boolean generatePowerLines) {
		this.generatePowerLines = generatePowerLines;
	}

	public boolean isGenerateAirfields() {
		return generateAirfields;
	}

	public void setGenerateAirfields(boolean generateAirfields) {
		this.generateAirfields = generateAirfields;
	}

	public boolean isGenerateAirfieldsBtg() {
		return generateAirfieldsBtg;
	}

	public void setGenerateAirfieldsBtg(boolean generateAirfieldsBtg) {
		this.generateAirfieldsBtg = generateAirfieldsBtg;
	}

	public boolean isGenerateAirfieldsBtgCut() {
		return generateAirfieldsBtgCut;
	}

	public void setGenerateAirfieldsBtgCut(boolean generateAirfieldsBtgCut) {
		this.generateAirfieldsBtgCut = generateAirfieldsBtgCut;
	}

	public boolean isIgnoreExistingAirfields() {
		return ignoreExistingAirfields;
	}

	public void setIgnoreExistingAirfields(boolean ignoreExistingAirfields) {
		this.ignoreExistingAirfields = ignoreExistingAirfields;
	}

	public List<String> getIgnoredAirfields() {
		if (ignoredAirfields == null) {
			ignoredAirfields = new ArrayList<>();
		}
		return ignoredAirfields;
	}

	public void setIgnoredAirfields(List<String> ignoredAirfields) {
		this.ignoredAirfields = ignoredAirfields;
	}

	public boolean isGenerateChimneys() {
		return generateChimneys;
	}

	public void setGenerateChimneys(boolean generateChimneys) {
		this.generateChimneys = generateChimneys;
	}

	public boolean isGenerateCoolingTowers() {
		return generateCoolingTowers;
	}

	public void setGenerateCoolingTowers(boolean generateCoolingTowers) {
		this.generateCoolingTowers = generateCoolingTowers;
	}

	public boolean isGenerateSilos() {
		return generateSilos;
	}

	public void setGenerateSilos(boolean generateSilos) {
		this.generateSilos = generateSilos;
	}

	public boolean isGenerateStorageTanks() {
		return generateStorageTanks;
	}

	public void setGenerateStorageTanks(boolean generateStorageTanks) {
		this.generateStorageTanks = generateStorageTanks;
	}

	public boolean isGenerateBuildingsElevation() {
		return generateBuildingsElevation;
	}

	public void setGenerateBuildingsElevation(boolean generateBuildingsElevation) {
		this.generateBuildingsElevation = generateBuildingsElevation;
	}

	public String getFgelevPath() {
		return fgelevPath;
	}

	public void setFgelevPath(String fgelevPath) {
		this.fgelevPath = fgelevPath;
	}

	public String getFlightGearSceneryPath() {
		return flightGearSceneryPath;
	}

	public void setFlightGearSceneryPath(String flightGearSceneryPath) {
		this.flightGearSceneryPath = flightGearSceneryPath;
	}

	public String getTerrainFormat() {
		return terrainFormat;
	}

	public void setTerrainFormat(String terrainFormat) {
		this.terrainFormat = terrainFormat;
	}

	/**
	 * TerraSync mirrors used for terrain downloads, tried in order until one is
	 * reachable. Defaults to {@link #DEFAULT_TERRAIN_MIRRORS}; override to pin a
	 * specific mirror or add a local one.
	 */
	public List<String> getTerrainMirrors() {
		if (terrainMirrors == null || terrainMirrors.isEmpty()) {
			terrainMirrors = new ArrayList<>(DEFAULT_TERRAIN_MIRRORS);
		}
		return terrainMirrors;
	}

	public void setTerrainMirrors(List<String> terrainMirrors) {
		this.terrainMirrors = terrainMirrors;
	}

	public double getBuildingListSmallMinSide() {
		return buildingListSmallMinSide;
	}

	public void setBuildingListSmallMinSide(double buildingListSmallMinSide) {
		this.buildingListSmallMinSide = buildingListSmallMinSide;
	}

	public double getBuildingListMediumMinSide() {
		return buildingListMediumMinSide;
	}

	public void setBuildingListMediumMinSide(double buildingListMediumMinSide) {
		this.buildingListMediumMinSide = buildingListMediumMinSide;
	}

	public double getBuildingListLargeMinSide() {
		return buildingListLargeMinSide;
	}

	public void setBuildingListLargeMinSide(double buildingListLargeMinSide) {
		this.buildingListLargeMinSide = buildingListLargeMinSide;
	}

	public int getBuildingListSmallMaxLevels() {
		return buildingListSmallMaxLevels;
	}

	public void setBuildingListSmallMaxLevels(int buildingListSmallMaxLevels) {
		this.buildingListSmallMaxLevels = buildingListSmallMaxLevels;
	}

	public int getBuildingListMediumMaxLevels() {
		return buildingListMediumMaxLevels;
	}

	public void setBuildingListMediumMaxLevels(int buildingListMediumMaxLevels) {
		this.buildingListMediumMaxLevels = buildingListMediumMaxLevels;
	}

	public int getBuildingListLargeMaxLevels() {
		return buildingListLargeMaxLevels;
	}

	public void setBuildingListLargeMaxLevels(int buildingListLargeMaxLevels) {
		this.buildingListLargeMaxLevels = buildingListLargeMaxLevels;
	}

	public boolean isBuildingListAllowNeighbours() {
		return buildingListAllowNeighbours;
	}

	public void setBuildingListAllowNeighbours(boolean buildingListAllowNeighbours) {
		this.buildingListAllowNeighbours = buildingListAllowNeighbours;
	}

	public double getBuildingListAreaDeviation() {
		return buildingListAreaDeviation;
	}

	public void setBuildingListAreaDeviation(double buildingListAreaDeviation) {
		this.buildingListAreaDeviation = buildingListAreaDeviation;
	}

	public double getBuildingListDistDeviation() {
		return buildingListDistDeviation;
	}

	public void setBuildingListDistDeviation(double buildingListDistDeviation) {
		this.buildingListDistDeviation = buildingListDistDeviation;
	}

	public int getBuildingTextureGroupRadius() {
		return buildingTextureGroupRadius;
	}

	public void setBuildingTextureGroupRadius(int buildingTextureGroupRadius) {
		this.buildingTextureGroupRadius = buildingTextureGroupRadius;
	}

	public double getRoofShapeFlatRatio() {
		return roofShapeFlatRatio;
	}

	public void setRoofShapeFlatRatio(double roofShapeFlatRatio) {
		this.roofShapeFlatRatio = roofShapeFlatRatio;
	}

	public double getRoofShapeGabledRatio() {
		return roofShapeGabledRatio;
	}

	public void setRoofShapeGabledRatio(double roofShapeGabledRatio) {
		this.roofShapeGabledRatio = roofShapeGabledRatio;
	}

	public double getRoofShapeHippedRatio() {
		return roofShapeHippedRatio;
	}

	public void setRoofShapeHippedRatio(double roofShapeHippedRatio) {
		this.roofShapeHippedRatio = roofShapeHippedRatio;
	}

}
