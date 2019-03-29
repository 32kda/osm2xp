package com.osm2xp.generation.parsers.listers;

import java.util.Set;

import com.osm2xp.core.exceptions.Osm2xpBusinessException;

import math.geom2d.Point2D;

/**
 * TilesLister.
 * 
 * @author Benjamin Blanchet
 * 
 */
public interface TilesLister {

	public Set<Point2D> getTilesList();

	public void process() throws Osm2xpBusinessException;
}
