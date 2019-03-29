package com.osm2xp.generation.parsers.listers;

import java.io.File;

/**
 * TilesListerFactory.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class TilesListerFactory {

	public static TilesLister getTilesLister(File file) {
		TilesLister result = null;
		if (file.getName().endsWith(".pbf")) {
			result = new PbfTilesLister(file);
		} else if (file.getName().endsWith(".osm")) {
			result = new XmlTilesLister(file);
		}
		if (file.getName().endsWith(".shp")) {
			result = new ShapefileTilesLister(file);
		}
		return result;
	}
}
