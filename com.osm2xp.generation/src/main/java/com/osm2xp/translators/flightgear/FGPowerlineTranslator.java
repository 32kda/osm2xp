package com.osm2xp.translators.flightgear;

import java.io.BufferedWriter;
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

public class FGPowerlineTranslator implements IPolyHandler {

    private final List<OsmPolyline> powerlines = new ArrayList<>();
    private BufferedWriter stgWriter;
    private FlightGearStgWriterProvider stgWriterProvider;

    @Override
    public void setStgWriter(BufferedWriter stgWriter) {
        this.stgWriter = stgWriter;
    }

    @Override
    public void setStgWriterProvider(FlightGearStgWriterProvider stgWriterProvider) {
        this.stgWriterProvider = stgWriterProvider;
    }

    @Override
    public boolean handlePoly(OsmPolyline osmPolyline) {
        FlightGearOptions options = FlightGearOptionsProvider.getOptions();
        if (!options.isGenerateTransportation()) {
            return false;
        }
        String power = osmPolyline.getTagValue("power");
        if (power == null) {
            return false;
        }
        // Accept line and minor_line
        if (!"line".equals(power) && !"minor_line".equals(power)) {
            return false;
        }
        powerlines.add(osmPolyline);
        return true;
    }

    @Override
    public void translationComplete() {
        if (powerlines.isEmpty()) {
            return;
        }
        Osm2xpLogger.info("FGPowerlineTranslator: " + powerlines.size() + " powerlines collected");
        for (OsmPolyline line : powerlines) {
            if (line.getNodes() == null || line.getNodes().size() < 2) continue;
            Point2D center = line.getCenter();
            BufferedWriter writer = resolveWriter(center.x(), center.y());
            if (writer == null) {
                continue;
            }
            String powerType = line.getTagValue("power");
            String cables = line.getTagValue("cables");
            String voltage = line.getTagValue("voltage");
            String wires = line.getTagValue("wires");
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US,
                "# powerline %d: type=%s cables=%s voltage=%s wires=%s nodes=%d center=%.6f,%.6f\n",
                line.getId(), powerType,
                cables != null ? cables : "?",
                voltage != null ? voltage : "?",
                wires != null ? wires : "?",
                line.getNodes().size(), center.y(), center.x()));
            // First and last node are pylon locations
            Node first = line.getNodes().get(0);
            Node last = line.getNodes().get(line.getNodes().size() - 1);
            sb.append(String.format(Locale.US,
                "#   pylons: start=%.6f,%.6f end=%.6f,%.6f\n",
                first.getLat(), first.getLon(), last.getLat(), last.getLon()));
            // All node coordinates
            sb.append("# coords:");
            for (int i = 0; i < line.getNodes().size(); i++) {
                Node n = line.getNodes().get(i);
                sb.append(String.format(Locale.US, " %.6f,%.6f", n.getLat(), n.getLon()));
            }
            sb.append("\n");
            try {
                writer.write(sb.toString());
            } catch (Exception e) {
                Osm2xpLogger.error("Error writing powerline entries", e);
            }
        }
    }

    private BufferedWriter resolveWriter(double lon, double lat) {
        if (stgWriterProvider != null) {
            return stgWriterProvider.getStgWriter(lon, lat);
        }
        return stgWriter;
    }

    @Override
    public String getId() {
        return "powerline";
    }

    @Override
    public boolean isTerminating() {
        return true;
    }

    public List<OsmPolyline> getPowerlines() {
        return powerlines;
    }
}
