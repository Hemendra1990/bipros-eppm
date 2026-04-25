import path from "node:path";
import { test, expect } from "../fixtures/auth.fixture";

test.describe("Documents tab", () => {
  test("default folders, create custom folder, upload + download", async ({ authenticatedPage: page }) => {
    // Navigate to the first seeded project's Documents tab
    await page.goto("/projects");
    await page.getByRole("table").getByRole("link").first().click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+/, { timeout: 10_000 });

    // The Documents tab is rendered as a <button> in the project tab strip
    await page.getByRole("button", { name: "Documents" }).first().click();
    await page.waitForURL(/\/projects\/[0-9a-f-]+\/documents/, { timeout: 10_000 });

    // The page renders a TabTip with "TIP · DOCUMENT MANAGEMENT"
    await expect(page.getByText(/DOCUMENT MANAGEMENT/i)).toBeVisible();

    // 1. Default folders are present
    await expect(page.getByText("📁 Drawings")).toBeVisible();
    await expect(page.getByText("📁 Specifications")).toBeVisible();
    await expect(page.getByText("📁 Contracts")).toBeVisible();
    await expect(page.getByText("📁 Approvals")).toBeVisible();
    await expect(page.getByText("📁 Correspondence")).toBeVisible();
    await expect(page.getByText("📁 As-Built")).toBeVisible();
    await expect(page.getByText("📁 General")).toBeVisible();

    // 2. Create a custom root folder
    const folderName = `E2E Folder ${Date.now()}`;
    const folderCode = `E2E${Date.now() % 10000}`;
    await page.getByTestId("new-folder-root").click();
    await expect(page.getByRole("dialog")).toBeVisible();
    await page.getByLabel("Name").fill(folderName);
    await page.getByLabel("Code").fill(folderCode);
    await page.getByRole("button", { name: "Create folder" }).click();
    await expect(page.getByRole("dialog")).toBeHidden();
    await expect(page.getByText(`📁 ${folderName}`)).toBeVisible();

    // The new folder is auto-selected → upload button is reachable
    await page.getByRole("button", { name: "+ Upload Document" }).click();

    // 3. Upload a file
    const docTitle = `E2E Doc ${Date.now()}`;
    const docNumber = `DOC-${Date.now() % 100000}`;
    await page.getByPlaceholder("Document title").fill(docTitle);
    await page.getByPlaceholder("e.g., DOC-001").fill(docNumber);
    await page.locator('input[type="file"]').setInputFiles(
      path.resolve(__dirname, "../fixtures/sample.pdf")
    );
    await page.getByRole("button", { name: "Upload Document" }).last().click();

    // 4. Verify document appears in the table
    await expect(page.getByRole("cell", { name: docTitle })).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole("cell", { name: docNumber })).toBeVisible();

    // 5. Verify Download button is visible for the uploaded doc
    // (The download is programmatic via <a>.click(); we just confirm the button is enabled)
    await expect(
      page.getByRole("button", { name: "Download" }).first()
    ).toBeEnabled();

    // 6. Sub-folder under Drawings — hover reveals "+" button; create child
    const drawingsRow = page.locator("li", { has: page.getByText("📁 Drawings") }).first();
    await drawingsRow.hover();
    const subName = `Plan View ${Date.now()}`;
    const subCode = `PV${Date.now() % 10000}`;
    // The child-folder button has aria-label="New sub-folder under Drawings"
    await drawingsRow.getByRole("button", { name: /New sub-folder under Drawings/i }).click({ force: true });
    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(
      page.getByRole("heading", { name: /New sub-folder under Drawings/i })
    ).toBeVisible();
    await page.getByLabel("Name").fill(subName);
    await page.getByLabel("Code").fill(subCode);
    await page.getByRole("button", { name: "Create folder" }).click();
    await expect(page.getByRole("dialog")).toBeHidden();
    // Success handler expands Drawings; children query re-runs with updated key,
    // so the new sub-folder appears in the tree.
    await expect(page.getByText(`📁 ${subName}`)).toBeVisible({ timeout: 15_000 });
  });
});
