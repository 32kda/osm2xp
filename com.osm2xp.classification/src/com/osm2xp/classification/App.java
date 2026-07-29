package com.osm2xp.classification;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.onpositive.classification.core.buildings.TypeProvider;
import com.osm2xp.classification.index.KdTree;
import com.osm2xp.classification.index.PointData;
import com.osm2xp.classification.learning.ModelGenerator;
import com.osm2xp.classification.model.WayEntity;
import com.osm2xp.classification.output.ARFFWriter;
import com.osm2xp.classification.output.CSVWithAdditionalsWriter;
import com.osm2xp.classification.output.StringDelimitedWriter;
import com.osm2xp.classification.parsing.LearningDataParser;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.core.model.osm.Tag;

import math.geom2d.Point2D;
import math.geom2d.ShapeArray2D;
import math.geom2d.polygon.LinearRing2D;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.evaluation.Evaluation;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.lmt.LogisticBase;
import weka.core.Debug;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Normalize;

public class App {

	private static final int NEIGHBOUR_COUNT = 4;
	
	private static final int WGS_TO_METERS_COEF = 111000;

	public static void main(String[] args) {
//		buildWithGeoindex(new File("F:/tmp/siberian-fed-district-latest.osm.pbf"));
		buildWithGeoindex(Arrays.asList(new File("f:\\tmp\\osm\\").listFiles(file -> file.getName().endsWith(".pbf") || file.getName().endsWith(".osm"))), data -> hasSuitableHeight(data));
//		buildWithGeoindex(Arrays.asList(new File("f:\\tmp\\osm\\").listFiles()), data -> data.getData().getType() != null);
//		buildDataset();
//		buildClassifier();
	}

	private static boolean hasSuitableHeight(PointData<WayEntity> data) {
		int height = HeightProvider.getHeight(data.getData().getTags());
		return height > 0 && height < 100;
	}

	protected static void buildDataset() {
		LearningDataParser parser = new LearningDataParser(new File("F:/tmp/siberian-fed-district-latest.osm.pbf"), getBuildingPredicate(), true);
		try (StringDelimitedWriter<BuildingData> writer = new ARFFWriter<BuildingData>(new File("type_ways.arff"), "type")) {
			List<WayEntity> typeWays = parser.getCollectedWays();
			for (WayEntity data : typeWays) {
				writer.write(createBuildingData(data, parser.getCollectedNodes()));
			}
		} catch (IOException e) {
			Osm2xpLogger.error("Error building dataset", e);
		}
	}
	
	protected static void buildWithGeoindex(List<File> files, Predicate<? super PointData<WayEntity>> classifiedPredicate) {
		try (CSVWithAdditionalsWriter<BuildingData> writer = new CSVWithAdditionalsWriter<>(new File(getName(files.get(0)) + "_" + NEIGHBOUR_COUNT + ".csv"), "levels", "levels", NEIGHBOUR_COUNT)) {
			for (File curFile : files) {
				processCurFile(writer, curFile, classifiedPredicate);
			}
		} catch (IOException e) {
			Osm2xpLogger.error("Error building geoindex", e);
		}
	}

	protected static void processCurFile(CSVWithAdditionalsWriter<BuildingData> writer, File curFile, Predicate<? super PointData<WayEntity>> classifiedPredicate) {
		System.out.println("Processing " + curFile.getAbsolutePath());
		KdTree<PointData<WayEntity>> kdTree = new KdTree<>();
		LearningDataParser parser = new LearningDataParser(curFile, getBuildingPredicate(), true);
		List<PointData<WayEntity>> classifiedPoints = getPointData(parser, kdTree, classifiedPredicate);
		for (PointData<WayEntity> pointData : classifiedPoints) {
			Collection<PointData<WayEntity>> neighbours = kdTree.nearestNeighbourSearch(NEIGHBOUR_COUNT + 1, pointData);
			neighbours.remove(pointData);
			writer.write(createBuildingData(pointData.getData(), parser.getCollectedNodes()), 
					neighbours.stream().limit(NEIGHBOUR_COUNT).map(data -> createBuildingData(data.getData(), parser.getCollectedNodes())).collect(Collectors.toList()));
		}
	}
	
	

	private static BuildingData createBuildingData(WayEntity way, Map<Long, Node> collectedNodes) {
		BuildingData buildingData = new BuildingData();
		computeGeometryData(buildingData, way, collectedNodes);
		return buildingData;
	}

	protected static List<PointData<WayEntity>> getPointData(LearningDataParser parser,
			KdTree<PointData<WayEntity>> kdTree, Predicate<? super PointData<WayEntity>> classifiedPredicate) {
		List<WayEntity> typeWays = parser.getCollectedWays();
		List<PointData<WayEntity>> pointsList = new ArrayList<PointData<WayEntity>>();
		int i = 0;
		for (WayEntity entity:typeWays) {
			i++;
			if (i % 50000 == 0) {
				System.out.println("Added " + i + " points");
			}
			PointData<WayEntity> pointData = new PointData<WayEntity>(entity.getCenter().x(), entity.getCenter().y(), entity);
			kdTree.add(pointData);
			pointsList.add(pointData);
		}
		List<PointData<WayEntity>> classifiedPoints = pointsList.stream().filter(classifiedPredicate).collect(Collectors.toList());
		return classifiedPoints;
	}
	
	private static Predicate<List<Tag>> getBuildingPredicate() {
		return (tags) -> TypeProvider.isBuilding(tags);
	}

	protected static String getName(File file) {
		String name = file.getName();
		int idx = name.indexOf('.');
		if (idx > 0) {
			return name.substring(0, idx);
		}
		return name;
	}	

	protected static void buildClassifier() {
		try {
			ModelGenerator mg = new ModelGenerator();

			Instances dataset = mg.loadDataset("type_ways.arff");

			Filter filter = new Normalize();

			// divide dataset to train dataset 80% and test dataset 20%
			int trainSize = (int) Math.round(dataset.numInstances() * 0.8);
			int testSize = dataset.numInstances() - trainSize;

			dataset.randomize(new Debug.Random(1));// if you comment this line the accuracy of the model will be droped from
													// 96.6% to 80%

			// Normalize dataset
			filter.setInputFormat(dataset);
			Instances datasetnor = Filter.useFilter(dataset, filter);

			Instances traindataset = new Instances(datasetnor, 0, trainSize);
			Instances testdataset = new Instances(datasetnor, trainSize, testSize);
			J48 m = new J48();
	        m.setBatchSize("100");
	        m.setSeed(0);
	        evaluate(traindataset, testdataset, m);
	        NaiveBayes bayes = new NaiveBayes();
	        bayes.setBatchSize("100");
	        evaluate(traindataset, testdataset, bayes);
	        LogisticBase logisticBase = new LogisticBase();
	        logisticBase.setBatchSize("100");
	        evaluate(traindataset, testdataset, logisticBase);
		} catch (Exception e) {
			Osm2xpLogger.error("Error building classifier", e);
		}
	}
	
	private static void computeGeometryData(BuildingData buildingData, WayEntity way, Map<Long, Node> collectedNodes) {
		int invalid = 0;
			List<Point2D> points = way.getNodes().stream()
				.map(id -> collectedNodes.get(id))
				.filter(node -> node != null)
				.map(node -> new Point2D(node.getLon(), node.getLat()))
				.collect(Collectors.toList());
			int n = points.size();
			ShapeArray2D<Point2D> shapeArray2D = new ShapeArray2D<Point2D>(points);
			way.setBoundingBox(shapeArray2D.boundingBox());
			if (n > 0 && n == way.getNodes().size()) {
				Point2D base = points.get(0);
				double coef = Math.cos(base.y());
				List<Point2D> resList = new ArrayList<>();
				resList.add(new Point2D(0,0));
				double centerLat = base.y() / n;
				double centerLon = base.x() / n;
				for (int i = 1; i < n; i++) {
					Point2D curPt = points.get(i);
					centerLat += curPt.y() / n;
					centerLon += curPt.x() / n;
					resList.add(new Point2D((curPt.x() - base.x()) * coef * WGS_TO_METERS_COEF, (curPt.y() - base.y()) * WGS_TO_METERS_COEF));
				}
				if (resList.size() > 2) {
					LinearRing2D ring2d = new LinearRing2D(resList);
					OptionalDouble max = ring2d.edges().stream().mapToDouble(edge -> edge.length()).max();
					double perimeter = ring2d.length();
					double area = Math.abs(ring2d.area());
					buildingData.setSidesCount(resList.size() - 1);
					buildingData.setPerimeter(perimeter);
					buildingData.setArea(area);
					way.setCenter(centerLon, centerLat);
					if (max.isPresent()) {
						buildingData.setMaxSide(max.getAsDouble());
					}
				} else {
					invalid++;
				}
			} else {
				invalid++;
			}
			
		System.out.println("Missing some nodes for " + invalid + " ways");
		
	}

	protected static void evaluate(Instances traindataset, Instances testdataset, AbstractClassifier classifier) throws Exception {
		System.out.println("Classifier: " + classifier.getClass().getSimpleName());
		classifier.buildClassifier(traindataset);
		Evaluation eval = new Evaluation(traindataset);
		eval.evaluateModel(classifier, testdataset);
		System.out.println(eval.toSummaryString("\nResults\n======\n", false));
	}

}
