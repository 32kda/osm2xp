package com.osm2xp.generation.options;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.osm2xp.core.exceptions.Osm2xpBusinessException;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.generation.options.rules.FlightGearObjectTagRule;
import com.osm2xp.generation.options.rules.FlightGearObjectsRulesList;
import com.osm2xp.generation.options.rules.ObjectsRulesList;
import com.osm2xp.generation.osm.OsmConstants;
import com.osm2xp.generation.paths.PathsService;

public class FlightGearOptionsProvider {
	
	private static final File FLIGHTGEAR_OPTIONS_FILE = new File(PathsService.getPathsProvider().getBasicFolder(),"flightgear/FlightGearOptions.xml");

	private static FlightGearOptions options;

	public static FlightGearOptions getOptions() {
		if (options == null) {
			if (FLIGHTGEAR_OPTIONS_FILE.isFile()) {
				try {
					options = (FlightGearOptions) XmlHelper.loadFileFromXml(
							FLIGHTGEAR_OPTIONS_FILE,
							FlightGearOptions.class);
				} catch (Osm2xpBusinessException e) {
					Osm2xpLogger.error(
							"Error initializing FlightGear options helper", e);
				}
			} 
			if (options == null) {
				options = createNewFlightGearOptionsBean();
			}
		}
		return options;
	}

	public static void setOptions(FlightGearOptions options) {
		FlightGearOptionsProvider.options = options;
	}

	public static void importObjectsRules(File file) {
		try {
			Object result = XmlHelper.loadFileFromXml(file,
					ObjectsRulesList.class);
			getOptions().setObjectsRules((FlightGearObjectsRulesList) result);
		} catch (Osm2xpBusinessException e) {
			Osm2xpLogger.error("Error importing objects rules file", e);
		}
		
	}
	

	/**
	 * @return
	 */
	private static FlightGearOptions createNewFlightGearOptionsBean() {
		FlightGearOptions result = new FlightGearOptions();
		result.setObjectsRules(createNewObjectsRules());

		return result;
	}

	/**
	 * @return
	 */
	@SuppressWarnings("serial")
	private static FlightGearObjectsRulesList createNewObjectsRules() {
		List<FlightGearObjectTagRule> FlightGearObjectTagRules = new ArrayList<FlightGearObjectTagRule>();
		FlightGearObjectTagRules.add(new FlightGearObjectTagRule(new Tag(
				OsmConstants.MAN_MADE_TAG, "lighthouse"), new ArrayList<ObjectFile>() {
			{
				add(new ObjectFile("Models/objects/capemay.ac"));
			}
		}, 0, true, false, false, 0, 0, 0, 0, false, 0, 0, false, false));
		FlightGearObjectTagRules.add(new FlightGearObjectTagRule(new Tag(
				OsmConstants.MAN_MADE_TAG, "water_tower"), new ArrayList<ObjectFile>() {
			{
				add(new ObjectFile("Models/objects/watertower-3.ac"));
			}
		}, 0, true, false, false, 0, 0, 0, 0, false, 0, 0, false, false));
		FlightGearObjectTagRules.add(new FlightGearObjectTagRule(new Tag(
				"generator:source", "wind"), new ArrayList<ObjectFile>() {
			{
				add(new ObjectFile("Models/objects/wind_turbine.ac"));
			}
		}, 0, true, false, false, 0, 0, 0, 0, false, 0, 0, false, false));
		FlightGearObjectTagRules.add(new FlightGearObjectTagRule(new Tag(
				"generator:method", "wind_turbine"), new ArrayList<ObjectFile>() {
			{
				add(new ObjectFile("Models/objects/wind_turbine.ac"));
			}
		}, 0, true, false, false, 0, 0, 0, 0, false, 0, 0, false, false));
		FlightGearObjectTagRules.add(new FlightGearObjectTagRule(new Tag(
				OsmConstants.MAN_MADE_TAG, "crane"), new ArrayList<ObjectFile>() {
			{
				add(new ObjectFile("Models/objects/crane.ac"));
			}
		}, 0, true, false, false, 0, 0, 0, 0, false, 0, 0, false, false));

		FlightGearObjectsRulesList result = new FlightGearObjectsRulesList(
				FlightGearObjectTagRules);
		return result;
	}

	/**
	 * @throws Osm2xpBusinessException
	 */
	public static void saveOptions() throws Osm2xpBusinessException {
		XmlHelper.saveToXml(getOptions(),FLIGHTGEAR_OPTIONS_FILE);

	}

}
