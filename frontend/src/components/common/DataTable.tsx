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
