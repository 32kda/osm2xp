package com.osm2xp.generation.options;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import com.osm2xp.generation.options.rules.FacadesRulesList;
import com.osm2xp.generation.options.rules.ForestsRulesList;
import com.osm2xp.generation.options.rules.ObjectsRulesList;
import com.osm2xp.generation.options.rules.PolygonRulesList;
import com.osm2xp.generation.options.rules.XplaneObjectsRulesList;

/**
 * XplaneOptions.
 * 
 * @author Benjamin Blanchet
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = { "autoExclude", "excludeObj", "excludeFac", "excludeFor", "smartExclusions", "smartExclusionSize",
		"smartExclusionDistance", "excludeBch", "excludeNet", "excludeLin", "excludePol", "excludeStr",
		"exclusionsFromInput", "residentialMin", "residentialMax", "buildingMin", "buildingMax", "minHouseSegment",
		"maxHouseSegment", "minHouseArea", "objectRenderLevel", "facadeRenderLevel", "buildLibrary", "generateObj",
		"generateObjBuildings", "generateFor", "generateBuildings", "generatePowerlines", "generateRailways",
		"generateRoads", "generateFence", "generateTanks", "generateChimneys", "generateCoolingTowers",
		"generateBridges", "generateSlopedRoofs", "generatePolys", "generateStreetLights", "lightsDensity",
		"city3LaneHighwayRoadType", "country3LaneHighwayRoadType","city2LaneHighwayRoadType","country2LaneHighwayRoadType","cityRoadType",    
		"countryRoadType", "oneLaneRoadType","railwayType", "powerlineType", "packageFacades", "hardBuildings", "lightObject", "lightObjectString", "facadeSets", 
		"restrictFacadeLod", "facadeLod", "generateXmlStats", "generatePdfStats", "generateDebugImg", "generateComments", "generateHighwayLights", 
		"roadBridgeRampLen","railBridgeRampLen", "streetLightsInterval", "maxPerimeterToSimplify", "objSizeTolerance", "objHeightTolerance", "objHeightAllowedDifference",
		"buildingsExclusions", "forestsRules", "objectsRules", "lightsRules", "facadesRules", "polygonRules", "streetLightObjects", "airfieldOptions",
		"deleteSrc" })
@XmlRootElement(name = "XplaneOptions")
public class XplaneOptions {

	protected boolean autoExclude = true;
	protected boolean excludeObj = true;
	protected boolean excludeFac = true;
	protected boolean excludeFor = true;
	protected boolean excludeBch = false;
	protected boolean excludeNet = true;
	protected boolean excludeLin = false;
	protected boolean excludePol = true;
	protected boolean excludeStr = false;
	protected boolean exclusionsFromInput = true;
	protected boolean smartExclusions;
	protected int residentialMin;
	protected int residentialMax;
	protected int buildingMin;
	protected int buildingMax;
	protected int minHouseSegment;
	protected int maxHouseSegment;
	protected int minHouseArea;
	protected int smartExclusionSize;
	protected int smartExclusionDistance;
	/**
	 * Object detail level for generated overlay, 1-6 (sim/require_object prop)
	 */
	protected int objectRenderLevel = 1;
	/**
	 * Facade detail level for generated overlay, 1-6 (sim/require_facade prop)
	 */
	protected int facadeRenderLevel = 3;
	/**
	 * Build separate X-Plane library - <code>true</code> or bundle all the resources into generated scenary - <code>false</code>
	 */
	protected boolean buildLibrary = false;
	protected boolean generateObj = true;
	protected boolean generateObjBuildings = true;
	protected boolean generateFor = true;
	protected boolean generateBuildings = true;
	protected boolean generatePowerlines = true;
	protected boolean generateRailways = true;
	protected boolean generateRoads = true;
	protected boolean generateFence = true;
	protected boolean generateTanks = true;
	protected boolean generateChimneys = true;
	protected boolean generateCoolingTowers= true;
	protected boolean generateBridges = true;
	protected boolean generatePolys = true;
	protected boolean generateStreetLights = true;
	protected boolean generateSlopedRoofs;
	protected boolean deleteSrc = true;
	protected int lightsDensity;
	protected boolean packageFacades;
	protected boolean hardBuildings;
	@XmlElement
	protected String lightObject;
	protected String lightObjectString = "objects/column_sng.str";
	protected String facadeSets;
	protected boolean restrictFacadeLod = false;
	protected int facadeLod;
//	@XmlElement(required = true)
//	protected String facadeSet;
	protected boolean generateXmlStats;
	protected boolean generatePdfStats;
	protected boolean generateDebugImg = false;
	protected boolean generateComments = false;
	protected boolean generateHighwayLights = true;
	protected int roadBridgeRampLen = 100;
	protected int railBridgeRampLen = 200;
	protected int streetLightsInterval = 50;
	protected int maxPerimeterToSimplify = 50;
	protected double objSizeTolerance = 0.1;
	protected double objHeightTolerance = 0.3;
	protected int objHeightAllowedDifference = 6;
	@XmlElement(name = "BuildingsExclusions", required = true)
	protected BuildingsExclusionsList buildingsExclusions;
	@XmlElement(name = "ForestsRules", required = true)
	protected ForestsRulesList forestsRules;
	@XmlElement(name = "ObjectsRules", required = true)
	protected XplaneObjectsRulesList objectsRules;
	@XmlElement(name = "LightsRules", required = true)
	protected LightsRulesList lightsRules;
	@XmlElement(name = "FacadesRules", required = true)
	protected FacadesRulesList facadesRules;
	@XmlElement(name = "StreetLightObjects", required = true)
	protected ObjectsList streetLightObjects;
	@XmlElement(name="Polygons")
	protected PolygonRulesList polygonRules;
	@XmlElement(name="AirfieldOptions", required=true) 
	protected XplaneAirfieldOptions airfieldOptions;
	
	protected int city3LaneHighwayRoadType = 14;
	protected int country3LaneHighwayRoadType = 14;
	protected int city2LaneHighwayRoadType=40;
	protected int country2LaneHighwayRoadType=40;
	protected int cityRoadType=40;
	protected int countryRoadType=40;
	protected int oneLaneRoadType=50;
	protected int railwayType=151;
	protected int powerlineType=220;
	/**
	 * Default no-arg constructor
	 * 
	 */
	public XplaneOptions() {
		super();
	}

	/**
	 * Fully-initializing value constructor
	 * 
	 */
	public XplaneOptions(final boolean excludeObj, final boolean excludeFac,
			final boolean excludeFor, final boolean excludeBch,
			final boolean excludeNet, final boolean excludeLin,
			final boolean excludePol, final boolean excludeStr,
			final int residentialMin, final int residentialMax,
			final int buildingMin, final int buildingMax,
			final int minHouseSegment, final int maxHouseSegment,
			final int minHouseArea, final boolean generateObj,
			final boolean generateFor, final boolean generateStreetLights,
			final boolean generateBuildings, final boolean generateSlopedRoofs,
			final int lightsDensity, final boolean packageFacades,
			final boolean hardBuildings, final String lightObject,
			final int facadeLod, final boolean generateXmlStats, final boolean generatePdfStats,
			final BuildingsExclusionsList buildingsExclusions,
			final ForestsRulesList forestsRules,
			final LightsRulesList lightsRules,
			final XplaneObjectsRulesList objectsRules,
			final FacadesRulesList facadesRules,
			final ObjectsList streetLightObjects,
			final int smartExclusionDistance, final int smartExclusionSize) {
		this.excludeObj = excludeObj;
		this.excludeFac = excludeFac;
		this.excludeFor = excludeFor;
		this.excludeBch = excludeBch;
		this.excludeNet = excludeNet;
		this.excludeLin = excludeLin;
		this.excludePol = excludePol;
		this.excludeStr = excludeStr;
		this.residentialMin = residentialMin;
		this.residentialMax = residentialMax;
		this.buildingMin = buildingMin;
		this.buildingMax = buildingMax;
		this.minHouseSegment = minHouseSegment;
		this.maxHouseSegment = maxHouseSegment;
		this.minHouseArea = minHouseArea;
		this.generateObj = generateObj;
		this.generateFor = generateFor;
		this.generateStreetLights = generateStreetLights;
		this.generateBuildings = generateBuildings;
		this.generateSlopedRoofs = generateSlopedRoofs;
		this.lightsDensity = lightsDensity;
		this.packageFacades = packageFacades;
		this.hardBuildings = hardBuildings;
		this.lightObject = lightObject;
		this.facadeLod = facadeLod;
//		this.facadeSet = facadeSet;
		this.generateXmlStats = generateXmlStats;
		this.generatePdfStats = generatePdfStats;
		this.buildingsExclusions = buildingsExclusions;
		this.forestsRules = forestsRules;
		this.objectsRules = objectsRules;
		this.facadesRules = facadesRules;
		this.streetLightObjects = streetLightObjects;
		this.smartExclusionDistance = smartExclusionDistance;
		this.smartExclusionSize = smartExclusionSize;
		this.lightsRules = lightsRules;
	}

	/**
	 * Gets the value of the excludeObj property.
	 * 
	 */
	public boolean isExcludeObj() {
		return excludeObj;
	}

	/**
	 * Sets the value of the excludeObj property.
	 * 
	 */
	public void setExcludeObj(boolean value) {
		this.excludeObj = value;
	}

	/**
	 * Gets the value of the excludeFac property.
	 * 
	 */
	public boolean isExcludeFac() {
		return excludeFac;
	}

	/**
	 * Sets the value of the excludeFac property.
	 * 
	 */
	public void setExcludeFac(boolean value) {
		this.excludeFac = value;
	}

	/**
	 * Gets the value of the excludeFor property.
	 * 
	 */
	public boolean isExcludeFor() {
		return excludeFor;
	}

	/**
	 * Sets the value of the excludeFor property.
	 * 
	 */
	public void setExcludeFor(boolean value) {
		this.excludeFor = value;
	}

	/**
	 * Gets the value of the excludeBch property.
	 * 
	 */
	public boolean isExcludeBch() {
		return excludeBch;
	}

	/**
	 * Sets the value of the excludeBch property.
	 * 
	 */
	public void setExcludeBch(boolean value) {
		this.excludeBch = value;
	}

	/**
	 * Gets the value of the excludeNet property.
	 * 
	 */
	public boolean isExcludeNet() {
		return excludeNet;
	}

	/**
	 * Sets the value of the excludeNet property.
	 * 
	 */
	public void setExcludeNet(boolean value) {
		this.excludeNet = value;
	}

	/**
	 * Gets the value of the excludeLin property.
	 * 
	 */
	public boolean isExcludeLin() {
		return excludeLin;
	}

	/**
	 * Sets the value of the excludeLin property.
	 * 
	 */
	public void setExcludeLin(boolean value) {
		this.excludeLin = value;
	}

	/**
	 * Gets the value of the excludePol property.
	 * 
	 */
	public boolean isExcludePol() {
		return excludePol;
	}

	/**
	 * Sets the value of the excludePol property.
	 * 
	 */
	public void setExcludePol(boolean value) {
		this.excludePol = value;
	}

	/**
	 * Gets the value of the excludeStr property.
	 * 
	 */
	public boolean isExcludeStr() {
		return excludeStr;
	}

	/**
	 * Sets the value of the excludeStr property.
	 * 
	 */
	public void setExcludeStr(boolean value) {
		this.excludeStr = value;
	}

	/**
	 * Gets the value of the residentialMin property.
	 * 
	 */
	public int getResidentialMin() {
		return residentialMin;
	}

	/**
	 * Sets the value of the residentialMin property.
	 * 
	 */
	public void setResidentialMin(int value) {
		this.residentialMin = value;
	}

	/**
	 * Gets the value of the residentialMax property.
	 * 
	 */
	public int getResidentialMax() {
		return residentialMax;
	}

	/**
	 * Sets the value of the residentialMax property.
	 * 
	 */
	public void setResidentialMax(int value) {
		this.residentialMax = value;
	}

	/**
	 * Gets the value of the buildingMin property.
	 * 
	 */
	public int getBuildingMin() {
		return buildingMin;
	}

	/**
	 * Sets the value of the buildingMin property.
	 * 
	 */
	public void setBuildingMin(int value) {
		this.buildingMin = value;
	}

	/**
	 * Gets the value of the buildingMax property.
	 * 
	 */
	public int getBuildingMax() {
		return buildingMax;
	}

	/**
	 * Sets the value of the buildingMax property.
	 * 
	 */
	public void setBuildingMax(int value) {
		this.buildingMax = value;
	}

	/**
	 * Gets the value of the minHouseSegment property.
	 * 
	 */
	public int getMinHouseSegment() {
		return minHouseSegment;
	}

	/**
	 * Sets the value of the minHouseSegment property.
	 * 
	 */
	public void setMinHouseSegment(int value) {
		this.minHouseSegment = value;
	}

	/**
	 * Gets the value of the maxHouseSegment property.
	 * 
	 */
	public int getMaxHouseSegment() {
		return maxHouseSegment;
	}

	/**
	 * Sets the value of the maxHouseSegment property.
	 * 
	 */
	public void setMaxHouseSegment(int value) {
		this.maxHouseSegment = value;
	}

	/**
	 * Gets the value of the minHouseArea property.
	 * 
	 */
	public int getMinHouseArea() {
		return minHouseArea;
	}

	/**
	 * Sets the value of the minHouseArea property.
	 * 
	 */
	public void setMinHouseArea(int value) {
		this.minHouseArea = value;
	}

	/**
	 * Gets the value of the generateObj property.
	 * 
	 */
	public boolean isGenerateObj() {
		return generateObj;
	}

	/**
	 * Sets the value of the generateObj property.
	 * 
	 */
	public void setGenerateObj(boolean value) {
		this.generateObj = value;
	}

	/**
	 * Gets the value of the generateFor property.
	 * 
	 */
	public boolean isGenerateFor() {
		return generateFor;
	}

	/**
	 * Sets the value of the generateFor property.
	 * 
	 */
	public void setGenerateFor(boolean value) {
		this.generateFor = value;
	}

	/**
	 * Gets the value of the generateStreetLights property.
	 * 
	 */
	public boolean isGenerateStreetLights() {
		return generateStreetLights;
	}

	/**
	 * Sets the value of the generateStreetLights property.
	 * 
	 */
	public void setGenerateStreetLights(boolean value) {
		this.generateStreetLights = value;
	}

	/**
	 * Gets the value of the generateBuildings property.
	 * 
	 */
	public boolean isGenerateBuildings() {
		return generateBuildings;
	}

	/**
	 * Sets the value of the generateBuildings property.
	 * 
	 */
	public void setGenerateBuildings(boolean value) {
		this.generateBuildings = value;
	}

	/**
	 * Gets the value of the generateSlopedRoofs property.
	 * 
	 */
	public boolean isGenerateSlopedRoofs() {
		return generateSlopedRoofs;
	}

	/**
	 * Sets the value of the generateSlopedRoofs property.
	 * 
	 */
	public void setGenerateSlopedRoofs(boolean value) {
		this.generateSlopedRoofs = value;
	}

	/**
	 * Gets the value of the lightsDensity property.
	 * 
	 */
	public int getLightsDensity() {
		return lightsDensity;
	}

	/**
	 * Sets the value of the lightsDensity property.
	 * 
	 */
	public void setLightsDensity(int value) {
		this.lightsDensity = value;
	}

	/**
	 * Gets the value of the packageFacades property.
	 * 
	 */
	public boolean isPackageFacades() {
		return packageFacades;
	}

	/**
	 * Sets the value of the packageFacades property.
	 * 
	 */
	public void setPackageFacades(boolean value) {
		this.packageFacades = value;
	}

	/**
	 * Gets the value of the hardBuildings property.
	 * 
	 */
	public boolean isHardBuildings() {
		return hardBuildings;
	}

	/**
	 * Sets the value of the hardBuildings property.
	 * 
	 */
	public void setHardBuildings(boolean value) {
		this.hardBuildings = value;
	}

	/**
	 * Gets the value of the lightObject property.
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
	public String getLightObject() {
		return lightObject;
	}

	/**
	 * Sets the value of the lightObject property.
	 * 
	 * @param value
	 *            allowed object is {@link String }
	 * 
	 */
	public void setLightObject(String value) {
		this.lightObject = value;
	}

	/**
	 * Gets the value of the facadeLod property.
	 * 
	 */
	public int getFacadeLod() {
		return facadeLod;
	}

	/**
	 * Sets the value of the facadeLod property.
	 * 
	 */
	public void setFacadeLod(int value) {
		this.facadeLod = value;
	}

	/**
	 * Gets the value of the facadeSet property.
	 * 
	 * @return possible object is {@link String }
	 * 
	 */
//	public String getFacadeSet() {
//		return facadeSet;
//	}

	/**
	 * Sets the value of the facadeSet property.
	 * 
	 * @param value
	 *            allowed object is {@link String }
	 * 
	 */
//	public void setFacadeSet(String value) {
//		this.facadeSet = value;
//	}

	/**
	 * Gets the value of the generateXmlStats property.
	 * 
	 */
	public boolean isGenerateXmlStats() {
		return generateXmlStats;
	}

	/**
	 * Sets the value of the generateXmlStats property.
	 * 
	 */
	public void setGenerateXmlStats(boolean value) {
		this.generateXmlStats = value;
	}

	/**
	 * Gets the value of the generatePdfStats property.
	 * 
	 */
	public boolean isGeneratePdfStats() {
		return generatePdfStats;
	}

	/**
	 * Sets the value of the generatePdfStats property.
	 * 
	 */
	public void setGeneratePdfStats(boolean value) {
		this.generatePdfStats = value;
	}

	/**
	 * Gets the value of the buildingsExclusions property.
	 * 
	 * @return possible object is {@link BuildingsExclusionsList }
	 * 
	 */
	public BuildingsExclusionsList getBuildingsExclusions() {
		return buildingsExclusions;
	}

	/**
	 * Sets the value of the buildingsExclusions property.
	 * 
	 * @param value
	 *            allowed object is {@link BuildingsExclusionsList }
	 * 
	 */
	public void setBuildingsExclusions(BuildingsExclusionsList value) {
		this.buildingsExclusions = value;
	}

	/**
	 * Gets the value of the forestsRules property.
	 * 
	 * @return possible object is {@link ForestsRulesList }
	 * 
	 */
	public ForestsRulesList getForestsRules() {
		return forestsRules;
	}

	/**
	 * Sets the value of the forestsRules property.
	 * 
	 * @param value
	 *            allowed object is {@link ForestsRulesList }
	 * 
	 */
	public void setForestsRules(ForestsRulesList value) {
		this.forestsRules = value;
	}

	/**
	 * Gets the value of the objectsRules property.
	 * 
	 * @return possible object is {@link ObjectsRulesList }
	 * 
	 */
	public XplaneObjectsRulesList getObjectsRules() {
		return objectsRules;
	}

	/**
	 * Sets the value of the objectsRules property.
	 * 
	 * @param value
	 *            allowed object is {@link ObjectsRulesList }
	 * 
	 */
	public void setObjectsRules(XplaneObjectsRulesList value) {
		this.objectsRules = value;
	}

	/**
	 * Gets the value of the facadesRules property.
	 * 
	 * @return possible object is {@link FacadesRulesList }
	 * 
	 */
	public FacadesRulesList getFacadesRules() {
		return facadesRules;
	}

	/**
	 * Sets the value of the facadesRules property.
	 * 
	 * @param value
	 *            allowed object is {@link FacadesRulesList }
	 * 
	 */
	public void setFacadesRules(FacadesRulesList value) {
		this.facadesRules = value;
	}

	/**
	 * Gets the value of the streetLightObjects property.
	 * 
	 * @return possible object is {@link ObjectsList }
	 * 
	 */
	public ObjectsList getStreetLightObjects() {
		return streetLightObjects;
	}

	/**
	 * Sets the value of the streetLightObjects property.
	 * 
	 * @param value
	 *            allowed object is {@link ObjectsList }
	 * 
	 */
	public void setStreetLightObjects(ObjectsList value) {
		this.streetLightObjects = value;
	}

	@Deprecated
	/**
	 * @return
	 * Should use exclusion zone from input file instead
	 */
	public boolean isSmartExclusions() {
		return smartExclusions;
	}

	@Deprecated
	public void setSmartExclusions(boolean smartExclusions) {
		this.smartExclusions = smartExclusions;
	}

	public int getSmartExclusionSize() {
		return smartExclusionSize;
	}

	public void setSmartExclusionSize(int smartExclusionSize) {
		this.smartExclusionSize = smartExclusionSize;
	}

	public int getSmartExclusionDistance() {
		return smartExclusionDistance;
	}

	public void setSmartExclusionDistance(int smartExclusionDistance) {
		this.smartExclusionDistance = smartExclusionDistance;
	}

	public LightsRulesList getLightsRules() {
		return lightsRules;
	}

	public void setLightsRules(LightsRulesList lightsRules) {
		this.lightsRules = lightsRules;
	}

	public boolean isGeneratePowerlines() {
		return generatePowerlines;
	}

	public void setGeneratePowerlines(boolean generatePowerlines) {
		this.generatePowerlines = generatePowerlines;
	}

	public boolean isGenerateRailways() {
		return generateRailways;
	}

	public void setGenerateRailways(boolean generateRailways) {
		this.generateRailways = generateRailways;
	}

	public boolean isGenerateRoads() {
		return generateRoads;
	}

	public void setGenerateRoads(boolean generateRoads) {
		this.generateRoads = generateRoads;
	}

	public boolean isGenerateFence() {
		return generateFence;
	}

	public void setGenerateFence(boolean generateFence) {
		this.generateFence = generateFence;
	}

	public boolean isGenerateDebugImg() {
		return generateDebugImg;
	}

	public void setGenerateDebugImg(boolean generateDebugImg) {
		this.generateDebugImg = generateDebugImg;
	}

	public boolean isGenerateTanks() {
		return generateTanks;
	}

	public void setGenerateTanks(boolean generateTanks) {
		this.generateTanks = generateTanks;
	}

	public boolean isGenerateChimneys() {
		return generateChimneys;
	}

	public void setGenerateChimneys(boolean generateChimneys) {
		this.generateChimneys = generateChimneys;
	}
	
	public boolean isGenerateBridges() {
		return generateBridges;
	}
	
	public void setGenerateBridges(boolean generateBridges) {
		this.generateBridges= generateBridges;
	}

	public boolean isGenerateComments() {
		return generateComments;
	}

	public void setGenerateComments(boolean generateComments) {
		this.generateComments = generateComments;
	}
	/**
	 * @return Road bridge ramp max length, 100m by default
	 */
	public int getRoadBridgeRampLen() {
		return roadBridgeRampLen;
	}

	public void setRoadBridgeRampLen(int roadBridgeRampLen) {
		this.roadBridgeRampLen = roadBridgeRampLen;
	}

	/**
	 * @return Rail bridge ramp max length, 200m by default
	 */
	public int getRailBridgeRampLen() {
		return railBridgeRampLen;
	}

	public void setRailBridgeRampLen(int railBridgeRampLen) {
		this.railBridgeRampLen = railBridgeRampLen;
	}

	public boolean isGenerateCoolingTowers() {
		return generateCoolingTowers;
	}

	public void setGenerateCoolingTowers(boolean generateCoolingTowers) {
		this.generateCoolingTowers = generateCoolingTowers;
	}

	public XplaneAirfieldOptions getAirfieldOptions() {
		if (airfieldOptions == null) {
			airfieldOptions = new XplaneAirfieldOptions();
		}
		return airfieldOptions;
	}

	public void setAirfieldOptions(XplaneAirfieldOptions airfieldOptions) {
		this.airfieldOptions = airfieldOptions;
	}

	public boolean isExclusionsFromInput() {
		return exclusionsFromInput;
	}

	public void setExclusionsFromInput(boolean exclusionsFromInput) {
		this.exclusionsFromInput = exclusionsFromInput;
	}

	public String getFacadeSets() {
		return facadeSets;
	}

	/**
	 * Set facade set string
	 * @param facadeSets Facade sets, separated with ';'
	 */
	public void setFacadeSets(String facadeSets) {
		this.facadeSets = facadeSets;
	}

	public boolean isRestrictFacadeLod() {
		return restrictFacadeLod;
	}

	public void setRestrictFacadeLod(boolean restrictFacadeLod) {
		this.restrictFacadeLod = restrictFacadeLod;
	}

	public PolygonRulesList getPolygonRules() {
		if (polygonRules == null) {
			polygonRules = new PolygonRulesList();
		}
		return polygonRules;
	}

	public void setPolygonRules(PolygonRulesList polygonRules) {
		this.polygonRules = polygonRules;
	}

	public boolean isGeneratePolys() {
		return generatePolys;
	}

	public void setGeneratePolys(boolean generatePolys) {
		this.generatePolys = generatePolys;
	}

	public boolean isGenerateObjBuildings() {
		return generateObjBuildings;
	}

	public void setGenerateObjBuildings(boolean generateObjBuildings) {
		this.generateObjBuildings = generateObjBuildings;
	}

	public double getObjSizeTolerance() {
		return objSizeTolerance;
	}

	public void setObjSizeTolerance(double objSizeTolerance) {
		this.objSizeTolerance = objSizeTolerance;
	}

	public int getMaxPerimeterToSimplify() {
		return maxPerimeterToSimplify;
	}

	public void setMaxPerimeterToSimplify(int maxPerimeterToSimplify) {
		this.maxPerimeterToSimplify = maxPerimeterToSimplify;
	}

	public boolean isDeleteSrc() {
		return deleteSrc;
	}

	public void setDeleteSrc(boolean deleteSrc) {
		this.deleteSrc = deleteSrc;
	}

	public int getObjectRenderLevel() {
		return objectRenderLevel;
	}

	public void setObjectRenderLevel(int objectRenderLevel) {
		this.objectRenderLevel = objectRenderLevel;
	}

	public int getFacadeRenderLevel() {
		return facadeRenderLevel;
	}

	public void setFacadeRenderLevel(int facadeRenderLevel) {
		this.facadeRenderLevel = facadeRenderLevel;
	}

	public boolean isBuildLibrary() {
		return buildLibrary;
	}

	public void setBuildLibrary(boolean buildLibrary) {
		this.buildLibrary = buildLibrary;
	}

	public boolean isAutoExclude() {
		return autoExclude;
	}

	public void setAutoExclude(boolean autoExclude) {
		this.autoExclude = autoExclude;
	}

	public int getCity3LaneHighwayRoadType() {
		return city3LaneHighwayRoadType;
	}

	public void setCity3LaneHighwayRoadType(int city3LaneHighwayRoadType) {
		this.city3LaneHighwayRoadType = city3LaneHighwayRoadType;
	}

	public int getCity2LaneHighwayRoadType() {
		return city2LaneHighwayRoadType;
	}

	public void setCity2LaneHighwayRoadType(int city2LaneHighwayRoadType) {
		this.city2LaneHighwayRoadType = city2LaneHighwayRoadType;
	}

	public int getCountry2LaneHighwayRoadType() {
		return country2LaneHighwayRoadType;
	}

	public void setCountry2LaneHighwayRoadType(int country2LaneHighwayRoadType) {
		this.country2LaneHighwayRoadType = country2LaneHighwayRoadType;
	}

	public int getCityRoadType() {
		return cityRoadType;
	}

	public void setCityRoadType(int cityRoadType) {
		this.cityRoadType = cityRoadType;
	}

	public int getCountryRoadType() {
		return countryRoadType;
	}

	public void setCountryRoadType(int countryRoadType) {
		this.countryRoadType = countryRoadType;
	}

	public int getOneLaneRoadType() {
		return oneLaneRoadType;
	}

	public void setOneLaneRoadType(int oneLaneRoadType) {
		this.oneLaneRoadType = oneLaneRoadType;
	}

	public int getCountry3LaneHighwayRoadType() {
		return country3LaneHighwayRoadType;
	}

	public void setCountry3LaneHighwayRoadType(int country3LaneHighwayRoadType) {
		this.country3LaneHighwayRoadType = country3LaneHighwayRoadType;
	}

	public int getPowerlineType() {
		return powerlineType;
	}

	public void setPowerlineType(int powerlineType) {
		this.powerlineType = powerlineType;
	}

	public int getRailwayType() {
		return railwayType;
	}

	public void setRailwayType(int railwayType) {
		this.railwayType = railwayType;
	}

	public double getObjHeightTolerance() {
		return objHeightTolerance;
	}

	public void setObjHeightTolerance(double objHeightTolerance) {
		this.objHeightTolerance = objHeightTolerance;
	}

	public int getObjHeightAllowedDifference() {
		return objHeightAllowedDifference;
	}

	public void setObjHeightAllowedDifference(int objHeightAllowedDifference) {
		this.objHeightAllowedDifference = objHeightAllowedDifference;
	}

	public boolean isGenerateHighwayLights() {
		return generateHighwayLights;
	}

	public void setGenerateHighwayLights(boolean generateHighwayLights) {
		this.generateHighwayLights = generateHighwayLights;
	}

	public String getLightObjectString() {
		return lightObjectString;
	}

	public void setLightObjectString(String lightObjectString) {
		this.lightObjectString = lightObjectString;
	}

	public int getStreetLightsInterval() {
		return streetLightsInterval;
	}

	public void setStreetLightsInterval(int streetLightsInterval) {
		this.streetLightsInterval = streetLightsInterval;
	}

}
