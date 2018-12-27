package com.osm2xp.parsers.impl;

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
import org.openstreetmap.osmosis.osmbinary.Osmformat.HeaderBlock;
import org.openstreetmap.osmosis.osmbinary.Osmformat.Node;
import org.openstreetmap.osmosis.osmbinary.Osmformat.Relation;
import org.openstreetmap.osmosis.osmbinary.file.BlockInputStream;

import com.osm2xp.exceptions.OsmParsingException;
import com.osm2xp.gui.Activator;
import com.osm2xp.model.osm.Member;
import com.osm2xp.model.osm.Nd;
import com.osm2xp.model.osm.Tag;
import com.osm2xp.parsers.IOSMDataVisitor;
import com.osm2xp.parsers.IParser;
import com.osm2xp.parsers.IVisitingParser;
import com.osm2xp.utils.geometry.GeomUtils;
import com.vividsolutions.jts.geom.Geometry;

public class TranslatingBinaryParser extends BinaryParser implements IParser, IVisitingParser {

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
			Activator.log(new OsmParsingException("Error loading file "
					+ binaryFile.getPath(), e1));
		} catch (IOException e) {
			Activator.log(new OsmParsingException(e));
		}
	}

	protected List<Geometry> fix(List<? extends Geometry> geometries) {
		return geometries.stream().map(geom -> GeomUtils.fix(geom)).filter(geom -> geom != null).collect(Collectors.toList());
	}

	protected com.osm2xp.model.osm.Way createWayFromParsed(Osmformat.Way curWay) {
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
	
		com.osm2xp.model.osm.Way way = new com.osm2xp.model.osm.Way();
		way.getTags().addAll(listedTags);
		way.setId(curWay.getId());
		way.getNd().addAll(listedLocalisationsRef);
		return way;
	}
	
	@Override
	protected void parse(HeaderBlock header) {
		osmDataVisitor.visit(header.getBbox());
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
					int keyid = nodes.getKeysVals(j++);
					int valid = nodes.getKeysVals(j++);
					Tag tag = new Tag();
					tag.setKey(getStringById(keyid));
					tag.setValue(getStringById(valid));
					tags.add(tag);
				}
				j++;
			}
			com.osm2xp.model.osm.Node node = new com.osm2xp.model.osm.Node();
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
			com.osm2xp.model.osm.Relation innerRelation = new com.osm2xp.model.osm.Relation();
			innerRelation.setTags(tagsModel);
			innerRelation.setId(pbfRelation.getId());
			for (int i = 0; i < pbfRelation.getMemidsList().size(); i++) {
				long memberId = lastMemberId + pbfRelation.getMemids(i);
				lastMemberId = memberId;
				Integer rolesSid = pbfRelation.getRolesSidList().get(i);
				String type = pbfRelation.getTypesList().get(i).toString();
				String role = "outer";
				if (rolesSid == 18) {
					role = "inner";
				}
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

	protected void processRelation(com.osm2xp.model.osm.Relation relation) {
		osmDataVisitor.visit(relation);
	}

	@Override
	public IOSMDataVisitor getVisitor() {
		return osmDataVisitor;
	}

}