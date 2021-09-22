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
	
	@Test
	void testOrder() {
		PathRulesList pathRulesList = new PathRulesList();
		pathRulesList.addRule(new PathRule("sample", "item.highway == 'motorway' && item.lanes >= 2", 110));
		pathRulesList.addRule(new PathRule("sample 1", "item.highway == 'motorway'", 100));
		
		RulesScriptEngine rulesScriptEngine = new RulesScriptEngine(pathRulesList.getRules());
		
		List<Tag> tags = new ArrayList<Tag>();
		tags.add(new Tag("highway", "motorway"));
		tags.add(new Tag("lanes", "2"));
		int result = rulesScriptEngine.evaluateResult(new CompositeTagSet(tags));
		assertEquals(110, result);
		
		List<Tag> tags1 = new ArrayList<Tag>();
		tags1.add(new Tag("highway", "primary"));
		tags1.add(new Tag("lanes", "2"));
		result = rulesScriptEngine.evaluateResult(new CompositeTagSet(tags1));
		assertEquals(-1, result);
	}
	
	@Test
	void testInSet() {
		PathRulesList pathRulesList = new PathRulesList();
		pathRulesList.addRule(new PathRule("sample", "['primary','primary_link','secondary','secondary_link'].indexOf(item.highway) > -1 && item.oneway == 'yes'",64));
		pathRulesList.addRule(new PathRule("sample 1", "item.highway == 'motorway'", 100));
		
		RulesScriptEngine rulesScriptEngine = new RulesScriptEngine(pathRulesList.getRules());
		
		List<Tag> tags = new ArrayList<Tag>();
		tags.add(new Tag("highway", "primary"));
		tags.add(new Tag("oneway", "yes"));
		int result = rulesScriptEngine.evaluateResult(new CompositeTagSet(tags));
		assertEquals(64, result);
		
		List<Tag> tags1 = new ArrayList<Tag>();
		tags1.add(new Tag("highway", "pedestrian"));		
		result = rulesScriptEngine.evaluateResult(new CompositeTagSet(tags1));
		assertEquals(-1, result);
	}

}
