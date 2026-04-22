import { apiClient } from "./client";
import { UUID } from "crypto";

export interface GisLayer {
  id: UUID;
  projectId: UUID;
  layerName: string;
  layerType: "WBS_POLYGON" | "SATELLITE_OVERLAY" | "VECTOR_LAYER" | "BASE_MAP";
  description?: string;
  isVisible: boolean;
  opacity: number;
  sortOrder: number;
  createdAt: string;
  createdBy: string;
}

export interface WbsPolygon {
  id: UUID;
  projectId: UUID;
  wbsNodeId: UUID;
  layerId: UUID;
  wbsCode: string;
  wbsName: string;
  polygonGeoJson: string;
  centerLatitude: number;
  centerLongitude: number;
  areaInSqMeters?: number;
  fillColor: string;
  strokeColor: string;
  createdAt: string;
  createdBy: string;
}

export interface GeoJsonFeatureCollection {
  type: "FeatureCollection";
  features: Array<{
    type: "Feature";
    properties: {
      wbsCode: string;
      wbsName: string;
      fillColor: string;
      strokeColor: string;
      id: string;
    };
    geometry: { type: string; coordinates: number[][][] };
  }>;
}

export interface SatelliteImage {
  id: UUID;
  projectId: UUID;
  layerId?: UUID;
  imageName: string;
  description?: string;
  captureDate: string;
  source:
    | "ISRO_CARTOSAT"
    | "PLANET_LABS"
    | "MAXAR"
    | "AIRBUS"
    | "DRONE"
    | "MANUAL_UPLOAD";
  resolution?: string;
  boundingBoxGeoJson?: string;
  filePath: string;
  fileSize: number;
  mimeType: string;
  northBound?: number;
  southBound?: number;
  eastBound?: number;
  westBound?: number;
  status: "UPLOADED" | "PROCESSING" | "READY" | "FAILED";
  createdAt: string;
  createdBy: string;
}

export interface ConstructionProgress {
  id: UUID;
  projectId: UUID;
  wbsPolygonId: UUID;
  captureDate: string;
  satelliteImageId?: UUID;
  derivedProgressPercent?: number;
  contractorClaimedPercent?: number;
  variancePercent?: number;
  analysisMethod: "MANUAL" | "AI_SEGMENTATION" | "VISUAL_INSPECTION";
  remarks?: string;
  createdAt: string;
  createdBy: string;
}

export interface IngestionResult {
  projectId: UUID;
  vendorId: string;
  runStartedAt: string;
  runFinishedAt: string;
  scenesFetched: number;
  scenesSkippedDedupe: number;
  snapshotsCreated: number;
  errors: string[];
}

export interface IngestionLogEntry {
  id: UUID;
  projectId: UUID;
  vendorId: string;
  fromDate: string;
  toDate: string;
  runStartedAt: string;
  runFinishedAt: string | null;
  scenesFetched: number;
  snapshotsCreated: number;
  status: "RUNNING" | "COMPLETED" | "FAILED" | "PARTIAL";
  metadataJson: string | null;
  errorsJson: string | null;
}

export interface ProgressVariance {
  wbsPolygonId: UUID;
  wbsCode: string;
  wbsName: string;
  derivedPercent?: number;
  claimedPercent?: number;
  variancePercent?: number;
  varianceStatus: "NO_DATA" | "ON_TRACK" | "BEHIND" | "AHEAD";
}

export interface CreateLayerRequest {
  layerName: string;
  layerType: "WBS_POLYGON" | "SATELLITE_OVERLAY" | "VECTOR_LAYER" | "BASE_MAP";
  description?: string;
  isVisible: boolean;
  opacity: number;
  sortOrder: number;
}

export interface UpdateLayerRequest {
  layerName?: string;
  layerType?: "WBS_POLYGON" | "SATELLITE_OVERLAY" | "VECTOR_LAYER" | "BASE_MAP";
  description?: string;
  isVisible?: boolean;
  opacity?: number;
  sortOrder?: number;
}

export interface CreatePolygonRequest {
  wbsNodeId: UUID;
  layerId: UUID;
  wbsCode: string;
  wbsName: string;
  polygonGeoJson: string;
  centerLatitude: number;
  centerLongitude: number;
  areaInSqMeters?: number;
  fillColor: string;
  strokeColor: string;
}

export interface UpdatePolygonRequest {
  wbsCode?: string;
  wbsName?: string;
  polygonGeoJson?: string;
  centerLatitude?: number;
  centerLongitude?: number;
  areaInSqMeters?: number;
  fillColor?: string;
  strokeColor?: string;
}

export interface CreateSatelliteImageRequest {
  imageName: string;
  description?: string;
  captureDate: string;
  source: "ISRO_CARTOSAT" | "PLANET_LABS" | "MAXAR" | "AIRBUS" | "DRONE" | "MANUAL_UPLOAD";
  resolution?: string;
  boundingBoxGeoJson?: string;
  filePath: string;
  fileSize: number;
  mimeType: string;
  northBound?: number;
  southBound?: number;
  eastBound?: number;
  westBound?: number;
}

export interface UpdateSatelliteImageRequest {
  imageName?: string;
  description?: string;
  captureDate?: string;
  source?: "ISRO_CARTOSAT" | "PLANET_LABS" | "MAXAR" | "AIRBUS" | "DRONE" | "MANUAL_UPLOAD";
  resolution?: string;
  boundingBoxGeoJson?: string;
  northBound?: number;
  southBound?: number;
  eastBound?: number;
  westBound?: number;
  status?: "UPLOADED" | "PROCESSING" | "READY" | "FAILED";
}

export interface CreateProgressSnapshotRequest {
  wbsPolygonId: UUID;
  captureDate: string;
  satelliteImageId?: UUID;
  derivedProgressPercent?: number;
  contractorClaimedPercent?: number;
  variancePercent?: number;
  analysisMethod: "MANUAL" | "AI_SEGMENTATION" | "VISUAL_INSPECTION";
  remarks?: string;
}

export interface UpdateProgressSnapshotRequest {
  captureDate?: string;
  satelliteImageId?: UUID;
  derivedProgressPercent?: number;
  contractorClaimedPercent?: number;
  variancePercent?: number;
  analysisMethod?: "MANUAL" | "AI_SEGMENTATION" | "VISUAL_INSPECTION";
  remarks?: string;
}

// GIS Layers
export const gisApi = {
  // Layers
  getLayers: (projectId: UUID) =>
    apiClient.get<{ data: GisLayer[] }>(`/v1/projects/${projectId}/gis/layers`),

  createLayer: (projectId: UUID, data: CreateLayerRequest) =>
    apiClient.post<{ data: GisLayer }>(
      `/v1/projects/${projectId}/gis/layers`,
      data
    ),

  updateLayer: (projectId: UUID, layerId: UUID, data: UpdateLayerRequest) =>
    apiClient.put<{ data: GisLayer }>(
      `/v1/projects/${projectId}/gis/layers/${layerId}`,
      data
    ),

  deleteLayer: (projectId: UUID, layerId: UUID) =>
    apiClient.delete(`/v1/projects/${projectId}/gis/layers/${layerId}`),

  // WBS Polygons
  getPolygons: (projectId: UUID) =>
    apiClient.get<{ data: WbsPolygon[] }>(
      `/v1/projects/${projectId}/gis/polygons`
    ),

  getPolygonsAsGeoJson: (projectId: UUID) =>
    apiClient.get<{ data: GeoJsonFeatureCollection }>(
      `/v1/projects/${projectId}/gis/polygons/geojson`
    ),

  createPolygon: (projectId: UUID, data: CreatePolygonRequest) =>
    apiClient.post<{ data: WbsPolygon }>(
      `/v1/projects/${projectId}/gis/polygons`,
      data
    ),

  updatePolygon: (projectId: UUID, polygonId: UUID, data: UpdatePolygonRequest) =>
    apiClient.put<{ data: WbsPolygon }>(
      `/v1/projects/${projectId}/gis/polygons/${polygonId}`,
      data
    ),

  deletePolygon: (projectId: UUID, polygonId: UUID) =>
    apiClient.delete(`/v1/projects/${projectId}/gis/polygons/${polygonId}`),

  // Satellite Images
  getSatelliteImages: (projectId: UUID, fromDate?: string, toDate?: string) => {
    let url = `/v1/projects/${projectId}/gis/satellite-images`;
    if (fromDate && toDate) {
      url += `?from=${fromDate}&to=${toDate}`;
    }
    return apiClient.get<{ data: SatelliteImage[] }>(url);
  },

  createSatelliteImage: (projectId: UUID, data: CreateSatelliteImageRequest) =>
    apiClient.post<{ data: SatelliteImage }>(
      `/v1/projects/${projectId}/gis/satellite-images`,
      data
    ),

  updateSatelliteImage: (projectId: UUID, imageId: UUID, data: UpdateSatelliteImageRequest) =>
    apiClient.put<{ data: SatelliteImage }>(
      `/v1/projects/${projectId}/gis/satellite-images/${imageId}`,
      data
    ),

  deleteSatelliteImage: (projectId: UUID, imageId: UUID) =>
    apiClient.delete(`/v1/projects/${projectId}/gis/satellite-images/${imageId}`),

  // Construction Progress
  getProgressSnapshots: (
    projectId: UUID,
    fromDate?: string,
    toDate?: string
  ) => {
    let url = `/v1/projects/${projectId}/gis/progress-snapshots`;
    if (fromDate && toDate) {
      url += `?from=${fromDate}&to=${toDate}`;
    }
    return apiClient.get<{ data: ConstructionProgress[] }>(url);
  },

  createProgressSnapshot: (projectId: UUID, data: CreateProgressSnapshotRequest) =>
    apiClient.post<{ data: ConstructionProgress }>(
      `/v1/projects/${projectId}/gis/progress-snapshots`,
      data
    ),

  updateProgressSnapshot: (projectId: UUID, snapshotId: UUID, data: UpdateProgressSnapshotRequest) =>
    apiClient.put<{ data: ConstructionProgress }>(
      `/v1/projects/${projectId}/gis/progress-snapshots/${snapshotId}`,
      data
    ),

  deleteProgressSnapshot: (projectId: UUID, snapshotId: UUID) =>
    apiClient.delete(
      `/v1/projects/${projectId}/gis/progress-snapshots/${snapshotId}`
    ),

  getProgressVariance: (projectId: UUID) =>
    apiClient.get<{ data: ProgressVariance[] }>(
      `/v1/projects/${projectId}/gis/progress-snapshots/variance`
    ),

  // Satellite ingestion (Phase 2-4): manually trigger a fetch+analyze run,
  // and inspect recent runs for a "last sync" indicator.
  ingestSatellite: (projectId: UUID, from: string, to: string) =>
    apiClient.post<{ data: IngestionResult }>(
      `/v1/projects/${projectId}/gis/ingest`,
      null,
      { params: { from, to } }
    ),

  getIngestionLog: (projectId: UUID) =>
    apiClient.get<{ data: IngestionLogEntry[] }>(
      `/v1/projects/${projectId}/gis/ingestion-log`
    ),
};
