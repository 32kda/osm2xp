package com.osm2xp.xplane.customrules;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.utils.NameUtils;
import jdk.nashorn.api.scripting.JSObject;

@SuppressWarnings("removal")
public class RulesScriptEngine {
		
	
	private static final int MAX_CACHED = 5000;

	protected LinkedHashMap<String, Integer> cache = new LinkedHashMap<String, Integer>() {
		
		private static final long serialVersionUID = 1898599717337876654L;

		protected boolean removeEldestEntry(java.util.Map.Entry<String, Integer> eldest) {
			return super.size() > MAX_CACHED;
		};
		
	};
	
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
//		String obj = createObject(target);
//		Integer cached = cache.get(obj);
//		if (cached != null) {
//			return cached;
//		}
//		String toEval = obj + "\n" + mainScript;
		engine.put("item", createObject(target));
		try {
//			Object result = engine.eval(toEval);
			Object result = engine.eval(mainScript);
			if (result instanceof Integer) {
//				cache.put(obj, (Integer) result);
				return (int) result;
			}
		} catch (ScriptException e) {
			Osm2xpLogger.error("Error calculating path condition " + mainScript, e);
		}
		return 0;
	}
	
//	private String createObject(IHasTags entity) {
//		StringBuilder builder = new StringBuilder();
//		builder.append("var item = new Object();");
//		builder.append("\n");
//		List<Tag> tags = entity.getTags();
//		for (Tag tag : tags) {
//			Object value = tryParse(tag.getValue());
//			if (value instanceof Double) {
//				Double dbl = (Double) value;				
//				builder.append("item.");
//				builder.append(NameUtils.toIdentifier(tag.getKey()));
//				builder.append(" = ");
//				builder.append(dbl);
//				builder.append(";\n");
//			} else {
//				builder.append("item.");
//				builder.append(NameUtils.toIdentifier(tag.getKey()));
//				builder.append(" = \"");
//				builder.append(normalizeStr(value.toString()));
//				builder.append("\";\n");
//				
//			}
//			
//		}
//		return builder.toString();		
//	}
	
	private JSObject createObject(IHasTags entity) {
		Map<String, String> props = new HashMap<>();
		List<Tag> tags = entity.getTags();
		for (Tag tag : tags) {
			props.put(tag.getKey(), tag.getValue());
		}
		return new JSMapObject(props);
	}
	
	private Map<String, String> createObjectMap(IHasTags entity) {
		Map<String, String> result = new HashMap<>();
		List<Tag> tags = entity.getTags();
		for (Tag tag : tags) {
				result.put(NameUtils.toIdentifier(tag.getKey()), normalizeStr(tag.getValue()));
		}
		return result;		
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
