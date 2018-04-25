package com.osm2xp.writers.impl;

import java.io.File;
import java.io.IOException;

import math.geom2d.Point2D;

import com.osm2xp.utils.FilesUtils;
import com.osm2xp.utils.helpers.FsxOptionsHelper;
import com.osm2xp.utils.logging.Osm2xpLogger;
import com.osm2xp.writers.IWriter;

/**
 * Bgl writer implementation.
 * 
 * @author Benjamin Blanchet
 * 
 */
public class BglWriterImpl implements IWriter {

	private File currentBglFile;
	private String sceneFolder;

	public BglWriterImpl(String sceneFolder) {
		this.sceneFolder = sceneFolder;

	}

	private void compileBgl() {
		try {
			Runtime runtime = Runtime.getRuntime();
			runtime.exec(new String[] {
					FsxOptionsHelper.getOptions().getBglCompPath(),
					currentBglFile.getPath() });
			currentBglFile.deleteOnExit();
		} catch (IOException e) {
			Osm2xpLogger.error("Error on .bgl compilation.", e);
		}
	}

	private void writeBglHeader() {
		String bglHeader = "<FSData version=\"9.0\" "
				+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
				+ "xsi:noNamespaceSchemaLocation=\"bglcomp.xsd\">";
		FilesUtils.writeTextToFile(currentBglFile, bglHeader, true);
	}

	@Override
	public void init(Point2D coordinates) {
		this.currentBglFile = new File(sceneFolder + File.separator + "osm2xp_"
				+ coordinates.y + "_" + coordinates.x + ".xml");
		writeBglHeader();

	}

	@Override
	public void write(Object data) {
		FilesUtils.writeTextToFile(currentBglFile, "\n" + (String) data, true);

	}

	@Override
	public void complete(Object data) {
		FilesUtils.writeTextToFile(currentBglFile, "\n</FSData>", true);
		compileBgl();

	}

	@Override
	public void write(Object data, Point2D coordinates) {
		// TODO Auto-generated method stub

	}

}
