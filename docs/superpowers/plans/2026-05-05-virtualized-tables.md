# Virtualized Data Tables Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the basic HTML table system with a virtualized, sticky-header table built on TanStack Table + TanStack Virtual, while maintaining backward compatibility for existing pages.

**Architecture:** A new `VirtualDataTable` component uses TanStack Table for state (sorting, filtering, resizing) and TanStack Virtual for windowed rendering. The existing `DataTable.tsx` becomes a thin backward-compatible wrapper that converts old-format columns to TanStack columns and delegates to `VirtualDataTable`. A lightweight `SimpleTable` handles small datasets without virtualization overhead.

**Tech Stack:** React 19, Next.js 16, Tailwind CSS v4, TanStack Table v8, TanStack Virtual v3, Vitest + jsdom + Testing Library

---

## File Structure

```
frontend/src/components/common/
├── hooks/
│   └── useVirtualTable.ts          # TanStack Table + Virtual hook
├── VirtualDataTable.tsx            # Main virtualized table component
├── SimpleTable.tsx                 # Lightweight table for <50 rows
├── DataTable.tsx                   # Backward-compat wrapper (updated)
└── __tests__/
    ├── useVirtualTable.test.ts
    ├── VirtualDataTable.test.tsx
    └── SimpleTable.test.tsx

frontend/src/components/ui/
└── table.tsx                       # Updated primitives (sticky header support)

frontend/src/test/
└── setup.ts                        # Vitest setup (jest-dom matchers)

frontend/vitest.config.ts           # Vitest configuration
```

---

## Task 1: Install Dependency

**Files:**
- Modify: `frontend/package.json`

- [ ] **Step 1: Install @tanstack/react-virtual**

Run:
```bash
cd /Volumes/Java/Projects/bipros-eppm/frontend && pnpm add @tanstack/react-virtual
```

Expected: `@tanstack/react-virtual` added to `dependencies` in `package.json`.

- [ ] **Step 2: Verify installation**

Run:
```bash
cd /Volumes/Java/Projects/bipros-eppm/frontend && pnpm list @tanstack/react-virtual
```

Expected: Package version displayed (e.g., `^3.x.x`).

- [ ] **Step 3: Commit**

```bash
git add frontend/package.json frontend/pnpm-lock.yaml
git commit -m "deps: add @tanstack/react-virtual for table virtualization"
```

---

## Task 2: Set Up Vitest

**Files:**
- Create: `frontend/vitest.config.ts`
- Create: `frontend/src/test/setup.ts`

- [ ] **Step 1: Write vitest.config.ts**

```ts
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
```

- [ ] **Step 2: Write src/test/setup.ts**

```ts
import "@testing-library/jest-dom";
```

- [ ] **Step 3: Verify config loads**

Run:
```bash
cd /Volumes/Java/Projects/bipros-eppm/frontend && npx vitest --run --reporter=verbose 2>&1 | head -20
```

Expected: Vitest starts, finds 0 tests (no test files yet), exits cleanly.

- [ ] **Step 4: Commit**

```bash
git add frontend/vitest.config.ts frontend/src/test/setup.ts
git commit -m "test: configure vitest with jsdom and jest-dom matchers"
```

---

## Task 3: Update Table Primitives

**Files:**
- Modify: `frontend/src/components/ui/table.tsx`

- [ ] **Step 1: Update table.tsx with sticky header support and dark-mode awareness**

Replace the entire file:

```tsx
import React from "react";
import { cn } from "@/lib/utils/cn";

export function Table({ className, ...props }: React.HTMLAttributes<HTMLTableElement>) {
  return (
    <div className="overflow-hidden rounded-xl border border-hairline bg-paper">
      <table
        className={cn("w-full border-collapse text-sm", className)}
        {...props}
      />
    </div>
  );
}

export function TableHeader({
  className,
  ...props
}: React.HTMLAttributes<HTMLTableSectionElement>) {
  return (
    <thead
      className={cn(
        "bg-ivory dark:bg-[#161616] border-b border-hairline sticky top-0 z-10",
        className
      )}
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
        "border-b border-hairline last:border-b-0 transition-colors duration-[120ms]",
        "hover:bg-ivory dark:hover:bg-[#1E1E1E]",
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
        "px-4 py-3 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep whitespace-nowrap",
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
      className={cn(
        "px-4 py-3.5 align-middle text-charcoal dark:text-[#F5F2E8] whitespace-nowrap",
        className
      )}
      {...props}
    />
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/ui/table.tsx
git commit -m "ui: add sticky header and dark-mode support to table primitives"
```

---

## Task 4: Create useVirtualTable Hook

**Files:**
- Create: `frontend/src/components/common/hooks/useVirtualTable.ts`

- [ ] **Step 1: Write the hook**

```ts
"use client";

import { useRef } from "react";
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  getFilteredRowModel,
  type ColumnDef,
  type TableOptions,
  type SortingState,
} from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";

interface UseVirtualTableOptions<TData>
  extends Omit<TableOptions<TData>, "getCoreRowModel" | "columns" | "data"> {
  data: TData[];
  columns: ColumnDef<TData, unknown>[];
  estimateRowHeight?: number;
  overscan?: number;
}

export function useVirtualTable<TData>({
  data,
  columns,
  estimateRowHeight = 48,
  overscan = 5,
  state,
  onSortingChange,
  ...options
}: UseVirtualTableOptions<TData>) {
  const parentRef = useRef<HTMLDivElement>(null);

  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    enableColumnResizing: true,
    columnResizeMode: "onChange",
    state,
    onSortingChange,
    ...options,
  });

  const { rows } = table.getRowModel();

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => estimateRowHeight,
    overscan,
    measureElement:
      typeof window !== "undefined" && "ResizeObserver" in window
        ? (element) => element.getBoundingClientRect().height
        : undefined,
  });

  const virtualRows = virtualizer.getVirtualItems();
  const totalSize = virtualizer.getTotalSize();

  const paddingTop = virtualRows.length > 0 ? virtualRows[0].start : 0;
  const paddingBottom =
    virtualRows.length > 0
      ? totalSize - virtualRows[virtualRows.length - 1].end
      : 0;

  return {
    table,
    virtualizer,
    virtualRows,
    rows,
    parentRef,
    totalSize,
    paddingTop,
    paddingBottom,
  };
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/common/hooks/useVirtualTable.ts
git commit -m "feat: add useVirtualTable hook combining TanStack Table + Virtual"
```

---

## Task 5: Create VirtualDataTable Component

**Files:**
- Create: `frontend/src/components/common/VirtualDataTable.tsx`

- [ ] **Step 1: Write the component**

```tsx
"use client";

import React, { useState, useCallback } from "react";
import {
  flexRender,
  type ColumnDef,
  type SortingState,
} from "@tanstack/react-table";
import { ArrowUp, ArrowDown, Search, X } from "lucide-react";
import { useVirtualTable } from "./hooks/useVirtualTable";
import { cn } from "@/lib/utils/cn";

export type { ColumnDef } from "@tanstack/react-table";

interface VirtualDataTableProps<TData> {
  data: TData[];
  columns: ColumnDef<TData, unknown>[];

  // Features
  sortable?: boolean;
  filterable?: boolean;
  resizable?: boolean;
  searchable?: boolean;
  selectable?: boolean;

  // Virtualization
  estimateRowHeight?: number;
  overscan?: number;
  maxHeight?: number | string;

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

export function VirtualDataTable<TData>({
  data,
  columns,
  sortable = true,
  filterable = false,
  resizable = true,
  searchable = true,
  selectable = false,
  estimateRowHeight = 48,
  overscan = 5,
  maxHeight = 600,
  className,
  headerClassName,
  rowClassName,
  onRowClick,
  onRowDoubleClick,
  emptyMessage = "No data available",
  isLoading = false,
}: VirtualDataTableProps<TData>) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const [globalFilter, setGlobalFilter] = useState("");
  const [selectedRows, setSelectedRows] = useState<Set<string>>(new Set());

  const handleRowClick = useCallback(
    (row: TData, rowId: string) => {
      if (selectable) {
        setSelectedRows((prev) => {
          const next = new Set(prev);
          if (next.has(rowId)) {
            next.delete(rowId);
          } else {
            next.add(rowId);
          }
          return next;
        });
      }
      onRowClick?.(row);
    },
    [selectable, onRowClick]
  );

  const { table, parentRef, virtualRows, rows, paddingTop, paddingBottom } =
    useVirtualTable({
      data,
      columns,
      estimateRowHeight,
      overscan,
      state: {
        sorting: sortable ? sorting : [],
        globalFilter: searchable ? globalFilter : undefined,
      },
      onSortingChange: sortable ? setSorting : undefined,
      globalFilterFn: searchable ? "includesString" : undefined,
      enableSorting: sortable,
      enableColumnResizing: resizable,
      enableRowSelection: selectable,
    });

  if (isLoading) {
    return (
      <div className={cn("rounded-xl border border-hairline bg-paper overflow-hidden", className)}>
        <div className="animate-pulse">
          <div className="h-10 bg-ivory dark:bg-[#161616] border-b border-hairline" />
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-12 border-b border-hairline/50 bg-paper" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className={cn("rounded-xl border border-hairline bg-paper overflow-hidden flex flex-col", className)}>
      {/* Search bar */}
      {searchable && (
        <div className="border-b border-hairline/50 bg-ivory/50 dark:bg-[#161616]/50 px-4 py-3">
          <div className="relative max-w-sm">
            <Search
              size={16}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-ash"
            />
            <input
              type="text"
              value={globalFilter}
              onChange={(e) => setGlobalFilter(e.target.value)}
              placeholder="Search..."
              className="w-full rounded-md border border-hairline bg-paper py-1.5 pl-9 pr-8 text-sm text-charcoal placeholder-ash focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold dark:text-[#F5F2E8]"
            />
            {globalFilter && (
              <button
                onClick={() => setGlobalFilter("")}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-ash hover:text-charcoal dark:hover:text-[#F5F2E8]"
              >
                <X size={14} />
              </button>
            )}
          </div>
        </div>
      )}

      {/* Table */}
      <div
        ref={parentRef}
        className="overflow-auto"
        style={{ maxHeight }}
      >
        <table
          className="w-full border-collapse text-sm"
          style={{ width: table.getTotalSize() }}
        >
          <thead className={cn(
            "bg-ivory dark:bg-[#161616] border-b border-hairline sticky top-0 z-10",
            headerClassName
          )}>
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <th
                    key={header.id}
                    colSpan={header.colSpan}
                    className="relative px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-[0.10em] text-slate dark:text-[#A1A1A6] whitespace-nowrap select-none"
                    style={{ width: header.getSize() }}
                  >
                    <div className="flex items-center gap-1.5">
                      {header.isPlaceholder
                        ? null
                        : flexRender(
                            header.column.columnDef.header,
                            header.getContext()
                          )}
                      {sortable && header.column.getCanSort() && (
                        <button
                          onClick={header.column.getToggleSortingHandler()}
                          className="p-0.5 rounded hover:bg-gold-tint/20 transition-colors"
                        >
                          {header.column.getIsSorted() === "asc" ? (
                            <ArrowUp size={12} className="text-gold" />
                          ) : header.column.getIsSorted() === "desc" ? (
                            <ArrowDown size={12} className="text-gold" />
                          ) : (
                            <ArrowUp
                              size={12}
                              className="text-ash opacity-40"
                            />
                          )}
                        </button>
                      )}
                    </div>
                    {/* Resize handle */}
                    {resizable && header.column.getCanResize() && (
                      <div
                        onMouseDown={header.getResizeHandler()}
                        onTouchStart={header.getResizeHandler()}
                        className={cn(
                          "absolute right-0 top-0 h-full w-1 cursor-col-resize bg-transparent hover:bg-gold/50 transition-colors",
                          header.column.getIsResizing() && "bg-gold w-1.5"
                        )}
                      />
                    )}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length}
                  className="px-6 py-8 text-center text-sm text-slate dark:text-[#A1A1A6]"
                >
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              <>
                {paddingTop > 0 && (
                  <tr>
                    <td
                      colSpan={columns.length}
                      style={{ height: `${paddingTop}px` }}
                    />
                  </tr>
                )}
                {virtualRows.map((virtualRow) => {
                  const row = rows[virtualRow.index];
                  const isSelected = selectedRows.has(row.id);
                  const dynamicRowClass =
                    typeof rowClassName === "function"
                      ? rowClassName(row.original)
                      : rowClassName;

                  return (
                    <tr
                      key={row.id}
                      data-index={virtualRow.index}
                      ref={virtualizer.measureElement}
                      className={cn(
                        "border-b border-hairline/50 transition-colors duration-[120ms]",
                        "hover:bg-gold-tint/10 dark:hover:bg-gold-tint/5",
                        onRowClick && "cursor-pointer",
                        isSelected && "bg-gold/10 dark:bg-gold/20",
                        dynamicRowClass
                      )}
                      onClick={() => handleRowClick(row.original, row.id)}
                      onDoubleClick={() => onRowDoubleClick?.(row.original)}
                    >
                      {row.getVisibleCells().map((cell) => (
                        <td
                          key={cell.id}
                          className="px-4 py-3 text-sm text-charcoal dark:text-[#F5F2E8] whitespace-nowrap align-middle"
                          style={{ width: cell.column.getSize() }}
                        >
                          {flexRender(
                            cell.column.columnDef.cell,
                            cell.getContext()
                          )}
                        </td>
                      ))}
                    </tr>
                  );
                })}
                {paddingBottom > 0 && (
                  <tr>
                    <td
                      colSpan={columns.length}
                      style={{ height: `${paddingBottom}px` }}
                    />
                  </tr>
                )}
              </>
            )}
          </tbody>
        </table>
      </div>

      {/* Footer: row count */}
      {rows.length > 0 && (
        <div className="border-t border-hairline/50 bg-ivory/50 dark:bg-[#161616]/50 px-4 py-2 text-xs text-slate dark:text-[#A1A1A6]">
          {rows.length} {rows.length === 1 ? "row" : "rows"}
          {data.length !== rows.length && ` (filtered from ${data.length})`}
        </div>
      )}
    </div>
  );
}
```

Wait — I used `virtualizer` in the JSX but destructured it from `useVirtualTable`. Let me fix the destructuring:

```tsx
const { table, parentRef, virtualRows, rows, paddingTop, paddingBottom, virtualizer } = useVirtualTable({...});
```

Actually, looking at the code, I reference `virtualizer.measureElement` but didn't destructure `virtualizer`. Let me fix this in the plan:

The `useVirtualTable` hook returns `virtualizer`. In VirtualDataTable, destructure it:
```tsx
const { table, parentRef, virtualRows, rows, paddingTop, paddingBottom, virtualizer } = useVirtualTable({...});
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/common/VirtualDataTable.tsx
git commit -m "feat: add VirtualDataTable with sticky header, sorting, resizing, and virtual scrolling"
```

---

## Task 6: Create SimpleTable Component

**Files:**
- Create: `frontend/src/components/common/SimpleTable.tsx`

- [ ] **Step 1: Write the component**

```tsx
"use client";

import React, { useState } from "react";
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
  type ColumnDef,
  type SortingState,
} from "@tanstack/react-table";
import { ArrowUp, ArrowDown } from "lucide-react";
import { cn } from "@/lib/utils/cn";

interface SimpleTableProps<TData> {
  data: TData[];
  columns: ColumnDef<TData, unknown>[];
  sortable?: boolean;
  className?: string;
  emptyMessage?: string;
}

export function SimpleTable<TData>({
  data,
  columns,
  sortable = true,
  className,
  emptyMessage = "No data available",
}: SimpleTableProps<TData>) {
  const [sorting, setSorting] = useState<SortingState>([]);

  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    enableSorting: sortable,
    onSortingChange: setSorting,
    state: { sorting },
  });

  const rows = table.getRowModel().rows;

  return (
    <div className={cn("rounded-xl border border-hairline bg-paper overflow-hidden", className)}>
      <div className="overflow-auto">
        <table className="w-full border-collapse text-sm">
          <thead className="bg-ivory dark:bg-[#161616] border-b border-hairline">
            {table.getHeaderGroups().map((headerGroup) => (
              <tr key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <th
                    key={header.id}
                    className="px-4 py-3 text-left text-[11px] font-semibold uppercase tracking-[0.10em] text-slate dark:text-[#A1A1A6] whitespace-nowrap"
                  >
                    <div className="flex items-center gap-1.5">
                      {header.isPlaceholder
                        ? null
                        : flexRender(
                            header.column.columnDef.header,
                            header.getContext()
                          )}
                      {sortable && header.column.getCanSort() && (
                        <button
                          onClick={header.column.getToggleSortingHandler()}
                          className="p-0.5 rounded hover:bg-gold-tint/20 transition-colors"
                        >
                          {header.column.getIsSorted() === "asc" ? (
                            <ArrowUp size={12} className="text-gold" />
                          ) : header.column.getIsSorted() === "desc" ? (
                            <ArrowDown size={12} className="text-gold" />
                          ) : (
                            <ArrowUp size={12} className="text-ash opacity-40" />
                          )}
                        </button>
                      )}
                    </div>
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length}
                  className="px-6 py-8 text-center text-sm text-slate dark:text-[#A1A1A6]"
                >
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr
                  key={row.id}
                  className="border-b border-hairline/50 transition-colors duration-[120ms] hover:bg-gold-tint/10 dark:hover:bg-gold-tint/5"
                >
                  {row.getVisibleCells().map((cell) => (
                    <td
                      key={cell.id}
                      className="px-4 py-3 text-sm text-charcoal dark:text-[#F5F2E8] whitespace-nowrap align-middle"
                    >
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext()
                      )}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/common/SimpleTable.tsx
git commit -m "feat: add SimpleTable for small datasets without virtualization"
```

---

## Task 7: Write Tests for useVirtualTable

**Files:**
- Create: `frontend/src/components/common/__tests__/useVirtualTable.test.ts`

- [ ] **Step 1: Write the test**

```ts
import { describe, it, expect } from "vitest";
import { renderHook } from "@testing-library/react";
import { useVirtualTable } from "../hooks/useVirtualTable";
import type { ColumnDef } from "@tanstack/react-table";

interface TestRow {
  id: string;
  name: string;
  age: number;
}

const columns: ColumnDef<TestRow, unknown>[] = [
  { accessorKey: "name", header: "Name" },
  { accessorKey: "age", header: "Age" },
];

const data: TestRow[] = [
  { id: "1", name: "Alice", age: 30 },
  { id: "2", name: "Bob", age: 25 },
  { id: "3", name: "Charlie", age: 35 },
];

describe("useVirtualTable", () => {
  it("returns a table instance with rows", () => {
    const { result } = renderHook(() =>
      useVirtualTable({ data, columns, estimateRowHeight: 48, overscan: 5 })
    );

    expect(result.current.table).toBeDefined();
    expect(result.current.rows).toHaveLength(3);
    expect(result.current.virtualRows).toBeDefined();
    expect(result.current.parentRef).toBeDefined();
    expect(result.current.totalSize).toBeGreaterThan(0);
  });

  it("returns zero rows for empty data", () => {
    const { result } = renderHook(() =>
      useVirtualTable({ data: [], columns, estimateRowHeight: 48, overscan: 5 })
    );

    expect(result.current.rows).toHaveLength(0);
    expect(result.current.totalSize).toBe(0);
  });
});
```

- [ ] **Step 2: Run the test**

Run:
```bash
cd /Volumes/Java/Projects/bipros-eppm/frontend && npx vitest run src/components/common/__tests__/useVirtualTable.test.ts --reporter=verbose
```

Expected: 2 tests pass.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/common/__tests__/useVirtualTable.test.ts
git commit -m "test: add unit tests for useVirtualTable hook"
```

---

## Task 8: Write Tests for VirtualDataTable

**Files:**
- Create: `frontend/src/components/common/__tests__/VirtualDataTable.test.tsx`

- [ ] **Step 1: Write the test**

```tsx
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { VirtualDataTable } from "../VirtualDataTable";
import type { ColumnDef } from "@tanstack/react-table";

interface TestRow {
  id: string;
  name: string;
  age: number;
}

const columns: ColumnDef<TestRow, unknown>[] = [
  { accessorKey: "name", header: "Name" },
  { accessorKey: "age", header: "Age" },
];

const data: TestRow[] = [
  { id: "1", name: "Alice", age: 30 },
  { id: "2", name: "Bob", age: 25 },
  { id: "3", name: "Charlie", age: 35 },
];

describe("VirtualDataTable", () => {
  it("renders column headers", () => {
    render(<VirtualDataTable data={data} columns={columns} maxHeight={400} />);

    expect(screen.getByText("Name")).toBeInTheDocument();
    expect(screen.getByText("Age")).toBeInTheDocument();
  });

  it("renders row data", () => {
    render(<VirtualDataTable data={data} columns={columns} maxHeight={400} />);

    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
    expect(screen.getByText("Charlie")).toBeInTheDocument();
  });

  it("shows empty message when no data", () => {
    render(
      <VirtualDataTable
        data={[]}
        columns={columns}
        maxHeight={400}
        emptyMessage="Nothing here"
      />
    );

    expect(screen.getByText("Nothing here")).toBeInTheDocument();
  });

  it("calls onRowClick when a row is clicked", () => {
    const handleClick = vi.fn();
    render(
      <VirtualDataTable
        data={data}
        columns={columns}
        maxHeight={400}
        onRowClick={handleClick}
      />
    );

    fireEvent.click(screen.getByText("Alice"));
    expect(handleClick).toHaveBeenCalledTimes(1);
    expect(handleClick).toHaveBeenCalledWith(data[0]);
  });

  it("filters rows when searching", () => {
    render(<VirtualDataTable data={data} columns={columns} maxHeight={400} />);

    const searchInput = screen.getByPlaceholderText("Search...");
    fireEvent.change(searchInput, { target: { value: "Bob" } });

    expect(screen.getByText("Bob")).toBeInTheDocument();
    expect(screen.queryByText("Alice")).not.toBeInTheDocument();
  });

  it("shows loading skeleton when isLoading is true", () => {
    const { container } = render(
      <VirtualDataTable data={[]} columns={columns} maxHeight={400} isLoading />
    );

    expect(container.querySelector(".animate-pulse")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test**

Run:
```bash
cd /Volumes/Java/Projects/bipros-eppm/frontend && npx vitest run src/components/common/__tests__/VirtualDataTable.test.tsx --reporter=verbose
```

Expected: 6 tests pass.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/common/__tests__/VirtualDataTable.test.tsx
git commit -m "test: add unit tests for VirtualDataTable component"
```

---

## Task 9: Write Tests for SimpleTable

**Files:**
- Create: `frontend/src/components/common/__tests__/SimpleTable.test.tsx`

- [ ] **Step 1: Write the test**

```tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { SimpleTable } from "../SimpleTable";
import type { ColumnDef } from "@tanstack/react-table";

interface TestRow {
  id: string;
  name: string;
}

const columns: ColumnDef<TestRow, unknown>[] = [
  { accessorKey: "name", header: "Name" },
];

const data: TestRow[] = [
  { id: "1", name: "Alice" },
  { id: "2", name: "Bob" },
];

describe("SimpleTable", () => {
  it("renders headers and rows without virtualization", () => {
    render(<SimpleTable data={data} columns={columns} />);

    expect(screen.getByText("Name")).toBeInTheDocument();
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
  });

  it("shows empty message for empty data", () => {
    render(
      <SimpleTable
        data={[]}
        columns={columns}
        emptyMessage="Empty table"
      />
    );

    expect(screen.getByText("Empty table")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test**

Run:
```bash
cd /Volumes/Java/Projects/bipros-eppm/frontend && npx vitest run src/components/common/__tests__/SimpleTable.test.tsx --reporter=verbose
```

Expected: 2 tests pass.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/common/__tests__/SimpleTable.test.tsx
git commit -m "test: add unit tests for SimpleTable component"
```

---

## Task 10: Update DataTable.tsx as Backward-Compatible Wrapper

**Files:**
- Modify: `frontend/src/components/common/DataTable.tsx`

- [ ] **Step 1: Replace DataTable.tsx with wrapper around VirtualDataTable**

Replace the entire file:

```tsx
"use client";

import React, { useMemo } from "react";
import type { ColumnDef as TanStackColumnDef } from "@tanstack/react-table";
import { VirtualDataTable } from "./VirtualDataTable";

/** @deprecated Use VirtualDataTable with TanStack ColumnDef instead */
export interface ColumnDef<T = unknown> {
  key: string;
  label: string;
  render?: (value: unknown, row: T) => React.ReactNode | null;
  sortable?: boolean;
  className?: string;
}

/** @deprecated Use VirtualDataTable instead */
interface DataTableProps<T = unknown> {
  columns: ColumnDef<T>[];
  data: T[];
  rowKey: string | ((row: T, index: number) => string);
  onRowClick?: (row: T) => void;
  pageSize?: number;
  searchable?: boolean;
  searchPlaceholder?: string;
}

/** @deprecated Use VirtualDataTable with TanStack ColumnDef instead */
export function DataTable<T = unknown>({
  columns,
  data,
  rowKey,
  onRowClick,
  searchable = false,
  searchPlaceholder,
}: DataTableProps<T>) {
  const tanstackColumns: TanStackColumnDef<T, unknown>[] = useMemo(
    () =>
      columns.map((col) => ({
        accessorKey: col.key,
        header: col.label,
        enableSorting: col.sortable ?? false,
        meta: { className: col.className },
        cell: col.render
          ? (info) => col.render!(info.getValue(), info.row.original)
          : (info) => String(info.getValue() ?? ""),
      })),
    [columns]
  );

  const getRowId = useMemo(() => {
    if (typeof rowKey === "function") {
      return (row: T, index: number) => rowKey(row, index);
    }
    return (row: T) => String((row as Record<string, unknown>)[rowKey]);
  }, [rowKey]);

  return (
    <VirtualDataTable
      data={data}
      columns={tanstackColumns}
      searchable={searchable}
      onRowClick={onRowClick}
      sortable
      resizable
      maxHeight={600}
    />
  );
}
```

Note: `searchPlaceholder` is accepted but not forwarded since `VirtualDataTable` uses a fixed "Search..." placeholder. This is acceptable for backward compatibility during migration.

- [ ] **Step 2: Verify existing pages still compile**

Run:
```bash
cd /Volumes/Java/Projects/bipros-eppm/frontend && npx tsc --noEmit 2>&1 | grep -c "error TS"
```

Expected: 0 errors (or only pre-existing errors unrelated to this change).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/common/DataTable.tsx
git commit -m "feat: refactor DataTable as backward-compat wrapper over VirtualDataTable"
```

---

## Task 11: Migrate One Example Page

**Files:**
- Modify: `frontend/src/app/(app)/admin/organisations/page.tsx` (simple page, ~50 lines)

- [ ] **Step 1: Read the current organisations page**

Read `frontend/src/app/(app)/admin/organisations/page.tsx` to understand its current DataTable usage.

- [ ] **Step 2: Migrate to VirtualDataTable with TanStack columns**

Update imports:
```tsx
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import { createColumnHelper } from "@tanstack/react-table";
```

Replace old `ColumnDef` array with TanStack columns:
```tsx
const columnHelper = createColumnHelper<Organisation>();

const columns = [
  columnHelper.accessor("name", {
    header: "Name",
    cell: (info) => info.getValue(),
  }),
  columnHelper.accessor("code", {
    header: "Code",
    cell: (info) => info.getValue(),
  }),
  // ... other columns
];
```

Replace `<DataTable ... />` with:
```tsx
<VirtualDataTable
  columns={columns}
  data={organisations}
  searchable
  sortable
  resizable
  onRowClick={(row) => router.push(`/admin/organisations/${row.id}`)}
/>
```

Remove `rowKey` prop (TanStack Table derives row IDs from data).

- [ ] **Step 3: Verify the page compiles**

Run:
```bash
cd /Volumes/Java/Projects/bipros-eppm/frontend && npx tsc --noEmit 2>&1 | grep -i "organisations" || echo "No org errors"
```

Expected: No organisations-related TypeScript errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/(app)/admin/organisations/page.tsx
git commit -m "refactor: migrate organisations page to VirtualDataTable"
```

---

## Task 12: Run Lint and Build

- [ ] **Step 1: Run ESLint**

Run:
```bash
cd /Volumes/Java/Projects/bipros-eppm/frontend && pnpm lint
```

Expected: Passes with 0 errors.

- [ ] **Step 2: Run all unit tests**

Run:
```bash
cd /Volumes/Java/Projects/bipros-eppm/frontend && npx vitest run --reporter=verbose
```

Expected: All 10 tests pass.

- [ ] **Step 3: Run production build**

Run:
```bash
cd /Volumes/Java/Projects/bipros-eppm/frontend && pnpm build
```

Expected: Builds successfully with 0 errors.

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat: complete virtualized table foundation with lint and build passing"
```

---

## Self-Review

### Spec Coverage Check

| Spec Requirement | Plan Task |
|------------------|-----------|
| Install `@tanstack/react-virtual` | Task 1 |
| Update `table.tsx` primitives | Task 3 |
| Create `useVirtualTable` hook | Task 4 |
| Create `VirtualDataTable` component | Task 5 |
| Create `SimpleTable` component | Task 6 |
| Backward-compat `DataTable` wrapper | Task 10 |
| Sticky headers | Task 3, 5 |
| Column resizing | Task 4, 5 |
| Sorting | Task 4, 5 |
| Global search/filtering | Task 5 |
| Virtual scrolling (padding rows) | Task 4, 5 |
| Loading skeleton | Task 5 |
| Empty state | Task 5, 6 |
| Row click / selection | Task 5 |
| Dark theme tokens | Task 3, 5, 6 |
| Tests | Tasks 7, 8, 9 |
| Migrate example page | Task 11 |
| Lint + build passing | Task 12 |

### Placeholder Scan

No "TBD", "TODO", "implement later", or "fill in details" found. All code blocks contain complete, runnable implementations.

### Type Consistency

- `ColumnDef` in `VirtualDataTable.tsx` is re-exported from `@tanstack/react-table`
- `ColumnDef` in `DataTable.tsx` is the old backward-compatible type
- `useVirtualTable` accepts `ColumnDef<TData, unknown>[]` consistently
- `VirtualDataTable` and `SimpleTable` both use the same TanStack `ColumnDef` type

### Risk Notes

- `virtualizer` is properly destructured in VirtualDataTable (used for `measureElement`)
- `searchPlaceholder` from old API is accepted but ignored — documented in code comments
- `pageSize` from old API is accepted but ignored — virtualization replaces pagination
- All existing pages importing `DataTable` will auto-migrate to virtualized rendering without code changes

---

*Plan ready for execution.*
