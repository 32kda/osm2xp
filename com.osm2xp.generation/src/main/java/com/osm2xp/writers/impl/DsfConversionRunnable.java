package com.osm2xp.writers.impl;

import java.io.File;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.dsf.DsfConversionException;
import com.osm2xp.generation.dsf.DsfConversionResult;
import com.osm2xp.generation.dsf.DsfConverter;

/**
 * Converts a generated DSFTool text file into a binary DSF file using the pure
 * Java converter. Optionally deletes the source text file after conversion.
 */
public class DsfConversionRunnable implements Runnable {

	private File textFile;
	private File dsfFile;
	private boolean deleteSourceFile;

	public DsfConversionRunnable(File textFile, File dsfFile, boolean deleteSourceFile) {
		this.textFile = textFile;
		this.dsfFile = dsfFile;
		this.deleteSourceFile = deleteSourceFile;
	}

	@Override
	public void run() {
		try {
			DsfConversionResult result = DsfConverter.textToBinary(textFile.toPath(), dsfFile.toPath());
			if (result.totalSkipped() > 0) {
				Osm2xpLogger.warning("Skipped " + result.skippedPolygons + " polygon(s), " + result.skippedObjects
						+ " object(s) and " + result.skippedChains + " network chain(s) that exceed DSF limits in "
						+ textFile.getName());
			}
			if (deleteSourceFile) {
				textFile.delete();
			}
		} catch (DsfConversionException e) {
			Osm2xpLogger.error("Error on .dsf conversion for file " + textFile, e);
		}
	}

}
