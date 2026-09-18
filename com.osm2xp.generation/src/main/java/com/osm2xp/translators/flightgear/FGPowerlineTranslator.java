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
import com.osm2xp.utils.geometry.GeomUtils;

import math.geom2d.Point2D;

/**
 * Places power-line pylons for {@code power=line} / {@code power=minor_line}
 * ways, using the shared pylon models shipped with FlightGear (see
 * {@code example/osm2city/static_types/shared_models.py}, DIR_MODELS_POWER).
 * <p>
 * Model selection mirrors osm2city's {@code _calc_and_map_powerline} heuristics
 * ({@code example/osm2city/pylons.py}): a minor line of short, low segments
 * becomes a wooden pole, otherwise the material/design/cables/height and the
 * maximum segment length pick an H-frame, steel-single or generic tower. Every
 * way node is a pylon and gets its own {@code OBJECT_SHARED_AGL Models/Power/...}
 * STG entry, oriented along the line.
 *
 * @author osm2xp
 */
public class FGPowerlineTranslator extends FlightGearObjectTranslator {

    private static final String POWER_PYLON_WOODEN_POLE_14M = "Models/Power/wooden_pole_14m.ac";
    private static final String POWER_PYLON_H_FRAME_STEEL_25M = "Models/Power/Transmission_25m_Steel_H.ac";
    private static final String POWER_PYLON_H_FRAME_WOOD_25M = "Models/Power/Transmission_25m_Wood_H.ac";
    private static final String POWER_PYLON_STEEL_SINGLE_30M = "Models/Power/Transmission_30m_Steel_Single.ac";
    private static final String POWER_PYLON_GENERIC_PYLON_25M = "Models/Power/generic_pylon_25m.ac";
    private static final String POWER_PYLON_GENERIC_PYLON_50M = "Models/Power/generic_pylon_50m.ac";
    private static final String POWER_PYLON_GENERIC_PYLON_100M = "Models/Power/generic_pylon_100m.ac";

    private final List<OsmPolyline> powerlines = new ArrayList<>();
    private BufferedWriter stgWriter;

    @Override
    public void setStgWriter(BufferedWriter stgWriter) {
        this.stgWriter = stgWriter;
    }

    @Override
    public boolean handlePoly(OsmPolyline osmPolyline) {
        FlightGearOptions options = FlightGearOptionsProvider.getOptions();
        if (!options.isGeneratePowerLines()) {
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
            List<Node> nodes = line.getNodes();
            if (nodes == null || nodes.size() < 2) {
                continue;
            }
            String model = selectPylonModel(line);
            for (int i = 0; i < nodes.size(); i++) {
                Node node = nodes.get(i);
                BufferedWriter writer = resolveWriter(node.getLon(), node.getLat());
                if (writer == null) {
                    continue;
                }
                double heading = pylonHeading(nodes, i);
                try {
                    writer.write(String.format(Locale.ROOT, STG_PATTERN,
                            model, node.getLon(), node.getLat(), heading));
                } catch (IOException e) {
                    Osm2xpLogger.error("Error writing powerline pylon entry", e);
                }
            }
        }
    }

    /**
     * Picks the shared pylon model for a whole power-line way. osm2xp discards per-node tags
     * when building polylines, so the same tags are read from the way itself.
     */
    private String selectPylonModel(OsmPolyline line) {
        boolean minor = "minor_line".equals(line.getTagValue("power"));
        int cables = parseMultiInt(line.getTagValue("cables"));
        double height = parseLength(line.getTagValue("height"));
        String material = line.getTagValue("material");
        String design = line.getTagValue("design");
        boolean isWood = "wood".equals(material);
        boolean isHFrame = design != null && design.toLowerCase().contains("h-frame");
        double maxLength = maxSegmentLength(line);

        if (minor && height <= 25.0 && maxLength <= 250.0) {
            return POWER_PYLON_WOODEN_POLE_14M;
        }
        if (isWood && height < 35.0) {
            return POWER_PYLON_H_FRAME_WOOD_25M;
        }
        if (isHFrame && height < 35.0) {
            return POWER_PYLON_H_FRAME_STEEL_25M;
        }
        if (cables > 0 && cables < 4 && height < 35.0) {
            return POWER_PYLON_STEEL_SINGLE_30M;
        }
        if (height < 35.0 && maxLength < 300.0) {
            return POWER_PYLON_GENERIC_PYLON_25M;
        }
        if (height < 75.0 && maxLength < 500.0) {
            return POWER_PYLON_GENERIC_PYLON_50M;
        }
        if (height > 75.0 && height < 200.0) {
        	return POWER_PYLON_GENERIC_PYLON_100M;
        }
        return POWER_PYLON_GENERIC_PYLON_50M;
    }

    /** Maximum great-circle distance between two consecutive way nodes, in metres. */
    private double maxSegmentLength(OsmPolyline line) {
        List<Node> nodes = line.getNodes();
        double max = 0;
        for (int i = 1; i < nodes.size(); i++) {
            Node prev = nodes.get(i - 1);
            Node cur = nodes.get(i);
            double len = GeomUtils.latLonDistance(prev.getLat(), prev.getLon(), cur.getLat(), cur.getLon());
            if (len > max) {
                max = len;
            }
        }
        return max;
    }

    /**
     * Heading of the pylon at {@code index}: the line bearing for the two
     * endpoints, the middle angle between the incoming and outgoing bearings
     * for interior nodes (mirrors osm2city's {@code _calc_heading_nodes}).
     */
    private double pylonHeading(List<Node> nodes, int index) {
        if (index == 0) {
            return bearing(nodes.get(0), nodes.get(1));
        }
        if (index == nodes.size() - 1) {
            return bearing(nodes.get(nodes.size() - 2), nodes.get(nodes.size() - 1));
        }
        double incoming = bearing(nodes.get(index - 1), nodes.get(index));
        double outgoing = bearing(nodes.get(index), nodes.get(index + 1));
        return middleAngle(incoming, outgoing);
    }

    private double bearing(Node a, Node b) {
        return GeomUtils.getTrueBearing(
                new Point2D(a.getLon(), a.getLat()), new Point2D(b.getLon(), b.getLat()));
    }

    /** Returns the angle halfway between two bearings (osm2city {@code _calc_middle_angle}). */
    private double middleAngle(double a1, double a2) {
        double middle;
        if (a1 == a2) {
            middle = a1;
        } else if (a1 > a2) {
            if (a2 == 0) {
                middle = middleAngle(a1, 360);
            } else {
                middle = a1 - (a1 - a2) / 2;
            }
        } else {
            if (Math.abs(a2 - a1) > 180) {
                middle = middleAngle(a1 + 360, a2);
            } else {
                middle = a2 - (a2 - a1) / 2;
            }
        }
        if (middle >= 360) {
            middle -= 360;
        }
        return middle;
    }

    private static double parseLength(String value) {
        if (value == null) {
            return 0;
        }
        value = value.trim();
        int end = 0;
        while (end < value.length()) {
            char c = value.charAt(end);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+') {
                end++;
            } else {
                break;
            }
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Double.parseDouble(value.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Parses a possibly semicolon-separated integer tag, returning the maximum value (osm2city {@code parse_multi_int_values}). */
    private static int parseMultiInt(String value) {
        if (value == null) {
            return 0;
        }
        int max = 0;
        for (String part : value.split(";")) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            try {
                int parsed = (int) Double.parseDouble(part);
                if (parsed > max) {
                    max = parsed;
                }
            } catch (NumberFormatException e) {
                // ignore unparsable parts
            }
        }
        return max;
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
