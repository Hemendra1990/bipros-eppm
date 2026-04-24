import { test, expect } from '@playwright/test';
import { login } from '../fixtures/auth.fixture';

test.describe('Authentication', () => {
  test('login with valid credentials redirects to dashboard', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL('/');
    // Multiple "Dashboard" headings exist (h1 title + h4 sidebar link); match only the h1.
    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
  });

  test('login with invalid credentials shows error', async ({ page }) => {
    await page.goto('/auth/login');
    await page.locator('input[name="username"]').fill('admin');
    await page.locator('input[name="password"]').fill('wrong_password');
    await page.getByRole('button', { name: /sign in/i }).click();
    // Inline error in red box or toast notification
    await expect(page.getByText('Invalid username or password').first()).toBeVisible({ timeout: 10_000 });
  });

  test('unauthenticated user redirected to login', async ({ page }) => {
    await page.goto('/projects');
    await expect(page).toHaveURL(/\/auth\/login/);
  });
});
