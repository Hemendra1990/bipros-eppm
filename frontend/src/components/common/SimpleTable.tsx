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
