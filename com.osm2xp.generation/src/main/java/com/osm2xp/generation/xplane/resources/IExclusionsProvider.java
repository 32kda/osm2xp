package com.osm2xp.generation.xplane.resources;


import math.geom2d.polygon.Polygon2D;

public interface IExclusionsProvider {

	boolean isExcludeObj();
	boolean isExcludeFac();
	boolean isExcludeFor();
	boolean isExcludeNet();
	boolean isExcludeLin();
	boolean isExcludePol();
	boolean isExcludeStr();
	boolean isExcludeBch();

	boolean objectsGenerated();
	boolean facadesGenerated();
	boolean forestsGenerated();
	boolean networkGenerated();
	boolean polysGenerated();

	default Polygon2D getExclusionPolygon() {
		return null;
	}

}
