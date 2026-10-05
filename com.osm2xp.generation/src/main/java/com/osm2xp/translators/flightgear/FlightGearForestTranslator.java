package com.osm2xp.translators.flightgear;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.model.osm.polygon.OsmMultiPolygon;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.spatial.TileSpatialIndex;
import com.osm2xp.translators.IPolyHandler;
import com.osm2xp.translators.flightgear.spatial.TerrainSpatialIndexService;

import math.geom2d.polygon.LinearRing2D;

/**
 * Detects OSM forest polygons and writes them as FlightGear trees using the
 * <code>TREE_LIST</code> STG directive (the "tree shader"/random vegetation),
 * the format FlightGear actually renders — BTG {@code SG_POINTS} are not used by
 * the tree shader.
 * <p>
 * For each bucket a gzipped tree-coordinate file is written next to the STG,
 * containing {@code X Y Z} lines (+X south, +Y east, +Z up, relative to the
 * bucket centre); a {@code TREE_LIST <file> <material> <lon> <lat> <elev>} line
 * is added to the bucket STG once per material. Tree elevations come from the
 * shared per-tile {@link TileSpatialIndex}.
 *
 * @author osm2xp
 */
public class FlightGearForestTranslator implements IPolyHandler {

	private static final int MAX_TREES_PER_BUCKET = 60000;
	private static final long MAX_GRID_CELLS = 2_000_000L;

	private final Map<FlightGearBucket, List<ForestArea>> byBucket = new LinkedHashMap<>();
	private final Random random = new Random(20240101L);

	private FlightGearBucketOutputProvider bucketOutputProvider;

	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		if (!FlightGearOptionsProvider.getOptions().isGenerateForests()) {
			return false;
		}
		if (!(osmPolyline instanceof OsmPolygon)) {
			return false;
		}
		OsmPolygon polygon = (OsmPolygon) osmPolyline;
		if (polygon.getPolygon() == null || !isForest(polygon.getTags())) {
			return false;
		}
		// Multipolygon forest relations carry holes; keep them so trees are not
		// placed inside e.g. lakes/clearings (same as the X-Plane forest output).
		List<LinearRing2D> holes = polygon instanceof OsmMultiPolygon
				? ((OsmMultiPolygon) polygon).getInnerPolys()
				: null;
		contribute(polygon.getPolygon(), holes, ForestType.fromTags(polygon.getTags()).getMaterial());
		return true;
	}

	/** Registers a forest polygon (already clipped to the tile) for tree output. */
	public void contribute(LinearRing2D ring, String material) {
		contribute(ring, null, material);
	}

	/** Registers a forest polygon with optional hole rings for tree output. */
	public void contribute(LinearRing2D ring, List<LinearRing2D> holes, String material) {
		if (ring == null || ring.vertices().size() < 3) {
			return;
		}
		ForestArea area = new ForestArea(ring, holes, material);
		for (FlightGearBucket bucket : bucketsFor(area)) {
			byBucket.computeIfAbsent(bucket, k -> new ArrayList<>()).add(area);
		}
	}

	@Override
	public void translationComplete() {
		if (byBucket.isEmpty()) {
			return;
		}
		TerrainSpatialIndexService service = TerrainSpatialIndexService.shared();
		if (service == null || service.isDisabled()) {
			Osm2xpLogger.warning("Forest generation skipped: no terrain spatial index available "
					+ "(no terrain downloaded?)");
			return;
		}
		double spacing = Math.max(1.0, FlightGearOptionsProvider.getOptions().getForestSpacingM());
		for (Map.Entry<FlightGearBucket, List<ForestArea>> entry : byBucket.entrySet()) {
			try {
				writeBucket(entry.getKey(), entry.getValue(), service, spacing);
			} catch (Throwable t) {
				Osm2xpLogger.error("Error writing forest trees for bucket " + entry.getKey(), t);
			}
		}
		byBucket.clear();
	}

	private void writeBucket(FlightGearBucket bucket, List<ForestArea> areas, TerrainSpatialIndexService service,
			double spacing) throws IOException {
		if (bucketOutputProvider == null) {
			return;
		}
		FlightGearBucketOutput output = bucketOutputProvider.getBucketOutput(bucket.getCenterLon(),
				bucket.getCenterLat());
		if (output == null) {
			return;
		}
		TileSpatialIndex index = service.indexFor(bucket);
		if (index == null) {
			return;
		}
		double anchorLon = bucket.getCenterLon();
		double anchorLat = bucket.getCenterLat();

		// Local bounds of THIS bucket: a polygon is only clipped to the 1-degree
		// tile, so without this every overlapping bucket would write the whole
		// polygon (duplicates + out-of-tile points) and the per-bucket tree budget
		// would be consumed by the first areas only.
		double[] bounds = bucketLocalBounds(bucket, anchorLon, anchorLat);
		double bucketMinE = bounds[0];
		double bucketMinN = bounds[1];
		double bucketMaxE = bounds[2];
		double bucketMaxN = bounds[3];

		List<AreaLocal> locals = new ArrayList<>(areas.size());
		double unionMinE = Double.POSITIVE_INFINITY;
		double unionMinN = Double.POSITIVE_INFINITY;
		double unionMaxE = Double.NEGATIVE_INFINITY;
		double unionMaxN = Double.NEGATIVE_INFINITY;
		for (ForestArea area : areas) {
			AreaLocal local = AreaLocal.of(area, anchorLon, anchorLat);
			locals.add(local);
			unionMinE = Math.min(unionMinE, local.minE);
			unionMinN = Math.min(unionMinN, local.minN);
			unionMaxE = Math.max(unionMaxE, local.maxE);
			unionMaxN = Math.max(unionMaxN, local.maxN);
		}
		double roiMinE = Math.max(unionMinE, bucketMinE);
		double roiMaxE = Math.min(unionMaxE, bucketMaxE);
		double roiMinN = Math.max(unionMinN, bucketMinN);
		double roiMaxN = Math.min(unionMaxN, bucketMaxN);
		if (roiMinE > roiMaxE || roiMinN > roiMaxN) {
			return;
		}
		Grid grid = Grid.create(roiMinE, roiMinN, roiMaxE, roiMaxN, spacing);

		// Collect candidate cells per material (deduplicated by cell would also
		// merge overlapping polygons; not needed here).
		Map<String, IntList> candidatesByMaterial = new LinkedHashMap<>();
		IntList scratch = new IntList();
		long total = 0;
		for (AreaLocal local : locals) {
			scratch.clear();
			scanlineFill(grid, local, scratch);
			if (scratch.size == 0) {
				continue;
			}
			IntList list = candidatesByMaterial.computeIfAbsent(local.area.getMaterial(), k -> new IntList());
			for (int i = 0; i < scratch.size; i++) {
				list.add(scratch.data[i]);
			}
			total += scratch.size;
		}
		if (total == 0) {
			return;
		}

		for (Map.Entry<String, IntList> entry : candidatesByMaterial.entrySet()) {
			String material = entry.getKey();
			IntList list = entry.getValue();
			shuffle(list);
			// Distribute the per-bucket budget proportionally so every zone gets
			// trees instead of the first one consuming the whole cap.
			int keep = (int) Math.min(list.size,
					total <= MAX_TREES_PER_BUCKET ? list.size
							: (long) MAX_TREES_PER_BUCKET * list.size / total);
			if (keep <= 0) {
				if (list.size > 0) {
					keep = 1; // never drop a whole zone just because it is small
				} else {
					continue;
				}
			}
			BufferedWriter writer = ensureWriter(output, material, anchorLon, anchorLat);
			if (writer == null) {
				continue;
			}
			// Build the whole material's list, then write it in one call: the writer
			// chain (BufferedWriter -> OutputStreamWriter -> GZIP) then encodes a
			// single large string instead of many small ones.
			StringBuilder buffer = new StringBuilder(1 << 16);
			for (int k = 0; k < keep; k++) {
				int cellIndex = list.data[k];
				int col = cellIndex % grid.cols;
				int row = cellIndex / grid.cols;
				double east = grid.eastOf(col) + (random.nextDouble() - 0.5) * grid.cell;
				double north = grid.northOf(row) + (random.nextDouble() - 0.5) * grid.cell;
				if (east < bucketMinE || east > bucketMaxE || north < bucketMinN || north > bucketMaxN) {
					continue;
				}
				double elevation = index.elevationLocal(east, north);
				if (!Double.isFinite(elevation)) {
					continue;
				}
				double z = elevation - FlightGearCoordinateUtils.calcHorizonElevLocal(east, north);
				if (!Double.isFinite(z) || Math.abs(z) > 20000.0) {
					continue;
				}
				appendFixed2(buffer, -north).append(' ');
				appendFixed2(buffer, east).append(' ');
				appendFixed2(buffer, z).append('\n');
			}
			if (buffer.length() > 0) {
				writer.write(buffer.toString());
			}
		}
	}

	/** Local east/north bounds of the bucket (anchor is the bucket centre). */
	private static double[] bucketLocalBounds(FlightGearBucket bucket, double anchorLon, double anchorLat) {
		double[][] corners = {
				{ bucket.getMinLon(), bucket.getMinLat() }, { bucket.getMaxLon(), bucket.getMinLat() },
				{ bucket.getMaxLon(), bucket.getMaxLat() }, { bucket.getMinLon(), bucket.getMaxLat() } };
		double minE = Double.POSITIVE_INFINITY;
		double minN = Double.POSITIVE_INFINITY;
		double maxE = Double.NEGATIVE_INFINITY;
		double maxN = Double.NEGATIVE_INFINITY;
		for (double[] corner : corners) {
			double[] local = FlightGearCoordinateUtils.toLocal(corner[0], corner[1], anchorLon, anchorLat);
			minE = Math.min(minE, local[0]);
			minN = Math.min(minN, local[1]);
			maxE = Math.max(maxE, local[0]);
			maxN = Math.max(maxN, local[1]);
		}
		return new double[] { minE, minN, maxE, maxN };
	}

	/** Opens the material's tree list and writes its STG header once. */
	private BufferedWriter ensureWriter(FlightGearBucketOutput output, String material, double anchorLon,
			double anchorLat) throws IOException {
		BufferedWriter writer = output.getTreeListWriter(material);
		if (writer == null) {
			return null;
		}
		if (!output.isTreeListHeaderWritten(material)) {
			BufferedWriter stgWriter = output.getStgWriter();
			if (stgWriter != null) {
				stgWriter.write(String.format(Locale.US, "TREE_LIST %s %s %.6f %.6f 0.00\n",
						output.getTreeListFileName(material), material, anchorLon, anchorLat));
			}
			output.setTreeListHeaderWritten(material);
		}
		return writer;
	}

	private static boolean isForest(List<com.osm2xp.core.model.osm.Tag> tags) {
		if (tags == null || tags.isEmpty()) {
			return false;
		}
		boolean hasBuilding = false;
		boolean forest = false;
		for (com.osm2xp.core.model.osm.Tag tag : tags) {
			String key = lower(tag.getKey());
			String value = lower(tag.getValue());
			if ("building".equals(key)) {
				hasBuilding = true;
			}
			if (("landuse".equals(key) && "forest".equals(value))
					|| ("natural".equals(key) && "wood".equals(value))
					|| "leaf_type".equals(key) || "wood".equals(key)
					|| key.contains("forest") || value.contains("forest")
					|| key.contains("wood") || value.contains("wood")) {
				forest = true;
			}
		}
		return forest && !hasBuilding;
	}

	private static String lower(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	@Override
	public void setBucketOutputProvider(FlightGearBucketOutputProvider bucketOutputProvider) {
		this.bucketOutputProvider = bucketOutputProvider;
	}

	@Override
	public String getId() {
		return "forest";
	}

	@Override
	public boolean isTerminating() {
		return true;
	}

	private static List<FlightGearBucket> bucketsFor(ForestArea area) {
		List<FlightGearBucket> result = new ArrayList<>();
		int lonStart = (int) Math.floor(area.getMinLon());
		int lonEnd = (int) Math.floor(area.getMaxLon());
		int latStart = (int) Math.floor(area.getMinLat());
		int latEnd = (int) Math.floor(area.getMaxLat());
		for (int lon = lonStart; lon <= lonEnd; lon++) {
			for (int lat = latStart; lat <= latEnd; lat++) {
				double span = FlightGearBucket.sgBucketSpan(lat + 0.5);
				int cols = span <= 1.0 ? (int) Math.round(1.0 / span) : 1;
				for (int x = 0; x < cols; x++) {
					for (int y = 0; y < 8; y++) {
						double centerLon = lon + x * span + span / 2.0;
						double centerLat = lat + y * FlightGearBucket.SG_BUCKET_SPAN
								+ FlightGearBucket.SG_BUCKET_SPAN / 2.0;
						FlightGearBucket bucket = FlightGearBucket.bucketFor(centerLon, centerLat);
						if (bucket.getMinLon() <= area.getMaxLon() && bucket.getMaxLon() >= area.getMinLon()
								&& bucket.getMinLat() <= area.getMaxLat() && bucket.getMaxLat() >= area.getMinLat()) {
							result.add(bucket);
						}
					}
				}
			}
		}
		return result;
	}

	private static void scanlineFill(Grid grid, AreaLocal local, IntList out) {
		double[] intersections = new double[local.totalVertices];
		int minRow = Math.max(0, grid.rowOf(local.minN));
		int maxRow = Math.min(grid.rows - 1, grid.rowOf(local.maxN));
		for (int row = minRow; row <= maxRow; row++) {
			double y = grid.northOf(row);
			int count = 0;
			// Even-odd across the outer ring AND all hole rings, so points inside a
			// hole cancel out and are not filled.
			for (int r = 0; r < local.ringX.size(); r++) {
				double[] px = local.ringX.get(r);
				double[] py = local.ringY.get(r);
				int n = px.length;
				for (int i = 0, j = n - 1; i < n; j = i++) {
					double yi = py[i];
					double yj = py[j];
					if ((yi > y) != (yj > y)) {
						intersections[count++] = px[i] + (y - yi) / (yj - yi) * (px[j] - px[i]);
					}
				}
			}
			if (count < 2) {
				continue;
			}
			Arrays.sort(intersections, 0, count);
			for (int k = 0; k + 1 < count; k += 2) {
				int c0 = grid.colOf(intersections[k]);
				int c1 = grid.colOf(intersections[k + 1]);
				if (c0 > c1) {
					int tmp = c0;
					c0 = c1;
					c1 = tmp;
				}
				c0 = Math.max(c0, 0);
				c1 = Math.min(c1, grid.cols - 1);
				for (int col = c0; col <= c1; col++) {
					out.add(row * grid.cols + col);
				}
			}
		}
	}

	private void shuffle(IntList list) {
		for (int i = list.size - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int tmp = list.data[i];
			list.data[i] = list.data[j];
			list.data[j] = tmp;
		}
	}

	/**
	 * Appends {@code value} with two decimals, avoiding the (very expensive)
	 * {@link String#format}: no locale parsing, no argument boxing, no regex.
	 * Equivalent to {@code String.format(Locale.US, "%.2f", value)} for the
	 * coordinate ranges used here.
	 */
	static StringBuilder appendFixed2(StringBuilder sb, double value) {
		long scaled = Math.round(value * 100.0);
		if (scaled < 0) {
			sb.append('-');
			scaled = -scaled;
		}
		long whole = scaled / 100;
		int frac = (int) (scaled % 100);
		sb.append(whole).append('.');
		if (frac < 10) {
			sb.append('0');
		}
		return sb.append(frac);
	}

	private static final class AreaLocal {
		final ForestArea area;
		final List<double[]> ringX;
		final List<double[]> ringY;
		final int totalVertices;
		final double minE;
		final double minN;
		final double maxE;
		final double maxN;

		private AreaLocal(ForestArea area, List<double[]> ringX, List<double[]> ringY, int totalVertices, double minE,
				double minN, double maxE, double maxN) {
			this.area = area;
			this.ringX = ringX;
			this.ringY = ringY;
			this.totalVertices = totalVertices;
			this.minE = minE;
			this.minN = minN;
			this.maxE = maxE;
			this.maxN = maxN;
		}

		static AreaLocal of(ForestArea area, double anchorLon, double anchorLat) {
			List<double[]> ringX = new ArrayList<>();
			List<double[]> ringY = new ArrayList<>();
			double minE = Double.POSITIVE_INFINITY;
			double minN = Double.POSITIVE_INFINITY;
			double maxE = Double.NEGATIVE_INFINITY;
			double maxN = Double.NEGATIVE_INFINITY;
			int total = 0;

			// Outer ring.
			int n = area.vertexCount();
			double[] ox = new double[n];
			double[] oy = new double[n];
			for (int i = 0; i < n; i++) {
				double[] local = FlightGearCoordinateUtils.toLocal(area.x(i), area.y(i), anchorLon, anchorLat);
				ox[i] = local[0];
				oy[i] = local[1];
				minE = Math.min(minE, ox[i]);
				minN = Math.min(minN, oy[i]);
				maxE = Math.max(maxE, ox[i]);
				maxN = Math.max(maxN, oy[i]);
			}
			ringX.add(ox);
			ringY.add(oy);
			total += n;

			// Holes.
			for (int h = 0; h < area.holeCount(); h++) {
				double[] gx = area.holeX(h);
				double[] gy = area.holeY(h);
				int hn = gx.length;
				double[] hx = new double[hn];
				double[] hy = new double[hn];
				for (int i = 0; i < hn; i++) {
					double[] local = FlightGearCoordinateUtils.toLocal(gx[i], gy[i], anchorLon, anchorLat);
					hx[i] = local[0];
					hy[i] = local[1];
					minE = Math.min(minE, hx[i]);
					minN = Math.min(minN, hy[i]);
					maxE = Math.max(maxE, hx[i]);
					maxN = Math.max(maxN, hy[i]);
				}
				ringX.add(hx);
				ringY.add(hy);
				total += hn;
			}
			return new AreaLocal(area, ringX, ringY, total, minE, minN, maxE, maxN);
		}
	}

	private static final class Grid {
		final double originE;
		final double originN;
		final double cell;
		final int cols;
		final int rows;

		private Grid(double originE, double originN, double cell, int cols, int rows) {
			this.originE = originE;
			this.originN = originN;
			this.cell = cell;
			this.cols = cols;
			this.rows = rows;
		}

		static Grid create(double minE, double minN, double maxE, double maxN, double spacing) {
			double width = Math.max(maxE - minE, 1e-6);
			double height = Math.max(maxN - minN, 1e-6);
			double cell = spacing;
			long cols = Math.max(1, (long) Math.ceil(width / cell)) + 1;
			long rows = Math.max(1, (long) Math.ceil(height / cell)) + 1;
			if (cols * rows > MAX_GRID_CELLS) {
				double target = Math.sqrt(width * height / (double) MAX_GRID_CELLS);
				if (target > cell) {
					cell = target;
				}
				cols = Math.max(1, (long) Math.ceil(width / cell)) + 1;
				rows = Math.max(1, (long) Math.ceil(height / cell)) + 1;
			}
			return new Grid(minE, minN, cell, (int) cols, (int) rows);
		}

		double eastOf(int col) {
			return originE + (col + 0.5) * cell;
		}

		double northOf(int row) {
			return originN + (row + 0.5) * cell;
		}

		int colOf(double e) {
			return (int) Math.floor((e - originE) / cell);
		}

		int rowOf(double n) {
			return (int) Math.floor((n - originN) / cell);
		}
	}

	private static final class IntList {
		int[] data = new int[1024];
		int size;

		void add(int value) {
			if (size == data.length) {
				data = Arrays.copyOf(data, size * 2);
			}
			data[size++] = value;
		}

		void clear() {
			size = 0;
		}
	}
}
