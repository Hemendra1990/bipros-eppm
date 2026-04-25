# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: tests/99-regression.spec.ts >> Defect Regression Matrix >> BUG-004: Project code SQL injection → rejected
- Location: e2e/tests/99-regression.spec.ts:38:7

# Error details

```
Error: API 401: {"error":{"code":"UNAUTHORIZED","message":"Invalid username or password"},"meta":{"timestamp":"2026-04-25T03:48:41.328963Z","version":"0.1.0"}}
```

# Test source

```ts
  1   | import type { Page } from "@playwright/test";
  2   | 
  3   | const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  4   | 
  5   | export interface ApiContext {
  6   |   token: string;
  7   | }
  8   | 
  9   | async function apiPost<T>(path: string, body: unknown, token?: string): Promise<T> {
  10  |   const res = await fetch(`${API_BASE}${path}`, {
  11  |     method: "POST",
  12  |     headers: {
  13  |       "Content-Type": "application/json",
  14  |       ...(token ? { Authorization: `Bearer ${token}` } : {}),
  15  |     },
  16  |     body: JSON.stringify(body),
  17  |   });
  18  |   const data = await res.json().catch(() => ({}));
  19  |   if (!res.ok) {
> 20  |     const err = new Error(`API ${res.status}: ${JSON.stringify(data)}`);
      |                 ^ Error: API 401: {"error":{"code":"UNAUTHORIZED","message":"Invalid username or password"},"meta":{"timestamp":"2026-04-25T03:48:41.328963Z","version":"0.1.0"}}
  21  |     (err as Error & { status: number; response: unknown }).status = res.status;
  22  |     (err as Error & { status: number; response: unknown }).response = data;
  23  |     throw err;
  24  |   }
  25  |   return data as T;
  26  | }
  27  | 
  28  | async function apiGet<T>(path: string, token: string): Promise<T> {
  29  |   const res = await fetch(`${API_BASE}${path}`, {
  30  |     headers: { Authorization: `Bearer ${token}` },
  31  |   });
  32  |   const data = await res.json().catch(() => ({}));
  33  |   if (!res.ok) {
  34  |     const err = new Error(`API ${res.status}: ${JSON.stringify(data)}`);
  35  |     (err as Error & { status: number; response: unknown }).status = res.status;
  36  |     (err as Error & { status: number; response: unknown }).response = data;
  37  |     throw err;
  38  |   }
  39  |   return data as T;
  40  | }
  41  | 
  42  | async function apiDelete(path: string, token: string): Promise<void> {
  43  |   const res = await fetch(`${API_BASE}${path}`, {
  44  |     method: "DELETE",
  45  |     headers: { Authorization: `Bearer ${token}` },
  46  |   });
  47  |   if (!res.ok && res.status !== 404) {
  48  |     const data = await res.json().catch(() => ({}));
  49  |     throw new Error(`API DELETE ${res.status}: ${JSON.stringify(data)}`);
  50  |   }
  51  | }
  52  | 
  53  | export async function loginViaApi(): Promise<ApiContext> {
  54  |   const resp = await apiPost<{ data: { accessToken: string } }>("/v1/auth/login", {
  55  |     username: "admin",
  56  |     password: "admin123",
  57  |   });
  58  |   return { token: resp.data.accessToken };
  59  | }
  60  | 
  61  | export async function getNh48ProjectId(ctx: ApiContext): Promise<string> {
  62  |   const resp = await apiGet<{ data: { content: Array<{ id: string; code: string }> } }>(
  63  |     "/v1/projects?size=50",
  64  |     ctx.token
  65  |   );
  66  |   const project = resp.data.content.find((p) => p.code.includes("NHAI"));
  67  |   if (!project) throw new Error("NH-48 seed project not found");
  68  |   return project.id;
  69  | }
  70  | 
  71  | export async function createMaterial(
  72  |   ctx: ApiContext,
  73  |   projectId: string,
  74  |   body: { name: string; category: string; unit: string; specificationGrade?: string; minStockLevel?: number; reorderQuantity?: number; leadTimeDays?: number }
  75  | ) {
  76  |   return apiPost<{ data: { id: string; code: string } }>(`/v1/projects/${projectId}/materials`, body, ctx.token);
  77  | }
  78  | 
  79  | export async function deleteMaterial(ctx: ApiContext, materialId: string) {
  80  |   return apiDelete(`/v1/materials/${materialId}`, ctx.token);
  81  | }
  82  | 
  83  | export async function createGrn(
  84  |   ctx: ApiContext,
  85  |   projectId: string,
  86  |   body: { materialId: string; receivedDate: string; quantity: number; unitRate?: number }
  87  | ) {
  88  |   return apiPost<{ data: { id: string; grnNumber: string; amount: number } }>(`/v1/projects/${projectId}/grns`, body, ctx.token);
  89  | }
  90  | 
  91  | export async function createIssue(
  92  |   ctx: ApiContext,
  93  |   projectId: string,
  94  |   body: { materialId: string; issueDate: string; quantity: number; wastageQuantity?: number }
  95  | ) {
  96  |   return apiPost<{ data: { id: string; challanNumber: string } }>(`/v1/projects/${projectId}/issues`, body, ctx.token);
  97  | }
  98  | 
  99  | export async function getStockRegister(ctx: ApiContext, projectId: string) {
  100 |   return apiGet<{ data: Array<{ materialId: string; currentStock: number; stockValue: number; stockStatusTag: string; wastagePercent: number | null }> }>(
  101 |     `/v1/projects/${projectId}/stock-register`,
  102 |     ctx.token
  103 |   );
  104 | }
  105 | 
  106 | export async function runSchedule(ctx: ApiContext, projectId: string) {
  107 |   return apiPost<{ data: { totalActivities: number; criticalActivities: number; projectFinishDate: string; criticalPathLength: number | null } }>(
  108 |     `/v1/projects/${projectId}/schedule`,
  109 |     {},
  110 |     ctx.token
  111 |   );
  112 | }
  113 | 
  114 | export async function getCriticalPath(ctx: ApiContext, projectId: string) {
  115 |   return apiGet<{ data: Array<{ id: string; name: string; totalFloat: number; freeFloat?: number; isCritical?: boolean }> }>(
  116 |     `/v1/projects/${projectId}/schedule/critical-path`,
  117 |     ctx.token
  118 |   );
  119 | }
  120 | 
```