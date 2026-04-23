import type { GeoJsonFeatureCollection } from "@/lib/api/gisApi";

/** [west, south, east, north] in EPSG:4326 (lon/lat). */
export type Bbox4326 = [number, number, number, number];

/**
 * Walk every Polygon / MultiPolygon coordinate in a FeatureCollection and
 * return the overall WGS84 bounding box. Returns null when the collection has
 * no features or all features are non-polygon (e.g. points) — caller can then
 * skip fit-to-polygons and relevance filtering.
 */
export function computeGeoJsonExtent4326(
  fc: GeoJsonFeatureCollection | null | undefined
): Bbox4326 | null {
  if (!fc || !fc.features || fc.features.length === 0) return null;
  let minLon = Infinity;
  let maxLon = -Infinity;
  let minLat = Infinity;
  let maxLat = -Infinity;
  let touched = false;

  const visit = (coords: unknown) => {
    if (!Array.isArray(coords)) return;
    if (coords.length === 2 && typeof coords[0] === "number" && typeof coords[1] === "number") {
      const [lon, lat] = coords as [number, number];
      if (Number.isFinite(lon) && Number.isFinite(lat)) {
        if (lon < minLon) minLon = lon;
        if (lon > maxLon) maxLon = lon;
        if (lat < minLat) minLat = lat;
        if (lat > maxLat) maxLat = lat;
        touched = true;
      }
      return;
    }
    for (const inner of coords as unknown[]) visit(inner);
  };

  for (const feature of fc.features) {
    const g = feature.geometry;
    if (!g || !g.coordinates) continue;
    if (g.type !== "Polygon" && g.type !== "MultiPolygon") continue;
    visit(g.coordinates);
  }
  if (!touched) return null;
  return [minLon, minLat, maxLon, maxLat];
}

/** Classic AABB overlap — true when two [W,S,E,N] boxes share any area. */
export function bboxIntersects(a: Bbox4326, b: Bbox4326): boolean {
  const [aW, aS, aE, aN] = a;
  const [bW, bS, bE, bN] = b;
  // Standard half-plane test: no overlap when one box is strictly on one side.
  if (aE < bW || bE < aW) return false;
  if (aN < bS || bN < aS) return false;
  return true;
}
