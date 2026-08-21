package com.osm2xp.translators.flightgear;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.osm2xp.core.model.osm.Node;
import com.osm2xp.core.model.osm.Tag;
import com.osm2xp.model.osm.polygon.OsmPolyline;
import com.osm2xp.translators.flightgear.TransportationData.FeatureType;
import com.osm2xp.translators.flightgear.TransportationData.TransportNode;
import com.osm2xp.translators.flightgear.TransportationData.TransportSegment;

public class TransportationParsingService {

    private static final Set<String> HIGHWAY_VALUES = new HashSet<>();
    private static final Set<String> RAILWAY_VALUES = new HashSet<>();
    private static final Set<String> POWER_VALUES = new HashSet<>();
    private static final Set<String> CHIMNEY_KEYS = new HashSet<>();
    private static final Set<String> BRIDGE_VALUES = new HashSet<>();

    static {
        HIGHWAY_VALUES.add("motorway");
        HIGHWAY_VALUES.add("trunk");
        HIGHWAY_VALUES.add("primary");
        HIGHWAY_VALUES.add("secondary");
        HIGHWAY_VALUES.add("tertiary");
        HIGHWAY_VALUES.add("unclassified");
        HIGHWAY_VALUES.add("residential");
        HIGHWAY_VALUES.add("service");
        HIGHWAY_VALUES.add("living_street");
        HIGHWAY_VALUES.add("road");
        HIGHWAY_VALUES.add("motorway_link");
        HIGHWAY_VALUES.add("trunk_link");
        HIGHWAY_VALUES.add("primary_link");
        HIGHWAY_VALUES.add("secondary_link");
        HIGHWAY_VALUES.add("tertiary_link");

        RAILWAY_VALUES.add("rail");
        RAILWAY_VALUES.add("tram");
        RAILWAY_VALUES.add("light_rail");
        RAILWAY_VALUES.add("subway");
        RAILWAY_VALUES.add("narrow_gauge");
        RAILWAY_VALUES.add("preserved");
        RAILWAY_VALUES.add("disused");

        POWER_VALUES.add("line");
        POWER_VALUES.add("minor_line");

        CHIMNEY_KEYS.add("man_made");
        CHIMNEY_KEYS.add("building");

        BRIDGE_VALUES.add("yes");
        BRIDGE_VALUES.add("viaduct");
        BRIDGE_VALUES.add("aqueduct");
    }

    private final Set<Long> processedWayIds = new HashSet<>();
    private final TransportationData data = new TransportationData();

    public TransportationData getData() {
        return data;
    }

    public void reset() {
        processedWayIds.clear();
        data.segments.clear();
        data.bridges.clear();
    }

    public boolean parsePolyline(OsmPolyline poly) {
        if (poly == null || poly.getNodes() == null || poly.getNodes().isEmpty()) {
            return false;
        }
        if (processedWayIds.contains(poly.getId())) {
            return false;
        }

        FeatureType type = classify(poly.getTags());
        if (type == null) {
            return false;
        }

        // Chimneys are point/polygon features, handle separately
        if (type == FeatureType.CHIMNEY) {
            return false;
        }

        processedWayIds.add(poly.getId());

        boolean isBridge = isBridge(poly);
        int layer = parseLayer(poly);
        double width = estimateWidth(poly, type);

        List<TransportNode> nodes = new ArrayList<>();
        for (Node node : poly.getNodes()) {
            nodes.add(new TransportNode(node.getId(), node.getLat(), node.getLon()));
        }

        TransportSegment segment = new TransportSegment(poly.getId(), type, nodes, isBridge, layer, width);
        data.addSegment(segment);
        return true;
    }

    public static FeatureType classify(List<Tag> tags) {
        String highway = getTagValue(tags, "highway");
        if (highway != null && HIGHWAY_VALUES.contains(highway)) {
            return FeatureType.ROAD;
        }

        String railway = getTagValue(tags, "railway");
        if (railway != null && RAILWAY_VALUES.contains(railway)) {
            return FeatureType.RAILWAY;
        }

        String power = getTagValue(tags, "power");
        if (power != null && POWER_VALUES.contains(power)) {
            return FeatureType.POWERLINE;
        }

        String manMade = getTagValue(tags, "man_made");
        if ("chimney".equals(manMade)) {
            return FeatureType.CHIMNEY;
        }

        return null;
    }

    public static boolean isBridge(OsmPolyline poly) {
        String bridge = poly.getTagValue("bridge");
        if (bridge != null && BRIDGE_VALUES.contains(bridge)) {
            return true;
        }
        String manMade = poly.getTagValue("man_made");
        return "bridge".equals(manMade);
    }

    public static int parseLayer(OsmPolyline poly) {
        String layerStr = poly.getTagValue("layer");
        if (layerStr != null) {
            try {
                return Integer.parseInt(layerStr.trim());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 0;
    }

    public static double estimateWidth(OsmPolyline poly, FeatureType type) {
        switch (type) {
            case ROAD: {
                String highway = poly.getTagValue("highway");
                if (highway == null) return 6.0;
                switch (highway) {
                    case "motorway": case "trunk": return 12.0;
                    case "primary": case "secondary": return 8.0;
                    case "tertiary": case "unclassified": case "road": return 6.0;
                    case "residential": case "living_street": case "service": return 4.0;
                    default: return 6.0;
                }
            }
            case RAILWAY: {
                String gauge = poly.getTagValue("gauge");
                if (gauge != null) {
                    try { return Double.parseDouble(gauge) / 1000.0 * 2.0; } catch (NumberFormatException e) {}
                }
                return 1.435;
            }
            case POWERLINE: {
                String cables = poly.getTagValue("cables");
                if (cables != null) {
                    try { return Integer.parseInt(cables) * 0.5; } catch (NumberFormatException e) {}
                }
                return 2.0;
            }
            default: return 1.0;
        }
    }

    private static String getTagValue(List<Tag> tags, String key) {
        for (Tag tag : tags) {
            if (tag.getKey().equalsIgnoreCase(key)) {
                return tag.getValue();
            }
        }
        return null;
    }
}