# Virtualized Data Tables Design Spec

**Date:** 2026-05-05  
**Status:** Approved  
**Scope:** All tables across the Bipros EPPM frontend  

---

## 1. Problem Statement

The current frontend uses basic HTML `<table>` elements styled with Tailwind CSS. With datasets reaching **thousands of rows**, this causes:
- **DOM bloat** — all rows render even when off-screen
- **Scroll lag** — unresponsive scrolling on large lists
- **Inconsistent UX** — ~30 inline tables with varying patterns, no shared behavior
- **Missing features** — no column resizing, no sticky headers, limited sorting/filtering

The reference screenshot shows a modern, polished dark-themed list view with gold accents that the current tables do not match.

## 2. Goals

1. **Virtual scrolling** — render only visible rows for 10,000+ row datasets
2. **Unified table system** — one component family for all tables
3. **Theme consistency** — match existing dark + gold application theme
4. **Rich interactions** — sticky headers, column resizing, sorting, filtering
5. **Backward compatibility** — migrate incrementally without breaking existing pages

## 3. Non-Goals

- Server-side pagination (APIs currently return full datasets)
- Inline cell editing (out of scope — forms exist for editing)
- Drag-and-drop row reordering
- Excel export (already handled elsewhere)

## 4. Architecture

### 4.1 Stack

| Layer | Library | Version | Responsibility |
|-------|---------|---------|----------------|
| Table State | `@tanstack/react-table` | ^8.21.3 | Sorting, filtering, column sizing, row selection |
| Virtualization | `@tanstack/react-virtual` | ^3.x | Windowed rendering — only visible rows in DOM |
| Data Fetching | `@tanstack/react-query` | ^5.96.2 | Already in use |
| UI Styling | Tailwind CSS v4 | ^4 | Utility-first styling with custom tokens |

**Note:** `@tanstack/react-table` is already installed in `package.json` but unused. `@tanstack/react-virtual` needs to be added.

### 4.2 Component Hierarchy

```
VirtualDataTable
├── TableHeader (sticky)
│   ├── ColumnHeader (sort indicator + resize handle)
│   └── FilterRow (optional column filters)
├── VirtualTableBody
│   └── VirtualRow (measured height)
│       └── TableCell (per column)
└── TableFooter (optional pagination info)
```

### 4.3 Custom Hook

`useVirtualTable<TData>` wires together TanStack Table + TanStack Virtual:

- Accepts: `data: TData[]`, `columns: ColumnDef<TData>[]`, table options
- Returns: `table` (TanStack Table instance), `virtualizer` (TanStack Virtual instance), `virtualRows`, `totalSize`, `scrollRef`

## 5. Component API

### 5.1 VirtualDataTable

```tsx
interface VirtualDataTableProps<TData> {
  data: TData[];
  columns: ColumnDef<TData>[];
  rowKey?: keyof TData | ((row: TData) => string);
  
  // Features
  sortable?: boolean;           // default: true
  filterable?: boolean;         // default: false
  resizable?: boolean;          // default: true
  searchable?: boolean;         // default: true (global search)
  selectable?: boolean;         // default: false (row selection)
  
  // Virtualization
  estimateRowHeight?: number;   // default: 48px
  overscan?: number;            // default: 5
  
  // Styling
  className?: string;
  headerClassName?: string;
  rowClassName?: string | ((row: TData) => string);
  
  // Events
  onRowClick?: (row: TData) => void;
  onRowDoubleClick?: (row: TData) => void;
  
  // Empty state
  emptyMessage?: string;
  
  // Loading
  isLoading?: boolean;
}
```

### 5.2 SimpleTable (for small static tables)

A lightweight wrapper for tables with <50 rows that don't need virtualization:

```tsx
interface SimpleTableProps<TData> {
  data: TData[];
  columns: ColumnDef<TData>[];
  sortable?: boolean;
  className?: string;
}
```

## 6. Migration Strategy

### 6.1 Table Categories

| Category | Criteria | Action | Count |
|----------|----------|--------|-------|
| **Large Data** | >100 rows or unknown size | Migrate to `VirtualDataTable` | ~15 |
| **Small Static** | <50 rows, rarely changes | Migrate to `SimpleTable` or keep existing | ~10 |
| **Complex/Tree** | Hierarchical, expand/collapse | Evaluate case-by-case | ~5 |

### 6.2 Migration Order

1. **Phase 1: Foundation** — Install `@tanstack/react-virtual`, build `useVirtualTable`, `VirtualDataTable`, `SimpleTable`
2. **Phase 2: Replace DataTable** — Swap `DataTable.tsx` usage in the ~15 pages that import it
3. **Phase 3: Inline Tables** — Migrate large inline tables; leave small ones as-is or wrap in `SimpleTable`
4. **Phase 4: Polish** — Column widths, row heights, theme refinements

### 6.3 Files to Migrate

**High priority (used by many pages):**
- `src/components/common/DataTable.tsx` → `src/components/common/VirtualDataTable.tsx`
- `src/components/ui/table.tsx` — update primitives

**Inline tables (evaluate per feature):**
- `src/components/labour-master/WorkerTable.tsx`
- `src/components/gis/ProgressVarianceTable.tsx`
- `src/components/baseline/ScheduleComparisonTable.tsx`
- `src/components/gis/GisLayerList.tsx`
- `src/components/contracts/AttachmentList.tsx`
- Report tables in `src/components/reports/`
- Dashboard tables in `src/components/dashboard/`

## 7. Styling

### 7.1 Theme Tokens (existing)

The design uses the existing Tailwind v4 custom properties:

```css
/* Existing tokens to use */
--color-paper        /* Table background */
--color-ivory        /* Header background */
--color-hairline     /* Borders */
--color-gold         /* Accent: sort indicator, selected row, hover */
--color-gold-deep    /* Active sort */
--color-gold-tint    /* Hover background */
--color-text-primary /* Primary text */
--color-text-secondary /* Secondary text */
--color-charcoal     /* Dark mode surface */
```

### 7.2 Row & Header Styling

- **Header row:** `bg-ivory dark:bg-charcoal sticky top-0 z-10`
- **Header text:** `text-[11px] font-semibold uppercase tracking-[0.10em] text-text-secondary`
- **Data rows:** `border-b border-hairline hover:bg-gold-tint/10`
- **Selected row:** `bg-gold/20`
- **Cell padding:** `px-4 py-3`
- **Sort indicator:** Gold chevron (up/down) in header
- **Resize handle:** `w-px bg-hairline hover:bg-gold cursor-col-resize`

### 7.3 Scroll Container

- `overflow-auto` on the table wrapper
- Sticky header via `position: sticky`
- Custom scrollbar styling matching the dark theme

## 8. Performance

### 8.1 Virtualization Behavior

- **Estimate row height:** 48px (default), customizable per table
- **Overscan:** 5 rows above and below viewport
- **Dynamic measurement:** Rows with variable content (multi-line text) are measured after mount
- **Scroll smoothing:** CSS `scroll-behavior: smooth` on the container

### 8.2 Sorting & Filtering

- Client-side only (since APIs return full datasets)
- Debounced global search: 150ms
- Column filters: per-column text input (optional)

## 9. Accessibility

- Semantic `<table>`, `<thead>`, `<tbody>`, `<tr>`, `<th>`, `<td>` elements
- `aria-sort` on sortable headers
- Keyboard navigation: Arrow keys to navigate rows, Enter to select/click
- Focus visible states using gold outline

## 10. Error Handling

- **Empty state:** Centered message with icon when `data.length === 0`
- **Loading state:** Skeleton rows (5 rows of pulsing blocks)
- **Error state:** Passed through from parent (React Query `isError`)

## 11. Testing Plan

### 11.1 Unit Tests

- `useVirtualTable` hook: verifies virtualizer returns correct range
- `VirtualDataTable`: renders headers, sorts on click, filters on input
- `SimpleTable`: renders without virtualization overhead

### 11.2 E2E Tests

- Scroll a table with 1,000 rows — verify smooth performance
- Resize a column — verify width persists
- Sort a column — verify order changes
- Filter — verify row count updates

## 12. Dependencies

### To Add
```json
{
  "@tanstack/react-virtual": "^3.0.0"
}
```

### Already Present
```json
{
  "@tanstack/react-table": "^8.21.3",
  "@tanstack/react-query": "^5.96.2",
  "tailwindcss": "^4",
  "tailwind-merge": "^3.5.0",
  "lucide-react": "^1.7.0"
}
```

## 13. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking existing table behavior | High | Maintain `DataTable` as deprecated wrapper during migration |
| Variable row heights cause jumpy scroll | Medium | Use dynamic measurement + overscan; test with worst-case content |
| Column resize state lost on unmount | Low | Persist widths to `localStorage` per table key |
| Team unfamiliar with TanStack Table APIs | Medium | Document common patterns; keep API surface small |

## 14. Success Criteria

- [ ] All tables with >100 rows use virtualization (no DOM bloat)
- [ ] Scroll remains at 60fps with 5,000+ rows
- [ ] Sticky headers work correctly
- [ ] Column resizing works
- [ ] Sorting/filtering works without page reload
- [ ] Visual design matches existing dark + gold theme
- [ ] No visual regressions on small tables (<50 rows)
- [ ] `pnpm lint` passes
- [ ] `pnpm build` passes

---

## Appendix A: Example Usage

### Before (Current)
```tsx
import { DataTable } from "@/components/common/DataTable";

const columns: ColumnDef<DprRow>[] = [
  { accessorKey: "date", header: "Date", sortable: true },
  { accessorKey: "supervisor", header: "Supervisor" },
  { accessorKey: "activity", header: "Activity" },
];

<DataTable columns={columns} data={rows} searchable rowKey="id" />
```

### After (Virtualized)
```tsx
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import { createColumnHelper } from "@tanstack/react-table";

const columnHelper = createColumnHelper<DprRow>();

const columns = [
  columnHelper.accessor("date", { header: "Date" }),
  columnHelper.accessor("supervisor", { header: "Supervisor" }),
  columnHelper.accessor("activity", { header: "Activity" }),
];

<VirtualDataTable
  columns={columns}
  data={rows}
  searchable
  sortable
  resizable
  estimateRowHeight={56}
  onRowClick={(row) => router.push(`/dpr/${row.id}`)}
/>
```

## Appendix B: File Structure

```
frontend/src/components/common/
├── VirtualDataTable.tsx       # Main virtualized table component
├── SimpleTable.tsx            # Lightweight table for small datasets
└── hooks/
    └── useVirtualTable.ts     # TanStack Table + Virtual hook

frontend/src/components/ui/
├── table.tsx                  # Updated primitives (sticky header support)
└── table-header.tsx           # Sortable, resizable header cell
```

---

*Approved by user on 2026-05-05. Proceed to implementation planning.*
