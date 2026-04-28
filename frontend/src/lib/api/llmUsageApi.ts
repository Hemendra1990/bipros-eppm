import { apiClient } from "./client";
import type { ApiResponse, UsageSummaryResponse } from "@/lib/types";

/**
 * Per-user usage rollup for the Usage tab on /settings/llm-providers.
 * Backend: GET /v1/llm-providers/me/usage. Defaults to last 30 days.
 */
export const llmUsageApi = {
  fetchMyUsage: (params?: { from?: string; to?: string }) => {
    const search = new URLSearchParams();
    if (params?.from) search.set("from", params.from);
    if (params?.to) search.set("to", params.to);
    const qs = search.toString();
    const url = qs
      ? `/v1/llm-providers/me/usage?${qs}`
      : `/v1/llm-providers/me/usage`;
    return apiClient
      .get<ApiResponse<UsageSummaryResponse>>(url)
      .then((r) => r.data);
  },
};
