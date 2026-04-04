import { test, expect } from '../fixtures/auth.fixture';

test.describe('Project Lifecycle', () => {
  test('view projects list', async ({ authenticatedPage: page }) => {
    await page.goto('/projects');
    await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible();
  });

  test('create new project', async ({ authenticatedPage: page }) => {
    await page.goto('/projects/new');
    await expect(page.getByRole('heading', { name: 'New Project' })).toBeVisible();
    await page.locator('[name="code"]').fill('E2E-PROJ');
    await page.locator('[name="name"]').fill('E2E Test Project');
    // Select an EPS node from the dropdown
    await page.locator('[name="epsNodeId"]').selectOption({ index: 1 });
    await page.locator('[name="plannedStartDate"]').fill('2026-07-01');
    await page.locator('[name="plannedFinishDate"]').fill('2027-07-01');
    await page.getByRole('button', { name: /create project/i }).click();
    await page.waitForURL(/\/projects\//, { timeout: 15_000 });
  });

  test('view project detail tabs', async ({ authenticatedPage: page }) => {
    await page.goto('/projects');
    // Click the E2E-PROJ link in the table
    await page.getByRole('link', { name: 'E2E-PROJ' }).first().click();
    await page.waitForURL(/\/projects\//, { timeout: 10_000 });
    await expect(page.getByText('Overview').first()).toBeVisible();
    await expect(page.getByText('Activities').first()).toBeVisible();
    await expect(page.getByText('Gantt').first()).toBeVisible();
  });
});
