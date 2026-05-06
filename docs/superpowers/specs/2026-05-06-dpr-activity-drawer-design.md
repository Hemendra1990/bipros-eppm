# DPR Activity Drawer

**Date:** 2026-05-06
**Status:** Approved (pending implementation plan)

## Background

Today, on `/projects/[projectId]/dpr`, clicking **Add Activity** (or **Edit** on a row) toggles `showForm` and renders `DprActivityForm` inline directly below the sticky page header (`frontend/src/app/(app)/projects/[projectId]/dpr/page.tsx:230-246`). The form is 584 lines and contains seven distinct sections: header/meta, location/chainage, activity link, BOQ link, manpower / equipment / material grids, and safety/delay. Rendered inline it pushes the day list far down the page and competes for vertical room with the sticky filter bar.

## Goal

Replace the inline form with a right-side slide-in **drawer**, keeping the day list visible underneath through a translucent backdrop and giving the form full viewport height for its many sections.

## Non-goals

- No changes to the DPR form's fields, validation, payload shape, or save/cancel callbacks.
- No changes to the DPR list, filters, sticky header, or AI insights panel.
- No general modal/drawer overhaul of the rest of the app — but the new `Drawer` is built generically so other pages can adopt it later.

## User-visible behavior

- Clicking **Add Activity** slides a panel in from the right (200ms transition) over a translucent backdrop. The day list and sticky header remain visible underneath.
- Clicking **Edit** on a DPR row opens the same drawer with the row's data prefilled.
- Drawer header shows **"Add Activity"** or **"Edit Activity"** plus an X close button.
- Drawer body is scrollable; the form's existing Save/Cancel buttons live at the bottom of the body.
- **Strict close behavior**: backdrop click and ESC do **not** dismiss the drawer. Only the X button or the form's Cancel button closes it. Rationale: forms have many fields and grid rows; accidental dismissal loses substantial typed data.
- Page body scroll is locked while the drawer is open.

## Components

### 1. `Drawer` (new, generic) — `frontend/src/components/common/Drawer.tsx`

Reusable right-side drawer. Props:

```ts
interface DrawerProps {
  open: boolean;
  onClose: () => void;
  title: string;
  widthClass?: string;        // default "max-w-2xl" (≈672px)
  children: React.ReactNode;
}
```

Behavior:

- Renders into `document.body` via `createPortal` so it overlays the entire page (not clipped by the sticky header's stacking/transform context).
- Always mounted while `open` is true; uses CSS transitions on `translate-x-full` ↔ `translate-x-0` for slide-in/out (200ms `ease-out`).
- Backdrop: `fixed inset-0 bg-charcoal/40 z-40`, click is a no-op (strict close).
- Panel: `fixed inset-y-0 right-0 z-50 w-full <widthClass> bg-paper shadow-xl flex flex-col`.
- Header (sticky inside panel): title left, X close button right, `border-b border-hairline px-5 py-3`.
- Body: `flex-1 overflow-y-auto`.
- While `open`: sets `document.body.style.overflow = "hidden"` on mount, restores prior value on unmount.
- ESC key: no listener (intentional — strict close).
- Visual style follows the existing `Dialog` component (`frontend/src/components/ui/dialog.tsx`): `bg-paper`, `text-charcoal`, hairline borders, gold accent on close hover.

### 2. `DprActivityForm` adjustments — `frontend/src/components/dpr/DprActivityForm.tsx`

- Remove the outer `<Card variant="elevated" className="p-0">` wrapper (line 227) and its top header row containing the title + X close button (≈lines 228-249). The drawer now owns the title and X.
- Keep all section dividers (`border-t border-hairline`) and section bodies as-is.
- Keep the existing Save/Cancel button row at the bottom of the form, unchanged. (No move to a sticky footer; users scroll to submit, matching today's flow.)
- Replace the outer wrapper with a plain `<form>` so its content fills the drawer body. Adjust horizontal padding so internal `px-5` sections still align (drawer body has no extra padding; form sections keep their `px-5 py-4`).
- Form `Props` interface, callbacks, and exported name remain unchanged. No call sites except the DPR page.

### 3. DPR page wiring — `frontend/src/app/(app)/projects/[projectId]/dpr/page.tsx`

- Import the new `Drawer`.
- Replace the inline render block (lines 230-246):

  ```tsx
  {showForm && (
    <div className="mb-6">
      <DprActivityForm key={...} editing={editing} ... />
    </div>
  )}
  ```

  with:

  ```tsx
  <Drawer
    open={showForm}
    onClose={closeForm}
    title={editing ? "Edit Activity" : "Add Activity"}
  >
    <DprActivityForm
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

- The `Drawer` is always rendered so its enter/exit transition can run; `open` controls visibility. The `key` on the form forces a remount when switching between new and edit (or between two different rows being edited), preserving today's behavior of reseeding `useState` initializers.

## Data flow

Unchanged. `DprActivityForm` calls `onSave(payload)` → page calls `dprApi.create` / `dprApi.update` → invalidates `["dpr", projectId, from, to]` and `["boq", projectId]` → calls `closeForm()` → drawer slides out.

## Edge cases

- **Long form**: drawer body is `overflow-y-auto`; the form's existing in-form error message at the top remains visible after scrolling back up.
- **Switch from edit row A to edit row B**: clicking Edit on row B updates `editing` → the `key` changes → form remounts with row B's seeded state. Drawer stays open; no slide animation re-trigger because `open` stays true.
- **Save error**: form keeps its existing error display; drawer stays open (no auto-close on failure since `closeForm()` is only called by `handleSave` after `await dprApi.create/update` resolves).
- **Browser back/route change while drawer open**: the page unmounts; drawer cleanup restores body scroll via its unmount effect.
- **z-index**: drawer panel `z-50` and backdrop `z-40` sit above the existing sticky page header (`z-20`) and the AI insights panel.
- **Mobile / narrow viewports**: panel is `w-full max-w-2xl`, so on screens narrower than 672px it occupies the full width — acceptable, matches drawer behavior in similar enterprise apps.

## Risks

- **Stacking context**: if the sticky header's `backdrop-blur` creates a containing block that swallows `position: fixed`, the drawer would clip. Mitigation: the drawer renders through a portal to `document.body`, sidestepping this.
- **Body scroll lock during nested drawers**: not an issue here — only one drawer at a time on this page, and the lock is reference-counted by being tied to mount/unmount.

## Testing

Manual verification on `/projects/[projectId]/dpr`:

1. Click **Add Activity** → drawer slides in from the right; backdrop appears; day list visible underneath.
2. Clicking the backdrop does nothing; ESC does nothing.
3. Clicking the X or Cancel closes the drawer.
4. Click **Edit** on a DPR row → drawer opens with row data prefilled; clicking Edit on a different row swaps the form contents without closing.
5. Save a new DPR → drawer closes; new row appears in the list.
6. Save fails (e.g., disconnect backend) → form shows error; drawer stays open.
7. While drawer is open, page body scroll is locked; closing restores it.
8. Sticky page header remains visible behind the backdrop with correct z-order.
9. On a narrow viewport (≤672px), drawer occupies full width.

No new automated tests required — this is a presentation refactor with unchanged form behavior.

## Out of scope / follow-ups

- Adopting `Drawer` for other heavy forms (resource detail, risk activity assignment, document folder editing, etc.). Each can migrate independently in a future change.
- Confirm-on-dirty-close pattern (warn before closing if the form has unsaved edits). Could be added later if users report accidental Cancel-clicks; current strict close already prevents the most common accidents.
