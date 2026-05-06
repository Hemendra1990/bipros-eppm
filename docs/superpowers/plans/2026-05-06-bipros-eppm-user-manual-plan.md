# Bipros EPPM User Manual — Implementation Plan

> **For agentic workers:** Use subagent-driven-development or executing-plans to implement task-by-task.

**Goal:** Expand existing `user-guide/` into a comprehensive user manual with Task Guides, Module Reference, and Appendices.

**Tech Stack:** Docusaurus 3.9.2, KaTeX for MathJax, Markdown/MDX

---

## Task 1: Infrastructure — KaTeX & Sidebar

**Files:**
- Modify: `user-guide/docusaurus.config.ts`
- Modify: `user-guide/sidebars.ts`
- Run: `cd user-guide && yarn add remark-math@6 rehype-katex@7 katex`

**Steps:**
- [ ] Install KaTeX dependencies
- [ ] Update docusaurus.config.ts with remarkMath/rehypeKatex plugins and katex CSS
- [ ] Update sidebars.ts with new structure (Getting Started, Task Guides, Module Reference, Appendices)
- [ ] Run `yarn build` to verify

---

## Task 2: Getting Started Expansion

**Files to create:**
- `user-guide/docs/getting-started/user-roles-permissions.md`
- `user-guide/docs/getting-started/ui-guide.md`

---

## Task 3: Task Guides

**Files to create:**
- `user-guide/docs/task-guides/index.md`
- `user-guide/docs/task-guides/creating-first-project.md`
- `user-guide/docs/task-guides/setting-up-wbs.md`
- `user-guide/docs/task-guides/scheduling-activities.md`
- `user-guide/docs/task-guides/tracking-daily-progress.md`
- `user-guide/docs/task-guides/managing-ra-bills.md`
- `user-guide/docs/task-guides/resource-planning-deployment.md`
- `user-guide/docs/task-guides/conducting-risk-analysis.md`
- `user-guide/docs/task-guides/running-schedule-compression.md`
- `user-guide/docs/task-guides/managing-permits.md`
- `user-guide/docs/task-guides/closing-project.md`

---

## Task 4: Module Reference — Core

**Files to create:**
- `user-guide/docs/module-reference/activities-scheduling/index.md`
- `user-guide/docs/module-reference/evm/index.md`
- `user-guide/docs/module-reference/evm/formulas.md`
- `user-guide/docs/module-reference/evm/techniques.md`
- `user-guide/docs/module-reference/cost-management/index.md`
- `user-guide/docs/module-reference/resource-management/index.md`

---

## Task 5: Module Reference — Supporting

**Files to create:**
- `user-guide/docs/module-reference/baselines/index.md`
- `user-guide/docs/module-reference/contracts-ra-bills/index.md`
- `user-guide/docs/module-reference/documents-drawings/index.md`
- `user-guide/docs/module-reference/gis-satellite/index.md`
- `user-guide/docs/module-reference/permits/index.md`

---

## Task 6: Module Reference — Advanced

**Files to create:**
- `user-guide/docs/module-reference/integrations/index.md`
- `user-guide/docs/module-reference/ai-predictions/index.md`
- `user-guide/docs/module-reference/security-access-control/index.md`

---

## Task 7: Appendices

**Files to create:**
- `user-guide/docs/appendices/formula-reference.md`
- `user-guide/docs/appendices/actor-use-case-matrix.md`
- `user-guide/docs/appendices/permission-matrix.md`

---

## Task 8: Final Build & Verification

- [ ] Run `yarn build`
- [ ] Fix any broken links
- [ ] Verify MathJax rendering
- [ ] Verify sidebar structure
