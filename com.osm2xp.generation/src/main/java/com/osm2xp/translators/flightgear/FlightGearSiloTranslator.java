package com.osm2xp.translators.flightgear;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.utils.geometry.GeomUtils;

/**
 * Places circular silos and storage tanks (fuel/oil tanks, gasometers) using the shared models
 * shipped with FlightGear, mirroring osm2city's {@code StorageTank} logic
 * ({@code example/osm2city/pylons.py}).
 * <p>
 * The tag determines the model family - a silo ({@code building/man_made = silo}), a gas tank
 * ({@code content = gas}) or an oil/fuel tank - and the polygon diameter (perimeter / &pi;) picks
 * the closest sized model from that family, exactly like the cooling tower translator.
 *
 * @author osm2xp
 */
public class FlightGearSiloTranslator extends FlightGearSpecObjectTranslator {
	
	private static final String SILO_TAG = "silo";
	// ---------------------------------------------------------------------
	// Hardcoded FlightGear shared silo/storage tank models (not bundled with
	// osm2xp), taken from example/osm2city/static_types/shared_models.py.
	// ---------------------------------------------------------------------
	protected static final String SILO_8M = "Models/Agriculture/1silo_8m.ac";
	protected static final String SILO_20M = "Models/Agriculture/1silo_20m.ac";
	protected static final String GAS_TANK_10M = "Models/Industrial/GenericPressureVessel10m.ac";
	protected static final String GAS_TANK_20M = "Models/Industrial/GenericPressureVessel20m.ac";
	protected static final String GAS_TANK_30M = "Models/Industrial/GenericPressureVessel30m.ac";
	protected static final String GAS_TANK_40M = "Models/Industrial/GenericPressureVessel40m.ac";
	protected static final String GASOMETER = "Models/Industrial/Gasometer.ac";
	protected static final String HC_TANK = "Models/Industrial/HC-Tank.ac";
	protected static final String FUEL_TANK_10M = "Models/Industrial/KOFF_Tank_10M.ac";
	protected static final String FUEL_TANK_12M = "Models/Industrial/KOFF_Tank_12M.ac";
	protected static final String FUEL_TANK_13M = "Models/Industrial/KOFF_Tank_13M.ac";
	protected static final String FUEL_TANK_20M = "Models/Industrial/KOFF_Tank_20M.ac";
	protected static final String FUEL_TANK_30M = "Models/Industrial/KOFF_Tank_30M.ac";
	protected static final String FUEL_TANK_65M = "Models/Industrial/65m.ac";
	protected static final String FUEL_TANK_80M = "Models/Industrial/80m.ac";

	private static final int MIN_DIAMETER = 5;

	private static final List<ObjectDef> SILO_DEFS = buildObjectDefs(SILO_8M, SILO_20M);

	private static final List<ObjectDef> GAS_TANK_DEFS = new ArrayList<>();
	private static final List<ObjectDef> FUEL_TANK_DEFS = buildObjectDefs(
			FUEL_TANK_10M, FUEL_TANK_12M, FUEL_TANK_13M, FUEL_TANK_20M, FUEL_TANK_30M, FUEL_TANK_65M, FUEL_TANK_80M);
	
	private static final Set<String> SILO_TYPES = Set.of("storage_tank", "tank", "oil_tank", "fuel_storage_tank","digester", "gasometer"); 

	static {
		GAS_TANK_DEFS.addAll(buildObjectDefs(GAS_TANK_10M, GAS_TANK_20M, GAS_TANK_30M, GAS_TANK_40M));
		// Gasometer and HC-Tank carry no diameter in their name - give them representative sizes.
		GAS_TANK_DEFS.add(objectDef(GASOMETER, 55));
		GAS_TANK_DEFS.add(objectDef(HC_TANK, 80));
	}

	@Override
	protected boolean canProcess(OsmPolygon osmPolygon) {
		return generationEnabled() && (isSilo(osmPolygon) || isStorageTank(osmPolygon));
	}

	@Override
	protected int getObjectSize(OsmPolygon osmPolygon) {
		double length = GeomUtils.computeEdgesLength(osmPolygon.getPolygon());
		int diameter = (int) Math.round(length / Math.PI);
		if (diameter < MIN_DIAMETER) {
			return -1;
		}
		return diameter;
	}

	@Override
	protected List<ObjectDef> getObjectDefs(OsmPolygon osmPolygon) {
		if (isSilo(osmPolygon)) {
			return SILO_DEFS;
		}
		if (isGasTank(osmPolygon)) {
			return GAS_TANK_DEFS;
		}
		return FUEL_TANK_DEFS;
	}
	
	private boolean isGasTank(OsmPolygon osmPolygon) {
		return "gasometer".equalsIgnoreCase(osmPolygon.getTagValue("man_made")) 
				|| "gas".equalsIgnoreCase(osmPolygon.getTagValue("content"));
	}

	private boolean isSilo(OsmPolygon osmPolygon) {
		return SILO_TAG.equalsIgnoreCase(osmPolygon.getTagValue("building"))
				|| SILO_TAG.equalsIgnoreCase(osmPolygon.getTagValue("building:part"))
				|| SILO_TAG.equalsIgnoreCase(osmPolygon.getTagValue("man_made"));
	}

	private boolean isStorageTank(OsmPolygon osmPolygon) {
		return isStorageTankValue(osmPolygon.getTagValue("building"))
				|| isStorageTankValue(osmPolygon.getTagValue("building:part"))
				|| isStorageTankValue(osmPolygon.getTagValue("man_made"));
	}

	private boolean isStorageTankValue(String value) {
		if (value == null) {
			return false;
		}
		return SILO_TYPES.contains(value.toLowerCase());
	}

	@Override
	protected String getObjectFilePreffix() {
		return SILO_TAG;
	}

	@Override
	protected boolean generationEnabled() {
		return FlightGearOptionsProvider.getOptions().isGenerateObjects();
	}

	@Override
	public String getId() {
		return SILO_TAG;
	}

}
