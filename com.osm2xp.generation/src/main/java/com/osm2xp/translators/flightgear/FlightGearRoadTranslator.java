package com.osm2xp.translators.flightgear;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.translators.FlightGearStgWriterProvider;
import com.osm2xp.translators.IPolyHandler;

import math.geom2d.Point2D;

/**
 * Collects highways and, on completion, writes them as real FlightGear scenery
 * via <code>LINE_FEATURE_LIST</code> list files (one gzipped file per bucket
 * and material), following the osm2city approach. Requires the VPB terrain
 * pipeline (FlightGear >= 2020.3) to be rendered.
 *
 * @author osm2xp
 */
public class FGRoadTranslator implements IPolyHandler {

    private static final String[] ALLOWED_HIGHWAY_TYPES = {
        "motorway", "trunk", "primary", "secondary", "tertiary",
        "unclassified", "residential", "service", "living_street", "road",
        "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link"
    };

    private static final String MATERIAL_ROAD = "ws30Road";
    private static final String MATERIAL_FREEWAY = "ws30Freeway";

    private final List<OsmPolyline> roads = new ArrayList<>();
    private BufferedWriter stgWriter;
    private FlightGearStgWriterProvider stgWriterProvider;
    private FlightGearBucketOutputProvider bucketOutputProvider;

    @Override
    public void setStgWriter(BufferedWriter stgWriter) {
        this.stgWriter = stgWriter;
    }

    @Override
    public void setStgWriterProvider(FlightGearStgWriterProvider stgWriterProvider) {
        this.stgWriterProvider = stgWriterProvider;
    }

    @Override
    public void setBucketOutputProvider(FlightGearBucketOutputProvider bucketOutputProvider) {
        this.bucketOutputProvider = bucketOutputProvider;
    }

    @Override
    public boolean handlePoly(OsmPolyline osmPolyline) {
        FlightGearOptions options = FlightGearOptionsProvider.getOptions();
        if (!options.isGenerateTransportation()) {
            return false;
        }
        String highway = osmPolyline.getTagValue("highway");
        if (highway == null) {
            return false;
        }
        for (String allowed : ALLOWED_HIGHWAY_TYPES) {
            if (allowed.equals(highway)) {
                roads.add(osmPolyline);
                return true;
            }
        }
        return false;
    }

    @Override
    public void translationComplete() {
        if (roads.isEmpty()) {
            return;
        }
        Osm2xpLogger.info("FGRoadTranslator: " + roads.size() + " roads collected");
        if (bucketOutputProvider == null) {
            Osm2xpLogger.warning(
                    "FGRoadTranslator: no bucket output provider set, skipping LINE_FEATURE_LIST output");
            return;
        }
        for (OsmPolyline road : roads) {
            if (road.getNodes() == null || road.getNodes().size() < 2) {
                continue;
            }
            Point2D center = road.getCenter();
            FlightGearBucketOutput output = bucketOutputProvider.getBucketOutput(center.x(), center.y());
            if (output == null) {
                continue;
            }
            String highwayType = road.getTagValue("highway");
            String material = resolveMaterial(highwayType);
            try {
                writeLineFeatureListHeader(output, material);
                BufferedWriter writer = output.getLineFeatureListWriter(material);
                if (writer == null) {
                    continue;
                }
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.US, "%.2f 0 1 1 1 1", estimateWidth(highwayType)));
                for (int i = 0; i < road.getNodes().size(); i++) {
                    Node n = road.getNodes().get(i);
                    row.append(String.format(Locale.US, " %.6f %.6f", n.getLon(), n.getLat()));
                }
                row.append('\n');
                writer.write(row.toString());
            } catch (IOException e) {
                Osm2xpLogger.error("Error writing road line feature", e);
            }
        }
    }

    private static void writeLineFeatureListHeader(FlightGearBucketOutput output, String material)
            throws IOException {
        if (!output.isLineFeatureListHeaderWritten(material)) {
            BufferedWriter stgWriter = output.getStgWriter();
            if (stgWriter != null) {
                stgWriter.write("LINE_FEATURE_LIST " + output.getLineFeatureListFileName(material)
                        + " " + material + "\n");
            }
            output.setLineFeatureListHeaderWritten(material);
        }
    }

    private static String resolveMaterial(String highwayType) {
        if (highwayType != null) {
            switch (highwayType) {
                case "motorway":
                case "trunk":
                case "motorway_link":
                case "trunk_link":
                    return MATERIAL_FREEWAY;
                default:
                    break;
            }
        }
        return MATERIAL_ROAD;
    }

    @Override
    public String getId() {
        return "road";
    }

    @Override
    public boolean isTerminating() {
        return true;
    }

    public static double estimateWidth(String highwayType) {
        if (highwayType == null) return 6.0;
        switch (highwayType) {
            case "motorway": case "trunk": return 12.0;
            case "primary": case "secondary": return 8.0;
            case "tertiary": case "unclassified": case "road": return 6.0;
            case "residential": case "living_street": case "service": return 4.0;
            default: return 6.0;
        }
    }

    public List<OsmPolyline> getRoads() {
        return roads;
    }
}
