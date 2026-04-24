import { test, expect } from "@playwright/test";
import { login } from "../fixtures/auth.fixture";

// PMS MasterData smoke tests — verifies the 10 master-data screens render and the new
// project-scoped modules (stretches, material sources, material catalogue, stock register,
// GRNs, issues) load without JS errors. Uses the NH-48 seed project.
test.describe("PMS MasterData", () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test("Projects list renders with PMS fields (category, chainage)", async ({ page }) => {
    await page.goto("/projects");
    await expect(page.getByRole("heading", { name: /projects/i })).toBeVisible();
    // NH-48 project should be in the seed data
    await expect(page.getByText(/NH-48|NHAI/i).first()).toBeVisible({ timeout: 10_000 });
  });

  test("Contractor Master list + new form loads", async ({ page }) => {
    await page.goto("/admin/organisations");
    // "New Organisation" link confirms the Contractor Master page rendered.
    const newLink = page.getByRole("link", { name: /new organisation/i });
    await expect(newLink).toBeVisible({ timeout: 15_000 });
    await newLink.click();
    await expect(page).toHaveURL(/organisations\/new$/);
    // Both placeholders begin with letter classes; use exact match to disambiguate.
    await expect(page.getByPlaceholder("AAAAA0000A", { exact: true })).toBeVisible();
    await expect(page.getByPlaceholder("22AAAAA0000A1Z5", { exact: true })).toBeVisible();
  });

  test("Unit Rate Master list renders", async ({ page }) => {
    await page.goto("/admin/unit-rate-master");
    await expect(page.getByRole("heading", { level: 1, name: /unit rate master/i })).toBeVisible();
  });

  test("NH-48 project — stretch master shows seeded stretches + progress", async ({ page }) => {
    await page.goto("/projects");
    // Click NH-48 project row
    await page
      .getByText(/NH-48|NHAI-NH48|BIPROS\/NHAI/i)
      .first()
      .click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/);
    const projectUrl = page.url();
    const projectId = projectUrl.split("/projects/")[1].split("/")[0];

    await page.goto(`/projects/${projectId}/stretches`);
    await expect(
      page.getByRole("heading", { name: /stretch/i }),
    ).toBeVisible();
    // Five seeded stretches STR-001..STR-005
    await expect(page.getByText("STR-001")).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText("STR-005")).toBeVisible();
  });

  test("NH-48 project — material sources tab navigates", async ({ page }) => {
    await page.goto("/projects");
    await page
      .getByText(/NH-48|BIPROS\/NHAI/i)
      .first()
      .click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/);
    const projectId = page.url().split("/projects/")[1].split("/")[0];

    await page.goto(`/projects/${projectId}/material-sources`);
    await expect(
      page.getByRole("heading", { name: /borrow|source/i }),
    ).toBeVisible();
    // Seeder creates BA-001, BA-002, QRY-001, BD-001
    await expect(page.getByText(/BA-001|Khodan/i).first()).toBeVisible({
      timeout: 10_000,
    });
  });

  test("NH-48 project — material catalogue loads", async ({ page }) => {
    await page.goto("/projects");
    await page
      .getByText(/NH-48|BIPROS\/NHAI/i)
      .first()
      .click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/);
    const projectId = page.url().split("/projects/")[1].split("/")[0];

    await page.goto(`/projects/${projectId}/materials`);
    await expect(
      page.getByRole("heading", { name: /material catalogue/i }),
    ).toBeVisible();
  });

  test("NH-48 project — stock register page loads", async ({ page }) => {
    await page.goto("/projects");
    await page
      .getByText(/NH-48|BIPROS\/NHAI/i)
      .first()
      .click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/);
    const projectId = page.url().split("/projects/")[1].split("/")[0];

    await page.goto(`/projects/${projectId}/stock-register`);
    // h1 title — not the empty-state "No stock transactions yet" heading.
    await expect(
      page.getByRole("heading", { name: /stock & inventory register/i, level: 1 }),
    ).toBeVisible();
  });

  test("Create stretch — new form saves and appears in the list", async ({ page }) => {
    await page.goto("/projects");
    await page
      .getByText(/NH-48|BIPROS\/NHAI/i)
      .first()
      .click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/);
    const projectId = page.url().split("/projects/")[1].split("/")[0];

    await page.goto(`/projects/${projectId}/stretches/new`);
    // Name field — locate by the input that sits directly under the "Name" label.
    // The simplest stable selector is by position among the two single-line text inputs in row 1.
    const inputs = page.locator('input[type="text"], input:not([type])');
    // Row 1: Stretch ID (auto) + Name; Row 2 uses placeholders 145+000 / 149+000.
    await inputs.nth(1).fill("E2E Test Stretch");
    await page.getByPlaceholder("145+000").fill("166+000");
    await page.getByPlaceholder("149+000").fill("167+000");
    await page.getByRole("button", { name: /create stretch/i }).click();

    await page.waitForURL(/\/stretches$/, { timeout: 15_000 });
    // One or more rows may match if the test has been run before; .first() is enough.
    await expect(page.getByText("E2E Test Stretch").first()).toBeVisible({ timeout: 10_000 });
  });
});
