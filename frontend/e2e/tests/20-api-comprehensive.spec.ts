import { test, expect } from '../fixtures/auth.fixture';
import {
  loginViaApi,
  getNh48ProjectId,
  getEpsTree,
  createProject,
  deleteProject,
  createWbs,
  createActivity,
  listBoqByProject,
  listResources,
  createResource,
  deleteResource,
  listStretches,
  createStretch,
  deleteStretch,
  listUnitRateMaster,
  listMaterialSources,
  createMaterialSource,
  deleteMaterialSource,
  createBaseline,
  deleteBaseline,
  listBaselines,
  calculateEvm,
  getCostSummary,
  runSchedule,
  listRisksByProject,
  createOrganisation,
  deleteOrganisation,
  listUsers,
  ApiContext,
} from '../fixtures/api.fixture';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

async function apiGet(path: string, token: string) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return { status: res.status, data: await res.json().catch(() => ({})) };
}

async function apiPost(path: string, body: unknown, token: string) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });
  return { status: res.status, data: await res.json().catch(() => ({})) };
}

async function apiDelete(path: string, token: string) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });
  return { status: res.status, data: await res.json().catch(() => ({})) };
}

test.describe('Comprehensive API Coverage', () => {
  let ctx: ApiContext;
  let projectId: string;

  test.beforeAll(async () => {
    ctx = await loginViaApi();
    projectId = await getNh48ProjectId(ctx);
  });

  // ===================== CALENDAR =====================

  test('CAL-001: GET /v1/calendars returns 200', async () => {
    const resp = await apiGet('/v1/calendars', ctx.token);
    expect(resp.status).toBe(200);
  });

  test('CAL-002: POST /v1/calendars creates calendar', async () => {
    const resp = await apiPost('/v1/calendars', {
      name: `E2E-Cal-${Date.now()}`,
      calendarType: 'PROJECT',
      standardWorkHoursPerDay: 8,
      standardWorkDaysPerWeek: 5,
    }, ctx.token);
    expect(resp.status).toBe(201);
    if (resp.data?.data?.id) {
      await apiDelete(`/v1/calendars/${resp.data.data.id}`, ctx.token);
    }
  });

  // ===================== COST ACCOUNTS =====================

  test('CST-001: GET /v1/cost-accounts returns 200', async () => {
    const resp = await apiGet('/v1/cost-accounts', ctx.token);
    expect(resp.status).toBe(200);
  });

  test('CST-002: POST /v1/cost-accounts creates account', async () => {
    const resp = await apiPost('/v1/cost-accounts', {
      code: `CA-E2E-${Date.now()}`,
      name: `Cost Account ${Date.now()}`,
    }, ctx.token);
    expect(resp.status).toBe(201);
    if (resp.data?.data?.id) {
      await apiDelete(`/v1/cost-accounts/${resp.data.data.id}`, ctx.token);
    }
  });

  // ===================== FUNDING SOURCES =====================

  test('FUND-001: GET /v1/funding-sources returns 200', async () => {
    const resp = await apiGet('/v1/funding-sources', ctx.token);
    expect(resp.status).toBe(200);
  });

  test('FUND-002: POST /v1/funding-sources creates source', async () => {
    const resp = await apiPost('/v1/funding-sources', {
      name: `Funding Source ${Date.now()}`,
      totalAmount: 100000,
    }, ctx.token);
    expect(resp.status).toBe(201);
  });

  // ===================== FINANCIAL PERIODS =====================

  test('FIN-001: GET /v1/financial-periods returns 200', async () => {
    const resp = await apiGet('/v1/financial-periods', ctx.token);
    expect(resp.status).toBe(200);
  });

  test('FIN-002: GET /v1/financial-periods/open returns 200', async () => {
    const resp = await apiGet('/v1/financial-periods/open', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== OBS =====================

  test('OBS-001: GET /v1/obs returns 200', async () => {
    const resp = await apiGet('/v1/obs', ctx.token);
    expect(resp.status).toBe(200);
  });

  test('OBS-002: POST /v1/obs creates node', async () => {
    const resp = await apiPost('/v1/obs', {
      code: `OBS-${Date.now()}`,
      name: `OBS Node ${Date.now()}`,
    }, ctx.token);
    expect(resp.status).toBe(201);
  });

  // ===================== RESOURCE TYPES =====================

  test('RT-001: GET /v1/resource-types returns 200', async () => {
    const resp = await apiGet('/v1/resource-types', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== RESOURCE CURVES =====================

  test('RC-001: GET /v1/resource-curves returns 200', async () => {
    const resp = await apiGet('/v1/resource-curves', ctx.token);
    expect(resp.status).toBe(200);
  });

  test('RC-002: GET /v1/resource-curves/defaults returns 200', async () => {
    const resp = await apiGet('/v1/resource-curves/defaults', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== PRODUCTIVITY NORMS =====================

  test('PN-001: GET /v1/productivity-norms returns 200', async () => {
    const resp = await apiGet('/v1/productivity-norms', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== UDF =====================

  test('UDF-001: GET /v1/udf returns 200', async () => {
    const resp = await apiGet('/v1/udf', ctx.token);
    expect(resp.status).toBe(200);
  });

  test('UDF-002: GET /v1/udf/fields returns 200', async () => {
    const resp = await apiGet('/v1/udf/fields', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== ORGANISATIONS =====================

  test('ORG-001: GET /v1/organisations returns 200', async () => {
    const resp = await apiGet('/v1/organisations', ctx.token);
    expect(resp.status).toBe(200);
  });

  test('ORG-002: GET /v1/organisations/as-contractors returns 200', async () => {
    const resp = await apiGet('/v1/organisations/as-contractors', ctx.token);
    expect(resp.status).toBe(200);
  });

  test('ORG-003: POST /v1/organisations creates organisation', async () => {
    const resp = await apiPost('/v1/organisations', {
      name: `E2E Org ${Date.now()}`,
      organisationType: 'CONTRACTOR',
    }, ctx.token);
    expect(resp.status).toBe(201);
    if (resp.data?.data?.id) {
      await apiDelete(`/v1/organisations/${resp.data.data.id}`, ctx.token);
    }
  });

  // ===================== UNITS OF MEASURE =====================

  test('UOM-001: GET /v1/admin/units-of-measure returns 200', async () => {
    const resp = await apiGet('/v1/admin/units-of-measure', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== CURRENCIES =====================

  test('CUR-001: GET /v1/admin/currencies returns 200', async () => {
    const resp = await apiGet('/v1/admin/currencies', ctx.token);
    expect(resp.status).toBe(200);
  });

  test('CUR-002: GET /v1/admin/currencies/convert returns 200', async () => {
    const resp = await apiGet('/v1/admin/currencies/convert?from=USD&to=INR&amount=100', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== ADMIN CATEGORIES =====================

  test('ADM-001: GET /v1/admin/categories returns 200', async () => {
    const resp = await apiGet('/v1/admin/categories', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== GLOBAL SETTINGS =====================

  test('SET-001: GET /v1/admin/settings returns 200', async () => {
    const resp = await apiGet('/v1/admin/settings', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== RISK TEMPLATES =====================

  test('RTPL-001: GET /v1/risk-templates returns 200', async () => {
    const resp = await apiGet('/v1/risk-templates', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== PORTFOLIOS =====================

  test('PORT-001: GET /v1/portfolios returns 200', async () => {
    const resp = await apiGet('/v1/portfolios', ctx.token);
    expect(resp.status).toBe(200);
  });

  test('PORT-002: POST /v1/portfolios creates portfolio', async () => {
    const resp = await apiPost('/v1/portfolios', {
      name: `E2E Portfolio ${Date.now()}`,
      description: 'Test portfolio',
    }, ctx.token);
    expect(resp.status).toBe(201);
    if (resp.data?.data?.id) {
      await apiDelete(`/v1/portfolios/${resp.data.data.id}`, ctx.token);
    }
  });

  // ===================== SCORING MODELS =====================

  test('SCORE-001: GET /v1/scoring-models returns 200', async () => {
    const resp = await apiGet('/v1/scoring-models', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== PROJECT SUB-ROUTES =====================

  test.describe('Project Sub-Routes (with valid project)', () => {
    test('PRJ-SUB-001: GET /projects/{id}/expenses returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/expenses`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-002: GET /projects/{id}/cash-flow returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/cash-flow`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-003: GET /projects/{id}/cost-summary returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/cost-summary`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-004: GET /projects/{id}/cost-periods returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/cost-periods`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-005: GET /projects/{id}/cost-forecast returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/cost-forecast`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-006: GET /projects/{id}/funding returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/funding`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-007: POST /projects/{id}/evm/calculate returns 200', async () => {
      const resp = await apiPost(`/v1/projects/${projectId}/evm/calculate`, {}, ctx.token);
      expect([200, 422]).toContain(resp.status);
    });

    test('PRJ-SUB-008: GET /projects/{id}/evm returns 200 or 404', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/evm`, ctx.token);
      expect([200, 404]).toContain(resp.status);
    });

    test('PRJ-SUB-009: GET /projects/{id}/evm/summary returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/evm/summary`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-010: GET /projects/{id}/evm/history returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/evm/history`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-011: GET /projects/{id}/baselines returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/baselines`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-012: GET /projects/{id}/risks/summary returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/risks/summary`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-013: GET /projects/{id}/risks/matrix returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/risks/matrix`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-014: GET /projects/{id}/weather returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/weather`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-015: GET /projects/{id}/dpr returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/dpr`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('PRJ-SUB-016: GET /projects/{id}/next-day-plan returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/next-day-plan`, ctx.token);
      expect(resp.status).toBe(200);
    });
  });

  // ===================== IMPORT-EXPORT =====================

  test.describe('Import-Export', () => {
    test('IMP-001: GET /v1/import-export/jobs returns 200', async () => {
      const resp = await apiGet('/v1/import-export/jobs', ctx.token);
      expect(resp.status).toBe(200);
    });
  });

  // ===================== DASHBOARDS =====================

  test.describe('Dashboards', () => {
    test('DASH-001: GET /v1/dashboards returns 200', async () => {
      const resp = await apiGet('/v1/dashboards', ctx.token);
      expect(resp.status).toBe(200);
    });

    test('DASH-002: GET /v1/dashboards/{tier} returns 200', async () => {
      const resp = await apiGet('/v1/dashboards/PORTFOLIO', ctx.token);
      expect(resp.status).toBe(200);
    });

    test('DASH-003: GET /v1/dashboards/kpi-definitions returns 200', async () => {
      const resp = await apiGet('/v1/dashboards/kpi-definitions', ctx.token);
      expect(resp.status).toBe(200);
    });
  });

  // ===================== REPORTS =====================

  test.describe('Reports', () => {
    test('RPT-001: GET /v1/reports returns 200', async () => {
      const resp = await apiGet('/v1/reports', ctx.token);
      expect(resp.status).toBe(200);
    });

    test('RPT-002: GET /v1/reports/definitions returns 200', async () => {
      const resp = await apiGet('/v1/reports/definitions', ctx.token);
      expect(resp.status).toBe(200);
    });
  });

  // ===================== PORTFOLIO REPORTS =====================

  test.describe('Portfolio Reports', () => {
    for (const endpoint of [
      '/v1/portfolio/evm-rollup',
      '/v1/portfolio/scorecard',
      '/v1/portfolio/delayed-projects',
      '/v1/portfolio/cost-overrun-projects',
      '/v1/portfolio/funding-utilization',
      '/v1/portfolio/contractor-league',
      '/v1/portfolio/risk-heatmap',
      '/v1/portfolio/cash-flow-outlook',
      '/v1/portfolio/compliance',
      '/v1/portfolio/schedule-health',
    ]) {
      test(`${endpoint} returns 200`, async () => {
        const resp = await apiGet(endpoint, ctx.token);
        expect(resp.status).toBe(200);
      });
    }
  });

  // ===================== WBS TEMPLATES =====================

  test('WBS-TPL-001: GET /v1/wbs-templates returns 200', async () => {
    const resp = await apiGet('/v1/wbs-templates', ctx.token);
    expect(resp.status).toBe(200);
  });

  // ===================== CONTRACT SUB-ROUTES =====================

  test.describe('Contracts', () => {
    test('CON-001: GET /projects/{id}/contracts returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/contracts`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('CON-002: GET /projects/{id}/tenders returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/tenders`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('CON-003: GET /projects/{id}/procurement-plans returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/procurement-plans`, ctx.token);
      expect(resp.status).toBe(200);
    });
  });

  // ===================== GIS =====================

  test.describe('GIS Endpoints', () => {
    test('GIS-001: GET /projects/{id}/gis/layers returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/gis/layers`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('GIS-002: GET /projects/{id}/gis/polygons returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/gis/polygons`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('GIS-003: GET /projects/{id}/gis/progress-snapshots returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/gis/progress-snapshots`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('GIS-004: GET /projects/{id}/gis/satellite-images returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/gis/satellite-images`, ctx.token);
      expect(resp.status).toBe(200);
    });
  });

  // ===================== MATERIAL & RESOURCE SUB-ROUTES =====================

  test.describe('Material & Resource Sub-Routes', () => {
    test('MAT-001: GET /projects/{id}/stock-register returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/stock-register`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('MAT-002: GET /projects/{id}/labour-returns returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/labour-returns`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('MAT-003: GET /projects/{id}/equipment-logs returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/equipment-logs`, ctx.token);
      expect(resp.status).toBe(200);
    });

    test('MAT-004: GET /projects/{id}/material-sources returns 200', async () => {
      const resp = await apiGet(`/v1/projects/${projectId}/material-sources`, ctx.token);
      expect(resp.status).toBe(200);
    });
  });

  // ===================== RESOURCE HIERARCHY =====================

  test('RES-001: GET /v1/resources/hierarchy/roots returns 200', async () => {
    const resp = await apiGet('/v1/resources/hierarchy/roots', ctx.token);
    expect(resp.status).toBe(200);
  });
});
