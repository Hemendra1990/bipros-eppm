"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  materialConsumptionReportApi,
  type MaterialConsumptionFilters,
  type MaterialConsumptionGroupBy,
  type MaterialConsumptionReportResponse,
  type MaterialConsumptionRow,
} from "@/lib/api/materialConsumptionReportApi";
import { projectApi } from "@/lib/api/projectApi";
import { materialRateMasterApi } from "@/lib/api/materialRateMasterApi";
import { userApi, type UserSummary } from "@/lib/api/userApi";
import { useProjectCurrency } from "@/lib/currency/ProjectCurrencyProvider";
import { formatDate } from "@/lib/utils/format";
import { getErrorMessage } from "@/lib/utils/error";

const ALERT_STYLES: Record<string, string> = {
  NEGATIVE_BALANCE: "bg-rose-500/10 text-rose-600 border-rose-500/30",
  MISSING_UNIT_RATE: "bg-blue-500/10 text-blue-600 border-blue-500/30",
  BELOW_MIN_STOCK: "bg-rose-500/10 text-rose-600 border-rose-500/30",
  LOW_COVER: "bg-amber-500/10 text-amber-700 border-amber-500/30",
};

const GROUP_BY_OPTIONS: Array<{ value: "" | MaterialConsumptionGroupBy; label: string }> = [
  { value: "", label: "No grouping" },
  { value: "DAY", label: "By day" },
  { value: "MATERIAL", label: "By material" },
  { value: "ACTIVITY", label: "By activity" },
  { value: "SUPERVISOR", label: "By supervisor" },
];

const PAGE_SIZES = [25, 50, 100, 0]; // 0 = all

function fmtNum(v: number | null | undefined, digits = 2): string {
  if (v === null || v === undefined) return "—";
  return Number(v).toLocaleString("en-IN", {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
}

function AlertChip({ code }: { code: string }) {
  const style = ALERT_STYLES[code] ?? "bg-surface-active/40 text-text-secondary border-border";
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-semibold tracking-wide ${style}`}
    >
      {code}
    </span>
  );
}

export default function MaterialConsumptionReportPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const { money, moneyCompact } = useProjectCurrency();

  const { data: projectData } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
    enabled: !!projectId,
  });
  const project = projectData?.data;

  const [draft, setDraft] = useState<MaterialConsumptionFilters>({});
  const [applied, setApplied] = useState<MaterialConsumptionFilters>({});

  useEffect(() => {
    if (!project) return;
    if (!draft.from && project.plannedStartDate) {
      const from = project.plannedStartDate;
      const to = project.plannedFinishDate ?? new Date().toISOString().split("T")[0];
      const seeded: MaterialConsumptionFilters = { from, to };
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setDraft(seeded);
      setApplied(seeded);
    }
  }, [project, draft.from]);

  // The seeded security role is STORE_MANAGER ("Storekeeper" is its display concept —
  // no role literally named STOREKEEPER exists, which used to leave this dropdown empty).
  const { data: storekeepers } = useQuery<UserSummary[]>({
    queryKey: ["users-by-role", "STORE_MANAGER"],
    queryFn: () => userApi.listByRoles(["STORE_MANAGER"]),
  });

  const { data: materialRates } = useQuery({
    queryKey: ["material-rate-masters"],
    queryFn: () => materialRateMasterApi.list(),
  });

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ["material-consumption-report", projectId, applied],
    queryFn: () => materialConsumptionReportApi.generate(projectId, applied),
    enabled: !!projectId && !!applied.from && !!applied.to,
  });

  const report: MaterialConsumptionReportResponse | undefined = data?.data ?? undefined;
  const rows = useMemo(() => report?.rows ?? [], [report]);
  const supervisors = useMemo(() => report?.supervisors ?? [], [report]);

  // Material availability (MAT-01) + supervisor issued-vs-reported (MAT-04) — same window.
  const { data: availabilityData } = useQuery({
    queryKey: ["material-availability", projectId, applied.from, applied.to],
    queryFn: () => materialConsumptionReportApi.availability(projectId, applied.from, applied.to),
    enabled: !!projectId && !!applied.from && !!applied.to,
  });
  const availability = availabilityData?.data;
  const { data: comparisonData } = useQuery({
    queryKey: ["material-supervisor-comparison", projectId, applied.to, applied.from],
    queryFn: () =>
      materialConsumptionReportApi.supervisorComparison(projectId, applied.to, applied.from),
    enabled: !!projectId && !!applied.to,
  });
  const comparison = comparisonData?.data ?? [];

  // Pagination
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => setPage(0), [applied, rows.length, pageSize]);
  const total = rows.length;
  const pagedRows = useMemo(
    () => (pageSize === 0 ? rows : rows.slice(page * pageSize, page * pageSize + pageSize)),
    [rows, page, pageSize],
  );
  const pageCount = pageSize === 0 ? 1 : Math.max(1, Math.ceil(total / pageSize));
  const rangeStart = total === 0 ? 0 : pageSize === 0 ? 1 : page * pageSize + 1;
  const rangeEnd = pageSize === 0 ? total : Math.min(total, page * pageSize + pageSize);

  const handleApply = () => setApplied({ ...draft });
  const handleReset = () => {
    const cleared: MaterialConsumptionFilters = { from: draft.from, to: draft.to };
    setDraft(cleared);
    setApplied(cleared);
  };

  const handleExport = async () => {
    try {
      const blob = await materialConsumptionReportApi.downloadExcel(projectId, applied);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `material-consumption-${applied.from ?? "start"}_${applied.to ?? "end"}.xlsx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error("Material consumption Excel export failed", err);
    }
  };

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-semibold">Material Consumption Report</h1>
          <p className="text-sm text-text-muted">
            Issued vs consumed material across the project window, with cost and alerts. Read-only.
          </p>
        </div>
      </div>

      {/* Filter panel */}
      <div className="rounded-lg border border-border bg-surface p-4">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-3 lg:grid-cols-4">
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-text-secondary">From</span>
            <input
              type="date"
              value={draft.from ?? ""}
              onChange={(e) => setDraft({ ...draft, from: e.target.value || undefined })}
              className="rounded border border-border bg-background px-2 py-1"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-text-secondary">To</span>
            <input
              type="date"
              value={draft.to ?? ""}
              onChange={(e) => setDraft({ ...draft, to: e.target.value || undefined })}
              className="rounded border border-border bg-background px-2 py-1"
            />
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span className="text-text-secondary">Supervisor</span>
            <select
              value={draft.supervisorUserId ?? ""}
              onChange={(e) =>
                setDraft({ ...draft, supervisorUserId: e.target.value || undefined })
              }
              className="rounded border border-border bg-background px-2 py-1"
            >
              <option value="">All supervisors</option>
              {supervisors.map((sv) => (
                <option key={sv.userId} value={sv.userId}>
                  {sv.name || sv.userId}
                </option>
              ))}
            </select>
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span className="text-text-secondary">Storekeeper</span>
            {storekeepers && storekeepers.length > 0 ? (
              <select
                value={draft.storekeeperUserId ?? ""}
                onChange={(e) =>
                  setDraft({ ...draft, storekeeperUserId: e.target.value || undefined })
                }
                className="rounded border border-border bg-background px-2 py-1"
              >
                <option value="">All storekeepers</option>
                {storekeepers.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name || u.username}
                  </option>
                ))}
              </select>
            ) : (
              <input
                type="text"
                placeholder="user id (no storekeepers found)"
                value={draft.storekeeperUserId ?? ""}
                onChange={(e) =>
                  setDraft({ ...draft, storekeeperUserId: e.target.value || undefined })
                }
                className="rounded border border-border bg-background px-2 py-1"
              />
            )}
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span className="text-text-secondary">Material</span>
            <select
              value={draft.materialRateMasterId ?? ""}
              onChange={(e) =>
                setDraft({ ...draft, materialRateMasterId: e.target.value || undefined })
              }
              className="rounded border border-border bg-background px-2 py-1"
            >
              <option value="">All materials</option>
              {(materialRates?.data ?? []).map((m) => (
                <option key={m.id} value={m.id}>
                  {m.categoryName ? `${m.categoryName} — ${m.specGrade}` : m.specGrade}
                </option>
              ))}
            </select>
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span className="text-text-secondary">WBS node id</span>
            <input
              type="text"
              placeholder="optional"
              value={draft.wbsNodeId ?? ""}
              onChange={(e) => setDraft({ ...draft, wbsNodeId: e.target.value || undefined })}
              className="rounded border border-border bg-background px-2 py-1"
            />
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span className="text-text-secondary">Activity id</span>
            <input
              type="text"
              placeholder="optional"
              value={draft.activityId ?? ""}
              onChange={(e) => setDraft({ ...draft, activityId: e.target.value || undefined })}
              className="rounded border border-border bg-background px-2 py-1"
            />
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span className="text-text-secondary">Group by</span>
            <select
              value={draft.groupBy ?? ""}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  groupBy: (e.target.value || undefined) as MaterialConsumptionGroupBy | undefined,
                })
              }
              className="rounded border border-border bg-background px-2 py-1"
            >
              {GROUP_BY_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="mt-3 flex gap-2">
          <button
            type="button"
            onClick={handleApply}
            className="rounded bg-accent px-3 py-1.5 text-sm font-medium text-white hover:bg-accent/90"
          >
            Apply filters
          </button>
          <button
            type="button"
            onClick={handleReset}
            className="rounded border border-border bg-surface px-3 py-1.5 text-sm hover:bg-surface-active"
          >
            Reset
          </button>
          <button
            type="button"
            onClick={handleExport}
            disabled={!report}
            className="rounded border border-border bg-surface px-3 py-1.5 text-sm hover:bg-surface-active disabled:opacity-50"
          >
            Export Excel
          </button>
          {(isLoading || isFetching) && (
            <span className="self-center text-xs text-text-muted">Loading…</span>
          )}
        </div>
      </div>

      {/* Totals */}
      {report && (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <SummaryCard label="Actual Cost" value={moneyCompact(report.totals?.actualCost)} />
          <SummaryCard
            label="Wastage % (avg)"
            value={
              report.totals?.wastagePercent_avg === undefined
                ? "—"
                : `${Number(report.totals.wastagePercent_avg).toFixed(2)}%`
            }
          />
        </div>
      )}

      {/* Material availability (store) — MAT-01: receipts / issue / closing balance */}
      {availability && !availability.tracked && (
        <div className="rounded-lg border border-amber-300 bg-amber-500/10 px-3 py-2 text-sm text-amber-800">
          <b>Stock not tracked on this project</b> — storekeeper GRN / issue-slip entries have not
          started, so receipts and closing balance cannot be computed. Consumption below comes from
          approved DPRs.
        </div>
      )}
      {availability?.tracked && availability.rows.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-border bg-surface">
          <div className="px-3 pt-3 text-sm font-medium">Material availability (store)</div>
          <div className="px-3 pb-2 text-xs text-text-muted">
            Received / Issued / Consumed are figures for the filter window; Closing balance and Days
            cover are as of the To date (closing = received − issued; the storekeeper log figure wins
            where entered). Consumed = approved-DPR consumption — the storekeeper log&apos;s figure
            stands in only for materials never captured through DPRs.
          </div>
          <table className="min-w-full text-sm">
            <thead className="bg-surface-active text-xs uppercase tracking-wide text-text-muted">
              <tr>
                <Th>Material</Th>
                <Th>Unit</Th>
                <Th align="right">Received</Th>
                <Th align="right">Issued</Th>
                <Th align="right">Consumed</Th>
                <Th align="right">Closing balance</Th>
                <Th align="right">Days cover</Th>
                <Th>Alerts</Th>
              </tr>
            </thead>
            <tbody>
              {availability.rows.map((r) => (
                <tr key={r.materialKey} className="border-t border-border/60">
                  <Td>{r.materialName ?? "—"}</Td>
                  <Td>{r.unit ?? "—"}</Td>
                  <Td align="right">{fmtNum(r.receivedWindow)}</Td>
                  <Td align="right">{fmtNum(r.issuedWindow)}</Td>
                  <Td align="right">{fmtNum(r.consumedWindow)}</Td>
                  <Td align="right">{fmtNum(r.storeClosing)}</Td>
                  <Td align="right">{r.daysOfCover === null ? "—" : fmtNum(r.daysOfCover, 1)}</Td>
                  <Td>
                    <div className="flex flex-wrap gap-1">
                      {r.alerts.map((a) => (
                        <AlertChip key={a} code={a} />
                      ))}
                    </div>
                  </Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Supervisor-wise issued vs reported — MAT-04 (flag only; DBS costing awaits Q20) */}
      {comparison.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-border bg-surface">
          <div className="px-3 pt-3 text-sm font-medium">
            Supervisor-wise issued material vs DPR-reported
          </div>
          <div className="px-3 pb-2 text-xs text-text-muted">
            Cumulative from the first store movement (GRN / issue slip) to {applied.to} — a
            positive variance means material was issued to the person but not accounted for in
            their approved DPRs; DPR consumption from before store tracking began is not
            compared. Flag only; no DBS cost is adjusted.
          </div>
          <table className="min-w-full text-sm">
            <thead className="bg-surface-active text-xs uppercase tracking-wide text-text-muted">
              <tr>
                <Th>Supervisor</Th>
                <Th>Material</Th>
                <Th>Unit</Th>
                <Th align="right">Issued to date</Th>
                <Th align="right">Reported (DPR)</Th>
                <Th align="right">Variance</Th>
                <Th align="right">Value</Th>
                <Th align="right">Wastage</Th>
              </tr>
            </thead>
            <tbody>
              {comparison.map((v) => (
                <tr key={`${v.supervisorKey}|${v.materialName}`} className="border-t border-border/60">
                  <Td>{v.supervisorName ?? "—"}</Td>
                  <Td>{v.materialName ?? "—"}</Td>
                  <Td>{v.unit ?? "—"}</Td>
                  <Td align="right">{fmtNum(v.issuedToDate)}</Td>
                  <Td align="right">{fmtNum(v.reportedToDate)}</Td>
                  <Td align="right">
                    <span className={v.varianceQty > 0 ? "font-semibold text-rose-600" : undefined}>
                      {fmtNum(v.varianceQty)}
                    </span>
                  </Td>
                  <Td align="right">{v.varianceValue === null ? "—" : money(v.varianceValue)}</Td>
                  <Td align="right">{fmtNum(v.wastageQty)}</Td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {report?.alertCounts && Object.keys(report.alertCounts).length > 0 && (
        <div className="rounded-lg border border-border bg-surface p-3">
          <div className="mb-2 text-sm font-medium">Alerts</div>
          <div className="flex flex-wrap gap-2">
            {Object.entries(report.alertCounts).map(([code, count]) => (
              <span
                key={code}
                className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-semibold ${ALERT_STYLES[code] ?? "bg-surface-active/40 text-text-secondary border-border"}`}
              >
                {code}
                <span className="rounded-full bg-background/70 px-1.5 py-0.5 text-[10px] text-text-secondary">
                  {count}
                </span>
              </span>
            ))}
          </div>
        </div>
      )}

      {error && (
        <div className="rounded border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          {getErrorMessage(error)}
        </div>
      )}

      {/* Data table */}
      <div className="overflow-x-auto rounded-lg border border-border bg-surface">
        <table className="min-w-full text-sm">
          <thead className="bg-surface-active text-xs uppercase tracking-wide text-text-muted">
            <tr>
              <Th>Date</Th>
              <Th>WBS</Th>
              <Th>Activity</Th>
              <Th>Supervisor</Th>
              <Th>Storekeeper</Th>
              <Th>Material</Th>
              <Th>Unit</Th>
              <Th align="right">Issued</Th>
              <Th align="right">Consumed</Th>
              <Th align="right">Balance</Th>
              <Th align="right">Wastage%</Th>
              <Th align="right">Unit Rate</Th>
              <Th align="right">Actual Cost</Th>
              <Th>Alerts</Th>
            </tr>
          </thead>
          <tbody>
            {total === 0 && !isLoading ? (
              <tr>
                <td colSpan={14} className="py-6 text-center text-text-muted">
                  No data for the selected filters.
                </td>
              </tr>
            ) : (
              pagedRows.map((r, idx) => (
                <Row key={idx} row={r} money={money} />
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {total > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
          <div className="flex items-center gap-2">
            <span className="text-text-muted">Rows per page</span>
            <select
              value={pageSize}
              onChange={(e) => setPageSize(Number(e.target.value))}
              className="rounded border border-border bg-background px-2 py-1"
            >
              {PAGE_SIZES.map((sz) => (
                <option key={sz} value={sz}>
                  {sz === 0 ? "All" : sz}
                </option>
              ))}
            </select>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-text-muted">
              {rangeStart}–{rangeEnd} of {total}
            </span>
            <button
              type="button"
              disabled={pageSize === 0 || page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded border border-border bg-surface px-2 py-1 disabled:opacity-40"
            >
              Prev
            </button>
            <span className="text-text-muted">
              {page + 1} / {pageCount}
            </span>
            <button
              type="button"
              disabled={pageSize === 0 || page >= pageCount - 1}
              onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
              className="rounded border border-border bg-surface px-2 py-1 disabled:opacity-40"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function Th({
  children,
  align = "left",
}: {
  children: React.ReactNode;
  align?: "left" | "right";
}) {
  return (
    <th
      className={`whitespace-nowrap px-2 py-2 ${align === "right" ? "text-right" : "text-left"}`}
    >
      {children}
    </th>
  );
}

function Row({
  row,
  money,
}: {
  row: MaterialConsumptionRow;
  money: (amount: number | null | undefined, opts?: { decimals?: number }) => string;
}) {
  const dateLabel =
    row.fromDate && row.toDate && row.fromDate === row.toDate
      ? formatDate(row.fromDate)
      : [row.fromDate, row.toDate].filter(Boolean).map(formatDate).join(" → ");
  return (
    <tr className="border-t border-border hover:bg-surface-active/40">
      <Td>{dateLabel || "—"}</Td>
      <Td>{row.wbsName ?? "—"}</Td>
      <Td>{row.activityName ?? "—"}</Td>
      <Td>{row.supervisorName ?? "—"}</Td>
      <Td>{row.storekeeperName ?? "—"}</Td>
      <Td>{row.materialName ?? "—"}</Td>
      <Td>{row.unit ?? "—"}</Td>
      <Td align="right">{fmtNum(row.issuedQty)}</Td>
      <Td align="right">{fmtNum(row.consumedQty)}</Td>
      <Td align="right">{fmtNum(row.balanceQty)}</Td>
      <Td align="right">{fmtNum(row.wastagePercent)}</Td>
      <Td align="right">{money(row.unitRate)}</Td>
      <Td align="right">{money(row.actualCost)}</Td>
      <Td>
        <div className="flex flex-wrap gap-1">
          {row.alerts?.map((c) => <AlertChip key={c} code={c} />)}
        </div>
      </Td>
    </tr>
  );
}

function Td({
  children,
  align = "left",
  className = "",
}: {
  children: React.ReactNode;
  align?: "left" | "right";
  className?: string;
}) {
  return (
    <td
      className={`whitespace-nowrap px-2 py-1.5 ${align === "right" ? "text-right" : "text-left"} ${className}`}
    >
      {children}
    </td>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border bg-surface p-3">
      <div className="text-xs uppercase tracking-wide text-text-muted">{label}</div>
      <div className="mt-1 text-lg font-semibold text-text-primary">{value}</div>
    </div>
  );
}
