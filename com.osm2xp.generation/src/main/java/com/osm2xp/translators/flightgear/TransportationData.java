package com.osm2xp.translators.flightgear;

import java.util.ArrayList;
import java.util.List;

import math.geom2d.Point2D;

public class TransportationData {

    public enum FeatureType {
        ROAD, RAILWAY, POWERLINE, BRIDGE, CHIMNEY
    }

    public static class TransportNode {
        public final double lat;
        public final double lon;
        public final long osmId;

        public TransportNode(long osmId, double lat, double lon) {
            this.osmId = osmId;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static class TransportSegment {
        public final long wayId;
        public final FeatureType type;
        public final List<TransportNode> nodes;
        public final boolean isBridge;
        public final int layer;
        public final double widthEstimate;

        public TransportSegment(long wayId, FeatureType type, List<TransportNode> nodes,
                                boolean isBridge, int layer, double widthEstimate) {
            this.wayId = wayId;
            this.type = type;
            this.nodes = nodes;
            this.isBridge = isBridge;
            this.layer = layer;
            this.widthEstimate = widthEstimate;
        }

        public double approximateLength() {
            if (nodes.size() < 2) return 0;
            double total = 0;
            for (int i = 1; i < nodes.size(); i++) {
                total += haversine(nodes.get(i - 1), nodes.get(i));
            }
            return total;
        }

        public Point2D center() {
            double sumLat = 0, sumLon = 0;
            for (TransportNode n : nodes) {
                sumLat += n.lat;
                sumLon += n.lon;
            }
            return new Point2D(sumLon / nodes.size(), sumLat / nodes.size());
        }
    }

    public final List<TransportSegment> segments = new ArrayList<>();
    public final List<TransportSegment> bridges = new ArrayList<>();

    public void addSegment(TransportSegment seg) {
        segments.add(seg);
        if (seg.isBridge) {
            bridges.add(seg);
        }
    }

    public int totalRoads() {
        return (int) segments.stream().filter(s -> s.type == FeatureType.ROAD).count();
    }

    public int totalRailways() {
        return (int) segments.stream().filter(s -> s.type == FeatureType.RAILWAY).count();
    }

    public int totalPowerlines() {
        return (int) segments.stream().filter(s -> s.type == FeatureType.POWERLINE).count();
    }

    public int totalBridges() {
        return bridges.size();
    }

    public int totalChimneys() {
        return (int) segments.stream().filter(s -> s.type == FeatureType.CHIMNEY).count();
    }

    public int totalSegments() {
        return segments.size();
    }

    static double haversine(TransportNode a, TransportNode b) {
        return haversineDeg(a.lat, a.lon, b.lat, b.lon);
    }

    static double haversineDeg(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}