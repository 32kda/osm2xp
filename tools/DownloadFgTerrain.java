import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

/**
 * Downloads FlightGear terrain (BTG tiles) for a bounding box from a TerraSync
 * mirror into a local scenery folder, without running the simulator.
 * Cross-platform (Windows/Linux/macOS), no external dependencies beyond JDK 11+.
 *
 * <p>The resulting layout matches what TerraSync maintains on disk:
 * {@code <Root>/Terrain/<band>/<cell>/<bucket-index>.btg.gz}, e.g.
 * {@code <Root>/Terrain/e080n50/e083n53/4318144.btg.gz}. This is exactly the
 * layout the application's {@code FlightGearElevProber} uses to locate a
 * building's tile before spawning {@code fgelev --use-vpb --tile-file <tile>}.
 *
 * <p>Run either with the single-file source launcher (JDK 11+):
 * <pre>
 *   java tools/DownloadFgTerrain.java --bbox "77.4,51.2,88.5,56.1" --root D:/Work/fg_scenery
 * </pre>
 * or compile once and reuse:
 * <pre>
 *   javac -d tools/out tools/DownloadFgTerrain.java
 *   java -cp tools/out DownloadFgTerrain --bbox "77.4,51.2,88.5,56.1" --root fg_scenery
 * </pre>
 *
 * Options:
 *   --bbox "lonMin,latMin,lonMax,latMax"   required, region to fetch
 *   --root &lt;dir&gt;                       output scenery root (default "fg_scenery")
 *   --base-url &lt;url&gt;                    mirror root (default https://terrasync.b-cdn.net,
 *                                          the WS2.0 CDN from the terrasync.flightgear.org
 *                                          NAPTR record; the Cloudflare-fronted sourceforge
 *                                          master rate-limits bulk downloads)
 *   --concurrency &lt;n&gt;                   parallel downloads (default 8)
 *   --force                               re-download tiles that already exist
 *
 * <p>The tile manifest comes from each 1-degree cell's {@code .dirindex} file;
 * every tile is verified against the recorded size before being moved into place,
 * and failed downloads are retried with exponential back-off.
 */
public class DownloadFgTerrain {

    private static final Pattern DIRINDEX_BTG =
            Pattern.compile("^f:(\\d+)\\.btg\\.gz:[0-9a-f]+:(\\d+)$");
    private static final int MAX_ATTEMPTS = 5;
    private static final long[] BACKOFF_MS = {1000L, 3000L, 7000L, 15000L, 30000L};
    private static final String DEFAULT_BASE_URL = "https://terrasync.b-cdn.net";
    private static final int DEFAULT_CONCURRENCY = 8;

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

    private final String baseUrl;
    private final Path root;
    private final int concurrency;
    private final boolean force;
    private final HttpClient client;

    private DownloadFgTerrain(String baseUrl, Path root, int concurrency, boolean force) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.root = root;
        this.concurrency = concurrency;
        this.force = force;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static void main(String[] args) throws Exception {
        String bbox = null;
        String baseUrl = DEFAULT_BASE_URL;
        String rootStr = "fg_scenery";
        int concurrency = DEFAULT_CONCURRENCY;
        boolean force = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--bbox":
                    bbox = requireValue(args, ++i, "--bbox");
                    break;
                case "--root":
                    rootStr = requireValue(args, ++i, "--root");
                    break;
                case "--base-url":
                    baseUrl = requireValue(args, ++i, "--base-url");
                    break;
                case "--concurrency":
                    concurrency = Integer.parseInt(requireValue(args, ++i, "--concurrency"));
                    break;
                case "--force":
                    force = true;
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(2);
            }
        }

        if (bbox == null) {
            System.err.println("Missing required option --bbox.");
            printUsage();
            System.exit(2);
        }

        double[] b = parseBbox(bbox);
        DownloadFgTerrain tool = new DownloadFgTerrain(baseUrl, Paths.get(rootStr), concurrency, force);
        tool.run(b);
    }

    private void run(double[] bbox) throws Exception {
        List<Cell> cells = new ArrayList<>();
        int lonMin = (int) Math.floor(bbox[0]);
        int lonMax = (int) Math.ceil(bbox[2]);
        int latMin = (int) Math.floor(bbox[1]);
        int latMax = (int) Math.ceil(bbox[3]);
        for (int lon = lonMin; lon <= lonMax; lon++) {
            for (int lat = latMin; lat <= latMax; lat++) {
                cells.add(new Cell(lon, lat));
            }
        }

        System.out.println("Enumerating terrain tiles from " + baseUrl + " for " + cells.size() + " cells...");
        List<Tile> tiles = new ArrayList<>();
        long totalBytes = 0;
        for (Cell cell : cells) {
            List<Tile> cellTiles = fetchDirIndex(cell);
            System.out.println("  cell " + cell.lon + "/" + cell.lat + ": " + cellTiles.size() + " tiles");
            for (Tile t : cellTiles) {
                totalBytes += t.size;
            }
            tiles.addAll(cellTiles);
        }
        System.out.printf(Locale.ROOT, "Found %d tiles, %.1f MB.%n", tiles.size(), totalBytes / 1048576.0);
        if (tiles.isEmpty()) {
            System.out.println("Nothing to download.");
            return;
        }

        AtomicInteger done = new AtomicInteger();
        AtomicInteger downloaded = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        List<Callable<String>> tasks = new ArrayList<>();
        for (Tile tile : tiles) {
            tasks.add(() -> {
                String result = downloadOne(tile);
                int d = done.incrementAndGet();
                if (result.equals("ok")) {
                    downloaded.incrementAndGet();
                } else if (result.equals("skip")) {
                    skipped.incrementAndGet();
                }
                if (d % 100 == 0) {
                    System.out.printf(Locale.ROOT, "  ... %d/%d (%d new, %d present)%n",
                            d, tiles.size(), downloaded.get(), skipped.get());
                }
                return result;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<String>> results = pool.invokeAll(tasks);
            int failed = 0;
            List<String> failedTiles = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                String r = results.get(i).get();
                if (r.equals("fail")) {
                    failed++;
                    Tile t = tiles.get(i);
                    if (failedTiles.size() < 20) {
                        failedTiles.add(t.band + "/" + t.cell + "/" + t.index);
                    }
                }
            }
            System.out.printf(Locale.ROOT, "Done: %d downloaded, %d already present, %d failed.%n",
                    downloaded.get(), skipped.get(), failed);
            if (!failedTiles.isEmpty()) {
                System.out.println("Failed tiles (first 20):");
                failedTiles.forEach(t -> System.out.println("  " + t));
            }
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Returns the terrain tile list for one 1-degree cell, or an empty list when
     * the cell has no terrain (e.g. fully over water).
     */
    private List<Tile> fetchDirIndex(Cell cell) throws Exception {
        String band = bandPath(cell.lon, cell.lat);
        String cellPath = cellPath(cell.lon, cell.lat);
        String url = baseUrl + "/Terrain/" + band + "/" + cellPath + "/.dirindex";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(1))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return new ArrayList<>();
        }
        List<Tile> tiles = new ArrayList<>();
        for (String line : response.body().split("\n")) {
            Matcher m = DIRINDEX_BTG.matcher(line.trim());
            if (m.matches()) {
                tiles.add(new Tile(band, cellPath, Long.parseLong(m.group(1)), Long.parseLong(m.group(2))));
            }
        }
        return tiles;
    }

    private String downloadOne(Tile tile) throws InterruptedException {
        Path dir = root.resolve("Terrain").resolve(tile.band).resolve(tile.cell);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return "fail";
        }
        Path target = dir.resolve(tile.index + ".btg.gz");
        if (!force && Files.isRegularFile(target)) {
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
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMinutes(2))
                        .GET()
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

    /** SimGear {@code SGBucket::gen_base_path} band folder, e.g. e080n50. */
    private static String bandPath(int lon, int lat) {
        int bandLon = (int) Math.floor(lon / 10.0) * 10;
        int bandLat = (int) Math.floor(lat / 10.0) * 10;
        return String.format(Locale.ROOT, "%s%03d%s%02d",
                hem(lon), Math.abs(bandLon), pole(lat), Math.abs(bandLat));
    }

    /** SimGear {@code SGBucket::gen_base_path} cell folder, e.g. e083n53. */
    private static String cellPath(int lon, int lat) {
        return String.format(Locale.ROOT, "%s%03d%s%02d",
                hem(lon), Math.abs(lon), pole(lat), Math.abs(lat));
    }

    private static String hem(int v) {
        return v >= 0 ? "e" : "w";
    }

    private static String pole(int v) {
        return v >= 0 ? "n" : "s";
    }

    private static double[] parseBbox(String bbox) {
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            System.err.println("Bbox must be 'lonMin,latMin,lonMax,latMax'.");
            System.exit(2);
        }
        double[] result = new double[4];
        for (int i = 0; i < 4; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }
        return result;
    }

    private static String requireValue(String[] args, int i, String option) {
        if (i >= args.length) {
            System.err.println("Missing value for " + option);
            System.exit(2);
        }
        return args[i];
    }

    private static String stripTrailingSlash(String url) {
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static void printUsage() {
        System.err.println("Usage: java tools/DownloadFgTerrain.java --bbox \"lonMin,latMin,lonMax,latMax\" [options]");
        System.err.println("Options:");
        System.err.println("  --bbox <lonMin,latMin,lonMax,latMax>   required");
        System.err.println("  --root <dir>          output scenery root (default fg_scenery)");
        System.err.println("  --base-url <url>      mirror root (default " + DEFAULT_BASE_URL + ")");
        System.err.println("  --concurrency <n>     parallel downloads (default " + DEFAULT_CONCURRENCY + ")");
        System.err.println("  --force               re-download tiles that already exist");
        System.err.println("  --help                this message");
    }
}
