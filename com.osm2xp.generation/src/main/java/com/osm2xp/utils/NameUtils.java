package com.osm2xp.utils;

public class NameUtils {
	
	
	public static String toIdentifier(String tagName) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < tagName.length(); i++) {
			char current = tagName.charAt(i);
			if ((i == 0 && Character.isJavaIdentifierStart(current)) || (i > 0 && Character.isJavaIdentifierPart(current))) {
				builder.append(current);
			} else {
				builder.append('_');
			}
		}
		return builder.toString();
	}

}
