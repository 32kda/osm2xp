package com.osm2xp.xplane.customrules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.osm2xp.core.model.osm.CompositeTagSet;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.utils.NameUtils;

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
	void testRailway00() {
		PathRulesList pathRulesList = new PathRulesList();
		pathRulesList.addRule(new PathRule("Railway (main line)", "item.railway=='rail' && item.usage=='main'", 151));
		pathRulesList
				.addRule(new PathRule("Railway (branch line)", "item.railway=='rail' && item.usage=='branch'", 152));
		pathRulesList
				.addRule(new PathRule("Railway (industrial)", "item.railway=='rail' && item.usage=='industrial'", 154));
		pathRulesList.addRule(new PathRule("Railway (industrial 1)", "item.railway=='rail' && 'service' in item", 154));
		pathRulesList.addRule(new PathRule("Railway (tram)", "item.railway=='tram'", 153));
		pathRulesList.addRule(new PathRule("Railway (disused)", "item.railway=='disused'", 153));
		pathRulesList.addRule(new PathRule("Railway (construction)", "item.railway=='construction'", 153));
		pathRulesList.addRule(new PathRule("Railway (other)", "item.railway=='rail'", 152));

		RulesScriptEngine rulesScriptEngine = new RulesScriptEngine(pathRulesList.getRules());

		List<Tag> tags = new ArrayList<Tag>();
		tags.add(new Tag("railway", "construction"));
		tags.add(new Tag("usage", "main"));
		tags.add(new Tag("construction", 	"rail"));
		tags.add(new Tag(NameUtils.toIdentifier("construction:railway"), "rail"));
		int result = rulesScriptEngine.evaluateResult(new CompositeTagSet(tags));
		assertEquals(153, result);

		List<Tag> tags1 = new ArrayList<Tag>();
		tags1.add(new Tag(NameUtils.toIdentifier("disused:railway"), "rail"));
		tags1.add(new Tag("railway", "disused"));
		result = rulesScriptEngine.evaluateResult(new CompositeTagSet(tags1));
		assertEquals(153, result);
		
		List<Tag> tagsTram = new ArrayList<Tag>();
		tagsTram.add(new Tag("railway", "tram"));
		result = rulesScriptEngine.evaluateResult(new CompositeTagSet(tagsTram));
		assertEquals(153, result);
	}

}
