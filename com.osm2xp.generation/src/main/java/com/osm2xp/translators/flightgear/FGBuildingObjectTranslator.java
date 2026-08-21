package com.osm2xp.translators.flightgear;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.onpositive.classification.core.buildings.OSMBuildingType;
import com.onpositive.classification.core.buildings.TypeProvider;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.generation.paths.PathsService;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.model.xplane.ModelWithSize;
import com.osm2xp.stats.CountStats;
import com.osm2xp.stats.StatsProvider;
import com.osm2xp.translators.FlightGearStgWriterProvider;
import com.osm2xp.translators.IPolyHandler;
import com.osm2xp.utils.geometry.GeomUtils;
import com.osm2xp.utils.osm.OsmUtils;

import math.geom2d.Point2D;
import math.geom2d.line.LineSegment2D;

/**
 * FlightGear counterpart of the X-Plane {@code XPPolyTo3DObjectTranslator}. Loads the models with
 * explicit size (e.g. <code>house/13.0x11.00</code>) from the bundled FlightGear
 * <code>flightgear/objects</code> folder and selects a suitable model by building type and size,
 * writing an <code>OBJECT_SHARED_AGL Models/objects/... </code> line into the sub-bucket STG.
 *
 * <p>
 * Only active when {@code generateBuildings3D} is enabled and the polygon is a building. Otherwise
 * buildings keep going to <code>BUILDING_LIST</code> as before.
 *
 * @author osm2xp
 */
public class FGBuildingObjectTranslator implements IPolyHandler {

	private static final String STG_PATTERN = "OBJECT_SHARED_AGL %s %.6f %.6f 0 %1.2f 0 0\n";

	private final Multimap<OSMBuildingType, ModelWithSize> modelsByType = ArrayListMultimap.create();
	private FlightGearStgWriterProvider stgWriterProvider;
	private final Random rand = new Random();

	public FGBuildingObjectTranslator() {
		File objectsFolder = PathsService.getPathsProvider().getFlightGearObjectsFolder();
		for (OSMBuildingType type : OSMBuildingType.values()) {
			File folder = new File(objectsFolder, type.name().toLowerCase());
			modelsByType.putAll(type,
					getFromDirectory(objectsFolder.getName() + "/" + folder.getName(), folder));
		}
	}

	@Override
	public void setStgWriterProvider(FlightGearStgWriterProvider stgWriterProvider) {
		this.stgWriterProvider = stgWriterProvider;
	}

	private static final String OBJ_EXT = ".ac";

	private List<ModelWithSize> getFromDirectory(String preffixPath, File parentFolder) {
		if (!parentFolder.isDirectory()) {
			return Collections.emptyList();
		}
		File[] files = parentFolder.listFiles((dir, name) -> name.endsWith(OBJ_EXT));
		List<ModelWithSize> resList = new ArrayList<>();
		if (files != null) {
			resList.addAll(Arrays.asList(files).stream()
					.map(file -> createFromFileName(preffixPath, file.getName()))
					.filter(model -> model != null).collect(Collectors.toList()));
		}
		File[] folders = parentFolder.listFiles(File::isDirectory);
		if (folders != null) {
			for (File folder : folders) {
				resList.addAll(getFromDirectory(preffixPath + "/" + folder.getName(), folder));
			}
		}
		return resList;
	}

	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		FlightGearOptions options = FlightGearOptionsProvider.getOptions();
		if (!options.isGenerateBuildings3D()) {
			return false;
		}
		if (!(osmPolyline instanceof OsmPolygon) || !OsmUtils.isBuilding(osmPolyline.getTags())) {
			return false;
		}
		OsmPolygon polygon = (OsmPolygon) osmPolyline;
		if (!polygon.isSimplePolygon()) {
			polygon = polygon.toSimplifiedPoly();
		}
		OSMBuildingType buildingType = TypeProvider.getBuildingType(osmPolyline.getTags());
		if (buildingType == null) {
			return false;
		}
		LineSegment2D edge0 = polygon.getPolyline().edge(0);
		LineSegment2D edge1 = polygon.getPolyline().edge(1);
		LineSegment2D edge2 = polygon.getPolyline().edge(2);
		LineSegment2D edge3 = polygon.getPolyline().edge(3);
		Collection<ModelWithSize> models = modelsByType.get(buildingType);
		if (models == null || models.isEmpty()) {
			return false;
		}
		double len1 = GeomUtils.computeAvgDistance(edge0, edge2);
		double len2 = GeomUtils.computeAvgDistance(edge1, edge3);
		int height = polygon.getHeight();
		ModelMatch match;
		if (height > 0) {
			match = selectMatchedModel(len1, len2, 0.3,
					chooseByHeight(height, 0.3, 2, models));
		} else {
			match = selectMatchedModel(len1, len2, 0.3, models);
		}
		if (match != null) {
			Point2D center = GeomUtils.getPolylineCenter(polygon.getPolyline());
			double angle = match.directAngle
					? GeomUtils.getTrueBearing(edge1.firstPoint(), edge1.lastPoint())
					: GeomUtils.getTrueBearing(edge0.firstPoint(), edge0.lastPoint());
			double d = Math.random();
			angle = d < 0.5 ? angle : (angle + 180) % 360;
			String modelPath = "Models/" + match.matchedModel.getPath();
			writeObject(modelPath, center, angle);
			return true;
		}
		return false;
	}

	private void writeObject(String modelPath, Point2D origin, double angle) {
		if (stgWriterProvider == null) {
			Osm2xpLogger.warning("FG building object translator: no STG writer provider set");
			return;
		}
		BufferedWriter writer = stgWriterProvider.getStgWriter(origin.x(), origin.y());
		if (writer == null) {
			return;
		}
		try {
			writer.write(String.format(Locale.ROOT, STG_PATTERN, modelPath, origin.x(), origin.y(), angle));
			CountStats countStats = StatsProvider.getCommonStats();
			if (countStats != null) {
				countStats.incCount("object");
			}
		} catch (IOException e) {
			Osm2xpLogger.error("Error writing FlightGear building object declaration", e);
		}
	}

	private Collection<ModelWithSize> chooseByHeight(int height, double heightTolerance,
			double allowedHeightDifference, Collection<ModelWithSize> models) {
		List<ModelWithSize> matchedModels = new ArrayList<>();
		double lowerBound = height - height * heightTolerance;
		double upperBound = height + height * heightTolerance;
		for (ModelWithSize curModel : models) {
			if (curModel.getHeight() == 0) {
				continue;
			}
			if ((Math.abs(height - curModel.getHeight()) < allowedHeightDifference)
					|| (curModel.getHeight() >= lowerBound && curModel.getHeight() <= upperBound)) {
				matchedModels.add(curModel);
			}
		}
		return matchedModels;
	}

	private ModelMatch selectMatchedModel(double len1, double len2, double tolerance,
			Collection<ModelWithSize> models) {
		double dist = Double.MAX_VALUE;
		List<ModelMatch> matchedList = new ArrayList<>();
		for (ModelWithSize model : models) {
			double dist1 = GeomUtils.fitWithDistance(model.geXSize(), model.getYSize(), tolerance, len1, len2);
			double dist2 = GeomUtils.fitWithDistance(model.geXSize(), model.getYSize(), tolerance, len2, len1);
			boolean directAngle = dist1 < dist2;
			if (dist1 < dist || dist2 < dist) {
				dist = Math.min(dist1, dist2);
				matchedList.clear();
				matchedList.add(new ModelMatch(model, directAngle));
			} else if (dist < Double.MAX_VALUE && (dist1 == dist || dist2 == dist)) {
				matchedList.add(new ModelMatch(model, directAngle));
			}
		}
		if (!matchedList.isEmpty()) {
			return matchedList.get(rand.nextInt(matchedList.size()));
		}
		return null;
	}

	@Override
	public void translationComplete() {
		// Nothing to flush
	}

	@Override
	public String getId() {
		return "building_object";
	}

	@Override
	public boolean isTerminating() {
		return false;
	}

	private static class ModelMatch {
		public final boolean directAngle;
		public final ModelWithSize matchedModel;

		ModelMatch(ModelWithSize matchedModel, boolean directAngle) {
			this.directAngle = directAngle;
			this.matchedModel = matchedModel;
		}
	}

	protected ModelWithSize createFromFileName(String preffixPath, String fileName) {
		int idx = 0;
		int n = fileName.length() - OBJ_EXT.length();
		while (idx < n) {
			if (Character.isDigit(fileName.charAt(idx))) {
				int start = idx;
				while (idx < n && (Character.isDigit(fileName.charAt(idx)) || fileName.charAt(idx) == 'x'
						|| fileName.charAt(idx) == '.')) {
					idx++;
				}
				idx--;
				while (idx > 0 && !Character.isDigit(fileName.charAt(idx))) {
					idx--;
				}
				idx++;
				String marking = fileName.substring(start, idx);
				String[] parts = marking.split("x");
				if (parts.length == 2) {
					try {
						double x = Double.parseDouble(parts[0]);
						double y = Double.parseDouble(parts[1]);
						ModelWithSize result = new ModelWithSize(preffixPath + "/" + fileName, x, y);
						if (idx < n && fileName.charAt(idx) == 'h') {
							idx++;
							StringBuilder builder = new StringBuilder();
							while (idx < n && (Character.isDigit(fileName.charAt(idx))
									|| fileName.charAt(idx) == '.')) {
								builder.append(fileName.charAt(idx));
								idx++;
							}
							if (builder.length() > 0) {
								result.setHeight(Double.parseDouble(builder.toString()));
							}
						}
						return result;
					} catch (NumberFormatException e) {
						Osm2xpLogger.error(e);
					}
				}
			} else {
				idx++;
			}
		}
		return null;
	}
}