package com.osm2xp.translators;

import java.util.Collection;

import math.geom2d.Point2D;

public interface ITranslatorProvider {

	public Collection<ISpecificTranslator> createAdditinalAdapters();
	
	public ITranslator getTranslator(Point2D currentTile);

	/**
	 * Releases any run-scoped resources held by this provider. Called once, after
	 * all translators have completed. The default implementation does nothing.
	 */
	default void close() {
	}

}
