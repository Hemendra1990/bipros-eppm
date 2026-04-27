import { test, expect } from '../fixtures/auth.fixture';

/**
 * E2E tests for P6-Style Risk Management.
 * Covers: Risk Register, Risk Detail (tabs), Activity Assignment, Scoring Matrix.
 */
test.describe('Risk Management', () => {

  // ── Risk Register ──────────────────────────────────────────────────────

  test('risk register page renders with RAG summary cards', async ({ authenticatedPage: page }) => {
    // Navigate to a project's risk register
    await page.goto('/projects');
    await page.getByRole('table').getByRole('link').first().click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/, { timeout: 10_000 });

    // Click the Risks tab
    await page.getByRole('button', { name: 'Risks' }).click();
    await page.waitForURL(/\/risks/, { timeout: 10_000 });

    // Verify page elements
    await expect(page.getByRole('heading', { name: 'Risk Register', level: 1 })).toBeVisible();
    await expect(page.getByText('Crimson')).toBeVisible();
    await expect(page.getByText('Red')).toBeVisible();
    await expect(page.getByText('Amber')).toBeVisible();
    await expect(page.getByText('Green')).toBeVisible();
    await expect(page.getByText('Opportunities')).toBeVisible();
  });

  test('can create a new risk from register', async ({ authenticatedPage: page }) => {
    await page.goto('/projects');
    await page.getByRole('table').getByRole('link').first().click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/, { timeout: 10_000 });
    await page.getByRole('button', { name: 'Risks' }).click();
    await page.waitForURL(/\/risks/, { timeout: 10_000 });

    // Click "New Risk"
    await page.getByRole('button', { name: /New Risk/i }).click();

    // Fill in the form
    await page.getByPlaceholder('Risk title').fill('E2E Test Risk');
    await page.getByPlaceholder('Risk description').fill('This is an automated test risk');

    // Select Threat type
    await page.locator('select[name="riskType"]').selectOption('THREAT');

    // Submit
    await page.getByRole('button', { name: /Create Risk/i }).click();

    // Verify the risk appears in the table
    await expect(page.getByText('E2E Test Risk')).toBeVisible({ timeout: 10_000 });
  });

  test('can navigate to risk detail page', async ({ authenticatedPage: page }) => {
    await page.goto('/projects');
    await page.getByRole('table').getByRole('link').first().click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/, { timeout: 10_000 });
    await page.getByRole('button', { name: 'Risks' }).click();
    await page.waitForURL(/\/risks/, { timeout: 10_000 });

    // Click on the first risk title link
    const riskLink = page.locator('table tbody tr').first().getByRole('link');
    if (await riskLink.count() > 0) {
      await riskLink.first().click();
      await page.waitForURL(/\/risks\/[0-9a-f-]+/, { timeout: 10_000 });

      // Verify we're on the detail page
      await expect(page.getByRole('button', { name: 'General' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Impact' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Activities' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Description' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Cause' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Effect' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Notes' })).toBeVisible();
    }
  });

  // ── Risk Detail - General Tab ──────────────────────────────────────────

  test('risk detail shows General tab with identification and exposure sections', async ({ authenticatedPage: page }) => {
    await page.goto('/projects');
    await page.getByRole('table').getByRole('link').first().click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/, { timeout: 10_000 });
    await page.getByRole('button', { name: 'Risks' }).click();
    await page.waitForURL(/\/risks/, { timeout: 10_000 });

    const riskLink = page.locator('table tbody tr').first().getByRole('link');
    if (await riskLink.count() > 0) {
      await riskLink.first().click();
      await page.waitForURL(/\/risks\/[0-9a-f-]+/, { timeout: 10_000 });

      // General tab should show identification fields
      await expect(page.getByText('Identification')).toBeVisible();
      await expect(page.getByText('Exposure')).toBeVisible();
      await expect(page.getByText('Scores')).toBeVisible();
      await expect(page.getByText('Risk ID')).toBeVisible();
      await expect(page.getByText('Risk Name')).toBeVisible();
      await expect(page.getByText('Type')).toBeVisible();
    }
  });

  // ── Risk Detail - Impact Tab ───────────────────────────────────────────

  test('risk detail Impact tab shows Pre/Response/Post columns', async ({ authenticatedPage: page }) => {
    await page.goto('/projects');
    await page.getByRole('table').getByRole('link').first().click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/, { timeout: 10_000 });
    await page.getByRole('button', { name: 'Risks' }).click();
    await page.waitForURL(/\/risks/, { timeout: 10_000 });

    const riskLink = page.locator('table tbody tr').first().getByRole('link');
    if (await riskLink.count() > 0) {
      await riskLink.first().click();
      await page.waitForURL(/\/risks\/[0-9a-f-]+/, { timeout: 10_000 });

      // Click Impact tab
      await page.getByRole('button', { name: 'Impact' }).click();

      // Verify three columns
      await expect(page.getByText('Pre-Response')).toBeVisible();
      await expect(page.getByText('Response')).toBeVisible();
      await expect(page.getByText('Post-Response')).toBeVisible();
    }
  });

  // ── Risk Detail - Activities Tab ───────────────────────────────────────

  test('risk detail Activities tab shows assign button', async ({ authenticatedPage: page }) => {
    await page.goto('/projects');
    await page.getByRole('table').getByRole('link').first().click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/, { timeout: 10_000 });
    await page.getByRole('button', { name: 'Risks' }).click();
    await page.waitForURL(/\/risks/, { timeout: 10_000 });

    const riskLink = page.locator('table tbody tr').first().getByRole('link');
    if (await riskLink.count() > 0) {
      await riskLink.first().click();
      await page.waitForURL(/\/risks\/[0-9a-f-]+/, { timeout: 10_000 });

      // Click Activities tab
      await page.getByRole('button', { name: 'Activities' }).click();

      // Verify assign button is visible
      await expect(page.getByRole('button', { name: /Assign Activity/i })).toBeVisible();
      await expect(page.getByText('Assigned Activities')).toBeVisible();
    }
  });

  // ── Cross-Project Risk Register ────────────────────────────────────────

  test('cross-project risk register is accessible from Reports', async ({ authenticatedPage: page }) => {
    await page.goto('/reports');
    await expect(page.getByRole('heading', { name: /Project.*programme reports/i })).toBeVisible();

    // Navigate to Standard Reports tab to find the risk register link
    await page.getByRole('button', { name: /Standard Reports/i }).click();

    // Look for the Cross-Project Risk Register link
    const riskRegisterLink = page.getByText('Cross-Project Risk Register');
    if (await riskRegisterLink.count() > 0) {
      await riskRegisterLink.first().click();
      await page.waitForURL(/\/reports\/risk-register/, { timeout: 10_000 });
      await expect(page.getByRole('heading', { name: 'Cross-Project Risk Register', level: 1 })).toBeVisible();
    }
  });

  // ── Sidebar Navigation ─────────────────────────────────────────────────

  test('sidebar does not show standalone Risk link', async ({ authenticatedPage: page }) => {
    await page.goto('/');

    // Risk should NOT be in the sidebar anymore
    const sidebarLinks = page.locator('aside a');
    const riskLink = sidebarLinks.filter({ hasText: /^Risk$/ });
    await expect(riskLink).toHaveCount(0);
  });

  test('sidebar shows Risk Scoring Matrix in admin section', async ({ authenticatedPage: page }) => {
    await page.goto('/');

    // Look for Risk Scoring Matrix in sidebar
    await expect(page.getByRole('link', { name: /Risk Scoring Matrix/ })).toBeVisible();
  });

  // ── Risk Scoring Matrix Admin ──────────────────────────────────────────

  test('risk scoring matrix admin page renders', async ({ authenticatedPage: page }) => {
    await page.goto('/admin/risk-scoring-matrix');
    await expect(page.getByRole('heading', { name: 'Risk Scoring Matrix', level: 1 })).toBeVisible();

    // Should have project selector
    await expect(page.getByText('Select Project')).toBeVisible();
  });

  test('risk scoring matrix shows matrix grid after project selection', async ({ authenticatedPage: page }) => {
    await page.goto('/admin/risk-scoring-matrix');

    // Select first project
    const projectSelect = page.locator('select').first();
    if (await projectSelect.locator('option').count() > 1) {
      await projectSelect.selectOption({ index: 1 });

      // Matrix grid should appear
      await expect(page.getByText('Scoring Method')).toBeVisible({ timeout: 10_000 });
      await expect(page.getByText('Highest Impact')).toBeVisible();
      await expect(page.getByText('Average Impact')).toBeVisible();
    }
  });

  // ── Old /risk route redirects ──────────────────────────────────────────

  test('old /risk route redirects to /reports/risk-register', async ({ authenticatedPage: page }) => {
    await page.goto('/risk');
    await page.waitForURL(/\/reports\/risk-register/, { timeout: 10_000 });
    await expect(page.getByRole('heading', { name: 'Cross-Project Risk Register', level: 1 })).toBeVisible();
  });
});
