package com.osm2xp.converters.impl;

import java.util.Collections;
import java.util.List;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import com.osm2xp.core.exceptions.Osm2xpBusinessException;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.core.model.osm.Way;
import com.osm2xp.datastore.IDataSink;
import com.osm2xp.translators.ISpecificTranslator;
import com.osm2xp.translators.ITranslatorProvider;
import com.osm2xp.translators.impl.TileTranslationAdapter;

import math.geom2d.Point2D;

public class SingleTileDataConverter extends AbstractTileDataConverter {
	
	private Envelope envelope;

	public SingleTileDataConverter(IDataSink processor, ITranslatorProvider translatorProvider, Point2D tile) {
		super(processor);
		this.translatorProvider = translatorProvider;
		translationAdapters.addAll(translatorProvider.createAdditinalAdapters());
		TileTranslationAdapter adapter = new TileTranslationAdapter(tile, dataSink, translatorProvider.getTranslator(tile));
		adapter.init();
		translationAdapters.add(adapter);
		envelope = new Envelope(tile.x(), tile.x() + 1, tile.y(), tile.y() + 1);
	}
	
	@Override
	protected void translatePolys(long id, List<Tag> tagsModel, List<Polygon> cleanedPolys) {
		boolean hasIntersection = false;
		for (Polygon polygon : cleanedPolys) {
			if (envelope.intersects(polygon.getEnvelopeInternal())) {
				hasIntersection = true;
				break;
			}
		}
		if (hasIntersection) {
			super.translatePolys(id, tagsModel, cleanedPolys);
		}
	}
	
	@Override
	protected void translateWay(Way way, List<Long> ids) throws Osm2xpBusinessException {
		Geometry geometry = getGeometry(ids);
		if (geometry == null) {
			return;
		}
		List<Geometry> fixed = fix(Collections.singletonList(geometry));
		if (fixed.isEmpty()) {
			return;
		}
		boolean hasIntersection = false;
		for (Geometry curGeometry : fixed) {
			if (envelope.intersects(curGeometry.getEnvelopeInternal())) {
				hasIntersection = true;
				break;
			}
			
		}		
		if (hasIntersection) {
			for (ISpecificTranslator adapter : translationAdapters) {
				adapter.processWays(way.getId(), way.getTags(), geometry, fixed);
			} 
		}
	}
	
	@Override
	public int getTilesCount() {
		return 1;
	}
}
