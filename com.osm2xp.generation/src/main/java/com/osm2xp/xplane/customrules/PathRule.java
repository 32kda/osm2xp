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
	protected String name;
	@XmlAttribute
	protected String condition;
	@XmlAttribute
	protected int resultValue;
	
	public PathRule() {
		
	}		
	
	public PathRule(String name, String condition, int resultValue) {
		super();
		this.name = name;
		this.condition = condition;
		this.resultValue = resultValue;
	}

	@Override
	public String toString() {
		return "PathRule [name=" + name + ", condition=" + condition + ", resultValue=" + resultValue + "]";
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCondition() {
		return condition;
	}

	public void setCondition(String condition) {
		this.condition = condition;
	}

	public int getResultValue() {
		return resultValue;
	}

	public void setResultValue(int resultValue) {
		this.resultValue = resultValue;
	}	
	
	
	

}
