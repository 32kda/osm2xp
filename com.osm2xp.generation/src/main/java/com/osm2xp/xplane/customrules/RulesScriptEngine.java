package com.osm2xp.xplane.customrules;

import java.util.List;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.utils.NameUtils;

public class RulesScriptEngine {
	
	protected static ScriptEngineManager manager = new ScriptEngineManager();
    protected static ScriptEngine engine = manager.getEngineByName("nashorn");
	protected String mainScript;
	
	public RulesScriptEngine(List<PathRule> pathRules) {
		StringBuilder builder = new StringBuilder("var result = -1;\n");
		for (PathRule pathRule : pathRules) {
			builder.append("if(");
			builder.append(pathRule.condition);
			builder.append(")result=");
			builder.append(pathRule.resultValue);
			builder.append(";\n");
		}
		builder.append("result");
		mainScript = builder.toString();
	}

	public int evaluateResult(IHasTags target) {
		String toEval = createObject(target) + "\n" + mainScript;
		try {
			Object result = engine.eval(toEval);
			if (result instanceof Integer) {
				return (int) result;
			}
		} catch (ScriptException e) {
			Osm2xpLogger.error("Error calculating path condition " + toEval, e);
		}
		return 0;
	}
	
	private String createObject(IHasTags entity) {
		StringBuilder builder = new StringBuilder();
		builder.append("var item = new Object();");
		builder.append("\n");
		List<Tag> tags = entity.getTags();
		for (Tag tag : tags) {
			Object value = tryParse(tag.getValue());
			if (value instanceof Double) {
				Double dbl = (Double) value;				
				builder.append("item.");
				builder.append(NameUtils.toIdentifier(tag.getKey()));
				builder.append(" = ");
				builder.append(dbl);
				builder.append(";\n");
			} else {
				builder.append("item.");
				builder.append(NameUtils.toIdentifier(tag.getKey()));
				builder.append(" = \"");
				builder.append(normalizeStr(value.toString()));
				builder.append("\";\n");
				
			}
			
		}
		return builder.toString();		
	}

	private String normalizeStr(String value) {
		return value.replace('"','\'');
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
