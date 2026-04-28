"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Activity, AlertTriangle, CheckCircle2 } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { analyticsHealthApi } from "@/lib/api/analyticsHealthApi";
import type {
  AnalyticsHealthResponse,
  ErrorBucket,
  HourlyMetric,
  TopUserRow,
  WatermarkAge,
} from "@/lib/types";
import { useAuth } from "@/lib/auth/useAuth";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

const WINDOW_OPTIONS = [
  { label: "Last 24h", value: 24 },
  { label: "Last 72h", value: 72 },
  { label: "Last 7d", value: 168 },
] as const;

function formatInt(n: number | null | undefined): string {
  if (n == null) return "—";
  return n.toLocaleString();
}

function formatMs(n: number | null | undefined): string {
  if (n == null) return "—";
  return `${Math.round(n)} ms`;
}

function formatAge(seconds: number): string {
  if (!Number.isFinite(seconds)) return "never";
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h`;
  return `${Math.floor(seconds / 86400)}d`;
}

function bucketLabel(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getMonth() + 1).padStart(2, "0")}-${String(
    d.getDate(),
  ).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:00`;
}

export default function AnalyticsHealthPage() {
  const { isAdmin } = useAuth();
  const [windowHours, setWindowHours] = useState<number>(24);

  const { data, isLoading, isError, error } = useQuery<AnalyticsHealthResponse | null>({
    queryKey: ["analytics-health", windowHours],
    queryFn: async () =>
      (await analyticsHealthApi.fetchHealth(windowHours)).data ?? null,
    enabled: isAdmin,
  });

  if (!isAdmin) {
    return (
      <div className="p-6">
        <Card>
          <CardContent>
            <div className="py-12 text-center text-sm text-burgundy">
              You need ROLE_ADMIN to view this page.
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-end justify-between flex-wrap gap-4">
        <div>
          <h1 className="font-display text-2xl font-semibold text-charcoal flex items-center gap-2">
            <Activity size={20} /> Analytics Health
          </h1>
          <p className="text-sm text-slate mt-1 max-w-2xl">
            Live observability for the analytics assistant: ETL freshness, query
            volume, error rate, latency percentiles, top users and top errors.
          </p>
        </div>
        <div className="flex items-center gap-2">
          {WINDOW_OPTIONS.map((o) => (
            <button
              key={o.value}
              onClick={() => setWindowHours(o.value)}
              className={
                windowHours === o.value
                  ? "px-3 py-1.5 rounded-md bg-charcoal text-paper text-sm font-medium"
                  : "px-3 py-1.5 rounded-md border border-divider text-sm text-slate hover:text-charcoal"
              }
            >
              {o.label}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <Card>
          <CardContent>
            <div className="py-12 text-center text-sm text-slate">Loading…</div>
          </CardContent>
        </Card>
      ) : isError ? (
        <Card>
          <CardContent>
            <div className="py-12 text-center text-sm text-burgundy">
              Couldn&rsquo;t load health data:{" "}
              {(error as { message?: string })?.message ?? "unknown error"}
            </div>
          </CardContent>
        </Card>
      ) : !data ? null : (
        <>
          <KpiStrip hourly={data.hourly} />
          <WatermarksCard rows={data.watermarks} />
          <VolumeAndLatencyCards hourly={data.hourly} />
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <TopUsersCard rows={data.topUsers} />
            <ErrorsCard rows={data.errors} />
          </div>
        </>
      )}
    </div>
  );
}

function KpiStrip({ hourly }: { hourly: HourlyMetric[] }) {
  const totals = useMemo(() => {
    let total = 0;
    let errors = 0;
    let latencyP95Max = 0;
    for (const h of hourly) {
      total += h.total ?? 0;
      errors += h.errors ?? 0;
      if (h.latencyP95Ms != null && h.latencyP95Ms > latencyP95Max) {
        latencyP95Max = h.latencyP95Ms;
      }
    }
    const errorRate = total === 0 ? 0 : (errors / total) * 100;
    return { total, errors, errorRate, latencyP95Max };
  }, [hourly]);

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
      <KpiTile label="Queries (window)" value={formatInt(totals.total)} />
      <KpiTile label="Errors" value={formatInt(totals.errors)} />
      <KpiTile
        label="Error rate"
        value={`${totals.errorRate.toFixed(1)}%`}
        warn={totals.errorRate >= 5}
      />
      <KpiTile label="Worst hourly p95" value={formatMs(totals.latencyP95Max)} />
    </div>
  );
}

function WatermarksCard({ rows }: { rows: WatermarkAge[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>ETL freshness</CardTitle>
        <CardDescription>
          Per-source watermark age. Stale = no advance in 30+ minutes.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {rows.length === 0 ? (
          <div className="text-sm text-slate py-6 text-center">
            No ETL watermarks recorded.
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Table</TableHead>
                <TableHead>Last synced</TableHead>
                <TableHead>Age</TableHead>
                <TableHead>Last run status</TableHead>
                <TableHead className="text-right">Health</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((w) => (
                <TableRow key={w.tableName}>
                  <TableCell className="font-medium">{w.tableName}</TableCell>
                  <TableCell className="text-slate">
                    {w.lastSyncedAt
                      ? new Date(w.lastSyncedAt).toLocaleString()
                      : "never"}
                  </TableCell>
                  <TableCell>{formatAge(w.ageSeconds)}</TableCell>
                  <TableCell>{w.lastRunStatus ?? "—"}</TableCell>
                  <TableCell className="text-right">
                    {w.stale ? (
                      <span className="inline-flex items-center gap-1 text-burgundy text-xs font-semibold">
                        <AlertTriangle size={14} /> Stale
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 text-emerald-700 text-xs font-semibold">
                        <CheckCircle2 size={14} /> Fresh
                      </span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function VolumeAndLatencyCards({ hourly }: { hourly: HourlyMetric[] }) {
  const data = useMemo(
    () =>
      hourly.map((h) => ({
        bucket: h.bucket,
        label: bucketLabel(h.bucket),
        total: h.total ?? 0,
        errors: h.errors ?? 0,
        ok: Math.max(0, (h.total ?? 0) - (h.errors ?? 0)),
        p50: h.latencyP50Ms ?? null,
        p95: h.latencyP95Ms ?? null,
      })),
    [hourly],
  );

  const empty = data.length === 0;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Query volume</CardTitle>
          <CardDescription>Per-hour stack: successful vs error.</CardDescription>
        </CardHeader>
        <CardContent>
          {empty ? (
            <div className="text-sm text-slate py-12 text-center">No traffic.</div>
          ) : (
            <div className="h-[260px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data} margin={{ top: 8, right: 16, left: 4, bottom: 4 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#E6E1D6" />
                  <XAxis dataKey="label" stroke="#828282" fontSize={11} />
                  <YAxis stroke="#828282" fontSize={11} tickFormatter={formatInt} />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="ok" name="OK" stackId="a" fill="#4A8A4A" />
                  <Bar dataKey="errors" name="Errors" stackId="a" fill="#A04A4A" />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Latency p50 / p95</CardTitle>
          <CardDescription>Per-hour Postgres percentiles in ms.</CardDescription>
        </CardHeader>
        <CardContent>
          {empty ? (
            <div className="text-sm text-slate py-12 text-center">No traffic.</div>
          ) : (
            <div className="h-[260px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={data} margin={{ top: 8, right: 16, left: 4, bottom: 4 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#E6E1D6" />
                  <XAxis dataKey="label" stroke="#828282" fontSize={11} />
                  <YAxis stroke="#828282" fontSize={11} tickFormatter={formatMs} />
                  <Tooltip formatter={(v) => formatMs(Number(v ?? 0))} />
                  <Legend />
                  <Line
                    type="monotone"
                    dataKey="p50"
                    name="p50"
                    stroke="#3A6FA0"
                    strokeWidth={2}
                    dot={{ r: 2 }}
                    connectNulls
                  />
                  <Line
                    type="monotone"
                    dataKey="p95"
                    name="p95"
                    stroke="#C9803A"
                    strokeWidth={2}
                    dot={{ r: 2 }}
                    connectNulls
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function TopUsersCard({ rows }: { rows: TopUserRow[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Top users</CardTitle>
        <CardDescription>By query count over the window.</CardDescription>
      </CardHeader>
      <CardContent>
        {rows.length === 0 ? (
          <div className="text-sm text-slate py-6 text-center">No queries yet.</div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>User ID</TableHead>
                <TableHead className="text-right">Queries</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((u) => (
                <TableRow key={u.userId}>
                  <TableCell className="font-mono text-xs">{u.userId}</TableCell>
                  <TableCell className="text-right">
                    {formatInt(u.queryCount)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function ErrorsCard({ rows }: { rows: ErrorBucket[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Top errors</CardTitle>
        <CardDescription>Status × error_kind histogram.</CardDescription>
      </CardHeader>
      <CardContent>
        {rows.length === 0 ? (
          <div className="text-sm text-slate py-6 text-center">
            No errors in the window.
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Status</TableHead>
                <TableHead>Error kind</TableHead>
                <TableHead className="text-right">Count</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((e, i) => (
                <TableRow key={`${e.status}-${e.errorKind}-${i}`}>
                  <TableCell className="font-medium">{e.status}</TableCell>
                  <TableCell className="text-slate">
                    {e.errorKind ?? "—"}
                  </TableCell>
                  <TableCell className="text-right">{formatInt(e.count)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function KpiTile({
  label,
  value,
  warn,
}: {
  label: string;
  value: string;
  warn?: boolean;
}) {
  return (
    <div className="rounded-lg border border-hairline bg-paper p-4">
      <div className="text-xs uppercase tracking-wide text-slate font-semibold">
        {label}
      </div>
      <div
        className={
          warn
            ? "text-2xl font-display font-semibold text-burgundy mt-1"
            : "text-2xl font-display font-semibold text-charcoal mt-1"
        }
      >
        {value}
      </div>
    </div>
  );
}
