package com.osm2xp.translators.xplane;

import java.util.List;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.writers.IWriter;
import com.osm2xp.xplane.customrules.PathRule;

public abstract class XPRulesPathTranslator extends XPPathTranslator {
	
	protected ScriptEngineManager manager = new ScriptEngineManager();
    protected ScriptEngine engine = manager.getEngineByName("nashorn");
	private List<PathRule> pathRules;

	public XPRulesPathTranslator(IWriter writer, XPOutputFormat outputFormat, IDRenumbererService idProvider, List<PathRule> pathRules) {
		super(writer, outputFormat, idProvider);
		this.pathRules = pathRules;
	}

	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected int getPathType(IHasTags polygon) {
		Bindings bindings = createBindings(polygon);
//		Object bindingsResult = engine.eval()
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
