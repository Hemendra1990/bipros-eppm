"use client";

import { Wallet, TrendingDown, TrendingUp } from "lucide-react";
import { MetricTile } from "../MetricTile";
import { MetricNumber } from "../primitives/MetricNumber";
import { Sparkline } from "../primitives/Sparkline";
import type { CashFlowOutlookPoint } from "@/lib/api/portfolioReportApi";
import { formatMoney, resolveCurrencyMeta } from "@/lib/currency/format";

interface Props {
  points: CashFlowOutlookPoint[] | null;
}

export function CashFlowTile({ points }: Props) {
  const all = points ?? [];
  // Pick dominant currency (most data points), filter to it — no FX, never mix currencies
  const counts = all.reduce(
    (m, p) => {
      const c = p.currency ?? "INR";
      m[c] = (m[c] ?? 0) + 1;
      return m;
    },
    {} as Record<string, number>,
  );
  const cur = Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] ?? "INR";
  const series = all.filter((p) => (p.currency ?? cur) === cur);
  const meta = resolveCurrencyMeta(cur);
  const fmt = (n: number) => formatMoney(n, meta, { compact: true });

  const hasMovement = series.some((p) => p.netRaw !== 0);
  const latest = series[series.length - 1] ?? null;
  const prev = series[series.length - 2] ?? null;
  const values = series.map((p) => p.netRaw);
  const net = latest?.netRaw ?? 0;
  const delta = latest && prev ? latest.netRaw - prev.netRaw : 0;
  const TrendIcon = delta >= 0 ? TrendingUp : TrendingDown;
  const trendTone = delta >= 0 ? "text-emerald" : "text-burgundy";

  return (
    <MetricTile
      title="Cash flow · this month"
      icon={Wallet}
      href="/reports/cash-flow"
      testid="mc-tile-cashflow"
    >
      {hasMovement ? (
        <>
          <div className="flex items-baseline gap-2">
            <MetricNumber
              value={net}
              format={fmt}
              className="font-display text-[34px] font-semibold leading-none tracking-tight text-charcoal tabular-nums"
              style={{ fontVariationSettings: "'opsz' 144" }}
            />
            <span
              className={`inline-flex items-center gap-1 text-[12px] font-semibold tabular-nums ${trendTone}`}
            >
              <TrendIcon size={14} strokeWidth={2} />
              {delta >= 0 ? "+" : ""}
              {fmt(delta)}
            </span>
          </div>
          <div className="mt-4">
            <Sparkline values={values} width={240} height={36} />
            <div className="mt-1.5 flex justify-between text-[10px] font-medium uppercase tracking-[0.14em] text-ash">
              <span>{series[0]?.yearMonth ?? "—"}</span>
              <span>{latest?.yearMonth ?? "—"}</span>
            </div>
          </div>
        </>
      ) : (
        <>
          <div className="flex items-baseline gap-2">
            <span
              className="font-display text-[34px] font-semibold leading-none tracking-tight text-ash tabular-nums"
              style={{ fontVariationSettings: "'opsz' 144" }}
            >
              —
            </span>
            <span className="text-[12px] font-medium text-slate">no movement yet</span>
          </div>
          <p className="mt-4 text-[12px] text-slate">
            Cash flow will appear once the first RA bills are released.
          </p>
        </>
      )}
    </MetricTile>
  );
}
