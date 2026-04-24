/**
 * Minimal dependency-free CSV export. Given an array of rows (objects) and an
 * optional column list, produce a downloadable CSV blob and trigger a click.
 * Values are stringified, nulls/undefined become empty, and any cell containing
 * a quote/comma/newline is RFC-4180 quoted.
 */
export interface CsvColumn<T> {
  key: keyof T | string;
  header?: string;
  accessor?: (row: T) => unknown;
}

function escapeCell(v: unknown): string {
  if (v == null) return "";
  const s = typeof v === "string" ? v : String(v);
  if (/[",\r\n]/.test(s)) {
    return `"${s.replace(/"/g, '""')}"`;
  }
  return s;
}

export function toCsv<T>(rows: T[], columns?: CsvColumn<T>[]): string {
  if (rows.length === 0) return "";
  const cols: CsvColumn<T>[] =
    columns ??
    Object.keys(rows[0] as object).map((k) => ({ key: k }));

  const header = cols.map((c) => escapeCell(c.header ?? String(c.key))).join(",");
  const body = rows
    .map((row) =>
      cols
        .map((c) => {
          const val = c.accessor
            ? c.accessor(row)
            : (row as Record<string, unknown>)[c.key as string];
          return escapeCell(val);
        })
        .join(","),
    )
    .join("\n");
  return `${header}\n${body}`;
}

export function downloadCsv(filename: string, csv: string): void {
  if (typeof window === "undefined") return;
  // BOM so Excel opens UTF-8 cleanly
  const blob = new Blob(["﻿", csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename.endsWith(".csv") ? filename : `${filename}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
