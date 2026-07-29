package com.osm2xp.translators.flightgear;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.osm2xp.core.model.osm.Tag;

public class BuildingLevelsDistributions {

	private static final Map<Integer, Double> CENTRE = createMap(4, 0.2, 5, 0.7, 6, 0.1);
	private static final Map<Integer, Double> BLOCK = createMap(4, 0.4, 5, 0.6);
	private static final Map<Integer, Double> DENSE = createMap(3, 0.25, 4, 0.75);
	private static final Map<Integer, Double> PERIPHERY = createMap(1, 0.3, 2, 0.65, 3, 0.05);
	private static final Map<Integer, Double> RURAL = createMap(1, 0.3, 2, 0.7);

	private static final Map<Integer, Double> APARTMENTS = createMap(2, 0.05, 3, 0.45, 4, 0.4, 5, 0.08, 6, 0.02);
	private static final Map<Integer, Double> INDUSTRIAL_DIST = createMap(1, 0.3, 2, 0.6, 3, 0.1);
	private static final Map<Integer, Double> OTHER = createMap(1, 0.2, 2, 0.4, 3, 0.3, 4, 0.1);

	private static Map<Integer, Double> createMap(Object... pairs) {
		Map<Integer, Double> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((Integer) pairs[i], (Double) pairs[i + 1]);
		}
		return map;
	}

	public static int randomLevels(SettlementType settlementType, DetailedBuildingClass buildingClass, Random random) {
		Map<Integer, Double> distribution;

		if (settlementType == SettlementType.CENTRE) {
			distribution = CENTRE;
		} else if (settlementType == SettlementType.BLOCK) {
			distribution = BLOCK;
		} else {
			if (buildingClass == DetailedBuildingClass.RESIDENTIAL
					|| buildingClass == DetailedBuildingClass.RESIDENTIAL_SMALL) {
				switch (settlementType) {
					case DENSE:
						distribution = DENSE;
						break;
					case PERIPHERY:
						distribution = PERIPHERY;
						break;
					default:
						distribution = RURAL;
						break;
				}
			} else if (buildingClass == DetailedBuildingClass.APARTMENTS) {
				distribution = APARTMENTS;
			} else if (buildingClass == DetailedBuildingClass.INDUSTRIAL
					|| buildingClass == DetailedBuildingClass.WAREHOUSE) {
				distribution = INDUSTRIAL_DIST;
			} else {
				distribution = OTHER;
			}
		}

		return randomFromDistribution(distribution, random);
	}

	public static int randomLevels(List<Tag> tags, Random random) {
		DetailedBuildingClass buildingClass = DetailedBuildingClass.classify(tags);
		return randomLevels(SettlementType.DEFAULT, buildingClass, random);
	}

	private static int randomFromDistribution(Map<Integer, Double> distribution, Random random) {
		double r = random.nextDouble();
		double cumulative = 0.0;
		for (Map.Entry<Integer, Double> entry : distribution.entrySet()) {
			cumulative += entry.getValue();
			if (r <= cumulative) {
				return entry.getKey();
			}
		}
		return distribution.keySet().iterator().next();
	}
}
