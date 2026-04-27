import { test, expect } from '../fixtures/auth.fixture';

async function navigateToFirstProject(page: any): Promise<string> {
  await page.goto('/projects');
  const projectLink = page.locator('table tbody tr a').first();
  if (!(await projectLink.isVisible({ timeout: 10_000 }).catch(() => false))) {
    return '';
  }
  await projectLink.click();
  await page.waitForURL(/\/projects\/[0-9a-f-]+/, { timeout: 10_000 });
  const url = page.url();
  return url.split('/projects/')[1].split('/')[0];
}

async function verifySubRoute(page: any, projectId: string, subRoute: string) {
  if (!projectId) return;
  await page.goto(`/projects/${projectId}/${subRoute}`);
  await page.waitForTimeout(2000);
  const errors = await page.locator('.text-red-500, .text-red-700').allTextContents().catch(() => []);
  expect(errors.length).toBeLessThanOrEqual(2);
}

test.describe('Project Insights & Contract Detail', () => {
  let projectId = '';

  test.beforeAll(async ({ authenticatedPage: page }) => {
    projectId = await navigateToFirstProject(page);
  });

  test.describe('Contract Detail Sub-Routes', () => {
    let contractId = '';

    test.beforeAll(async ({ authenticatedPage: page }) => {
      if (!projectId) return;
      await page.goto(`/projects/${projectId}/contracts`);
      const contractLink = page.locator('table tbody tr a').first();
      if (await contractLink.isVisible({ timeout: 5_000 }).catch(() => false)) {
        await contractLink.click();
        await page.waitForURL(/\/projects\/[0-9a-f-]+\/contracts\/[0-9a-f-]+/, { timeout: 10_000 });
        const url = page.url();
        contractId = url.split('/contracts/')[1];
      }
    });

    test('contract detail page has milestones section', async ({ authenticatedPage: page }) => {
      if (!contractId) return test.skip();
      await page.goto(`/projects/${projectId}/contracts/${contractId}`);
      const hasMilestones = await page.getByText(/milestone/i).first().isVisible({ timeout: 5_000 }).catch(() => false);
      const hasVO = await page.getByText(/variation/i).first().isVisible({ timeout: 5_000 }).catch(() => false);
      expect(hasMilestones || hasVO).toBeTruthy();
    });

    test('contract page has attachment section', async ({ authenticatedPage: page }) => {
      if (!contractId) return test.skip();
      await page.goto(`/projects/${projectId}/contracts/${contractId}`);
      const hasAttachments = await page.getByText(/attachment/i).first().isVisible({ timeout: 5_000 }).catch(() => false);
      expect(hasAttachments).toBeTruthy();
    });
  });

  test.describe('Project Detail Tabs', () => {
    test('overview tab visible', async ({ authenticatedPage: page }) => {
      if (!projectId) return test.skip();
      await page.goto(`/projects/${projectId}`);
      await expect(page.getByText(/overview/i).first()).toBeVisible({ timeout: 10_000 });
    });

    test('activities tab accessible', async ({ authenticatedPage: page }) => {
      if (!projectId) return test.skip();
      await page.goto(`/projects/${projectId}`);
      await page.getByText(/activities/i).first().click();
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });

    test('gantt tab accessible', async ({ authenticatedPage: page }) => {
      if (!projectId) return test.skip();
      await page.goto(`/projects/${projectId}`);
      await page.getByText('Gantt').first().click();
      await page.waitForTimeout(2000);
      const hasSvg = await page.locator('svg').first().isVisible({ timeout: 5_000 }).catch(() => false);
      const hasCanvas = await page.locator('canvas').first().isVisible({ timeout: 5_000 }).catch(() => false);
      expect(hasSvg || hasCanvas).toBeTruthy();
    });
  });

  test.describe('Reports - Variance', () => {
    test('reports variance page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/reports/variance');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });

  test.describe('Reports - Portfolio', () => {
    test('portfolio reports page loads', async ({ authenticatedPage: page }) => {
      await page.goto('/reports/portfolio');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });

  test.describe('Sidebar Navigation', () => {
    test('sidebar has Plan group links', async ({ authenticatedPage: page }) => {
      await page.goto('/');
      await expect(page.getByRole('link', { name: /eps/i }).first()).toBeVisible({ timeout: 10_000 });
      await expect(page.getByRole('link', { name: /projects/i }).first()).toBeVisible({ timeout: 5_000 });
    });

    test('sidebar has Execute group links', async ({ authenticatedPage: page }) => {
      await page.goto('/');
      await expect(page.getByRole('link', { name: /resources/i }).first()).toBeVisible({ timeout: 5_000 });
    });

    test('sidebar has Control group links', async ({ authenticatedPage: page }) => {
      await page.goto('/');
      await expect(page.getByRole('link', { name: /risk/i }).first()).toBeVisible({ timeout: 5_000 });
      await expect(page.getByRole('link', { name: /report/i }).first()).toBeVisible({ timeout: 5_000 });
    });
  });

  test.describe('Auth Edge Cases', () => {
    test('login page shows form fields', async ({ page }) => {
      await page.goto('/auth/login');
      await expect(page.locator('form input[type="text"]').first()).toBeVisible();
      await expect(page.locator('form input[type="password"]').first()).toBeVisible();
      await expect(page.getByRole('button', { name: /sign in|login/i }).first()).toBeVisible();
    });

    test('welcome page is public', async ({ page }) => {
      await page.goto('/welcome');
      await expect(page.locator('body')).not.toHaveText(/error/i);
    });
  });
});
