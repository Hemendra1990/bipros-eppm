"use client";

import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import type Feature from "ol/Feature";
import type Polygon from "ol/geom/Polygon";
import { MapViewer, type LayerVisibility } from "@/components/gis/MapViewer";
import { ScenePicker } from "@/components/gis/ScenePicker";
import { LayerControlPanel } from "@/components/gis/LayerControlPanel";
import PolygonListPanel, {
  type PolygonListItem,
} from "@/components/gis/PolygonListPanel";
import { GisLayerList } from "@/components/gis/GisLayerList";
import { SatelliteImageGallery } from "@/components/gis/SatelliteImageGallery";
import { UploadSatelliteImageModal } from "@/components/gis/UploadSatelliteImageModal";
import { MapModeToolbar, type MapMode } from "@/components/gis/MapModeToolbar";
import {
  DrawReviewPanel,
  type DrawPayload,
} from "@/components/gis/DrawReviewPanel";
import { PolygonEditPanel } from "@/components/gis/PolygonEditPanel";
import { TabTip } from "@/components/common/TabTip";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
// import { AiInsightsPanel } from "@/components/ai/AiInsightsPanel";
import { Button } from "@/components/ui/button";
import { Loader2 } from "lucide-react";
import toast from "react-hot-toast";
import {
  gisApi,
  type IngestionLogEntry,
  type SatelliteImage,
  type GeoJsonFeatureCollection,
} from "@/lib/api/gisApi";
import { projectApi } from "@/lib/api/projectApi";
import { useAuthStore } from "@/lib/state/store";
import { useSceneBlobUrl } from "@/lib/gis/useSceneBlobUrl";
import {
  computeGeoJsonExtent4326,
  bboxIntersects,
  type Bbox4326,
} from "@/lib/gis/extent";
import { computePolygonMeta, type PolygonMeta } from "@/lib/gis/geometry";

type TabId = "map" | "layers" | "satellite" | "progress";

type FeatureJson = GeoJsonFeatureCollection["features"][number];

export default function GisViewerPage() {
  return (
    <Suspense fallback={<div className="p-6 text-center text-text-muted">Loading…</div>}>
      <GisViewerPageInner />
    </Suspense>
  );
}

function GisViewerPageInner() {
  const params = useParams();
  const projectId = params.projectId as `${string}-${string}-${string}-${string}-${string}`;
  const router = useRouter();
  const searchParams = useSearchParams();
  const [activeTab, setActiveTab] = useState<TabId>("map");
  const qc = useQueryClient();

  const [visibility, setVisibility] = useState<LayerVisibility>({
    baseMap: true,
    polygons: true,
    satellite: true,
  });
  const [satelliteOpacity, setSatelliteOpacity] = useState(0.8);
  const [selectedSceneId, setSelectedSceneId] = useState<string | null>(null);
  const [fitSignal, setFitSignal] = useState(0);
  const [showAllScenes, setShowAllScenes] = useState(false);
  const [selectedPolygonId, setSelectedPolygonId] = useState<string | null>(
    null
  );

  // Drawing / editing state.
  const [mapMode, setMapMode] = useState<MapMode>("view");
  const [pendingDrawMeta, setPendingDrawMeta] = useState<PolygonMeta | null>(
    null
  );
  const [selectedFeatureId, setSelectedFeatureId] = useState<string | null>(
    null
  );
  const [lastSavedAt, setLastSavedAt] = useState<Date | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);

  const [ingestFrom, setIngestFrom] = useState(
    () => new Date(Date.now() - 7 * 24 * 3600 * 1000).toISOString().slice(0, 10)
  );
  const [ingestTo, setIngestTo] = useState(() => new Date().toISOString().slice(0, 10));
  const [lastRun, setLastRun] = useState<IngestionLogEntry | null>(null);
  const [uploadModalOpen, setUploadModalOpen] = useState(false);
  // Async ingestion tracking: the in-flight run id + a live elapsed counter so
  // the UI can show a background loader and toast on completion.
  const [ingestRunId, setIngestRunId] = useState<string | null>(null);
  const [ingestStartedAt, setIngestStartedAt] = useState<number | null>(null);
  const [ingestElapsed, setIngestElapsed] = useState(0);
  // Themed delete-confirmation (replaces window.confirm). Holds the pending
  // action so one dialog serves single, edit-panel, and batch polygon deletes.
  const [confirmState, setConfirmState] = useState<{
    title: string;
    message: string;
    onConfirm: () => void;
  } | null>(null);

  const ingesting = ingestRunId !== null;

  // Dispatch a background ingestion run. Returns immediately with a run id;
  // the actual work happens server-side and we poll the ingestion log below.
  const startIngest = useMutation({
    mutationFn: (vars: { polygonId?: string; from?: string; to?: string }) =>
      gisApi.ingestSatellite(
        projectId,
        vars.from ?? ingestFrom,
        vars.to ?? ingestTo,
        vars.polygonId as
          | `${string}-${string}-${string}-${string}-${string}`
          | undefined
      ),
    onSuccess: (response) => {
      setIngestRunId(response.data.data.runId);
      setIngestStartedAt(Date.now());
      setIngestElapsed(0);
    },
    onError: (err: unknown) => {
      toast.error(
        err instanceof Error ? err.message : "Failed to start ingestion"
      );
    },
  });

  const { data: geoJsonResponse, isLoading: geoJsonLoading } = useQuery({
    queryKey: ["gis", projectId, "geojson"],
    queryFn: async () => (await gisApi.getPolygonsAsGeoJson(projectId)).data,
  });

  const { data: layersResponse } = useQuery({
    queryKey: ["gis", projectId, "layers"],
    queryFn: async () => (await gisApi.getLayers(projectId)).data,
  });

  const { data: satelliteImagesResponse } = useQuery({
    queryKey: ["gis", projectId, "satellite-images"],
    queryFn: async () => (await gisApi.getSatelliteImages(projectId)).data,
  });

  // Poll the ingestion log only while a background run is in flight.
  const { data: ingestionLogResponse } = useQuery({
    queryKey: ["gis", projectId, "ingestion-log"],
    queryFn: async () => (await gisApi.getIngestionLog(projectId)).data,
    enabled: ingesting,
    refetchInterval: ingesting ? 2500 : false,
  });

  const { data: wbsTreeResponse } = useQuery({
    queryKey: ["project", projectId, "wbs-tree"],
    queryFn: () => projectApi.getWbsTree(projectId),
  });
  const wbsTree = wbsTreeResponse?.data ?? [];

  const allScenes: SatelliteImage[] = useMemo(
    () => satelliteImagesResponse?.data ?? [],
    [satelliteImagesResponse]
  );

  const polygonExtent4326 = useMemo(
    () =>
      geoJsonResponse?.data
        ? computeGeoJsonExtent4326(geoJsonResponse.data)
        : null,
    [geoJsonResponse]
  );

  const relevantScenes = useMemo(() => {
    // A selected polygon takes precedence: show only the scenes it owns.
    if (selectedPolygonId) {
      return allScenes.filter((s) => s.wbsPolygonId === selectedPolygonId);
    }
    if (showAllScenes || !polygonExtent4326) return allScenes;
    return allScenes.filter((s) => {
      if (
        typeof s.westBound !== "number" ||
        typeof s.southBound !== "number" ||
        typeof s.eastBound !== "number" ||
        typeof s.northBound !== "number"
      ) {
        return false;
      }
      const sceneBox: Bbox4326 = [
        s.westBound,
        s.southBound,
        s.eastBound,
        s.northBound,
      ];
      return bboxIntersects(sceneBox, polygonExtent4326);
    });
  }, [allScenes, polygonExtent4326, showAllScenes, selectedPolygonId]);

  const selectedScene = useMemo(
    () => relevantScenes.find((s) => s.id === selectedSceneId) ?? null,
    [relevantScenes, selectedSceneId]
  );

  const wbsPolygonLayer = useMemo(
    () => layersResponse?.data?.find((l) => l.layerType === "WBS_POLYGON"),
    [layersResponse]
  );

  const features: FeatureJson[] = useMemo(
    () => geoJsonResponse?.data?.features ?? [],
    [geoJsonResponse]
  );
  const mappedNodeIds = useMemo(
    () => new Set(features.map((f) => f.properties.wbsNodeId)),
    [features]
  );
  const selectedFeature = useMemo(
    () => features.find((f) => f.properties.id === selectedFeatureId) ?? null,
    [features, selectedFeatureId]
  );

  // Scenes owned per polygon (satellite images now carry wbsPolygonId).
  const sceneCountByPolygon = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const s of allScenes) {
      if (s.wbsPolygonId) {
        counts[s.wbsPolygonId] = (counts[s.wbsPolygonId] ?? 0) + 1;
      }
    }
    return counts;
  }, [allScenes]);

  const polygonListItems: PolygonListItem[] = useMemo(
    () =>
      features.map((f) => ({
        id: f.properties.id,
        name: f.properties.name,
        wbsCode: f.properties.wbsCode,
        wbsName: f.properties.wbsName,
        areaInSqMeters: f.properties.areaInSqMeters,
      })),
    [features]
  );

  const galleryPolygons = useMemo(
    () =>
      polygonListItems.map((p) => ({
        id: p.id,
        name: p.name,
        wbsCode: p.wbsCode,
      })),
    [polygonListItems]
  );

  // Auto-select the first polygon once, so the viewer opens scoped to a single
  // polygon rather than a merged "all polygons" set. The user can still clear
  // the selection ("Show all") afterwards without it snapping back.
  const didAutoSelectPolygonRef = useRef(false);
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (
      !didAutoSelectPolygonRef.current &&
      !selectedPolygonId &&
      polygonListItems.length > 0
    ) {
      didAutoSelectPolygonRef.current = true;
      setSelectedPolygonId(polygonListItems[0].id);
    }
  }, [polygonListItems, selectedPolygonId]);
  /* eslint-enable react-hooks/set-state-in-effect */

  // WhatsApp deep-link: hydrate auth from ?auth= query param or cookie.
  useEffect(() => {
    const storeToken = useAuthStore.getState().accessToken;
    if (storeToken) return;

    const authParam = searchParams.get("auth");
    const cookieEntry = document.cookie
      .split("; ")
      .find((row) => row.startsWith("access_token="));
    const cookieToken = cookieEntry?.split("=")[1];

    const token = authParam || cookieToken;
    if (!token) return;

    const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
    fetch(`${API_BASE_URL}/v1/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => {
        if (!res.ok) throw new Error("Failed to fetch user");
        return res.json();
      })
      .then((json) => {
        if (json.data) {
          useAuthStore.getState().setAuth(json.data, token, "");
          // Set cookie so middleware allows subsequent page navigation
          if (authParam) {
            const maxAge = 3600;
            document.cookie = `access_token=${token}; path=/; max-age=${maxAge}; SameSite=Lax`;
            // Remove ?auth= from URL
            const url = new URL(window.location.href);
            url.searchParams.delete("auth");
            window.history.replaceState({}, "", url.toString());
          }
        }
      })
      .catch(() => {
        document.cookie = "access_token=; path=/; max-age=0; SameSite=Lax";
        if (authParam) {
          window.location.href = "/auth/login?error=invalid_token";
        }
      });
  }, []);

  // --- Mutations ------------------------------------------------------------

  const createPolygon = useMutation({
    mutationFn: async (args: {
      layerId: string;
      payload: DrawPayload;
      meta: PolygonMeta;
    }) => {
      const { payload, meta, layerId } = args;
      const response = await gisApi.createPolygon(projectId, {
        wbsNodeId: payload.wbsNodeId as `${string}-${string}-${string}-${string}-${string}`,
        layerId: layerId as `${string}-${string}-${string}-${string}-${string}`,
        wbsCode: payload.wbsCode,
        wbsName: payload.wbsName,
        name: payload.name,
        polygonGeoJson: meta.geoJsonString,
        centerLatitude: meta.centerLat,
        centerLongitude: meta.centerLon,
        areaInSqMeters: meta.areaSqM,
        fillColor: payload.fillColor,
        strokeColor: payload.strokeColor,
      });
      return response.data.data;
    },
    onSuccess: (created) => {
      qc.invalidateQueries({ queryKey: ["gis", projectId, "geojson"] });
      setPendingDrawMeta(null);
      setMutationError(null);
      // Land the user on the new polygon in modify mode so they can kick off
      // satellite-imagery download for it without hunting through tabs.
      setSelectedFeatureId(created.id as string);
      setMapMode("modify");
      setLastSavedAt(null);
    },
    onError: (err: unknown) => {
      setMutationError(err instanceof Error ? err.message : String(err));
    },
  });

  // Keep a lightweight in-flight guard per-polygon so sequential modifyend
  // events for the same feature don't stack up.
  const modifyTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(
    new Map()
  );

  const updatePolygon = useMutation({
    mutationFn: async (args: {
      polygonId: string;
      feature: FeatureJson;
      meta: PolygonMeta;
    }) => {
      const { polygonId, feature, meta } = args;
      await gisApi.updatePolygon(
        projectId,
        polygonId as `${string}-${string}-${string}-${string}-${string}`,
        {
          wbsCode: feature.properties.wbsCode,
          wbsName: feature.properties.wbsName,
          polygonGeoJson: meta.geoJsonString,
          centerLatitude: meta.centerLat,
          centerLongitude: meta.centerLon,
          areaInSqMeters: meta.areaSqM,
          fillColor: feature.properties.fillColor,
          strokeColor: feature.properties.strokeColor,
        }
      );
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["gis", projectId, "geojson"] });
      setLastSavedAt(new Date());
      setMutationError(null);
    },
    onError: (err: unknown) => {
      setMutationError(err instanceof Error ? err.message : String(err));
    },
  });

  // Per-polygon "fetch imagery" now flows through the shared async ingestion
  // (startIngest); these fetchResult/fetchError slots are retained for the
  // PolygonEditPanel contract but stay null — feedback is via the toast.
  const [fetchResult] = useState<
    { fetched: number; skipped: number; errors: number; errorMessages: string[] } | null
  >(null);
  const [fetchError] = useState<string | null>(null);

  const deletePolygon = useMutation({
    mutationFn: (polygonId: string) =>
      gisApi.deletePolygon(
        projectId,
        polygonId as `${string}-${string}-${string}-${string}-${string}`
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["gis", projectId, "geojson"] });
      // Cascade also removes the polygon's satellite scenes/rasters.
      qc.invalidateQueries({ queryKey: ["gis", projectId, "satellite-images"] });
      setSelectedFeatureId(null);
      setSelectedPolygonId(null);
      setMutationError(null);
    },
    onError: (err: unknown) => {
      setMutationError(err instanceof Error ? err.message : String(err));
    },
  });

  const batchDeletePolygons = useMutation({
    mutationFn: (ids: string[]) => gisApi.batchDeletePolygons(projectId, ids),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["gis", projectId, "geojson"] });
      qc.invalidateQueries({ queryKey: ["gis", projectId, "satellite-images"] });
      setSelectedPolygonId(null);
      setSelectedFeatureId(null);
      setMutationError(null);
    },
    onError: (err: unknown) => {
      setMutationError(err instanceof Error ? err.message : String(err));
    },
  });

  const handleBatchDelete = useCallback(
    (ids: string[]) => {
      if (ids.length === 0) return;
      setConfirmState({
        title: ids.length > 1 ? "Delete polygons" : "Delete polygon",
        message: `Delete ${ids.length} polygon(s) and their satellite scenes? This cannot be undone.`,
        onConfirm: () => batchDeletePolygons.mutate(ids),
      });
    },
    [batchDeletePolygons]
  );

  const createDefaultLayer = useMutation({
    mutationFn: () =>
      gisApi.createLayer(projectId, {
        layerName: "WBS Polygons",
        layerType: "WBS_POLYGON",
        isVisible: true,
        opacity: 1,
        sortOrder: 0,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["gis", projectId, "layers"] });
    },
  });

  // --- Map-mode handlers ----------------------------------------------------

  const handleDrawEnd = useCallback((geom: Polygon) => {
    setPendingDrawMeta(computePolygonMeta(geom));
    setMutationError(null);
  }, []);

  const handleModifyEnd = useCallback(
    (feature: Feature) => {
      const polygonId = feature.get("id") as string | undefined;
      if (!polygonId) return;
      const geom = feature.getGeometry();
      if (!geom || !("getInteriorPoint" in geom)) return;
      const meta = computePolygonMeta(geom as Polygon);

      const featureJson = features.find((f) => f.properties.id === polygonId);
      if (!featureJson) return;

      // Debounce per-feature so a chain of vertex drags collapses to one PUT.
      const timers = modifyTimersRef.current;
      const existing = timers.get(polygonId);
      if (existing) clearTimeout(existing);
      const handle = setTimeout(() => {
        timers.delete(polygonId);
        updatePolygon.mutate({ polygonId, feature: featureJson, meta });
      }, 500);
      timers.set(polygonId, handle);
    },
    [features, updatePolygon]
  );

  const handleDeleteClick = useCallback(
    (feature: Feature) => {
      const polygonId = feature.get("id") as string | undefined;
      const wbsCode = feature.get("wbsCode") as string | undefined;
      if (!polygonId) return;
      setConfirmState({
        title: "Delete polygon",
        message: `Delete polygon ${wbsCode ?? polygonId} and its satellite scenes? This cannot be undone.`,
        onConfirm: () => deletePolygon.mutate(polygonId),
      });
    },
    [deletePolygon]
  );

  const handleSelectFeature = useCallback((feature: Feature | null) => {
    setSelectedFeatureId(feature ? (feature.get("id") as string) : null);
    setLastSavedAt(null);
  }, []);

  const handleModeChange = useCallback((next: MapMode) => {
    setMapMode(next);
    setPendingDrawMeta(null);
    setSelectedFeatureId(null);
    setMutationError(null);
    setLastSavedAt(null);
  }, []);

  // --- Scene default + URL sync (unchanged from previous) -------------------

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (relevantScenes.length === 0) {
      if (selectedSceneId !== null) setSelectedSceneId(null);
      return;
    }
    if (selectedSceneId && relevantScenes.some((s) => s.id === selectedSceneId)) {
      return;
    }
    const urlScene = searchParams.get("scene");
    const fromUrl = urlScene && relevantScenes.find((s) => s.id === urlScene);
    if (fromUrl) {
      setSelectedSceneId(fromUrl.id as string);
      return;
    }
    // Default to the least-cloudy scene so users don't open on a white frame;
    // fall back to newest-first when cloud data is missing.
    const best = [...relevantScenes].sort((a, b) => {
      const ca = a.cloudCoverPercent;
      const cb = b.cloudCoverPercent;
      if (typeof ca === "number" && typeof cb === "number" && ca !== cb) {
        return ca - cb;
      }
      return (
        new Date(b.captureDate).getTime() - new Date(a.captureDate).getTime()
      );
    })[0];
    setSelectedSceneId(best.id as string);
  }, [relevantScenes, searchParams, selectedSceneId]);
  /* eslint-enable react-hooks/set-state-in-effect */

  useEffect(() => {
    const current = searchParams.get("scene");
    if (selectedSceneId && current !== selectedSceneId) {
      const qs = new URLSearchParams(searchParams.toString());
      qs.set("scene", selectedSceneId);
      router.replace(`?${qs.toString()}`, { scroll: false });
    } else if (!selectedSceneId && current) {
      const qs = new URLSearchParams(searchParams.toString());
      qs.delete("scene");
      const suffix = qs.toString();
      router.replace(suffix ? `?${suffix}` : "", { scroll: false });
    }
  }, [selectedSceneId, router, searchParams]);

  const { url: sceneBlobUrl, error: sceneBlobError } = useSceneBlobUrl(
    projectId,
    selectedSceneId,
    selectedScene?.mimeType
  );

  // Detect background-ingestion completion by polling the ingestion log; on a
  // terminal status, toast + refresh scenes so the new imagery shows up.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!ingestRunId) return;
    const run = ingestionLogResponse?.data?.find((r) => r.id === ingestRunId);
    if (!run || run.status === "RUNNING") return;
    if (run.status === "FAILED") {
      toast.error("Ingestion failed — see the run log.");
    } else {
      const n = run.scenesFetched ?? 0;
      toast.success(
        `Ingestion complete — ${n} scene${n === 1 ? "" : "s"} added` +
          (run.status === "PARTIAL" ? " (partial)" : "")
      );
    }
    setLastRun(run);
    setIngestRunId(null);
    setIngestStartedAt(null);
    qc.invalidateQueries({ queryKey: ["gis", projectId, "satellite-images"] });
    qc.invalidateQueries({ queryKey: ["gis", projectId, "geojson"] });
  }, [ingestionLogResponse, ingestRunId, qc, projectId]);
  /* eslint-enable react-hooks/set-state-in-effect */

  // Live elapsed-seconds counter for the background-ingestion loader banner.
  useEffect(() => {
    if (ingestStartedAt == null) return;
    const t = setInterval(
      () => setIngestElapsed(Math.floor((Date.now() - ingestStartedAt) / 1000)),
      1000
    );
    return () => clearInterval(t);
  }, [ingestStartedAt]);

  const canEdit = !!wbsPolygonLayer;

  const tabs = [
    { id: "map" as TabId, label: "Map Viewer" },
    { id: "layers" as TabId, label: "Layers" },
    { id: "satellite" as TabId, label: "Satellite Images" },
    // "Progress Tracking" tab hidden for the demo (code retained).
  ];

  // What side panel to show in the right column.
  const showDrawReview = mapMode === "draw" && pendingDrawMeta !== null;
  const showPolygonEdit = mapMode === "modify" && selectedFeature !== null;

  return (
    <div className="flex flex-col h-full gap-4 p-4">
      {/* <AiInsightsPanel
        projectId={projectId}
        endpoint={`/v1/projects/${projectId}/gis/ai/insights`}
        defaultCollapsed
      /> */}
      <TabTip
        title="GIS Map Viewer"
        description="View your project location on a map. Draw, edit, and delete WBS polygons; step through satellite scenes; track construction progress geographically."
      />
      {ingesting && (
        <div className="flex items-center gap-3 rounded-lg border border-accent/30 bg-accent/10 px-4 py-2 text-sm text-text-primary">
          <Loader2 className="h-4 w-4 animate-spin text-accent" />
          <span>
            Ingestion running in the background — you can keep working
            {ingestElapsed > 0 ? ` · ${ingestElapsed}s` : ""}.
          </span>
        </div>
      )}
      <div className="flex gap-2 border-b border-border">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 font-medium text-sm transition-colors ${
              activeTab === tab.id
                ? "border-b-2 border-blue-600 text-accent"
                : "text-text-secondary hover:text-text-primary"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="flex-1 overflow-auto">
        {activeTab === "map" && (
          <div className="grid grid-cols-1 md:grid-cols-[1fr_320px] gap-4">
            <div className="flex flex-col gap-3 min-w-0">
              <div className="flex flex-wrap items-center gap-3">
                <MapModeToolbar
                  mode={mapMode}
                  onModeChange={handleModeChange}
                  canEdit={canEdit}
                  editDisabledReason={
                    canEdit
                      ? undefined
                      : "No WBS_POLYGON layer configured for this project."
                  }
                />
                {!canEdit && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => createDefaultLayer.mutate()}
                    disabled={createDefaultLayer.isPending}
                  >
                    {createDefaultLayer.isPending
                      ? "Creating layer…"
                      : "Create default polygon layer"}
                  </Button>
                )}
                {mapMode === "modify" && !selectedFeatureId && (
                  <span className="text-xs text-text-muted">
                    Click a polygon to select it, then drag a vertex.
                  </span>
                )}
                {mapMode === "delete" && (
                  <span className="text-xs text-text-muted">
                    Click a polygon to delete it.
                  </span>
                )}
              </div>
              <ScenePicker
                scenes={relevantScenes}
                selectedSceneId={selectedSceneId}
                onChange={setSelectedSceneId}
              />
              {allScenes.length > 0 &&
                relevantScenes.length === 0 &&
                (selectedPolygonId ||
                  (!showAllScenes && polygonExtent4326)) && (
                  <div className="rounded-md border border-warning/30 bg-warning/10 px-3 py-2 text-xs text-warning flex items-center justify-between gap-2">
                    <span>
                      {selectedPolygonId
                        ? "No scenes for the selected polygon yet. Fetch imagery or re-run ingestion."
                        : "No scenes intersect this project's polygon area."}
                    </span>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() =>
                        selectedPolygonId
                          ? setSelectedPolygonId(null)
                          : setShowAllScenes(true)
                      }
                    >
                      Show all
                    </Button>
                  </div>
                )}
              {sceneBlobError && (
                <div className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-xs text-danger">
                  Could not load scene imagery · {selectedSceneId}
                </div>
              )}
              {geoJsonLoading ? (
                <div className="flex items-center justify-center h-96 rounded-lg border border-border bg-surface/50">
                  <span className="text-text-muted">Loading map data...</span>
                </div>
              ) : (
                <MapViewer
                  geoJsonData={
                    geoJsonResponse?.data ?? {
                      type: "FeatureCollection",
                      features: [],
                    }
                  }
                  visibility={visibility}
                  satelliteOpacity={satelliteOpacity}
                  selectedScene={selectedScene}
                  sceneBlobUrl={sceneBlobUrl}
                  fitPolygonsSignal={fitSignal}
                  mode={mapMode}
                  highlightId={selectedPolygonId}
                  onDrawEnd={handleDrawEnd}
                  onModifyEnd={handleModifyEnd}
                  onDeleteClick={handleDeleteClick}
                  onSelectFeature={handleSelectFeature}
                  onViewSelectFeature={setSelectedPolygonId}
                />
              )}
            </div>
            {showDrawReview && pendingDrawMeta && wbsPolygonLayer ? (
              <DrawReviewPanel
                meta={pendingDrawMeta}
                tree={wbsTree}
                mappedNodeIds={mappedNodeIds}
                isSaving={createPolygon.isPending}
                saveError={mutationError}
                onSave={(payload) =>
                  createPolygon.mutate({
                    layerId: wbsPolygonLayer.id as string,
                    payload,
                    meta: pendingDrawMeta,
                  })
                }
                onDiscard={() => {
                  setPendingDrawMeta(null);
                  setMutationError(null);
                }}
              />
            ) : showPolygonEdit && selectedFeature ? (
              <PolygonEditPanel
                polygon={selectedFeature}
                isSaving={updatePolygon.isPending}
                saveError={mutationError}
                lastSavedAt={lastSavedAt}
                isDeleting={deletePolygon.isPending}
                isFetchingImagery={ingesting}
                fetchResult={fetchResult}
                fetchError={fetchError}
                onFetchImagery={() => {
                  const to = new Date().toISOString().slice(0, 10);
                  const from = new Date(Date.now() - 30 * 24 * 3600 * 1000)
                    .toISOString()
                    .slice(0, 10);
                  startIngest.mutate({
                    polygonId: selectedFeature.properties.id,
                    from,
                    to,
                  });
                }}
                onDelete={() =>
                  setConfirmState({
                    title: "Delete polygon",
                    message: `Delete polygon ${selectedFeature.properties.wbsCode} and its satellite scenes? This cannot be undone.`,
                    onConfirm: () =>
                      deletePolygon.mutate(selectedFeature.properties.id),
                  })
                }
              />
            ) : (
              <div className="flex flex-col gap-4">
                <PolygonListPanel
                  polygons={polygonListItems}
                  selectedPolygonId={selectedPolygonId}
                  onSelect={setSelectedPolygonId}
                  sceneCountByPolygon={sceneCountByPolygon}
                  onBatchDelete={handleBatchDelete}
                />
                <LayerControlPanel
                  projectId={projectId}
                  visibility={visibility}
                  onVisibilityChange={setVisibility}
                  satelliteOpacity={satelliteOpacity}
                  onSatelliteOpacityChange={setSatelliteOpacity}
                  selectedScene={selectedScene}
                  onZoomToPolygons={() => setFitSignal((n) => n + 1)}
                  canZoomToPolygons={!!polygonExtent4326}
                  backendLayers={layersResponse?.data}
                />
              </div>
            )}
          </div>
        )}

        {activeTab === "layers" && (
          <div>
            {layersResponse?.data && layersResponse.data.length > 0 ? (
              <GisLayerList projectId={projectId} layers={layersResponse.data} />
            ) : (
              <div className="flex items-center justify-center h-96">
                <span className="text-text-muted">No layers available</span>
              </div>
            )}
          </div>
        )}

        {activeTab === "satellite" && (
          <div className="space-y-4">
            <div className="rounded-lg border border-border bg-surface/50 p-4">
              <div className="flex flex-wrap items-end gap-3">
                <label className="text-sm">
                  <span className="block text-text-secondary mb-1">From</span>
                  <input
                    type="date"
                    value={ingestFrom}
                    onChange={(e) => setIngestFrom(e.target.value)}
                    max={ingestTo}
                    className="rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text-primary"
                  />
                </label>
                <label className="text-sm">
                  <span className="block text-text-secondary mb-1">To</span>
                  <input
                    type="date"
                    value={ingestTo}
                    onChange={(e) => setIngestTo(e.target.value)}
                    min={ingestFrom}
                    className="rounded-md border border-border bg-surface px-2 py-1.5 text-sm text-text-primary"
                  />
                </label>
                <Button
                  onClick={() => startIngest.mutate({})}
                  disabled={ingesting}
                >
                  {ingesting ? "Ingestion running…" : "Run Ingestion"}
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => setUploadModalOpen(true)}
                >
                  Upload Image
                </Button>
                {ingesting && (
                  <span className="flex items-center gap-1.5 text-sm text-text-secondary">
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    Running in the background
                    {ingestElapsed > 0 ? ` · ${ingestElapsed}s` : ""}
                  </span>
                )}
              </div>
              {lastRun && !ingesting && (
                <div className="mt-3 text-sm text-text-secondary">
                  Last run (<span className="text-text-primary">{lastRun.vendorId}</span>):{" "}
                  <span className="text-text-primary">{lastRun.scenesFetched}</span> fetched,{" "}
                  <span className="text-text-primary">{lastRun.snapshotsCreated}</span> snapshots queued ·{" "}
                  <span className="text-text-primary">{lastRun.status}</span>
                </div>
              )}
            </div>

            {satelliteImagesResponse?.data && satelliteImagesResponse.data.length > 0 ? (
              <SatelliteImageGallery
                projectId={projectId}
                images={satelliteImagesResponse.data}
                polygons={galleryPolygons}
                selectedPolygonId={selectedPolygonId}
              />
            ) : (
              <div className="flex items-center justify-center h-40 rounded-lg border border-dashed border-border">
                <span className="text-text-muted">
                  No satellite images yet. Run ingestion or upload manually.
                </span>
              </div>
            )}
          </div>
        )}

      </div>

      <UploadSatelliteImageModal
        projectId={projectId}
        open={uploadModalOpen}
        onClose={() => setUploadModalOpen(false)}
      />

      <ConfirmDialog
        open={confirmState !== null}
        title={confirmState?.title ?? ""}
        message={confirmState?.message ?? ""}
        confirmLabel="Delete"
        variant="danger"
        onConfirm={() => {
          confirmState?.onConfirm();
          setConfirmState(null);
        }}
        onCancel={() => setConfirmState(null)}
      />
    </div>
  );
}
