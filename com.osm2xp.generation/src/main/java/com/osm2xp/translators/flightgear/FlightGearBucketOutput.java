package com.osm2xp.translators.flightgear;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import com.osm2xp.core.logging.Osm2xpLogger;

/**
 * Output files for one FlightGear sub-bucket tile: the STG file
 * <code>Objects/&lt;bucket_path&gt;/&lt;index&gt;.stg</code> and, optionally,
 * the compressed building list next to it. Files are opened lazily and only if
 * the tile actually receives content.
 *
 * @author osm2xp
 */
public class FlightGearBucketOutput {

	private static final String OBJECTS_FOLDER_NAME = "Objects";

	private final FlightGearBucket bucket;
	private final File folder;
	private final File stgFile;
	private final boolean generateBuildings;
	private final String buildingListFileName;
	private final Map<String, BufferedWriter> lineFeatureListWriters = new HashMap<>();
	private final Map<String, String> lineFeatureListFileNames = new HashMap<>();
	private final Set<String> lineFeatureListHeadersWritten = new HashSet<>();
	private BufferedWriter stgWriter;
	private BufferedWriter buildingListWriter;
	private boolean buildingListHeaderWritten;
	private boolean closed;

	public FlightGearBucketOutput(File sceneryRoot, FlightGearBucket bucket, boolean generateBuildings) {
		this.bucket = bucket;
		this.generateBuildings = generateBuildings;
		this.buildingListFileName = "BuildingList_" + bucket.getIndex() + ".txt.gz";
		this.folder = new File(new File(sceneryRoot, OBJECTS_FOLDER_NAME), bucket.genBasePath());
		folder.mkdirs();
		this.stgFile = new File(folder, bucket.getIndex() + ".stg");
	}

	public FlightGearBucket getBucket() {
		return bucket;
	}

	public File getFolder() {
		return folder;
	}

	public File getStgFile() {
		return stgFile;
	}

	public String getBuildingListFileName() {
		return buildingListFileName;
	}

	public BufferedWriter getStgWriter() {
		if (stgWriter == null) {
			try {
				stgWriter = new BufferedWriter(
						new OutputStreamWriter(new FileOutputStream(stgFile), StandardCharsets.UTF_8));
			} catch (IOException e) {
				Osm2xpLogger.error("Error opening STG file " + stgFile, e);
			}
		}
		return stgWriter;
	}

	public BufferedWriter getBuildingListWriter() {
		if (!generateBuildings) {
			return null;
		}
		if (buildingListWriter == null) {
			try {
				GZIPOutputStream gzipOut = new GZIPOutputStream(
						new FileOutputStream(new File(folder, buildingListFileName)));
				buildingListWriter = new BufferedWriter(new OutputStreamWriter(gzipOut, StandardCharsets.UTF_8));
			} catch (IOException e) {
				Osm2xpLogger.error("Error opening building list file in " + folder, e);
			}
		}
		return buildingListWriter;
	}

	public boolean isBuildingListHeaderWritten() {
		return buildingListHeaderWritten;
	}

	public void setBuildingListHeaderWritten(boolean buildingListHeaderWritten) {
		this.buildingListHeaderWritten = buildingListHeaderWritten;
	}

	/**
	 * Deterministic file name (relative to the bucket folder) of the
	 * <code>LINE_FEATURE_LIST</code> list file for the given FlightGear material,
	 * e.g. <code>LineFeatureList_ws30Road_4268928.txt.gz</code>.
	 */
	public String getLineFeatureListFileName(String material) {
		return lineFeatureListFileNames.computeIfAbsent(material,
				m -> "LineFeatureList_" + m + "_" + bucket.getIndex() + ".txt.gz");
	}

	/**
	 * Lazily opens (and caches) the gzipped list file for the given FlightGear
	 * material. The returned writer is closed by {@link #close()}.
	 */
	public BufferedWriter getLineFeatureListWriter(String material) {
		return lineFeatureListWriters.computeIfAbsent(material, m -> {
			try {
				GZIPOutputStream gzipOut = new GZIPOutputStream(
						new FileOutputStream(new File(folder, getLineFeatureListFileName(m))));
				return new BufferedWriter(new OutputStreamWriter(gzipOut, StandardCharsets.UTF_8));
			} catch (IOException e) {
				Osm2xpLogger.error("Error opening line feature list file in " + folder, e);
				return null;
			}
		});
	}

	public boolean isLineFeatureListHeaderWritten(String material) {
		return lineFeatureListHeadersWritten.contains(material);
	}

	public void setLineFeatureListHeaderWritten(String material) {
		lineFeatureListHeadersWritten.add(material);
	}

	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		if (stgWriter != null) {
			try {
				stgWriter.close();
			} catch (IOException e) {
				Osm2xpLogger.error("Error closing STG file " + stgFile, e);
			}
		}
		if (buildingListWriter != null) {
			try {
				buildingListWriter.close();
			} catch (IOException e) {
				Osm2xpLogger.error("Error closing building list file", e);
			}
		}
		for (BufferedWriter lineFeatureListWriter : lineFeatureListWriters.values()) {
			try {
				lineFeatureListWriter.close();
			} catch (IOException e) {
				Osm2xpLogger.error("Error closing line feature list file", e);
			}
		}
		lineFeatureListWriters.clear();
	}
}
