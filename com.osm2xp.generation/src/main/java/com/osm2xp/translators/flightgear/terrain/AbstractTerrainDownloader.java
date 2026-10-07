package com.osm2xp.translators.flightgear.terrain;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.FlightGearOptions;

/**
 * Base class for FlightGear terrain downloaders. Holds the shared HTTP client,
 * mirror selection, retry/back-off policy and thread-pool sizing; subclasses
 * decide which files to fetch for the requested area.
 * <p>
 * TerraSync is served by several mirrors. The FlightGear CDN
 * ({@code terrasync.b-cdn.net}) is not reachable from every network, so the
 * official SourceForge master and the Gdańsk mirror are tried as fallbacks. The
 * first mirror that answers is resolved once (by probing its root
 * {@code .dirindex}) and used for the rest of the run, so a dead mirror does not
 * add a timeout to every request.
 *
 * @author osm2xp
 */
public abstract class AbstractTerrainDownloader {

	protected static final int MAX_ATTEMPTS = 5;
	protected static final long[] BACKOFF_MS = { 1000L, 3000L, 7000L, 15000L, 30000L };

	/**
	 * Default TerraSync mirrors, taken from
	 * {@link FlightGearOptions#DEFAULT_TERRAIN_MIRRORS}.
	 */
	public static final List<String> DEFAULT_BASE_URLS = FlightGearOptions.DEFAULT_TERRAIN_MIRRORS;

	public static final int DEFAULT_CONCURRENCY = 10;

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
	private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(8);

	protected final List<String> baseUrls;
	protected final int concurrency;
	protected final HttpClient client;

	private volatile String activeBaseUrl;

	protected AbstractTerrainDownloader() {
		this(DEFAULT_BASE_URLS, DEFAULT_CONCURRENCY);
	}

	protected AbstractTerrainDownloader(String baseUrl, int concurrency) {
		this(Collections.singletonList(baseUrl), concurrency);
	}

	protected AbstractTerrainDownloader(List<String> baseUrls, int concurrency) {
		List<String> stripped = new ArrayList<>();
		for (String base : baseUrls) {
			if (base != null && !base.trim().isEmpty()) {
				stripped.add(stripTrailingSlash(base.trim()));
			}
		}
		this.baseUrls = stripped.isEmpty() ? DEFAULT_BASE_URLS : Collections.unmodifiableList(stripped);
		this.concurrency = concurrency;
		this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
				.followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	/**
	 * Ensures the terrain covering the given lon/lat bounding box is present
	 * under {@link TerrainPaths#getCacheDir()}, and copies every tile of the area
	 * into {@link TerrainPaths#getOutputTerrainDir()}.
	 *
	 * @return the number of tiles ensured (downloaded, copied or already
	 *         present), or {@code -1} on failure
	 */
	public abstract int download(double minLon, double minLat, double maxLon, double maxLat, TerrainPaths paths);

	protected ExecutorService newPool() {
		return Executors.newFixedThreadPool(concurrency);
	}

	/** The mirror to use, resolved once by probing the candidates in order. */
	protected String activeBaseUrl() {
		String resolved = activeBaseUrl;
		if (resolved == null) {
			synchronized (this) {
				resolved = activeBaseUrl;
				if (resolved == null) {
					resolved = resolveActiveBaseUrl();
					activeBaseUrl = resolved;
				}
			}
		}
		return resolved;
	}

	private String resolveActiveBaseUrl() {
		List<String> candidates = new ArrayList<>();
		String lastMirror = TerrainMirrorStore.load();
		if (lastMirror != null && baseUrls.contains(lastMirror)) {
			candidates.add(lastMirror);
		}
		for (String base : baseUrls) {
			if (!candidates.contains(base)) {
				candidates.add(base);
			}
		}
		for (String base : candidates) {
			if (isReachable(base)) {
				Osm2xpLogger.info("Using FlightGear terrain mirror " + base);
				TerrainMirrorStore.save(base);
				return base;
			}
			Osm2xpLogger.warning("FlightGear terrain mirror unreachable: " + base);
		}
		Osm2xpLogger.warning("No FlightGear terrain mirror reachable, trying " + candidates.get(0));
		return candidates.get(0);
	}

	private boolean isReachable(String base) {
		try {
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(base + "/.dirindex"))
					.timeout(PROBE_TIMEOUT).GET().build();
			HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
			return response.statusCode() == 200;
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** Fetches a text resource relative to the active mirror, or {@code null} on failure. */
	protected String fetchString(String relativePath) throws InterruptedException {
		String url = activeBaseUrl() + relativePath;
		try {
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(1)).GET()
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			return response.statusCode() == 200 ? response.body() : null;
		} catch (IOException e) {
			Osm2xpLogger.error("Error fetching " + url, e);
			return null;
		}
	}

	/**
	 * Reports the number of bytes received so far while a resource is being
	 * downloaded. Called on the downloading thread.
	 */
	@FunctionalInterface
	public interface ProgressListener {
		void onProgress(long bytesRead, long total);
	}

	/**
	 * Downloads the resource at {@code relativePath} (relative to the active
	 * mirror) to {@code tmp}, retrying with exponential back-off. A negative
	 * {@code expectedSize} disables the size check.
	 */
	protected boolean downloadToFile(String relativePath, Path tmp, long expectedSize) throws InterruptedException {
		return downloadToFile(relativePath, tmp, expectedSize, null);
	}

	/**
	 * As {@link #downloadToFile(String, Path, long)}, but reports the received
	 * byte count to {@code listener} while the body is streamed to disk.
	 */
	protected boolean downloadToFile(String relativePath, Path tmp, long expectedSize, ProgressListener listener)
			throws InterruptedException {
		String url = activeBaseUrl() + relativePath;
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(2))
						.GET().build();
				HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
				if (response.statusCode() == 200) {
					long read;
					try (InputStream body = response.body()) {
						read = copyToFile(body, tmp, expectedSize, listener);
					}
					if (expectedSize < 0 || read == expectedSize) {
						return true;
					}
				} else {
					response.body().close();
				}
			} catch (IOException e) {
				// transient failure, retry below
			}
			// This attempt failed: remove the partial file before retrying.
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
				// best effort
			}
			Thread.sleep(BACKOFF_MS[attempt]);
		}
		return false;
	}

	private static long copyToFile(InputStream in, Path tmp, long total, ProgressListener listener) throws IOException {
		long read = 0;
		byte[] buffer = new byte[64 * 1024];
		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp))) {
			int n;
			while ((n = in.read(buffer)) != -1) {
				out.write(buffer, 0, n);
				read += n;
				if (listener != null) {
					listener.onProgress(read, total);
				}
			}
		}
		return read;
	}

	protected static String stripTrailingSlash(String url) {
		String result = url;
		while (result != null && result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}
}
