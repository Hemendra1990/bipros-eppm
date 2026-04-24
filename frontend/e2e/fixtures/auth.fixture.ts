import { test as base, expect, Page } from '@playwright/test';

/**
 * Shared login helper. The login form was redesigned as a marketing landing in commit
 * f0b5f09 and the #username / #password id attributes were removed in favour of name=…
 * only, so we target by input name to stay resilient across future CSS-only tweaks.
 */
export async function login(page: Page, username = 'admin', password = 'admin123') {
  await page.goto('/auth/login');
  await page.locator('input[name="username"]').fill(username);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
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
