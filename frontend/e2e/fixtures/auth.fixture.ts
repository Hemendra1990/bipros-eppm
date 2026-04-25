import { test as base, expect, Page } from '@playwright/test';

/**
 * Shared login helper. The login form is now a marketing landing page;
 * inputs are controlled components without name attributes. We target by
 * input type and placeholder to stay resilient across CSS tweaks.
 */
export async function login(page: Page, username = 'admin', password = 'admin123') {
  await page.goto('/auth/login');
  // Username: first text input inside the sign-in card (placeholder "you@company.com")
  const usernameInput = page.locator('form input[type="text"]').first();
  await expect(usernameInput).toBeVisible({ timeout: 10_000 });
  await usernameInput.fill(username);

  // Password: the password input inside the sign-in card
  const passwordInput = page.locator('form input[type="password"]').first();
  await passwordInput.fill(password);

  await page.locator('form').getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL('/', { timeout: 15_000 });
  await expect(page).toHaveURL('/');
}

export const test = base.extend<{ authenticatedPage: Page }>({
  authenticatedPage: async ({ page }, use) => {
    await login(page);
    await use(page);
  },
});

export { expect };
