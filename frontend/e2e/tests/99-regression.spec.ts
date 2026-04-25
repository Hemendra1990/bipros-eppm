import { test, expect } from "../fixtures/auth.fixture";
import {
  loginViaApi,
  getNh48ProjectId,
  getEpsTree,
  createProject,
  deleteProject,
  createBaseline,
  deleteBaseline,
  listBaselines,
} from "../fixtures/api.fixture";

test.describe("Defect Regression Matrix", () => {
  test("BUG-001: Project create with finish-before-start → 422", async () => {
    const ctx = await loginViaApi();
    const eps = await getEpsTree(ctx);
    const firstEps = eps.data[0];

    let apiError: Error & { status?: number; response?: { error?: { details?: Array<{ field: string; reason: string }> } } } | null = null;
    try {
      await createProject(ctx, {
        code: `BUG001-${Date.now()}`,
        name: "BUG-001 Test",
        epsNodeId: firstEps.id,
        plannedStartDate: "2027-12-31",
        plannedFinishDate: "2026-01-01",
        priority: 5,
      });
    } catch (err) {
      apiError = err as Error & { status?: number; response?: { error?: { details?: Array<{ field: string; reason: string }> } } };
    }
    expect(apiError).not.toBeNull();
    expect(apiError!.status).toBe(422);
    const msg = JSON.stringify(apiError!.response ?? {});
    expect(msg.toLowerCase()).toContain("finish");
  });

  test("BUG-004: Project code SQL injection → rejected", async () => {
    const ctx = await loginViaApi();
    const eps = await getEpsTree(ctx);
    const firstEps = eps.data[0];

    let apiError: Error & { status?: number } | null = null;
    try {
      await createProject(ctx, {
        code: "QA'; DROP TABLE--",
        name: "BUG-004 Test",
        epsNodeId: firstEps.id,
        plannedStartDate: "2026-01-01",
        plannedFinishDate: "2027-12-31",
        priority: 5,
      });
    } catch (err) {
      apiError = err as Error & { status?: number };
    }
    expect(apiError).not.toBeNull();
    expect(apiError!.status).toBe(400);
  });

  test("BUG-010: Empty project cost summary has CPI null (not 1.0)", async () => {
    const ctx = await loginViaApi();
    const eps = await getEpsTree(ctx);
    const firstEps = eps.data[0];

    // Create a fresh empty project
    const project = await createProject(ctx, {
      code: `BUG010${Date.now()}`,
      name: "BUG-010 Empty Project",
      epsNodeId: firstEps.id,
      plannedStartDate: "2026-01-01",
      plannedFinishDate: "2027-12-31",
      priority: 5,
    });

    try {
      const resp = await fetch(
        `http://localhost:8080/v1/projects/${project.data.id}/cost-summary`,
        { headers: { Authorization: `Bearer ${ctx.token}` } }
      );
      const data = await resp.json();
      // CPI should be null when there are no actual costs
      expect(data.data.costPerformanceIndex).toBeNull();
    } finally {
      await deleteProject(ctx, project.data.id).catch(() => {});
    }
  });

  test("BUG-037: Baseline duplicate name → 422 DUPLICATE_CODE", async () => {
    const ctx = await loginViaApi();
    const projectId = await getNh48ProjectId(ctx);

    // Create first baseline
    const first = await createBaseline(ctx, projectId, {
      name: `BUG-037-${Date.now()}`,
      baselineType: "PROJECT",
    });

    try {
      // Try duplicate
      let apiError: Error & { status?: number; response?: { error?: { code?: string } } } | null = null;
      try {
        await createBaseline(ctx, projectId, {
          name: first.data.name,
          baselineType: "PROJECT",
        });
      } catch (err) {
        apiError = err as Error & { status?: number; response?: { error?: { code?: string } } };
      }
      expect(apiError).not.toBeNull();
      expect(apiError!.status).toBe(422);
      expect(apiError!.response?.error?.code).toBe("DUPLICATE_CODE");
    } finally {
      await deleteBaseline(ctx, projectId, first.data.id).catch(() => {});
    }
  });

  test("BUG-046: GET /v1/projects/{p}/relationships/{id} → 200", async () => {
    const ctx = await loginViaApi();
    const projectId = await getNh48ProjectId(ctx);

    // List relationships first
    const listResp = await fetch(
      `http://localhost:8080/v1/projects/${projectId}/relationships`,
      { headers: { Authorization: `Bearer ${ctx.token}` } }
    );
    const listData = await listResp.json();
    const relationships = listData.data ?? [];
    test.skip(relationships.length === 0, "No relationships to test BUG-046");

    const firstId = relationships[0].id;
    const getResp = await fetch(
      `http://localhost:8080/v1/projects/${projectId}/relationships/${firstId}`,
      { headers: { Authorization: `Bearer ${ctx.token}` } }
    );
    expect(getResp.status).toBe(200);
  });
});
