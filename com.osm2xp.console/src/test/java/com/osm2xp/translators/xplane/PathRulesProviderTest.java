package com.osm2xp.translators.xplane;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.osm2xp.xplane.customrules.PathRule;
import com.osm2xp.xplane.customrules.PathRulesList;
import com.osm2xp.xplane.customrules.PathRulesProvider;
import com.osm2xp.xplane.customrules.PathRulesProvider.PathOptionsType;

class PathRulesProviderTest {

	@Test
	void test() {
		PathRulesList pathRulesList = new PathRulesList();
		pathRulesList.addRule(new PathRule("sample", "highway == 'primary' && lit == 'yes'", 100));
		pathRulesList.addRule(new PathRule("sample 1", "highway == 'secondary' && lit == 'yes'", 101));
		PathRulesProvider.saveOptions(PathOptionsType.ROADS_CITY, pathRulesList);
		PathRulesList loaded = PathRulesProvider.getRulesList(PathOptionsType.ROADS_CITY);
		assertEquals(pathRulesList.getRules().size(), loaded.getRules().size());
	}

}
