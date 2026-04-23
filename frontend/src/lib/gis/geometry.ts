import GeoJSON from "ol/format/GeoJSON";
import { getArea } from "ol/sphere";
import { toLonLat } from "ol/proj";
import type { Geometry } from "ol/geom";
import Polygon from "ol/geom/Polygon";

/**
 * Polygon stats derived once and reused by both Draw and Modify handlers so
 * create/update payloads always agree on centroid/area/GeoJSON. Assumes the
 * map projection is EPSG:3857 (the only projection the map is configured
 * with) and emits a 4326 GeoJSON string for the backend, which treats the
 * stored geometry as WGS84 lat/lon.
 */
export interface PolygonMeta {
  centerLat: number;
  centerLon: number;
  areaSqM: number;
  geoJsonString: string;
}

export function computePolygonMeta(geom: Polygon): PolygonMeta {
  // ol/sphere.getArea is WGS84 haversine in square meters when the geometry
  // is in EPSG:3857, which is what our map uses.
  const areaSqM = getArea(geom);
  const interior = geom.getInteriorPoint().getCoordinates();
  const [lon3857, lat3857] = interior;
  const [lon, lat] = toLonLat([lon3857, lat3857]);
  // Clamp longitude to [-180, 180] in case a drag crossed the antimeridian.
  const centerLon = Math.max(-180, Math.min(180, lon));
  const centerLat = Math.max(-90, Math.min(90, lat));
  const geoJsonString = new GeoJSON().writeGeometry(geom, {
    featureProjection: "EPSG:3857",
    dataProjection: "EPSG:4326",
  });
  return { centerLat, centerLon, areaSqM, geoJsonString };
}

export function isValidPolygon(geom: Geometry | undefined): geom is Polygon {
  return geom instanceof Polygon;
}
