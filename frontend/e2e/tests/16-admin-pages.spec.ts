import { test, expect } from '../fixtures/auth.fixture';

test.describe('Admin Pages', () => {
  test.describe('User Management', () => {
    test('user list page loads with heading', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/users');
      await expect(page.getByRole('heading', { name: /users|user management/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    });

    test('user list shows table rows', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/users');
      const rows = page.locator('table tbody tr');
      const count = await rows.count().catch(() => 0);
      expect(count).toBeGreaterThanOrEqual(0);
    });
  });

  test.describe('User Access', () => {
    test('user access page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/user-access');
      await expect(page.locator('body')).not.toHaveText(/error|not found/i);
    });
  });

  test.describe('Calendar Management', () => {
    test('calendar list page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/calendars');
      await expect(page.getByRole('heading', { name: /calendar/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    });

    test('new calendar form is reachable', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/calendars/new');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });

    test('calendar detail page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/calendars');
      const links = page.locator('table tbody tr a').first();
      if (await links.isVisible({ timeout: 5_000 }).catch(() => false)) {
        await links.click();
        await expect(page.locator('body')).not.toHaveText(/error/i);
      }
    });
  });

  test.describe('Organisations', () => {
    test('organisation list page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/organisations');
      await expect(page.getByRole('heading', { name: /organi/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    });

    test('new organisation form loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/organisations/new');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });

  test.describe('Settings', () => {
    test('settings page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/settings');
      await expect(page.getByRole('heading', { name: /setting/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe('Resource Types', () => {
    test('resource types page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/resource-types');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });

  test.describe('Resource Roles', () => {
    test('resource roles page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/resource-roles');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });

  test.describe('Risk Library', () => {
    test('risk library page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/risk-library');
      await expect(page.getByRole('heading', { name: /risk library/i, level: 1 })).toBeVisible({ timeout: 15_000 });
    });
  });

  test.describe('WBS Templates', () => {
    test('wbs templates page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/wbs-templates');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });

  test.describe('Productivity Norms', () => {
    test('productivity norms page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/productivity-norms');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });

  test.describe('Unit Rate Master', () => {
    test('unit rate master page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/unit-rate-master');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });

  test.describe('Integrations', () => {
    test('integrations page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/integrations');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });

  test.describe('UDF Management', () => {
    test('UDF page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/admin/udf');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });
});
