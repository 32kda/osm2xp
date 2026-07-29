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
		"useBuildingList", "buildingListSmallMinSide",
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
	protected boolean useBuildingList = true;
	protected double buildingListSmallMinSide = 3.0;
	protected double buildingListMediumMinSide = 10.0;
	protected double buildingListLargeMinSide = 20.0;
	protected int buildingListSmallMaxLevels = 3;
	protected int buildingListMediumMaxLevels = 8;
	protected int buildingListLargeMaxLevels = 22;
	protected boolean buildingListAllowNeighbours = true;
	protected double buildingListAreaDeviation = 0.9;
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

	public boolean isUseBuildingList() {
		return useBuildingList;
	}

	public void setUseBuildingList(boolean useBuildingList) {
		this.useBuildingList = useBuildingList;
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
