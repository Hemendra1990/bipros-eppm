"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowDown, ArrowUp, FileSpreadsheet, FileText, Users } from "lucide-react";
import toast from "react-hot-toast";

import { SearchableSelect, type SelectOption } from "@/components/common/SearchableSelect";
import { EmptyState } from "@/components/common/EmptyState";
import {
  dbsApi,
  type DbsPeriodType,
  type DbsSupervisorDayResponse,
  type DbsSupervisorSummaryDto,
} from "@/lib/api/dbsApi";
import { dprApi } from "@/lib/api/dprApi";
import { useAuthStore } from "@/lib/state/store";
import { getErrorMessage } from "@/lib/utils/error";
import { formatCurrency, formatPercent } from "@/lib/utils/format";

import { DailyBreakdownTable } from "./DailyBreakdownTable";
import { SectionCard } from "./SectionCard";
import { TotalsPanel } from "./TotalsPanel";

/**
 * Supervisor tab — mirrors the per-supervisor sheets in the client workbook
 * (Anbazhagan-TS, …). Picker is the supervisors who actually have data for
 * the selected date (from {@link dbsApi.listSupervisorsForDay}), so users don't
 * see an empty page for someone who didn't submit a DPR.
 *
 * Selected supervisor + period-type drives either {@link dbsApi.getSupervisorDay}
 * (DAY) or {@link dbsApi.getSupervisorPeriod} (WEEK / MONTH). The DAY response
 * carries the per-section line arrays; for WEEK / MONTH we render the totals'
 * section arrays (backend rolls them up) plus a small daily breakdown table.
 */
export interface SupervisorDbsTabProps {
  projectId: string;
  date: string;
  periodType: DbsPeriodType;
  /** Controlled supervisor selection — owned by the page so it survives tab swaps. */
  supervisorUserId: string;
  onSupervisorChange: (value: string) => void;
  /** Project currency from `budgetApi.getBudgetSummary`. */
  currency?: string | null;
}

/** Sortable columns of the supervisor comparison table. (No DPR-count column: the
 *  backend hardcodes dprCount=1 per supervisor in DAY mode and returns days-with-data
 *  in period mode — neither is a real DPR count, so it isn't shown as a metric.) */
type ComparisonSortKey =
  | "name"
  | "totalIncome"
  | "totalExpense"
  | "contribution"
  | "contributionPct"
  | "directCost"
  | "prelimCost"
  | "totalCostInclPrelims"
  | "pctAchieved";

export function SupervisorDbsTab({
  projectId,
  date,
  periodType,
  supervisorUserId,
  onSupervisorChange,
  currency,
}: SupervisorDbsTabProps) {
  const qc = useQueryClient();
  const hasPermission = useAuthStore((s) => s.hasPermission);
  // Same affordance rule as PmDbsTab — recompute POSTs are gated by DBS.RECOMPUTE
  // on the backend, so don't offer the button to roles that would just get a 403.
  const canRecompute =
    hasPermission("DBS.RECOMPUTE") || hasPermission("PROJECT_MEMBER.MANAGE");
  const [exporting, setExporting] = useState<"xlsx" | "pdf" | null>(null);
  const [sort, setSort] = useState<{ key: ComparisonSortKey; dir: 1 | -1 }>({
    key: "contribution",
    dir: -1,
  });

  const handleExport = async (kind: "xlsx" | "pdf") => {
    if (!projectId || !date || !supervisorUserId) return;
    setExporting(kind);
    try {
      if (kind === "xlsx") {
        await dbsApi.downloadExcel(projectId, date, "SUPERVISOR", supervisorUserId);
      } else {
        await dbsApi.downloadPdf(projectId, date, "SUPERVISOR", supervisorUserId);
      }
    } catch (err) {
      toast.error(getErrorMessage(err, `Export ${kind.toUpperCase()} failed`));
    } finally {
      setExporting(null);
    }
  };

  // Roster of supervisors with data on the chosen date. Backend returns a
  // summary row per supervisor; we feed the picker from this.
  const { data: rosterData, isLoading: rosterLoading } = useQuery({
    // periodType included in the key so WEEK / MONTH share their own cache slot — and so
    // the roster widens to "supervisors with activity in the period" when not in DAY mode.
    // Without this, the focal-date roster was empty whenever the day itself had no DPRs
    // even if the week/month had data.
    queryKey: ["dbs-supervisor-roster", projectId, date, periodType],
    queryFn: () => dbsApi.listSupervisorsForDay(projectId, date, periodType),
    enabled: !!projectId && !!date,
  });
  const roster = useMemo(() => rosterData?.data ?? [], [rosterData]);

  // When the DBS roster is empty, the underlying DPR list may still have rows —
  // the DBS aggregate is just stale. We only probe this when the roster is
  // empty so we don't hit the DPR endpoint on every render.
  const rosterEmpty = !rosterLoading && roster.length === 0;
  const { data: dprData } = useQuery({
    queryKey: ["dpr-list-for-dbs-empty-state", projectId, date],
    queryFn: () => dprApi.list(projectId, { from: date, to: date }),
    enabled: !!projectId && !!date && rosterEmpty,
  });
  const dprsForDate = dprData?.data?.items ?? [];

  // Manual Recompute trigger for the roster-empty / DPRs-exist case. We do
  // NOT auto-fire — the user must opt in (the PM admin Recompute on the PM tab
  // is the canonical one; this is a supervisor-tab convenience).
  const recomputeMutation = useMutation({
    mutationFn: () => dbsApi.recompute(projectId, date),
    onSuccess: () => {
      toast.success("Recompute triggered. Refreshing aggregates…");
      qc.invalidateQueries({ queryKey: ["dbs-supervisor-roster", projectId, date] });
      qc.invalidateQueries({ queryKey: ["dbs-supervisor-day", projectId] });
      qc.invalidateQueries({ queryKey: ["dbs-engineer-day", projectId] });
      qc.invalidateQueries({ queryKey: ["dbs-project-day", projectId, date] });
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Recompute failed"));
    },
  });

  // Auto-select the first supervisor when the roster loads and nothing is picked
  // yet — saves a click in the common "open the page → see today's numbers" flow.
  useEffect(() => {
    if (!supervisorUserId && roster.length > 0 && roster[0].supervisorUserId) {
      onSupervisorChange(roster[0].supervisorUserId);
    }
  }, [roster, supervisorUserId, onSupervisorChange]);

  const supervisorOptions: SelectOption[] = useMemo(
    () =>
      roster
        .filter((s) => !!s.supervisorUserId)
        .map((s) => ({
          value: s.supervisorUserId as string,
          label: `${s.supervisorName ?? "Unnamed"} — ${s.dprCount} DPR${s.dprCount === 1 ? "" : "s"}`,
        })),
    [roster],
  );

  // Comparison table rows — the roster response already carries every financial
  // field; previously only the name fed the picker and the rest was discarded.
  const sortedRoster = useMemo(() => {
    const rows = roster.filter((s) => !!s.supervisorUserId);
    const val = (s: DbsSupervisorSummaryDto): string | number =>
      sort.key === "name" ? s.supervisorName ?? "" : s[sort.key] ?? 0;
    return [...rows].sort((a, b) => {
      const av = val(a);
      const bv = val(b);
      if (typeof av === "string" || typeof bv === "string") {
        return String(av).localeCompare(String(bv)) * sort.dir;
      }
      return (av - bv) * sort.dir;
    });
  }, [roster, sort]);

  const toggleSort = (key: ComparisonSortKey) =>
    setSort((prev) =>
      prev.key === key ? { key, dir: prev.dir === 1 ? -1 : 1 } : { key, dir: -1 },
    );

  // Day vs period query — both run conditionally on `periodType` so we never
  // pay for a query the active mode doesn't need.
  const dayQuery = useQuery({
    queryKey: ["dbs-supervisor-day", projectId, supervisorUserId, date],
    queryFn: () => dbsApi.getSupervisorDay(projectId, supervisorUserId, date),
    enabled: !!projectId && !!supervisorUserId && !!date && periodType === "DAY",
  });

  const periodQuery = useQuery({
    queryKey: ["dbs-supervisor-period", projectId, supervisorUserId, date, periodType],
    queryFn: () =>
      dbsApi.getSupervisorPeriod(projectId, supervisorUserId, date, periodType),
    enabled: !!projectId && !!supervisorUserId && !!date && periodType !== "DAY",
  });

  const boqSummaryQuery = useQuery({
    queryKey: ["dbs-boq-summary", projectId, date, periodType, supervisorUserId],
    queryFn: () => dbsApi.getBoqExecutedSummary(projectId, date, periodType, supervisorUserId),
    enabled: !!projectId && !!date && !!supervisorUserId,
  });

  const day: DbsSupervisorDayResponse | null =
    periodType === "DAY"
      ? dayQuery.data?.data ?? null
      : periodQuery.data?.data?.totals ?? null;

  const dailyRows = periodQuery.data?.data?.dailyRows ?? [];

  const isLoading = periodType === "DAY" ? dayQuery.isLoading : periodQuery.isLoading;

  if (rosterLoading) {
    return <div className="text-center text-text-muted">Loading supervisors…</div>;
  }

  if (roster.length === 0) {
    // When DPRs exist for the date but the DBS aggregate is empty, the
    // event-driven recompute hasn't fired yet (or backfill is pending).
    // Surface a one-click Recompute affordance — clearer than telling the
    // user the date is empty when it isn't.
    if (dprsForDate.length > 0) {
      return (
        <EmptyState
          title="DBS not computed for this date"
          description={
            canRecompute
              ? "No DBS data computed for this date — click Recompute to build the aggregates from the underlying DPRs."
              : "No DBS data computed for this date — ask a Project Manager to run Recompute from the PM tab."
          }
          action={
            canRecompute
              ? {
                  label: recomputeMutation.isPending ? "Recomputing…" : "Recompute now",
                  onClick: () => {
                    if (recomputeMutation.isPending) return;
                    recomputeMutation.mutate();
                  },
                }
              : undefined
          }
        />
      );
    }
    return (
      <EmptyState
        title="No supervisor activity on this date"
        description="No DPRs were submitted by supervisors for the chosen date. Pick another date or check that DPRs have been recorded."
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* Picker row */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="w-full max-w-md">
          <label className="mb-1 block text-xs font-medium uppercase tracking-wide text-text-secondary">
            Supervisor
          </label>
          <SearchableSelect
            options={supervisorOptions}
            value={supervisorUserId}
            onChange={onSupervisorChange}
            placeholder="Pick a supervisor…"
          />
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 text-xs text-text-muted">
            <Users size={14} />
            {roster.length} supervisor{roster.length === 1 ? "" : "s"} with activity
            {periodType !== "DAY" ? ` · ${periodType.toLowerCase()} view` : ""}
          </div>
          {/* Exports for the selected supervisor's sheet. The backend export endpoint is
              day-scoped, so the buttons are disabled in WEEK/MONTH mode — otherwise the
              downloaded sheet would silently carry different numbers than the period view. */}
          <button
            type="button"
            onClick={() => handleExport("xlsx")}
            disabled={exporting !== null || !supervisorUserId || periodType !== "DAY"}
            title={periodType !== "DAY" ? "Exports cover a single day — switch to Day view" : undefined}
            className="inline-flex items-center gap-2 rounded-md border border-border bg-surface px-3 py-1.5 text-sm text-text-primary hover:bg-surface-hover disabled:opacity-60"
          >
            <FileSpreadsheet size={14} />
            {exporting === "xlsx" ? "Exporting…" : "Export Excel"}
          </button>
          <button
            type="button"
            onClick={() => handleExport("pdf")}
            disabled={exporting !== null || !supervisorUserId || periodType !== "DAY"}
            title={periodType !== "DAY" ? "Exports cover a single day — switch to Day view" : undefined}
            className="inline-flex items-center gap-2 rounded-md border border-border bg-surface px-3 py-1.5 text-sm text-text-primary hover:bg-surface-hover disabled:opacity-60"
          >
            <FileText size={14} />
            {exporting === "pdf" ? "Exporting…" : "Export PDF"}
          </button>
        </div>
      </div>

      {!supervisorUserId ? (
        <EmptyState
          title="Choose a supervisor"
          description="Pick a supervisor from the dropdown to see their daily balance sheet."
        />
      ) : isLoading ? (
        <div className="text-center text-text-muted">Loading DBS…</div>
      ) : !day ? (
        <EmptyState
          title="No data"
          description="No DBS data for this selection. The backend may not have computed yet — try the Recompute button from the PM tab."
        />
      ) : (
        <>
          <TotalsPanel
            materialAmount={day.materialAmount}
            manpowerAmount={day.manpowerAmount}
            adminAmount={day.adminAmount}
            machineryAmount={day.machineryAmount}
            fuelAmount={day.fuelAmount}
            subcontractAmount={day.subcontractAmount}
            totalExpense={day.totalExpense}
            totalIncome={day.totalIncome}
            contribution={day.contribution}
            contributionPct={day.contributionPct * 100}
            currency={currency}
            boqItemsExecuted={boqSummaryQuery.data?.data?.boqItemsExecuted}
            boqQtyExecuted={boqSummaryQuery.data?.data?.boqQtyExecuted}
            boqBillableQty={boqSummaryQuery.data?.data?.boqBillableQty}
          />

          {/* Section accordions — order mirrors the client workbook. */}
          <div className="space-y-3">
            <SectionCard
              title="A. Manpower"
              lines={day.manpowerLines}
              total={day.manpowerAmount}
              currency={currency}
            />
            <SectionCard
              title="B. Admin / Catering"
              lines={day.adminLines}
              total={day.adminAmount}
              currency={currency}
            />
            <SectionCard
              title="C. Machinery"
              lines={day.machineryLines}
              total={day.machineryAmount}
              currency={currency}
            />
            <SectionCard
              title="D. Fuel"
              lines={day.fuelLines}
              total={day.fuelAmount}
              currency={currency}
            />
            <SectionCard
              title="E. Material"
              lines={day.materialLines}
              total={day.materialAmount}
              currency={currency}
            />
            {/* F. Sub-Contractor is intentionally hidden on the Supervisor tab.
                SC is a separate domain entity, tracked at the project level
                only — see the PM tab for the SC breakdown. */}
            <SectionCard
              title="BOQ Work executed"
              lines={day.boqLines}
              total={day.boqAchievedAmount}
              currency={currency}
            />
          </div>

          {/* Period-mode daily breakdown — cumulative columns + period totals. */}
          {periodType !== "DAY" && dailyRows.length > 0 ? (
            <DailyBreakdownTable
              rows={dailyRows}
              currency={currency}
              periodLabel={periodType.toLowerCase()}
            />
          ) : null}

          {/* Supervisor comparison — every roster row's financials side by side
              (client workbook DBS-10). Same response that feeds the picker; the
              backend already returns these figures per supervisor. Click a row
              to select that supervisor above. contributionPct arrives as a
              FRACTION (contribution ÷ income), hence the × 100. */}
          <section className="rounded-lg border border-border bg-surface/50 shadow-sm">
            <header className="border-b border-border px-4 py-3">
              <h3 className="text-sm font-semibold text-text-primary">
                Supervisor comparison
                {periodType !== "DAY" ? ` — this ${periodType.toLowerCase()}` : ""}
              </h3>
              <p className="mt-0.5 text-xs text-text-muted">
                Income / Cost / Contribution per supervisor. Click a column to sort, a row to
                open that supervisor.
              </p>
            </header>
            <div className="overflow-x-auto">
              <table className="w-full text-sm" data-testid="supervisor-comparison">
                <thead className="bg-surface/40 text-left text-xs uppercase tracking-wide text-text-muted">
                  <tr>
                    {(
                      [
                        ["name", "Supervisor", "left"],
                        ["totalIncome", "Income", "right"],
                        ["totalExpense", "Cost (Expense)", "right"],
                        ["contribution", "Contribution", "right"],
                        ["contributionPct", "Contribution %", "right"],
                        ["directCost", "Direct BOQ", "right"],
                        ["prelimCost", "Prelim BOQ", "right"],
                        ["totalCostInclPrelims", "BOQ incl Prelims", "right"],
                        ["pctAchieved", "% Achieved", "right"],
                      ] as Array<[ComparisonSortKey, string, "left" | "right"]>
                    ).map(([key, label, align]) => (
                      <th
                        key={key}
                        onClick={() => toggleSort(key)}
                        className={`cursor-pointer select-none px-4 py-2 hover:text-text-primary ${align === "right" ? "text-right" : ""}`}
                      >
                        {label}
                        {sort.key === key ? (
                          sort.dir === -1 ? (
                            <ArrowDown size={11} className="ml-0.5 inline" />
                          ) : (
                            <ArrowUp size={11} className="ml-0.5 inline" />
                          )
                        ) : null}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {sortedRoster.map((s) => {
                    const selected = s.supervisorUserId === supervisorUserId;
                    return (
                      <tr
                        key={s.supervisorUserId}
                        onClick={() => onSupervisorChange(s.supervisorUserId as string)}
                        className={`cursor-pointer hover:bg-surface-hover ${selected ? "bg-surface-hover/70" : ""}`}
                      >
                        <td className="px-4 py-2 font-medium text-text-primary">
                          {s.supervisorName ?? (s.supervisorUserId ?? "").slice(0, 8) + "…"}
                        </td>
                        <td className="px-4 py-2 text-right font-mono text-text-secondary">
                          {formatCurrency(s.totalIncome ?? 0, currency)}
                        </td>
                        <td className="px-4 py-2 text-right font-mono text-text-secondary">
                          {formatCurrency(s.totalExpense ?? 0, currency)}
                        </td>
                        <td
                          className={`px-4 py-2 text-right font-mono ${
                            (s.contribution ?? 0) > 0
                              ? "text-emerald-700"
                              : (s.contribution ?? 0) < 0
                                ? "text-red-700"
                                : "text-text-secondary"
                          }`}
                        >
                          {formatCurrency(s.contribution ?? 0, currency)}
                        </td>
                        <td className="px-4 py-2 text-right font-mono text-text-secondary">
                          {s.contributionPct != null
                            ? formatPercent(s.contributionPct * 100)
                            : "—"}
                        </td>
                        <td className="px-4 py-2 text-right font-mono text-text-secondary">
                          {formatCurrency(s.directCost ?? 0, currency)}
                        </td>
                        <td className="px-4 py-2 text-right font-mono text-text-secondary">
                          {formatCurrency(s.prelimCost ?? 0, currency)}
                        </td>
                        <td className="px-4 py-2 text-right font-mono text-text-secondary">
                          {formatCurrency(s.totalCostInclPrelims ?? 0, currency)}
                        </td>
                        <td className="px-4 py-2 text-right font-mono text-text-secondary">
                          {s.pctAchieved != null ? formatPercent(s.pctAchieved) : "—"}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </div>
  );
}
