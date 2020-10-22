package com.osm2xp.xplane.customrules;

import java.util.List;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.core.model.osm.Tag;

public class RulesScriptEngine {
	
	protected ScriptEngineManager manager = new ScriptEngineManager();
    protected ScriptEngine engine = manager.getEngineByName("nashorn");
	
	
	public int evaluateResult(IHasTags target, List<PathRule> pathRules) {
			
			Bindings bindings = createBindings(target);
		for (PathRule pathRule : pathRules) {
			String script = "var result=" + pathRule.getCondition() + ";" +
		"result";
			try {
				engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
				Object bindingsResult = engine.eval(script);
				if (Boolean.TRUE.toString().equalsIgnoreCase(bindingsResult.toString())) {
					return pathRule.getResultValue();
				}
			} catch (ScriptException e) {
				Osm2xpLogger.error("Error clculating path condition " + script, e);
			}
			
		}
		return 0;
	}

	private Bindings createBindings(IHasTags polygon) {
		Bindings bindings = engine.createBindings();
		List<Tag> tags = polygon.getTags();
		for (Tag tag : tags) {
			bindings.put(tag.getKey(), tryParse(tag.getValue()));
		}
		return bindings;
	}
	
	private Object tryParse(String value) {
		try {
			return Double.parseDouble(value);
		} catch (Exception e) {
			// Best effort
		}
		return value;
	}

}
