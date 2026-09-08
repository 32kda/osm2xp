package com.osm2xp.translators.airfield;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.osm2xp.core.exceptions.Osm2xpBusinessException;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.CompositeTagSet;
import com.osm2xp.core.model.osm.IHasTags;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.model.osm.polygon.OsmPolylineFactory;
import com.osm2xp.stats.StatsProvider;
import com.osm2xp.translators.ISpecificTranslator;
import com.osm2xp.generation.options.XPlaneOptionsProvider;
import com.osm2xp.generation.paths.PathsService;
import com.osm2xp.utils.FilesUtils;
import com.osm2xp.utils.osm.OsmUtils;

import org.apache.commons.lang.StringUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.index.kdtree.KdNode;
import org.locationtech.jts.index.kdtree.KdTree;

import math.geom2d.Box2D;
import math.geom2d.Point2D;
import math.geom2d.line.Line2D;

public class XPAirfieldTranslationAdapter implements ISpecificTranslator {
	
	private static final String RUNWAY_TAG = "runway";
	private static final String HELIPAD_TAG = "helipad";
	private static final String AERODROME_TAG = "aerodrome";
	private static final String HELIPORT_TAG = "heliport";
	private List<AirfieldData> airfieldList = new ArrayList<AirfieldData>();
	private List<OsmPolyline> runwayList = new ArrayList<>();
	private List<OsmPolyline> apronAreasList = new ArrayList<>();
	private List<OsmPolyline> taxiLanesList = new ArrayList<>();
	private List<OsmPolygon> heliAreasList = new ArrayList<>();
	private List<Node> helipadsList = new ArrayList<>();
	private File workFolder;
	private KdTree orphanRunwaysTree = new KdTree();
	private LocalGeonameProvider provider;
	private Box2D bbox;
	private boolean perAirportAptDat;

	public XPAirfieldTranslationAdapter(String outputFolder) {
		this(outputFolder, false);
	}

	public XPAirfieldTranslationAdapter(String outputFolder, boolean perAirportAptDat) {
		workFolder = new File(outputFolder);
		this.perAirportAptDat = perAirportAptDat;
	}

	protected List<AirfieldData> getAirfieldList() {
		return airfieldList;
	}

	protected File getWorkFolder() {
		return workFolder;
	}
	
	protected boolean handlePoly(OsmPolyline osmPolyline) {
		if (!XPlaneOptionsProvider.getOptions().getAirfieldOptions().isGenerateAirfields()) {
			return false;
		}
		String wayType = osmPolyline.getTagValue("aeroway");
		if (AERODROME_TAG.equalsIgnoreCase(wayType) || HELIPORT_TAG.equalsIgnoreCase(wayType)) {
			addAirfiled(osmPolyline);
			return true;
		} else if (RUNWAY_TAG.equalsIgnoreCase(wayType)) {
			runwayList.add(osmPolyline);
		} else if ("apron".equalsIgnoreCase(wayType) || "taxiway".equalsIgnoreCase(wayType) || "taxilane".equalsIgnoreCase(wayType)) {
			if (osmPolyline.getPolyline().isClosed()) {
				apronAreasList.add(osmPolyline);
			} else {
				taxiLanesList.add(osmPolyline);
			}
		} else if (HELIPAD_TAG.equalsIgnoreCase(wayType)) {
			if (osmPolyline instanceof OsmPolygon) {
				heliAreasList.add((OsmPolygon) osmPolyline);
			}
		}
		return false;
	}

	protected void addAirfiled(IHasTags osmEntity) {
		AirfieldData data;
		if (osmEntity instanceof OsmPolyline) {
			data = new PolyAirfieldData((OsmPolyline) osmEntity);
		} else {
			data = new PointAirfieldData((Node) osmEntity);
			if (HELIPORT_TAG.equalsIgnoreCase(osmEntity.getTagValue("type"))) {
				((PointAirfieldData) data).setMaxRadius(500);
			}
		}
		checkGetAdditinalInfo(data);
		airfieldList.add(data);
	}

	protected void checkGetAdditinalInfo(AirfieldData data) {
		Point2D areaCenter = data.getAreaCenter();
		if (!data.hasActualElevation() &&  XPlaneOptionsProvider.getOptions().getAirfieldOptions().isTryGetElev()) {
			Double elevation = ElevationProvidingService.getInstance().getElevation(areaCenter, true);
			if (elevation != null) {
				data.setElevation((int) Math.round(elevation));
			}
		}
		if (data.getName() == null && data.getICAO() == null) {
			String name = null;
			if (getLocalProvider() != null) {
				name = getLocalProvider().getName(areaCenter.x(), areaCenter.y());
			}
			if (name == null && XPlaneOptionsProvider.getOptions().getAirfieldOptions().isTryGetName()) {
				name = GeonameProvidingService.getInstance().getMeta(areaCenter, true);
			}
			if (name != null) {
				data.setName(name);
			}
		}
	}
	
	protected LocalGeonameProvider getLocalProvider() {
		if (provider == null) {
			File basicFolder = PathsService.getPathsProvider().getBasicFolder();
			File indexFile = new File(basicFolder, "geo/index.dat");
			if (indexFile.isFile()) {
				try {
					provider = new LocalGeonameProvider(bbox, indexFile);
				} catch (Exception e) {
					Osm2xpLogger.error("Unable to use local geoname index");
				}
			}
		}
		return provider;
	}

	@Override
	public void processWays(long wayId, List<Tag> tags, Geometry originalGeometry,
			List<? extends Geometry> fixedGeometries) {
		if (!OsmUtils.isAeroway(tags)) {
			return;
		}
		for (Geometry geometry : fixedGeometries) {
			List<OsmPolyline> polylines = OsmPolylineFactory.createPolylinesFromJTSGeometry(wayId, tags, geometry, false);
			for (OsmPolyline osmPolyline : polylines) {
				handlePoly(osmPolyline);
			}
		}
	}

	@Override
	public void init() {
		// Do nothing
	}

	@Override
	public void complete() {
		bindAirways(airfieldList.stream().filter(data -> data instanceof PolyAirfieldData).collect(Collectors.toList())); //Poly-based airfileds should be processed first - they are more precise, than point-based ones
		bindAirways(airfieldList.stream().filter(data -> data instanceof PointAirfieldData).collect(Collectors.toList()));
		for (OsmPolyline line : runwayList) {
			Point2D center = line.getCenter();
			orphanRunwaysTree.insert(new Coordinate(center.x(), center.y()), line);
		}
		
		Set<OsmPolyline> added = Sets.newHashSet();
		
		List<AirfieldData> synteticAirfields = Lists.newArrayList();
		for (OsmPolyline line : runwayList) {
			if (added.contains(line)) {
				continue;
			}
			Point2D center = line.getCenter();
			double xDist = 2.0 / (111 * Math.cos(center.y())); //2km in each direction for now
			double yDist = 2.0 / 111;
			Envelope envelope = new Envelope(center.x() - xDist, center.x() + xDist, center.y() - yDist, center.y() + yDist);
			List<KdNode> result =  Lists.newArrayList();
			orphanRunwaysTree.query(envelope, result);
			if (result.size() > 1) { //1 means this line itself was found
				List<OsmPolyline> nearRunways = result.stream().map(node -> (OsmPolyline) node.getData()).collect(Collectors.toList());
				synteticAirfields.add(createSyntheticAirfield(nearRunways));
				added.addAll(nearRunways);
			}
		}
		runwayList.removeAll(added);
		bindAdditions(synteticAirfields);
		airfieldList.addAll(synteticAirfields);
		
		if (XPlaneOptionsProvider.getOptions().getAirfieldOptions().isTryGetElev()) {		
			ElevationProvidingService.getInstance().finish();
			airfieldList.stream().filter(data -> !data.hasActualElevation()).forEach(data -> {
				Double elevation = ElevationProvidingService.getInstance().getElevation(data.getAreaCenter(), false);
				if (elevation != null) {
					data.setElevation((int) Math.round(elevation));
				}
			});
		}
		if (XPlaneOptionsProvider.getOptions().getAirfieldOptions().isTryGetName()) {
			GeonameProvidingService.getInstance().finish();
			airfieldList.stream().filter(data -> data.getName() == null && data.getICAO() == null).forEach(data -> {
				String name = GeonameProvidingService.getInstance().getMeta(data.getAreaCenter(), false);
				if (name != null) {
					data.setName(name);
				}
			});
		}
		boolean writeAsMainAirfield = XPlaneOptionsProvider.getOptions().getAirfieldOptions().isUseSingleAptAsMain() && (airfieldList.size() + runwayList.size() == 1); //If we have only one airport/only one runway - write it as main airfield of scenario
		File airfieldOutputFolder = perAirportAptDat ? workFolder : (writeAsMainAirfield ? workFolder : getMultiAirfieldsFolder());
		XPAirfieldOutput airfieldOutput = createAirfieldOutput(airfieldOutputFolder, writeAsMainAirfield);
		for (AirfieldData airfieldData : airfieldList) {
			if (XPlaneOptionsProvider.getOptions().getAirfieldOptions().getIgnoredAirfields().contains(airfieldData.getICAO())) { //We can't check this at earlier stage since we need to ignore associated runways and other stuff as well
				continue;
			}
			airfieldOutput.writeAirfield(airfieldData);
		}
		for (OsmPolyline runway : runwayList) {
			RunwayData data = new RunwayData(runway);
			if (data.getName() == null && XPlaneOptionsProvider.getOptions().getAirfieldOptions().isTryGetName()) {
				Line2D line = data.getRunwayLine();
				String name = GeonameProvidingService.getInstance().getValueSync(Point2D.centroid(new Point2D[] {line.p1, line.p2}));
				if (name != null) {
					data.setName(name);
				}
			}
			airfieldOutput.writeSingleRunway(data);
		}
		
		StatsProvider.getCommonStats().setCount("Airfields", airfieldList.size());
		StatsProvider.getCommonStats().setCount("Separate Runways", runwayList.size());
	}

	protected XPAirfieldOutput createAirfieldOutput(File airfieldOutputFolder, boolean writeAsMainAirfield) {
		return new XPAirfieldOutput(airfieldOutputFolder, writeAsMainAirfield, perAirportAptDat);
	}

	protected File getMultiAirfieldsFolder() {
		File airfieldsFolder = new File(workFolder.getParentFile(), workFolder.getName() + "_airfields");
		if (airfieldsFolder.exists()) {
			FilesUtils.deleteDirectory(airfieldsFolder);
		}
		return airfieldsFolder;
	}

	protected void bindAirways(List<AirfieldData> airfieldList) {
		//First, try getting registered airfield areafor this
		for (Iterator<OsmPolyline> iterator = runwayList.iterator(); iterator.hasNext();) { //Check runways matching airports
			OsmPolyline runway = (OsmPolyline) iterator.next();
			for (AirfieldData airfieldData : airfieldList) {
				if (airfieldData.containsPolyline(runway)) {
					airfieldData.addRunway(runway);
					iterator.remove();
					break;
				}
			}
		}
		bindAdditions(airfieldList);
		
	}

	protected void bindAdditions(List<AirfieldData> airfieldList) {
		for (Iterator<OsmPolyline> iterator = apronAreasList.iterator(); iterator.hasNext();) { //Check apron areas matching airports
			OsmPolyline area = (OsmPolyline) iterator.next();
			for (AirfieldData airfieldData : airfieldList) {
				if (airfieldData.containsPolyline(area)) {
					airfieldData.addApronArea(area);
					iterator.remove();
					break;
				}
			}
		}
		for (Iterator<OsmPolyline> iterator = taxiLanesList.iterator(); iterator.hasNext();) { //Check apron areas matching airports
			OsmPolyline lane = (OsmPolyline) iterator.next();
			for (AirfieldData airfieldData : airfieldList) {
				if (airfieldData.containsPolyline(lane)) {
					airfieldData.addTaxiLane(lane);
					iterator.remove();
					break;
				}
			}
		}
		for (Iterator<OsmPolygon> iterator = heliAreasList.iterator(); iterator.hasNext();) { //Check apron areas matching airports
			OsmPolygon lane = (OsmPolygon) iterator.next();
			for (AirfieldData airfieldData : airfieldList) {
				if (airfieldData.containsPolyline(lane)) {
					airfieldData.addHeliArea(lane);
					iterator.remove();
					break;
				}
			}
		}
		for (Iterator<Node> iterator = helipadsList.iterator(); iterator.hasNext();) { //Check helipad areas matching airports
			Node helipad = (Node) iterator.next();
			for (AirfieldData airfieldData : airfieldList) {
				if (airfieldData.contains(helipad.getLon(), helipad.getLat())) {
					airfieldData.addHelipad(new HelipadData(helipad.getLon(), helipad.getLat()));
					iterator.remove();
					break;
				}
			}
		}
	}

	protected AirfieldData createSyntheticAirfield(List<OsmPolyline> nearRunways) {
		double length = 0;
		OsmPolyline longest = null;
		String name = null;
		List<RunwayData> rwyList = Lists.newArrayList();
		CompositeTagSet tagsHolder = new CompositeTagSet();
		for (OsmPolyline osmPolyline : nearRunways) {
			RunwayData runwayData = new RunwayData(osmPolyline);
			if (osmPolyline.getPolyline().length() > length) {
				length = osmPolyline.getPolyline().length();
				longest = osmPolyline;
				rwyList.add(runwayData);
			}
			if (runwayData.getName() != null && !runwayData.getName().equals(runwayData.getId())) {
				name = runwayData.getName();
			}
			tagsHolder.addTags(osmPolyline);
		}
		PointAirfieldData airfieldData = new PointAirfieldData(longest.getCenter(), tagsHolder);
		if (!StringUtils.isEmpty(name)) {
			airfieldData.setName(name);
		}
		for (RunwayData runwayData : rwyList) {
			if (runwayData.hasActualElevation()) {
				airfieldData.setElevation(runwayData.getElevation());
				break;
			}
		}
		airfieldData.addRunways(rwyList);
		checkGetAdditinalInfo(airfieldData);
		return airfieldData;
	}

	@Override
	public void processNode(Node node) throws Osm2xpBusinessException {
		String type = node.getTagValue("aeroway");
		if (HELIPAD_TAG.equals(type)) {
			helipadsList.add(node);
		}
		if (AERODROME_TAG.equalsIgnoreCase(type) || HELIPORT_TAG.equalsIgnoreCase(type)) {
			addAirfiled(node);
		}
	}
	

	@Override
	public void processBoundingBox(Box2D bbox) {
		this.bbox = bbox;
	}

	@Override
	public boolean mustProcessPolyline(List<Tag> tags) {
		return OsmUtils.isAeroway(tags);
	}

	@Override
	public boolean mustStoreNode(Node node) {
		return true;
	}

}
