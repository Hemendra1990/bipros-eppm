# DPR Activity Drawer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the inline `DprActivityForm` on the DPR page with a right-side slide-in drawer.

**Architecture:** Add a generic `Drawer` component in `frontend/src/components/common/`, render it via a React portal so it overlays the sticky page header, and use strict close behavior (no backdrop / ESC dismissal) to protect typed form data. Trim the form's redundant outer `Card` chrome and title bar so the drawer owns the title and X close button. Wire the drawer into `app/(app)/projects/[projectId]/dpr/page.tsx`.

**Tech Stack:** Next.js 16, React 19, TypeScript, Tailwind CSS, lucide-react icons, Vitest + @testing-library/react for unit tests, Playwright for e2e (existing — no new e2e here). Spec: `docs/superpowers/specs/2026-05-06-dpr-activity-drawer-design.md`.

---

## File Structure

| Path | Action | Responsibility |
|---|---|---|
| `frontend/src/components/common/Drawer.tsx` | Create | Generic right-side slide-in drawer (portal, backdrop, slide animation, body-scroll-lock, strict close) |
| `frontend/src/components/common/__tests__/Drawer.test.tsx` | Create | Unit tests covering open/close transitions, strict close behavior, scroll lock |
| `frontend/src/components/dpr/DprActivityForm.tsx` | Modify (lines 4–6, 226–248, 498) | Remove outer `<Card>` wrapper; replace title-bar row with badges-only row (drawer owns title/X) |
| `frontend/src/app/(app)/projects/[projectId]/dpr/page.tsx` | Modify (lines 1–22, 230–246) | Import `Drawer`; replace inline-form render with `<Drawer>…</Drawer>` |

---

## Task 1: Generic `Drawer` component — write the failing tests

**Files:**
- Create: `frontend/src/components/common/__tests__/Drawer.test.tsx`

This task only writes the tests. The implementation (Task 2) makes them pass.

- [ ] **Step 1: Create the test file**

Create `frontend/src/components/common/__tests__/Drawer.test.tsx` with the full content below.

```tsx
import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { Drawer } from "../Drawer";

afterEach(() => {
  cleanup();
  document.body.style.overflow = "";
});

describe("Drawer", () => {
  it("renders title and children when open", () => {
    render(
      <Drawer open onClose={() => {}} title="Add Activity">
        <p>Form body</p>
      </Drawer>
    );
    expect(screen.getByText("Add Activity")).toBeInTheDocument();
    expect(screen.getByText("Form body")).toBeInTheDocument();
  });

  it("renders title and children even when closed (stays mounted for slide-out)", () => {
    render(
      <Drawer open={false} onClose={() => {}} title="Add Activity">
        <p>Form body</p>
      </Drawer>
    );
    expect(screen.getByText("Add Activity")).toBeInTheDocument();
    expect(screen.getByText("Form body")).toBeInTheDocument();
  });

  it("applies translate-x-0 when open and translate-x-full when closed", () => {
    const { rerender } = render(
      <Drawer open onClose={() => {}} title="T">
        <p>x</p>
      </Drawer>
    );
    const panelOpen = screen.getByRole("dialog");
    expect(panelOpen.className).toContain("translate-x-0");
    expect(panelOpen.className).not.toContain("translate-x-full");

    rerender(
      <Drawer open={false} onClose={() => {}} title="T">
        <p>x</p>
      </Drawer>
    );
    const panelClosed = screen.getByRole("dialog");
    expect(panelClosed.className).toContain("translate-x-full");
  });

  it("calls onClose when the X close button is clicked", () => {
    const onClose = vi.fn();
    render(
      <Drawer open onClose={onClose} title="T">
        <p>x</p>
      </Drawer>
    );
    fireEvent.click(screen.getByRole("button", { name: /close/i }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("does NOT call onClose when the backdrop is clicked (strict close)", () => {
    const onClose = vi.fn();
    render(
      <Drawer open onClose={onClose} title="T">
        <p>x</p>
      </Drawer>
    );
    fireEvent.click(screen.getByTestId("drawer-backdrop"));
    expect(onClose).not.toHaveBeenCalled();
  });

  it("does NOT call onClose when Escape is pressed (strict close)", () => {
    const onClose = vi.fn();
    render(
      <Drawer open onClose={onClose} title="T">
        <p>x</p>
      </Drawer>
    );
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).not.toHaveBeenCalled();
  });

  it("locks body scroll while open and restores it on close", () => {
    document.body.style.overflow = "auto";
    const { rerender } = render(
      <Drawer open onClose={() => {}} title="T">
        <p>x</p>
      </Drawer>
    );
    expect(document.body.style.overflow).toBe("hidden");

    rerender(
      <Drawer open={false} onClose={() => {}} title="T">
        <p>x</p>
      </Drawer>
    );
    expect(document.body.style.overflow).toBe("auto");
  });

  it("restores body scroll on unmount", () => {
    document.body.style.overflow = "auto";
    const { unmount } = render(
      <Drawer open onClose={() => {}} title="T">
        <p>x</p>
      </Drawer>
    );
    expect(document.body.style.overflow).toBe("hidden");
    unmount();
    expect(document.body.style.overflow).toBe("auto");
  });

  it("respects custom widthClass", () => {
    render(
      <Drawer open onClose={() => {}} title="T" widthClass="max-w-3xl">
        <p>x</p>
      </Drawer>
    );
    expect(screen.getByRole("dialog").className).toContain("max-w-3xl");
  });
});
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `cd frontend && pnpm exec vitest run src/components/common/__tests__/Drawer.test.tsx`
Expected: FAIL — `Cannot find module '../Drawer'` (the implementation file doesn't exist yet).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/common/__tests__/Drawer.test.tsx
git commit -m "test(drawer): add failing tests for generic Drawer component"
```

---

## Task 2: Generic `Drawer` component — implementation

**Files:**
- Create: `frontend/src/components/common/Drawer.tsx`

- [ ] **Step 1: Create the Drawer implementation**

Create `frontend/src/components/common/Drawer.tsx` with the full content below.

```tsx
"use client";

import { useEffect, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface DrawerProps {
  open: boolean;
  onClose: () => void;
  title: string;
  /** Tailwind max-width class for the panel. Default: "max-w-2xl" (~672px). */
  widthClass?: string;
  children: ReactNode;
}

/**
 * Right-side slide-in drawer with strict close behavior:
 * the backdrop and ESC key do NOT dismiss — only the X button (or whatever
 * the consumer wires into onClose) closes it. Designed for forms with
 * unsaved data where accidental dismissal is costly.
 *
 * Stays mounted across open/close so the slide-out transition can play.
 * Renders into document.body via a portal to avoid being clipped by the
 * stacking/transform context of sticky page headers.
 */
export function Drawer({
  open,
  onClose,
  title,
  widthClass = "max-w-2xl",
  children,
}: DrawerProps) {
  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [open]);

  if (typeof document === "undefined") return null;

  const node = (
    <div aria-hidden={!open}>
      <div
        data-testid="drawer-backdrop"
        className={cn(
          "fixed inset-0 z-40 bg-charcoal/40 transition-opacity duration-200 ease-out",
          open ? "opacity-100" : "pointer-events-none opacity-0"
        )}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={cn(
          "fixed inset-y-0 right-0 z-50 flex w-full flex-col bg-paper shadow-[0_20px_40px_rgba(28,28,28,0.12)] transition-transform duration-200 ease-out",
          widthClass,
          open ? "translate-x-0" : "pointer-events-none translate-x-full"
        )}
      >
        <div className="flex items-center justify-between gap-3 border-b border-hairline px-5 py-3">
          <h2 className="font-display text-lg font-semibold tracking-tight text-charcoal">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-md p-1.5 text-slate transition-colors hover:bg-ivory hover:text-charcoal"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto">{children}</div>
      </div>
    </div>
  );

  return createPortal(node, document.body);
}
```

- [ ] **Step 2: Run the Drawer tests**

Run: `cd frontend && pnpm exec vitest run src/components/common/__tests__/Drawer.test.tsx`
Expected: PASS — all 9 tests green.

- [ ] **Step 3: Run lint**

Run: `cd frontend && pnpm lint --max-warnings=0 src/components/common/Drawer.tsx src/components/common/__tests__/Drawer.test.tsx`
Expected: no errors, no warnings.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/common/Drawer.tsx
git commit -m "feat(common): add generic right-side Drawer component"
```

---

## Task 3: Trim `DprActivityForm` outer chrome

The form currently wraps its body in `<Card variant="elevated">` with a top header row containing the title text + an X close button. The drawer now owns those. Replace the title row with a slim badges-only row, and unwrap the `Card`. The existing sticky footer (totals + Cancel/Save) below the closing `</Card>` stays as-is — `position: sticky; bottom: 0` continues to work inside the drawer body's scroll container.

**Files:**
- Modify: `frontend/src/components/dpr/DprActivityForm.tsx` (lines 4–6, 226–248, 498)

- [ ] **Step 1: Drop the now-unused `Card` and `X` imports**

The only `<X />` usage in this file is inside the title bar's close button (line 246), which gets removed in Step 2. Drop both imports up front.

In `frontend/src/components/dpr/DprActivityForm.tsx`, find this block near the top:

```tsx
import { Briefcase, HardHat, Package, Save, X } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
```

Replace with (drop the `Card` import; remove `X` from the lucide import; `Badge` stays):

```tsx
import { Briefcase, HardHat, Package, Save } from "lucide-react";
import { Badge } from "@/components/ui/badge";
```

- [ ] **Step 2: Replace the outer `<Card>` opener and title bar (lines 226–248) with a plain badges row**

Find this block (the form's `return` through the end of the title bar):

```tsx
  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Card variant="elevated" className="p-0">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-hairline px-5 py-3">
          <div className="flex items-center gap-2">
            <h3 className="font-display text-lg font-semibold text-charcoal">
              {editing ? "Edit activity" : "New activity"}
            </h3>
            <Badge variant={editing ? "info" : "gold"} withDot>
              {state.approvalStatus ?? "DRAFT"}
            </Badge>
            {state.shift && (
              <Badge variant="neutral">{state.shift === "DAY" ? "Day shift" : "Night shift"}</Badge>
            )}
          </div>
          <button
            type="button"
            onClick={onCancel}
            className="rounded-md p-1.5 text-slate hover:bg-ivory hover:text-charcoal"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
```

Replace with (no `<Card>` wrapper; title text + X are gone — the drawer renders them — but the badges row is preserved):

```tsx
  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="flex flex-wrap items-center gap-2 border-b border-hairline px-5 py-3">
        <Badge variant={editing ? "info" : "gold"} withDot>
          {state.approvalStatus ?? "DRAFT"}
        </Badge>
        {state.shift && (
          <Badge variant="neutral">{state.shift === "DAY" ? "Day shift" : "Night shift"}</Badge>
        )}
      </div>
```

- [ ] **Step 3: Remove the closing `</Card>` tag**

Find this block (around line 497–500 after the previous edits):

```tsx
          />
        </div>
      </Card>

      {/* Sticky footer: totals + save */}
```

Replace with (drop the `</Card>` line):

```tsx
          />
        </div>

      {/* Sticky footer: totals + save */}
```

- [ ] **Step 4: Run lint**

Run: `cd frontend && pnpm lint --max-warnings=0 src/components/dpr/DprActivityForm.tsx`
Expected: no errors, no warnings (especially no "unused import" for `Card` or `X`).

- [ ] **Step 5: TypeScript check**

Run: `cd frontend && pnpm exec tsc --noEmit`
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/dpr/DprActivityForm.tsx
git commit -m "refactor(dpr): drop form Card chrome — drawer will own title and close"
```

---

## Task 4: Wire `Drawer` into the DPR page

**Files:**
- Modify: `frontend/src/app/(app)/projects/[projectId]/dpr/page.tsx` (lines 1–22, 230–246)

- [ ] **Step 1: Add the `Drawer` import**

Find this block at the top of `frontend/src/app/(app)/projects/[projectId]/dpr/page.tsx`:

```tsx
import { AiInsightsPanel } from "@/components/ai/AiInsightsPanel";
import { TabTip } from "@/components/common/TabTip";
import { DprActivityForm } from "@/components/dpr/DprActivityForm";
```

Replace with (add `Drawer` import alphabetically before `TabTip`):

```tsx
import { AiInsightsPanel } from "@/components/ai/AiInsightsPanel";
import { Drawer } from "@/components/common/Drawer";
import { TabTip } from "@/components/common/TabTip";
import { DprActivityForm } from "@/components/dpr/DprActivityForm";
```

- [ ] **Step 2: Replace the inline form render with the Drawer**

Find this block (lines 230–246):

```tsx
        {showForm && (
          <div className="mb-6">
            <DprActivityForm
              // Re-mount when switching between edit targets (or new vs. edit) so the form's
              // lazy useState initializer reseeds. Without this, clicking Edit on row B while
              // the form for row A is open would keep A's children visible.
              key={editing?.id ?? "new"}
              editing={editing}
              defaultDate={editing?.reportDate ?? from ?? todayIso()}
              supervisorOptions={supervisorOptions}
              activityOptions={activityOptions}
              boqOptions={boqOptions}
              onCancel={closeForm}
              onSave={handleSave}
            />
          </div>
        )}
```

Replace with (drawer is rendered unconditionally so its slide-out can animate; `open` drives visibility):

```tsx
        <Drawer
          open={showForm}
          onClose={closeForm}
          title={editing ? "Edit Activity" : "Add Activity"}
        >
          <DprActivityForm
            // Re-mount when switching between edit targets (or new vs. edit) so the form's
            // lazy useState initializer reseeds. Without this, clicking Edit on row B while
            // the form for row A is open would keep A's children visible.
            key={editing?.id ?? "new"}
            editing={editing}
            defaultDate={editing?.reportDate ?? from ?? todayIso()}
            supervisorOptions={supervisorOptions}
            activityOptions={activityOptions}
            boqOptions={boqOptions}
            onCancel={closeForm}
            onSave={handleSave}
          />
        </Drawer>
```

- [ ] **Step 3: Run lint**

Run: `cd frontend && pnpm lint --max-warnings=0 "src/app/(app)/projects/[projectId]/dpr/page.tsx"`
Expected: no errors, no warnings.

- [ ] **Step 4: TypeScript check**

Run: `cd frontend && pnpm exec tsc --noEmit`
Expected: no errors.

- [ ] **Step 5: Build check (Next.js production build)**

Run: `cd frontend && pnpm build`
Expected: build succeeds without errors.

- [ ] **Step 6: Commit**

```bash
git add "frontend/src/app/(app)/projects/[projectId]/dpr/page.tsx"
git commit -m "feat(dpr): open Add/Edit Activity in a right-side Drawer"
```

---

## Task 5: Manual browser verification

**Files:** none modified — verification only. CLAUDE.md requires UI changes be exercised in a browser before claiming completion.

- [ ] **Step 1: Ensure backend is running**

Run (in a separate terminal if not already running):

```bash
cd backend && mvn spring-boot:run -pl bipros-api
```

Wait until you see `Started BiprosApplication` in the logs.

- [ ] **Step 2: Start the frontend dev server**

Run:

```bash
cd frontend && pnpm dev
```

Expected: server listening on `http://localhost:3000`.

- [ ] **Step 3: Navigate to a project's DPR page**

In a browser, log in as `admin` / `admin123` (seeded on first backend boot), pick any project from the list, and navigate to its **Daily Progress Report** tab. URL pattern: `http://localhost:3000/projects/<projectId>/dpr`.

- [ ] **Step 4: Verify each item from the spec's Testing section**

Run through these checks; all must pass before marking this task complete:

1. Click **Add Activity** → drawer slides in from the right; backdrop appears; day list visible underneath.
2. Click on the backdrop → drawer does **not** close.
3. Press **Escape** → drawer does **not** close.
4. Click the X (top-right of drawer) → drawer slides out and closes.
5. Click **Add Activity** again → drawer reopens. Click **Cancel** in the form footer → drawer closes.
6. Click **Edit** on an existing DPR row → drawer opens with that row's data prefilled. The header reads "Edit Activity".
7. While the drawer is open on row A, click **Edit** on row B → drawer contents swap to row B without the drawer closing.
8. Save a new DPR (fill required fields → click Save) → drawer closes, new row appears in the list.
9. Verify the page body cannot be scrolled while the drawer is open; verify it can scroll again after closing.
10. Verify the sticky page header (with the From/To filter inputs and **Add Activity** button) renders **behind** the backdrop with correct stacking — backdrop dims the header but the drawer panel is above.
11. Resize the browser window to ≤672px wide → drawer occupies full width.
12. Open the drawer, then refresh the page (browser reload) → no console errors; body scroll restored on the fresh page.

- [ ] **Step 5: Check the browser console for errors**

In DevTools → Console, confirm no red errors during any of the steps above. Hydration warnings about `createPortal` should not appear (the component guards `typeof document === "undefined"`).

- [ ] **Step 6: Stop the dev server**

Stop `pnpm dev` (Ctrl+C). No commit for this task — verification only.

---

## Done

After Task 5 passes, the feature is complete: 4 commits (test, drawer impl, form trim, page wiring). No additional follow-ups beyond those listed in the spec's "Out of scope" section.
