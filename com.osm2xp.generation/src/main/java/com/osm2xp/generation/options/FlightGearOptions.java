package com.osm2xp.generation.options;

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
		"generateTransportation", "generateAirfields", "generateAirfieldsBtg",
		"generateChimneys",
		"generateCoolingTowers", "generateBuildingsElevation", "fgelevPath",
		"flightGearSceneryPath",
		"buildingListSmallMinSide",
		"buildingListMediumMinSide", "buildingListLargeMinSide",
		"buildingListSmallMaxLevels", "buildingListMediumMaxLevels",
		"buildingListLargeMaxLevels", "buildingListAllowNeighbours",
		"buildingListAreaDeviation", "buildingListDistDeviation",
		"buildingTextureGroupRadius", "roofShapeFlatRatio",
		"roofShapeGabledRatio", "roofShapeHippedRatio" })
@XmlRootElement(name = "FlightGearOptions")
public class FlightGearOptions {

	@XmlElement(name = "ObjectsRules", required = true)
	protected FlightGearObjectsRulesList objectsRules;

	protected boolean generateBuildings = true;
	protected boolean generateObjects = true;
	protected boolean generateBuildings3D = true;
	protected boolean useBuildingList = true;
	protected boolean generateTransportation = true;
	protected boolean generateAirfields = true;
	protected boolean generateAirfieldsBtg = true;
	protected boolean generateChimneys = true;
	protected boolean generateCoolingTowers = true;
	protected boolean generateBuildingsElevation = true;
	protected String fgelevPath = "";
	protected String flightGearSceneryPath = "";
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
