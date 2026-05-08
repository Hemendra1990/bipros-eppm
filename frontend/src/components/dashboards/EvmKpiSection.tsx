"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { evmKpiApi, type WbsEvmNode } from "@/lib/api/evmKpiApi";

interface Props {
  projectId: string;
  density?: "compact" | "full";
}

type SortKey = "name" | "bac" | "pv" | "ev" | "ac" | "spi" | "cpi" | "vac";

function flattenLeaves(nodes: WbsEvmNode[]): WbsEvmNode[] {
  const out: WbsEvmNode[] = [];
  const walk = (n: WbsEvmNode) => {
    if (!n.children || n.children.length === 0) {
      out.push(n);
    } else {
      n.children.forEach(walk);
    }
  };
  nodes.forEach(walk);
  return out;
}

function formatRupees(value: number | null | undefined, fractionDigits = 0): string {
  if (value == null || Number.isNaN(value)) return "—";
  return `₹${value.toLocaleString("en-IN", {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })}`;
}

function formatIndex(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value) || value === 0) return "—";
  return value.toFixed(2);
}

function indexClass(value: number | null | undefined): string {
  if (value == null || value === 0) return "text-text-primary";
  if (value < 0.9) return "text-danger";
  if (value > 1.1) return "text-success";
  return "text-text-primary";
}

export function EvmKpiSection({ projectId, density = "compact" }: Props) {
  const { data, isLoading, error } = useQuery({
    queryKey: ["evm-kpis", projectId],
    queryFn: () => evmKpiApi.getKpis(projectId),
    enabled: !!projectId,
    retry: 1,
  });

  if (!projectId) return null;
  if (isLoading) {
    return (
      <div className="rounded-lg border border-border bg-surface/40 p-4 text-sm text-text-muted">
        Loading EVM KPIs…
      </div>
    );
  }
  if (error || !data?.data) {
    return (
      <div className="rounded-lg border border-border bg-surface/40 p-4 text-sm text-text-muted">
        EVM KPIs not yet calculated for this project. Run an EVM calculation to populate this
        section.
      </div>
    );
  }

  const evm = data.data;
  const hasData =
    (evm.budgetAtCompletion ?? 0) > 0 || (evm.earnedValue ?? 0) > 0 || (evm.actualCost ?? 0) > 0;

  if (!hasData) {
    return (
      <div className="rounded-lg border border-border bg-surface/40 p-4 text-sm text-text-muted">
        EVM not yet calculated. Use the EVM module to run a calculation, then this section will
        show BAC / PV / EV / AC / SPI / CPI / EAC / VAC.
      </div>
    );
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-semibold text-text-primary">EVM KPIs</h2>
        <div className="text-[11px] text-text-muted">
          {evm.evmTechnique ?? "—"} · ETC: {evm.etcMethod ?? "—"}
        </div>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Kpi label="BAC" value={formatRupees(evm.budgetAtCompletion)} hint="Budget at Completion" />
        <Kpi label="PV" value={formatRupees(evm.plannedValue)} hint="KPI 10.1 — BAC × Planned %" />
        <Kpi label="EV" value={formatRupees(evm.earnedValue)} hint="KPI 10.2 — BAC × Actual %" />
        <Kpi label="AC" value={formatRupees(evm.actualCost)} hint="KPI 10.3 — Actual costs to date" />
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Kpi
          label="SV"
          value={formatRupees(evm.scheduleVariance)}
          hint="KPI 10.4 — EV − PV (negative = behind schedule)"
          accent={evm.scheduleVariance != null && evm.scheduleVariance < 0 ? "danger" : "default"}
        />
        <Kpi
          label="CV"
          value={formatRupees(evm.costVariance)}
          hint="KPI 10.5 — EV − AC (negative = over budget)"
          accent={evm.costVariance != null && evm.costVariance < 0 ? "danger" : "default"}
        />
        <Kpi
          label="SPI"
          value={formatIndex(evm.schedulePerformanceIndex)}
          hint="KPI 10.6 — EV ÷ PV (1.0 = on plan)"
          accentClass={indexClass(evm.schedulePerformanceIndex)}
        />
        <Kpi
          label="CPI"
          value={formatIndex(evm.costPerformanceIndex)}
          hint="KPI 10.7 — EV ÷ AC (1.0 = on budget)"
          accentClass={indexClass(evm.costPerformanceIndex)}
        />
      </div>

      {density === "full" && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Kpi label="EAC" value={formatRupees(evm.estimateAtCompletion)} hint="KPI 10.8 — Forecast at completion" />
          <Kpi label="ETC" value={formatRupees(evm.estimateToComplete)} hint="Estimate to complete" />
          <Kpi
            label="VAC"
            value={formatRupees(evm.varianceAtCompletion)}
            hint="KPI 10.9 — BAC − EAC"
            accent={evm.varianceAtCompletion != null && evm.varianceAtCompletion < 0 ? "danger" : "default"}
          />
          <Kpi
            label="TCPI"
            value={formatIndex(evm.toCompletePerformanceIndex)}
            hint="KPI 10.10 — (BAC − EV) ÷ (BAC − AC)"
            accentClass={indexClass(evm.toCompletePerformanceIndex)}
          />
        </div>
      )}

      {density === "full" && <EvmActivityTable projectId={projectId} />}
    </section>
  );
}

/**
 * Per-activity (WBS leaf) EVM table — KPI Framework SCR-001 requires "Activity-wise
 * PV/EV/AC/SPI/CPI". Sortable client-side; reads /v1/projects/{id}/evm/wbs-tree and
 * flattens the tree to leaves so each row is one trackable activity / WBS bottom node.
 */
function EvmActivityTable({ projectId }: { projectId: string }) {
  const [sortKey, setSortKey] = useState<SortKey>("spi");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");

  const { data, isLoading, error } = useQuery({
    queryKey: ["evm-wbs-tree", projectId],
    queryFn: () => evmKpiApi.getWbsTree(projectId),
    enabled: !!projectId,
    retry: 1,
  });

  const leaves = useMemo(() => flattenLeaves(data?.data ?? []), [data]);

  const sorted = useMemo(() => {
    const get = (n: WbsEvmNode): number | string | null => {
      switch (sortKey) {
        case "name": return n.name ?? "";
        case "bac":  return n.budgetAtCompletion;
        case "pv":   return n.plannedValue;
        case "ev":   return n.earnedValue;
        case "ac":   return n.actualCost;
        case "spi":  return n.schedulePerformanceIndex;
        case "cpi":  return n.costPerformanceIndex;
        case "vac":  return n.varianceAtCompletion;
      }
    };
    const dir = sortDir === "asc" ? 1 : -1;
    return [...leaves].sort((a, b) => {
      const va = get(a);
      const vb = get(b);
      if (va == null && vb == null) return 0;
      if (va == null) return 1;
      if (vb == null) return -1;
      if (typeof va === "string" && typeof vb === "string") return va.localeCompare(vb) * dir;
      return ((va as number) - (vb as number)) * dir;
    });
  }, [leaves, sortKey, sortDir]);

  const onSort = (key: SortKey) => {
    if (key === sortKey) setSortDir(sortDir === "asc" ? "desc" : "asc");
    else { setSortKey(key); setSortDir(key === "name" ? "asc" : "asc"); }
  };

  if (isLoading) return null;
  if (error || leaves.length === 0) return null;

  const Header = ({ k, label, right = false }: { k: SortKey; label: string; right?: boolean }) => (
    <th className={`pb-1 cursor-pointer select-none ${right ? "text-right" : "text-left"}`} onClick={() => onSort(k)}>
      {label}{sortKey === k ? (sortDir === "asc" ? " ▲" : " ▼") : ""}
    </th>
  );

  return (
    <div className="rounded-lg border border-border bg-surface/40 p-4">
      <h3 className="text-sm font-semibold text-text-primary mb-2">
        EVM by activity / WBS leaf
        <span className="ml-2 text-xs font-normal text-text-muted">{leaves.length} rows · click headers to sort</span>
      </h3>
      <table className="w-full text-xs">
        <thead className="text-text-muted">
          <tr>
            <Header k="name" label="Activity / WBS" />
            <Header k="bac"  label="BAC"  right />
            <Header k="pv"   label="PV"   right />
            <Header k="ev"   label="EV"   right />
            <Header k="ac"   label="AC"   right />
            <Header k="spi"  label="SPI"  right />
            <Header k="cpi"  label="CPI"  right />
            <Header k="vac"  label="VAC"  right />
          </tr>
        </thead>
        <tbody>
          {sorted.map((n) => (
            <tr key={n.id} className="text-text-primary">
              <td className="py-1 truncate max-w-[260px]" title={n.name}>
                <span className="text-text-muted mr-2">{n.code}</span>{n.name}
              </td>
              <td className="py-1 text-right">{formatRupees(n.budgetAtCompletion)}</td>
              <td className="py-1 text-right">{formatRupees(n.plannedValue)}</td>
              <td className="py-1 text-right">{formatRupees(n.earnedValue)}</td>
              <td className="py-1 text-right">{formatRupees(n.actualCost)}</td>
              <td className={`py-1 text-right ${indexClass(n.schedulePerformanceIndex)}`}>
                {formatIndex(n.schedulePerformanceIndex)}
              </td>
              <td className={`py-1 text-right ${indexClass(n.costPerformanceIndex)}`}>
                {formatIndex(n.costPerformanceIndex)}
              </td>
              <td className={`py-1 text-right ${n.varianceAtCompletion != null && n.varianceAtCompletion < 0 ? "text-danger" : ""}`}>
                {formatRupees(n.varianceAtCompletion)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Kpi({
  label,
  value,
  hint,
  accent,
  accentClass,
}: {
  label: string;
  value: string;
  hint: string;
  accent?: "default" | "danger" | "success";
  accentClass?: string;
}) {
  const cls = accentClass
    ? accentClass
    : accent === "danger"
      ? "text-danger"
      : accent === "success"
        ? "text-success"
        : "text-text-primary";
  return (
    <div className="rounded-lg border border-border bg-surface/50 p-4">
      <div className="text-xs uppercase tracking-wide text-text-muted">{label}</div>
      <div className={`mt-1 text-2xl font-semibold ${cls}`} title={hint}>
        {value}
      </div>
      <div className="mt-1 text-[11px] text-text-secondary">{hint}</div>
    </div>
  );
}
