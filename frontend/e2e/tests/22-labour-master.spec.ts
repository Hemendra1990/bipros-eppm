import { test, expect } from "@playwright/test";
import { login } from "../fixtures/auth.fixture";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

/**
 * Pull the first project's UUID from the API. Lets the per-project labour-master
 * routes render without hardcoding a project ID into the test.
 */
async function firstProjectId(page: import("@playwright/test").Page): Promise<string | null> {
  const res = await page.request.get(`${API_BASE}/v1/projects?page=0&size=1`);
  if (!res.ok()) return null;
  const body = (await res.json()) as { data?: { content?: Array<{ id: string }> } };
  return body.data?.content?.[0]?.id ?? null;
}

test.describe("Labour Master", () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test("layout renders the module header and tab nav", async ({ page }) => {
    await page.goto("/labour-master");
    await expect(page.getByRole("heading", { name: "Labour Master" })).toBeVisible();
    for (const tab of ["Dashboard", "Cards", "Table", "Reference", "+ Add"]) {
      await expect(page.getByRole("link", { name: tab })).toBeVisible();
    }
  });

  test("dashboard shows the project-required hint when no projectId is set", async ({ page }) => {
    await page.goto("/labour-master");
    await expect(page.getByText(/Select a project/i)).toBeVisible();
  });

  test("reference page lists the 5 grades and Oman regulatory notes", async ({ page }) => {
    await page.goto("/labour-master/reference");
    // 5 grade rows + 1 header + total = at least 6 rows; assert each grade letter is visible.
    for (const grade of ["A", "B", "C", "D", "E"]) {
      await expect(page.locator("table").getByText(grade, { exact: true }).first()).toBeVisible();
    }
    await expect(
      page.getByRole("heading", { name: /Regulatory & Compliance Notes/i })
    ).toBeVisible();
  });

  test("add-designation form renders all required inputs", async ({ page }) => {
    await page.goto("/labour-master/new");
    await expect(page.getByText("Worker Code")).toBeVisible();
    await expect(page.getByText("Designation")).toBeVisible();
    await expect(page.getByText("Trade")).toBeVisible();
    await expect(page.getByText("Daily Rate (OMR)")).toBeVisible();
    await expect(page.getByRole("button", { name: "Save" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancel" })).toBeVisible();
  });

  test("dashboard renders KPIs when a real project is in scope", async ({ page }) => {
    const projectId = await firstProjectId(page);
    test.skip(!projectId, "no project available in this database");
    await page.goto(`/labour-master?projectId=${projectId}`);
    // KPI tile labels are rendered by common/KpiTile; the labels themselves are stable.
    for (const label of [
      "Total Designations",
      "Total Workforce",
      "Daily Payroll",
      "Skill Categories",
      "Nationality Mix",
    ]) {
      await expect(page.getByText(label).first()).toBeVisible();
    }
  });
});
