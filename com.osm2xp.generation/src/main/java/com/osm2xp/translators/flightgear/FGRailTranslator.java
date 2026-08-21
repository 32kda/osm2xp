package com.osm2xp.translators.flightgear;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.osm2xp.core.logging.Osm2xpLogger;
import com.osm2xp.core.model.osm.Node;
import com.osm2xp.generation.options.FlightGearOptions;
import com.osm2xp.generation.options.FlightGearOptionsProvider;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.translators.FlightGearStgWriterProvider;
import com.osm2xp.translators.IPolyHandler;

import math.geom2d.Point2D;

/**
 * Collects railways and, on completion, writes them as real FlightGear scenery
 * via <code>LINE_FEATURE_LIST</code> list files using the <code>ws30Railway</code>
 * material, following the osm2city approach. Requires the VPB terrain pipeline
 * (FlightGear >= 2020.3) to be rendered.
 *
 * @author osm2xp
 */
public class FGRailTranslator implements IPolyHandler {

    private static final Set<String> ALLOWED_RAILWAY_VALUES = new HashSet<>();
    static {
        ALLOWED_RAILWAY_VALUES.add("rail");
        ALLOWED_RAILWAY_VALUES.add("tram");
        ALLOWED_RAILWAY_VALUES.add("light_rail");
        ALLOWED_RAILWAY_VALUES.add("subway");
        ALLOWED_RAILWAY_VALUES.add("narrow_gauge");
        ALLOWED_RAILWAY_VALUES.add("preserved");
        ALLOWED_RAILWAY_VALUES.add("disused");
    }

    private static final String MATERIAL_RAILWAY = "ws30Railway";

    private final List<OsmPolyline> railways = new ArrayList<>();
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
        String railway = osmPolyline.getTagValue("railway");
        if (railway == null || !ALLOWED_RAILWAY_VALUES.contains(railway)) {
            return false;
        }
        // Skip proposed and abandoned
        if ("proposed".equals(railway) || "abandoned".equals(railway) || "construction".equals(railway)) {
            return false;
        }
        railways.add(osmPolyline);
        return true;
    }

    @Override
    public void translationComplete() {
        if (railways.isEmpty()) {
            return;
        }
        Osm2xpLogger.info("FGRailTranslator: " + railways.size() + " railways collected");
        if (bucketOutputProvider == null) {
            Osm2xpLogger.warning(
                    "FGRailTranslator: no bucket output provider set, skipping LINE_FEATURE_LIST output");
            return;
        }
        for (OsmPolyline rail : railways) {
            if (rail.getNodes() == null || rail.getNodes().size() < 2) {
                continue;
            }
            Point2D center = rail.getCenter();
            FlightGearBucketOutput output = bucketOutputProvider.getBucketOutput(center.x(), center.y());
            if (output == null) {
                continue;
            }
            try {
                writeLineFeatureListHeader(output, MATERIAL_RAILWAY);
                BufferedWriter writer = output.getLineFeatureListWriter(MATERIAL_RAILWAY);
                if (writer == null) {
                    continue;
                }
                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.US, "%.2f 0 1 1 1 1", estimateRailWidth(rail)));
                for (int i = 0; i < rail.getNodes().size(); i++) {
                    Node n = rail.getNodes().get(i);
                    row.append(String.format(Locale.US, " %.6f %.6f", n.getLon(), n.getLat()));
                }
                row.append('\n');
                writer.write(row.toString());
            } catch (IOException e) {
                Osm2xpLogger.error("Error writing railway line feature", e);
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

    /**
     * Track width in metres derived from the gauge tag, mirroring osm2city's
     * <code>_calc_railway_gauge</code> (the track uses 57 out of 128 texture pixels).
     */
    private static double estimateRailWidth(OsmPolyline rail) {
        String gauge = rail.getTagValue("gauge");
        double gaugeMm = 1435;
        if (gauge != null) {
            try {
                gaugeMm = Double.parseDouble(gauge.trim());
            } catch (NumberFormatException e) {
                // keep default gauge
            }
        }
        return gaugeMm / 1000.0 * 128.0 / 57.0;
    }

    @Override
    public String getId() {
        return "railway";
    }

    @Override
    public boolean isTerminating() {
        return true;
    }

    public List<OsmPolyline> getRailways() {
        return railways;
    }
}
