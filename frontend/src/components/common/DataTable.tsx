import React, { useState, useMemo } from "react";
import { ArrowUpDown, ChevronLeft, ChevronRight, Search, X } from "lucide-react";

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
  pageSize?: number;
  searchable?: boolean;
  searchPlaceholder?: string;
}

type SortOrder = "asc" | "desc" | null;

const PAGE_SIZE_OPTIONS = [10, 25, 50, 100];

export function DataTable<T = unknown>({
  columns,
  data,
  rowKey,
  onRowClick,
  pageSize: initialPageSize = 25,
  searchable = false,
  searchPlaceholder = "Search...",
}: DataTableProps<T>) {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortOrder, setSortOrder] = useState<SortOrder>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(initialPageSize);
  const [searchQuery, setSearchQuery] = useState("");

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

  const filteredData = useMemo(() => {
    const items = Array.isArray(data) ? data : [];
    if (!searchQuery.trim()) return items;
    const q = searchQuery.toLowerCase();
    return items.filter((row) => {
      const record = row as Record<string, unknown>;
      return columns.some((col) => {
        const val = record[col.key];
        if (val === null || val === undefined) return false;
        return String(val).toLowerCase().includes(q);
      });
    });
  }, [data, searchQuery, columns]);

  const sortedData = useMemo(() => {
    const items = [...filteredData];
    if (sortKey && sortOrder) {
      items.sort((a, b) => {
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
    return items;
  }, [filteredData, sortKey, sortOrder]);

  const totalPages = Math.max(1, Math.ceil(sortedData.length / pageSize));
  const safePage = Math.min(currentPage, totalPages - 1);
  const paginatedData = sortedData.slice(safePage * pageSize, (safePage + 1) * pageSize);
  const startRow = safePage * pageSize + 1;
  const endRow = Math.min((safePage + 1) * pageSize, sortedData.length);

  const getRowKey = (row: T, index: number) => {
    if (typeof rowKey === "function") {
      return rowKey(row, index);
    }
    const val = (row as Record<string, unknown>)[rowKey];
    return String(val);
  };

  return (
    <div className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900/50 shadow-xl">
      {searchable && (
        <div className="border-b border-slate-800/50 bg-slate-900/60 px-4 py-3">
          <div className="relative max-w-sm">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(0); }}
              placeholder={searchPlaceholder}
              className="w-full rounded-md border border-slate-700 bg-slate-800 py-1.5 pl-9 pr-8 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
            {searchQuery && (
              <button
                onClick={() => { setSearchQuery(""); setCurrentPage(0); }}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300"
              >
                <X size={14} />
              </button>
            )}
          </div>
        </div>
      )}
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
          {paginatedData.length === 0 ? (
            <tr>
              <td
                colSpan={columns.length}
                className="px-6 py-8 text-center text-sm text-slate-400"
              >
                No data available
              </td>
            </tr>
          ) : (
            paginatedData.map((row, idx) => (
              <tr
                key={getRowKey(row, safePage * pageSize + idx)}
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
            ))
          )}
        </tbody>
      </table>

      {/* Pagination Footer */}
      {sortedData.length > 0 && (
        <div className="flex items-center justify-between border-t border-slate-800/50 bg-slate-900/60 px-6 py-3">
          <div className="flex items-center gap-3 text-sm text-slate-400">
            <span>
              {startRow}–{endRow} of {sortedData.length}
            </span>
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(Number(e.target.value));
                setCurrentPage(0);
              }}
              className="rounded border border-slate-700 bg-slate-800 px-2 py-1 text-xs text-slate-300 focus:border-blue-500 focus:outline-none"
            >
              {PAGE_SIZE_OPTIONS.map((size) => (
                <option key={size} value={size}>
                  {size} / page
                </option>
              ))}
            </select>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setCurrentPage(0)}
              disabled={safePage === 0}
              className="rounded px-2 py-1 text-xs text-slate-400 hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed"
            >
              First
            </button>
            <button
              onClick={() => setCurrentPage((p) => Math.max(0, p - 1))}
              disabled={safePage === 0}
              className="rounded p-1 text-slate-400 hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronLeft size={16} />
            </button>
            <span className="px-3 text-sm text-slate-300">
              {safePage + 1} / {totalPages}
            </span>
            <button
              onClick={() => setCurrentPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={safePage >= totalPages - 1}
              className="rounded p-1 text-slate-400 hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronRight size={16} />
            </button>
            <button
              onClick={() => setCurrentPage(totalPages - 1)}
              disabled={safePage >= totalPages - 1}
              className="rounded px-2 py-1 text-xs text-slate-400 hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed"
            >
              Last
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
