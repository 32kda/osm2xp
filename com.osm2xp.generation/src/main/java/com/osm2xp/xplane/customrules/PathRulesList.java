package com.osm2xp.xplane.customrules;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = { "rules" })
public class PathRulesList {
	@XmlElement(name="rule", required = true)
	protected List<PathRule> rules;
	
	public List<PathRule> getRules() {
		if (rules == null) {
			rules = new ArrayList<PathRule>();
		}
		return this.rules;
	}
	
	public void addRule(PathRule pathRule) {
		List<PathRule> rulesList = getRules();
		rulesList.add(pathRule);
	}
	
	public boolean removeRule(PathRule pathRule) {
		List<PathRule> rulesList = getRules();
		return rulesList.remove(pathRule);
	}

}
