package com.osm2xp.core.parsers.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.openstreetmap.osmosis.osmbinary.BinaryParser;
import org.openstreetmap.osmosis.osmbinary.Osmformat;
import org.openstreetmap.osmosis.osmbinary.Osmformat.DenseNodes;
import org.openstreetmap.osmosis.osmbinary.Osmformat.HeaderBBox;
import org.openstreetmap.osmosis.osmbinary.Osmformat.HeaderBlock;
import org.openstreetmap.osmosis.osmbinary.Osmformat.Node;
import org.openstreetmap.osmosis.osmbinary.Osmformat.Relation;
import org.openstreetmap.osmosis.osmbinary.file.BlockInputStream;

import com.osm2xp.core.exceptions.OsmParsingException;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Member;
import com.osm2xp.core.model.osm.Nd;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.core.parsers.IOSMDataVisitor;
import com.osm2xp.core.parsers.IParser;
import com.osm2xp.core.parsers.IVisitingParser;

import math.geom2d.Box2D;

public class TranslatingBinaryParser extends BinaryParser implements IParser, IVisitingParser {

	/**
	 * Factor to get bounding box lat/long - PBF contains value in nanodegrees
	 */
	protected static final double COORD_DIV_FACTOR = 1000000000;
	
	protected IOSMDataVisitor osmDataVisitor;
	private File binaryFile;
	
	public TranslatingBinaryParser(File binaryFile,IOSMDataVisitor osmDataVisitor) {
		this.binaryFile = binaryFile;
		this.osmDataVisitor = osmDataVisitor;
	}

	@Override
	protected void parseNodes(List<Node> nodes) {
	}
	
	public void process() {
		try {
			InputStream input;
			input = new FileInputStream(this.binaryFile);
			BlockInputStream bm = new BlockInputStream(input, this);
			bm.process();
		} catch (FileNotFoundException e1) {
			Osm2xpLogger.log(new OsmParsingException("Error loading file "
					+ binaryFile.getPath(), e1));
		} catch (IOException e) {
			Osm2xpLogger.log(new OsmParsingException(e));
		}
	}

	protected com.osm2xp.core.model.osm.Way createWayFromParsed(Osmformat.Way curWay) {
		List<Tag> listedTags = new ArrayList<Tag>();
		for (int j = 0; j < curWay.getKeysCount(); j++) {
			Tag tag = new Tag();
			tag.setKey(getStringById(curWay.getKeys(j)));
			tag.setValue(getStringById(curWay.getVals(j)));
			listedTags.add(tag);
		}
	
		long lastId = 0;
		List<Nd> listedLocalisationsRef = new ArrayList<Nd>();
		for (long j : curWay.getRefsList()) {
			Nd nd = new Nd();
			nd.setRef(j + lastId);
			listedLocalisationsRef.add(nd);
			lastId = j + lastId;
		}
	
		com.osm2xp.core.model.osm.Way way = new com.osm2xp.core.model.osm.Way();
		way.getTags().addAll(listedTags);
		way.setId(curWay.getId());
		way.getNd().addAll(listedLocalisationsRef);
		return way;
	}
	
	@Override
	protected void parse(HeaderBlock header) {
		// The header bounding box is optional; when absent getBbox() returns the
		// all-zero default, which must not be reported as a real extent.
		if (!header.hasBbox()) {
			return;
		}
		HeaderBBox bbox = header.getBbox();
		osmDataVisitor.visit(new Box2D(bbox.getLeft() / COORD_DIV_FACTOR, bbox.getRight() / COORD_DIV_FACTOR, bbox.getBottom() / COORD_DIV_FACTOR, bbox.getTop() / COORD_DIV_FACTOR));  
	}

	
	@Override
	protected void parseWays(List<Osmformat.Way> ways) {
		for (Osmformat.Way curWay : ways) {
			osmDataVisitor.visit(createWayFromParsed(curWay));
		}
	}
	
	@Override
	protected void parseDense(DenseNodes nodes) {
		// parse nodes only if we're not on a single pass mode, or if the nodes
		// collection of single pass mode is done

		long lastId = 0, lastLat = 0, lastLon = 0;
		int j = 0;
		for (int i = 0; i < nodes.getIdCount(); i++) {
			List<Tag> tags = new ArrayList<Tag>();
			long lat = nodes.getLat(i) + lastLat;
			lastLat = lat;
			long lon = nodes.getLon(i) + lastLon;
			lastLon = lon;
			long id = nodes.getId(i) + lastId;
			lastId = id;
			double latf = parseLat(lat), lonf = parseLon(lon);
			if (nodes.getKeysValsCount() > 0) {
				while (nodes.getKeysVals(j) != 0) {
					int keyId = nodes.getKeysVals(j++);
					int valId = nodes.getKeysVals(j++);
					Tag tag = new Tag();
					tag.setKey(getStringById(keyId));
					tag.setValue(getStringById(valId));
					tags.add(tag);
				}
				j++;
			}
			com.osm2xp.core.model.osm.Node node = new com.osm2xp.core.model.osm.Node();
			node.setId(id);
			node.setLat(latf);
			node.setLon(lonf);
			node.getTags().addAll(tags);
			osmDataVisitor.visit(node);
		}
	}

	@Override
	protected void parseRelations(List<Relation> rels) {
		for (Relation pbfRelation : rels) {	
			Map<String, String> tags = new HashMap<String, String>();
			for (int j = 0; j < pbfRelation.getKeysCount(); j++) {
				tags.put(getStringById(pbfRelation.getKeys(j)), getStringById(pbfRelation.getVals(j)));
			}
			long lastMemberId = 0;
			List<Tag> tagsModel = tags.keySet().stream().map(key -> new Tag(key, tags.get(key)))
					.collect(Collectors.toList());
			com.osm2xp.core.model.osm.Relation innerRelation = new com.osm2xp.core.model.osm.Relation();
			innerRelation.setTags(tagsModel);
			innerRelation.setId(pbfRelation.getId());
			for (int i = 0; i < pbfRelation.getMemidsList().size(); i++) {
				long memberId = lastMemberId + pbfRelation.getMemids(i);
				lastMemberId = memberId;
				Integer rolesSid = pbfRelation.getRolesSidList().get(i);
				String type = pbfRelation.getTypesList().get(i).toString();
				String role = getStringById(rolesSid);
				String ref = pbfRelation.getMemidsList().get(i).toString();
				innerRelation.getMember().add(new Member(memberId, type, ref, role));
			}
			 
			processRelation(innerRelation);
		}
	}
	
	@Override
	public void complete() {
		osmDataVisitor.complete();
	}

	protected void processRelation(com.osm2xp.core.model.osm.Relation relation) {
		osmDataVisitor.visit(relation);
	}

	@Override
	public IOSMDataVisitor getVisitor() {
		return osmDataVisitor;
	}

}