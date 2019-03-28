package com.osm2xp.utils.helpers;

import com.google.common.collect.Lists;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.generation.options.ObjectFile;
import com.osm2xp.generation.options.ObjectTagRule;

/**
 * OptionsHelper.
 * 
 * @author Benjamin Blanchet
 * 
 */
public abstract class OptionsHelper {

	/**
	 * @return
	 */
	public static ObjectTagRule createObjectTagRule(final String objectMessage) {
		return new ObjectTagRule(new Tag("a tag key", "a tag value"),
				Lists.newArrayList(new ObjectFile(objectMessage)), 0, true);
	}
}
