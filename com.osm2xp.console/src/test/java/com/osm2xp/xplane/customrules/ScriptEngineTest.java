package com.osm2xp.xplane.customrules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.osm2xp.core.model.osm.CompositeTagSet;
import com.osm2xp.core.model.osm.Tag;

class ScriptEngineTest {

	@Test
	void test() {
		PathRulesList pathRulesList = new PathRulesList();
		pathRulesList.addRule(new PathRule("sample", "item.highway == 'primary' && item.lit == 'yes'", 100));
		pathRulesList.addRule(new PathRule("sample 1", "item.highway == 'secondary' && item.lit == 'yes'", 101));
		
		RulesScriptEngine rulesScriptEngine = new RulesScriptEngine(pathRulesList.getRules());
		
		List<Tag> tags = new ArrayList<Tag>();
		tags.add(new Tag("highway", "primary"));
		tags.add(new Tag("lit", "yes"));
		int result = rulesScriptEngine.evaluateResult(new CompositeTagSet(tags));
		assertEquals(100, result);
	}

}
