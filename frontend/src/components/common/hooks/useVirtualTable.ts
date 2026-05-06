"use client";

import { useRef } from "react";
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  getFilteredRowModel,
  type ColumnDef,
  type TableOptions,
} from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";

interface UseVirtualTableOptions<TData>
  extends Omit<TableOptions<TData>, "getCoreRowModel" | "columns" | "data"> {
  // IMPORTANT: pass stable references for `data` and `columns` (useMemo at the
  // call site, or define `columns` outside the component). TanStack rebuilds
  // the row/sorted/filtered models whenever these references change; for
  // 10k-row datasets that means re-sorting on every parent re-render.
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
    // onEnd avoids a re-render on every mousemove during a column-resize drag.
    columnResizeMode: "onEnd",
    state,
    onSortingChange,
    ...options,
  });

  const { rows } = table.getRowModel();

  // NOTE: do NOT pass `measureElement`. With variable-height cells (e.g. tree
  // view's chevrons + indented labels) the resulting ResizeObserver-driven
  // re-measurement loop produces a steady stream of high-priority state
  // updates that starves out router transitions — clicking a <Link> queues
  // router.push() but React 19 never gets idle time to apply it, so the page
  // appears frozen until any other event flushes the queue. Fixed-row sizing
  // (estimateSize only) sidesteps this.
  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => estimateRowHeight,
    overscan,
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
