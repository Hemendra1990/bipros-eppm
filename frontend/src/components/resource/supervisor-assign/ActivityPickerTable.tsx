"use client";

import { Search, ListChecks } from "lucide-react";
import { type ActivityResponse } from "@/lib/api/activityApi";
import { StatusBadge } from "@/components/common/StatusBadge";

export type ActivityFilter = "all" | "unassigned" | "has-supervisor" | "conflicts";

interface FilterCounts {
  all: number;
  unassigned: number;
  hasSupervisor: number;
  conflicts: number;
}

interface Props {
  activities: ActivityResponse[];
  filteredActivities: ActivityResponse[];
  totalActivityCount: number;
  filterCounts: FilterCounts;
  selectedSupervisorId: string;
  checkedActivityIds: Set<string>;
  onToggleActivity: (id: string) => void;
  onToggleAll: () => void;
  search: string;
  onSearchChange: (value: string) => void;
  filter: ActivityFilter;
  onFilterChange: (filter: ActivityFilter) => void;
  isLoading: boolean;
}

const formatDate = (value: string | null | undefined) => {
  if (!value) return "—";
  const d = new Date(value);
  return d.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
};

interface PillProps {
  label: string;
  count: number;
  active: boolean;
  onClick: () => void;
  tone?: "neutral" | "warning";
}

function FilterPill({ label, count, active, onClick, tone = "neutral" }: PillProps) {
  const base =
    "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors";
  const activeCls =
    tone === "warning"
      ? "border-warning/60 bg-warning/15 text-warning"
      : "border-accent/60 bg-accent-glow text-text-primary";
  const inactiveCls =
    "border-border bg-surface text-text-secondary hover:bg-surface-hover";
  return (
    <button
      type="button"
      onClick={onClick}
      className={`${base} ${active ? activeCls : inactiveCls}`}
    >
      <span>{label}</span>
      <span
        className={`rounded-full px-1.5 text-[10px] ${
          active
            ? tone === "warning"
              ? "bg-warning/25"
              : "bg-accent/20"
            : "bg-surface-hover text-text-muted"
        }`}
      >
        {count}
      </span>
    </button>
  );
}

export function ActivityPickerTable({
  activities,
  filteredActivities,
  totalActivityCount,
  filterCounts,
  selectedSupervisorId,
  checkedActivityIds,
  onToggleActivity,
  onToggleAll,
  search,
  onSearchChange,
  filter,
  onFilterChange,
  isLoading,
}: Props) {
  const allFilteredChecked =
    filteredActivities.length > 0 &&
    filteredActivities.every((a) => checkedActivityIds.has(a.id));
  const showConflictsPill = !!selectedSupervisorId && filterCounts.conflicts > 0;

  return (
    <div className="flex flex-col rounded-lg border border-border bg-surface shadow-sm">
      <div className="space-y-3 border-b border-border px-4 py-3">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-text-muted">
            <ListChecks size={12} />
            Activities
            <span className="ml-1 normal-case tracking-normal text-text-secondary">
              · {checkedActivityIds.size} of {filteredActivities.length} shown selected
              <span className="text-text-muted"> ({totalActivityCount} total)</span>
            </span>
          </div>
          <button
            type="button"
            onClick={onToggleAll}
            disabled={filteredActivities.length === 0}
            className="text-xs font-medium text-accent hover:underline disabled:cursor-not-allowed disabled:text-text-muted disabled:no-underline"
          >
            {allFilteredChecked ? "Deselect shown" : "Select all shown"}
          </button>
        </div>

        <label className="flex items-center gap-2 rounded-md border border-border bg-surface-hover px-2.5 py-1.5 focus-within:border-accent/60">
          <Search size={13} className="shrink-0 text-text-muted" />
          <input
            type="text"
            placeholder="Search activity by code or name…"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            className="w-full bg-transparent text-sm text-text-primary placeholder-text-muted focus:outline-none"
          />
        </label>

        <div className="flex flex-wrap items-center gap-1.5">
          <FilterPill
            label="All"
            count={filterCounts.all}
            active={filter === "all"}
            onClick={() => onFilterChange("all")}
          />
          <FilterPill
            label="Unassigned"
            count={filterCounts.unassigned}
            active={filter === "unassigned"}
            onClick={() => onFilterChange("unassigned")}
          />
          <FilterPill
            label="Has supervisor"
            count={filterCounts.hasSupervisor}
            active={filter === "has-supervisor"}
            onClick={() => onFilterChange("has-supervisor")}
          />
          {showConflictsPill && (
            <FilterPill
              label="Will replace"
              count={filterCounts.conflicts}
              active={filter === "conflicts"}
              onClick={() => onFilterChange("conflicts")}
              tone="warning"
            />
          )}
        </div>
      </div>

      <div className="max-h-[calc(100vh-340px)] min-h-[280px] overflow-auto">
        {isLoading ? (
          <div className="px-4 py-12 text-center text-sm text-text-muted">
            Loading activities…
          </div>
        ) : activities.length === 0 ? (
          <div className="px-4 py-12 text-center text-sm text-text-muted">
            No activities in this project.
          </div>
        ) : filteredActivities.length === 0 ? (
          <div className="px-4 py-12 text-center text-sm text-text-muted">
            No activities match the current filter.
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="sticky top-0 z-10 border-b border-border bg-surface/95 backdrop-blur">
              <tr>
                <th className="w-10 px-4 py-2 text-left text-xs font-medium text-text-secondary"></th>
                <th className="whitespace-nowrap px-4 py-2 text-left text-xs font-medium text-text-secondary">
                  Code
                </th>
                <th className="px-4 py-2 text-left text-xs font-medium text-text-secondary">
                  Name
                </th>
                <th className="whitespace-nowrap px-4 py-2 text-left text-xs font-medium text-text-secondary">
                  Status
                </th>
                <th className="whitespace-nowrap px-4 py-2 text-left text-xs font-medium text-text-secondary">
                  Planned Start
                </th>
                <th className="whitespace-nowrap px-4 py-2 text-left text-xs font-medium text-text-secondary">
                  Planned Finish
                </th>
                <th className="whitespace-nowrap px-4 py-2 text-right text-xs font-medium text-text-secondary">
                  % Complete
                </th>
                <th className="whitespace-nowrap px-4 py-2 text-left text-xs font-medium text-text-secondary">
                  Current Supervisor
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/40">
              {filteredActivities.map((a) => {
                const checked = checkedActivityIds.has(a.id);
                const matchesPicked =
                  selectedSupervisorId !== "" &&
                  a.responsibleResourceId === selectedSupervisorId &&
                  a.responsibleResourceId != null;
                const isConflict =
                  checked &&
                  selectedSupervisorId !== "" &&
                  a.responsibleResourceId != null &&
                  a.responsibleResourceId !== selectedSupervisorId;
                return (
                  <tr
                    key={a.id}
                    onClick={() => onToggleActivity(a.id)}
                    className={`cursor-pointer transition-colors hover:bg-surface-hover/50 ${
                      isConflict ? "bg-warning/10" : checked ? "bg-accent-glow/60" : ""
                    }`}
                  >
                    <td className="px-4 py-2">
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => onToggleActivity(a.id)}
                        onClick={(e) => e.stopPropagation()}
                        className="rounded border-border accent-accent"
                        aria-label={`Toggle ${a.code}`}
                      />
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-text-secondary">
                      {a.code}
                    </td>
                    <td className="px-4 py-2 text-text-primary">{a.name}</td>
                    <td className="whitespace-nowrap px-4 py-2">
                      <StatusBadge status={a.status} />
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-text-secondary">
                      {formatDate(a.plannedStartDate)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-text-secondary">
                      {formatDate(a.plannedFinishDate)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-right text-text-primary">
                      {a.percentComplete ?? 0}%
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-text-secondary">
                      {a.responsibleResourceName ? (
                        <span
                          className={
                            matchesPicked
                              ? "font-medium text-accent"
                              : isConflict
                                ? "text-warning"
                                : "text-text-secondary"
                          }
                        >
                          {a.responsibleResourceName}
                          {matchesPicked && (
                            <span className="ml-1 text-xs text-text-muted">
                              (current)
                            </span>
                          )}
                          {isConflict && (
                            <span className="ml-1 text-xs font-semibold">
                              (will replace)
                            </span>
                          )}
                        </span>
                      ) : (
                        <span className="text-text-muted">—</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
