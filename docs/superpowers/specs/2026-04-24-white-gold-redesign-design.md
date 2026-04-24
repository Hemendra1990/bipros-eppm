# Bipros EPPM — White & Gold frontend redesign

**Date:** 2026-04-24
**Owner:** @hemendra
**Status:** Approved design, ready for implementation planning
**Stack in scope:** `frontend/` — Next.js 16, React 19, Tailwind v4, pnpm

## 1. Overview

Transform the Bipros EPPM frontend from a generic dark-by-default SaaS aesthetic into a premium White + Gold identity (editorial-luxury tier — think Financial Times or a private-bank dashboard, not Rolex-ornate). The goal is visual quality on par with Apple's public UI and premium fintech, not flashiness.

The work is deliberately scoped so that **~80% of the redesign ships through tokens + primitives + shell**, with three "hero" pages (Dashboard, Projects list, Login/landing) polished as the pattern reference. Every other page under `src/app/(app)/**` inherits the new look automatically and receives a later polish sweep outside this spec.

## 2. Goals

- Land a cohesive luxury identity across the authenticated app and the public marketing/login surface.
- Centralise design decisions in token-level primitives so future screens inherit the identity for free.
- Preserve existing information architecture, data flows, and behaviour — this is a **visual** redesign, not a functional one.
- Ship as three reviewable PRs (Foundation → Hero pages → Landing), each independently revertable.

## 3. Non-goals (out of scope)

- **Dark-mode repaint.** `.dark` tokens in `globals.css` stay as-is. `ThemeProvider` default flips from `"dark"` to `"light"`; users who explicitly toggle dark still get the current blue-accent dark UI. Harmonising dark is a separate future pass.
- **Admin sub-pages and other routes under `(app)/**` beyond Dashboard + Projects list** — they inherit the new tokens and primitives automatically. A follow-up sweep (separate spec) tidies any hardcoded colours.
- **New auth routes** (register, forgot-password) — none exist today and we are not adding any.
- **Chart libraries** — the Dashboard currently uses none; not adding any.
- **Visual regression tooling** — recommended but not in scope.
- **Backend or API changes.**
- **Mobile-specific responsive rework** beyond the existing Tailwind breakpoints.

## 4. Design decisions (locked)

| Decision | Chosen option | Notes |
|---|---|---|
| Scope | A — Foundation + hero screens | Token swap + primitive rebuild + shell redesign + three hero pages. |
| Gold intensity | B — Editorial | Gold appears as a permanent-but-disciplined presence: hairline under the header, 3px side-rail on flagged cards, gold-bronze uppercase labels. Not Whisper-minimal, not Monogram-ornate. |
| Typography pairing | B — Fraunces + Inter + JetBrains Mono | Fraunces (variable, optical-size 9–144) for display and big numbers. Inter for body/UI. JetBrains Mono for IDs and WBS codes. |
| Login surface | B — Repaint & keep | The 28KB marketing landing at `/auth/login` is preserved structurally; all 11 sections are repainted in white+gold. `landing.css` is deleted; styles move into Tailwind + shared tokens. |
| Dark-mode treatment | Deferred | See §3. |

## 5. Design system

### 5.1 Palette

Warm-neutral base + two-stop gold accent + muted jewel-tone semantics. Semantic colours are deliberately desaturated so the UI reads expensive even on a row flagged overdue.

| Role | Hex | CSS token | Usage |
|---|---|---|---|
| Paper | `#FFFFFF` | `--color-bg` | Primary surface |
| Ivory | `#FAF9F6` | `--color-bg-muted` | Page canvas, hover fills, header backgrounds |
| Parchment | `#F5F2E8` | `--color-bg-sunken` | Disabled inputs, progress-bar tracks |
| Charcoal | `#1C1C1C` | `--color-text` | Primary text |
| Slate | `#6B7280` | `--color-text-muted` | Secondary text, table cell meta |
| Ash | `#9CA3AF` | `--color-text-subtle` | Placeholders, captions |
| Hairline | `#EDE7D3` | `--color-border` | Card borders, dividers (warm) |
| Divider | `#E5E7EB` | `--color-border-subtle` | Input borders (cool-neutral) |
| Gold-tint | `#F5E7B5` | `--color-gold-tint` | Badge fills, active-icon pill, avatar ring |
| Gold | `#D4AF37` | `--color-gold` | Primary action, accent rail, icon stroke |
| Gold-deep | `#B8962E` | `--color-gold-deep` | Hover state for gold, kicker labels |
| Bronze | `#8C6F1E` | `--color-gold-ink` | Text on gold-tint backgrounds |
| Emerald | `#2E7D5B` | `--color-success` | Active/on-time |
| Bronze-warn | `#C7882E` | `--color-warning` | At-risk (visually distinct from brand gold) |
| Burgundy | `#9B2C2C` | `--color-danger` | Overdue, destructive actions |
| Steel | `#475569` | `--color-info` | Archived, neutral-informational |

### 5.2 Typography

- **Display / big numbers:** Fraunces (variable), weight 600, optical-size axis used at 96 for section heads and 144 for hero.
- **Body / UI:** Inter, 400 body / 500–600 labels.
- **Mono:** JetBrains Mono, 500, for IDs, WBS codes, keyboard shortcuts, tabular numbers in tables.

**Scale:**

| Token | Face | Size / line-height | Primary use |
|---|---|---|---|
| `display/xl` | Fraunces 600, opsz 144 | 64 / 1.05 | Landing hero h1 |
| `display/lg` | Fraunces 600, opsz 96 | 40 / 1.1 | Page h1 |
| `heading/lg` | Fraunces 600 | 24 / 1.2 | Section headings |
| `heading/md` | Inter 600 | 18 / 1.3 | Card titles, dialog heading |
| `body/md` | Inter 400 | 15 / 1.6 | Primary body |
| `body/sm` | Inter 400 | 13 / 1.5 | Table cells, secondary copy |
| `label` | Inter 600, tracked .14em | 11 / 1 uppercase | Kickers, column headers, chip labels |
| `numeric/display` | Fraunces 600, `tnum` | 38 / 1 | KPI values |
| `mono` | JetBrains Mono 500 | 12 / 1.3 | Project codes, WBS |

### 5.3 Spacing

4px base. Tailwind default scale — no override. Pages use generous whitespace: 28–32px around page chrome, 18–22px inside cards.

### 5.4 Radii

- `6px` — chip, badge
- `10px` — input, button
- `12px` — card, pill, toolbar control
- `16px` — dialog, sheet

### 5.5 Elevation

| Token | Shadow | Use |
|---|---|---|
| `--shadow-card` | `0 1px 2px rgba(28,28,28,.04)` | Card at rest |
| `--shadow-lifted` | `0 4px 20px rgba(28,28,28,.05)` | Card hover, sign-in card |
| `--shadow-overlay` | `0 20px 40px rgba(28,28,28,.08)` | Dialog, dropdown, command palette |
| `--shadow-gold` | `0 4px 14px rgba(212,175,55,.30)` | **Primary-CTA hover only** |

### 5.6 Motion

One easing curve, three durations. `prefers-reduced-motion` respected — all transitions honour the `@media (prefers-reduced-motion: reduce)` guard.

- `--ease-out: cubic-bezier(.2, .7, .2, 1)`
- `--duration-micro: 120ms` — hover colour, focus ring
- `--duration-std: 200ms` — buttons, cards, tabs
- `--duration-lg: 320ms` — dialogs, drawers, command palette

### 5.7 Tailwind v4 token exposure

All tokens above live in `frontend/src/app/globals.css` under an `@theme` block so Tailwind v4 auto-generates the utility classes (`bg-gold`, `text-charcoal`, `shadow-gold`, etc.). The existing `.dark { ... }` selector stays untouched.

Reference block (authoritative):

```css
@theme {
  --color-bg:            #FFFFFF;
  --color-bg-muted:      #FAF9F6;
  --color-bg-sunken:     #F5F2E8;
  --color-text:          #1C1C1C;
  --color-text-muted:    #6B7280;
  --color-text-subtle:   #9CA3AF;
  --color-border:        #EDE7D3;
  --color-border-subtle: #E5E7EB;

  --color-gold:          #D4AF37;
  --color-gold-deep:     #B8962E;
  --color-gold-ink:      #8C6F1E;
  --color-gold-tint:     #F5E7B5;

  --color-success:       #2E7D5B;
  --color-warning:       #C7882E;
  --color-danger:        #9B2C2C;
  --color-info:          #475569;

  --font-display:        var(--font-fraunces);
  --font-sans:           var(--font-inter);
  --font-mono:           var(--font-jetbrains);

  --radius-sm: 6px; --radius-md: 10px; --radius-lg: 12px; --radius-xl: 16px;

  --shadow-card:    0 1px 2px rgba(28,28,28,.04);
  --shadow-lifted:  0 4px 20px rgba(28,28,28,.05);
  --shadow-overlay: 0 20px 40px rgba(28,28,28,.08);
  --shadow-gold:    0 4px 14px rgba(212,175,55,.30);

  --ease-out:       cubic-bezier(.2,.7,.2,1);
  --duration-micro: 120ms;
  --duration-std:   200ms;
  --duration-lg:    320ms;
}
```

## 6. Component primitives

Seven shared components under `frontend/src/components/ui/` get rebuilt:

### 6.1 `button.tsx`

Variants: **primary** (gold fill, white text, `--shadow-gold` + 1px translateY on hover), **secondary** (white fill, gold hairline border, gold-deep text), **ghost** (no chrome at rest, ivory on hover), **danger** (burgundy fill). Sizes: `sm` (h-30, r-8), `md` (h-38, r-10), `lg` (h-46, r-12). Icon-only variants at square dimensions. Disabled state: parchment fill, ash text, no hover.

### 6.2 `card.tsx`

Variants: **flat** (hairline only), **elevated** (`--shadow-card`), **interactive** (hover adds `--shadow-lifted` + 2px lift + warmer border), **accent** (3px gold left-rail, reserved for flagged/featured items). All 12px radius, 18–22px padding.

### 6.3 `dialog.tsx`

16px radius, `--shadow-overlay`, Fraunces headline (22/1.2, opsz 96), ivory footer with hairline top divider, 24px padding. Close button top-right in slate, gold on hover. Destructive confirmations use the danger button in the primary slot.

### 6.4 `input.tsx` (and sibling form controls)

**Label above the input, never placeholder-only.** 10px radius, 40px height, 1px `--color-border-subtle` at rest. Hover: `#D1C7A0` (warm hairline). Focus: 1px gold border + 3px gold-tint glow (`0 0 0 3px rgba(212,175,55,.18)`). Error: burgundy border + matching low-opacity ring, with burgundy 11px error message below. Disabled: parchment fill, ash text. Same pattern on `<select>`, `<textarea>`, date inputs.

### 6.5 `badge.tsx`

Tinted background + matching hairline — never solid fills. Variants: **neutral** (ivory + charcoal), **gold** (gold-tint + bronze, for "featured"), **success**, **warning**, **danger**, **info**. Optional leading 6px dot in `currentColor`.

### 6.6 `progress.tsx`

6px track on parchment, 999px radius, gradient fill. Variants: **default** (gold→gold-deep), **success**, **warning**, **danger**. Optional label row above: name on the left in slate, value on the right in charcoal JetBrains Mono with `tabular-nums`.

### 6.7 `table.tsx`

Ivory `<thead>` (not white), gold-bronze uppercase column labels tracked at .14em, 10px. Hairline row dividers (`--color-border`, slightly warmer than body text). **No zebra striping.** 46px row height. Row hover fades to ivory over 120ms. Project codes use JetBrains Mono in gold-deep. Numeric columns right-aligned, `tabular-nums`.

## 7. App shell

### 7.1 Sidebar — `frontend/src/components/common/Sidebar.tsx`

**Expanded (260px):**
- White canvas, `--color-border` right edge.
- **Brand block** at top: 28px gold-gradient mark + Fraunces wordmark + small uppercase "EPPM" tag in bronze.
- **Workspace picker** below brand: ivory pill showing current org with caret.
- **Nav, grouped** in three sections: `Plan` (Dashboard, Portfolios, Projects, EPS), `Execute` (Scheduling, Resources), `Control` (Cost & EVM, Risk, OBS, Reports). Group headings are 9px label-kicker in ash.
- **Active state:** 3px gold left-rail, faint gold gradient wash (`rgba(212,175,55,.09) → transparent`), gold icon, charcoal weight-600 text. No loud fills.
- **Counts** on nav rows: JetBrains Mono 10px in ash at rest; wrapped in a gold-tint pill when the row is active.
- **User chip** pinned at bottom above a hairline: 30px avatar in gold-tint with a 2px gold ring, name + role kicker, trailing caret.

**Collapsed (64px):**
- Icon-only rail. Active icon keeps the gold left-rail plus a gold-tint pill behind the icon. Hover shows a floating label popover anchored to the right.

### 7.2 Header — `frontend/src/components/common/Header.tsx`

64px tall, white bg, hairline `--color-border` bottom, plus a faint gold-gradient hairline overlay (`linear-gradient(90deg,transparent,#D4AF37,transparent)` at 40% opacity).

- **Left:** breadcrumbs — slate with gold-deep hover, gold caret separators, final crumb in charcoal weight-600.
- **Centre:** command-palette search — 38px ivory pill, magnifying-glass icon, `⌘K` hint rendered in a JetBrains Mono kbd chip. Opens a palette (dialog primitive) on press.
- **Right:** notification bell (gold dot for unread), help icon, vertical hairline, primary `+ New project` CTA.

## 8. Hero pages

### 8.1 Dashboard — `frontend/src/app/(app)/page.tsx`

Layout (top to bottom):
1. **Page head:** dated kicker (`Q2 · April 24, 2026`), Fraunces h1 (`Portfolio dashboard`), 1-line lede, right-aligned actions (`Export` secondary + `+ New project` primary).
2. **Status KPI row** (4 columns): Planned / Active / Completed / Resources. Uses the card primitive with a 10px label-kicker, Fraunces 38 value, and slate meta line with a coloured delta number.
3. **Activity KPI row** (3 columns): Total activities / Critical path / Overdue. The critical card uses the `warning` variant (`C7882E` left-rail); the overdue card uses `critical` (burgundy left-rail).
4. **Recent projects table** with a section heading and a right-aligned "View all projects →" link. Uses the table primitive.
5. **Jump back in** — 4 quick-action tiles using the card primitive's `interactive` variant. Each tile has a gold-gradient icon badge, Fraunces 18 title, slate one-liner, and a ghost-arrow bottom-right that brightens and nudges on hover.

Existing data flow (TanStack Query 60s stale, `/v1/projects`, per-project activities loop) is **preserved** — only presentational code changes. The `TabTip` component is restyled to match the editorial tone (kicker label + Fraunces heading + slate body), not removed.

### 8.2 Projects list — `frontend/src/app/(app)/projects/page.tsx`

Layout:
1. **Page head:** counter kicker (`247 active · 34 planned · 128 completed`), Fraunces h1 (`Projects`), lede, right-aligned `Export CSV` + `+ New project`.
2. **Toolbar row:** flex row with a 340px max-width search pill and two select pills (`Status`, `Priority`). Both filters are client-side — status exists today; priority is new but filterable against the already-displayed `priority` field without any API change. State lives in React `useState` (`searchQuery`, `statusFilter`, `priorityFilter`), applied via `useMemo` over the project list, matching the existing pattern.
3. **Active filter chips row** (conditional): gold-tint chips with a close affordance, plus a "Clear all" link in gold-deep.
4. **Table** with the 7 columns from the anatomy report (Code / Name / Status / Start / Finish / Priority / Actions). Rows are hover-clickable to `/projects/{id}`. Trailing Edit and Delete icon buttons visible on row hover, gold-deep on hover.
5. **Footer row:** `Showing 6 of 247 · Load more →` — keeps the current fixed-page-50 behaviour but adds a "Load more" affordance (increases the size param; no URL pagination). The inline `StatusBadge` becomes the shared `Badge` primitive.

### 8.3 Login / landing — `frontend/src/app/auth/login/page.tsx`

All 11 sections from the current page are preserved in structure and copy intent; each is rewritten in Tailwind + shared primitives. `landing.css` is deleted.

1. **Nav** — sticky, white, 72px padding-x, hairline + gold-gradient underline; left: mark + Fraunces wordmark; centre: `Platform · Industries · Customers · Pricing · Resources`; right: `Sign in` ghost + `Request demo` primary.
2. **Hero** — 1.25fr / 1fr split on `linear-gradient(180deg,#FFFFFF,#FAF9F6)` with a gold radial glow top-right. Left: `ENTERPRISE PPM` kicker, Fraunces h1 with the last word in italic gold (`Run every infrastructure programme as one.`), 17px lede, two large CTAs, social-proof row (avatar stack + 4.8★ G2 + "400+ teams"). Right: sign-in card (16px radius, lifted shadow) with Fraunces "Welcome back" heading, three SSO buttons (Google/Microsoft/SSO), "or with email" hairline divider, email/password inputs using the primitive, remember + forgot row, full-width primary sign-in button, and a trust-chip row (`SOC 2 · ISO 27001 · SSO / SAML`) rendered in JetBrains Mono.
3. **Platform pillars** — ivory section, 3 columns, each a card with a 3px gold left-rail, Roman-numeral kicker (`I · PLAN` etc.), gold-tinted icon badge, Fraunces 22 title, slate body, gold-deep "learn more →" link.
4. **Modules (dark)** — charcoal (`#141414`) canvas with a gold hairline top edge. Centre-aligned head with gold kicker, Fraunces h2 in white with one italic gold word, warm gold-tint lede. 3×3 grid of tiles, each with `M01`–`M09` JetBrains Mono number, gold icon badge, Fraunces 18 title in white, warm-gold body. Hover brightens the gold border + tiny lift.
5. **Industries** — ivory. 4 cards with a dark 110px art panel (`linear-gradient(180deg,#141414,#0B0B0B)` + a radial gold bloom) containing a stroked gold line-art illustration; body shows a `01`–`04` JetBrains Mono label, Fraunces 19 name, slate one-liner.
6. **Stats strip** — white, bordered top and bottom, 4 columns: `$42B / 1,800+ / 32% / 99.95%` in Fraunces 48 gold-deep with 11px slate label-kicker beneath.
7. **Master-schedule showcase** — ivory, 2-column. Left: kicker + Fraunces h2 (italic gold emphasis) + 4 feature bullets with gold checkmarks + secondary CTA. Right: a Gantt mock (list of activities with gradient bars in the four semantic colours on an ivory track).
8. **Workflow** — white, centre-aligned heading, 4 steps in a row connected by a dashed gold line. Each step: 48px white circle with 2px gold ring and a Fraunces Roman numeral inside, Fraunces 18 title, slate one-liner.
9. **CTA band** — charcoal gradient with a centred gold radial bloom. Gold kicker, Fraunces 42 headline in white with italic gold emphasis, 15px warm-gold lede, primary (`Request demo →`) + gold-outline-on-dark (`Download the whitepaper`) CTAs.
10. **Footer** — ivory, 5-column grid: 1.3fr brand column (Fraunces wordmark, description, three 32px social buttons) + 4 link columns with gold-kicker `h6`s and charcoal links with gold-deep hover. Below, a hairline divider, then a flex legal row with copyright left, `Privacy · Terms · Security · SOC 2` right.

Section rhythm alternates ivory → dark → ivory so the eye never fatigues on one value.

## 9. Implementation plan

### 9.1 Fonts — `frontend/src/app/layout.tsx`

- **Remove:** Geist Sans and Geist Mono imports.
- **Add** via `next/font/google`:
  - `Fraunces` — weights 500/600/700 + italic 500/600, with `axes: ['opsz']` so optical-size 96 and 144 render correctly at hero scale. Variable: `--font-fraunces`.
  - `Inter` — weights 400/500/600/700. Variable: `--font-inter`.
  - `JetBrains_Mono` — weights 400/500. Variable: `--font-jetbrains`.
- `display: 'swap'`, subset `latin`.
- Attach all three `.variable` class names to `<html>` so the `@theme` block resolves them.
- **Delete** the Google Fonts `@import` at the top of `landing.css` (the landing page reuses the globally loaded fonts).

### 9.2 Theme provider — `frontend/src/components/theme/ThemeProvider.tsx`

Flip `defaultTheme="dark"` to `defaultTheme="light"`. System-preference detection stays enabled. The existing `.dark { ... }` block in `globals.css` is untouched — users who explicitly toggle dark still get the current blue dark UI.

### 9.3 Migration — three sequential PRs

**PR 1 — Foundation**
`globals.css`, `layout.tsx` (root), `ThemeProvider.tsx`, all seven files under `src/components/ui/`, `src/components/common/Sidebar.tsx`, `src/components/common/Header.tsx`, `src/app/(app)/layout.tsx` (any chrome-width adjustments).

After PR 1 merges, every page in the app re-renders in the new palette, type, and primitives. Most pages will already look ~80% correct; remaining polish is follow-up work.

**PR 2 — Hero pages (app-side)**
`src/app/(app)/page.tsx` (Dashboard), `src/components/common/TabTip.tsx`, `src/app/(app)/projects/page.tsx` (Projects list). Extract the inline `StatusBadge` and replace with the shared `Badge` primitive.

**PR 3 — Landing repaint**
Rewrite `src/app/auth/login/page.tsx` in Tailwind + shared primitives across all 11 sections. **Delete** `src/app/auth/login/landing.css`.

PR 2 and PR 3 can be drafted in parallel after PR 1 lands but merge after it.

### 9.4 Dark mode

Deferred. The `.dark` selector in `globals.css` is preserved as-is. A later spec harmonises it with the new gold identity.

## 10. Testing

- **Playwright e2e** — tests under `frontend/e2e/tests/` pass unchanged (they assert on roles and text, not colours or classes). CI-gated on every PR.
- **Manual visual QA** — after PR 1 merges, walk through 8 representative inherited pages (one per top-level `(app)/*` section) to catch hardcoded colours and `blue-*` Tailwind classes. Log findings as follow-up tickets, not blockers.
- **Visual regression snapshots** — recommended baseline after PR 3 lands, to protect against future drift. Out of scope for this spec.

## 11. Rollback

Each PR is independently revertable via `git revert`. PR 1 carries the highest blast radius because it touches global tokens; treat it as a dedicated deployment with a quick visual smoke test before merging PRs 2 and 3. Do **not** retain old blue tokens as commented-out blocks — use git history.

## 12. Risks

| Risk | Mitigation |
|---|---|
| Tailwind v4 `@theme` directive edge cases | Verify locally before PR 1: `bg-gold`, `text-charcoal`, `shadow-gold` utility classes must resolve. If any token name collides with a Tailwind default, rename. |
| `next/font` + Fraunces optical-size axis | Next.js 16 supports `axes: ['opsz']` on `next/font/google`. Verify the variable font loads with the axis exposed before committing hero type scale. |
| Inherited pages with hardcoded `#2563eb` or `blue-*` classes | Expected. Addressed in the post-PR-1 follow-up sweep — not a blocker for this redesign. |
| `.dark` variant looking out of sync with new light identity | Accepted per §3. Communicated in release notes; dark-mode repaint queued as a follow-up spec. |
| Large single-file change in PR 3 (login rewrite) | Split review: each of the 11 landing sections is a distinct component-level block. Reviewer can walk them top to bottom. |

## 13. Acceptance checklist

- [ ] Dashboard, Projects list, and Login/landing render per §8 at desktop breakpoints (1280+).
- [ ] All 7 UI primitives (`button`, `card`, `dialog`, `input`, `badge`, `progress`, `table`) expose the variants listed in §6 and pass existing e2e tests.
- [ ] Sidebar and Header match §7 in both expanded and collapsed states.
- [ ] `landing.css` is deleted; no references remain.
- [ ] Geist fonts are removed; Fraunces + Inter + JetBrains Mono are loaded via `next/font/google`.
- [ ] `ThemeProvider` default is `"light"`.
- [ ] `.dark` tokens in `globals.css` are unchanged.
- [ ] Playwright e2e suite passes on each PR.

## 14. Known follow-ups (out of this spec)

1. **Inherited-page polish sweep** — survey every route under `src/app/(app)/**` besides Dashboard and Projects list for hardcoded colours or `blue-*` classes. Own spec.
2. **Dark-mode harmonisation** — repaint the `.dark` tokens in the new gold identity. Own spec.
3. **Visual regression baseline** — add Playwright screenshot tests. Own spec.
4. **Admin sub-pages** — any admin screen with bespoke inline styling will surface in the sweep above.
