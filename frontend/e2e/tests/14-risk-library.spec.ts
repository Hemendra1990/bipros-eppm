import { test, expect } from '../fixtures/auth.fixture';

/**
 * Smoke coverage for the Risk Library admin page and the "Add from Library" flow on the
 * risk register, plus the analysis-quality column. Assumes the dev backend is running
 * and the RiskTemplateSeeder has populated the system-default rows.
 */
test.describe('Risk Library', () => {
  test('admin page renders seeded templates with system badge', async ({
    authenticatedPage: page,
  }) => {
    await page.goto('/admin/risk-library');
    await expect(page.getByRole('heading', { name: 'Risk Library', level: 1 })).toBeVisible();

    // Seeded ROAD-LAND-001 should appear (industry filter defaults to All).
    await expect(page.getByText('ROAD-LAND-001').first()).toBeVisible();
    // System-default rows carry a "system" badge in the Code column.
    await expect(page.locator('tr', { hasText: 'ROAD-LAND-001' }).getByText('system')).toBeVisible();
  });

  test('industry filter narrows the list', async ({ authenticatedPage: page }) => {
    await page.goto('/admin/risk-library');
    await page
      .getByRole('combobox')
      .first()
      .selectOption('OIL_GAS');

    // OIL_GAS templates appear; ROAD codes should not.
    await expect(page.getByText('OG-HOTWORK-007').first()).toBeVisible();
    await expect(page.getByText('ROAD-LAND-001')).toHaveCount(0);
  });

  test('Risk register exposes "Add from Library" button', async ({
    authenticatedPage: page,
  }) => {
    await page.goto('/risk');
    await expect(
      page.getByRole('button', { name: /Add from Library/i }),
    ).toBeVisible();
    // Disabled until a project is selected.
    await expect(
      page.getByRole('button', { name: /Add from Library/i }),
    ).toBeDisabled();
  });

  test('Sidebar admin nav links to Risk Library', async ({ authenticatedPage: page }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: /Risk Library/ })).toBeVisible();
  });
});
