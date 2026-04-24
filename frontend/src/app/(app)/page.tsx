"use client";

import {
  BarChart3,
  Calendar,
  FolderTree,
  Plus,
  Users,
} from "lucide-react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { apiClient } from "@/lib/api/client";
import { TabTip } from "@/components/common/TabTip";
import { Badge } from "@/components/ui/badge";

interface ProjectData {
  id: string;
  name: string;
  status: string;
  createdAt: string;
}
interface ActivityData {
  totalActivities: number;
  criticalActivities: number;
  overdueCount: number;
}
interface MetricsData {
  plannedCount: number;
  activeCount: number;
  completedCount: number;
  resourceCount: number;
  recentProjects: ProjectData[];
  activities: ActivityData;
}

async function fetchMetrics(): Promise<MetricsData> {
  try {
    const projectsResponse = await apiClient.get("/v1/projects?page=0&size=100");
    const projects: ProjectData[] = projectsResponse.data.data?.content || [];
    const plannedCount = projects.filter((p) => p.status === "PLANNED").length;
    const activeCount = projects.filter((p) => p.status === "ACTIVE").length;
    const completedCount = projects.filter((p) => p.status === "COMPLETED").length;
    const recentProjects = [...projects]
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 5);

    const resourcesResponse = await apiClient.get("/v1/resources");
    const resourcesList = resourcesResponse.data.data || [];
    const resourceCount = Array.isArray(resourcesList) ? resourcesList.length : 0;

    let totalActivities = 0;
    let criticalActivities = 0;
    for (const proj of projects.slice(0, 10)) {
      try {
        const actResp = await apiClient.get(`/v1/projects/${proj.id}/activities?page=0&size=500`);
        const actData = actResp.data.data;
        totalActivities += actData?.pagination?.totalElements || 0;
        const list: Array<{ isCritical?: boolean; totalFloat?: number }> = actData?.content || [];
        criticalActivities += list.filter((a) => a.isCritical === true || a.totalFloat === 0).length;
      } catch { /* skip */ }
    }

    return {
      plannedCount, activeCount, completedCount, resourceCount, recentProjects,
      activities: { totalActivities, criticalActivities, overdueCount: 0 },
    };
  } catch (error) {
    console.error("Failed to fetch metrics:", error);
    return {
      plannedCount: 0, activeCount: 0, completedCount: 0, resourceCount: 0,
      recentProjects: [], activities: { totalActivities: 0, criticalActivities: 0, overdueCount: 0 },
    };
  }
}

function Kpi({
  label, value, tone = "default", delta,
}: {
  label: string;
  value: number | string;
  tone?: "default" | "warning" | "critical";
  delta?: string;
}) {
  const rail =
    tone === "critical"
      ? "border-l-[3px] border-l-burgundy"
      : tone === "warning"
        ? "border-l-[3px] border-l-bronze-warn"
        : "";
  const kickerColor =
    tone === "critical" ? "text-burgundy" : tone === "warning" ? "text-bronze-warn" : "text-gold-deep";

  return (
    <div
      className={`rounded-xl border border-hairline bg-paper p-5 transition-all duration-200 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] hover:-translate-y-0.5 ${rail}`}
    >
      <div className={`text-[10px] font-semibold uppercase tracking-[0.14em] ${kickerColor} mb-2`}>
        {label}
      </div>
      <div className="font-display text-[38px] font-semibold leading-none tracking-tight text-charcoal"
           style={{ fontVariationSettings: "'opsz' 144" }}>
        {value}
      </div>
      {delta && <div className="mt-2 text-xs text-slate">{delta}</div>}
    </div>
  );
}

function statusVariant(status: string) {
  switch (status) {
    case "ACTIVE": return "success" as const;
    case "PLANNED": return "gold" as const;
    case "COMPLETED": return "info" as const;
    case "INACTIVE": return "warning" as const;
    default: return "neutral" as const;
  }
}

const quickActions = [
  { title: "Projects", href: "/projects", icon: FolderTree, blurb: "Portfolio, baselines, programme hierarchy." },
  { title: "Resources", href: "/resources", icon: Users, blurb: "People, equipment, rate cards, allocations." },
  { title: "Calendars", href: "/admin/calendars", icon: Calendar, blurb: "Working days, holidays, shift patterns." },
  { title: "Reports", href: "/reports", icon: BarChart3, blurb: "Earned-value, variance, executive summaries." },
];

export default function DashboardPage() {
  const [isClient, setIsClient] = useState(false);
  const { data: metrics, isLoading } = useQuery<MetricsData>({
    queryKey: ["dashboard-metrics"],
    queryFn: fetchMetrics,
    staleTime: 60_000,
  });

  useEffect(() => setIsClient(true), []);
  if (!isClient) return null;

  const today = new Date().toLocaleDateString("en-US", {
    year: "numeric", month: "long", day: "numeric",
  });

  return (
    <div>
      {/* Page head */}
      <div className="mb-8 flex items-start justify-between gap-6">
        <div>
          <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-1.5">
            Today · {today}
          </div>
          <h1 className="font-display text-[38px] font-semibold leading-[1.08] tracking-tight text-charcoal"
              style={{ fontVariationSettings: "'opsz' 144" }}>
            Portfolio dashboard
          </h1>
          <p className="mt-2 max-w-[600px] text-sm text-slate leading-relaxed">
            Programme health across your active portfolio.
          </p>
        </div>
        <Link
          href="/projects/new"
          className="inline-flex h-10 items-center gap-1.5 rounded-[10px] bg-gold px-4 text-sm font-semibold text-paper transition-all duration-200 hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.3)] hover:-translate-y-px"
        >
          <Plus size={14} strokeWidth={2.5} />
          New project
        </Link>
      </div>

      <TabTip
        title="Dashboard"
        description="Your command centre. See project counts by status, total activities, resource utilisation, and recent projects at a glance. Click any card to drill in."
      />

      {/* Status KPIs */}
      {isLoading ? (
        <div className="mb-7 grid grid-cols-1 gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-28 animate-pulse rounded-xl bg-parchment" />
          ))}
        </div>
      ) : (
        <div className="mb-7 grid grid-cols-1 gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
          <Kpi label="Planned" value={metrics?.plannedCount ?? 0} />
          <Kpi label="Active" value={metrics?.activeCount ?? 0} />
          <Kpi label="Completed" value={metrics?.completedCount ?? 0} />
          <Kpi label="Resources" value={metrics?.resourceCount ?? 0} />
        </div>
      )}

      {/* Activity KPIs */}
      {!isLoading && (
        <div className="mb-8 grid grid-cols-1 gap-3.5 sm:grid-cols-3">
          <Kpi label="Total activities" value={metrics?.activities.totalActivities ?? 0} />
          <Kpi label="Critical path" value={metrics?.activities.criticalActivities ?? 0} tone="warning" />
          <Kpi label="Overdue" value={metrics?.activities.overdueCount ?? 0} tone="critical" />
        </div>
      )}

      {/* Recent projects table */}
      <div className="mb-8">
        <div className="mb-3.5 flex items-baseline justify-between">
          <h2 className="font-display text-xl font-semibold tracking-tight text-charcoal">
            Recent projects
          </h2>
          <Link href="/projects" className="text-xs font-semibold text-gold-deep hover:text-gold-ink">
            View all projects →
          </Link>
        </div>
        <div className="overflow-hidden rounded-xl border border-hairline bg-paper">
          <table className="w-full border-collapse text-sm">
            <thead className="border-b border-hairline bg-ivory">
              <tr>
                <th className="px-4 py-3 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Project</th>
                <th className="px-4 py-3 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Status</th>
                <th className="px-4 py-3 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">Created</th>
              </tr>
            </thead>
            <tbody>
              {metrics?.recentProjects && metrics.recentProjects.length > 0 ? (
                metrics.recentProjects.map((p) => (
                  <tr key={p.id} className="border-b border-hairline transition-colors last:border-b-0 hover:bg-ivory">
                    <td className="px-4 py-3.5">
                      <Link href={`/projects/${p.id}`} className="font-semibold text-charcoal hover:text-gold-deep">
                        {p.name}
                      </Link>
                    </td>
                    <td className="px-4 py-3.5">
                      <Badge variant={statusVariant(p.status)} withDot>{p.status}</Badge>
                    </td>
                    <td className="px-4 py-3.5 text-slate">
                      {new Date(p.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={3} className="px-4 py-6 text-center text-sm text-slate">
                    No recent projects
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Quick actions */}
      <div>
        <h2 className="mb-3.5 font-display text-xl font-semibold tracking-tight text-charcoal">
          Jump back in
        </h2>
        <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
          {quickActions.map((card) => (
            <Link
              key={card.title}
              href={card.href}
              className="group relative rounded-xl border border-hairline bg-paper p-5 transition-all duration-200 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] hover:-translate-y-0.5 hover:border-gold/40"
            >
              <div className="mb-3.5 flex h-9 w-9 items-center justify-center rounded-[10px] bg-gold-tint text-gold-deep">
                <card.icon size={18} strokeWidth={1.5} />
              </div>
              <div className="font-display text-lg font-semibold tracking-tight text-charcoal">
                {card.title}
              </div>
              <p className="mt-1 text-xs leading-relaxed text-slate">{card.blurb}</p>
              <span
                aria-hidden
                className="absolute right-5 top-5 text-sm text-gold opacity-40 transition-all duration-200 group-hover:opacity-100 group-hover:translate-x-0.5"
              >
                →
              </span>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
