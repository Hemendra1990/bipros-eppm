package com.bipros.api.config.seeder.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds rectangular polygons hugging a project's road corridor for GIS seeding.
 * The corridor is treated as a great-circle line between two lat/lon endpoints; each
 * package gets a contiguous segment widened by ±halfWidthMetres perpendicular to
 * the corridor heading. Output polygons are in EPSG:4326 (degrees lat/lon) and ready
 * to drop into a {@code WbsPolygon} entity.
 *
 * <p>Used by {@code NhaiRoadAttachmentsSeeder} (NH-48 Rajasthan corridor) and
 * {@code OdishaSh10AttachmentsSeeder} (SH-10 Bhubaneswar–Cuttack corridor). The math
 * is small enough that we don't pull in a projection library — a metres-per-degree
 * approximation at the corridor's mid-latitude is good to ~0.5% over a 30 km stretch.
 */
public final class CorridorGeometry {

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private CorridorGeometry() {}

    /** A single rectangular polygon segment for one WBS package. */
    public record Segment(Polygon polygon, double centerLat, double centerLon, double areaSqMetres) {}

    /**
     * Split the corridor into {@code segmentCount} equal segments and return one polygon
     * per segment, widened by {@code halfWidthMetres} on each side of the centreline.
     */
    public static List<Segment> buildSegments(
            double startLat, double startLon,
            double endLat, double endLon,
            int segmentCount,
            double halfWidthMetres) {

        if (segmentCount < 1) throw new IllegalArgumentException("segmentCount must be >= 1");

        double midLat = (startLat + endLat) / 2.0;
        // Mercator-flat approximation: lat → ~110_540 m/deg, lon → cos(lat) × 111_320 m/deg
        double mPerDegLat = 110_540.0;
        double mPerDegLon = 111_320.0 * Math.cos(Math.toRadians(midLat));

        // Corridor heading vector (in degrees of lat/lon)
        double dLat = endLat - startLat;
        double dLon = endLon - startLon;
        double heading = Math.atan2(dLat * mPerDegLat, dLon * mPerDegLon);

        // Perpendicular offset in degrees (rotate heading +90°)
        double perpDx = Math.sin(heading) * halfWidthMetres / mPerDegLon;
        double perpDy = -Math.cos(heading) * halfWidthMetres / mPerDegLat;

        List<Segment> out = new ArrayList<>(segmentCount);
        for (int i = 0; i < segmentCount; i++) {
            double t0 = (double) i / segmentCount;
            double t1 = (double) (i + 1) / segmentCount;

            double lat0 = startLat + dLat * t0;
            double lon0 = startLon + dLon * t0;
            double lat1 = startLat + dLat * t1;
            double lon1 = startLon + dLon * t1;

            // Four corners of the segment rectangle: (left-bottom, right-bottom, right-top, left-top)
            Coordinate[] ring = new Coordinate[] {
                    new Coordinate(lon0 - perpDx, lat0 - perpDy),
                    new Coordinate(lon1 - perpDx, lat1 - perpDy),
                    new Coordinate(lon1 + perpDx, lat1 + perpDy),
                    new Coordinate(lon0 + perpDx, lat0 + perpDy),
                    new Coordinate(lon0 - perpDx, lat0 - perpDy)
            };
            Polygon polygon = FACTORY.createPolygon(ring);

            double centerLat = (lat0 + lat1) / 2.0;
            double centerLon = (lon0 + lon1) / 2.0;
            double segmentLengthM = Math.hypot(
                    (lat1 - lat0) * mPerDegLat,
                    (lon1 - lon0) * mPerDegLon);
            double area = segmentLengthM * (2 * halfWidthMetres);

            out.add(new Segment(polygon, centerLat, centerLon, area));
        }
        return out;
    }

    /** Bounding-box union of every segment polygon. Returned as [west, south, east, north]. */
    public static double[] bbox(List<Segment> segments) {
        double w = Double.POSITIVE_INFINITY, s = Double.POSITIVE_INFINITY;
        double e = Double.NEGATIVE_INFINITY, n = Double.NEGATIVE_INFINITY;
        for (Segment seg : segments) {
            for (Coordinate c : seg.polygon().getExteriorRing().getCoordinates()) {
                if (c.x < w) w = c.x;
                if (c.x > e) e = c.x;
                if (c.y < s) s = c.y;
                if (c.y > n) n = c.y;
            }
        }
        return new double[] {w, s, e, n};
    }

    /** GeoJSON Polygon string for a bbox returned by {@link #bbox(List)}. */
    public static String bboxAsGeoJson(double[] bbox) {
        double w = bbox[0], s = bbox[1], e = bbox[2], n = bbox[3];
        return "{\"type\":\"Polygon\",\"coordinates\":[[["
                + w + "," + s + "],[" + e + "," + s + "],[" + e + "," + n + "],["
                + w + "," + n + "],[" + w + "," + s + "]]]}";
    }
}
