import React, { useState } from "react";
import { ArrowUpDown } from "lucide-react";

export interface ColumnDef<T = unknown> {
  key: string;
  label: string;
  render?: (value: unknown, row: T) => React.ReactNode | null;
  sortable?: boolean;
  className?: string;
}

interface DataTableProps<T = unknown> {
  columns: ColumnDef<T>[];
  data: T[];
  rowKey: string | ((row: T, index: number) => string);
  onRowClick?: (row: T) => void;
}

type SortOrder = "asc" | "desc" | null;

export function DataTable<T = unknown>({
  columns,
  data,
  rowKey,
  onRowClick,
}: DataTableProps<T>) {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortOrder, setSortOrder] = useState<SortOrder>(null);

  const handleSort = (key: string) => {
    if (sortKey === key) {
      if (sortOrder === "asc") {
        setSortOrder("desc");
      } else if (sortOrder === "desc") {
        setSortKey(null);
        setSortOrder(null);
      }
    } else {
      setSortKey(key);
      setSortOrder("asc");
    }
  };

  let sortedData = [...(Array.isArray(data) ? data : [])];
  if (sortKey && sortOrder) {
    sortedData.sort((a, b) => {
      const aVal = (a as Record<string, unknown>)[sortKey];
      const bVal = (b as Record<string, unknown>)[sortKey];

      if (aVal === null || aVal === undefined) return 1;
      if (bVal === null || bVal === undefined) return -1;

      if (typeof aVal === "string" && typeof bVal === "string") {
        return sortOrder === "asc" ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
      }

      if (typeof aVal === "number" && typeof bVal === "number") {
        return sortOrder === "asc" ? aVal - bVal : bVal - aVal;
      }

      return 0;
    });
  }

  const getRowKey = (row: T, index: number) => {
    if (typeof rowKey === "function") {
      return rowKey(row, index);
    }
    const val = (row as Record<string, unknown>)[rowKey];
    return String(val);
  };

  return (
    <div className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900/50 shadow-xl">
      <table className="min-w-full divide-y divide-slate-800/50">
        <thead className="bg-slate-900/80 border-b border-slate-700/50">
          <tr>
            {columns.map((col) => (
              <th
                key={String(col.key)}
                className={`px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400 ${
                  col.sortable ? "cursor-pointer hover:bg-slate-800/50" : ""
                } ${col.className ?? ""}`}
                onClick={() => col.sortable && handleSort(String(col.key))}
              >
                <div className="flex items-center gap-2">
                  <span>{col.label}</span>
                  {col.sortable && (
                    <ArrowUpDown
                      size={14}
                      className={`text-slate-500 transition-opacity ${
                        sortKey === col.key ? "opacity-100" : "opacity-30"
                      }`}
                    />
                  )}
                </div>
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800/50">
          {sortedData.map((row, idx) => (
            <tr
              key={getRowKey(row, idx)}
              className={`border-b border-slate-800/50 transition-colors ${onRowClick ? "cursor-pointer hover:bg-slate-800/30" : ""}`}
              onClick={() => onRowClick?.(row)}
            >
              {columns.map((col) => (
                <td
                  key={String(col.key)}
                  className={`px-6 py-4 text-sm text-slate-300 ${col.className ?? ""}`}
                >
                  {col.render
                    ? col.render((row as Record<string, unknown>)[col.key], row)
                    : String((row as Record<string, unknown>)[col.key] ?? "")}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
