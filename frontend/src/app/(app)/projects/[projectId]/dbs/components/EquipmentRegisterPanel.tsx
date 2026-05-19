"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { EmptyState } from "@/components/common/EmptyState";
import { dbsApi, type CmShiftCount } from "@/lib/api/dbsApi";

/**
 * Phase 8 — Equipment Deployment Register panel.
 *
 * Two display modes (toggle in the header):
 *   - **Today** — `GET /v1/projects/{projectId}/dbs/register/equipment?date=...`
 *     shows one row per equipment type with Day / Night sub-columns per CM.
 *   - **Cumulative** — `GET /v1/projects/{projectId}/dbs/register/cumulative?asOf=...`
 *     collapses to a two-column table (`Equipment | Cumulative Days`) since the
 *     cumulative endpoint does not carry CM / shift breakdown.
 *
 * The CM-column header is built from the *union* of CMs that appear in any of
 * the equipment rows on the chosen date — keeps the table compact when only a
 * subset of CMs deployed equipment.
 */
export interface EquipmentRegisterPanelProps {
  projectId: string;
  date: string;
}

type Mode = "today" | "cumulative";

export function EquipmentRegisterPanel({ projectId, date }: EquipmentRegisterPanelProps) {
  const [mode, setMode] = useState<Mode>("today");

  const todayQuery = useQuery({
    queryKey: ["dbs-equipment-register", projectId, date],
    queryFn: () => dbsApi.getEquipmentRegister(projectId, date),
    enabled: !!projectId && !!date && mode === "today",
  });

  const cumulativeQuery = useQuery({
    queryKey: ["dbs-cumulative-days", projectId, date],
    queryFn: () => dbsApi.getCumulative(projectId, date),
    enabled: !!projectId && !!date && mode === "cumulative",
  });

  // Memoise these so they're stable identities — drives the deps for `cmColumns`
  // below and the React Compiler's manual-memoization check.
  const equipmentRows = useMemo(
    () => todayQuery.data?.data?.equipment ?? [],
    [todayQuery.data],
  );
  const cumulativeRows = useMemo(
    () => cumulativeQuery.data?.data?.equipment ?? [],
    [cumulativeQuery.data],
  );

  // Union of CM columns across every equipment row — keeps the header stable
  // even when one row has more CMs than another, and renders zeroes for gaps.
  const cmColumns = useMemo(() => {
    const map = new Map<string, string>(); // cmUserId -> cmName
    for (const row of equipmentRows) {
      for (const cm of row.byCm) {
        if (!map.has(cm.cmUserId)) map.set(cm.cmUserId, cm.cmName ?? "CM");
      }
    }
    return Array.from(map.entries()).map(([cmUserId, cmName]) => ({ cmUserId, cmName }));
  }, [equipmentRows]);

  const lookupCm = (row: { byCm: CmShiftCount[] }, cmUserId: string) =>
    row.byCm.find((c) => c.cmUserId === cmUserId);

  return (
    <section className="rounded-lg border border-border bg-surface/50 shadow-sm">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3">
        <div>
          <h3 className="text-sm font-semibold text-text-primary">
            Equipment Deployment Register
          </h3>
          <p className="mt-0.5 text-xs text-text-muted">
            Per equipment type, sliced by Construction Manager and shift.
            Toggle Cumulative to see total deployment-days since project start.
          </p>
        </div>
        <div className="inline-flex overflow-hidden rounded-md border border-border bg-surface">
          <button
            type="button"
            onClick={() => setMode("today")}
            className={`px-3 py-1.5 text-xs font-semibold transition-colors ${
              mode === "today"
                ? "bg-accent text-accent-foreground"
                : "text-text-secondary hover:bg-surface-hover"
            }`}
          >
            Today
          </button>
          <button
            type="button"
            onClick={() => setMode("cumulative")}
            className={`px-3 py-1.5 text-xs font-semibold transition-colors ${
              mode === "cumulative"
                ? "bg-accent text-accent-foreground"
                : "text-text-secondary hover:bg-surface-hover"
            }`}
          >
            Cumulative
          </button>
        </div>
      </header>

      {mode === "today" ? (
        todayQuery.isLoading ? (
          <div className="px-4 py-6 text-center text-xs text-text-muted">Loading register…</div>
        ) : equipmentRows.length === 0 ? (
          <EmptyState
            title="No equipment deployed on this date"
            description="No DPR equipment rows submitted for this date — nothing to roll up."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm" data-testid="equipment-register-today">
              <thead className="bg-surface/40 text-left text-xs uppercase tracking-wide text-text-muted">
                <tr>
                  <th className="px-4 py-2">Equipment</th>
                  <th className="px-4 py-2 text-right">Total</th>
                  {cmColumns.map((cm) => (
                    <th
                      key={cm.cmUserId}
                      colSpan={2}
                      className="border-l border-border px-4 py-2 text-center"
                      title={cm.cmName}
                    >
                      {cm.cmName}
                    </th>
                  ))}
                </tr>
                <tr className="text-[10px] uppercase tracking-wide text-text-muted">
                  <th className="px-4 pb-2"></th>
                  <th className="px-4 pb-2 text-right">Day+Night</th>
                  {cmColumns.flatMap((cm) => [
                    <th
                      key={`${cm.cmUserId}-day`}
                      className="border-l border-border px-2 pb-2 text-right"
                    >
                      Day
                    </th>,
                    <th key={`${cm.cmUserId}-night`} className="px-2 pb-2 text-right">
                      Night
                    </th>,
                  ])}
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {equipmentRows.map((row) => (
                  <tr key={row.type}>
                    <td className="px-4 py-2 font-medium text-text-primary">{row.type}</td>
                    <td className="px-4 py-2 text-right font-mono font-semibold text-text-primary">
                      {row.total}
                    </td>
                    {cmColumns.flatMap((cm) => {
                      const hit = lookupCm(row, cm.cmUserId);
                      return [
                        <td
                          key={`${row.type}-${cm.cmUserId}-day`}
                          className="border-l border-border px-2 py-2 text-right font-mono text-text-secondary"
                        >
                          {hit?.day ?? 0}
                        </td>,
                        <td
                          key={`${row.type}-${cm.cmUserId}-night`}
                          className="px-2 py-2 text-right font-mono text-text-secondary"
                        >
                          {hit?.night ?? 0}
                        </td>,
                      ];
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : cumulativeQuery.isLoading ? (
        <div className="px-4 py-6 text-center text-xs text-text-muted">
          Loading cumulative days…
        </div>
      ) : cumulativeRows.length === 0 ? (
        <EmptyState
          title="No cumulative equipment-days yet"
          description="No equipment has been deployed on this project as of the chosen date."
        />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm" data-testid="equipment-register-cumulative">
            <thead className="bg-surface/40 text-left text-xs uppercase tracking-wide text-text-muted">
              <tr>
                <th className="px-4 py-2">Equipment</th>
                <th className="px-4 py-2 text-right">Cumulative Days</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {cumulativeRows.map((row) => (
                <tr key={row.type}>
                  <td className="px-4 py-2 font-medium text-text-primary">{row.type}</td>
                  <td className="px-4 py-2 text-right font-mono font-semibold text-text-primary">
                    {row.days}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
