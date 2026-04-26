import { test, expect } from '@playwright/test';
import { login } from '../fixtures/auth.fixture';

/**
 * Frontend security spec — mirrors the highest-value scenarios from the backend `SecurityIT`
 * suite, exercised through the actual UI.
 *
 * <p>Scope is deliberately tight: anonymous / admin / non-admin paths plus the /forbidden flow.
 * Per-role row filtering and field masking are already proven by SecurityIT against the API; this
 * file confirms that the UI layer (middleware, sidebar, axios interceptor) honours the same model.
 *
 * <p>The non-admin user is created idempotently via the public /v1/auth/register endpoint, which
 * auto-assigns ROLE_VIEWER (the default in {@code AuthService.register}). VIEWER is sufficient
 * for these tests — they care about the admin/non-admin distinction, not which specific role.
 * A backend endpoint to mutate user roles via the API doesn't exist yet; once added, swap in
 * a stronger role here.
 */

const NON_ADMIN_USERNAME = 'pw_team_member';
const NON_ADMIN_PASSWORD = 'PlayPa55!';
const NON_ADMIN_EMAIL = 'pw.team@bipros.test';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

async function ensureNonAdminUser(): Promise<void> {
  // Idempotent register — 409 (username taken) and 400 (email taken) are both treated as
  // "user already exists, that's fine".
  const reg = await fetch(`${API_BASE}/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: NON_ADMIN_USERNAME,
      email: NON_ADMIN_EMAIL,
      password: NON_ADMIN_PASSWORD,
      firstName: 'PW',
      lastName: 'Team',
    }),
  });
  if (!reg.ok && reg.status !== 409 && reg.status !== 400) {
    const body = await reg.text();
    throw new Error(`Registering ${NON_ADMIN_USERNAME} failed: ${reg.status} ${body}`);
  }
}

test.describe('Security UX — RBAC + ABAC + RLS surface', () => {
  test.beforeAll(async () => {
    await ensureNonAdminUser();
  });

  test('anonymous user is redirected to /auth/login when visiting /', async ({ page }) => {
    // Make sure no leftover cookie from a prior test
    await page.context().clearCookies();
    const res = await page.goto('/');
    // Either the navigation already lands on /auth/login (server redirect) or we end up
    // on the login form via the client-side bounce. Both are acceptable.
    await page.waitForURL(/\/auth\/login/, { timeout: 10_000 });
    expect(page.url()).toMatch(/\/auth\/login/);
    expect(res?.status() ?? 200).toBeLessThan(500);
  });

  test('unauthenticated visit to /admin/users redirects to /auth/login (not /forbidden)', async ({ page }) => {
    await page.context().clearCookies();
    await page.goto('/admin/users');
    await page.waitForURL(/\/auth\/login/, { timeout: 10_000 });
    // The middleware should preserve where the user was headed via ?next=
    expect(page.url()).toContain('next=%2Fadmin%2Fusers');
  });

  test('admin can sign in and lands on the dashboard', async ({ page }) => {
    await login(page); // defaults to admin/admin123
    await expect(page).toHaveURL('/');
    // Sidebar should include the Admin group for ROLE_ADMIN
    const sidebar = page.locator('aside').first();
    await expect(sidebar).toContainText(/Admin/i);
    await expect(sidebar).toContainText(/Users/i);
  });

  test('admin can reach /admin/users (no /forbidden bounce)', async ({ page }) => {
    await login(page);
    await page.goto('/admin/users');
    // Either the page renders or we get a 200 without a redirect to /forbidden.
    await expect(page).toHaveURL(/\/admin\/users/);
    await expect(page.locator('body')).not.toContainText(/403|Forbidden|don.?t have access/i);
  });

  test('non-admin user has the Admin sidebar group hidden after login', async ({ page }) => {
    await login(page, NON_ADMIN_USERNAME, NON_ADMIN_PASSWORD);
    await expect(page).toHaveURL('/');
    const sidebar = page.locator('aside').first();
    // Other groups should still be visible
    await expect(sidebar).toContainText(/Plan|Execute|Control/i);
    // The "Admin" GROUP HEADER and the admin-only items should NOT appear.
    // (Some other items may incidentally contain the word "admin" in tooltips — match the
    // group header, which is uppercase tracking-wide text.)
    const adminGroupHeader = sidebar.locator('text=/^ADMIN$/i').first();
    await expect(adminGroupHeader).toBeHidden({ timeout: 5_000 }).catch(async () => {
      // Fallback assertion — the group might just not exist in the DOM at all.
      expect(await adminGroupHeader.count()).toBe(0);
    });
  });

  test('non-admin user visiting /admin/users is redirected to /forbidden by middleware', async ({ page }) => {
    await login(page, NON_ADMIN_USERNAME, NON_ADMIN_PASSWORD);
    await page.goto('/admin/users');
    await page.waitForURL(/\/forbidden/, { timeout: 10_000 });
    await expect(page.locator('body')).toContainText(/Forbidden|don.?t have access/i);
  });

  test('/welcome stays public — visible to unauthenticated users', async ({ page }) => {
    await page.context().clearCookies();
    const res = await page.goto('/welcome');
    expect(res?.status() ?? 200).toBeLessThan(400);
    // Should NOT have been redirected to /auth/login
    expect(page.url()).toContain('/welcome');
  });
});
