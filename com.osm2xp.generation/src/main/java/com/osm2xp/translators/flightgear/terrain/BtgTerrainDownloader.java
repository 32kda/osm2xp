package com.osm2xp.translators.flightgear.terrain;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.osm2xp.core.logging.Osm2xpLogger;

/**
 * Ensures BTG (WS2.0) terrain tiles for a bounding box are available, preferring
 * local sources over the network and downloading each tile at most once:
 * <ol>
 * <li>a tile already in the {@code source_tiles} cache is reused;</li>
 * <li>otherwise it is copied from the user's scenery root
 * ({@code flightGearSceneryPath}) when present;</li>
 * <li>otherwise it is downloaded from the active TerraSync mirror.</li>
 * </ol>
 * Every ensured tile is then copied into the generated scenery's {@code Terrain/}
 * directory (patched or not).
 * <p>
 * The tile manifest for each 1-degree cell comes from its {@code .dirindex} file;
 * when a cell is absent from the mirror the user scenery cell is scanned instead.
 * Both the manifest fetches and the tile work run on the shared
 * {@link AbstractTerrainDownloader} thread pool.
 *
 * @author osm2xp
 */
public class BtgTerrainDownloader extends AbstractTerrainDownloader {

	private static final Pattern DIRINDEX_BTG = Pattern.compile("^f:(\\d+)\\.btg\\.gz:[0-9a-f]+:(\\d+)$");
	private static final String BTG_SUFFIX = ".btg.gz";

	public BtgTerrainDownloader() {
		super();
	}

	public BtgTerrainDownloader(String baseUrl, int concurrency) {
		super(baseUrl, concurrency);
	}

	public BtgTerrainDownloader(List<String> baseUrls, int concurrency) {
		super(baseUrls, concurrency);
	}

	@Override
	public int download(double minLon, double minLat, double maxLon, double maxLat, TerrainPaths paths) {
		File cacheDir = paths == null ? null : paths.getCacheDir();
		if (cacheDir == null) {
			return -1;
		}
		List<Cell> cells = enumerateCells(minLon, minLat, maxLon, maxLat);
		ExecutorService pool = newPool();
		try {
			List<Tile> tiles = collectTiles(cells, paths.getSimTerrainDir(), pool);
			if (tiles.isEmpty()) {
				return 0;
			}

			AtomicInteger downloaded = new AtomicInteger();
			AtomicInteger copied = new AtomicInteger();
			AtomicInteger present = new AtomicInteger();
			List<Callable<String>> tasks = new ArrayList<>();
			for (Tile tile : tiles) {
				tasks.add(() -> ensureTile(tile, paths, downloaded, copied, present));
			}

			int failed = 0;
			for (Future<String> future : pool.invokeAll(tasks)) {
				if ("fail".equals(future.get())) {
					failed++;
				}
			}
			Osm2xpLogger.info("FlightGear terrain: " + downloaded.get() + " downloaded, " + copied.get()
					+ " copied from scenery, " + present.get() + " cached"
					+ (failed > 0 ? ", " + failed + " failed" : ""));
			return failed > 0 ? -1 : tiles.size();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return -1;
		} catch (Exception e) {
			Osm2xpLogger.error("Error preparing FlightGear terrain", e);
			return -1;
		} finally {
			pool.shutdown();
		}
	}

	private List<Tile> collectTiles(List<Cell> cells, File simTerrainDir, ExecutorService pool)
			throws InterruptedException {
		List<Callable<List<Tile>>> tasks = new ArrayList<>();
		for (Cell cell : cells) {
			tasks.add(() -> {
				List<Tile> cellTiles = fetchDirIndex(cell);
				if (cellTiles.isEmpty()) {
					cellTiles = scanSimCell(cell, simTerrainDir);
				}
				return cellTiles;
			});
		}
		Map<String, Tile> unique = new LinkedHashMap<>();
		for (Future<List<Tile>> future : pool.invokeAll(tasks)) {
			try {
				for (Tile tile : future.get()) {
					unique.put(tile.band + "/" + tile.cell + "/" + tile.index, tile);
				}
			} catch (ExecutionException e) {
				Osm2xpLogger.error("Error collecting terrain tiles", e.getCause());
			}
		}
		return new ArrayList<>(unique.values());
	}

	/** Ensures the tile is in the cache and copies it into the output terrain dir. */
	private String ensureTile(Tile tile, TerrainPaths paths, AtomicInteger downloaded, AtomicInteger copied,
			AtomicInteger present) {
		Path cacheTile = cacheTile(tile, paths.getCacheDir());
		try {
			if (Files.isRegularFile(cacheTile) && (tile.size < 0 || Files.size(cacheTile) == tile.size)) {
				present.incrementAndGet();
			} else if (copyFromSim(tile, paths.getSimTerrainDir(), cacheTile)) {
				copied.incrementAndGet();
			} else if (downloadTile(tile, cacheTile)) {
				downloaded.incrementAndGet();
			} else {
				return "fail";
			}
			copyToOutput(cacheTile, tile, paths.getOutputTerrainDir());
			return "ok";
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return "fail";
		} catch (IOException e) {
			return "fail";
		}
	}

	private Path cacheTile(Tile tile, File cacheDir) {
		return new File(new File(new File(cacheDir, tile.band), tile.cell), tile.index + BTG_SUFFIX).toPath();
	}

	private boolean copyFromSim(Tile tile, File simTerrainDir, Path cacheTile) {
		if (simTerrainDir == null) {
			return false;
		}
		Path source = new File(new File(new File(simTerrainDir, tile.band), tile.cell), tile.index + BTG_SUFFIX)
				.toPath();
		if (!Files.isRegularFile(source)) {
			return false;
		}
		try {
			Files.createDirectories(cacheTile.getParent());
			Files.copy(source, cacheTile, StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException e) {
			Osm2xpLogger.warning("Unable to copy terrain tile from scenery: " + e.getMessage());
			return false;
		}
	}

	private void copyToOutput(Path cacheTile, Tile tile, File outputTerrainDir) {
		if (outputTerrainDir == null) {
			return;
		}
		Path target = new File(new File(new File(outputTerrainDir, tile.band), tile.cell), tile.index + BTG_SUFFIX)
				.toPath();
		try {
			if (Files.isRegularFile(target) && Files.size(target) == Files.size(cacheTile)) {
				return;
			}
			Files.createDirectories(target.getParent());
			Files.copy(cacheTile, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Osm2xpLogger.warning("Unable to copy terrain tile to output: " + e.getMessage());
		}
	}

	private List<Cell> enumerateCells(double minLon, double minLat, double maxLon, double maxLat) {
		List<Cell> cells = new ArrayList<>();
		int lonMin = (int) Math.floor(minLon);
		int lonMax = (int) Math.ceil(maxLon);
		int latMin = (int) Math.floor(minLat);
		int latMax = (int) Math.ceil(maxLat);
		for (int lon = lonMin; lon <= lonMax; lon++) {
			for (int lat = latMin; lat <= latMax; lat++) {
				cells.add(new Cell(lon, lat));
			}
		}
		return cells;
	}

	private List<Tile> fetchDirIndex(Cell cell) {
		String band = bandPath(cell.lon, cell.lat);
		String cellPath = cellPath(cell.lon, cell.lat);
		String relativePath = "/Terrain/" + band + "/" + cellPath + "/.dirindex";
		List<Tile> tiles = new ArrayList<>();
		try {
			String body = fetchString(relativePath);
			if (body == null) {
				return tiles;
			}
			for (String line : body.split("\n")) {
				Matcher matcher = DIRINDEX_BTG.matcher(line.trim());
				if (matcher.matches()) {
					tiles.add(new Tile(band, cellPath, Long.parseLong(matcher.group(1)),
							Long.parseLong(matcher.group(2))));
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			Osm2xpLogger.error("Interrupted fetching dirindex " + relativePath, e);
		}
		return tiles;
	}

	/** Lists the {@code .btg.gz} tiles of a cell from the user's scenery root. */
	private List<Tile> scanSimCell(Cell cell, File simTerrainDir) {
		List<Tile> tiles = new ArrayList<>();
		if (simTerrainDir == null) {
			return tiles;
		}
		String band = bandPath(cell.lon, cell.lat);
		String cellPath = cellPath(cell.lon, cell.lat);
		File dir = new File(new File(simTerrainDir, band), cellPath);
		File[] files = dir.listFiles((d, name) -> name.endsWith(BTG_SUFFIX));
		if (files == null) {
			return tiles;
		}
		for (File file : files) {
			String name = file.getName();
			try {
				long index = Long.parseLong(name.substring(0, name.length() - BTG_SUFFIX.length()));
				tiles.add(new Tile(band, cellPath, index, file.length()));
			} catch (NumberFormatException e) {
				// not a numeric tile (e.g. an ICAO airport tile), skip
			}
		}
		return tiles;
	}

	private boolean downloadTile(Tile tile, Path cacheTile) throws InterruptedException {
		try {
			Files.createDirectories(cacheTile.getParent());
		} catch (IOException e) {
			return false;
		}
		Path tmp = cacheTile.resolveSibling(cacheTile.getFileName() + ".tmp");
		String relativePath = "/Terrain/" + tile.band + "/" + tile.cell + "/" + tile.index + BTG_SUFFIX;
		if (downloadToFile(relativePath, tmp, tile.size)) {
			try {
				Files.move(tmp, cacheTile, StandardCopyOption.REPLACE_EXISTING);
				return true;
			} catch (IOException e) {
				return false;
			}
		}
		return false;
	}

	/** SimGear {@code SGBucket::gen_base_path} band folder, e.g. e080n50. */
	private static String bandPath(int lon, int lat) {
		int bandLon = (int) Math.floor(lon / 10.0) * 10;
		int bandLat = (int) Math.floor(lat / 10.0) * 10;
		return String.format(Locale.ROOT, "%s%03d%s%02d", hem(lon), Math.abs(bandLon), pole(lat), Math.abs(bandLat));
	}

	/** SimGear {@code SGBucket::gen_base_path} cell folder, e.g. e080n52. */
	private static String cellPath(int lon, int lat) {
		return String.format(Locale.ROOT, "%s%03d%s%02d", hem(lon), Math.abs(lon), pole(lat), Math.abs(lat));
	}

	private static String hem(int v) {
		return v >= 0 ? "e" : "w";
	}

	private static String pole(int v) {
		return v >= 0 ? "n" : "s";
	}

	private static final class Cell {
		final int lon;
		final int lat;

		Cell(int lon, int lat) {
			this.lon = lon;
			this.lat = lat;
		}
	}

	private static final class Tile {
		final String band;
		final String cell;
		final long index;
		final long size;

		Tile(String band, String cell, long index, long size) {
			this.band = band;
			this.cell = cell;
			this.index = index;
			this.size = size;
		}
	}
}
