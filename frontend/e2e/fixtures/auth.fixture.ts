import { test as base, expect, Page } from '@playwright/test';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

/**
 * Shared login helper. We bypass the form entirely and seed the access_token cookie + the
 * Zustand-persisted auth store directly. This keeps the test deterministic against the real
 * backend without depending on the form's hydration timing.
 */
export async function login(page: Page, username = 'admin', password = 'admin123') {
  // Step 1: get tokens directly from the backend
  const loginRes = await page.request.post(`${API_BASE}/v1/auth/login`, {
    data: { username, password },
    headers: { 'Content-Type': 'application/json' },
  });
  if (!loginRes.ok()) {
    throw new Error(`login(${username}) failed: ${loginRes.status()} ${await loginRes.text()}`);
  }
  const loginBody = (await loginRes.json()) as {
    data: { accessToken: string; refreshToken: string };
  };
  const { accessToken, refreshToken } = loginBody.data;

  // Step 2: fetch the canonical UserResponse so the persisted store mirrors what the real
  // login flow would have produced (Sidebar reads `user.roles` from this).
  const meRes = await page.request.get(`${API_BASE}/v1/users/me`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!meRes.ok()) {
    throw new Error(`/v1/users/me failed for ${username}: ${meRes.status()}`);
  }
  const meBody = (await meRes.json()) as { data: unknown };
  const user = meBody.data;

  // Step 3: seed the cookie the middleware reads, plus localStorage entries the axios
  // interceptor + Zustand auth store expect. Doing this before the page loads guarantees the
  // very first render of `/` sees an authenticated context.
  await page.context().addCookies([
    {
      name: 'access_token',
      value: accessToken,
      domain: 'localhost',
      path: '/',
      sameSite: 'Strict',
    },
  ]);
  await page.addInitScript(
    ({ access, refresh, userObj }) => {
      try {
        localStorage.setItem('access_token', access);
        localStorage.setItem('refresh_token', refresh);
        localStorage.setItem(
          'bipros-auth',
          JSON.stringify({
            state: { user: userObj, accessToken: access, refreshToken: refresh },
            version: 0,
          }),
        );
      } catch {
        /* test-fixture only */
      }
    },
    { access: accessToken, refresh: refreshToken, userObj: user },
  );

  // Step 4: navigate; middleware sees the cookie and lets the dashboard render.
  await page.goto('/');
  await page.waitForURL(/\/$|\/$/, { timeout: 15_000 });
  await expect(page).toHaveURL('/');
}

export const test = base.extend<{ authenticatedPage: Page }>({
  authenticatedPage: async ({ page }, use) => {
    await login(page);
    await use(page);
  },
});

export { expect };
