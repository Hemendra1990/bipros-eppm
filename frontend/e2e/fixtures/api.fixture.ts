import type { Page } from "@playwright/test";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export interface ApiContext {
  token: string;
}

async function apiPost<T>(path: string, body: unknown, token?: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(`API ${res.status}: ${JSON.stringify(data)}`);
    (err as Error & { status: number; response: unknown }).status = res.status;
    (err as Error & { status: number; response: unknown }).response = data;
    throw err;
  }
  return data as T;
}

async function apiGet<T>(path: string, token: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const err = new Error(`API ${res.status}: ${JSON.stringify(data)}`);
    (err as Error & { status: number; response: unknown }).status = res.status;
    (err as Error & { status: number; response: unknown }).response = data;
    throw err;
  }
  return data as T;
}

async function apiDelete(path: string, token: string): Promise<void> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok && res.status !== 404) {
    const data = await res.json().catch(() => ({}));
    throw new Error(`API DELETE ${res.status}: ${JSON.stringify(data)}`);
  }
}

export async function loginViaApi(): Promise<ApiContext> {
  const resp = await apiPost<{ data: { accessToken: string } }>("/v1/auth/login", {
    username: "admin",
    password: "admin123",
  });
  return { token: resp.data.accessToken };
}

export async function getNh48ProjectId(ctx: ApiContext): Promise<string> {
  const resp = await apiGet<{ data: { content: Array<{ id: string; code: string }> } }>(
    "/v1/projects?size=50",
    ctx.token
  );
  const project = resp.data.content.find((p) => p.code.includes("NHAI"));
  if (!project) throw new Error("NH-48 seed project not found");
  return project.id;
}

export async function createMaterial(
  ctx: ApiContext,
  projectId: string,
  body: { name: string; category: string; unit: string; specificationGrade?: string; minStockLevel?: number; reorderQuantity?: number; leadTimeDays?: number }
) {
  return apiPost<{ data: { id: string; code: string } }>(`/v1/projects/${projectId}/materials`, body, ctx.token);
}

export async function deleteMaterial(ctx: ApiContext, materialId: string) {
  return apiDelete(`/v1/materials/${materialId}`, ctx.token);
}

export async function createGrn(
  ctx: ApiContext,
  projectId: string,
  body: { materialId: string; receivedDate: string; quantity: number; unitRate?: number }
) {
  return apiPost<{ data: { id: string; grnNumber: string; amount: number } }>(`/v1/projects/${projectId}/grns`, body, ctx.token);
}

export async function createIssue(
  ctx: ApiContext,
  projectId: string,
  body: { materialId: string; issueDate: string; quantity: number; wastageQuantity?: number }
) {
  return apiPost<{ data: { id: string; challanNumber: string } }>(`/v1/projects/${projectId}/issues`, body, ctx.token);
}

export async function getStockRegister(ctx: ApiContext, projectId: string) {
  return apiGet<{ data: Array<{ materialId: string; currentStock: number; stockValue: number; stockStatusTag: string; wastagePercent: number | null }> }>(
    `/v1/projects/${projectId}/stock-register`,
    ctx.token
  );
}

export async function runSchedule(ctx: ApiContext, projectId: string) {
  return apiPost<{ data: { totalActivities: number; criticalActivities: number; projectFinishDate: string; criticalPathLength: number | null } }>(
    `/v1/projects/${projectId}/schedule`,
    {},
    ctx.token
  );
}

export async function getCriticalPath(ctx: ApiContext, projectId: string) {
  return apiGet<{ data: Array<{ id: string; name: string; totalFloat: number; freeFloat?: number; isCritical?: boolean }> }>(
    `/v1/projects/${projectId}/schedule/critical-path`,
    ctx.token
  );
}

export async function getCostSummary(ctx: ApiContext, projectId: string) {
  return apiGet<{ data: { totalBudget: number; totalActual: number; materialProcurementCost: number | null; openStockValue: number | null; materialIssuedCost: number | null; costPerformanceIndex: number | null } }>(
    `/v1/projects/${projectId}/cost-summary`,
    ctx.token
  );
}

export async function calculateEvm(
  ctx: ApiContext,
  projectId: string,
  technique = "ACTIVITY_PERCENT_COMPLETE",
  etcMethod = "CPI_BASED"
) {
  return apiPost<{ data: { budgetAtCompletion: number; plannedValue: number; earnedValue: number; actualCost: number; scheduleVariance: number; costVariance: number; schedulePerformanceIndex: number; costPerformanceIndex: number; estimateAtCompletion: number; estimateToComplete: number } }>(
    `/v1/projects/${projectId}/evm/calculate`,
    { technique, etcMethod },
    ctx.token
  );
}

export async function createBaseline(
  ctx: ApiContext,
  projectId: string,
  body: { name: string; baselineType: string; description?: string }
) {
  return apiPost<{ data: { id: string; name: string; totalActivities: number; totalCost: number } }>(`/v1/projects/${projectId}/baselines`, body, ctx.token);
}

export async function deleteBaseline(ctx: ApiContext, projectId: string, baselineId: string) {
  return apiDelete(`/v1/projects/${projectId}/baselines/${baselineId}`, ctx.token);
}

export async function listBaselines(ctx: ApiContext, projectId: string) {
  return apiGet<{ data: Array<{ id: string; name: string; totalActivities: number; totalCost: number }> }>(
    `/v1/projects/${projectId}/baselines`,
    ctx.token
  );
}

export async function listRisksByProject(ctx: ApiContext, projectId: string) {
  return apiGet<{ data: Array<{ id: string; title: string; rag: string; probability: number; impact: number }> }>(
    `/v1/projects/${projectId}/risks`,
    ctx.token
  );
}

export async function createOrganisation(
  ctx: ApiContext,
  body: { name: string; organisationType: string; pan?: string; gstin?: string; city?: string; state?: string; pincode?: string; contactPersonName?: string; contactMobile?: string; contactEmail?: string; registrationStatus?: string }
) {
  return apiPost<{ data: { id: string; code: string } }>("/v1/organisations", body, ctx.token);
}

export async function deleteOrganisation(ctx: ApiContext, id: string) {
  return apiDelete(`/v1/organisations/${id}`, ctx.token);
}

export async function listUsers(ctx: ApiContext, page = 0, size = 50) {
  return apiGet<{ data: { content: Array<{ id: string; username: string; employeeCode?: string | null; mobile?: string | null; department?: string | null; presenceStatus?: string | null; roles: string[] }> } }>(
    `/v1/users?page=${page}&size=${size}`,
    ctx.token
  );
}

export async function updateUser(ctx: ApiContext, userId: string, body: { mobile?: string; department?: string; presenceStatus?: string; joiningDate?: string }) {
  return apiPost<{ data: { id: string; mobile?: string | null; department?: string | null; presenceStatus?: string | null } }>(`/v1/users/${userId}`, body, ctx.token);
}

export async function listBoqByProject(ctx: ApiContext, projectId: string) {
  return apiGet<{ data: Array<{ id: string; itemCode: string; description: string; boqQty: number; boqRate: number; boqAmount: number; budgetedRate: number; budgetedAmount: number; qtyExecuted: number; percentComplete: number; status: string }> }>(
    `/v1/projects/${projectId}/boq`,
    ctx.token
  );
}

export async function listResources(ctx: ApiContext, page = 0, size = 100) {
  return apiGet<{ data: { content: Array<{ id: string; code: string; name: string; resourceType: string; status: string }> } }>(
    `/v1/resources?page=${page}&size=${size}`,
    ctx.token
  );
}

export async function createResource(
  ctx: ApiContext,
  body: { name: string; resourceType: string; maxUnitsPerDay?: number; hourlyRate?: number; costPerUse?: number }
) {
  return apiPost<{ data: { id: string; code: string } }>("/v1/resources", body, ctx.token);
}

export async function deleteResource(ctx: ApiContext, id: string) {
  return apiDelete(`/v1/resources/${id}`, ctx.token);
}

export async function listStretches(ctx: ApiContext, projectId: string) {
  return apiGet<{ data: Array<{ id: string; stretchCode: string; name: string | null; fromChainageM: number; toChainageM: number; lengthM: number | null; status: string | null }> }>(
    `/v1/projects/${projectId}/stretches`,
    ctx.token
  );
}

export async function deleteStretch(ctx: ApiContext, id: string) {
  return apiDelete(`/v1/stretches/${id}`, ctx.token);
}

export async function createStretch(
  ctx: ApiContext,
  projectId: string,
  body: { name?: string; fromChainageM: number; toChainageM: number; packageCode?: string; targetDate?: string; status?: string }
) {
  return apiPost<{ data: { id: string; stretchCode: string; lengthM: number | null } }>(`/v1/projects/${projectId}/stretches`, body, ctx.token);
}

export async function listUnitRateMaster(ctx: ApiContext, page = 0, size = 100) {
  return apiGet<{ data: { content: Array<{ id: string; resourceName: string; budgetedRate: number; actualRate: number; variancePercent: number | null }> } }>(
    `/v1/unit-rate-master?page=${page}&size=${size}`,
    ctx.token
  );
}

export async function listMaterialSources(ctx: ApiContext, projectId: string) {
  return apiGet<{ data: Array<{ id: string; sourceCode: string; name: string | null; sourceType: string; labTestStatus: string | null }> }>(
    `/v1/projects/${projectId}/material-sources`,
    ctx.token
  );
}

export async function createMaterialSource(
  ctx: ApiContext,
  projectId: string,
  body: { sourceType: string; name: string; village?: string; district?: string; state?: string; distanceKm?: number; approvedQuantity?: number; approvedQuantityUnit?: string; cbrAveragePercent?: number; mddGcc?: number; labTests?: Array<{ testName: string; standardReference?: string; resultValue?: number; resultUnit?: string; passed?: boolean; testDate?: string }> }
) {
  return apiPost<{ data: { id: string; sourceCode: string; labTestStatus: string | null } }>(`/v1/projects/${projectId}/material-sources`, body, ctx.token);
}

export async function deleteMaterialSource(ctx: ApiContext, id: string) {
  return apiDelete(`/v1/material-sources/${id}`, ctx.token);
}

export async function getEpsTree(ctx: ApiContext) {
  return apiGet<{ data: Array<{ id: string; code: string; name: string; children: unknown[] }> }>("/v1/eps", ctx.token);
}

export async function createProject(ctx: ApiContext, body: Record<string, unknown>) {
  return apiPost<{ data: { id: string; code: string; totalLengthKm: number | null } }>("/v1/projects", body, ctx.token);
}

export async function deleteProject(ctx: ApiContext, id: string) {
  return apiDelete(`/v1/projects/${id}`, ctx.token);
}

export async function createWbs(ctx: ApiContext, projectId: string, body: Record<string, unknown>) {
  return apiPost<{ data: { id: string; code: string } }>(`/v1/projects/${projectId}/wbs`, body, ctx.token);
}

export async function createActivity(ctx: ApiContext, projectId: string, body: Record<string, unknown>) {
  return apiPost<{ data: { id: string; code: string } }>(`/v1/projects/${projectId}/activities`, body, ctx.token);
}
