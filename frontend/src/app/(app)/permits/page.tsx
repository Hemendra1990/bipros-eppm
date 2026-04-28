"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import {
  AlertTriangle,
  CheckCircle2,
  ClipboardList,
  Clock,
  Plus,
} from "lucide-react";
import { permitApi } from "@/lib/api/permitApi";
import { PermitStatusBadge, PermitTypeBadge, RiskBadge } from "@/components/permits";

export default function PermitsDashboardPage() {
  const search = useSearchParams();
  const projectId = search.get("projectId") || undefined;

  const [todayLabel, setTodayLabel] = useState("");
  useEffect(() => {
    setTodayLabel(new Date().toLocaleDateString(undefined, { dateStyle: "full" }));
  }, []);

  const { data, isLoading, error } = useQuery({
    queryKey: ["permits-dashboard", projectId],
    queryFn: () => permitApi.dashboardSummary(projectId),
    staleTime: 60_000,
  });

  return (
    <div className="space-y-6 p-6">
      <header className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs uppercase tracking-widest text-slate">HSE Operations Center</p>
          <h1 className="mt-1 text-2xl font-bold text-charcoal">Workers Permit Dashboard</h1>
          <p className="text-sm text-slate" suppressHydrationWarning>
            {todayLabel || " "}
          </p>
        </div>
        <Link
          href="/permits/new"
          className="inline-flex items-center gap-1.5 rounded-md bg-gold px-4 py-2 text-sm font-semibold text-charcoal shadow-sm transition hover:bg-gold-deep"
        >
          <Plus size={16} /> New Permit
        </Link>
      </header>

      {isLoading && <p className="text-sm text-slate">Loading…</p>}
      {error && <p className="text-sm text-burgundy">Failed to load dashboard.</p>}

      {data && (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <KpiCard
              icon={<ClipboardList size={18} />}
              label="Active Permits"
              value={data.activePermits}
              tone="accent"
              hint="Today"
            />
            <KpiCard
              icon={<Clock size={18} />}
              label="Pending Review"
              value={data.pendingReview}
              tone="info"
              hint="Awaiting action"
            />
            <KpiCard
              icon={<AlertTriangle size={18} />}
              label="Expiring Today"
              value={data.expiringToday}
              tone="danger"
              hint="Renewal required"
            />
            <KpiCard
              icon={<CheckCircle2 size={18} />}
              label="Closed This Month"
              value={data.closedThisMonth}
              tone="success"
            />
          </div>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <section className="rounded-xl border border-hairline bg-paper p-5 shadow-sm lg:col-span-2">
              <h2 className="text-sm font-semibold text-charcoal">Active Permits by Type</h2>
              <div className="mt-3 space-y-2">
                {data.activeByType.length === 0 && (
                  <p className="text-sm text-slate">No active permits.</p>
                )}
                {data.activeByType.map((row) => {
                  const max = Math.max(...data.activeByType.map((r) => r.count), 1);
                  const pct = (row.count / max) * 100;
                  return (
                    <div key={row.code} className="space-y-1">
                      <div className="flex items-center justify-between text-xs">
                        <PermitTypeBadge code={row.code} name={row.name} colorHex={row.colorHex} />
                        <span className="font-semibold text-charcoal">{row.count}</span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-divider/50">
                        <div
                          className="h-2 rounded-full"
                          style={{
                            width: `${pct}%`,
                            backgroundColor: row.colorHex || "#D4AF37",
                          }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>

            <section className="rounded-xl border border-hairline bg-paper p-5 shadow-sm">
              <h2 className="text-sm font-semibold text-charcoal">Status Breakdown</h2>
              <ul className="mt-3 space-y-2">
                {Object.entries(data.statusBreakdown).map(([status, count]) => (
                  <li key={status} className="flex items-center justify-between text-sm">
                    <PermitStatusBadge status={status as keyof typeof data.statusBreakdown} />
                    <span className="font-semibold text-charcoal">{count}</span>
                  </li>
                ))}
              </ul>
            </section>
          </div>

          <section className="rounded-xl border border-hairline bg-paper p-5 shadow-sm">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-charcoal">Recent Permit Activity</h2>
              <Link href="/permits/register" className="text-xs font-semibold text-gold-deep hover:underline">
                View All →
              </Link>
            </div>
            <ul className="mt-3 divide-y divide-hairline">
              {data.recentActivity.map((p) => (
                <li key={p.id} className="flex items-center gap-3 py-2">
                  <Link
                    href={`/permits/${p.id}`}
                    className="font-mono text-xs font-semibold text-gold-deep hover:underline"
                  >
                    {p.permitCode}
                  </Link>
                  <span className="flex-1 truncate text-sm text-charcoal">{p.workDescription}</span>
                  <span className="text-xs text-slate">{p.principalWorkerName}</span>
                  <RiskBadge level={p.riskLevel} />
                  <PermitStatusBadge status={p.status} />
                </li>
              ))}
              {data.recentActivity.length === 0 && (
                <li className="py-3 text-sm text-slate">No recent activity.</li>
              )}
            </ul>
          </section>
        </>
      )}
    </div>
  );
}

function KpiCard({
  icon,
  label,
  value,
  hint,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  hint?: string;
  tone: "accent" | "info" | "danger" | "success";
}) {
  const tones = {
    accent: "border-gold/40 text-gold-deep",
    info: "border-steel/40 text-steel",
    danger: "border-burgundy/40 text-burgundy",
    success: "border-emerald/40 text-emerald",
  };
  return (
    <div className={`rounded-xl border bg-paper p-5 shadow-sm ${tones[tone]}`}>
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider opacity-80">
        {icon}
        {label}
      </div>
      <div className="mt-2 text-3xl font-bold text-charcoal">{value}</div>
      {hint && <div className="mt-1 text-[11px] text-slate">{hint}</div>}
    </div>
  );
}
