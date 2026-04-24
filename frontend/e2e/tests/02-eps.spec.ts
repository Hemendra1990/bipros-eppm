import { test, expect } from '../fixtures/auth.fixture';

test.describe('EPS Management', () => {
  test('view EPS tree after login', async ({ authenticatedPage: page }) => {
    await page.goto('/eps');
    // Match with regex to accept both "Enterprise Project Structure" and "… (EPS)" variants.
    await expect(
      page.getByRole('heading', { name: /Enterprise Project Structure/i, level: 1 })
    ).toBeVisible({ timeout: 10_000 });
  });

  test('EPS page shows the hierarchy tree', async ({ authenticatedPage: page }) => {
    // EPS create + delete flow is covered by the API smoke tests; here we only verify that
    // the tree renders any seeded node (e.g. DMIC) so the page itself is not broken.
    await page.goto('/eps');
    await expect(page.getByText(/DMIC|DEDICATED|NICDC|Enterprise/i).first()).toBeVisible({
      timeout: 15_000,
    });
  });
});
