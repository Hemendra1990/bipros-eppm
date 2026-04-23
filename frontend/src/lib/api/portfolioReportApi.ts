import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface PortfolioEvmRow {
  projectId: string;
  projectCode: string;
  projectName: string;
  pv: number;
  ev: number;
  ac: number;
  cpi: number;
  spi: number;
  cv: number;
  sv: number;
  eac: number;
  bac: number;
}

export const portfolioReportApi = {
  getEvmRollup: () =>
    apiClient
      .get<ApiResponse<PortfolioEvmRow[]>>("/v1/portfolio/evm-rollup")
      .then((r) => r.data),
};
