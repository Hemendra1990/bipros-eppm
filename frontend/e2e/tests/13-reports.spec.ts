import { test, expect } from "../fixtures/auth.fixture";

test.describe("Reports & Dashboards", () => {
  test("Reports list page loads without errors", async ({
    authenticatedPage: page,
  }) => {
    await page.goto("/reports");
    await expect(
      page.getByRole("heading", { name: "Reports", level: 1 }),
    ).toBeVisible({ timeout: 15_000 });

    const errors = await page.locator(".text-red-500, .text-red-700").allTextContents();
    expect(errors).toHaveLength(0);
  });

  test("Executive dashboard loads", async ({ authenticatedPage: page }) => {
    await page.goto("/dashboards/executive");
    await expect(
      page.getByRole("heading", { name: /Executive|Dashboard/i }).first(),
    ).toBeVisible({ timeout: 15_000 });

    const errors = await page.locator(".text-red-500, .text-red-700").allTextContents();
    expect(errors).toHaveLength(0);
  });

  test("Programme dashboard loads", async ({ authenticatedPage: page }) => {
    await page.goto("/dashboards/programme");
    await expect(
      page.getByRole("heading", { name: /Programme|Dashboard/i }).first(),
    ).toBeVisible({ timeout: 15_000 });

    const errors = await page.locator(".text-red-500, .text-red-700").allTextContents();
    expect(errors).toHaveLength(0);
  });

  test("Operational dashboard loads", async ({ authenticatedPage: page }) => {
    await page.goto("/dashboards/operational");
    await expect(
      page.getByRole("heading", { name: /Operational|Dashboard/i }).first(),
    ).toBeVisible({ timeout: 15_000 });

    const errors = await page.locator(".text-red-500, .text-red-700").allTextContents();
    expect(errors).toHaveLength(0);
  });

  test("Field dashboard loads", async ({ authenticatedPage: page }) => {
    await page.goto("/dashboards/field");
    await expect(
      page.getByRole("heading", { name: /Field|Dashboard/i }).first(),
    ).toBeVisible({ timeout: 15_000 });

    const errors = await page.locator(".text-red-500, .text-red-700").allTextContents();
    expect(errors).toHaveLength(0);
  });
});
