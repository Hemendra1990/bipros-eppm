import { apiClient } from "./client";
import type { ApiResponse } from "../types";

/** Backend sentinel (MarginRollupService.OTHER_ROW_LABEL) for the reconciling row that books
 *  non-execution cost (general expenses + procurement) so each breakdown foots to total actual cost.
 *  MUST stay byte-identical to the backend constant. */
export const OTHER_ROW_LABEL = "Other (general expenses & unattributed)";

export interface MarginItem {
  boqItemId: string | null;
  itemNo: string;
  description: string;
  unit: string | null;
  qtyExecuted: number | null;
  rate: number | null;
  revenue: number;
  actualCost: number;
  margin: number;
  marginPct: number | null;
}

export interface MarginActivity {
  activity: string;
  revenue: number;
  actualCost: number;
  margin: number;
  marginPct: number | null;
}

export interface MarginPeriod {
  periodId: string;
  periodName: string;
  periodType: string | null;
  startDate: string;
  endDate: string;
  revenue: number;
  actualCost: number;
  margin: number;
  marginPct: number | null;
}

export interface MarginSummary {
  revenue: number;
  actualCost: number;
  margin: number;
  marginPct: number | null;
}

export type MarginScope = "budgeted" | "boq";

function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

export const marginApi = {
  /** Excel download for the P&L screens (Access-Output row 5). Server gate: COST.READ + REPORT.EXPORT. */
  downloadExcel: async (projectId: string, scope: MarginScope, cadence: string) => {
    const res = await apiClient.get<Blob>(
      `/v1/projects/${projectId}/pnl/${scope}/export.xlsx`,
      { params: { periodType: cadence }, responseType: "blob" },
    );
    triggerBlobDownload(res.data, `pnl-${scope}.xlsx`);
  },

  items: (projectId: string, scope: MarginScope) =>
    apiClient
      .get<ApiResponse<MarginItem[]>>(`/v1/projects/${projectId}/pnl/${scope}/items`)
      .then((r) => r.data),

  activities: (projectId: string, scope: MarginScope) =>
    apiClient
      .get<ApiResponse<MarginActivity[]>>(`/v1/projects/${projectId}/pnl/${scope}/activities`)
      .then((r) => r.data),

  periods: (projectId: string, scope: MarginScope, periodType: "D" | "W" | "M") =>
    apiClient
      .get<ApiResponse<MarginPeriod[]>>(`/v1/projects/${projectId}/pnl/${scope}/periods`, {
        params: { periodType },
      })
      .then((r) => r.data),

  summary: (projectId: string, scope: MarginScope) =>
    apiClient
      .get<ApiResponse<MarginSummary>>(`/v1/projects/${projectId}/pnl/${scope}/summary`)
      .then((r) => r.data),
};
