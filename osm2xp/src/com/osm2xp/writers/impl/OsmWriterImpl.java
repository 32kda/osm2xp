package com.osm2xp.writers.impl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import math.geom2d.Point2D;

import com.osm2xp.constants.Osm2xpConstants;
import com.osm2xp.utils.logging.Osm2xpLogger;
import com.osm2xp.writers.IWriter;

/**
 * Osm Writer implementation.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class OsmWriterImpl implements IWriter {
	private String sceneFolder;
	private BufferedWriter output;

	public OsmWriterImpl(String sceneFolder) {
		this.sceneFolder = sceneFolder;
	}

	@Override
	public void init(Point2D coordinates) {
		try {
			new File(sceneFolder).mkdirs();
			String fileName = sceneFolder + File.separator + coordinates.y
					+ "_" + coordinates.x + ".osm";
			output = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(fileName), "UTF-8"));
			writerHeader();
		} catch (IOException e) {
			Osm2xpLogger.error("Error initializing writer.", e);
		}

	}

	private void writerHeader() {
		write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		write("<osm version=\"0.6\" generator=\"osm2xp "
				+ Osm2xpConstants.OSM2XP_VERSION + "\">\n");
	}

	private void writerFooter() {
		write("</osm>");
	}

	/**
	 * Ecrit une ligne dans le fichier
	 * 
	 * @param string
	 */
	public void write(Object data) {
		if (data != null) {
			try {
				output.write((String) data);
				output.flush();
			} catch (IOException e) {
				Osm2xpLogger.error("Error writing data", e);
			}
		}
	}

	@Override
	public void complete(Object data) {
		writerFooter();
	}

	@Override
	public void write(Object data, Point2D coordinates) {
		// TODO Auto-generated method stub

	}

}
