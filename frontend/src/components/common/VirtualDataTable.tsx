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

  const { table, parentRef, virtualRows, rows, paddingTop, paddingBottom, virtualizer } =
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
