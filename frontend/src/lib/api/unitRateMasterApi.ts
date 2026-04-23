import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export type UnitRateCategory = "Equipment" | "Manpower" | "Material" | "Sub-Contract";
export type UnitRateSource = "RESOURCE" | "RESOURCE_ROLE";

export interface UnitRateMasterRow {
  id: string;
  source: UnitRateSource;
  category: UnitRateCategory | string;
  description: string;
  unit: string | null;
  budgetedRate: number | null;
  actualRate: number | null;
  variance: number | null;
  variancePercent: number | null;
  remarks: string | null;
}

export type UnitRateCategoryFilter = "EQUIPMENT" | "MANPOWER" | "MATERIAL" | "SUB_CONTRACT";

export const unitRateMasterApi = {
  list: (category?: UnitRateCategoryFilter) => {
    const qs = category ? `?category=${category}` : "";
    return apiClient
      .get<ApiResponse<UnitRateMasterRow[]>>(`/v1/unit-rate-master${qs}`)
      .then((r) => r.data);
  },
};
