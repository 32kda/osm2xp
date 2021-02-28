package com.osm2xp.xplane.customrules;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import jdk.nashorn.api.scripting.AbstractJSObject;

@SuppressWarnings("removal")
public class JSMapObject extends AbstractJSObject{
	
	private Map<String, String> properties;

	public JSMapObject(Map<String, String> properties) {
		this.properties = properties;
	}
	
	@Override
	public boolean hasMember(String name) {
		return properties.containsKey(name);
	}
	
	@Override
	public Object getMember(String name) {
		return properties.get(name);
	}
	
	@Override
	public void removeMember(String name) {
		properties.remove(name);
	}
	
	@Override
	public void setMember(String name, Object value) {
		properties.put(name, value != null ? value.toString() : null);
	}
	
	@Override
	public Set<String> keySet() {
		return properties.keySet();
	}
	
	@Override
	public Collection<Object> values() {
		return Collections.unmodifiableCollection(properties.values());
	}

}
