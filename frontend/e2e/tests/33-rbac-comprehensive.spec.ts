import {
  test,
  expect,
  login,
  loginAs,
  loginAsSeeded,
  decodeJwt,
  getE2eProjectId,
} from '../fixtures/auth.fixture';

/**
 * RBAC end-to-end suite (30 scenarios) for the 7-phase RBAC overhaul on
 * feat/ai-scope-and-rate-hardening (commits c3aedba…5831b27). Backed by
 * `docs/superpowers/specs/2026-05-14-rbac-e2e-tests-design.md`.
 *
 * Requires: backend running with IcpmsPhaseASeeder applied (ChangeMe@2026
 * password for all ICPMS users); Playwright globalSetup has provisioned
 * the four e2e_* profile users.
 *
 * Two known boundaries the suite intentionally pokes at; failures here
 * surface real gaps rather than test bugs:
 *  - The JWT `perms` claim is a sorted CSV string (JwtTokenProvider:75 —
 *    `String.join(",", new TreeSet<>(perms))`), not an array. Tests that
 *    assert on `perms` parse it before comparing.
 *  - Block D test 17 assumes `dmicdc.pd.n03`'s corridor scope grants
 *    ProjectMember-equivalent access on N03 projects. If the evaluator
 *    only consults ProjectMember rows, the test fails — that is the bug,
 *    not the test.
 */

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

interface JwtClaims {
  perms?: string;
  roles?: string;
  sub?: string;
}

interface AuthLoginResponse {
  data: { accessToken: string; refreshToken: string };
}

interface ProjectResponse {
  id: string;
  code: string;
  name: string;
}

interface PagedProjects {
  data: { content: ProjectResponse[] };
}

interface PagedUsers {
  data: { content: Array<{ id: string; username: string }> };
}

interface PagedDprs {
  data: { content: Array<{ id: string; supervisorName?: string | null }> };
}

function parsePermsClaim(claim: string | undefined | null): string[] {
  if (!claim) return [];
  return claim
    .split(',')
    .map((c) => c.trim())
    .filter((c) => c.length > 0);
}

async function listProjects(
  request: import('@playwright/test').APIRequestContext,
  token: string,
  size = 100,
): Promise<ProjectResponse[]> {
  const res = await request.get(`${API_BASE}/v1/projects?page=0&size=${size}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) {
    throw new Error(`GET /v1/projects failed: ${res.status()} ${await res.text()}`);
  }
  const body = (await res.json()) as PagedProjects;
  return body.data.content;
}

async function adminToken(
  request: import('@playwright/test').APIRequestContext,
): Promise<string> {
  const res = await request.post(`${API_BASE}/v1/auth/login`, {
    data: { username: 'admin', password: 'admin123' },
    headers: { 'Content-Type': 'application/json' },
  });
  if (!res.ok()) {
    throw new Error(`admin login failed: ${res.status()} ${await res.text()}`);
  }
  const body = (await res.json()) as AuthLoginResponse;
  return body.data.accessToken;
}

async function findUserId(
  request: import('@playwright/test').APIRequestContext,
  token: string,
  username: string,
): Promise<string | null> {
  const res = await request.get(`${API_BASE}/v1/users?page=0&size=500`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) return null;
  const body = (await res.json()) as PagedUsers;
  return body.data.content.find((u) => u.username === username)?.id ?? null;
}

async function firstAccessibleProjectId(
  page: import('@playwright/test').Page,
  token: string,
): Promise<string | null> {
  const res = await page.request.get(`${API_BASE}/v1/projects?page=0&size=5`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok()) return null;
  const body = (await res.json()) as PagedProjects;
  return body.data?.content?.[0]?.id ?? null;
}

test.describe('RBAC Block A — /v1/auth/me permissions claim', () => {
  test('A1: admin permissions include ADMIN_USER.CREATE, ADMIN_USER.UPDATE, ADMIN_PROFILE.CREATE', async ({
    page,
  }) => {
    await login(page, 'admin', 'admin123');
    const token = await page.evaluate(() => localStorage.getItem('access_token'));
    expect(token, 'admin access_token must be persisted by login()').toBeTruthy();

    const meRes = await page.request.get(`${API_BASE}/v1/users/me`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(meRes.ok(), `/v1/users/me failed: ${meRes.status()}`).toBe(true);

    const body = (await meRes.json()) as { data: { permissions?: string[] } };
    const perms = body.data.permissions ?? [];

    expect(perms).toEqual(
      expect.arrayContaining(['ADMIN_USER.CREATE', 'ADMIN_USER.UPDATE', 'ADMIN_PROFILE.CREATE']),
    );
  });

  test('A2: aadhaar.citizen permissions contain no *.CREATE / *.DELETE / *.MANAGE codes', async ({
    page,
  }) => {
    const { user } = await loginAsSeeded(page, 'aadhaar.citizen');
    const perms = ((user.permissions as string[] | undefined) ?? []).slice();

    const writeCodes = perms.filter((c) => /\.(CREATE|DELETE|MANAGE)$/.test(c));
    expect(
      writeCodes,
      `citizen viewer leaked write permissions: ${writeCodes.join(', ')}`,
    ).toEqual([]);
  });

  test('A3: JWT perms claim (CSV) equals /v1/users/me.permissions[] set (dmicdc.pd.n03)', async ({
    page,
  }) => {
    const { accessToken, user } = await loginAsSeeded(page, 'dmicdc.pd.n03');
    const claims = decodeJwt<JwtClaims>(accessToken);
    const jwtPerms = new Set(parsePermsClaim(claims.perms));
    const apiPerms = new Set((user.permissions as string[] | undefined) ?? []);

    expect(jwtPerms.size, 'JWT perms claim must be non-empty for a seeded PD').toBeGreaterThan(0);
    expect([...jwtPerms].sort()).toEqual([...apiPerms].sort());
  });

  test('A4: refresh token preserves perms set (aecom.pmc.lead)', async ({ page }) => {
    const { accessToken: original, refreshToken } = await loginAsSeeded(page, 'aecom.pmc.lead');
    const before = new Set(parsePermsClaim(decodeJwt<JwtClaims>(original).perms));
    expect(before.size, 'pre-refresh perms must be non-empty').toBeGreaterThan(0);

    const refreshRes = await page.request.post(`${API_BASE}/v1/auth/refresh`, {
      data: { refreshToken },
      headers: { 'Content-Type': 'application/json' },
    });
    expect(refreshRes.ok(), `refresh failed: ${refreshRes.status()}`).toBe(true);

    const body = (await refreshRes.json()) as AuthLoginResponse;
    const after = new Set(parsePermsClaim(decodeJwt<JwtClaims>(body.data.accessToken).perms));

    expect([...after].sort()).toEqual([...before].sort());
  });

  test('A5: freshly registered user lands on the VIEWER row of RolePermissionMatrix', async ({
    page,
  }) => {
    const stamp = Date.now();
    const username = `pw_block_a_${stamp}`;
    const password = 'PlayPa55!';
    const email = `${username}@bipros.test`;

    const reg = await page.request.post(`${API_BASE}/v1/auth/register`, {
      data: { username, email, password, firstName: 'PW', lastName: 'BlockA' },
      headers: { 'Content-Type': 'application/json' },
    });
    expect(reg.ok(), `register failed: ${reg.status()} ${await reg.text()}`).toBe(true);

    const { user } = await loginAsSeeded(page, username, password);
    const perms = ((user.permissions as string[] | undefined) ?? []).slice();
    const roles = ((user.roles as string[] | undefined) ?? []).map((r) => r.toUpperCase());

    expect(roles.some((r) => r.includes('VIEWER'))).toBe(true);
    const writeCodes = perms.filter((c) =>
      /\.(CREATE|DELETE|MANAGE|UPDATE|APPROVE|SUBMIT|REJECT)$/.test(c),
    );
    expect(
      writeCodes,
      `freshly registered VIEWER leaked write perms: ${writeCodes.join(', ')}`,
    ).toEqual([]);
  });
});

test.describe('RBAC Block B — Sidebar permission gating', () => {
  test.beforeEach(async ({ page }) => {
    // Wipe any persisted sidebar-group collapse state so admin items aren't
    // hidden purely because a previous test collapsed the group.
    await page.addInitScript(() => {
      try {
        localStorage.removeItem('bipros.sidebar.groups.v1');
      } catch {
        /* test-fixture only */
      }
    });
  });

  test('B6: admin sidebar contains /admin/users, /admin/roles, /admin/profiles', async ({
    page,
  }) => {
    await login(page, 'admin', 'admin123');
    await page.goto('/');
    const sidebar = page.locator('aside').first();
    await expect(sidebar).toBeVisible({ timeout: 15_000 });

    for (const href of ['/admin/users', '/admin/roles', '/admin/profiles']) {
      await expect(
        sidebar.locator(`a[href="${href}"]`).first(),
        `admin sidebar must contain ${href}`,
      ).toBeVisible({ timeout: 10_000 });
    }
  });

  test('B7: aadhaar.citizen sidebar has zero /admin/ links in DOM', async ({ page }) => {
    await loginAsSeeded(page, 'aadhaar.citizen');
    await page.goto('/');
    const sidebar = page.locator('aside').first();
    await expect(sidebar).toBeVisible({ timeout: 15_000 });

    const adminLinks = sidebar.locator('a[href^="/admin/"]');
    expect(await adminLinks.count(), 'citizen viewer must not see any /admin/* nav').toBe(0);
  });

  test('B8: aecom.pmc.lead sidebar has no /admin/users; DPR/activity/project nav present', async ({
    page,
  }) => {
    await loginAsSeeded(page, 'aecom.pmc.lead');
    await page.goto('/');
    const sidebar = page.locator('aside').first();
    await expect(sidebar).toBeVisible({ timeout: 15_000 });

    expect(
      await sidebar.locator('a[href="/admin/users"]').count(),
      'PMC Lead must not see /admin/users (admin group is ROLE_ADMIN-gated)',
    ).toBe(0);

    const projectishLink = sidebar
      .getByRole('link', { name: /dpr|activity|activities|projects/i })
      .first();
    await expect(
      projectishLink,
      'PMC Lead should see at least a project/DPR/activity nav entry',
    ).toBeVisible({ timeout: 10_000 });
  });

  test('B9: cag.auditor sidebar has no "New" / "Create" / "Add" buttons', async ({ page }) => {
    await loginAsSeeded(page, 'cag.auditor');
    await page.goto('/');
    const sidebar = page.locator('aside').first();
    await expect(sidebar).toBeVisible({ timeout: 15_000 });

    const writeButtons = sidebar.getByRole('button', { name: /^(\s*)(new|create|add)\b/i });
    expect(
      await writeButtons.count(),
      'auditor sidebar must not expose new/create/add buttons',
    ).toBe(0);
  });

  test('B10: SITE_MANAGER profile sees no /admin/ href in sidebar', async ({ page }) => {
    await loginAs(page, 'SITE_MANAGER');
    await page.goto('/');
    const sidebar = page.locator('aside').first();
    await expect(sidebar).toBeVisible({ timeout: 15_000 });

    const adminLinks = sidebar.locator('a[href^="/admin/"]');
    const hrefs = await adminLinks.evaluateAll((els) =>
      (els as HTMLAnchorElement[]).map((a) => a.getAttribute('href') ?? ''),
    );
    expect(hrefs, `SITE_MANAGER leaked /admin/ nav: ${hrefs.join(', ')}`).toEqual([]);
  });
});

test.describe('RBAC Block C — Admin route guarding', () => {
  test('C11: admin opens /admin/users — heading visible', async ({ page }) => {
    await login(page);
    await page.goto('/admin/users');
    await expect(
      page.getByRole('heading', { name: /users|user management/i, level: 1 }),
    ).toBeVisible({ timeout: 15_000 });
  });

  test('C12: lnt.pm.n03 blocked from /admin/users', async ({ page }) => {
    await loginAsSeeded(page, 'lnt.pm.n03');
    await page.goto('/admin/users');
    const url = page.url();
    if (/forbidden|auth\/login/.test(url)) {
      expect(url).toMatch(/forbidden|auth\/login/);
    } else {
      await expect(
        page.locator('text=/forbidden|access denied|not authoriz/i').first(),
      ).toBeVisible({ timeout: 10_000 });
    }
  });

  test('C13: admin opens /admin/roles — at least 20 role rows visible', async ({ page }) => {
    await login(page);
    await page.goto('/admin/roles');
    await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);
    const rows = await page.locator('table tbody tr').count().catch(() => 0);
    expect(rows).toBeGreaterThanOrEqual(20);
  });

  test('C14: dmicdc.pd.n03 blocked from /admin/roles', async ({ page }) => {
    await loginAsSeeded(page, 'dmicdc.pd.n03');
    await page.goto('/admin/roles');
    const url = page.url();
    if (/forbidden|auth\/login/.test(url)) {
      expect(url).toMatch(/forbidden|auth\/login/);
    } else {
      await expect(
        page.locator('text=/forbidden|access denied|not authoriz/i').first(),
      ).toBeVisible({ timeout: 10_000 });
    }
  });

  test('C15: admin opens /admin/profiles — at least 20 profile rows visible', async ({ page }) => {
    await login(page);
    await page.goto('/admin/profiles');
    await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);
    const rows = await page.locator('table tbody tr').count().catch(() => 0);
    expect(rows).toBeGreaterThanOrEqual(20);
  });

  test('C16: cag.auditor blocked from /admin/profiles', async ({ page }) => {
    await loginAsSeeded(page, 'cag.auditor');
    await page.goto('/admin/profiles');
    const url = page.url();
    if (/forbidden|auth\/login/.test(url)) {
      expect(url).toMatch(/forbidden|auth\/login/);
    } else {
      await expect(
        page.locator('text=/forbidden|access denied|not authoriz/i').first(),
      ).toBeVisible({ timeout: 10_000 });
    }
  });
});

test.describe('RBAC Block D — Project-scoped permissions', () => {
  test('D17: dmicdc.pd.n03 can fetch own project members but not stranger project', async ({
    page,
  }) => {
    // If this test fails at the members fetch, the gap is in the evaluator: PD's
    // corridor scope is not being translated into project-scoped access.
    const { accessToken: pdToken } = await loginAsSeeded(page, 'dmicdc.pd.n03');
    const pdProjects = await listProjects(page.request, pdToken);
    expect(pdProjects.length).toBeGreaterThan(0);
    const myProj = pdProjects[0].id;

    const adminTok = await adminToken(page.request);
    const allProjects = await listProjects(page.request, adminTok);
    const stranger = allProjects.find((p) => p.id !== myProj);
    expect(stranger, 'admin should see at least one project different from PD N03').toBeTruthy();
    const strangerProj = stranger!.id;

    const okRes = await page.request.get(`${API_BASE}/v1/projects/${myProj}/members`, {
      headers: { Authorization: `Bearer ${pdToken}` },
    });
    expect(okRes.status()).toBe(200);

    const denyRes = await page.request.get(
      `${API_BASE}/v1/projects/${strangerProj}/members`,
      { headers: { Authorization: `Bearer ${pdToken}` } },
    );
    expect(denyRes.status()).toBe(403);
  });

  test('D18: e2e_smanager reads DPRs in enrolled project but 403 in stranger', async ({
    page,
  }) => {
    const enrolled = getE2eProjectId();
    expect(enrolled, 'globalSetup must have provisioned a project id').toBeTruthy();

    const { accessToken: sToken } = await loginAsSeeded(page, 'e2e_smanager', 'e2e-Site!123');

    const adminTok = await adminToken(page.request);
    const allProjects = await listProjects(page.request, adminTok);
    const stranger = allProjects.find((p) => p.id !== enrolled);
    expect(stranger, 'need a project distinct from the enrolled e2e project').toBeTruthy();

    const okRes = await page.request.get(`${API_BASE}/v1/projects/${enrolled}/dpr`, {
      headers: { Authorization: `Bearer ${sToken}` },
    });
    expect(okRes.status()).toBe(200);

    const denyRes = await page.request.get(`${API_BASE}/v1/projects/${stranger!.id}/dpr`, {
      headers: { Authorization: `Bearer ${sToken}` },
    });
    // If this returns 200 not 403, the DPR controller is missing its project-scope guard —
    // documented as a known gap in the spec; the test surfaces it intentionally.
    expect(denyRes.status()).toBe(403);
  });

  test('D19: aadhaar.citizen visiting /projects/{id}/members is denied', async ({ page }) => {
    const adminTok = await adminToken(page.request);
    const allProjects = await listProjects(page.request, adminTok);
    expect(allProjects.length).toBeGreaterThan(0);
    const anyId = allProjects[0].id;

    await loginAsSeeded(page, 'aadhaar.citizen');
    await page.goto(`/projects/${anyId}/members`);

    const url = page.url();
    if (/forbidden|auth\/login/.test(url)) {
      expect(url).toMatch(/forbidden|auth\/login/);
    } else {
      await expect(
        page.locator('text=/forbidden|access denied|not authoriz|403/i').first(),
      ).toBeVisible({ timeout: 10_000 });
    }
  });

  test('D20: PM sees Add DPR button; VIEWER on same project does not', async ({ page }) => {
    const { accessToken: pmToken } = await loginAsSeeded(page, 'lnt.pm.n03');
    const pmProjects = await listProjects(page.request, pmToken);
    expect(pmProjects.length).toBeGreaterThan(0);
    const projId = pmProjects[0].id;

    await page.goto(`/projects/${projId}/dpr`);
    await expect(
      page.getByRole('button', { name: /add dpr|new dpr|create dpr/i }).first(),
    ).toBeVisible({ timeout: 15_000 });

    await page.context().clearCookies();
    await loginAsSeeded(page, 'lnt.sitein');
    await page.goto(`/projects/${projId}/dpr`);
    await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);
    const btnCount = await page
      .getByRole('button', { name: /add dpr|new dpr|create dpr/i })
      .count();
    expect(btnCount).toBe(0);
  });

  test('D21: removing a member cuts off project access', async ({ page }) => {
    const projectId = getE2eProjectId();
    expect(projectId, 'globalSetup must have provisioned a project id').toBeTruthy();

    const adminTok = await adminToken(page.request);
    const pengineerId = await findUserId(page.request, adminTok, 'e2e_pengineer');
    expect(pengineerId, 'e2e_pengineer user must exist').toBeTruthy();

    const listBefore = await page.request.get(
      `${API_BASE}/v1/projects/${projectId}/members`,
      { headers: { Authorization: `Bearer ${adminTok}` } },
    );
    expect(listBefore.status()).toBe(200);
    const membersBefore = (await listBefore.json()) as {
      data: Array<{ id: string; userId: string; role: string }>;
    };
    const existingRows = membersBefore.data.filter((m) => m.userId === pengineerId);

    const { accessToken: engTokBefore } = await loginAsSeeded(
      page,
      'e2e_pengineer',
      'e2e-Eng!123',
    );
    const okRes = await page.request.get(`${API_BASE}/v1/projects/${projectId}`, {
      headers: { Authorization: `Bearer ${engTokBefore}` },
    });
    expect(okRes.status()).toBe(200);

    for (const row of existingRows) {
      const del = await page.request.delete(
        `${API_BASE}/v1/projects/${projectId}/members/${row.id}`,
        { headers: { Authorization: `Bearer ${adminTok}` } },
      );
      expect([200, 204]).toContain(del.status());
    }

    try {
      // Fresh login so the cached token from the cookie/store is replaced — otherwise
      // the engineer keeps their pre-revocation JWT until it expires.
      await page.context().clearCookies();
      const { accessToken: engTokAfter } = await loginAsSeeded(
        page,
        'e2e_pengineer',
        'e2e-Eng!123',
      );
      const denyRes = await page.request.get(`${API_BASE}/v1/projects/${projectId}`, {
        headers: { Authorization: `Bearer ${engTokAfter}` },
      });
      expect(denyRes.status()).toBe(403);
    } finally {
      for (const row of existingRows) {
        await page.request.post(`${API_BASE}/v1/projects/${projectId}/members`, {
          headers: {
            Authorization: `Bearer ${adminTok}`,
            'Content-Type': 'application/json',
          },
          data: { userId: row.userId, role: row.role },
        });
      }
    }
  });
});

test.describe('RBAC Block E — Action-level button gating', () => {
  test('E22: admin row on /admin/users exposes a delete/deactivate control', async ({ page }) => {
    await login(page);
    await page.goto('/admin/users');
    await expect(page).toHaveURL(/\/admin\/users/);

    // Admin UI uses an icon button labelled "Deactivate" (admin/users/page.tsx ~line 630);
    // accept either spelling so a future Delete→Deactivate rename doesn't break the test.
    const destructive = page.getByRole('button', { name: /delete|deactivate/i });
    await expect(destructive.first()).toBeVisible({ timeout: 10_000 });
    expect(await destructive.count()).toBeGreaterThan(0);
  });

  test('E23: dmicdc.ceo (PROJECT_MANAGER) cannot see Delete on /admin/users', async ({ page }) => {
    await loginAsSeeded(page, 'dmicdc.ceo');
    await page.goto('/admin/users');
    await page.waitForLoadState('domcontentloaded');

    if (/\/forbidden/.test(page.url())) {
      await expect(page.locator('body')).toContainText(/Forbidden|don.?t have access/i);
      return;
    }
    const destructive = page.getByRole('button', { name: /delete|deactivate/i });
    expect(await destructive.count()).toBe(0);
  });

  test('E24: lnt.pm.n03 sees "New Activity" on own project activities page', async ({ page }) => {
    const { accessToken } = await loginAsSeeded(page, 'lnt.pm.n03');
    const projectId = await firstAccessibleProjectId(page, accessToken);
    expect(projectId, 'lnt.pm.n03 must see at least one project').toBeTruthy();

    await page.goto(`/projects/${projectId}/activities`);
    await page.waitForLoadState('domcontentloaded');

    if (/\/forbidden/.test(page.url())) {
      throw new Error(`PM unexpectedly forbidden from own project ${projectId}`);
    }
    await expect(
      page.getByRole('button', { name: /^new activity$|add activity/i }),
    ).toBeVisible({ timeout: 10_000 });
  });

  test('E25: lnt.sitein (VIEWER) does not see "New Activity" on same project', async ({ page }) => {
    const { accessToken } = await loginAsSeeded(page, 'lnt.sitein');
    const projectId = await firstAccessibleProjectId(page, accessToken);
    expect(projectId, 'lnt.sitein must see at least one project').toBeTruthy();

    await page.goto(`/projects/${projectId}/activities`);
    await page.waitForLoadState('domcontentloaded');

    if (/\/forbidden/.test(page.url())) {
      await expect(page.locator('body')).toContainText(/Forbidden|don.?t have access/i);
      return;
    }
    const addBtn = page.getByRole('button', { name: /^new activity$|add activity/i });
    expect(await addBtn.count()).toBe(0);
  });
});

test.describe('RBAC Block F — Legacy aliases & supervisor cutover', () => {
  test('F26: QC_MANAGER profile resolves to QA_QC_ENGINEER perms (project-scoped NCR not 403)', async ({
    page,
  }) => {
    await loginAs(page, 'QC_MANAGER');
    const token = await page.evaluate(() => localStorage.getItem('access_token'));
    expect(token).toBeTruthy();

    const projectId = await firstAccessibleProjectId(page, token!);
    expect(
      projectId,
      'e2e_qcmanager must be enrolled in at least one project via globalSetup',
    ).toBeTruthy();

    const res = await page.request.get(
      `${API_BASE}/v1/projects/${projectId}/qc-test-types`,
      { headers: { Authorization: `Bearer ${token}` } },
    );
    expect(
      res.status(),
      `QC_MANAGER → QA_QC_ENGINEER alias should resolve; got ${res.status()} ${await res.text()}`,
    ).not.toBe(403);
  });

  test('F27: DPR supervisor picker hits /v1/users?roles=..., never /v1/resources?type=SUPERVISOR', async ({
    page,
  }) => {
    // Use admin so the picker's underlying GET /v1/users (ADMIN_USER.READ-gated) can resolve.
    // The test verifies the call shape, not who can call it.
    await login(page, 'admin', 'admin123');
    const token = await page.evaluate(() => localStorage.getItem('access_token'));
    const projectId = await firstAccessibleProjectId(page, token!);
    expect(projectId).toBeTruthy();

    const seenUrls: string[] = [];
    page.on('request', (req) => seenUrls.push(req.url()));

    const usersByRolesPromise = page.waitForRequest(
      (req) => /\/v1\/users(\?|$)/.test(req.url()) && /[?&]roles=/.test(req.url()),
      { timeout: 15_000 },
    );

    await page.goto(`/projects/${projectId}/dpr`);
    await usersByRolesPromise;
    await page.waitForTimeout(2_000);

    const legacy = seenUrls.filter((u) => /\/v1\/resources\?[^#]*type=SUPERVISOR/i.test(u));
    expect(
      legacy,
      `Phase 4.4 should have removed all /v1/resources?type=SUPERVISOR calls; saw: ${legacy.join(', ')}`,
    ).toEqual([]);
  });

  test('F28: DPR detail resolves supervisor via User identity (non-empty supervisor name)', async ({
    page,
  }) => {
    const { accessToken } = await loginAsSeeded(page, 'aecom.pmc.lead');
    const projectId = await firstAccessibleProjectId(page, accessToken);
    expect(projectId).toBeTruthy();

    const dprRes = await page.request.get(
      `${API_BASE}/v1/projects/${projectId}/dprs?page=0&size=25`,
      { headers: { Authorization: `Bearer ${accessToken}` } },
    );
    test.skip(!dprRes.ok(), `dprs endpoint returned ${dprRes.status()} — seed data missing?`);

    const body = (await dprRes.json()) as PagedDprs;
    const rows = body.data?.content ?? [];
    test.skip(rows.length === 0, 'no seeded DPRs in this project — nothing to assert on');

    const withSupervisor = rows.find(
      (r) => typeof r.supervisorName === 'string' && r.supervisorName.trim().length > 0,
    );
    expect(
      withSupervisor,
      'at least one DPR row must carry a supervisorName resolved via the User pool',
    ).toBeTruthy();

    const name = (withSupervisor!.supervisorName ?? '').trim();
    expect(name).not.toMatch(/^(unknown|—|-|n\/a)$/i);
  });
});

test.describe('RBAC Block G — Negative & edge cases', () => {
  test('G29: tampered access_token cookie redirects to /auth/login', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL('/');

    await page.context().clearCookies();
    await page.context().addCookies([
      {
        name: 'access_token',
        value: 'tampered.bad.token',
        domain: 'localhost',
        path: '/',
        sameSite: 'Strict',
      },
    ]);
    // Wipe the Zustand-persisted auth store too — middleware reads the cookie but the app
    // shell hydrates from localStorage; without this the dashboard renders briefly before
    // the next API call 401s.
    await page.evaluate(() => {
      try {
        localStorage.removeItem('bipros-auth');
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
      } catch {
        /* test-fixture only */
      }
    });

    await page.goto('/');
    await page.waitForURL(/\/auth\/login/, { timeout: 15_000 });
    expect(page.url()).toMatch(/\/auth\/login/);
  });

  test('G30: freshly registered VIEWER-tier user — dashboard renders, admin nav hidden, /admin/users forbidden', async ({
    page,
  }) => {
    const stamp = Date.now();
    const username = `pw_zero_${stamp}`;
    const password = 'PlayPa55!';
    const email = `pw.zero.${stamp}@bipros.test`;

    const reg = await page.request.post(`${API_BASE}/v1/auth/register`, {
      data: { username, email, password, firstName: 'PW', lastName: 'Zero' },
    });
    expect(
      [200, 201, 409, 400].includes(reg.status()),
      `register returned unexpected ${reg.status()}: ${await reg.text()}`,
    ).toBe(true);

    await login(page, username, password);
    await expect(page).toHaveURL('/');

    const sidebar = page.locator('aside').first();
    const adminLinks = sidebar.locator('a[href^="/admin/"]');
    expect(
      await adminLinks.count(),
      'a VIEWER-tier user must see zero /admin/ sidebar links',
    ).toBe(0);

    await page.goto('/admin/users');
    await page.waitForLoadState('domcontentloaded');
    if (/\/forbidden/.test(page.url())) {
      await expect(page.locator('body')).toContainText(/Forbidden|don.?t have access/i);
    } else {
      await expect(page.locator('body')).toContainText(
        /Forbidden|don.?t have access|not authori[sz]ed/i,
      );
    }
  });
});
