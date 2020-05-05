package com.osm2xp.converters.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

import com.osm2xp.core.exceptions.Osm2xpBusinessException;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.datastore.IDataSink;
import com.osm2xp.translators.ISpecificTranslator;
import com.osm2xp.translators.ITranslatorProvider;
import com.osm2xp.utils.osm.OsmUtils;

import math.geom2d.Box2D;

public abstract class AbstractTileDataConverter extends AbstractOSMDataConverter {
	protected List<ISpecificTranslator> translationAdapters = new ArrayList<>();
	protected Box2D boundingBox;
	protected ITranslatorProvider translatorProvider;

	public AbstractTileDataConverter(IDataSink processor) {
		super(processor);
	}

	public void complete() {
		for (ISpecificTranslator tileTranslationAdapter : translationAdapters) {
			tileTranslationAdapter.complete();
		}
		super.complete();
	}

	@Override
	protected boolean mustProcessPolyline(List<Tag> tagsModel) {
		for (ISpecificTranslator adapter : translationAdapters) {
			if (adapter.mustProcessPolyline(tagsModel)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void translatePolys(long id, List<Tag> tagsModel, List<Polygon> cleanedPolys) {
		if (cleanedPolys.isEmpty()) {
			String type = OsmUtils.getReadableType(tagsModel);
			Osm2xpLogger.warning( "Way/Relation of type " + type + " with id " + id + " is invalid, unable to fix it automatically. Possible reasons - self-intersection or partial node information.");
			return;
		}
		for (ISpecificTranslator adapter : translationAdapters) {
			adapter.processWays(id, tagsModel, null, cleanedPolys);
		}
	}

	@Override
	protected void translateWay(com.osm2xp.core.model.osm.Way way, List<Long> ids) throws Osm2xpBusinessException {
		Geometry geometry = getGeometry(ids);
		if (geometry == null) {
			return;
		}
		List<Geometry> fixed = fix(Collections.singletonList(geometry));
		if (fixed.isEmpty()) {
			return;
		} 
		for (ISpecificTranslator adapter : translationAdapters) {
			adapter.processWays(way.getId(), way.getTags(), geometry, fixed);
		}
	}

	@Override
	public void visit(Box2D boundingBox) {
		super.visit(boundingBox);
		this.boundingBox = boundingBox;
		for (ISpecificTranslator tileTranslationAdapter : translationAdapters) {
			tileTranslationAdapter.processBoundingBox(boundingBox);
		}
		
	}
	
	@Override
	public void visit(Node node) {
		// give the node to the translator for processing 
		for (ISpecificTranslator adapter : translationAdapters) {
			try {
				adapter.processNode(node);
			} catch (Exception e) {
				Osm2xpLogger.log(e);
			}
		}
		super.visit(node);
	}

	@Override
	protected boolean mustStoreNode(Node node) {
		if (dataSink.isReadOnly()) {
			return false;
		}
		for (ISpecificTranslator tileTranslationAdapter : translationAdapters) {
			if (tileTranslationAdapter.mustStoreNode(node)) {
				return true;		
			}
		}
		return false;
	}
	
	public abstract int getTilesCount();
}