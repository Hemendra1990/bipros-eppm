import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface LlmProviderResponse {
  id: string;
  name: string;
  baseUrl: string;
  model: string;
  maxTokens: number;
  temperature: number;
  timeoutMs: number;
  authScheme: string;
  supportsNativeTools: boolean;
  isDefault: boolean;
  isActive: boolean;
}

export interface CreateLlmProviderRequest {
  name: string;
  baseUrl: string;
  apiKey: string;
  model: string;
  maxTokens?: number;
  temperature?: number;
  timeoutMs?: number;
  authScheme?: string;
  supportsNativeTools?: boolean;
  isDefault?: boolean;
  isActive?: boolean;
}

export interface UpdateLlmProviderRequest {
  name?: string;
  baseUrl?: string;
  apiKey?: string;
  model?: string;
  maxTokens?: number;
  temperature?: number;
  timeoutMs?: number;
  authScheme?: string;
  supportsNativeTools?: boolean;
  isDefault?: boolean;
  isActive?: boolean;
}

export interface ProviderTestResponse {
  ok: boolean;
  latencyMs: number;
  tokensIn: number;
  tokensOut: number;
  modelEcho: string | null;
  error: string | null;
}

export interface ChatRequest {
  conversationId?: string | null;
  projectId: string;
  module: string;
  message: string;
  imageUrl?: string | null;
}

export interface SseEvent {
  event: string;
  data: Record<string, unknown>;
}

export const aiApi = {
  listProviders: () =>
    apiClient.get<ApiResponse<LlmProviderResponse[]>>("/v1/admin/llm-providers").then((r) => r.data),

  getProvider: (id: string) =>
    apiClient.get<ApiResponse<LlmProviderResponse>>(`/v1/admin/llm-providers/${id}`).then((r) => r.data),

  createProvider: (data: CreateLlmProviderRequest) =>
    apiClient.post<ApiResponse<LlmProviderResponse>>("/v1/admin/llm-providers", data).then((r) => r.data),

  updateProvider: (id: string, data: UpdateLlmProviderRequest) =>
    apiClient.put<ApiResponse<LlmProviderResponse>>(`/v1/admin/llm-providers/${id}`, data).then((r) => r.data),

  deleteProvider: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/v1/admin/llm-providers/${id}`).then((r) => r.data),

  testProvider: (id: string) =>
    apiClient.post<ApiResponse<ProviderTestResponse>>(`/v1/admin/llm-providers/${id}/test`).then((r) => r.data),

  speechToText: (audioBlob: Blob) => {
    const token = typeof window !== "undefined" ? localStorage.getItem("access_token") : "";
    const form = new FormData();
    form.append("audio", audioBlob, "recording.webm");
    return fetch(`${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"}/v1/ai/speech-to-text`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: form,
    }).then(async (r) => {
      if (!r.ok) throw new Error(`STT ${r.status}`);
      const data = await r.json();
      return data.data as string;
    });
  },

  uploadImage: (conversationId: string, imageFile: File) => {
    const token = typeof window !== "undefined" ? localStorage.getItem("access_token") : "";
    const form = new FormData();
    form.append("conversationId", conversationId);
    form.append("image", imageFile);
    return fetch(`${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"}/v1/ai/images`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: form,
    }).then(async (r) => {
      if (!r.ok) throw new Error(`upload ${r.status}`);
      const data = await r.json();
      return data.data as { fileName: string; url: string; mimeType: string };
    });
  },

  streamChat: async function* (req: ChatRequest, signal: AbortSignal): AsyncGenerator<SseEvent> {
    const token = typeof window !== "undefined" ? localStorage.getItem("access_token") : "";
    const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"}/v1/ai/chat/stream`, {
      method: "POST",
      signal,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        Accept: "text/event-stream",
      },
      body: JSON.stringify(req),
    });
    if (!res.ok || !res.body) throw new Error(`stream ${res.status}`);
    const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
    let buf = "";
    const parseField = (line: string, prefix: string): string | null => {
      if (!line.startsWith(prefix)) return null;
      const rest = line.slice(prefix.length);
      return rest.startsWith(" ") ? rest.slice(1) : rest;
    };
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buf += value;
      const frames = buf.split(/\r?\n\r?\n/);
      buf = frames.pop() ?? "";
      for (const f of frames) {
        const lines = f.split(/\r?\n/);
        let event = "message";
        const dataLines: string[] = [];
        for (const line of lines) {
          if (!line || line.startsWith(":")) continue;
          const ev = parseField(line, "event:");
          if (ev !== null) {
            event = ev.trim();
            continue;
          }
          const d = parseField(line, "data:");
          if (d !== null) {
            dataLines.push(d);
          }
        }
        const data = dataLines.join("\n");
        if (data) {
          try {
            yield { event, data: JSON.parse(data) };
          } catch {
            yield { event, data: { raw: data } };
          }
        }
      }
    }
  },
};
