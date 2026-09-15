package com.osm2xp.translators.flightgear;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.osm2xp.core.logging.Osm2xpLogger;

/**
 * Integrated FlightGear terrain downloader (Step 0 of the airfield cut mode).
 * Downloads the missing BTG terrain tiles for a bounding box from a TerraSync
 * mirror into the local {@code flightGearSceneryPath}, mirroring the standalone
 * {@code tools/DownloadFgTerrain.java} tool.
 * <p>
 * The manifest for each 1-degree cell comes from its {@code .dirindex} file;
 * every tile is verified against the recorded size before being moved into
 * place, and failed downloads are retried with exponential back-off.
 */
public class FlightGearTerrainDownloader {

	private static final Pattern DIRINDEX_BTG = Pattern.compile("^f:(\\d+)\\.btg\\.gz:[0-9a-f]+:(\\d+)$");
	private static final int MAX_ATTEMPTS = 5;
	private static final long[] BACKOFF_MS = { 1000L, 3000L, 7000L, 15000L, 30000L };
	private static final String DEFAULT_BASE_URL = "https://terrasync.b-cdn.net";
	private static final int DEFAULT_CONCURRENCY = 8;

	private final String baseUrl;
	private final int concurrency;
	private final HttpClient client;

	public FlightGearTerrainDownloader() {
		this(DEFAULT_BASE_URL, DEFAULT_CONCURRENCY);
	}

	public FlightGearTerrainDownloader(String baseUrl, int concurrency) {
		this.baseUrl = stripTrailingSlash(baseUrl);
		this.concurrency = concurrency;
		this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	/**
	 * Ensures the terrain tiles for the given lon/lat bounding box are present
	 * under {@code sceneryRoot}.
	 *
	 * @return the number of tiles already present or downloaded, or {@code -1} when
	 *         the download could not be completed
	 */
	public int download(double minLon, double minLat, double maxLon, double maxLat, File sceneryRoot) {
		if (sceneryRoot == null) {
			return -1;
		}
		List<Cell> cells = enumerateCells(minLon, minLat, maxLon, maxLat);
		List<Tile> tiles = new ArrayList<>();
		for (Cell cell : cells) {
			tiles.addAll(fetchDirIndex(cell));
		}
		if (tiles.isEmpty()) {
			return 0;
		}

		Path root = sceneryRoot.toPath();
		AtomicInteger downloaded = new AtomicInteger();
		AtomicInteger present = new AtomicInteger();
		List<Callable<String>> tasks = new ArrayList<>();
		for (Tile tile : tiles) {
			tasks.add(() -> {
				String result = downloadOne(tile, root);
				if ("ok".equals(result)) {
					downloaded.incrementAndGet();
				} else if ("skip".equals(result)) {
					present.incrementAndGet();
				}
				return result;
			});
		}

		ExecutorService pool = Executors.newFixedThreadPool(concurrency);
		int failed = 0;
		try {
			List<Future<String>> results = pool.invokeAll(tasks);
			for (Future<String> future : results) {
				if ("fail".equals(future.get())) {
					failed++;
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return -1;
		} catch (Exception e) {
			Osm2xpLogger.error("Error downloading FlightGear terrain", e);
			return -1;
		} finally {
			pool.shutdown();
		}

		if (failed > 0) {
			Osm2xpLogger.info("FlightGear terrain download finished: " + downloaded.get() + " new, "
					+ present.get() + " present, " + failed + " failed");
			return -1;
		}
		Osm2xpLogger.info("FlightGear terrain download finished: " + downloaded.get() + " new, " + present.get()
				+ " present");
		return downloaded.get() + present.get();
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
		String url = baseUrl + "/Terrain/" + band + "/" + cellPath + "/.dirindex";
		List<Tile> tiles = new ArrayList<>();
		try {
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(1)).GET()
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return tiles;
			}
			for (String line : response.body().split("\n")) {
				Matcher matcher = DIRINDEX_BTG.matcher(line.trim());
				if (matcher.matches()) {
					tiles.add(new Tile(band, cellPath, Long.parseLong(matcher.group(1)),
							Long.parseLong(matcher.group(2))));
				}
			}
		} catch (IOException | InterruptedException e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			Osm2xpLogger.error("Error fetching dirindex " + url, e);
		}
		return tiles;
	}

	private String downloadOne(Tile tile, Path root) throws InterruptedException {
		Path dir = root.resolve("Terrain").resolve(tile.band).resolve(tile.cell);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			return "fail";
		}
		Path target = dir.resolve(tile.index + ".btg.gz");
		if (Files.isRegularFile(target)) {
			try {
				if (Files.size(target) == tile.size) {
					return "skip";
				}
			} catch (IOException ignored) {
				// fall through to re-download
			}
		}
		Path tmp = dir.resolve(tile.index + ".btg.gz.tmp");
		String url = baseUrl + "/Terrain/" + tile.band + "/" + tile.cell + "/" + tile.index + ".btg.gz";
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(2)).GET()
						.build();
				HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tmp));
				if (response.statusCode() == 200 && Files.size(tmp) == tile.size) {
					Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
					return "ok";
				}
			} catch (IOException e) {
				// transient failure, retry below
			} finally {
				if (attempt < MAX_ATTEMPTS - 1) {
					try {
						Files.deleteIfExists(tmp);
					} catch (IOException ignored) {
						// best effort
					}
				}
			}
			Thread.sleep(BACKOFF_MS[attempt]);
		}
		return "fail";
	}

	private static String bandPath(int lon, int lat) {
		int bandLon = (int) Math.floor(lon / 10.0) * 10;
		int bandLat = (int) Math.floor(lat / 10.0) * 10;
		return String.format(Locale.ROOT, "%s%03d%s%02d", hem(lon), Math.abs(bandLon), pole(lat), Math.abs(bandLat));
	}

	private static String cellPath(int lon, int lat) {
		return String.format(Locale.ROOT, "%s%03d%s%02d", hem(lon), Math.abs(lon), pole(lat), Math.abs(lat));
	}

	private static String hem(int v) {
		return v >= 0 ? "e" : "w";
	}

	private static String pole(int v) {
		return v >= 0 ? "n" : "s";
	}

	private static String stripTrailingSlash(String url) {
		String result = url;
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
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
