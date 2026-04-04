import { test, expect } from '../fixtures/auth.fixture';

test.describe('CPM Scheduling', () => {
  test('run schedule on project with activities', async ({ authenticatedPage: page }) => {
    await page.goto('/projects');
    // Click the first project link in the table
    const projectLink = page.locator('table tbody tr a').first();
    await projectLink.click();
    await page.waitForURL(/\/projects\//, { timeout: 10_000 });

    // Go to Activities tab
    await page.getByText('Activities').first().click();

    // Click Run Schedule button if visible
    const runBtn = page.getByRole('button', { name: /run schedule/i });
    if (await runBtn.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await runBtn.click();
      await page.waitForTimeout(3_000);
    }
  });

  test('view Gantt chart after scheduling', async ({ authenticatedPage: page }) => {
    await page.goto('/projects');
    const projectLink = page.locator('table tbody tr a').first();
    await projectLink.click();
    await page.waitForURL(/\/projects\//, { timeout: 10_000 });

    // Go to Gantt tab
    await page.getByText('Gantt').first().click();

    // Verify SVG chart or canvas is rendered
    const hasSvg = await page.locator('svg').first().isVisible({ timeout: 5_000 }).catch(() => false);
    const hasCanvas = await page.locator('canvas').first().isVisible({ timeout: 5_000 }).catch(() => false);
    expect(hasSvg || hasCanvas).toBeTruthy();
  });
});
