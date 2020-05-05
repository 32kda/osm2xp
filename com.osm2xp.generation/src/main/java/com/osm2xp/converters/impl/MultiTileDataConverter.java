package com.osm2xp.converters.impl;

import java.util.Set;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.datastore.IDataSink;
import com.osm2xp.generation.collections.PointSet;
import com.osm2xp.translators.ITranslatorProvider;
import com.osm2xp.translators.impl.TileTranslationAdapter;

import math.geom2d.Point2D;

/**
 * OSM dta converter with ability to automatically detect tiles and split parsed data around several polygons 
 * using JTS library to clip, cut, simplify and fix OSM polygons 
 * 
 * @author Dmitry Karpenko, OnPositive
 * 
 */
public class MultiTileDataConverter extends AbstractTileDataConverter {
	
	private Set<Point2D> tiles = new PointSet();
	public MultiTileDataConverter(IDataSink processor, ITranslatorProvider translatorProvider) {
		super(processor);
		this.translatorProvider = translatorProvider;
		translationAdapters.addAll(translatorProvider.createAdditinalAdapters());
	}
	
	protected void pointParsed(double lonf, double latf) {
		Point2D cleanedLoc = new Point2D((int) Math.floor(lonf), (int) Math.floor(latf));
		if (!tiles.contains(cleanedLoc)) {
			Osm2xpLogger.info("Detected tile (" + cleanedLoc.x() + ", " + cleanedLoc.y() + ")");
			addTranslationAdapter(cleanedLoc);
		}
	}

	protected void addTranslationAdapter(Point2D point) {
		TileTranslationAdapter adapter = new TileTranslationAdapter(point, dataSink, translatorProvider.getTranslator(point));
		adapter.init();
		translationAdapters.add(adapter);
		tiles.add(point);
		if (boundingBox != null) {
			adapter.processBoundingBox(boundingBox);
		}
	}
	
	
	@Override
	public void visit(Node node) {
		pointParsed(node.getLon(), node.getLat());
		super.visit(node);
	}
	
	public int getTilesCount() {
		return tiles.size();
	}

}
