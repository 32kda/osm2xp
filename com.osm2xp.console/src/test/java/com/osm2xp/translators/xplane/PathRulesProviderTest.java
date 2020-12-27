package com.osm2xp.translators.xplane;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.osm2xp.xplane.customrules.PathRule;
import com.osm2xp.xplane.customrules.PathRulesProvider;
import com.osm2xp.xplane.customrules.PathRulesProvider.PathOptionsType;

class PathRulesProviderTest {

	@Test
	void test() {
		List<PathRule> pathRulesList = new ArrayList<>();
		pathRulesList.add(new PathRule("sample", "highway == 'primary' && lit == 'yes'", 100));
		pathRulesList.add(new PathRule("sample 1", "highway == 'secondary' && lit == 'yes'", 101));
		PathRulesProvider.saveOptions(PathOptionsType.ROADS_CITY, pathRulesList);
		List<PathRule> loaded = PathRulesProvider.getRulesList(PathOptionsType.ROADS_CITY);
		assertEquals(pathRulesList.size(), loaded.size());
	}

}
