package com.osm2xp.parsers.tilesLister;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openstreetmap.osmosis.osmbinary.BinaryParser;
import org.openstreetmap.osmosis.osmbinary.Osmformat.*;
import org.openstreetmap.osmosis.osmbinary.file.BlockInputStream;

import math.geom2d.Point2D;

import com.osm2xp.exceptions.Osm2xpBusinessException;
import com.osm2xp.model.osm.Tag;


/**
 * PbfTilesLister.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class PbfTilesLister extends BinaryParser implements TilesLister {

	private Set<Point2D> tilesList = new HashSet<Point2D>();
	private File file;

	public PbfTilesLister(File file) {
		this.file = file;

	}

	/**
	 * 
	 */
	public void complete() {

	}

	@Override
	protected void parseRelations(List<Relation> rels) {
	}

	@Override
	protected void parseDense(DenseNodes nodes) {
		long lastId = 0, lastLat = 0, lastLon = 0;

		int j = 0; // Index into the keysvals array.

		// Stuff for dense info
		DenseInfo di = null;
		if (nodes.hasDenseinfo()) {
			di = nodes.getDenseinfo();
		}
		for (int i = 0; i < nodes.getIdCount(); i++) {

			List<Tag> tags = new ArrayList<Tag>(0);
			long lat = nodes.getLat(i) + lastLat;
			lastLat = lat;
			long lon = nodes.getLon(i) + lastLon;
			lastLon = lon;
			long id = nodes.getId(i) + lastId;
			lastId = id;
			double latf = parseLat(lat), lonf = parseLon(lon);
			// If empty, assume that nothing here has keys or vals.
			if (nodes.getKeysValsCount() > 0) {
				while (nodes.getKeysVals(j) != 0) {
					int keyid = nodes.getKeysVals(j++);
					int valid = nodes.getKeysVals(j++);
					Tag Tag = new Tag();
					Tag.setKey(getStringById(keyid));
					Tag.setValue(getStringById(valid));
					tags.add(Tag);
				}
				j++; // Skip over the '0' delimiter.
			}
			// Handle dense info.
//			if (di != null) {
				Point2D loc = new Point2D(lonf, latf);
				int longi = (int) Math.floor(loc.x);
				int lati = (int) Math.floor(loc.y);
				Point2D cleanedLoc = new Point2D(longi, lati);
				tilesList.add(cleanedLoc);

//			}
		}

	}

	@Override
	protected void parseNodes(List<Node> nodes) {
		for (Node node : nodes) {
			System.out.println("PbfTilesLister.parseNodes()");
		}
	}

	@Override
	protected void parseWays(List<Way> ways) {

	}

	@Override
	protected void parse(HeaderBlock header) {
	}

	public void process() throws Osm2xpBusinessException {

		InputStream input = null;
		try {
			input = new FileInputStream(this.file);
		} catch (FileNotFoundException e) {
			throw new Osm2xpBusinessException(e.getMessage());
		}
		BlockInputStream bm = new BlockInputStream(input, this);
		try {
			bm.process();
		} catch (IOException e) {
			throw new Osm2xpBusinessException(e.getMessage());

		}
	}

	public Set<Point2D> getTilesList() {
		return this.tilesList;
	}

}
