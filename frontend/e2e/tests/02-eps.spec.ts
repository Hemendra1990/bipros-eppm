import { test, expect } from '../fixtures/auth.fixture';

test.describe('EPS Management', () => {
  test('view EPS tree after login', async ({ authenticatedPage: page }) => {
    await page.goto('/eps');
    await expect(page.getByRole('heading', { name: 'Enterprise Project Structure' })).toBeVisible();
  });

  test('create EPS node', async ({ authenticatedPage: page }) => {
    await page.goto('/eps');
    await page.getByRole('button', { name: /add node/i }).click();
    await page.getByPlaceholder('Code').fill('E2E-TEST');
    await page.getByPlaceholder('Name').fill('E2E Test Node');
    await page.getByRole('button', { name: /^Create$/i }).click();
    await expect(page.getByText('E2E Test Node')).toBeVisible({ timeout: 10_000 });
  });

  test('create child EPS node', async ({ authenticatedPage: page }) => {
    await page.goto('/eps');
    // Click on the E2E-TEST node to select it
    await page.getByText('E2E-TEST').click();
    await page.getByRole('button', { name: /add node/i }).click();
    await page.getByPlaceholder('Code').fill('E2E-CHILD');
    await page.getByPlaceholder('Name').fill('E2E Child Node');
    await page.getByRole('button', { name: /^Create$/i }).click();
    await expect(page.getByText('E2E Child Node')).toBeVisible({ timeout: 10_000 });
  });
});
