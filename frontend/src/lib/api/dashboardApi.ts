import { apiClient } from '@/lib/api/client'

export interface DashboardConfig {
  id: string
  tier: 'EXECUTIVE' | 'PROGRAMME' | 'OPERATIONAL' | 'FIELD'
  layoutConfig: Record<string, unknown>
}

export interface KpiDefinition {
  id: string
  name: string
  code: string
  formula: string
  unit: string
  greenThreshold?: number
  amberThreshold?: number
  redThreshold?: number
  moduleSource: string
  isActive: boolean
}

export interface KpiSnapshot {
  id: string
  kpiDefinitionId: string
  projectId: string
  value: number
  status: 'GREEN' | 'AMBER' | 'RED'
  calculatedAt: string
}

export interface CreateKpiDefinitionRequest {
  name: string
  code: string
  formula: string
  unit: string
  greenThreshold?: number
  amberThreshold?: number
  redThreshold?: number
  moduleSource: string
  isActive: boolean
}

export const dashboardApi = {
  // Dashboard Config
  getDashboardByTier: (tier: string) =>
    apiClient.get<DashboardConfig>(`/v1/dashboards/${tier}`),

  // KPI Snapshots by Tier
  getKpisByTierAndProject: (tier: string, projectId?: string) => {
    const url = projectId
      ? `/v1/dashboards/${tier}/kpis?projectId=${projectId}`
      : `/v1/dashboards/${tier}/kpis`
    return apiClient.get<KpiSnapshot[]>(url)
  },

  // KPI Definitions
  getKpiDefinitions: () =>
    apiClient.get<KpiDefinition[]>(`/v1/dashboards/kpi-definitions`),

  createKpiDefinition: (request: CreateKpiDefinitionRequest) =>
    apiClient.post<KpiDefinition>(`/v1/dashboards/kpi-definitions`, request),

  // Project-specific KPIs
  getProjectKpiSnapshots: (projectId: string) =>
    apiClient.get<KpiSnapshot[]>(`/v1/dashboards/projects/${projectId}/kpi-snapshots`),

  calculateProjectKpis: (projectId: string) =>
    apiClient.post<KpiSnapshot[]>(`/v1/dashboards/projects/${projectId}/kpi-snapshots/calculate`, {}),
}
