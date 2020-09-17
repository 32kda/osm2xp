package com.osm2xp.xplane.customrules;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "")
@XmlRootElement(name = "rule")
public class PathRule {
	
	@XmlAttribute
	public final String name;
	@XmlAttribute
	public final String condition;
	@XmlAttribute
	public final String resultValue;
	
	public PathRule(String name, String condition, String resultValue) {
		super();
		this.name = name;
		this.condition = condition;
		this.resultValue = resultValue;
	}

	@Override
	public String toString() {
		return "PathRule [name=" + name + ", condition=" + condition + ", resultValue=" + resultValue + "]";
	}	
	
	
	

}
