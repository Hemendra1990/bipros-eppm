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
