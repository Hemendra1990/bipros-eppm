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

  let sortedData = [...data];
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
    <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
      <table className="min-w-full divide-y divide-gray-200">
        <thead className="bg-gray-50">
          <tr>
            {columns.map((col) => (
              <th
                key={String(col.key)}
                className={`px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 ${
                  col.sortable ? "cursor-pointer hover:bg-gray-100" : ""
                } ${col.className ?? ""}`}
                onClick={() => col.sortable && handleSort(String(col.key))}
              >
                <div className="flex items-center gap-2">
                  <span>{col.label}</span>
                  {col.sortable && (
                    <ArrowUpDown
                      size={14}
                      className={`transition-opacity ${
                        sortKey === col.key ? "opacity-100" : "opacity-30"
                      }`}
                    />
                  )}
                </div>
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200">
          {sortedData.map((row, idx) => (
            <tr
              key={getRowKey(row, idx)}
              className={`${onRowClick ? "cursor-pointer hover:bg-gray-50" : ""}`}
              onClick={() => onRowClick?.(row)}
            >
              {columns.map((col) => (
                <td
                  key={String(col.key)}
                  className={`px-6 py-4 text-sm text-gray-900 ${col.className ?? ""}`}
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
