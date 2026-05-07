import { apiClient } from "./client";
import type { ApiResponse } from "../types";
import type {
  CreateDailyProgressReportRequest,
  DailyProgressReportResponse,
  DprAttachment,
  UpdateDailyProgressReportRequest,
} from "../types/dpr";

// Re-export so existing call sites (`import ... from "@/lib/api/dprApi"`) keep working.
export type {
  CreateDailyProgressReportRequest,
  DailyProgressReportResponse,
  DprAttachment,
  UpdateDailyProgressReportRequest,
} from "../types/dpr";

export interface DprListFilters {
  from?: string;
  to?: string;
  activity?: string;
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export const dprApi = {
  list: (projectId: string, filters: DprListFilters = {}) => {
    const params = new URLSearchParams();
    if (filters.from) params.set("from", filters.from);
    if (filters.to) params.set("to", filters.to);
    if (filters.activity) params.set("activity", filters.activity);
    const qs = params.toString() ? `?${params.toString()}` : "";
    return apiClient
      .get<ApiResponse<DailyProgressReportResponse[]>>(`/v1/projects/${projectId}/dpr${qs}`)
      .then((r) => r.data);
  },

  get: (projectId: string, id: string) =>
    apiClient
      .get<ApiResponse<DailyProgressReportResponse>>(`/v1/projects/${projectId}/dpr/${id}`)
      .then((r) => r.data),

  create: (projectId: string, request: CreateDailyProgressReportRequest) =>
    apiClient
      .post<ApiResponse<DailyProgressReportResponse>>(`/v1/projects/${projectId}/dpr`, request)
      .then((r) => r.data),

  update: (projectId: string, id: string, request: UpdateDailyProgressReportRequest) =>
    apiClient
      .put<ApiResponse<DailyProgressReportResponse>>(`/v1/projects/${projectId}/dpr/${id}`, request)
      .then((r) => r.data),

  delete: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/dpr/${id}`),

  // ─── Photo attachments ───────────────────────────────────────────────────────
  // Multi-image upload. The backend accepts parallel `files[]` and `captions[]` parts; we
  // serialize captions as same-index entries so a missing index = no caption for that file.

  uploadPhotos: (
    projectId: string,
    dprId: string,
    files: File[],
    captions?: Array<string | null | undefined>
  ) => {
    const form = new FormData();
    files.forEach((f) => form.append("files", f));
    if (captions) {
      // Send captions as text/plain Blobs (not raw strings). FormData.append(key, string)
      // produces a part with no Content-Type, which Spring's @RequestPart String[] resolver
      // can't deserialize without registering a custom converter. Tagging as text/plain makes
      // both @RequestPart and @RequestParam variants accept the value.
      captions.forEach((c) =>
        form.append("captions", new Blob([c ?? ""], { type: "text/plain" }))
      );
    }
    return apiClient
      .post<ApiResponse<DprAttachment[]>>(
        `/v1/projects/${projectId}/dpr/${dprId}/photos`,
        form,
        // Override the JSON default so axios picks the multipart boundary from the FormData.
        { headers: { "Content-Type": "multipart/form-data" } }
      )
      .then((r) => r.data);
  },

  listPhotos: (projectId: string, dprId: string) =>
    apiClient
      .get<ApiResponse<DprAttachment[]>>(`/v1/projects/${projectId}/dpr/${dprId}/photos`)
      .then((r) => r.data),

  deletePhoto: (projectId: string, dprId: string, photoId: string) =>
    apiClient.delete(`/v1/projects/${projectId}/dpr/${dprId}/photos/${photoId}`),

  /**
   * Fetches a JPEG/PNG/WebP/HEIC binary as an object URL suitable for {@code <img src>}.
   * The endpoint is JWT-protected, so we cannot point an `<img>` at it directly — we authenticate
   * the fetch ourselves, then turn the response into a blob URL. Caller is responsible for
   * revoking the URL when the image unmounts.
   */
  fetchPhotoBlobUrl: async (projectId: string, dprId: string, photoId: string): Promise<string> => {
    const token = typeof window !== "undefined" ? localStorage.getItem("access_token") : "";
    const res = await fetch(
      `${API_BASE_URL}/v1/projects/${projectId}/dpr/${dprId}/photos/${photoId}`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    if (!res.ok) throw new Error(`photo fetch ${res.status}`);
    const blob = await res.blob();
    return URL.createObjectURL(blob);
  },

  // ─── Voice form-fill ─────────────────────────────────────────────────────────

  voiceFill: async (
    projectId: string,
    audio: Blob,
    state: unknown,
    history: DprVoiceTurn[],
    dprId?: string | null
  ): Promise<DprVoiceFillResponse> => {
    const token = typeof window !== "undefined" ? localStorage.getItem("access_token") : "";
    const form = new FormData();
    form.append("audio", audio, "voice.webm");
    form.append("state", new Blob([JSON.stringify(state)], { type: "application/json" }));
    form.append("history", new Blob([JSON.stringify(history)], { type: "application/json" }));
    if (dprId) form.append("dprId", dprId);

    const res = await fetch(
      `${API_BASE_URL}/v1/projects/${projectId}/dpr/voice-fill`,
      {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
        body: form,
      }
    );
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`voice-fill ${res.status}: ${text || "request failed"}`);
    }
    const data: ApiResponse<DprVoiceFillResponse> = await res.json();
    if (!data.data) throw new Error("voice-fill returned empty body");
    return data.data;
  },
};

// ─── Voice form-fill types ──────────────────────────────────────────────────────

export interface DprVoiceTurn {
  role: "user" | "assistant";
  content: string;
}

export interface DprVoicePhotoCaption {
  photoId: string;
  caption: string;
}

/**
 * Patch shape returned by the backend voice form-fill endpoint. Mirrors {@code DprBaseFields}
 * keys so the form can deep-merge it directly. Every key is optional and may be {@code null}
 * (meaning "the user did not address this field this turn").
 */
export interface DprVoicePatch {
  reportDate?: string | null;
  supervisorResourceId?: string | null;
  supervisorName?: string | null;
  activityId?: string | null;
  activityName?: string | null;
  contractorName?: string | null;
  weatherCondition?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  shift?: "DAY" | "NIGHT" | null;
  approvalStatus?: "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED" | null;
  side?: "LHS" | "RHS" | "CENTER" | null;
  landmark?: string | null;
  chainageFromM?: number | null;
  chainageToM?: number | null;
  boqItemNo?: string | null;
  unit?: string | null;
  qtyExecuted?: number | null;
  remarks?: string | null;
  delayReason?: string | null;
  safetyObservation?: string | null;
  safetyIncidentType?: "NONE" | "NEAR_MISS" | "INCIDENT" | null;
  manpower?: Array<Record<string, unknown>>;
  equipment?: Array<Record<string, unknown>>;
  materials?: Array<Record<string, unknown>>;
}

export interface DprVoiceFillResponse {
  transcript: string;
  patch: DprVoicePatch;
  photoCaptions: DprVoicePhotoCaption[];
  followUpQuestion: string | null;
  complete: boolean;
  assistantTurn: DprVoiceTurn;
}
