package com.osm2xp.translators.flightgear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.osm2xp.translators.flightgear.TransportationData.FeatureType;
import com.osm2xp.translators.flightgear.TransportationData.TransportSegment;

public class TransportationValidator {

    public static class ValidationReport {
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public final Map<String, Object> telemetry = new HashMap<>();

        public boolean hasErrors() { return !errors.isEmpty(); }

        public void addError(String msg) { errors.add(msg); }
        public void addWarning(String msg) { warnings.add(msg); }

        public void putTelemetry(String key, Object value) { telemetry.put(key, value); }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Validation: ").append(errors.size()).append(" errors, ")
                    .append(warnings.size()).append(" warnings\n");
            if (!errors.isEmpty()) {
                sb.append("Errors:\n");
                for (String e : errors) sb.append("  - ").append(e).append("\n");
            }
            if (!warnings.isEmpty()) {
                sb.append("Warnings:\n");
                for (String w : warnings) sb.append("  - ").append(w).append("\n");
            }
            sb.append("Telemetry:\n");
            for (Map.Entry<String, Object> e : telemetry.entrySet()) {
                sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
            return sb.toString();
        }
    }

    private static final double MIN_SEGMENT_LENGTH = 0.5;
    private static final double MAX_SEGMENT_LENGTH = 500_000;
    private static final double MIN_WAY_LENGTH = 1.0;
    private static final double MAX_WAY_LENGTH = 1_000_000;
    private static final double MIN_NODE_SPACING = 0.1;
    private static final int MAX_JUNCTION_DEGREE = 10;

    public ValidationReport validate(TransportationData data) {
        ValidationReport report = new ValidationReport();

        if (data.segments.isEmpty()) {
            report.addWarning("No transportation segments found");
            return report;
        }

        // Collect telemetry
        report.putTelemetry("totalSegments", data.totalSegments());
        report.putTelemetry("totalRoads", data.totalRoads());
        report.putTelemetry("totalRailways", data.totalRailways());
        report.putTelemetry("totalPowerlines", data.totalPowerlines());
        report.putTelemetry("totalBridges", data.totalBridges());

        // Build length histogram per type
        List<Double> roadLengths = new ArrayList<>();
        List<Double> railLengths = new ArrayList<>();
        List<Double> powerLengths = new ArrayList<>();
        List<Double> bridgeLengths = new ArrayList<>();

        for (TransportSegment seg : data.segments) {
            double len = seg.approximateLength();
            switch (seg.type) {
                case ROAD: roadLengths.add(len); break;
                case RAILWAY: railLengths.add(len); break;
                case POWERLINE: powerLengths.add(len); break;
                default: break;
            }
            if (seg.isBridge) bridgeLengths.add(len);

            // Segment length sanity
            if (len < MIN_SEGMENT_LENGTH) {
                report.addWarning("Way " + seg.wayId + " (" + seg.type + ") length " +
                        String.format("%.2f", len) + "m < minimum " + MIN_SEGMENT_LENGTH + "m");
            }
            if (len > MAX_SEGMENT_LENGTH) {
                report.addWarning("Way " + seg.wayId + " (" + seg.type + ") length " +
                        String.format("%.0f", len) + "m > maximum " + MAX_SEGMENT_LENGTH + "m");
            }

            // Node count sanity
            if (seg.nodes.size() < 2) {
                report.addError("Way " + seg.wayId + " has " + seg.nodes.size() + " nodes (minimum 2)");
            }

            // Node spacing sanity
            for (int i = 1; i < seg.nodes.size(); i++) {
                double spacing = TransportationData.haversine(seg.nodes.get(i - 1), seg.nodes.get(i));
                if (spacing < MIN_NODE_SPACING) {
                    report.addWarning("Way " + seg.wayId + " node spacing " +
                            String.format("%.3f", spacing) + "m between nodes " +
                            seg.nodes.get(i - 1).osmId + " and " + seg.nodes.get(i).osmId);
                }
            }

            // Negative/zero length
            if (len <= 0) {
                report.addError("Way " + seg.wayId + " has non-positive length: " + len);
            }

            // Negative width estimate
            if (seg.widthEstimate < 0) {
                report.addWarning("Way " + seg.wayId + " has negative width estimate: " + seg.widthEstimate);
            }

            // Layer sanity
            if (seg.layer < -5 || seg.layer > 20) {
                report.addWarning("Way " + seg.wayId + " has unusual layer: " + seg.layer);
            }

            // Coordinate bounds
            for (TransportationData.TransportNode node : seg.nodes) {
                if (node.lat < -90 || node.lat > 90) {
                    report.addError("Way " + seg.wayId + " node " + node.osmId +
                            " has invalid latitude: " + node.lat);
                }
                if (node.lon < -180 || node.lon > 180) {
                    report.addError("Way " + seg.wayId + " node " + node.osmId +
                            " has invalid longitude: " + node.lon);
                }
            }
        }

        // Build histograms
        report.putTelemetry("roadLengthHistogram", buildHistogram(roadLengths));
        report.putTelemetry("railLengthHistogram", buildHistogram(railLengths));
        report.putTelemetry("powerLengthHistogram", buildHistogram(powerLengths));
        report.putTelemetry("bridgeLengthHistogram", buildHistogram(bridgeLengths));

        // Road/rail length statistics
        if (!roadLengths.isEmpty()) {
            report.putTelemetry("roadMinLength", String.format("%.2f", Collections.min(roadLengths)));
            report.putTelemetry("roadMaxLength", String.format("%.2f", Collections.max(roadLengths)));
            report.putTelemetry("roadMeanLength", String.format("%.2f", mean(roadLengths)));
            report.putTelemetry("roadMedianLength", String.format("%.2f", median(roadLengths)));
        }

        if (!railLengths.isEmpty()) {
            report.putTelemetry("railMinLength", String.format("%.2f", Collections.min(railLengths)));
            report.putTelemetry("railMaxLength", String.format("%.2f", Collections.max(railLengths)));
            report.putTelemetry("railMeanLength", String.format("%.2f", mean(railLengths)));
            report.putTelemetry("railMedianLength", String.format("%.2f", median(railLengths)));
        }

        if (!bridgeLengths.isEmpty()) {
            report.putTelemetry("bridgeMinLength", String.format("%.2f", Collections.min(bridgeLengths)));
            report.putTelemetry("bridgeMaxLength", String.format("%.2f", Collections.max(bridgeLengths)));
        }

        // Junction degree analysis
        Map<Long, Integer> junctionDegrees = new HashMap<>();
        for (TransportSegment seg : data.segments) {
            if (!seg.nodes.isEmpty()) {
                long firstId = seg.nodes.get(0).osmId;
                long lastId = seg.nodes.get(seg.nodes.size() - 1).osmId;
                junctionDegrees.merge(firstId, 1, Integer::sum);
                junctionDegrees.merge(lastId, 1, Integer::sum);
            }
        }
        List<Integer> highDegreeJunctions = junctionDegrees.values().stream()
                .filter(d -> d > MAX_JUNCTION_DEGREE)
                .collect(Collectors.toList());
        if (!highDegreeJunctions.isEmpty()) {
            report.addWarning(highDegreeJunctions.size() + " junctions with degree > " +
                    MAX_JUNCTION_DEGREE + " (max: " + Collections.max(highDegreeJunctions) + ")");
        }
        report.putTelemetry("maxJunctionDegree",
                junctionDegrees.isEmpty() ? 0 : Collections.max(junctionDegrees.values()));
        report.putTelemetry("totalJunctions", junctionDegrees.size());

        // Bridge ratio
        if (data.totalSegments() > 0) {
            double bridgeRatio = (double) data.totalBridges() / data.totalSegments();
            report.putTelemetry("bridgeRatio", String.format("%.4f", bridgeRatio));
            if (bridgeRatio > 0.5) {
                report.addWarning("Bridge ratio " + String.format("%.1f", bridgeRatio * 100) +
                        "% is unusually high");
            }
        }

        return report;
    }

    private static Map<String, Integer> buildHistogram(List<Double> values) {
        if (values.isEmpty()) return Collections.emptyMap();
        Map<String, Integer> hist = new HashMap<>();
        double[] buckets = {0, 1, 10, 50, 100, 500, 1000, 5000, 10000, 50000, 100000, Double.MAX_VALUE};
        String[] labels = {"0-1m", "1-10m", "10-50m", "50-100m", "100-500m", "500m-1km",
                "1-5km", "5-10km", "10-50km", "50-100km", "100km+"};
        for (double v : values) {
            for (int i = 0; i < buckets.length - 1; i++) {
                if (v >= buckets[i] && v < buckets[i + 1]) {
                    hist.merge(labels[i], 1, Integer::sum);
                    break;
                }
            }
        }
        return hist;
    }

    private static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(mid);
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }
}