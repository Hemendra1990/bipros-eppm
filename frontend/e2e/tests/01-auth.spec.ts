import { test, expect } from '@playwright/test';
import { login } from '../fixtures/auth.fixture';

test.describe('Authentication', () => {
  test('login with valid credentials redirects to dashboard', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  });

  test('login with invalid credentials shows error', async ({ page }) => {
    await page.goto('/auth/login');
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('wrong_password');
    await page.getByRole('button', { name: /sign in/i }).click();
    // Inline error in red box or toast notification
    await expect(page.getByText('Invalid username or password').first()).toBeVisible({ timeout: 10_000 });
  });

  test('unauthenticated user redirected to login', async ({ page }) => {
    await page.goto('/projects');
    await expect(page).toHaveURL(/\/auth\/login/);
  });
});
