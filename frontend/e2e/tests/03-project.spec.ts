import { test, expect } from '../fixtures/auth.fixture';

test.describe('Project Lifecycle', () => {
  test('view projects list', async ({ authenticatedPage: page }) => {
    await page.goto('/projects');
    await expect(page.getByRole('heading', { name: 'Projects', level: 1 })).toBeVisible();
  });

  test('create new project form is reachable', async ({ authenticatedPage: page }) => {
    // The full end-to-end create is exercised in 10-pms-masterdata.spec.ts via the
    // stretch-new form. Here we confirm the project-new page renders with its enriched
    // PMS MasterData fields (category, chainage, contract block).
    await page.goto('/projects/new');
    await expect(page.getByRole('heading', { name: 'New Project', level: 1 })).toBeVisible();
    await expect(page.getByText(/Category/i).first()).toBeVisible();
    await expect(page.getByText(/From Chainage/i).first()).toBeVisible();
    await expect(page.getByText(/Primary Contract/i).first()).toBeVisible();
  });

  test('view project detail tabs', async ({ authenticatedPage: page }) => {
    await page.goto('/projects');
    // Use the first seeded project — the create-project test above may have been skipped.
    await page.getByRole('table').getByRole('link').first().click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/, { timeout: 10_000 });
    await expect(page.getByText('Overview').first()).toBeVisible();
    await expect(page.getByText('Activities').first()).toBeVisible();
    await expect(page.getByText('Gantt').first()).toBeVisible();
  });
});
