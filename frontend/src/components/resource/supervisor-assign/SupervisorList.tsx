"use client";

import { Search, Users } from "lucide-react";
import { SupervisorRow, type SupervisorOption } from "./SupervisorRow";

interface Props {
  options: SupervisorOption[];
  filteredOptions: SupervisorOption[];
  selectedId: string;
  onSelect: (id: string) => void;
  search: string;
  onSearchChange: (value: string) => void;
  workloadById: Map<string, number>;
  isLoading: boolean;
}

export function SupervisorList({
  options,
  filteredOptions,
  selectedId,
  onSelect,
  search,
  onSearchChange,
  workloadById,
  isLoading,
}: Props) {
  return (
    <div className="flex flex-col rounded-lg border border-border bg-surface shadow-sm">
      <div className="border-b border-border px-3 py-3">
        <div className="mb-2 flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-text-muted">
          <Users size={12} />
          Supervisor
        </div>
        <label className="flex items-center gap-2 rounded-md border border-border bg-surface-hover px-2.5 py-1.5 focus-within:border-accent/60">
          <Search size={13} className="shrink-0 text-text-muted" />
          <input
            type="text"
            placeholder="Filter by name, code, role…"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            className="w-full bg-transparent text-sm text-text-primary placeholder-text-muted focus:outline-none"
          />
        </label>
        <p className="mt-1.5 text-[11px] text-text-muted">
          {options.length} in pool
          {search && ` · ${filteredOptions.length} match`}
        </p>
      </div>

      <div className="max-h-[calc(100vh-280px)] min-h-[200px] overflow-y-auto">
        {isLoading ? (
          <div className="px-4 py-8 text-center text-sm text-text-muted">
            Loading project pool…
          </div>
        ) : options.length === 0 ? (
          <div className="px-4 py-8 text-center text-sm text-text-muted">
            No labor resources in this project&apos;s pool. Add some via the Pool
            sub-tab.
          </div>
        ) : filteredOptions.length === 0 ? (
          <div className="px-4 py-8 text-center text-sm text-text-muted">
            No matches.
          </div>
        ) : (
          <ul className="divide-y divide-border/40">
            {filteredOptions.map((opt) => (
              <SupervisorRow
                key={opt.value}
                option={opt}
                selected={selectedId === opt.value}
                workload={workloadById.get(opt.value) ?? 0}
                onSelect={() => onSelect(opt.value)}
              />
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
