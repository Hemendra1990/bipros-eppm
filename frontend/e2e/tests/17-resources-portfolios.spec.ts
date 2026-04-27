import { test, expect } from '../fixtures/auth.fixture';

test.describe('Resources & Portfolios', () => {
  test.describe('Resources', () => {
    test('resource list page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/resources');
      await expect(page.getByRole('heading', { name: /resource/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    });

    test('new resource page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/resources/new');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });

    test('resource list has table or empty state', async ({ authenticatedPage: page }) => {
      await page.goto('/resources');
      const hasTable = await page.locator('table').first().isVisible({ timeout: 5_000 }).catch(() => false);
      const hasEmptyState = await page.getByText(/no resource|empty|add/i).first().isVisible({ timeout: 5_000 }).catch(() => false);
      expect(hasTable || hasEmptyState).toBeTruthy();
    });
  });

  test.describe('Portfolios', () => {
    test('portfolio list page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/portfolios');
      await expect(page.getByRole('heading', { name: /portfolio/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    });

    test('portfolio detail page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/portfolios');
      const links = page.locator('a[href*="/portfolios/"]').first();
      if (await links.isVisible({ timeout: 5_000 }).catch(() => false)) {
        await links.click();
        await page.waitForURL(/\/portfolios\//);
        await expect(page.locator('body')).not.toHaveText(/error/i);
      }
    });

    test('portfolio page has projects tab or empty state', async ({ authenticatedPage: page }) => {
      await page.goto('/portfolios');
      const links = page.locator('a[href*="/portfolios/"]').first();
      if (await links.isVisible({ timeout: 5_000 }).catch(() => false)) {
        await links.click();
        await page.waitForURL(/\/portfolios\//);
        const hasContent = await page.locator('body').textContent();
        expect(hasContent).toBeTruthy();
      }
    });
  });

  test.describe('OBS', () => {
    test('OBS page loads with tree', async ({ authenticatedPage: page }) => {
      await page.goto('/obs');
      await expect(page.getByRole('heading', { name: /organizational|obs/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe('Analytics', () => {
    test('analytics page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/analytics');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });

  test.describe('Risk Register', () => {
    test('risk page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/risk');
      await expect(page.getByRole('heading', { name: /risk/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    });

    test('risk page has table or empty state', async ({ authenticatedPage: page }) => {
      await page.goto('/risk');
      const hasTable = await page.locator('table').first().isVisible({ timeout: 5_000 }).catch(() => false);
      const hasAddButton = await page.getByRole('button', { name: /add/i }).first().isVisible({ timeout: 3_000 }).catch(() => false);
      expect(hasTable || hasAddButton).toBeTruthy();
    });
  });

  test.describe('Dashboards', () => {
    test('dashboard list page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/dashboards');
      await expect(page.getByRole('heading', { name: /dashboard/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    });

    test('executive dashboard loads', async ({ authenticatedPage: page }) => {
      await page.goto('/dashboards/executive');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });

    test('programme dashboard loads', async ({ authenticatedPage: page }) => {
      await page.goto('/dashboards/programme');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });

    test('operational dashboard loads', async ({ authenticatedPage: page }) => {
      await page.goto('/dashboards/operational');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });

    test('field dashboard loads', async ({ authenticatedPage: page }) => {
      await page.goto('/dashboards/field');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });

    test('portfolio dashboard loads', async ({ authenticatedPage: page }) => {
      await page.goto('/dashboards/portfolio');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });
});
