"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { llmUsageApi } from "@/lib/api/llmUsageApi";
import type { UsageDailyRow, UsageSummaryResponse } from "@/lib/types";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input, Field, Label } from "@/components/ui/input";

/** ISO YYYY-MM-DD (UTC) for an instant N days back from now. */
function isoDate(daysBack: number): string {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - daysBack);
  return d.toISOString().slice(0, 10);
}

function formatUsd(costMicros: number): string {
  // costMicros = micro-USD (1_000_000 = $1).
  const dollars = costMicros / 1_000_000;
  return `$${dollars.toLocaleString(undefined, {
    minimumFractionDigits: 4,
    maximumFractionDigits: 4,
  })}`;
}

function formatInt(n: number): string {
  return n.toLocaleString();
}

function dayLabel(iso: string): string {
  return iso.slice(5, 10); // MM-DD
}

/** Pivot daily rows into one chart-ready row per day with one column per provider. */
function pivot(daily: UsageDailyRow[]): {
  rows: Array<Record<string, number | string>>;
  providers: string[];
} {
  const providerSet = new Set<string>();
  const byDay = new Map<string, Record<string, number | string>>();
  for (const r of daily) {
    providerSet.add(r.provider);
    const dayIso = r.day.slice(0, 10);
    const existing = byDay.get(dayIso) ?? { day: dayIso };
    const tokens = r.tokensIn + r.tokensOut;
    existing[r.provider] = ((existing[r.provider] as number) ?? 0) + tokens;
    byDay.set(dayIso, existing);
  }
  return {
    rows: [...byDay.values()].sort((a, b) =>
      String(a.day).localeCompare(String(b.day)),
    ),
    providers: [...providerSet].sort(),
  };
}

const PROVIDER_COLORS: Record<string, string> = {
  ANTHROPIC: "#C9803A",     // gold-deep
  OPENAI: "#4A8A4A",
  GOOGLE: "#3A6FA0",
  OLLAMA: "#7A6FA0",
  AZURE_OPENAI: "#A04A6F",
  MISTRAL: "#A0863A",
};
const FALLBACK_COLOR = "#828282";

export default function UsageTab() {
  const [from, setFrom] = useState<string>(isoDate(29));
  const [to, setTo] = useState<string>(isoDate(0));

  const { data, isLoading, isError, error, refetch } = useQuery<UsageSummaryResponse | null>({
    queryKey: ["llm-usage", from, to],
    queryFn: async () => (await llmUsageApi.fetchMyUsage({ from, to })).data ?? null,
  });

  const { rows: chartRows, providers } = useMemo(
    () => pivot(data?.daily ?? []),
    [data?.daily],
  );

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Usage</CardTitle>
          <CardDescription>
            Tokens and cost from your queries against the analytics assistant.
            BYOK — these are billed to <em>your</em> provider key, not the platform.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap items-end gap-4 mb-6">
            <Field className="w-[160px]">
              <Label>From</Label>
              <Input
                type="date"
                value={from}
                max={to}
                onChange={(e) => setFrom(e.target.value)}
              />
            </Field>
            <Field className="w-[160px]">
              <Label>To</Label>
              <Input
                type="date"
                value={to}
                min={from}
                max={isoDate(0)}
                onChange={(e) => setTo(e.target.value)}
              />
            </Field>
            <button
              onClick={() => refetch()}
              className="h-10 px-4 rounded-[10px] border border-divider bg-paper text-sm text-charcoal hover:bg-hairline/40"
            >
              Refresh
            </button>
          </div>

          {isLoading ? (
            <div className="text-sm text-slate py-12 text-center">Loading usage…</div>
          ) : isError ? (
            <div className="text-sm text-burgundy py-8 text-center">
              Couldn&rsquo;t load usage:{" "}
              {(error as { message?: string })?.message ?? "unknown error"}
            </div>
          ) : !data || data.daily.length === 0 ? (
            <div className="text-sm text-slate py-12 text-center">
              No queries in the selected range.
            </div>
          ) : (
            <>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 mb-6">
                <KpiTile label="Queries" value={formatInt(data.totals.queryCount)} />
                <KpiTile label="Tokens in" value={formatInt(data.totals.tokensIn)} />
                <KpiTile label="Tokens out" value={formatInt(data.totals.tokensOut)} />
                <KpiTile label="Cost" value={formatUsd(data.totals.costMicros)} />
              </div>

              <div className="h-[320px]">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={chartRows} margin={{ top: 8, right: 16, left: 4, bottom: 4 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#E6E1D6" />
                    <XAxis
                      dataKey="day"
                      tickFormatter={dayLabel}
                      stroke="#828282"
                      fontSize={12}
                    />
                    <YAxis stroke="#828282" fontSize={12} tickFormatter={formatInt} />
                    <Tooltip
                      formatter={(v) => formatInt(Number(v ?? 0))}
                      labelFormatter={(l) => `Day ${l}`}
                    />
                    <Legend />
                    {providers.map((p) => (
                      <Line
                        key={p}
                        type="monotone"
                        dataKey={p}
                        name={p}
                        stroke={PROVIDER_COLORS[p] ?? FALLBACK_COLOR}
                        strokeWidth={2}
                        dot={{ r: 3 }}
                        connectNulls
                      />
                    ))}
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function KpiTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-hairline bg-paper p-4">
      <div className="text-xs uppercase tracking-wide text-slate font-semibold">
        {label}
      </div>
      <div className="text-2xl font-display font-semibold text-charcoal mt-1">
        {value}
      </div>
    </div>
  );
}
