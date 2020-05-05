package com.osm2xp.translators.xplane;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.onpositive.classification.core.buildings.OSMBuildingType;
import com.onpositive.classification.core.buildings.TypeProvider;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.generation.options.GlobalOptionsProvider;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.options.XplaneOptions;
import com.osm2xp.generation.paths.PathsService;
import com.osm2xp.generation.xplane.resources.DsfObjectsProvider;
import com.osm2xp.generation.xplane.resources.XPOutputFormat;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.model.xplane.ModelWithSize;
import com.osm2xp.utils.geometry.GeomUtils;
import com.osm2xp.utils.osm.OsmUtils;
import com.osm2xp.writers.IWriter;

import math.geom2d.Point2D;
import math.geom2d.line.LineSegment2D;


/**
 * This translator loads models with specified size (like house_10x10.obj) from xplane/objects/ folder subfolders and tries to select suitable model by type and size
 * For supported building types, see {@link OSMBuildingType}  
 * @author 32kda
 *
 */
public class XPPolyTo3DObjectTranslator extends XPWritingTranslator {
	
	private class ModelMatch {
		public final boolean directAngle;
		public final ModelWithSize matchedModel;
		
		public ModelMatch(ModelWithSize matchedModel, boolean directAngle) {
			super();
			this.directAngle = directAngle;
			this.matchedModel = matchedModel;
		}
		
	}

	private Multimap<OSMBuildingType, ModelWithSize> modelsByType = ArrayListMultimap.create();
	private DsfObjectsProvider dsfObjectsProvider;
	private XPOutputFormat outputFormat;
	private Random rand = new Random();
	
	public XPPolyTo3DObjectTranslator(IWriter writer, DsfObjectsProvider dsfObjectsProvider, XPOutputFormat outputFormat) {
		super(writer);
		this.dsfObjectsProvider = dsfObjectsProvider;
		this.outputFormat = outputFormat;
		File objectsFolder = PathsService.getPathsProvider().getObjectsFolder();
		for (OSMBuildingType type : OSMBuildingType.values()) {
			File folder = new File(objectsFolder, type.name().toLowerCase());
			modelsByType.putAll(type,getFromDirectory(objectsFolder.getName() + "/" + folder.getName(), folder));
		}
	}

	protected List<ModelWithSize> getFromDirectory(String preffixPath, File parentFolder) {
		if (!parentFolder.isDirectory()) {
			return Collections.emptyList();
		}
		List<ModelWithSize> resList = new ArrayList<ModelWithSize>();
		File[] files = parentFolder.listFiles((dir,name) -> name.endsWith(DsfObjectsProvider.OBJ_EXT));
		resList.addAll(Arrays.asList(files).stream().map(file -> createFromFileName(preffixPath, file.getName())).filter(model -> model != null).collect(Collectors.toList()));
		
		File[] folders = parentFolder.listFiles(file -> file.isDirectory());
		for (File folder : folders) {
			resList.addAll(getFromDirectory(preffixPath + "/" + folder.getName(), folder));
		}
		return resList;
	}

	@Override
	public boolean handlePoly(OsmPolyline osmPolyline) {
		XplaneOptions xpOptions = XPlaneOptionsProvider.getOptions();
		if (!xpOptions.isGenerateObjBuildings()) {
			return false;
		}
		if (!(osmPolyline instanceof OsmPolygon)) {
			return false;
		}
		if (!((OsmPolygon) osmPolyline).isSimplePolygon()) {
			double length = GeomUtils.computeEdgesLength(osmPolyline.getPolyline());
			if (length <= xpOptions.getMaxPerimeterToSimplify()) { //If house is small enough, we don't care about complex shape and just simplify it to try finding suitable model
				osmPolyline = ((OsmPolygon) osmPolyline).toSimplifiedPoly();
			} else {
				return false;
			}
		}
		double tolerance = xpOptions.getObjSizeTolerance();
		OSMBuildingType buildingType = TypeProvider.getBuildingType(osmPolyline.getTags());

		if (buildingType != null) {
			LineSegment2D edge0 = osmPolyline.getPolyline().edge(0);
			LineSegment2D edge1 = osmPolyline.getPolyline().edge(1);
			LineSegment2D edge2 = osmPolyline.getPolyline().edge(2);
			LineSegment2D edge3 = osmPolyline.getPolyline().edge(3);
			Collection<ModelWithSize> models = modelsByType.get(buildingType);
			if (models.isEmpty()) {
				return false;
			}
			double len1 = GeomUtils.computeAvgDistance(edge0,edge2);
			double len2 = GeomUtils.computeAvgDistance(edge1,edge3);
			
			int height = OsmUtils.getHeightFromTags(osmPolyline.getTags());
			ModelMatch match = null;
			if (height > 0) {
				Collection<ModelWithSize> modelsByHeight = chooseByHeight(height, xpOptions.getObjHeightTolerance(), xpOptions.getObjHeightAllowedDifference(), models);
				match = selectMatchedModel(len1, len2, tolerance, modelsByHeight);
			} else {
				match = selectMatchedModel(len1, len2, tolerance, models);
			}			
			if (match != null) {
				Point2D center = GeomUtils.getPolylineCenter(osmPolyline.getPolyline());
				double angle = match.directAngle? GeomUtils.getTrueBearing(edge1.firstPoint(), edge1.lastPoint()) : 
											GeomUtils.getTrueBearing(edge0.firstPoint(), edge0.lastPoint());
				double d = Math.random();
				angle = d < 0.5 ? angle : (angle + 180) % 360;
				String objectString = outputFormat.getObjectString(dsfObjectsProvider.getObjectIndex(match.matchedModel.getPath()),center.x(), center.y(), angle);
				writer.write(objectString);
				return true;
			}
		}
		return false;
	}

	private Collection<ModelWithSize> chooseByHeight(int height, double heightTolerance, double allowedHeightDifference, Collection<ModelWithSize> models) {
		List<ModelWithSize> matchedModels = new ArrayList<ModelWithSize>();
		double lowerBound = height - height * heightTolerance;
		double upperBound = height + height * heightTolerance;
		for (ModelWithSize curModel : models) {
			if (curModel.getHeight() == 0) {
				continue;
			}
			if ((Math.abs(height - curModel.getHeight()) < allowedHeightDifference) ||
				(curModel.getHeight() >= lowerBound && curModel.getHeight() <= upperBound )) {
				matchedModels.add(curModel);
			}
		}
		return matchedModels;
	}

	private ModelMatch selectMatchedModel(double len1, double len2, double tolerance, Collection<ModelWithSize> models) {
		double dist = Double.MAX_VALUE;
		List<ModelMatch> matchedList = new ArrayList<ModelMatch>();
		for (ModelWithSize model : models) {
			double dist1 = GeomUtils.fitWithDistance(model.geXSize(), model.getYSize(),tolerance,len1,len2);
			double dist2 = GeomUtils.fitWithDistance(model.geXSize(), model.getYSize(),tolerance,len2,len1);
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
	
	private OSMBuildingType getTypeFromLanduse(OsmPolyline osmPolyline) {
		String landuse = osmPolyline.getTagValue("landuse"); 
		if ("allotments".equals(landuse)) {
			return OSMBuildingType.HOUSE;
		}
		if ("industrial".equals(landuse)) {
			return OSMBuildingType.INDUSTRIAL;
		}
		if ("residential".equals(landuse) || "apartments".equals(landuse)) {
			return OSMBuildingType.BLOCK;
		}
		if ("commercial".equals(landuse)) {
			return OSMBuildingType.SHOP;
		}
		return null;
	}

	@Override
	public void translationComplete() {
		// Do nothing
	}

	@Override
	public String getId() {
		return "polygon_to_object";
	}
	
	protected ModelWithSize createFromFileName(String preffixPath, String fileName) {
		int idx = 0;
		int n = fileName.length() - DsfObjectsProvider.OBJ_EXT.length();
		while (idx < n) {
			if (Character.isDigit(fileName.charAt(idx))) {
				int start = idx;
				while (idx < n && (Character.isDigit(fileName.charAt(idx)) || fileName.charAt(idx) == 'x' || fileName.charAt(idx) == '.')) {
					idx++;
				}
				idx--;
				while(idx > 0 && !Character.isDigit(fileName.charAt(idx))) { //Skip possible tail until we see a number;
					idx--;
				}
				idx++;
				String marking = fileName.substring(start, idx);
				String[] parts = marking.split("x");
				if (parts.length == 2) {
					try {
						double x = Double.parseDouble(parts[0]);
						double y = Double.parseDouble(parts[1]);
						ModelWithSize result = new ModelWithSize(preffixPath + "/" + fileName, x ,y);
						if (idx < n && fileName.charAt(idx) == 'h') {
							idx++;
							StringBuilder builder = new StringBuilder();
							while(idx < n && (Character.isDigit(fileName.charAt(idx)) || fileName.charAt(idx) == '.')) { //Skip possible tail until we see a number;
								builder.append(fileName.charAt(idx));
								idx++;
							}
							String heightVal = builder.toString();
							if (heightVal.length() > 0) {
								result.setHeight(Double.parseDouble(heightVal));
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
