package com.osm2xp.translators.xplane;

import java.util.List;

import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.writers.IWriter;
import com.osm2xp.xplane.customrules.PathRule;
import com.osm2xp.xplane.customrules.RulesScriptEngine;

public abstract class XPRulesPathTranslator extends XPPathTranslator {
	
	private RulesScriptEngine scriptEngine = new RulesScriptEngine();
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
		return "Path(Rules-based)";
	}

	@Override
	protected int getPathType(IHasTags polygon) {
		return scriptEngine.evaluateResult(polygon, pathRules);
	}
	

}
