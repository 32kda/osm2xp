package com.osm2xp.translators.impl;

import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang.StringUtils;

import com.osm2xp.core.exceptions.Osm2xpBusinessException;
import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.model.osm.polygon.OsmPolygon;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.stats.CountStats;
import com.osm2xp.stats.StatsProvider;
import com.osm2xp.translators.FlightGearStgWriterProvider;
import com.osm2xp.translators.IPolyHandler;
import com.osm2xp.translators.ITranslator;
import com.osm2xp.translators.flightgear.FGBuildingObjectTranslator;
import com.osm2xp.translators.flightgear.FGRailTranslator;
import com.osm2xp.translators.flightgear.FGRoadTranslator;
import com.osm2xp.translators.flightgear.FGPowerlineTranslator;
import com.osm2xp.translators.flightgear.FGRulesObjectTranslator;
import com.osm2xp.translators.flightgear.FlightGearBuildingAnalyzer;
import com.osm2xp.translators.flightgear.FlightGearBuildingListEntry;
import com.osm2xp.translators.flightgear.FlightGearBucket;
import com.osm2xp.translators.flightgear.FlightGearBucketOutput;
import com.osm2xp.translators.flightgear.FlightGearChimneyTranslator;
import com.osm2xp.translators.flightgear.FlightGearCoolingTowerTranslator;
import com.osm2xp.translators.flightgear.FlightGearElevProber;
import com.osm2xp.translators.flightgear.FlightGearModelsProvider;
import com.osm2xp.translators.flightgear.FlightGearSiloTranslator;
import com.osm2xp.translators.flightgear.TransportationData;
import com.osm2xp.translators.flightgear.TransportationParsingService;
import com.osm2xp.translators.flightgear.TransportationValidator;
import com.osm2xp.translators.flightgear.TransportationValidator.ValidationReport;
import com.osm2xp.utils.FilesUtils;
import com.osm2xp.utils.geometry.GeomUtils;
import com.osm2xp.utils.osm.OsmUtils;

import math.geom2d.Box2D;
import math.geom2d.Point2D;

public class FlightGearTranslatorImpl implements ITranslator {
    private Point2D currentTile;
    private String folderPath;
    private static final String FLIGHT_GEAR_OBJECT_DECLARATION = "OBJECT_SHARED_AGL {0} {1} {2} {3} {4} {5} {6}\n";
    private final Random random = new Random();

    private final List<IPolyHandler> polyHandlers = new ArrayList<>();
    private final List<IPolyHandler> objectHandlers = new ArrayList<>();
    private final TransportationParsingService transportationService = new TransportationParsingService();
    private final TransportationValidator transportationValidator = new TransportationValidator();
    private ValidationReport transportReport;

    private final Map<Long, FlightGearBucketOutput> bucketOutputs = new HashMap<>();
    private FlightGearStgWriterProvider stgWriterProvider;
    private FlightGearElevProber elevProber;

    public FlightGearTranslatorImpl(Point2D currentTile, String folderPath) {
        super();
        this.currentTile = currentTile;
        this.folderPath = folderPath;

        // Register poly handlers (order matters — most specific first)
        polyHandlers.add(new FGRoadTranslator());
        polyHandlers.add(new FGRailTranslator());
        polyHandlers.add(new FGPowerlineTranslator());

        // 3D object handlers (rule-based, special and building-size), following the X-Plane translators
		objectHandlers.add(new FlightGearCoolingTowerTranslator());
		objectHandlers.add(new FlightGearChimneyTranslator());
		objectHandlers.add(new FlightGearSiloTranslator());
		objectHandlers.add(new FGRulesObjectTranslator());
        objectHandlers.add(new FGBuildingObjectTranslator());
    }

    @Override
    public void processNode(Node node) throws Osm2xpBusinessException {
        if (node == null || node.getTags() == null || node.getTags().isEmpty()) {
            return;
        }
        if (!GeomUtils.compareCoordinates(currentTile, node)) {
            return;
        }
        FlightGearOptions options = FlightGearOptionsProvider.getOptions();
        if (!options.isGenerateObjects()) {
            return;
        }
        for (IPolyHandler objectHandler : objectHandlers) {
            if (objectHandler instanceof FGRulesObjectTranslator) {
                if (((FGRulesObjectTranslator) objectHandler).handleNode(node)) {
                    StatsProvider.getTileStats(currentTile, true).incCount("object");
                    StatsProvider.getCommonStats().incCount("object");
                    return;
                }
            }
        }
    }

    @Override
    public void processPolyline(OsmPolyline osmPolyline) throws Osm2xpBusinessException {
        if (osmPolyline == null || osmPolyline.getNodes() == null) {
            return;
        }
        FlightGearOptions options = FlightGearOptionsProvider.getOptions();

        // 1. Try poly handlers (roads, railways, powerlines)
        if (options.isGenerateTransportation()) {
            for (IPolyHandler handler : polyHandlers) {
                if (handler.handlePoly(osmPolyline)) {
                    StatsProvider.getTileStats(currentTile, true).incCount(handler.getId());
                    StatsProvider.getCommonStats().incCount(handler.getId());
                    transportationService.parsePolyline(osmPolyline);
                    return;
                }
            }
        }

        // 2. 3D objects (rule-based and building-size), mirroring the X-Plane object translators
        for (IPolyHandler objectHandler : objectHandlers) {
            if (objectHandler.handlePoly(osmPolyline)) {
                return;
            }
        }

        // 3. Building analysis for BUILDING_LIST
        if (options.isGenerateBuildings() && osmPolyline instanceof OsmPolygon
                && OsmUtils.isBuilding(osmPolyline.getTags())) {
            processBuilding((OsmPolygon) osmPolyline);
        }
    }

    private void processBuilding(OsmPolygon polygon) throws Osm2xpBusinessException {
        FlightGearOptions options = FlightGearOptionsProvider.getOptions();
        FlightGearBuildingAnalyzer analyzer = new FlightGearBuildingAnalyzer(options, random, elevProber);
        FlightGearBuildingListEntry entry = analyzer.analyze(polygon);
        if (entry != null) {
            StatsProvider.getTileStats(currentTile, true).incCount("building");
            StatsProvider.getCommonStats().incCount("building");
            try {
                FlightGearBucketOutput output = bucketOutputFor(entry.getLon(), entry.getLat());
                BufferedWriter buildingListWriter = output.getBuildingListWriter();
                if (buildingListWriter != null) {
                    buildingListWriter.write(entry.formatDataLine(
                            output.getBucket().getCenterLon(), output.getBucket().getCenterLat()));
                }
                if (!output.isBuildingListHeaderWritten()) {
                    double bucketCenterLon = output.getBucket().getCenterLon();
                    double bucketCenterLat = output.getBucket().getCenterLat();
                    String header = String.format(Locale.US,
                            "BUILDING_LIST " + output.getBuildingListFileName() + " OSMBuildings %.6f %.6f 0.00\n",
                            bucketCenterLon, bucketCenterLat);
                    BufferedWriter stgWriter = output.getStgWriter();
                    if (stgWriter != null) {
                        stgWriter.write(header);
                    }
                    output.setBuildingListHeaderWritten(true);
                }
            } catch (Exception e) {
                throw new Osm2xpBusinessException("Error writing building list entry", e);
            }
        }
    }

    private FlightGearBucketOutput bucketOutputFor(double lon, double lat) {
        // Clamp to the interior of this tile so a coordinate exactly on the tile
        // boundary (e.g. lon == tile.x + 1.0, produced by the tile clipper) resolves
        // to THIS tile's bucket rather than the neighbouring tile's. Without this, two
        // tiles can open the same .stg file (the second FileOutputStream truncates the
        // first), corrupting the STG with NUL-padded holes.
        double minLon = currentTile.x();
        double maxLon = minLon + 1.0 - 1e-6;
        double minLat = currentTile.y();
        double maxLat = minLat + 1.0 - 1e-6;
        lon = Math.min(Math.max(lon, minLon), maxLon);
        lat = Math.min(Math.max(lat, minLat), maxLat);
        FlightGearBucket bucket = FlightGearBucket.bucketFor(lon, lat);
        FlightGearOptions options = FlightGearOptionsProvider.getOptions();
        return bucketOutputs.computeIfAbsent(bucket.getIndex(),
                key -> new FlightGearBucketOutput(new File(folderPath), bucket, options.isGenerateBuildings()));
    }

    @Override
    public void complete() {
        // Complete poly handlers
        for (IPolyHandler handler : polyHandlers) {
            handler.translationComplete();
        }

        for (IPolyHandler objectHandler : objectHandlers) {
            objectHandler.translationComplete();
        }

        for (FlightGearBucketOutput output : bucketOutputs.values()) {
            output.close();
        }

        if (elevProber != null) {
            elevProber.close();
            elevProber = null;
        }

        // Validate and report transportation data
        FlightGearOptions options = FlightGearOptionsProvider.getOptions();
        if (options.isGenerateTransportation()) {
            TransportationData data = transportationService.getData();
            transportReport = transportationValidator.validate(data);
            Osm2xpLogger.info("Transportation validation complete:\n" + transportReport.summary());

            File reportFile = new File(folderPath, "transportation_report_" + currentTile.y() + "_" + currentTile.x() + ".txt");
            try {
                FilesUtils.writeTextToFile(reportFile, transportReport.summary(), false);
            } catch (Exception e) {
                Osm2xpLogger.error("Error writing transportation report", e);
            }
        }

        // Output generated features stats
        CountStats tileStats = StatsProvider.getTileStats(currentTile, false);
        String tileSummary = tileStats != null ? tileStats.getSummary() : "";
        if (!tileSummary.isEmpty()) {
            System.out.println("Tile " + currentTile + ", generated: " + tileSummary.toLowerCase());
        } else {
            System.out.println("Tile " + currentTile + " is empty, no generation stats");
        }

        Osm2xpLogger.info("FlightGear file finished.");
    }

    @Override
    public void init() {
        FlightGearOptions options = FlightGearOptionsProvider.getOptions();
        File parentDir = new File(folderPath);
        parentDir.mkdirs();
        bucketOutputs.clear();
		stgWriterProvider = (lon, lat) -> bucketOutputFor(lon, lat).getStgWriter();
		for (IPolyHandler handler : polyHandlers) {
			handler.setStgWriterProvider(stgWriterProvider);
			handler.setBucketOutputProvider(this::bucketOutputFor);
		}
		for (IPolyHandler objectHandler : objectHandlers) {
			objectHandler.setStgWriterProvider(stgWriterProvider);
			objectHandler.setBucketOutputProvider(this::bucketOutputFor);
		}
		FlightGearModelsProvider.ensureModelsCopied(parentDir);

        if (options.isGenerateTransportation()) {
            transportationService.reset();
            transportReport = null;
        }

        elevProber = createElevProber(options);

        Osm2xpLogger.info("Starting FlightGear file for tile "
                + this.currentTile.y() + "/" + this.currentTile.x() + ".");
    }

    private FlightGearElevProber createElevProber(FlightGearOptions options) {
        if (!options.isGenerateBuildings() || !options.isGenerateBuildingsElevation()) {
            return null;
        }
        String fgelevPath = options.getFgelevPath();
        File fgelevBinary = StringUtils.isNotBlank(fgelevPath) ? new File(fgelevPath) : null;
        FlightGearElevProber prober = new FlightGearElevProber(fgelevBinary, options.getFlightGearSceneryPath());
        if (prober.isDisabled()) {
            Osm2xpLogger.warning("Building elevation probing is disabled: configure fgelevPath and "
                    + "flightGearSceneryPath for terrain-accurate building elevations.");
        }
        return prober;
    }

    @Override
    public boolean mustStoreNode(Node node) {
        return GeomUtils.compareCoordinates(currentTile, node);
    }

    @Override
    public boolean mustProcessPolyline(List<Tag> tags) {
        return true;
    }

    @Override
    public void processBoundingBox(Box2D bbox) {
    }

    @Override
    public int getMaxHoleCount(List<Tag> tags) {
        return Integer.MAX_VALUE;
    }

    public TransportationData getTransportationData() {
        return transportationService.getData();
    }

    public ValidationReport getTransportReport() {
        return transportReport;
    }

    public List<IPolyHandler> getPolyHandlers() {
        return polyHandlers;
    }

    public Map<Long, FlightGearBucketOutput> getBucketOutputs() {
        return bucketOutputs;
    }
}
