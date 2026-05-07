"use client";

import { Plus, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils/cn";

export interface RowGridColumn<R> {
  key: string;
  label: string;
  width?: string;
  align?: "left" | "right" | "center";
  render: (row: R, idx: number, update: (patch: Partial<R>) => void) => React.ReactNode;
}

interface RowGridProps<R> {
  title: string;
  rows: R[];
  columns: RowGridColumn<R>[];
  onAdd: () => void;
  onChange: (idx: number, patch: Partial<R>) => void;
  onRemove: (idx: number) => void;
  emptyHint?: string;
  addLabel?: string;
}

/**
 * Compact editable table for nested DPR resource rows. Header + body + sticky footer with the
 * "+ Add row" button. The parent owns the row list — this component is presentational.
 */
export function RowGrid<R>({
  title,
  rows,
  columns,
  onAdd,
  onChange,
  onRemove,
  emptyHint,
  addLabel,
}: RowGridProps<R>) {
  return (
    <div className="rounded-lg border border-hairline bg-paper">
      <div className="flex items-center justify-between border-b border-hairline px-4 py-2.5">
        <div>
          <span className="text-sm font-semibold text-charcoal">{title}</span>
          {rows.length > 0 && (
            <span className="ml-2 text-xs text-slate">
              {rows.length} row{rows.length === 1 ? "" : "s"}
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={onAdd}
          className="inline-flex items-center gap-1.5 rounded-md bg-gold px-2.5 py-1 text-xs font-semibold text-gold-ink hover:bg-gold-deep transition"
        >
          <Plus className="h-3.5 w-3.5" />
          {addLabel ?? "Add row"}
        </button>
      </div>
      {rows.length === 0 ? (
        <div className="px-4 py-6 text-center text-sm text-slate">
          {emptyHint ?? "No rows yet — click Add row to get started."}
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-ivory/60">
              <tr>
                {columns.map((c) => (
                  <th
                    key={c.key}
                    className={cn(
                      "px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate",
                      c.align === "right" && "text-right",
                      c.align === "center" && "text-center",
                      c.align !== "right" && c.align !== "center" && "text-left"
                    )}
                    style={c.width ? { width: c.width } : undefined}
                  >
                    {c.label}
                  </th>
                ))}
                <th className="w-10" />
              </tr>
            </thead>
            <tbody>
              {rows.map((row, idx) => (
                <tr key={idx} className="border-t border-hairline align-middle">
                  {columns.map((c) => (
                    <td
                      key={c.key}
                      className={cn(
                        "px-2 py-1.5",
                        c.align === "right" && "text-right",
                        c.align === "center" && "text-center"
                      )}
                    >
                      {c.render(row, idx, (patch) => onChange(idx, patch))}
                    </td>
                  ))}
                  <td className="pr-2 text-right">
                    <button
                      type="button"
                      onClick={() => onRemove(idx)}
                      className="rounded-md p-1.5 text-slate hover:bg-burgundy/10 hover:text-burgundy transition"
                      aria-label="Remove row"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/** Inline cell editors used by the grids. */
export function CellInput({
  value,
  onChange,
  type = "text",
  placeholder,
  step,
  min,
  className,
}: {
  value: string | number | null | undefined;
  onChange: (v: string) => void;
  type?: "text" | "number";
  placeholder?: string;
  step?: string;
  min?: string;
  className?: string;
}) {
  return (
    <input
      type={type}
      value={value ?? ""}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      step={step}
      min={min}
      className={cn(
        "w-full rounded border border-hairline bg-paper px-2 py-1 text-sm text-charcoal",
        "focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold/40",
        type === "number" && "text-right tabular-nums",
        className
      )}
    />
  );
}

export function CellSelect({
  value,
  onChange,
  options,
  className,
}: {
  value: string | null | undefined;
  onChange: (v: string) => void;
  options: Array<{ value: string; label: string }>;
  className?: string;
}) {
  return (
    <select
      value={value ?? ""}
      onChange={(e) => onChange(e.target.value)}
      className={cn(
        "w-full rounded border border-hairline bg-paper px-2 py-1 text-sm text-charcoal",
        "focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold/40",
        className
      )}
    >
      <option value="">—</option>
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
}
