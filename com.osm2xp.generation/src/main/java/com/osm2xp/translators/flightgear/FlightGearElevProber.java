package com.osm2xp.translators.flightgear;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.osm2xp.core.logging.Osm2xpLogger;

/**
 * Probes terrain elevation from the local FlightGear scenery using the fgelev
 * utility, mirroring OSM2City's elev_probe.py.
 * <p>
 * FlightGear 2024.1 (and newer) fgelev is VPB-only: it no longer accepts
 * {@code --tile-lon/--tile-lat}, so one fgelev subprocess is spawned per
 * <em>sub-bucket</em> tile via {@code --use-vpb --tile-file <tile.btg.gz>} and
 * queried over stdin/stdout with {@code <id> <lon> <lat>} lines, receiving
 * {@code <id> <elevation>} replies.
 * <p>
 * The tile for a lon/lat is located with {@link FlightGearBucket} at
 * {@code <sceneryRoot>/Terrain/<band>/<cell>/<bucketIndex>.btg.gz}, the same
 * layout TerraSync maintains on disk. Subprocesses are capped at
 * {@link #MAX_PROCESSES}; the least recently used one is closed first.
 * <p>
 * Elevation is in metres above mean sea level. A building is considered
 * unsuitable (water / no reliable terrain) when {@link #NO_ELEV} is returned.
 */
public class FlightGearElevProber {

	public static final double NO_ELEV = -9999.0;
	private static final String HOLE = "-1000";
	private static final int MAX_SKIPPED_LINES = 20;
	private static final int MAX_PROCESSES = 32;

	private final File fgelevBinary;
	private final File sceneryRoot;
	private final FlightGearElevationIndex elevationIndex;
	private boolean disabled;
	private boolean spawnErrorLogged;
	private int record;

	private static final class ProcessInfo {
		final Process process;
		final BufferedReader reader;
		final BufferedWriter writer;
		long lastUse;

		ProcessInfo(Process process, BufferedReader reader, BufferedWriter writer) {
			this.process = process;
			this.reader = reader;
			this.writer = writer;
			this.lastUse = System.currentTimeMillis();
		}
	}

	private final Map<String, ProcessInfo> processes = new HashMap<>();

	public FlightGearElevProber(File fgelevBinary, String sceneryPath) {
		this(fgelevBinary, sceneryPath, null);
	}

	/**
	 * Constructs a prober that optionally uses an in-process
	 * {@link FlightGearElevationIndex} over the given {@code Terrain} directory
	 * instead of spawning {@code fgelev}. When {@code terrainRoot} is provided the
	 * index is preferred and {@code fgelevBinary} is ignored.
	 *
	 * @param fgelevBinary path to the {@code fgelev} executable (may be {@code null}
	 *            when an index is used)
	 * @param sceneryPath FlightGear scenery root (ignored when an index is used)
	 * @param terrainRoot the {@code Terrain} directory of the scenery, or
	 *            {@code null} to fall back to {@code fgelev}
	 */
	public FlightGearElevProber(File fgelevBinary, String sceneryPath, File terrainRoot) {
		this.fgelevBinary = fgelevBinary;
		this.sceneryRoot = sceneryPath == null ? null : new File(sceneryPath);
		this.elevationIndex = terrainRoot == null ? null : new FlightGearElevationIndex(terrainRoot);
		this.disabled = elevationIndex != null
				? elevationIndex.isDisabled()
				: fgelevBinary == null || !fgelevBinary.isFile()
						|| sceneryRoot == null || !sceneryRoot.isDirectory();
	}

	public boolean isDisabled() {
		return disabled;
	}

	/**
	 * Returns the ground elevation (MSL metres) at the given global lon/lat, or
	 * {@link #NO_ELEV} if the point is in water, on a hole, or no terrain tile
	 * exists there. Returns 0.0 when probing is disabled.
	 */
	public double probe(double lon, double lat) {
		if (disabled) {
			return 0.0;
		}
		if (elevationIndex != null) {
			double elevation = elevationIndex.probe(lon, lat);
			return Double.isNaN(elevation) ? NO_ELEV : elevation;
		}
		File tileFile = tileFileFor(lon, lat);
		if (tileFile == null || !tileFile.isFile()) {
			return NO_ELEV;
		}
		String tileKey = tileFile.getAbsolutePath();
		try {
			ProcessInfo info = ensureProcess(tileKey, tileFile);
			if (disabled) {
				return 0.0;
			}
			info.lastUse = System.currentTimeMillis();
			long id = record++;
			String query = String.format(Locale.ROOT, "%d %.10f %.10f\r\n", id, lon, lat);
			info.writer.write(query);
			info.writer.flush();
			int skipped = 0;
			String line;
			while ((line = info.reader.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				if (line.startsWith("Now checking") || line.startsWith("osg::Registry::addImageProcessor")
						|| line.startsWith("Loaded plug-in")) {
					if (++skipped > MAX_SKIPPED_LINES) {
						return NO_ELEV;
					}
					continue;
				}
				String[] parts = line.trim().split("\\s+");
				if (parts.length < 1 || !parts[0].equals(id + ":")) {
					continue;
				}
				if (parts.length < 2) {
					return NO_ELEV;
				}
				if (HOLE.equals(parts[1])) {
					return NO_ELEV;
				}
				try {
					return Double.parseDouble(parts[1]);
				} catch (NumberFormatException e) {
					return NO_ELEV;
				}
			}
		} catch (IOException e) {
			logSpawnError(e);
			disable();
		}
		return NO_ELEV;
	}

	/**
	 * Resolves the terrain tile (BTG) covering the given coordinate, using the
	 * same sub-bucket math as SimGear's {@code SGBucket} and the TerraSync on-disk
	 * layout {@code <root>/Terrain/<band>/<cell>/<bucketIndex>.btg.gz}.
	 */
	private File tileFileFor(double lon, double lat) {
		FlightGearBucket bucket = FlightGearBucket.bucketFor(lon, lat);
		String cellPath = bucket.genBasePath().replace('/', File.separatorChar);
		return new File(sceneryRoot,
				new File(new File("Terrain", cellPath), bucket.getIndex() + ".btg.gz").getPath());
	}

	private ProcessInfo ensureProcess(String tileKey, File tileFile) throws IOException {
		ProcessInfo info = processes.get(tileKey);
		if (info != null) {
			return info;
		}
		if (processes.size() >= MAX_PROCESSES) {
			evictLeastRecentlyUsed();
		}
		ProcessBuilder builder = new ProcessBuilder(
				fgelevBinary.getAbsolutePath(),
				"--use-vpb",
				"--tile-file", tileFile.getAbsolutePath());
		Process process = builder.start();
		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
		drainStderr(process);
		info = new ProcessInfo(process, reader, writer);
		processes.put(tileKey, info);
		return info;
	}

	private void evictLeastRecentlyUsed() {
		String oldestKey = null;
		long oldest = Long.MAX_VALUE;
		for (Map.Entry<String, ProcessInfo> entry : processes.entrySet()) {
			if (entry.getValue().lastUse < oldest) {
				oldest = entry.getValue().lastUse;
				oldestKey = entry.getKey();
			}
		}
		if (oldestKey != null) {
			ProcessInfo info = processes.remove(oldestKey);
			closeInfo(info);
		}
	}

	private void drainStderr(final Process process) {
		Thread thread = new Thread(() -> {
			try (BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				while (err.readLine() != null) {
					// drain to avoid blocking fgelev on a full stderr pipe
				}
			} catch (IOException e) {
				// process already gone
			}
		}, "fgelev-stderr");
		thread.setDaemon(true);
		thread.start();
	}

	private void logSpawnError(IOException e) {
		if (!spawnErrorLogged) {
			spawnErrorLogged = true;
			Osm2xpLogger.error("Error probing FlightGear elevation with fgelev (" + fgelevBinary.getAbsolutePath()
					+ "): " + e.getMessage());
		}
	}

	private void disable() {
		disabled = true;
		close();
	}

	private void closeInfo(ProcessInfo info) {
		if (info.writer != null) {
			try {
				info.writer.close();
			} catch (IOException e) {
				// ignore
			}
		}
		info.process.destroy();
	}

	public void close() {
		for (ProcessInfo info : processes.values()) {
			closeInfo(info);
		}
		processes.clear();
	}
}
