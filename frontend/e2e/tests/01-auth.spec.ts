import { test, expect } from '@playwright/test';
import { login } from '../fixtures/auth.fixture';

test.describe('Authentication', () => {
  test('login with valid credentials redirects to home', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL('/');
    // Home hub renders a time-aware greeting heading and a link to the programme dashboard.
    await expect(page.getByTestId('hub-greeting')).toBeVisible();
    await expect(page.getByTestId('hub-dashboard-link')).toBeVisible();
  });

  test('login with invalid credentials shows error', async ({ page }) => {
    await page.goto('/auth/login');
    await page.locator('form input[type="text"]').first().fill('admin');
    await page.locator('form input[type="password"]').first().fill('wrong_password');
    await page.locator('form').getByRole('button', { name: /sign in/i }).click();
    // Inline error in red box or toast notification
    await expect(page.getByText('Invalid username or password').first()).toBeVisible({ timeout: 10_000 });
  });

  test('unauthenticated user redirected to login', async ({ page }) => {
    await page.goto('/projects');
    await expect(page).toHaveURL(/\/auth\/login/);
  });
});
