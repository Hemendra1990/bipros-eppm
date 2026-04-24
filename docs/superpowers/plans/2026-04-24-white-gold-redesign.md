# Bipros EPPM — White & Gold Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the approved White & Gold identity (Fraunces + Inter display, editorial-gold accents) across the Bipros EPPM frontend in three sequential PRs — Foundation → Hero pages → Landing repaint.

**Architecture:** Tailwind v4 `@theme inline` CSS-first tokens authored in `frontend/src/app/globals.css`; Fraunces / Inter / JetBrains Mono loaded via `next/font/google` as CSS variables; seven shared UI primitives rebuilt to the new variant system; `Sidebar` + `Header` redesigned with a gold-rail active state and `⌘K` command-palette search; three hero pages (Dashboard, Projects list, Login/landing) polished as the pattern reference. Every other route under `src/app/(app)/**` inherits the new tokens automatically.

**Tech Stack:** Next.js 16.2.2 (App Router) · React 19.2.4 · Tailwind v4 · next-themes 0.4.6 · `next/font/google` (Fraunces variable + Inter + JetBrains Mono) · lucide-react 1.7.0 · clsx + tailwind-merge · TanStack Query (already present) · Playwright e2e (already present).

---

## Context

- The design spec is `docs/superpowers/specs/2026-04-24-white-gold-redesign-design.md`. Read it end-to-end before starting. This plan is the execution layer; the spec is the source of truth for any question about colour, type, structure, or scope.
- **Next.js 16 is NOT the Next.js in your training data.** `frontend/AGENTS.md` is explicit about this. Before writing any code that touches Next-specific APIs (especially `next/font`, layouts, `next-themes`), read the relevant file in `frontend/node_modules/next/dist/docs/` and verify the API you intend to call still exists and takes the parameters you expect.
- The frontend runs on `pnpm`, not npm. Always use `pnpm` from inside `frontend/`.
- The backend must be running at `http://localhost:8080` for the Playwright e2e suite to pass (see `CLAUDE.md` at repo root for `./scripts/*` helpers). For this visual redesign you rarely need the backend; the dev server alone is usually sufficient.
- Dev server already runs on port 3000 by default. `playwright.config.ts` auto-starts `pnpm dev` if it's not running.

## Testing & verification approach

There is **no unit-test runner** in the frontend — only Playwright e2e. For this visual redesign we use a four-gate verification on every task:

1. `pnpm tsc --noEmit` — no TypeScript errors.
2. `pnpm lint` — no ESLint errors (warnings tolerated).
3. `pnpm dev` running + **manual visual walk-through** of the affected surface — confirm colour, type, spacing match the approved mockups.
4. At the end of each phase (not per task), the full Playwright suite: `pnpm test:e2e`. All existing tests must pass (they assert on roles and text, not colours or classes, so they survive this redesign).

Commit after every task. Each phase closes with one clean merge-ready commit on top of the task commits. Do **not** squash; reviewers walk the history per task.

## Critical preflight notes

1. **Playfair Display is NOT used** in the final design. Fraunces replaces it. Earlier mockups referenced Playfair as a fallback; that was illustrative only — Fraunces is the single display face.
2. **Dark mode is deferred.** Do not touch the `.dark { ... }` block in `globals.css`. The `ThemeProvider` default flips from `"dark"` to `"light"` in Task 1.4. Users who explicitly toggle dark will still see the old blue-accent dark UI — that is accepted per the spec.
3. **Do not squash, do not force-push, do not skip hooks.** Each task commit stands on its own.

## File structure — what gets touched

**Phase 1 (Foundation):**
- Modify: `frontend/src/app/layout.tsx` · `frontend/src/app/globals.css` · `frontend/src/app/(app)/layout.tsx`
- Modify: `frontend/src/components/theme/ThemeProvider.tsx`
- Modify: all seven `frontend/src/components/ui/*.tsx` (Button, Card, Dialog, Input, Badge, Progress, Table)
- Modify: `frontend/src/components/common/Sidebar.tsx` · `frontend/src/components/common/Header.tsx`
- Grep + edit: Button `outline` variant callers (renamed to `ghost`) across the codebase

**Phase 2 (Hero pages):**
- Modify: `frontend/src/components/common/TabTip.tsx`
- Modify: `frontend/src/app/(app)/page.tsx`
- Modify: `frontend/src/app/(app)/projects/page.tsx`

**Phase 3 (Landing repaint):**
- Create: `frontend/src/app/auth/login/_components/` with one file per section (10 files)
- Modify: `frontend/src/app/auth/login/page.tsx`
- **Delete:** `frontend/src/app/auth/login/landing.css`

---

## Phase 1 — Foundation (PR 1)

### Task 1.1: Preflight — verify Next.js 16 `next/font/google` supports Fraunces variable with optical-size axis

**Files:** none (research only)

- [ ] **Step 1: Read the `next/font` docs shipped with this project's Next.js**

```bash
ls frontend/node_modules/next/dist/docs/ 2>/dev/null | head -20
find frontend/node_modules/next -path '*/docs*' -name '*font*' 2>/dev/null | head
```

Open whatever the listing surfaces for font loading (typically `font/google.md` or a route under `api-reference/components/font.md`). Confirm:
- `axes` option still exists for variable fonts and accepts the array form (e.g., `axes: ['opsz']`).
- `Fraunces`, `Inter`, `JetBrains_Mono` are exported from `next/font/google`.
- `variable` prop is supported (produces `.variable` className returning a CSS `--font-*` variable).
- `display`, `subsets`, `weight`, `style` are all still accepted.

- [ ] **Step 2: If the API differs from what Task 1.2 assumes, stop and re-plan**

If `axes` has been renamed, the config syntax has changed, or Fraunces/Inter/JetBrains_Mono aren't exported — pause and report. Do NOT improvise around a broken font config; the redesign depends on Fraunces's `opsz` axis rendering correctly at 144.

- [ ] **Step 3: No commit** — this task is research only.

---

### Task 1.2: Load Fraunces + Inter + JetBrains Mono via `next/font/google`; retire Geist

**Files:**
- Modify: `frontend/src/app/layout.tsx`

- [ ] **Step 1: Read current `layout.tsx`** to confirm the `Providers` (`@/lib/providers`) and `AppToaster` wiring is still what you're replacing. It is.

- [ ] **Step 2: Replace `layout.tsx` contents with:**

```tsx
import type { Metadata } from "next";
import { Fraunces, Inter, JetBrains_Mono } from "next/font/google";
import "./globals.css";
import { Providers } from "@/lib/providers";
import { AppToaster } from "@/components/common/Toaster";
import { ThemeProvider } from "@/components/theme/ThemeProvider";

const fraunces = Fraunces({
  subsets: ["latin"],
  axes: ["opsz"],
  weight: ["500", "600", "700"],
  style: ["normal", "italic"],
  variable: "--font-fraunces",
  display: "swap",
});

const inter = Inter({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-inter",
  display: "swap",
});

const jetbrainsMono = JetBrains_Mono({
  subsets: ["latin"],
  weight: ["400", "500"],
  variable: "--font-jetbrains",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Bipros EPPM",
  description: "Enterprise Project Portfolio Management System",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html
      lang="en"
      className={`${fraunces.variable} ${inter.variable} ${jetbrainsMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="h-full bg-background text-foreground">
        <ThemeProvider>
          <Providers>{children}</Providers>
        </ThemeProvider>
        <AppToaster />
      </body>
    </html>
  );
}
```

- [ ] **Step 3: Verify the TypeScript compiles**

```bash
cd frontend && pnpm tsc --noEmit
```
Expected: no errors. (Tokens still reference `--font-geist-sans` in `globals.css` at this point — that's fine; Task 1.3 rewrites globals.)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/layout.tsx
git commit -m "feat(frontend): load Fraunces + Inter + JetBrains Mono via next/font"
```

---

### Task 1.3: Rewrite `globals.css` — new `:root` tokens + `@theme inline` additions

**Files:**
- Modify: `frontend/src/app/globals.css`

**Important:** Preserve the `@custom-variant dark` directive and the **entire** `.dark { ... }` block untouched (dark-mode harmonisation is out of scope). Keep the `glass`, `glass-subtle`, `glow-accent`, `bg-grid`, `::selection`, scrollbar, and universal transition rules — they're used by inherited pages we haven't rebuilt yet. Update their *colour references* only where they point at tokens we're renaming.

- [ ] **Step 1: Replace `globals.css` contents with:**

```css
@import "tailwindcss";

@custom-variant dark (&:where(.dark, .dark *));

@theme inline {
  /* legacy aliases (kept during migration so inherited pages keep rendering) */
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-surface: var(--surface);
  --color-surface-hover: var(--surface-hover);
  --color-surface-active: var(--surface-active);
  --color-border: var(--border);
  --color-border-subtle: var(--border-subtle);
  --color-accent: var(--accent);
  --color-accent-hover: var(--accent-hover);
  --color-accent-glow: var(--accent-glow);
  --color-text-primary: var(--text-primary);
  --color-text-secondary: var(--text-secondary);
  --color-text-muted: var(--text-muted);
  --color-success: var(--success);
  --color-warning: var(--warning);
  --color-danger: var(--danger);
  --color-info: var(--info);
  --color-input: var(--input);
  --color-ring: var(--ring);
  --color-muted-foreground: var(--muted-foreground);

  /* new White & Gold tokens */
  --color-paper: var(--paper);
  --color-ivory: var(--ivory);
  --color-parchment: var(--parchment);
  --color-charcoal: var(--charcoal);
  --color-slate: var(--slate);
  --color-ash: var(--ash);
  --color-hairline: var(--hairline);
  --color-divider: var(--divider);
  --color-gold: var(--gold);
  --color-gold-deep: var(--gold-deep);
  --color-gold-ink: var(--gold-ink);
  --color-gold-tint: var(--gold-tint);
  --color-emerald: var(--emerald);
  --color-bronze-warn: var(--bronze-warn);
  --color-burgundy: var(--burgundy);
  --color-steel: var(--steel);

  --font-sans: var(--font-inter);
  --font-display: var(--font-fraunces);
  --font-mono: var(--font-jetbrains);
}

:root {
  /* ---- legacy token aliases, now pointing at the new palette ---- */
  --background: #FFFFFF;          /* paper */
  --foreground: #1C1C1C;          /* charcoal */
  --surface: #FFFFFF;             /* paper */
  --surface-hover: #FAF9F6;       /* ivory */
  --surface-active: #F5F2E8;      /* parchment */
  --border: #EDE7D3;              /* warm hairline */
  --border-subtle: #E5E7EB;       /* cool divider */
  --accent: #D4AF37;              /* gold */
  --accent-hover: #B8962E;        /* gold-deep */
  --accent-glow: rgba(212, 175, 55, 0.12);
  --text-primary: #1C1C1C;        /* charcoal */
  --text-secondary: #6B7280;      /* slate */
  --text-muted: #9CA3AF;          /* ash */
  --success: #2E7D5B;             /* emerald */
  --warning: #C7882E;             /* bronze-warn */
  --danger: #9B2C2C;              /* burgundy */
  --info: #475569;                /* steel */
  --input: #E5E7EB;
  --ring: #D4AF37;
  --muted-foreground: #6B7280;

  /* ---- new canonical tokens (prefer these going forward) ---- */
  --paper: #FFFFFF;
  --ivory: #FAF9F6;
  --parchment: #F5F2E8;
  --charcoal: #1C1C1C;
  --slate: #6B7280;
  --ash: #9CA3AF;
  --hairline: #EDE7D3;
  --divider: #E5E7EB;
  --gold: #D4AF37;
  --gold-deep: #B8962E;
  --gold-ink: #8C6F1E;
  --gold-tint: #F5E7B5;
  --emerald: #2E7D5B;
  --bronze-warn: #C7882E;
  --burgundy: #9B2C2C;
  --steel: #475569;

  /* glass / scrollbar / selection / grid — retune for ivory canvas */
  --glass-bg: rgba(255, 255, 255, 0.7);
  --glass-border: rgba(28, 28, 28, 0.06);
  --scrollbar-thumb: #D1C7A0;
  --scrollbar-track: #FAF9F6;
  --selection-bg: rgba(212, 175, 55, 0.22);
  --selection-color: #1C1C1C;
  --grid-color: rgba(28, 28, 28, 0.035);
}

.dark {
  /* LEFT INTENTIONALLY UNTOUCHED — dark-mode harmonisation is out of scope.
     Users who explicitly toggle dark still see the original blue palette. */
  --background: #0a0e1a;
  --foreground: #e2e8f0;
  --surface: #111827;
  --surface-hover: #1e293b;
  --surface-active: #253449;
  --border: #1e293b;
  --border-subtle: #162032;
  --accent: #3b82f6;
  --accent-hover: #60a5fa;
  --accent-glow: rgba(59, 130, 246, 0.15);
  --text-primary: #f1f5f9;
  --text-secondary: #94a3b8;
  --text-muted: #64748b;
  --success: #10b981;
  --warning: #f59e0b;
  --danger: #ef4444;
  --info: #06b6d4;
  --input: #1e293b;
  --ring: #3b82f6;
  --muted-foreground: #94a3b8;
  --glass-bg: rgba(17, 24, 39, 0.7);
  --glass-border: rgba(255, 255, 255, 0.06);
  --scrollbar-thumb: #1e293b;
  --scrollbar-track: #0a0e1a;
  --selection-bg: rgba(59, 130, 246, 0.3);
  --selection-color: #f1f5f9;
  --grid-color: rgba(255, 255, 255, 0.02);
}

* {
  scrollbar-width: thin;
  scrollbar-color: var(--scrollbar-thumb) var(--scrollbar-track);
}

body {
  background: var(--background);
  color: var(--foreground);
}

.glass {
  background: var(--glass-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid var(--glass-border);
}

.glass-subtle {
  background: var(--glass-bg);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--glass-border);
}

.glow-accent {
  box-shadow: 0 4px 14px var(--accent-glow);
}

.gradient-text {
  background: linear-gradient(135deg, var(--gold) 0%, var(--gold-deep) 60%, var(--gold-ink) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.bg-grid {
  background-image:
    linear-gradient(var(--grid-color) 1px, transparent 1px),
    linear-gradient(90deg, var(--grid-color) 1px, transparent 1px);
  background-size: 40px 40px;
}

* {
  transition-property: background-color, border-color, color, opacity, box-shadow, transform;
  transition-duration: 120ms;
  transition-timing-function: cubic-bezier(.2, .7, .2, 1);
}

html, body, main, nav, aside {
  transition: none;
}

::selection {
  background: var(--selection-bg);
  color: var(--selection-color);
}

@media (prefers-reduced-motion: reduce) {
  * { transition: none !important; animation: none !important; }
}
```

- [ ] **Step 2: Run the dev server and eyeball the app**

```bash
cd frontend && pnpm dev
```
Open `http://localhost:3000`. Every page should now render on an ivory/white canvas with gold accents wherever `--accent` was used (buttons, links, badges). Sidebar active state, Header accent bar, various `text-accent` spots will all be gold. Expect visible quirks — we haven't touched components yet, just tokens. Verify there are no broken layouts from CSS-var renaming. Stop the dev server.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/globals.css
git commit -m "feat(frontend): swap design tokens to White & Gold palette

- Introduce canonical --gold/--ivory/--charcoal/--emerald/--burgundy tokens
- Repoint legacy --accent/--surface/--text-* aliases at the new palette so
  inherited pages keep rendering through the migration
- Retune scrollbar, selection, and grid to warm neutrals
- Change base transition easing to cubic-bezier(.2,.7,.2,1); respect
  prefers-reduced-motion
- .dark block intentionally unchanged (dark-mode repaint out of scope)"
```

---

### Task 1.4: Flip the theme default from `dark` to `light`

**Files:**
- Modify: `frontend/src/components/theme/ThemeProvider.tsx`

- [ ] **Step 1: Replace file contents with:**

```tsx
"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";
import type { ReactNode } from "react";

export function ThemeProvider({ children }: { children: ReactNode }) {
  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme="light"
      enableSystem
      disableTransitionOnChange
    >
      {children}
    </NextThemesProvider>
  );
}
```

- [ ] **Step 2: Verify by reloading the app** (dev server should hot-reload). Default view is the new White & Gold light UI. Toggle theme — dark still works, still uses the preserved blue palette.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/theme/ThemeProvider.tsx
git commit -m "feat(frontend): default theme from dark to light"
```

---

### Task 1.5: Tidy `(app)` layout — drop the grid + gradient overlay

**Files:**
- Modify: `frontend/src/app/(app)/layout.tsx`

The current layout stacks a `bg-grid` texture and an `accent-glow` gradient on every authenticated page. For the editorial-luxury identity we want a cleaner ivory canvas. Keep the sidebar/header/main shell.

- [ ] **Step 1: Replace file contents with:**

```tsx
import { Sidebar } from "@/components/common/Sidebar";
import { Header } from "@/components/common/Header";

export default function AppLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex h-full">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-y-auto bg-ivory">
          <div className="mx-auto max-w-[1400px] px-8 py-8">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Verify** the dev server: pages now sit on an ivory (`#FAF9F6`) canvas with consistent 32px page padding and a 1400px content cap. No texture, no glow.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/\(app\)/layout.tsx
git commit -m "refactor(frontend): simplify (app) layout — ivory canvas, centred content"
```

---

### Task 1.6: Rebuild `Button` primitive

**Files:**
- Modify: `frontend/src/components/ui/button.tsx`
- Grep + edit: any caller passing `variant="outline"` (renamed to `variant="ghost"`)

The existing primitive has variants `primary | secondary | danger | outline`. The new spec calls for `primary | secondary | ghost | danger`. `secondary` changes visually (gold outline instead of a surface fill). `outline` → `ghost` with different visuals (no chrome at rest). Callers that used `outline` must move to `ghost`; callers that used `secondary` **get a visual change for free** — audit them, but no code change needed.

- [ ] **Step 1: Replace `button.tsx` with:**

```tsx
import { ButtonHTMLAttributes, forwardRef } from "react";
import { cn } from "@/lib/utils/cn";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md" | "lg";
}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = "primary", size = "md", ...props }, ref) => {
    const base =
      "inline-flex items-center justify-center gap-2 font-semibold border border-transparent transition-all duration-200 " +
      "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gold focus-visible:ring-offset-2 focus-visible:ring-offset-paper " +
      "disabled:cursor-not-allowed disabled:bg-parchment disabled:text-ash disabled:border-hairline disabled:shadow-none disabled:translate-y-0";

    const variants: Record<NonNullable<ButtonProps["variant"]>, string> = {
      primary:
        "bg-gold text-paper hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.30)] hover:-translate-y-px",
      secondary:
        "bg-paper text-gold-deep border-gold hover:bg-ivory hover:text-gold-ink hover:border-gold-deep",
      ghost:
        "bg-transparent text-charcoal hover:bg-ivory",
      danger:
        "bg-burgundy text-paper hover:bg-[#7F2424]",
    };

    const sizes: Record<NonNullable<ButtonProps["size"]>, string> = {
      sm: "h-8 px-3 text-xs rounded-lg",
      md: "h-10 px-4 text-sm rounded-[10px]",
      lg: "h-12 px-5 text-sm rounded-xl",
    };

    return (
      <button
        ref={ref}
        className={cn(base, variants[variant], sizes[size], className)}
        {...props}
      />
    );
  }
);

Button.displayName = "Button";

export { Button };
```

- [ ] **Step 2: Find every `variant="outline"` caller and rename to `variant="ghost"`**

```bash
cd frontend && grep -rn 'variant="outline"' src/ | grep -i button
```

For each match, edit the file and change `"outline"` to `"ghost"`. Verify the fix with the same grep — expected: no results.

- [ ] **Step 3: TypeScript check**

```bash
cd frontend && pnpm tsc --noEmit
```
Expected: no errors (TypeScript will surface any remaining `outline` usages because the variant literal union no longer includes it).

- [ ] **Step 4: Visually spot-check** three pages that use buttons heavily: Projects list, Project detail, login. Confirm primary CTAs are solid gold, secondaries are gold-outlined, any former `outline` usages now read as ghost.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/ui/button.tsx $(cd frontend && grep -rl 'variant=.ghost.' src/ 2>/dev/null)
git commit -m "feat(ui): rebuild Button primitive for White & Gold system

- Variants: primary (gold fill) · secondary (gold outline) · ghost (no chrome)
  · danger (burgundy)
- Sizes: sm/md/lg at 32/40/48px
- Rename outline → ghost across callers; secondary restyled as gold outline
- Gold focus ring, gold glow on primary hover, prefers-reduced-motion safe"
```

---

### Task 1.7: Rebuild `Card` primitive

**Files:**
- Modify: `frontend/src/components/ui/card.tsx`

- [ ] **Step 1: Replace file contents with:**

```tsx
import React from "react";
import { cn } from "@/lib/utils/cn";

type Variant = "flat" | "elevated" | "interactive" | "accent";

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: Variant;
}

const variantClass: Record<Variant, string> = {
  flat: "border border-hairline",
  elevated: "border border-hairline shadow-[0_1px_2px_rgba(28,28,28,0.04)]",
  interactive:
    "border border-hairline cursor-pointer transition-all duration-200 " +
    "hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] hover:-translate-y-0.5 hover:border-[#F0E3A7]",
  accent: "border border-hairline border-l-[3px] border-l-gold",
};

export function Card({ className = "", variant = "flat", ...props }: CardProps) {
  return (
    <div
      className={cn(
        "rounded-xl bg-paper p-6",
        variantClass[variant],
        className
      )}
      {...props}
    />
  );
}

export function CardHeader({ className = "", ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("border-b border-hairline px-6 pb-4 -mx-6 -mt-2 mb-4", className)} {...props} />;
}

export function CardTitle({ className = "", ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3
      className={cn("font-display text-xl font-semibold text-charcoal tracking-tight", className)}
      {...props}
    />
  );
}

export function CardDescription({
  className = "",
  ...props
}: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn("mt-1 text-sm text-slate", className)} {...props} />;
}

export function CardContent({ className = "", ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("", className)} {...props} />;
}
```

- [ ] **Step 2: TypeScript + visual** check. `pnpm tsc --noEmit` clean; any page that uses `<Card>` still renders.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ui/card.tsx
git commit -m "feat(ui): rebuild Card primitive with flat/elevated/interactive/accent variants"
```

---

### Task 1.8: Rebuild `Dialog` primitive

**Files:**
- Modify: `frontend/src/components/ui/dialog.tsx`

- [ ] **Step 1: Replace file contents with:**

```tsx
"use client";

import React, { useState, useEffect } from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface DialogProps {
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  children: React.ReactNode;
}

interface DialogContextType {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

const DialogContext = React.createContext<DialogContextType | undefined>(undefined);

function useDialog() {
  const ctx = React.useContext(DialogContext);
  if (!ctx) throw new Error("Dialog components must be used within a Dialog");
  return ctx;
}

export function Dialog({ open = false, onOpenChange, children }: DialogProps) {
  const [isOpen, setIsOpen] = useState(open);

  useEffect(() => setIsOpen(open), [open]);

  const handleOpenChange = (next: boolean) => {
    setIsOpen(next);
    onOpenChange?.(next);
  };

  return (
    <DialogContext.Provider value={{ isOpen, onOpenChange: handleOpenChange }}>
      {children}
    </DialogContext.Provider>
  );
}

export type DialogContentProps = React.HTMLAttributes<HTMLDivElement>;

export function DialogContent({ className = "", children, ...props }: DialogContentProps) {
  const { isOpen, onOpenChange } = useDialog();
  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-charcoal/40 p-4"
      onClick={() => onOpenChange(false)}
    >
      <div
        className={cn(
          "relative w-full max-w-md rounded-2xl bg-paper shadow-[0_20px_40px_rgba(28,28,28,0.08)]",
          className
        )}
        onClick={(e) => e.stopPropagation()}
        {...props}
      >
        <button
          onClick={() => onOpenChange(false)}
          className="absolute right-4 top-4 rounded-md p-1 text-slate hover:text-gold-deep transition-colors"
          aria-label="Close"
        >
          <X size={18} />
        </button>
        {children}
      </div>
    </div>
  );
}

export function DialogHeader({ className = "", ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("px-6 pt-6", className)} {...props} />;
}

export function DialogTitle({
  className = "",
  ...props
}: React.HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h2
      className={cn(
        "font-display text-xl font-semibold text-charcoal tracking-tight",
        className
      )}
      {...props}
    />
  );
}

export function DialogBody({ className = "", ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("px-6 py-4 text-sm text-slate leading-relaxed", className)} {...props} />;
}

export function DialogFooter({ className = "", ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        "flex items-center justify-end gap-2 rounded-b-2xl border-t border-hairline bg-ivory px-6 py-4",
        className
      )}
      {...props}
    />
  );
}
```

- [ ] **Step 2: Verify existing callers still render.** The dialog primitive is imported in 1 file today; find it and confirm no typing breaks.

```bash
cd frontend && grep -rln '@/components/ui/dialog' src/
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ui/dialog.tsx
git commit -m "feat(ui): rebuild Dialog — Fraunces heading, ivory footer, backdrop click closes"
```

---

### Task 1.9: Rebuild `Input` primitive + add `Field` / `Label` / `FieldError` helpers

**Files:**
- Modify: `frontend/src/components/ui/input.tsx`

The spec requires labels-above-inputs everywhere. Today only `Input` is exported; add `Field`, `Label`, `FieldHint`, `FieldError` so every consumer gets a consistent form field.

- [ ] **Step 1: Replace file contents with:**

```tsx
import * as React from "react";
import { cn } from "@/lib/utils/cn";

export type InputProps = React.InputHTMLAttributes<HTMLInputElement> & {
  invalid?: boolean;
};

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, invalid, ...props }, ref) => (
    <input
      type={type}
      aria-invalid={invalid || undefined}
      ref={ref}
      className={cn(
        "flex h-10 w-full rounded-[10px] border bg-paper px-3.5 text-sm text-charcoal",
        "placeholder:text-ash",
        "transition-all duration-120",
        "hover:border-[#D1C7A0]",
        "focus-visible:outline-none focus-visible:border-gold focus-visible:shadow-[0_0_0_3px_rgba(212,175,55,0.18)]",
        "disabled:cursor-not-allowed disabled:bg-parchment disabled:text-ash",
        invalid
          ? "border-burgundy focus-visible:border-burgundy focus-visible:shadow-[0_0_0_3px_rgba(155,44,44,0.12)]"
          : "border-divider",
        className
      )}
      {...props}
    />
  )
);
Input.displayName = "Input";

export interface FieldProps extends React.HTMLAttributes<HTMLDivElement> {}

export function Field({ className, ...props }: FieldProps) {
  return <div className={cn("flex flex-col gap-1.5", className)} {...props} />;
}

export interface LabelProps extends React.LabelHTMLAttributes<HTMLLabelElement> {}

export function Label({ className, ...props }: LabelProps) {
  return (
    <label
      className={cn("text-xs font-semibold text-charcoal", className)}
      {...props}
    />
  );
}

export function FieldHint({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn("text-xs text-slate", className)} {...props} />;
}

export function FieldError({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return (
    <p
      className={cn("flex items-center gap-1 text-xs text-burgundy", className)}
      {...props}
    />
  );
}

export { Input };
```

- [ ] **Step 2: `pnpm tsc --noEmit`** — should pass because we only *added* exports. Existing consumers using `<Input>` directly keep working.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ui/input.tsx
git commit -m "feat(ui): rebuild Input; add Field/Label/FieldHint/FieldError helpers

Gold focus ring (1px border + 3px tinted glow), burgundy error state,
parchment disabled bg. Label-above pattern available as first-class
primitives so form screens stop hand-rolling labels."
```

---

### Task 1.10: Rebuild `Badge` primitive (six variants)

**Files:**
- Modify: `frontend/src/components/ui/badge.tsx`

The badge currently has `default | secondary | destructive | outline`. Redefine as `neutral | gold | success | warning | danger | info` per spec §6.5. Badge has 0 direct imports today, so no callers break — but inline `StatusBadge` components elsewhere (notably `projects/page.tsx`) will be replaced with this in Phase 2.

- [ ] **Step 1: Replace file contents with:**

```tsx
import React from "react";
import { cn } from "@/lib/utils/cn";

export type BadgeVariant =
  | "neutral"
  | "gold"
  | "success"
  | "warning"
  | "danger"
  | "info";

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
  withDot?: boolean;
}

const variants: Record<BadgeVariant, string> = {
  neutral: "bg-ivory text-charcoal border-hairline",
  gold: "bg-gold-tint text-gold-ink border-[#E8D68A]",
  success: "bg-[#E5F1EB] text-emerald border-[#C8E0D3]",
  warning: "bg-[#FBEDD5] text-[#8B5E14] border-[#F0DDAE]",
  danger: "bg-[#F5E2E2] text-burgundy border-[#E5C4C4]",
  info: "bg-[#E8ECF1] text-steel border-[#CFD6DF]",
};

export function Badge({
  variant = "neutral",
  withDot = false,
  className,
  children,
  ...props
}: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-md border px-2.5 py-0.5 text-[11px] font-semibold tracking-wide",
        variants[variant],
        className
      )}
      {...props}
    >
      {withDot && (
        <span
          className="h-1.5 w-1.5 rounded-full"
          style={{ background: "currentColor" }}
          aria-hidden
        />
      )}
      {children}
    </span>
  );
}
```

- [ ] **Step 2: `pnpm tsc --noEmit`.** Expected: no errors (no callers yet).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ui/badge.tsx
git commit -m "feat(ui): rebuild Badge — 6 variants (neutral/gold/success/warning/danger/info)"
```

---

### Task 1.11: Rebuild `Progress` primitive

**Files:**
- Modify: `frontend/src/components/ui/progress.tsx`

- [ ] **Step 1: Replace file contents with:**

```tsx
import React from "react";
import { cn } from "@/lib/utils/cn";

export type ProgressVariant = "gold" | "success" | "warning" | "danger";

export interface ProgressProps extends React.HTMLAttributes<HTMLDivElement> {
  value?: number;
  max?: number;
  variant?: ProgressVariant;
  label?: string;
}

const fills: Record<ProgressVariant, string> = {
  gold: "linear-gradient(90deg,#D4AF37,#B8962E)",
  success: "linear-gradient(90deg,#2E7D5B,#256B4C)",
  warning: "linear-gradient(90deg,#C7882E,#A6701F)",
  danger: "linear-gradient(90deg,#9B2C2C,#7F2424)",
};

export function Progress({
  value = 0,
  max = 100,
  variant = "gold",
  label,
  className,
  ...props
}: ProgressProps) {
  const pct = Math.min(100, Math.max(0, (value / max) * 100));

  return (
    <div className={cn("flex flex-col gap-1.5", className)} {...props}>
      {label && (
        <div className="flex justify-between text-xs">
          <span className="text-slate">{label}</span>
          <span className="font-semibold text-charcoal font-mono tabular-nums">
            {Math.round(pct)}%
          </span>
        </div>
      )}
      <div
        className="h-1.5 w-full overflow-hidden rounded-full bg-parchment"
        role="progressbar"
        aria-valuenow={Math.round(pct)}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          className="h-full rounded-full transition-[width] duration-300 ease-[cubic-bezier(.2,.7,.2,1)]"
          style={{ width: `${pct}%`, background: fills[variant] }}
        />
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Check callers.** The progress primitive is imported in 2 files. Both continue to work — `value` and `max` props are preserved; `variant` and `label` are new and optional.

```bash
cd frontend && grep -rln '@/components/ui/progress' src/
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ui/progress.tsx
git commit -m "feat(ui): rebuild Progress — 6px track, 4 semantic gradient fills, optional label row"
```

---

### Task 1.12: Rebuild `Table` primitive

**Files:**
- Modify: `frontend/src/components/ui/table.tsx`

- [ ] **Step 1: Replace file contents with:**

```tsx
import React from "react";
import { cn } from "@/lib/utils/cn";

export function Table({ className, ...props }: React.HTMLAttributes<HTMLTableElement>) {
  return (
    <div className="overflow-hidden rounded-xl border border-hairline bg-paper">
      <table className={cn("w-full border-collapse text-sm", className)} {...props} />
    </div>
  );
}

export function TableHeader({
  className,
  ...props
}: React.HTMLAttributes<HTMLTableSectionElement>) {
  return (
    <thead
      className={cn("bg-ivory border-b border-hairline", className)}
      {...props}
    />
  );
}

export function TableBody({
  className,
  ...props
}: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={cn("", className)} {...props} />;
}

export function TableRow({
  className,
  ...props
}: React.HTMLAttributes<HTMLTableRowElement>) {
  return (
    <tr
      className={cn(
        "border-b border-[#F4EDD8] last:border-b-0 transition-colors duration-120",
        "hover:bg-ivory",
        className
      )}
      {...props}
    />
  );
}

export function TableHead({
  className,
  ...props
}: React.HTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      className={cn(
        "px-4 py-3 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep",
        className
      )}
      {...props}
    />
  );
}

export function TableCell({
  className,
  ...props
}: React.HTMLAttributes<HTMLTableCellElement>) {
  return (
    <td
      className={cn("px-4 py-3.5 align-middle text-charcoal", className)}
      {...props}
    />
  );
}
```

- [ ] **Step 2: Verify callers.** The primitive is imported in 1 file. Still works — same API.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/ui/table.tsx
git commit -m "feat(ui): rebuild Table — ivory header, gold-bronze tracked labels, hairline rows"
```

---

### Task 1.13: Rebuild `Sidebar`

**Files:**
- Modify: `frontend/src/components/common/Sidebar.tsx`

This replaces the 21-item flat list with four named groups (`Plan`, `Execute`, `Control`, `Admin`) plus a brand block with workspace picker at top and a user chip at bottom. Collapsed state keeps the gold rail.

- [ ] **Step 1: Replace file contents with:**

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { LucideIcon } from "lucide-react";
import {
  BarChart3, Briefcase, Building2, Calendar, ChevronDown, ChevronLeft,
  ChevronRight, FileText, FolderTree, Gauge, LayoutDashboard, Layers,
  LogOut, Network, Plug, Settings, Shield, SlidersHorizontal, Sparkles,
  UserCog, Users,
} from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { useAppStore, useAuthStore } from "@/lib/state/store";
import { useRouter } from "next/navigation";

type NavItem = { name: string; href: string; icon: LucideIcon; count?: number };
type NavGroup = { label: string; items: NavItem[] };

const groups: NavGroup[] = [
  {
    label: "Plan",
    items: [
      { name: "Dashboard", href: "/", icon: LayoutDashboard },
      { name: "Portfolios", href: "/portfolios", icon: Briefcase },
      { name: "Projects", href: "/projects", icon: FolderTree },
      { name: "EPS", href: "/eps", icon: Layers },
      { name: "Dashboards", href: "/dashboards", icon: LayoutDashboard },
    ],
  },
  {
    label: "Execute",
    items: [
      { name: "Resources", href: "/resources", icon: Users },
      { name: "Calendars", href: "/admin/calendars", icon: Calendar },
    ],
  },
  {
    label: "Control",
    items: [
      { name: "Reports", href: "/reports", icon: BarChart3 },
      { name: "Risk", href: "/risk", icon: Shield },
      { name: "OBS", href: "/obs", icon: Network },
      { name: "Analytics", href: "/analytics", icon: Sparkles },
    ],
  },
  {
    label: "Admin",
    items: [
      { name: "Users", href: "/admin/users", icon: Users },
      { name: "Organisations", href: "/admin/organisations", icon: Building2 },
      { name: "User Access", href: "/admin/user-access", icon: UserCog },
      { name: "WBS Templates", href: "/admin/wbs-templates", icon: FileText },
      { name: "Productivity Norms", href: "/admin/productivity-norms", icon: Gauge },
      { name: "Unit Rate Master", href: "/admin/unit-rate-master", icon: Gauge },
      { name: "Resource Roles", href: "/admin/resource-roles", icon: Users },
      { name: "Integrations", href: "/admin/integrations", icon: Plug },
      { name: "User Defined Fields", href: "/admin/udf", icon: SlidersHorizontal },
      { name: "Settings", href: "/admin/settings", icon: Settings },
    ],
  },
];

function isActive(pathname: string, href: string) {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(href + "/");
}

export function Sidebar() {
  const pathname = usePathname();
  const { sidebarCollapsed, toggleSidebar } = useAppStore();
  const { user, clearAuth } = useAuthStore();
  const router = useRouter();

  const handleLogout = () => {
    document.cookie = "access_token=; path=/; max-age=0";
    clearAuth();
    router.push("/auth/login");
  };

  const initials = (user?.firstName ?? user?.username ?? "U")
    .slice(0, 2)
    .toUpperCase();

  return (
    <aside
      className={cn(
        "flex flex-col bg-paper border-r border-hairline transition-[width] duration-200",
        sidebarCollapsed ? "w-16" : "w-[260px]"
      )}
    >
      {/* Brand + collapse */}
      <div className="flex items-center justify-between border-b border-[#F4EDD8] px-4 py-5">
        {!sidebarCollapsed && (
          <div className="flex items-center gap-2.5">
            <div
              className="flex h-8 w-8 items-center justify-center rounded-lg text-paper font-display font-bold text-base"
              style={{ background: "linear-gradient(135deg,#D4AF37,#B8962E 60%,#8C6F1E)" }}
            >
              B
            </div>
            <div className="flex flex-col leading-none">
              <span className="font-display font-semibold text-lg text-charcoal tracking-tight">
                Bipros
              </span>
              <span className="text-[9px] font-semibold uppercase tracking-[0.2em] text-gold-deep mt-0.5">
                EPPM
              </span>
            </div>
          </div>
        )}
        {sidebarCollapsed && (
          <div
            className="mx-auto flex h-8 w-8 items-center justify-center rounded-lg text-paper font-display font-bold"
            style={{ background: "linear-gradient(135deg,#D4AF37,#B8962E 60%,#8C6F1E)" }}
          >
            B
          </div>
        )}
        {!sidebarCollapsed && (
          <button
            onClick={toggleSidebar}
            aria-label="Collapse sidebar"
            className="rounded-md p-1 text-slate hover:bg-ivory hover:text-gold-deep"
          >
            <ChevronLeft size={16} />
          </button>
        )}
      </div>

      {sidebarCollapsed && (
        <button
          onClick={toggleSidebar}
          aria-label="Expand sidebar"
          className="mx-auto my-2 rounded-md p-1 text-slate hover:bg-ivory hover:text-gold-deep"
        >
          <ChevronRight size={16} />
        </button>
      )}

      {/* Workspace picker */}
      {!sidebarCollapsed && (
        <div className="px-4 pt-4 pb-2">
          <div className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.12em] text-slate">
            Workspace
          </div>
          <button
            type="button"
            className="flex w-full items-center justify-between rounded-[10px] border border-hairline bg-ivory px-3 py-2.5 text-sm font-semibold text-charcoal hover:bg-paper"
          >
            <span className="truncate">{user?.username ?? "Acme Infrastructure"}</span>
            <ChevronDown size={14} className="text-ash" />
          </button>
        </div>
      )}

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto px-3 pb-3 pt-2">
        {groups.map((group) => (
          <div key={group.label} className="mb-1">
            {!sidebarCollapsed && (
              <div className="px-2.5 pt-4 pb-1.5 text-[9px] font-semibold uppercase tracking-[0.14em] text-ash">
                {group.label}
              </div>
            )}
            {group.items.map((item) => {
              const active = isActive(pathname, item.href);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  title={sidebarCollapsed ? item.name : undefined}
                  className={cn(
                    "relative flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-[13px] font-medium transition-colors",
                    active
                      ? "text-charcoal font-semibold bg-[linear-gradient(90deg,rgba(212,175,55,0.09),rgba(212,175,55,0)_90%)]"
                      : "text-charcoal hover:bg-ivory",
                    sidebarCollapsed && "justify-center"
                  )}
                >
                  {active && (
                    <span
                      aria-hidden
                      className="absolute left-[-13px] top-1.5 bottom-1.5 w-[3px] rounded-r-[3px] bg-gold"
                    />
                  )}
                  <item.icon
                    size={16}
                    className={cn("shrink-0", active ? "text-gold-deep" : "text-slate")}
                    strokeWidth={1.5}
                  />
                  {!sidebarCollapsed && <span className="truncate">{item.name}</span>}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      {/* User chip */}
      {!sidebarCollapsed && (
        <div className="border-t border-[#F4EDD8] p-3">
          <div className="flex items-center gap-2.5 rounded-[10px] px-2.5 py-2 hover:bg-ivory">
            <div
              className="flex h-8 w-8 items-center justify-center rounded-full bg-parchment text-gold-deep font-display font-semibold text-xs"
              style={{ border: "2px solid #D4AF37" }}
            >
              {initials}
            </div>
            <div className="min-w-0 flex-1 leading-tight">
              <div className="truncate text-[13px] font-semibold text-charcoal">
                {user?.firstName ?? user?.username ?? "User"}
              </div>
              <div className="truncate text-[10px] font-semibold uppercase tracking-[0.08em] text-gold-deep">
                {user?.role ?? "Programme lead"}
              </div>
            </div>
            <button
              onClick={handleLogout}
              aria-label="Sign out"
              className="rounded-md p-1.5 text-slate hover:bg-paper hover:text-burgundy"
            >
              <LogOut size={14} />
            </button>
          </div>
        </div>
      )}
    </aside>
  );
}
```

- [ ] **Step 2: Check for `user.role`**

The `useAuthStore` user shape may not include `role`. Inspect:

```bash
cd frontend && grep -rn 'interface.*User\|type User\b' src/lib/ src/types/ 2>/dev/null | head
```

If `role` isn't on the User type, remove the `{user?.role ?? "Programme lead"}` line and replace with the static string `"Programme lead"`. Do not expand the auth type just to support a sidebar label.

- [ ] **Step 3: `pnpm tsc --noEmit`.** Expected: no errors.

- [ ] **Step 4: Visual** — reload the app. Sidebar now has grouped nav, gold rail on the active item, workspace picker at top, user chip at bottom. Collapse button toggles 260 ↔ 64px.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/common/Sidebar.tsx
git commit -m "feat(shell): redesign Sidebar — grouped nav, gold rail, workspace + user chip

- Four groups (Plan / Execute / Control / Admin) across the 21 existing routes
- Fraunces wordmark + gold-gradient brand mark, EPPM kicker
- Active state = 3px gold left rail + faint gold gradient wash, gold icon
- Workspace picker, collapsible 260 ↔ 64 rail, footer user chip with logout"
```

---

### Task 1.14: Rebuild `Header`

**Files:**
- Modify: `frontend/src/components/common/Header.tsx`

The new header adds breadcrumbs, a placeholder `⌘K` search pill, a notification bell, and a primary "+ New project" CTA. The existing user dropdown + logout live on the sidebar now, so they're removed from the header.

- [ ] **Step 1: Replace file contents with:**

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Bell, HelpCircle, Plus, Search } from "lucide-react";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { cn } from "@/lib/utils/cn";

function humanise(segment: string) {
  const lookup: Record<string, string> = {
    admin: "Admin",
    udf: "User Defined Fields",
    obs: "OBS",
    eps: "EPS",
  };
  if (lookup[segment]) return lookup[segment];
  return segment.replace(/-/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

function useBreadcrumbs(pathname: string) {
  if (pathname === "/") return [{ label: "Dashboard", href: "/" }];
  const parts = pathname.split("/").filter(Boolean);
  const crumbs = [{ label: "Dashboard", href: "/" }];
  let href = "";
  for (const part of parts) {
    href += "/" + part;
    crumbs.push({ label: humanise(part), href });
  }
  return crumbs;
}

export function Header() {
  const pathname = usePathname();
  const crumbs = useBreadcrumbs(pathname);

  return (
    <header className="relative flex h-16 items-center gap-5 border-b border-hairline bg-paper px-7">
      {/* gold gradient hairline under header */}
      <div
        aria-hidden
        className="absolute inset-x-0 -bottom-px h-px"
        style={{
          background:
            "linear-gradient(90deg, transparent, #D4AF37 20%, #D4AF37 80%, transparent)",
          opacity: 0.4,
        }}
      />

      {/* Breadcrumbs */}
      <nav aria-label="Breadcrumbs" className="flex items-center gap-2 text-[13px]">
        {crumbs.map((c, i) => {
          const last = i === crumbs.length - 1;
          return (
            <span key={c.href} className="flex items-center gap-2">
              {last ? (
                <span className="font-semibold text-charcoal truncate max-w-[280px]">
                  {c.label}
                </span>
              ) : (
                <Link
                  href={c.href}
                  className="text-slate hover:text-gold-deep transition-colors truncate max-w-[160px]"
                >
                  {c.label}
                </Link>
              )}
              {!last && <span className="text-[#D1C7A0]" aria-hidden>›</span>}
            </span>
          );
        })}
      </nav>

      {/* Command-palette search */}
      <button
        type="button"
        className={cn(
          "ml-4 flex h-10 max-w-[440px] flex-1 items-center gap-2.5 rounded-[10px] border border-hairline bg-ivory px-3.5",
          "text-[13px] text-slate hover:border-[#D1C7A0] transition-colors"
        )}
        title="Search (⌘K)"
      >
        <Search size={15} className="text-ash" strokeWidth={1.5} />
        <span className="flex-1 text-left">Search projects, activities, resources…</span>
        <kbd className="rounded border border-hairline bg-paper px-1.5 py-0.5 font-mono text-[10px] text-slate">
          ⌘K
        </kbd>
      </button>

      {/* Right cluster */}
      <div className="flex items-center gap-2.5">
        <button
          type="button"
          aria-label="Notifications"
          className="relative flex h-10 w-10 items-center justify-center rounded-[10px] border border-transparent text-slate transition-colors hover:border-hairline hover:bg-ivory hover:text-gold-deep"
        >
          <Bell size={17} strokeWidth={1.5} />
          <span
            aria-hidden
            className="absolute right-2 top-2 h-[7px] w-[7px] rounded-full bg-gold ring-2 ring-paper"
          />
        </button>
        <button
          type="button"
          aria-label="Help"
          className="flex h-10 w-10 items-center justify-center rounded-[10px] border border-transparent text-slate transition-colors hover:border-hairline hover:bg-ivory hover:text-gold-deep"
        >
          <HelpCircle size={17} strokeWidth={1.5} />
        </button>
        <div className="h-5 w-px bg-hairline" />
        <ThemeToggle />
        <Link
          href="/projects/new"
          className="inline-flex h-10 items-center gap-1.5 rounded-[10px] bg-gold px-3.5 text-[13px] font-semibold text-paper transition-all duration-200 hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.3)] hover:-translate-y-px"
        >
          <Plus size={14} strokeWidth={2.5} />
          New project
        </Link>
      </div>
    </header>
  );
}
```

- [ ] **Step 2: Verify** that `ThemeToggle` is still exported from `@/components/theme/ThemeToggle`. It is; we're preserving it.

- [ ] **Step 3: `pnpm tsc --noEmit`** → clean. Dev server reload → breadcrumbs reflect the current route, search pill renders, bell + help icons + gold CTA on the right.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/common/Header.tsx
git commit -m "feat(shell): redesign Header — breadcrumbs, ⌘K search, gold CTA

- Auto-generated breadcrumbs from the current pathname; last crumb bold
- Command-palette placeholder search pill with ⌘K hint
- Notification bell with gold unread dot, help icon, ThemeToggle preserved
- Primary '+ New project' CTA replaces the old user/logout cluster
  (user actions now live on the sidebar footer chip)
- Gold-gradient hairline under the entire header"
```

---

### Task 1.15: Phase 1 verification — e2e + visual walk-through

**Files:** none (verification only)

- [ ] **Step 1: Lint**

```bash
cd frontend && pnpm lint
```
Expected: no errors (warnings tolerated). If errors land, fix them before continuing.

- [ ] **Step 2: Full Playwright suite**

Backend must be running (`(cd backend && mvn spring-boot:run -pl bipros-api)` in a separate terminal). Then:

```bash
cd frontend && pnpm test:e2e
```
Expected: all tests pass. The suite asserts on roles/text, not colours — a failure now indicates a selector we broke, not a visual regression. Fix selector issues in the tests or the components, not by disabling tests.

- [ ] **Step 3: Manual visual walk-through**

Start dev server. Walk these pages:
- `http://localhost:3000/` (Dashboard — inherited look for now)
- `/projects`
- `/portfolios`
- `/resources`
- `/admin/users`
- `/auth/login`

For each: sidebar, header, and primitives (buttons, inputs on forms, badges on list pages) should all render in the new palette. Content inside pages may still look old (rebuilt in Phases 2/3). Inherited-page oddities (inline blue hexes) are expected — log them for the follow-up sweep, don't fix them here.

- [ ] **Step 4: Commit any lint/test fixes as isolated commits.** Phase 1 is complete once the checklist above is green.

---

## Phase 2 — Hero pages: Dashboard + Projects list (PR 2)

### Task 2.1: Rebuild `TabTip` (33 consumers — API preserved)

**Files:**
- Modify: `frontend/src/components/common/TabTip.tsx`

The component is imported in 33 files. Do **not** change the prop signature: `title`, `description`, optional `steps`. Only the visual rewrites.

- [ ] **Step 1: Replace file contents with:**

```tsx
import { Info } from "lucide-react";

interface TabTipProps {
  title: string;
  description: string;
  steps?: string[];
}

export function TabTip({ title, description, steps }: TabTipProps) {
  return (
    <div className="mb-6 rounded-xl border border-hairline bg-paper p-5 border-l-[3px] border-l-gold">
      <div className="flex items-start gap-3">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gold-tint text-gold-deep">
          <Info size={16} strokeWidth={1.5} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-1">
            Tip · {title}
          </div>
          <p className="text-sm text-slate leading-relaxed">{description}</p>
          {steps && steps.length > 0 && (
            <ol className="mt-2.5 space-y-1 pl-4 text-sm text-slate list-decimal marker:text-gold-deep marker:font-mono marker:text-xs">
              {steps.map((step, i) => (
                <li key={i}>{step}</li>
              ))}
            </ol>
          )}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: `pnpm tsc --noEmit`** → clean. Spot-check any 3 pages that use TabTip (grep for consumers, open two list pages and one dashboard).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/common/TabTip.tsx
git commit -m "feat(shell): restyle TabTip — gold side-rail + ivory icon chip, preserved API"
```

---

### Task 2.2: Rebuild Dashboard page

**Files:**
- Modify: `frontend/src/app/(app)/page.tsx`

Preserve: the `fetchMetrics` function signature and the `useQuery` wiring (60s stale time, queryKey `["dashboard-metrics"]`). The data types (`MetricsData`, `ProjectData`, `ActivityData`) stay. Replace the visual layer entirely.

- [ ] **Step 1: Replace file contents with:**

```tsx
"use client";

import {
  AlertCircle,
  BarChart3,
  Calendar,
  Clock,
  FolderTree,
  Plus,
  TrendingUp,
  Users,
} from "lucide-react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { apiClient } from "@/lib/api/client";
import { TabTip } from "@/components/common/TabTip";
import { Badge } from "@/components/ui/badge";

interface ProjectData {
  id: string;
  name: string;
  status: string;
  createdAt: string;
}
interface ActivityData {
  totalActivities: number;
  criticalActivities: number;
  overdueCount: number;
}
interface MetricsData {
  plannedCount: number;
  activeCount: number;
  completedCount: number;
  resourceCount: number;
  recentProjects: ProjectData[];
  activities: ActivityData;
}

async function fetchMetrics(): Promise<MetricsData> {
  try {
    const projectsResponse = await apiClient.get("/v1/projects?page=0&size=100");
    const projects: ProjectData[] = projectsResponse.data.data?.content || [];
    const plannedCount = projects.filter((p) => p.status === "PLANNED").length;
    const activeCount = projects.filter((p) => p.status === "ACTIVE").length;
    const completedCount = projects.filter((p) => p.status === "COMPLETED").length;
    const recentProjects = [...projects]
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 5);

    const resourcesResponse = await apiClient.get("/v1/resources");
    const resourcesList = resourcesResponse.data.data || [];
    const resourceCount = Array.isArray(resourcesList) ? resourcesList.length : 0;

    let totalActivities = 0;
    let criticalActivities = 0;
    for (const proj of projects.slice(0, 10)) {
      try {
        const actResp = await apiClient.get(`/v1/projects/${proj.id}/activities?page=0&size=500`);
        const actData = actResp.data.data;
        totalActivities += actData?.pagination?.totalElements || 0;
        const list: Array<{ isCritical?: boolean; totalFloat?: number }> = actData?.content || [];
        criticalActivities += list.filter((a) => a.isCritical === true || a.totalFloat === 0).length;
      } catch { /* skip */ }
    }

    return {
      plannedCount, activeCount, completedCount, resourceCount, recentProjects,
      activities: { totalActivities, criticalActivities, overdueCount: 0 },
    };
  } catch (error) {
    console.error("Failed to fetch metrics:", error);
    return {
      plannedCount: 0, activeCount: 0, completedCount: 0, resourceCount: 0,
      recentProjects: [], activities: { totalActivities: 0, criticalActivities: 0, overdueCount: 0 },
    };
  }
}

function Kpi({
  label, value, tone = "default", delta,
}: {
  label: string;
  value: number | string;
  tone?: "default" | "warning" | "critical";
  delta?: string;
}) {
  const rail =
    tone === "critical"
      ? "border-l-[3px] border-l-burgundy"
      : tone === "warning"
        ? "border-l-[3px] border-l-[#C7882E]"
        : "";
  const kickerColor =
    tone === "critical" ? "text-burgundy" : tone === "warning" ? "text-[#8B5E14]" : "text-gold-deep";

  return (
    <div
      className={`rounded-xl border border-hairline bg-paper p-5 transition-all duration-200 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] hover:-translate-y-0.5 ${rail}`}
    >
      <div className={`text-[10px] font-semibold uppercase tracking-[0.14em] ${kickerColor} mb-2`}>
        {label}
      </div>
      <div className="font-display text-[38px] font-semibold leading-none tracking-tight text-charcoal"
           style={{ fontVariationSettings: "'opsz' 144" }}>
        {value}
      </div>
      {delta && <div className="mt-2 text-xs text-slate">{delta}</div>}
    </div>
  );
}

function statusVariant(status: string) {
  switch (status) {
    case "ACTIVE": return "success" as const;
    case "PLANNED": return "gold" as const;
    case "COMPLETED": return "info" as const;
    case "INACTIVE": return "warning" as const;
    default: return "neutral" as const;
  }
}

const quickActions = [
  { title: "Projects", href: "/projects", icon: FolderTree, blurb: "Portfolio, baselines, programme hierarchy." },
  { title: "Resources", href: "/resources", icon: Users, blurb: "People, equipment, rate cards, allocations." },
  { title: "Calendars", href: "/admin/calendars", icon: Calendar, blurb: "Working days, holidays, shift patterns." },
  { title: "Reports", href: "/reports", icon: BarChart3, blurb: "Earned-value, variance, executive summaries." },
];

export default function DashboardPage() {
  const [isClient, setIsClient] = useState(false);
  const { data: metrics, isLoading } = useQuery<MetricsData>({
    queryKey: ["dashboard-metrics"],
    queryFn: fetchMetrics,
    staleTime: 60_000,
  });

  useEffect(() => setIsClient(true), []);
  if (!isClient) return null;

  const today = new Date().toLocaleDateString("en-US", {
    year: "numeric", month: "long", day: "numeric",
  });

  return (
    <div>
      {/* Page head */}
      <div className="mb-8 flex items-start justify-between gap-6">
        <div>
          <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-1.5">
            Today · {today}
          </div>
          <h1 className="font-display text-[38px] font-semibold leading-[1.08] tracking-tight text-charcoal"
              style={{ fontVariationSettings: "'opsz' 144" }}>
            Portfolio dashboard
          </h1>
          <p className="mt-2 max-w-[600px] text-sm text-slate leading-relaxed">
            Programme health across your active portfolio.
          </p>
        </div>
        <Link
          href="/projects/new"
          className="inline-flex h-10 items-center gap-1.5 rounded-[10px] bg-gold px-4 text-sm font-semibold text-paper transition-all duration-200 hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.3)] hover:-translate-y-px"
        >
          <Plus size={14} strokeWidth={2.5} />
          New project
        </Link>
      </div>

      <TabTip
        title="Dashboard"
        description="Your command centre. See project counts by status, total activities, resource utilisation, and recent projects at a glance. Click any card to drill in."
      />

      {/* Status KPIs */}
      {isLoading ? (
        <div className="mb-7 grid grid-cols-1 gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-28 animate-pulse rounded-xl bg-parchment" />
          ))}
        </div>
      ) : (
        <div className="mb-7 grid grid-cols-1 gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
          <Kpi label="Planned" value={metrics?.plannedCount ?? 0} />
          <Kpi label="Active" value={metrics?.activeCount ?? 0} />
          <Kpi label="Completed" value={metrics?.completedCount ?? 0} />
          <Kpi label="Resources" value={metrics?.resourceCount ?? 0} />
        </div>
      )}

      {/* Activity KPIs */}
      {!isLoading && (
        <div className="mb-8 grid grid-cols-1 gap-3.5 sm:grid-cols-3">
          <Kpi label="Total activities" value={metrics?.activities.totalActivities ?? 0} />
          <Kpi label="Critical path" value={metrics?.activities.criticalActivities ?? 0} tone="warning" />
          <Kpi label="Overdue" value={metrics?.activities.overdueCount ?? 0} tone="critical" />
        </div>
      )}

      {/* Recent projects table */}
      <div className="mb-8">
        <div className="mb-3.5 flex items-baseline justify-between">
          <h2 className="font-display text-xl font-semibold tracking-tight text-charcoal">
            Recent projects
          </h2>
          <Link href="/projects" className="text-xs font-semibold text-gold-deep hover:text-gold-ink">
            View all projects →
          </Link>
        </div>
        <div className="overflow-hidden rounded-xl border border-hairline bg-paper">
          <table className="w-full border-collapse text-sm">
            <thead className="border-b border-hairline bg-ivory">
              <tr>
                <th className="px-4 py-3 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Project</th>
                <th className="px-4 py-3 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Status</th>
                <th className="px-4 py-3 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Created</th>
              </tr>
            </thead>
            <tbody>
              {metrics?.recentProjects && metrics.recentProjects.length > 0 ? (
                metrics.recentProjects.map((p) => (
                  <tr key={p.id} className="border-b border-[#F4EDD8] transition-colors last:border-b-0 hover:bg-ivory">
                    <td className="px-4 py-3.5">
                      <Link href={`/projects/${p.id}`} className="font-semibold text-charcoal hover:text-gold-deep">
                        {p.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3.5">
                      <Badge variant={statusVariant(p.status)} withDot>{p.status}</Badge>
                    </td>
                    <td className="px-4 py-3.5 text-slate">
                      {new Date(p.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={3} className="px-4 py-6 text-center text-sm text-slate">
                    No recent projects
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Quick actions */}
      <div>
        <h2 className="mb-3.5 font-display text-xl font-semibold tracking-tight text-charcoal">
          Jump back in
        </h2>
        <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
          {quickActions.map((card) => (
            <Link
              key={card.title}
              href={card.href}
              className="group relative rounded-xl border border-hairline bg-paper p-5 transition-all duration-200 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] hover:-translate-y-0.5 hover:border-[#F0E3A7]"
            >
              <div className="mb-3.5 flex h-9 w-9 items-center justify-center rounded-[10px] text-gold-deep"
                   style={{ background: "linear-gradient(135deg,#FDF6DD,#F5E7B5)" }}>
                <card.icon size={18} strokeWidth={1.5} />
              </div>
              <div className="font-display text-lg font-semibold tracking-tight text-charcoal">
                {card.title}
              </div>
              <p className="mt-1 text-xs leading-relaxed text-slate">{card.blurb}</p>
              <span
                aria-hidden
                className="absolute right-5 top-5 text-sm text-gold opacity-40 transition-all duration-200 group-hover:opacity-100 group-hover:translate-x-0.5"
              >
                →
              </span>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Verify** `pnpm tsc --noEmit` clean. Reload the app, open `/`. Confirm header → KPIs → activity KPIs → recent-projects table → jump-back-in tiles render per the mockup. Clicking a project name still routes to `/projects/<id>`.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/\(app\)/page.tsx
git commit -m "feat(dashboard): rebuild Dashboard in White & Gold identity

- Dated kicker + Fraunces h1 + right-aligned primary CTA
- 4 status KPIs + 3 activity KPIs (Critical warning-rail, Overdue burgundy-rail)
- Recent projects table using shared Badge with withDot for status
- Jump-back-in tiles with gold-gradient icon badges + ghost-arrow hover
- Data layer unchanged — fetchMetrics + useQuery 60s stale preserved"
```

---

### Task 2.3: Rebuild Projects list page; replace inline `StatusBadge` with shared `Badge`

**Files:**
- Modify: `frontend/src/app/(app)/projects/page.tsx`

Preserve: the `useQuery` wiring, `useMutation` delete flow, client-side `useMemo` filter. Add a priority filter (client-side, over the existing `priority` field). Remove the inline `StatusBadge` and use the shared `Badge`.

- [ ] **Step 1: Verify the `getPriorityInfo` util** at `@/lib/utils/format` still returns `{ label, color }` — a quick read confirms it before we reuse it.

```bash
cd frontend && grep -n 'getPriorityInfo' src/lib/utils/format.ts | head
```

- [ ] **Step 2: Replace `projects/page.tsx` contents with:**

```tsx
"use client";

import { useState, useMemo } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search, Trash2, Edit2, Download } from "lucide-react";
import Link from "next/link";
import { projectApi } from "@/lib/api/projectApi";
import { formatDate, getPriorityInfo } from "@/lib/utils/format";
import { Badge } from "@/components/ui/badge";

const STATUS_OPTIONS = ["All", "PLANNED", "ACTIVE", "INACTIVE", "COMPLETED"] as const;
const PRIORITY_OPTIONS = ["All", "CRITICAL", "HIGH", "MEDIUM", "LOW"] as const;

function statusVariant(status: string) {
  switch (status) {
    case "ACTIVE": return "success" as const;
    case "PLANNED": return "gold" as const;
    case "COMPLETED": return "info" as const;
    case "INACTIVE": return "warning" as const;
    default: return "neutral" as const;
  }
}

export default function ProjectsPage() {
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("All");
  const [priorityFilter, setPriorityFilter] = useState<string>("All");

  const { data, isLoading, error } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectApi.listProjects(0, 50),
  });

  const deleteMutation = useMutation({
    mutationFn: (projectId: string) => projectApi.deleteProject(projectId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["projects"] }),
  });

  const allProjects = data?.data?.content ?? [];

  const projects = useMemo(() => {
    let out = allProjects;
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      out = out.filter(
        (p) => p.code.toLowerCase().includes(q) || p.name.toLowerCase().includes(q)
      );
    }
    if (statusFilter !== "All") out = out.filter((p) => p.status === statusFilter);
    if (priorityFilter !== "All") {
      out = out.filter(
        (p) => (p.priority ?? "").toString().toUpperCase() === priorityFilter
      );
    }
    return out;
  }, [allProjects, searchQuery, statusFilter, priorityFilter]);

  const counts = useMemo(() => {
    const active = allProjects.filter((p) => p.status === "ACTIVE").length;
    const planned = allProjects.filter((p) => p.status === "PLANNED").length;
    const completed = allProjects.filter((p) => p.status === "COMPLETED").length;
    return { active, planned, completed };
  }, [allProjects]);

  return (
    <div>
      {/* Page head */}
      <div className="mb-8 flex items-start justify-between gap-6">
        <div>
          <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-1.5">
            {counts.active} active · {counts.planned} planned · {counts.completed} completed
          </div>
          <h1 className="font-display text-[38px] font-semibold leading-[1.08] tracking-tight text-charcoal"
              style={{ fontVariationSettings: "'opsz' 144" }}>
            Projects
          </h1>
          <p className="mt-2 max-w-[560px] text-sm text-slate leading-relaxed">
            All projects in your portfolio. Filter, drill in, or spin up a new programme.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            className="inline-flex h-10 items-center gap-1.5 rounded-[10px] border border-gold bg-paper px-4 text-sm font-semibold text-gold-deep hover:bg-ivory"
          >
            <Download size={14} strokeWidth={1.5} />
            Export CSV
          </button>
          <Link
            href="/projects/new"
            className="inline-flex h-10 items-center gap-1.5 rounded-[10px] bg-gold px-4 text-sm font-semibold text-paper transition-all duration-200 hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.3)] hover:-translate-y-px"
          >
            <Plus size={14} strokeWidth={2.5} />
            New project
          </Link>
        </div>
      </div>

      {/* Toolbar */}
      <div className="mb-5 flex flex-wrap items-center gap-2.5">
        <div className="flex h-10 flex-1 max-w-[340px] items-center gap-2 rounded-[10px] border border-hairline bg-paper px-3">
          <Search size={15} className="text-ash" strokeWidth={1.5} />
          <input
            type="text"
            placeholder="Search by code or name…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="flex-1 border-none bg-transparent text-sm text-charcoal placeholder:text-ash outline-none"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="h-10 rounded-[10px] border border-hairline bg-paper pl-3.5 pr-8 text-sm font-medium text-charcoal focus:border-gold focus:outline-none focus:shadow-[0_0_0_3px_rgba(212,175,55,0.18)]"
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {s === "All" ? "All statuses" : s}
            </option>
          ))}
        </select>
        <select
          value={priorityFilter}
          onChange={(e) => setPriorityFilter(e.target.value)}
          className="h-10 rounded-[10px] border border-hairline bg-paper pl-3.5 pr-8 text-sm font-medium text-charcoal focus:border-gold focus:outline-none focus:shadow-[0_0_0_3px_rgba(212,175,55,0.18)]"
        >
          {PRIORITY_OPTIONS.map((p) => (
            <option key={p} value={p}>
              {p === "All" ? "All priorities" : p.charAt(0) + p.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
      </div>

      {/* Active filter chips */}
      {(statusFilter !== "All" || priorityFilter !== "All") && (
        <div className="mb-3 flex items-center gap-1.5">
          {statusFilter !== "All" && (
            <button
              onClick={() => setStatusFilter("All")}
              className="inline-flex items-center gap-1.5 rounded-md border border-[#E8D68A] bg-gold-tint px-2.5 py-1 text-[11px] font-semibold text-gold-ink hover:bg-[#EFDD94]"
            >
              Status: {statusFilter} <span aria-hidden>✕</span>
            </button>
          )}
          {priorityFilter !== "All" && (
            <button
              onClick={() => setPriorityFilter("All")}
              className="inline-flex items-center gap-1.5 rounded-md border border-[#E8D68A] bg-gold-tint px-2.5 py-1 text-[11px] font-semibold text-gold-ink hover:bg-[#EFDD94]"
            >
              Priority: {priorityFilter} <span aria-hidden>✕</span>
            </button>
          )}
          <button
            onClick={() => { setStatusFilter("All"); setPriorityFilter("All"); }}
            className="ml-1 text-[11px] font-semibold text-gold-deep hover:text-gold-ink"
          >
            Clear all
          </button>
        </div>
      )}

      {isLoading && (
        <div className="space-y-3">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="h-14 animate-pulse rounded-lg bg-parchment" />
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-xl border border-[#E5C4C4] bg-[#F5E2E2] p-4 text-sm text-burgundy">
          Failed to load projects. Is the backend running?
        </div>
      )}

      {!isLoading && allProjects.length === 0 && (
        <div className="rounded-xl border border-dashed border-hairline bg-paper py-12 text-center">
          <p className="text-sm text-slate">No projects yet. Create your first project to get started.</p>
        </div>
      )}

      {!isLoading && allProjects.length > 0 && projects.length === 0 && (
        <div className="rounded-xl border border-dashed border-hairline bg-paper py-12 text-center">
          <p className="text-sm text-slate">No projects match your filters.</p>
        </div>
      )}

      {projects.length > 0 && (
        <>
          <div className="overflow-hidden rounded-xl border border-hairline bg-paper">
            <table className="w-full border-collapse text-sm">
              <thead className="border-b border-hairline bg-ivory">
                <tr>
                  {["Code","Name","Status","Start","Finish","Priority","Actions"].map((h) => (
                    <th
                      key={h}
                      className={`px-4 py-3 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep ${h === "Actions" ? "text-right" : ""}`}
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {projects.map((project) => {
                  const priority = getPriorityInfo(project.priority);
                  return (
                    <tr
                      key={project.id}
                      className="border-b border-[#F4EDD8] transition-colors last:border-b-0 hover:bg-ivory"
                    >
                      <td className="px-4 py-3.5">
                        <Link
                          href={`/projects/${project.id}`}
                          className="font-mono text-[12px] font-medium text-gold-deep hover:text-gold-ink"
                        >
                          {project.code}
                        </Link>
                      </td>
                      <td className="px-4 py-3.5 font-semibold text-charcoal">{project.name}</td>
                      <td className="px-4 py-3.5">
                        <Badge variant={statusVariant(project.status)} withDot>
                          {project.status}
                        </Badge>
                      </td>
                      <td className="px-4 py-3.5 text-slate">{formatDate(project.plannedStartDate)}</td>
                      <td className="px-4 py-3.5 text-slate">{formatDate(project.plannedFinishDate)}</td>
                      <td className={`px-4 py-3.5 font-semibold ${priority.color}`}>{priority.label}</td>
                      <td className="px-4 py-3.5">
                        <div className="flex items-center justify-end gap-1">
                          <Link
                            href={`/projects/${project.id}`}
                            className="rounded-md p-1.5 text-slate transition-colors hover:bg-parchment hover:text-gold-deep"
                            aria-label="Edit"
                          >
                            <Edit2 size={14} strokeWidth={1.5} />
                          </Link>
                          <button
                            onClick={() => {
                              if (window.confirm("Are you sure you want to delete this project?")) {
                                deleteMutation.mutate(project.id);
                              }
                            }}
                            disabled={deleteMutation.isPending}
                            className="rounded-md p-1.5 text-slate transition-colors hover:bg-parchment hover:text-burgundy disabled:cursor-not-allowed disabled:opacity-50"
                            aria-label="Delete"
                          >
                            <Trash2 size={14} strokeWidth={1.5} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="pt-3 text-center text-xs text-slate">
            Showing <span className="font-semibold text-charcoal">{projects.length} of {allProjects.length}</span>
          </div>
        </>
      )}
    </div>
  );
}
```

- [ ] **Step 3: `pnpm tsc --noEmit`** → clean. Reload `/projects`: counter kicker, Fraunces h1, toolbar with search + 2 filters, chips appear when filters are set, table renders with gold-bronze header labels and monospaced project codes.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/\(app\)/projects/page.tsx
git commit -m "feat(projects): rebuild Projects list in White & Gold identity

- Counter-kicker + Fraunces h1 + right-aligned Export/New CTAs
- Search pill + Status + Priority filters (both client-side)
- Active filter chips row with 'Clear all'
- Replace inline StatusBadge with shared Badge (withDot)
- Monospaced gold project codes, hairline rows, ivory hover
- Row action icons (Edit/Delete) with gold/burgundy hover"
```

---

### Task 2.4: Phase 2 verification

- [ ] **Step 1: lint + e2e + manual walkthrough** (same gates as Task 1.15), specifically checking Dashboard and Projects list.
- [ ] **Step 2: No separate commit** unless fixes are needed.

---

## Phase 3 — Landing repaint (PR 3)

### Task 3.1: Scaffold landing section components directory

**Files:**
- Create: `frontend/src/app/auth/login/_components/landing-data.ts`

- [ ] **Step 1: Create directory and data module**

```bash
mkdir -p frontend/src/app/auth/login/_components
```

Create `frontend/src/app/auth/login/_components/landing-data.ts` with:

```tsx
import type { LucideIcon } from "lucide-react";
import {
  BarChart3, Briefcase, Calendar, CheckCircle, FolderTree, Layers, Settings,
  Shield, ShoppingBag, TrendingUp, Users,
} from "lucide-react";

export type Module = { n: string; title: string; blurb: string; Icon: LucideIcon };
export const modules: Module[] = [
  { n: "M01", title: "Portfolio",   blurb: "Programmes, baselines, funding.",           Icon: Briefcase },
  { n: "M02", title: "Schedule",    blurb: "CPM, levelling, baselines.",                Icon: Calendar },
  { n: "M03", title: "Cost",        blurb: "Budget, actuals, commitments.",             Icon: TrendingUp },
  { n: "M04", title: "Risk",        blurb: "Register, analysis, mitigation.",           Icon: Shield },
  { n: "M05", title: "Procurement", blurb: "Contracts, POs, awards.",                   Icon: ShoppingBag },
  { n: "M06", title: "Field",       blurb: "Daily progress, quantities.",               Icon: FolderTree },
  { n: "M07", title: "Quality",     blurb: "Inspections, NCRs, punch lists.",           Icon: CheckCircle },
  { n: "M08", title: "HSE",         blurb: "Incidents, audits, compliance.",            Icon: BarChart3 },
  { n: "M09", title: "Resources",   blurb: "People, equipment, rate cards.",            Icon: Users },
];

export type Pillar = { roman: string; title: string; blurb: string; link: string; Icon: LucideIcon };
export const pillars: Pillar[] = [
  {
    roman: "I · PLAN", title: "Plan",
    blurb: "Portfolios, enterprise project structures, baselines — model programmes the way they're actually funded and reported.",
    link: "Portfolio, EPS, baselines →",
    Icon: Briefcase,
  },
  {
    roman: "II · EXECUTE", title: "Execute",
    blurb: "Scheduling, resource levelling, field operations. The only CPM engine built for multi-project portfolios at programme scale.",
    link: "Scheduling, resources, field →",
    Icon: Calendar,
  },
  {
    roman: "III · CONTROL", title: "Control",
    blurb: "Cost, earned-value, risk, variance — one source of truth for the numbers your sponsors and auditors ask for.",
    link: "Cost, EVM, risk →",
    Icon: Settings,
  },
];

export type Industry = { n: string; name: string; blurb: string; art: React.ReactNode };

export type Stat = { v: string; l: string };
export const stats: Stat[] = [
  { v: "$42B",    l: "Portfolio value under management" },
  { v: "1,800+",  l: "Projects delivered" },
  { v: "32%",     l: "Schedule improvement" },
  { v: "99.95%",  l: "Platform uptime" },
];

export type Step = { roman: string; title: string; blurb: string };
export const steps: Step[] = [
  { roman: "I",   title: "Configure", blurb: "WBS, codes, calendars, cost structures." },
  { roman: "II",  title: "Migrate",   blurb: "Import from P6, MSP, or spreadsheets." },
  { roman: "III", title: "Roll out",  blurb: "Train programme managers & controllers." },
  { roman: "IV",  title: "Operate",   blurb: "Weekly updates, monthly baselines, quarterly reviews." },
];

export type GanttRow = { name: string; tone: "default" | "success" | "warning" | "danger"; leftPct: number; widthPct: number };
export const ganttRows: GanttRow[] = [
  { name: "Earthworks",                   tone: "success", leftPct: 2,  widthPct: 28 },
  { name: "Bridge sections",              tone: "success", leftPct: 12, widthPct: 34 },
  { name: "Track laying",                 tone: "default", leftPct: 32, widthPct: 38 },
  { name: "Signalling",                   tone: "warning", leftPct: 48, widthPct: 24 },
  { name: "Station fit-out",              tone: "default", leftPct: 58, widthPct: 30 },
  { name: "Testing & commissioning",      tone: "danger",  leftPct: 76, widthPct: 22 },
];
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/auth/login/_components/landing-data.ts
git commit -m "chore(login): add landing content data module"
```

---

### Task 3.2: Build `LandingNav` component

**Files:**
- Create: `frontend/src/app/auth/login/_components/LandingNav.tsx`

- [ ] **Step 1: Create the file with:**

```tsx
"use client";

export function LandingNav() {
  return (
    <nav className="relative flex items-center bg-paper border-b border-hairline px-9 py-4">
      <div
        aria-hidden
        className="absolute inset-x-0 -bottom-px h-px"
        style={{ background: "linear-gradient(90deg, transparent, #D4AF37 20%, #D4AF37 80%, transparent)", opacity: 0.4 }}
      />
      <div className="flex items-center gap-2.5">
        <div
          className="flex h-7 w-7 items-center justify-center rounded-[7px] text-paper font-display font-bold text-sm"
          style={{ background: "linear-gradient(135deg,#D4AF37,#B8962E 60%,#8C6F1E)" }}
        >
          B
        </div>
        <span className="font-display font-semibold text-xl text-charcoal tracking-tight">Bipros</span>
      </div>
      <div className="mx-auto flex items-center gap-7 text-[13px] text-slate">
        <a className="hover:text-charcoal cursor-pointer">Platform</a>
        <a className="hover:text-charcoal cursor-pointer">Industries</a>
        <a className="hover:text-charcoal cursor-pointer">Customers</a>
        <a className="hover:text-charcoal cursor-pointer">Pricing</a>
        <a className="hover:text-charcoal cursor-pointer">Resources</a>
      </div>
      <div className="flex items-center gap-2.5">
        <button type="button" className="h-9 px-3 text-sm font-semibold text-charcoal rounded-lg hover:bg-ivory">
          Sign in
        </button>
        <button
          type="button"
          className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-gold px-4 text-sm font-semibold text-paper transition-all duration-200 hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.3)] hover:-translate-y-px"
        >
          Request demo
        </button>
      </div>
    </nav>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/auth/login/_components/LandingNav.tsx
git commit -m "feat(login): add LandingNav section"
```

---

### Task 3.3: Build `LandingHero` component (hero + sign-in card)

**Files:**
- Create: `frontend/src/app/auth/login/_components/LandingHero.tsx`

This carries the real sign-in form wiring (authApi, useAuthStore, useRouter). The existing logic is preserved; only presentation changes.

- [ ] **Step 1: Create the file with:**

```tsx
"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import toast from "react-hot-toast";
import { Eye, EyeOff } from "lucide-react";
import { authApi } from "@/lib/api/authApi";
import { useAuthStore } from "@/lib/state/store";

export function LandingHero() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(true);
  const [showPw, setShowPw] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const res = await authApi.login({ username: email, password });
      setAuth(res.user, res.accessToken, res.refreshToken);
      toast.success("Signed in");
      router.push("/");
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Sign-in failed";
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section
      className="relative grid gap-12 px-9 py-20 lg:grid-cols-[1.25fr_1fr]"
      style={{ background: "linear-gradient(180deg,#FFFFFF 0%, #FAF9F6 100%)" }}
    >
      <div
        aria-hidden
        className="pointer-events-none absolute right-[-120px] top-[-120px] h-[340px] w-[340px] rounded-full"
        style={{ background: "radial-gradient(circle, rgba(212,175,55,0.08) 0%, transparent 70%)" }}
      />
      <div className="relative">
        <div className="mb-3.5 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-gold-deep">
          <span aria-hidden className="inline-block h-px w-5 bg-gold" />
          Enterprise PPM
        </div>
        <h1
          className="font-display font-semibold text-[56px] leading-[1.04] tracking-[-0.018em] text-charcoal"
          style={{ fontVariationSettings: "'opsz' 144" }}
        >
          Run every infrastructure programme
          <br />
          as <em className="not-italic font-medium italic text-gold-deep">one</em>.
        </h1>
        <p className="mt-4 max-w-[500px] text-[17px] leading-relaxed text-charcoal">
          Portfolio, schedule, cost, risk, and field operations on a single spine. Trusted by
          programme leaders to deliver at scale — from rail corridors to solar farms.
        </p>
        <div className="mt-7 flex gap-2.5">
          <button
            type="button"
            className="inline-flex h-12 items-center gap-1.5 rounded-xl bg-gold px-5 text-sm font-semibold text-paper transition-all duration-200 hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.3)] hover:-translate-y-px"
          >
            Request demo →
          </button>
          <button
            type="button"
            className="inline-flex h-12 items-center rounded-xl border border-gold bg-paper px-5 text-sm font-semibold text-gold-deep hover:bg-ivory"
          >
            Explore the platform
          </button>
        </div>
        <div className="mt-8 flex items-center gap-4">
          <div className="flex">
            {["MC", "JR", "AS", "+"].map((s, i) => (
              <div
                key={i}
                className={`flex h-8 w-8 items-center justify-center rounded-full bg-parchment border-2 border-paper ${i ? "-ml-2" : ""} font-display font-semibold text-[11px] text-gold-deep`}
              >
                {s}
              </div>
            ))}
          </div>
          <div>
            <div className="text-[12px] text-gold-deep">★★★★★ <span className="font-medium text-slate">4.8 on G2</span></div>
            <div className="text-[12px] text-slate">
              <strong className="text-charcoal">400+ teams</strong> delivering programmes on Bipros
            </div>
          </div>
        </div>
      </div>

      {/* Sign-in card */}
      <form
        onSubmit={handleSubmit}
        className="relative z-10 rounded-2xl border border-hairline bg-paper p-7 shadow-[0_4px_20px_rgba(28,28,28,0.05)]"
      >
        <h3 className="font-display text-xl font-semibold text-charcoal">Welcome back</h3>
        <div className="mt-0.5 text-xs text-slate">Sign in to your portfolio</div>

        <div className="mt-4 grid grid-cols-3 gap-1.5">
          {["Google", "Microsoft", "SSO"].map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => toast("SSO sign-in is not yet configured")}
              className="h-10 rounded-[10px] border border-hairline bg-paper text-[11px] font-medium text-charcoal hover:border-gold transition-colors"
            >
              {p}
            </button>
          ))}
        </div>

        <div className="my-4 flex items-center gap-3 text-[10px] font-semibold uppercase tracking-[0.14em] text-ash">
          <div className="h-px flex-1 bg-hairline" />
          or with email
          <div className="h-px flex-1 bg-hairline" />
        </div>

        {error && (
          <div className="mb-3 rounded-md border border-[#E5C4C4] bg-[#F5E2E2] px-3 py-2 text-xs text-burgundy">
            {error}
          </div>
        )}

        <label className="block text-xs font-semibold text-charcoal mb-1.5">Email or username</label>
        <input
          type="text"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@company.com"
          required
          className="mb-3 h-10 w-full rounded-[10px] border border-divider bg-paper px-3.5 text-sm text-charcoal placeholder:text-ash outline-none transition-all duration-120 hover:border-[#D1C7A0] focus:border-gold focus:shadow-[0_0_0_3px_rgba(212,175,55,0.18)]"
        />

        <label className="block text-xs font-semibold text-charcoal mb-1.5">Password</label>
        <div className="relative mb-3">
          <input
            type={showPw ? "text" : "password"}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            className="h-10 w-full rounded-[10px] border border-divider bg-paper px-3.5 pr-10 text-sm text-charcoal outline-none transition-all duration-120 hover:border-[#D1C7A0] focus:border-gold focus:shadow-[0_0_0_3px_rgba(212,175,55,0.18)]"
          />
          <button
            type="button"
            onClick={() => setShowPw((s) => !s)}
            aria-label={showPw ? "Hide password" : "Show password"}
            className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-slate hover:text-gold-deep"
          >
            {showPw ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>

        <div className="mb-4 flex items-center justify-between text-[11px] text-slate">
          <label className="flex items-center gap-1.5">
            <input
              type="checkbox"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
              className="accent-[#D4AF37]"
            />
            Remember me
          </label>
          <a className="font-semibold text-gold-deep hover:text-gold-ink cursor-pointer">Forgot?</a>
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="inline-flex h-12 w-full items-center justify-center rounded-xl bg-gold text-sm font-semibold text-paper transition-all duration-200 hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.3)] hover:-translate-y-px disabled:opacity-70 disabled:cursor-not-allowed disabled:translate-y-0 disabled:shadow-none"
        >
          {submitting ? "Signing in…" : "Sign in"}
        </button>

        <div className="mt-4 flex justify-center gap-1.5">
          {["SOC 2", "ISO 27001", "SSO / SAML"].map((t) => (
            <span
              key={t}
              className="rounded border border-hairline bg-ivory px-2 py-1 font-mono text-[9px] uppercase tracking-[0.1em] text-slate"
            >
              {t}
            </span>
          ))}
        </div>
      </form>
    </section>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/auth/login/_components/LandingHero.tsx
git commit -m "feat(login): add LandingHero — editorial h1, sign-in form wired to authApi"
```

---

### Task 3.4: Build `LandingPillars` component

**Files:**
- Create: `frontend/src/app/auth/login/_components/LandingPillars.tsx`

- [ ] **Step 1: Create the file with:**

```tsx
import { pillars } from "./landing-data";

export function LandingPillars() {
  return (
    <section className="bg-ivory px-9 py-20">
      <div className="mx-auto mb-10 max-w-[640px] text-center">
        <div className="mb-2.5 text-[10px] font-semibold uppercase tracking-[0.18em] text-gold-deep">
          The platform
        </div>
        <h2
          className="font-display text-[38px] font-semibold leading-[1.1] tracking-tight text-charcoal"
          style={{ fontVariationSettings: "'opsz' 144" }}
        >
          One spine for planning, execution, and control
        </h2>
        <p className="mt-2.5 text-[15px] leading-relaxed text-slate">
          Three disciplines, nine modules, one data model. Replace stitched-together tools with a
          system designed for how programmes actually run.
        </p>
      </div>
      <div className="mx-auto grid max-w-[1180px] gap-5 lg:grid-cols-3">
        {pillars.map((p) => (
          <div
            key={p.title}
            className="rounded-xl border border-hairline border-l-[3px] border-l-gold bg-paper p-7 transition-all duration-200 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] hover:-translate-y-0.5"
          >
            <div className="font-mono text-[10px] tracking-[0.14em] text-gold-deep">{p.roman}</div>
            <div
              className="mt-4 flex h-10 w-10 items-center justify-center rounded-[10px] text-gold-deep"
              style={{ background: "linear-gradient(135deg,#FDF6DD,#F5E7B5)" }}
            >
              <p.Icon size={20} strokeWidth={1.5} />
            </div>
            <h4 className="mt-4 font-display text-xl font-semibold tracking-tight text-charcoal">
              {p.title}
            </h4>
            <p className="mt-2 text-[13px] leading-relaxed text-slate">{p.blurb}</p>
            <a className="mt-3.5 inline-block text-xs font-semibold text-gold-deep hover:text-gold-ink cursor-pointer">
              {p.link}
            </a>
          </div>
        ))}
      </div>
    </section>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/auth/login/_components/LandingPillars.tsx
git commit -m "feat(login): add LandingPillars — three-up card grid with gold side-rails"
```

---

### Task 3.5: Build `LandingModules` (dark section)

**Files:**
- Create: `frontend/src/app/auth/login/_components/LandingModules.tsx`

- [ ] **Step 1: Create the file with:**

```tsx
import { modules } from "./landing-data";

export function LandingModules() {
  return (
    <section className="relative px-9 py-20" style={{ background: "#141414", color: "#F5E7B5" }}>
      <div
        aria-hidden
        className="absolute inset-x-0 top-0 h-px"
        style={{ background: "linear-gradient(90deg, transparent, #D4AF37, transparent)" }}
      />
      <div className="mx-auto mb-11 max-w-[640px] text-center">
        <div className="mb-2.5 text-[10px] font-semibold uppercase tracking-[0.18em] text-gold">
          Nine modules · one model
        </div>
        <h2
          className="font-display text-[38px] font-semibold leading-[1.1] tracking-tight text-paper"
          style={{ fontVariationSettings: "'opsz' 144" }}
        >
          Built for the{" "}
          <em className="not-italic font-medium italic text-gold">whole</em> programme
        </h2>
        <p className="mt-2.5 text-sm text-[rgba(245,231,181,0.65)]">
          Every module shares a single data model, so cost reconciles to schedule, risk rolls up to
          portfolio, and field progress flows back to EVM without export-and-reimport.
        </p>
      </div>
      <div className="mx-auto grid max-w-[1040px] gap-3 md:grid-cols-3">
        {modules.map((m) => (
          <div
            key={m.n}
            className="group cursor-pointer rounded-[10px] border p-5.5 transition-all duration-200 hover:-translate-y-0.5"
            style={{ background: "rgba(255,255,255,0.02)", borderColor: "rgba(212,175,55,0.18)" }}
          >
            <div className="font-mono text-[10px] tracking-[0.14em] text-gold">{m.n}</div>
            <div
              className="mt-2.5 flex h-7 w-7 items-center justify-center rounded-[7px] text-gold"
              style={{ background: "rgba(212,175,55,0.12)" }}
            >
              <m.Icon size={14} strokeWidth={1.5} />
            </div>
            <h5 className="mt-3 font-display text-lg font-semibold text-paper">{m.title}</h5>
            <p className="mt-1 text-xs leading-relaxed" style={{ color: "rgba(245,231,181,0.55)" }}>
              {m.blurb}
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/auth/login/_components/LandingModules.tsx
git commit -m "feat(login): add LandingModules — dark charcoal grid, 9 gold module tiles"
```

---

### Task 3.6: Build `LandingIndustries`

**Files:**
- Create: `frontend/src/app/auth/login/_components/LandingIndustries.tsx`

- [ ] **Step 1: Create the file with:**

```tsx
function Art({ children }: { children: React.ReactNode }) {
  return (
    <div
      className="relative flex h-[110px] items-center justify-center overflow-hidden text-gold"
      style={{ background: "linear-gradient(180deg,#141414,#0B0B0B)" }}
    >
      <div
        aria-hidden
        className="absolute inset-0"
        style={{ background: "radial-gradient(circle at 70% 30%, rgba(212,175,55,0.15), transparent 60%)" }}
      />
      <svg width="90" height="60" viewBox="0 0 90 60" fill="none" stroke="currentColor" strokeWidth="1" className="relative z-10">
        {children}
      </svg>
    </div>
  );
}

export function LandingIndustries() {
  return (
    <section className="bg-ivory px-9 py-20">
      <div className="mx-auto mb-10 max-w-[640px] text-center">
        <div className="mb-2.5 text-[10px] font-semibold uppercase tracking-[0.18em] text-gold-deep">
          Built for heavy infrastructure
        </div>
        <h2
          className="font-display text-[36px] font-semibold leading-[1.1] tracking-tight text-charcoal"
          style={{ fontVariationSettings: "'opsz' 144" }}
        >
          Industries we serve
        </h2>
      </div>
      <div className="mx-auto grid max-w-[1180px] gap-4 md:grid-cols-2 lg:grid-cols-4">
        {[
          {
            n: "01", name: "Road & Rail",
            blurb: "Linear mega-projects with corridor-based phasing.",
            art: (
              <>
                <path d="M5 50 L35 25 L55 40 L85 15" />
                <circle cx="5" cy="50" r="2" fill="currentColor" />
                <circle cx="35" cy="25" r="2" fill="currentColor" />
                <circle cx="55" cy="40" r="2" fill="currentColor" />
                <circle cx="85" cy="15" r="2" fill="currentColor" />
                <path d="M0 55 L90 55" strokeDasharray="2 3" />
              </>
            ),
          },
          {
            n: "02", name: "Energy",
            blurb: "Solar, wind, and transmission programmes.",
            art: (
              <>
                <path d="M45 10 L60 30 L75 10" />
                <path d="M15 10 L30 30 L45 10" />
                <path d="M10 50 L80 50" />
                <path d="M20 50 L20 30 M40 50 L40 30 M60 50 L60 30" />
              </>
            ),
          },
          {
            n: "03", name: "Water",
            blurb: "Dams, treatment, distribution networks.",
            art: (
              <>
                <path d="M10 45 Q25 30 40 45 T70 45 T90 45" />
                <path d="M10 50 Q25 35 40 50 T70 50 T90 50" opacity=".5" />
                <circle cx="25" cy="20" r="4" />
                <path d="M25 24 L25 35" />
              </>
            ),
          },
          {
            n: "04", name: "Urban Rail",
            blurb: "Metro, light rail, and station programmes.",
            art: (
              <>
                <path d="M5 40 L85 40" />
                <path d="M5 30 L85 30" strokeDasharray="3 2" />
                <rect x="20" y="15" width="10" height="25" />
                <rect x="55" y="15" width="10" height="25" />
              </>
            ),
          },
        ].map((i) => (
          <div
            key={i.n}
            className="cursor-pointer overflow-hidden rounded-xl border border-hairline bg-paper transition-all duration-200 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] hover:-translate-y-0.5"
          >
            <Art>{i.art}</Art>
            <div className="p-5">
              <div className="font-mono text-[10px] tracking-[0.14em] text-gold-deep">{i.n}</div>
              <h5 className="mt-1 font-display text-lg font-semibold tracking-tight text-charcoal">
                {i.name}
              </h5>
              <p className="mt-1 text-xs leading-relaxed text-slate">{i.blurb}</p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/auth/login/_components/LandingIndustries.tsx
git commit -m "feat(login): add LandingIndustries — 4 cards with gold line-art thumbnails"
```

---

### Task 3.7: Build `LandingStats`

**Files:**
- Create: `frontend/src/app/auth/login/_components/LandingStats.tsx`

- [ ] **Step 1: Create the file with:**

```tsx
import { stats } from "./landing-data";

export function LandingStats() {
  return (
    <section className="grid gap-8 border-y border-hairline bg-paper px-9 py-14 md:grid-cols-2 lg:grid-cols-4 text-center">
      {stats.map((s) => (
        <div key={s.l}>
          <div
            className="font-display text-[48px] font-semibold leading-none tracking-tight text-gold-deep"
            style={{ fontVariationSettings: "'opsz' 144" }}
          >
            {s.v}
          </div>
          <div className="mt-2 text-[11px] font-semibold uppercase tracking-[0.14em] text-slate">
            {s.l}
          </div>
        </div>
      ))}
    </section>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/auth/login/_components/LandingStats.tsx
git commit -m "feat(login): add LandingStats — four Fraunces gold metrics"
```

---

### Task 3.8: Build `LandingShowcase` (with Gantt mock)

**Files:**
- Create: `frontend/src/app/auth/login/_components/LandingShowcase.tsx`

- [ ] **Step 1: Create the file with:**

```tsx
import { ganttRows } from "./landing-data";

const fills: Record<(typeof ganttRows)[number]["tone"], string> = {
  default: "linear-gradient(90deg,#D4AF37,#B8962E)",
  success: "linear-gradient(90deg,#2E7D5B,#256B4C)",
  warning: "linear-gradient(90deg,#C7882E,#A6701F)",
  danger: "linear-gradient(90deg,#9B2C2C,#7F2424)",
};

export function LandingShowcase() {
  return (
    <section className="grid items-center gap-12 bg-ivory px-9 py-20 lg:grid-cols-[1fr_1.2fr]">
      <div>
        <div className="mb-3 text-[10px] font-semibold uppercase tracking-[0.18em] text-gold-deep">
          Showcase · Master schedule
        </div>
        <h2
          className="font-display text-[38px] font-semibold leading-[1.1] tracking-tight text-charcoal"
          style={{ fontVariationSettings: "'opsz' 144" }}
        >
          Every programme on{" "}
          <em className="not-italic font-medium italic text-gold-deep">one</em> Gantt.
        </h2>
        <p className="mt-3 text-[15px] leading-relaxed text-charcoal">
          Roll 2,000+ activities across 50 projects into a single master schedule. Drive
          critical-path across the portfolio, not project-by-project.
        </p>
        <ul className="mt-5 space-y-2.5">
          {[
            "Portfolio-level CPM with resource levelling",
            "Baselines per programme, per sponsor, per audit",
            "Import from P6, MS Project, Primavera in minutes",
            "Variance surfaced against baseline, not version-to-version",
          ].map((item) => (
            <li key={item} className="flex items-start gap-2.5 text-sm text-charcoal">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#D4AF37" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="mt-1 flex-shrink-0">
                <polyline points="20 6 9 17 4 12" />
              </svg>
              {item}
            </li>
          ))}
        </ul>
        <button
          type="button"
          className="mt-6 inline-flex h-10 items-center rounded-[10px] border border-gold bg-paper px-4 text-sm font-semibold text-gold-deep hover:bg-paper hover:text-gold-ink"
        >
          Explore scheduling →
        </button>
      </div>

      {/* Gantt */}
      <div className="rounded-2xl border border-hairline bg-paper p-4 shadow-[0_4px_20px_rgba(28,28,28,0.05)]">
        <div className="flex justify-between border-b border-[#F4EDD8] px-2 pb-2.5 font-mono text-[10px] tracking-[0.08em] text-slate">
          <span>NORTHWEST RAIL EXT · WBS 1.4.2</span>
          <span>JAN · FEB · MAR · APR · MAY · JUN</span>
        </div>
        {ganttRows.map((row) => (
          <div key={row.name} className="grid grid-cols-[140px_1fr] items-center px-2 py-2.5 text-[11px]">
            <span className="font-medium text-charcoal">{row.name}</span>
            <div className="relative h-3.5 rounded bg-ivory">
              <div
                className="absolute inset-y-0 rounded"
                style={{ left: `${row.leftPct}%`, width: `${row.widthPct}%`, background: fills[row.tone] }}
              />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/auth/login/_components/LandingShowcase.tsx
git commit -m "feat(login): add LandingShowcase — showcase copy + Gantt mock"
```

---

### Task 3.9: Build `LandingWorkflow`

**Files:**
- Create: `frontend/src/app/auth/login/_components/LandingWorkflow.tsx`

- [ ] **Step 1: Create the file with:**

```tsx
import { steps } from "./landing-data";

export function LandingWorkflow() {
  return (
    <section className="bg-paper px-9 py-20">
      <div className="mx-auto mb-12 max-w-[640px] text-center">
        <div className="mb-2.5 text-[10px] font-semibold uppercase tracking-[0.18em] text-gold-deep">
          How rollouts happen
        </div>
        <h2
          className="font-display text-[34px] font-semibold leading-[1.1] tracking-tight text-charcoal"
          style={{ fontVariationSettings: "'opsz' 144" }}
        >
          From kickoff to live programme in weeks.
        </h2>
      </div>
      <div
        className="relative mx-auto grid max-w-[1040px] grid-cols-2 gap-3 md:grid-cols-4"
        style={{
          backgroundImage:
            "repeating-linear-gradient(90deg, transparent 0 60px, transparent 60px calc(100% - 60px), transparent 100%)",
        }}
      >
        <div
          aria-hidden
          className="absolute left-[60px] right-[60px] top-6 hidden h-px md:block"
          style={{ background: "repeating-linear-gradient(90deg,#D4AF37 0 4px, transparent 4px 10px)" }}
        />
        {steps.map((s) => (
          <div key={s.roman} className="relative z-10 px-3 text-center">
            <div
              className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-paper font-display font-semibold text-xl text-gold-deep"
              style={{ border: "2px solid #D4AF37" }}
            >
              {s.roman}
            </div>
            <h5 className="font-display text-lg font-semibold tracking-tight text-charcoal">
              {s.title}
            </h5>
            <p className="mt-1 text-xs leading-relaxed text-slate">{s.blurb}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/auth/login/_components/LandingWorkflow.tsx
git commit -m "feat(login): add LandingWorkflow — 4-step Roman-numeral flow with dashed connector"
```

---

### Task 3.10: Build `LandingCTA` (dark)

**Files:**
- Create: `frontend/src/app/auth/login/_components/LandingCTA.tsx`

- [ ] **Step 1: Create the file with:**

```tsx
export function LandingCTA() {
  return (
    <section
      className="relative overflow-hidden px-9 py-18 text-center"
      style={{ background: "linear-gradient(180deg,#141414 0%,#0B0B0B 100%)" }}
    >
      <div
        aria-hidden
        className="absolute inset-0"
        style={{ background: "radial-gradient(circle at 50% 50%, rgba(212,175,55,0.12), transparent 60%)" }}
      />
      <div className="relative mx-auto max-w-[720px]">
        <div className="mb-3 text-[10px] font-semibold uppercase tracking-[0.18em] text-gold">
          Ready when you are
        </div>
        <h2
          className="font-display text-[42px] font-semibold leading-[1.1] tracking-[-0.015em] text-paper"
          style={{ fontVariationSettings: "'opsz' 144" }}
        >
          Run every programme{" "}
          <em className="not-italic font-medium italic text-gold">as one.</em>
        </h2>
        <p className="mt-4 text-[15px]" style={{ color: "rgba(245,231,181,0.7)" }}>
          See Bipros EPPM on your own data. Demo in 30 minutes, pilot in two weeks.
        </p>
        <div className="mt-7 flex justify-center gap-2.5">
          <button
            type="button"
            className="inline-flex h-12 items-center rounded-xl bg-gold px-5 text-sm font-semibold text-paper transition-all duration-200 hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.3)] hover:-translate-y-px"
          >
            Request demo →
          </button>
          <button
            type="button"
            className="inline-flex h-12 items-center rounded-xl border border-gold bg-transparent px-5 text-sm font-semibold text-gold hover:bg-[rgba(212,175,55,0.1)]"
          >
            Download the whitepaper
          </button>
        </div>
      </div>
    </section>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/auth/login/_components/LandingCTA.tsx
git commit -m "feat(login): add LandingCTA — dark band, gold radial bloom"
```

---

### Task 3.11: Build `LandingFooter`

**Files:**
- Create: `frontend/src/app/auth/login/_components/LandingFooter.tsx`

- [ ] **Step 1: Create the file with:**

```tsx
const linkColumns: { heading: string; items: string[] }[] = [
  { heading: "Product",    items: ["Platform", "Scheduling", "Cost & EVM", "Risk", "Pricing"] },
  { heading: "Industries", items: ["Road & Rail", "Energy", "Water", "Urban Rail"] },
  { heading: "Resources",  items: ["Customers", "Whitepapers", "Docs", "Webinars"] },
  { heading: "Company",    items: ["About", "Careers", "Partners", "Contact"] },
];

function SocialIcon({ path }: { path: string }) {
  return (
    <a className="flex h-8 w-8 items-center justify-center rounded-lg border border-hairline bg-paper text-slate transition-colors hover:border-gold hover:text-gold-deep cursor-pointer">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
        <path d={path} />
      </svg>
    </a>
  );
}

export function LandingFooter() {
  return (
    <footer className="border-t border-hairline bg-ivory px-9 pt-14 pb-7">
      <div className="mx-auto mb-9 grid max-w-[1180px] gap-8 md:grid-cols-[1.3fr_1fr_1fr_1fr_1fr]">
        <div>
          <div className="font-display text-xl font-semibold text-charcoal mb-2.5">Bipros</div>
          <p className="max-w-[260px] text-xs leading-relaxed text-slate">
            Enterprise portfolio & project management for heavy infrastructure. Built on one data
            model, delivered as one platform.
          </p>
          <div className="mt-3.5 flex gap-2">
            <SocialIcon path="M22.46 6c-.77.35-1.6.58-2.46.67.89-.53 1.57-1.37 1.88-2.38-.83.5-1.75.85-2.72 1.05C18.37 4.5 17.26 4 16 4c-2.35 0-4.27 1.92-4.27 4.29 0 .34.04.67.11.98C8.28 9.09 5.11 7.38 3 4.79c-.37.63-.58 1.37-.58 2.15 0 1.49.75 2.81 1.91 3.56-.71 0-1.37-.2-1.95-.5v.03c0 2.08 1.48 3.82 3.44 4.21a4.22 4.22 0 0 1-1.93.07 4.28 4.28 0 0 0 4 2.98 8.521 8.521 0 0 1-5.33 1.84c-.34 0-.68-.02-1.02-.06C3.44 20.29 5.7 21 8.12 21 16 21 20.33 14.46 20.33 8.79c0-.19 0-.37-.01-.56.84-.6 1.56-1.36 2.14-2.23z" />
            <SocialIcon path="M19 3a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14m-.5 15.5v-5.3a3.26 3.26 0 0 0-3.26-3.26c-.85 0-1.84.52-2.32 1.3v-1.11h-2.79v8.37h2.79v-4.93c0-.77.62-1.4 1.39-1.4a1.4 1.4 0 0 1 1.4 1.4v4.93h2.79M6.88 8.56a1.68 1.68 0 0 0 1.68-1.68c0-.93-.75-1.69-1.68-1.69a1.69 1.69 0 0 0-1.69 1.69c0 .93.76 1.68 1.69 1.68m1.39 9.94v-8.37H5.5v8.37h2.77z" />
            <SocialIcon path="M12 2C6.48 2 2 6.58 2 12.23c0 4.51 2.88 8.34 6.87 9.7.5.1.69-.22.69-.48v-1.7c-2.79.62-3.38-1.35-3.38-1.35-.46-1.17-1.12-1.48-1.12-1.48-.91-.63.07-.62.07-.62 1.01.07 1.54 1.06 1.54 1.06.9 1.57 2.35 1.12 2.92.86.09-.67.35-1.12.64-1.38-2.23-.26-4.58-1.14-4.58-5.07 0-1.12.39-2.04 1.03-2.76-.1-.26-.45-1.29.1-2.69 0 0 .85-.28 2.78 1.05.8-.23 1.66-.34 2.52-.34.86 0 1.72.11 2.52.34 1.93-1.33 2.78-1.05 2.78-1.05.55 1.4.2 2.43.1 2.69.64.72 1.03 1.64 1.03 2.76 0 3.94-2.36 4.81-4.6 5.06.36.32.68.93.68 1.89v2.8c0 .27.19.58.69.48C19.13 20.57 22 16.74 22 12.23 22 6.58 17.52 2 12 2z" />
          </div>
        </div>
        {linkColumns.map((col) => (
          <div key={col.heading}>
            <h6 className="mb-3.5 text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
              {col.heading}
            </h6>
            <ul className="space-y-2">
              {col.items.map((it) => (
                <li key={it}>
                  <a className="text-[13px] text-charcoal hover:text-gold-deep cursor-pointer">{it}</a>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="mx-auto flex max-w-[1180px] items-center justify-between border-t border-hairline pt-6 text-[11px] text-slate">
        <div>© {new Date().getFullYear()} Bipros. All rights reserved.</div>
        <div className="flex gap-4">
          {["Privacy", "Terms", "Security", "SOC 2"].map((t) => (
            <a key={t} className="hover:text-gold-deep cursor-pointer">{t}</a>
          ))}
        </div>
      </div>
    </footer>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/app/auth/login/_components/LandingFooter.tsx
git commit -m "feat(login): add LandingFooter — 5-col grid, gold-kicker headings, socials"
```

---

### Task 3.12: Rewire `login/page.tsx` to compose the new sections

**Files:**
- Modify: `frontend/src/app/auth/login/page.tsx`

This replaces the 28KB monolith with a thin composition of the ten new section components. Auth flow (authApi, useAuthStore, redirect on success) lives inside `LandingHero`.

- [ ] **Step 1: Replace file contents with:**

```tsx
import { LandingNav } from "./_components/LandingNav";
import { LandingHero } from "./_components/LandingHero";
import { LandingPillars } from "./_components/LandingPillars";
import { LandingModules } from "./_components/LandingModules";
import { LandingIndustries } from "./_components/LandingIndustries";
import { LandingStats } from "./_components/LandingStats";
import { LandingShowcase } from "./_components/LandingShowcase";
import { LandingWorkflow } from "./_components/LandingWorkflow";
import { LandingCTA } from "./_components/LandingCTA";
import { LandingFooter } from "./_components/LandingFooter";

export default function LoginLandingPage() {
  return (
    <main className="min-h-screen bg-paper text-charcoal font-sans">
      <LandingNav />
      <LandingHero />
      <LandingPillars />
      <LandingModules />
      <LandingIndustries />
      <LandingStats />
      <LandingShowcase />
      <LandingWorkflow />
      <LandingCTA />
      <LandingFooter />
    </main>
  );
}
```

- [ ] **Step 2: `pnpm tsc --noEmit`** → clean. Reload `/auth/login`. Every section should render in sequence. Sign in with `admin` / `admin123` against the running backend — should redirect to `/`.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/auth/login/page.tsx
git commit -m "feat(login): compose landing page from section components"
```

---

### Task 3.13: Delete `landing.css`

**Files:**
- Delete: `frontend/src/app/auth/login/landing.css`

- [ ] **Step 1: Verify it's not imported anywhere else**

```bash
cd frontend && grep -rln 'landing.css' src/
```
Expected: only `src/app/auth/login/page.tsx` (if the old import lingered) or no results. Remove any lingering import. Then:

- [ ] **Step 2: Delete the file**

```bash
cd frontend && git rm src/app/auth/login/landing.css
```

- [ ] **Step 3: Visually verify** `/auth/login` still renders correctly (no unstyled flash of content).

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(login): delete landing.css — styles migrated to Tailwind + shared tokens"
```

---

### Task 3.14: Phase 3 verification + final smoke

- [ ] **Step 1: lint + full Playwright suite + manual walk-through**

```bash
cd frontend && pnpm lint && pnpm tsc --noEmit && pnpm test:e2e
```

Manual: walk Dashboard → Projects list → Login/landing → 3 inherited pages (Portfolios, Resources, Admin/Users) and verify the White & Gold identity is consistent across the whole product.

- [ ] **Step 2: Commit any fixes as isolated commits.** Redesign is done when the gates pass.

---

## Self-review checklist

Run through the spec one more time against this plan:

- [ ] Spec §5.1 palette — every token appears in the `@theme inline` block AND `:root` in Task 1.3. ✅
- [ ] Spec §5.2 typography — Fraunces/Inter/JetBrains Mono loaded in Task 1.2, referenced via `--font-display/sans/mono` in Task 1.3. ✅
- [ ] Spec §5.3–§5.6 spacing/radii/elevation/motion — applied in primitives (Tasks 1.6–1.12) and in globals (Task 1.3). ✅
- [ ] Spec §6 primitives — all 7 rebuilt in Tasks 1.6–1.12. ✅
- [ ] Spec §7 shell — Sidebar and Header in Tasks 1.13–1.14. ✅
- [ ] Spec §8 hero pages — Dashboard (Task 2.2), Projects list (Task 2.3), Landing (Tasks 3.1–3.13). ✅
- [ ] Spec §9 implementation plan — 3 PRs mapped to Phases 1/2/3. ✅
- [ ] Spec §10 testing — Playwright gates at end of each phase. ✅
- [ ] Spec §14 known follow-ups — NOT in this plan by design. ✅
- [ ] No `TODO` / `TBD` / placeholder steps. ✅
- [ ] Every code step has a complete code block. ✅
- [ ] Every task ends in a commit. ✅
- [ ] Type and prop-name consistency between tasks (Button variants, Badge variants, Progress variants, Kpi tones, statusVariant helper duplicated in both `(app)/page.tsx` and `(app)/projects/page.tsx` for isolation — that duplication is intentional). ✅

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-24-white-gold-redesign.md`. Two execution options:

**1. Subagent-Driven (recommended)** — a fresh subagent per task, with review checkpoints between tasks. Best for a plan this size because each task is large enough to want an independent review before committing.

**2. Inline Execution** — execute tasks in this session using `superpowers:executing-plans`, batching with checkpoints for review. Faster but less granular review.

**Which approach?**
