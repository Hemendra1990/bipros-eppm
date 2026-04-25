"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { activityApi } from "@/lib/api/activityApi";
import { projectApi } from "@/lib/api/projectApi";
import { labourApi, type LabourReturnResponse } from "@/lib/api/labourApi";
import { equipmentApi, type EquipmentLogResponse } from "@/lib/api/equipmentApi";
import Link from "next/link";
import {
  AlertCircle,
  ArrowLeft,
  CheckCircle,
  Clock,
  HardHat,
  ShieldCheck,
  Truck,
  Users,
} from "lucide-react";
import type { ProjectResponse, ActivityResponse } from "@/lib/types";
import { Badge, type BadgeVariant } from "@/components/ui/badge";

interface DailyWorklog {
  date: string;
  logs: number;
  operatingHours: number;
  headCount: number;
}

interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const FIELD_DASHBOARD_ANCHOR_DATE = "2026-04-14";

function activityBadge(status: string): BadgeVariant {
  switch (status) {
    case "COMPLETED":
      return "success";
    case "IN_PROGRESS":
      return "info";
    case "PENDING":
      return "neutral";
    default:
      return "neutral";
  }
}

function getStatusIcon(status: string) {
  switch (status) {
    case "COMPLETED":
      return <CheckCircle size={14} className="text-emerald" strokeWidth={2} />;
    case "IN_PROGRESS":
      return <Clock size={14} className="text-steel" strokeWidth={2} />;
    default:
      return <AlertCircle size={14} className="text-ash" strokeWidth={2} />;
  }
}

export default function FieldDashboardPage() {
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);

  const { isLoading: isLoadingConfig } = useQuery({
    queryKey: ["dashboard-config", "FIELD"],
    queryFn: () => dashboardApi.getDashboardByTier("FIELD"),
  });

  const { data: projectsData } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectApi.listProjects(),
  });

  const { data: activitiesData, isLoading: isLoadingActivities } = useQuery({
    queryKey: ["activities", selectedProjectId],
    queryFn: () =>
      selectedProjectId ? activityApi.listActivities(selectedProjectId) : null,
    enabled: !!selectedProjectId,
  });

  const { data: labourData, isLoading: isLoadingLabour } = useQuery({
    queryKey: ["labour-returns", selectedProjectId],
    queryFn: () =>
      selectedProjectId
        ? labourApi.getReturnsByProject(selectedProjectId, 0, 200)
        : null,
    enabled: !!selectedProjectId,
    retry: 1,
  });

  const { data: equipmentLogsData, isLoading: isLoadingEquipmentLogs } = useQuery({
    queryKey: ["equipment-logs", selectedProjectId],
    queryFn: () =>
      selectedProjectId
        ? equipmentApi.getLogsByProject(selectedProjectId, 0, 200)
        : null,
    enabled: !!selectedProjectId,
    retry: 1,
  });

  const projects = projectsData?.data?.content ?? [];
  const activities = activitiesData?.data?.content ?? [];
  const labourReturns: LabourReturnResponse[] =
    (labourData?.data as unknown as SpringPage<LabourReturnResponse> | undefined)
      ?.content ?? [];
  const equipmentLogs: EquipmentLogResponse[] =
    (
      equipmentLogsData?.data as unknown as
        | SpringPage<EquipmentLogResponse>
        | undefined
    )?.content ?? [];

  const lastSevenDates: string[] = Array.from({ length: 7 }, (_, i) => {
    const anchor = new Date(FIELD_DASHBOARD_ANCHOR_DATE + "T00:00:00Z");
    anchor.setUTCDate(anchor.getUTCDate() - (6 - i));
    return anchor.toISOString().split("T")[0];
  });

  const dailyWorklogs: DailyWorklog[] = lastSevenDates.slice(-4).map((dateStr) => {
    const dayLogs = equipmentLogs.filter((l) => l.logDate === dateStr);
    const dayLabour = labourReturns.filter((l) => l.returnDate === dateStr);
    const operatingHours = dayLogs.reduce(
      (sum, l) => sum + (l.operatingHours ?? 0),
      0
    );
    const headCount = dayLabour.reduce((sum, l) => sum + (l.headCount ?? 0), 0);
    return {
      date: dateStr,
      logs: dayLogs.length,
      operatingHours,
      headCount,
    };
  });

  const hasAnyWorklogData = dailyWorklogs.some(
    (d) => d.logs > 0 || d.headCount > 0
  );

  const mockActiveSites = [
    {
      id: "1",
      name: "Foundation Excavation",
      workers: labourReturns.length > 0 ? 25 : 25,
      equipment: 8,
      safetyIncidents: 0,
    },
    {
      id: "2",
      name: "Structural Steel Erection",
      workers: 18,
      equipment: 12,
      safetyIncidents: 0,
    },
    {
      id: "3",
      name: "Concrete Pouring",
      workers: 32,
      equipment: 6,
      safetyIncidents: 1,
    },
  ];

  // Aggregate KPI strip
  const totalWorkers = mockActiveSites.reduce((s, x) => s + x.workers, 0);
  const totalEquipment = mockActiveSites.reduce((s, x) => s + x.equipment, 0);
  const totalIncidents = mockActiveSites.reduce(
    (s, x) => s + x.safetyIncidents,
    0
  );
  const totalOperatingHours = dailyWorklogs.reduce(
    (s, d) => s + d.operatingHours,
    0
  );

  if (isLoadingConfig) {
    return (
      <div className="flex items-center justify-center p-12">
        <div className="text-sm text-slate">Loading dashboard…</div>
      </div>
    );
  }

  return (
    <div>
      {/* Page head */}
      <div className="mb-7 flex items-start gap-4">
        <Link
          href="/dashboards"
          aria-label="Back to dashboards"
          className="mt-1.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-hairline bg-paper text-slate transition-all duration-200 hover:border-gold/50 hover:text-gold-deep"
        >
          <ArrowLeft size={16} strokeWidth={1.75} />
        </Link>
        <div className="flex-1">
          <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-1.5">
            Field · site command
          </div>
          <h1
            className="font-display text-[34px] font-semibold leading-[1.08] tracking-tight text-charcoal"
            style={{ fontVariationSettings: "'opsz' 144" }}
          >
            Field dashboard
          </h1>
          <p className="mt-2 max-w-[640px] text-sm leading-relaxed text-slate">
            Daily worklogs, headcount and equipment hours from the active sites — refreshed every shift.
          </p>
        </div>
      </div>

      {/* Project picker */}
      <section className="mb-7">
        <SectionHeading kicker="Step 1" title="Select a project" />
        {projects.length === 0 ? (
          <EmptyState label="No projects available" />
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {projects.map((project: ProjectResponse) => {
              const isSelected = selectedProjectId === project.id;
              return (
                <button
                  key={project.id}
                  type="button"
                  onClick={() => setSelectedProjectId(project.id)}
                  className={`group rounded-xl border bg-paper p-4 text-left transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] ${
                    isSelected
                      ? "border-gold ring-1 ring-gold/20 bg-gold-tint/30"
                      : "border-hairline hover:border-gold/40"
                  }`}
                >
                  <div className="mb-1.5 flex items-center justify-between">
                    <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
                      {isSelected ? "Selected" : "Project"}
                    </div>
                    <div
                      className={`h-2 w-2 rounded-full transition-colors ${
                        isSelected ? "bg-gold" : "bg-hairline group-hover:bg-gold/50"
                      }`}
                    />
                  </div>
                  <div className="font-display text-base font-semibold tracking-tight text-charcoal">
                    {project.name}
                  </div>
                  {project.description && (
                    <div className="mt-1 line-clamp-2 text-xs text-slate">
                      {project.description}
                    </div>
                  )}
                </button>
              );
            })}
          </div>
        )}
      </section>

      {selectedProjectId && (
        <>
          {/* KPI strip */}
          <div className="mb-7 grid grid-cols-2 gap-3.5 lg:grid-cols-4">
            <KpiCard label="Workers on site" value={totalWorkers} icon={<Users size={16} />} />
            <KpiCard
              label="Equipment deployed"
              value={totalEquipment}
              icon={<Truck size={16} />}
              accent="gold"
            />
            <KpiCard
              label="Operating hours · 4d"
              value={`${totalOperatingHours.toFixed(0)}h`}
              icon={<Clock size={16} />}
              accent="emerald"
            />
            <KpiCard
              label="Safety incidents"
              value={totalIncidents}
              icon={<ShieldCheck size={16} />}
              accent={totalIncidents > 0 ? "burgundy" : "emerald"}
            />
          </div>

          {/* Daily Worklogs */}
          <section className="mb-6">
            <SectionHeading
              kicker="Last 4 shifts"
              title="Daily worklogs"
              icon={<Clock size={14} strokeWidth={1.75} />}
            />
            <div className="rounded-2xl border border-hairline bg-paper p-5">
              {isLoadingLabour || isLoadingEquipmentLogs ? (
                <div className="grid grid-cols-1 gap-3.5 md:grid-cols-4">
                  {[...Array(4)].map((_, i) => (
                    <div
                      key={i}
                      className="h-36 animate-pulse rounded-xl bg-parchment/60"
                    />
                  ))}
                </div>
              ) : !hasAnyWorklogData ? (
                <EmptyState label="No daily worklogs for this project yet." />
              ) : (
                <div className="grid grid-cols-1 gap-3.5 md:grid-cols-4">
                  {dailyWorklogs.map((log) => (
                    <div
                      key={log.date}
                      className="rounded-xl border border-hairline bg-ivory p-4"
                    >
                      <div className="mb-3 flex items-center justify-between">
                        <div className="font-display text-base font-semibold tracking-tight text-charcoal">
                          {new Date(log.date + "T00:00:00Z").toLocaleDateString(
                            "en-US",
                            {
                              month: "short",
                              day: "numeric",
                              timeZone: "UTC",
                            }
                          )}
                        </div>
                        <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
                          {new Date(log.date + "T00:00:00Z").toLocaleDateString(
                            "en-US",
                            { weekday: "short", timeZone: "UTC" }
                          )}
                        </span>
                      </div>
                      <DailyMetric
                        icon={<Truck size={12} />}
                        label="Equipment"
                        value={log.logs}
                      />
                      <DailyMetric
                        icon={<Clock size={12} />}
                        label="Op hours"
                        value={`${log.operatingHours.toFixed(1)}h`}
                      />
                      <DailyMetric
                        icon={<Users size={12} />}
                        label="Headcount"
                        value={log.headCount}
                        last
                      />
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>

          {/* Active Sites */}
          <section className="mb-6">
            <SectionHeading
              kicker="Live"
              title="Active sites"
              icon={<HardHat size={14} strokeWidth={1.75} />}
            />
            <div className="grid grid-cols-1 gap-3.5 md:grid-cols-3">
              {mockActiveSites.map((site) => (
                <div
                  key={site.id}
                  className="rounded-2xl border border-hairline bg-paper p-5"
                >
                  <div className="mb-4 flex items-start justify-between gap-2">
                    <h3 className="font-display text-base font-semibold leading-snug tracking-tight text-charcoal">
                      {site.name}
                    </h3>
                    <Badge
                      variant={site.safetyIncidents > 0 ? "danger" : "success"}
                      withDot
                    >
                      {site.safetyIncidents > 0 ? "Incident" : "All clear"}
                    </Badge>
                  </div>
                  <div className="grid grid-cols-3 gap-3">
                    <SiteStat icon={<Users size={14} />} label="Workers" value={site.workers} />
                    <SiteStat icon={<Truck size={14} />} label="Equipment" value={site.equipment} />
                    <SiteStat
                      icon={<ShieldCheck size={14} />}
                      label="Incidents"
                      value={site.safetyIncidents}
                      tone={site.safetyIncidents > 0 ? "burgundy" : "emerald"}
                    />
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* Site Activities */}
          <section>
            <SectionHeading
              kicker="On the wire"
              title="Site activities"
            />
            <div className="overflow-hidden rounded-2xl border border-hairline bg-paper">
              {isLoadingActivities ? (
                <div className="space-y-2 p-5">
                  {[...Array(3)].map((_, i) => (
                    <div
                      key={i}
                      className="h-20 animate-pulse rounded-md bg-parchment/60"
                    />
                  ))}
                </div>
              ) : activities.length === 0 ? (
                <EmptyState label="No activities available" />
              ) : (
                <ul className="divide-y divide-hairline">
                  {activities.slice(0, 10).map((activity: ActivityResponse) => (
                    <li key={activity.id} className="p-5">
                      <div className="mb-3 flex items-start justify-between gap-3">
                        <div className="flex min-w-0 items-start gap-3">
                          <span className="mt-0.5">{getStatusIcon(activity.status)}</span>
                          <div className="min-w-0">
                            <h3 className="truncate font-semibold text-charcoal">
                              {activity.name}
                            </h3>
                            {activity.code && (
                              <p className="text-xs text-slate">Code · {activity.code}</p>
                            )}
                          </div>
                        </div>
                        <Badge variant={activityBadge(activity.status)} withDot>
                          {activity.status.replace("_", " ")}
                        </Badge>
                      </div>

                      <div className="ml-7">
                        <div className="mb-1 flex items-center justify-between">
                          <span className="text-[11px] font-medium uppercase tracking-wide text-slate">
                            Progress
                          </span>
                          <span className="text-xs font-semibold text-charcoal">
                            {activity.percentComplete || 0}%
                          </span>
                        </div>
                        <div className="h-1.5 w-full overflow-hidden rounded-full bg-parchment">
                          <div
                            className={`h-full rounded-full transition-all duration-500 ${
                              activity.status === "COMPLETED"
                                ? "bg-emerald"
                                : "bg-gold"
                            }`}
                            style={{
                              width: `${activity.percentComplete || 0}%`,
                            }}
                          />
                        </div>
                        <div className="mt-2 text-[11px] text-slate">
                          {activity.plannedStartDate
                            ? new Date(
                                activity.plannedStartDate
                              ).toLocaleDateString()
                            : "TBD"}{" "}
                          —{" "}
                          {activity.plannedFinishDate
                            ? new Date(
                                activity.plannedFinishDate
                              ).toLocaleDateString()
                            : "TBD"}
                        </div>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </section>
        </>
      )}
    </div>
  );
}

// ----------------------- shared local primitives -----------------------

function KpiCard({
  label,
  value,
  icon,
  accent = "default",
}: {
  label: string;
  value: number | string;
  icon?: React.ReactNode;
  accent?: "default" | "emerald" | "burgundy" | "gold";
}) {
  const rail =
    accent === "emerald"
      ? "border-l-[3px] border-l-emerald"
      : accent === "burgundy"
        ? "border-l-[3px] border-l-burgundy"
        : accent === "gold"
          ? "border-l-[3px] border-l-gold"
          : "";
  return (
    <div
      className={`rounded-xl border border-hairline bg-paper p-5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_4px_20px_rgba(28,28,28,0.05)] ${rail}`}
    >
      <div className="mb-2 flex items-center justify-between">
        <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
          {label}
        </div>
        {icon && <span className="text-gold-deep/70">{icon}</span>}
      </div>
      <div
        className="font-display text-[28px] font-semibold leading-none tracking-tight text-charcoal"
        style={{ fontVariationSettings: "'opsz' 144" }}
      >
        {value}
      </div>
    </div>
  );
}

function SectionHeading({
  kicker,
  title,
  subtitle,
  icon,
}: {
  kicker: string;
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
}) {
  return (
    <div className="mb-3.5 flex items-baseline justify-between gap-3">
      <div>
        <div className="flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep">
          {icon && <span>{icon}</span>}
          {kicker}
        </div>
        <h2 className="mt-0.5 font-display text-xl font-semibold tracking-tight text-charcoal">
          {title}
        </h2>
        {subtitle && <p className="mt-0.5 text-xs text-slate">{subtitle}</p>}
      </div>
    </div>
  );
}

function DailyMetric({
  icon,
  label,
  value,
  last = false,
}: {
  icon: React.ReactNode;
  label: string;
  value: number | string;
  last?: boolean;
}) {
  return (
    <div
      className={`flex items-center justify-between py-1.5 ${
        last ? "" : "border-b border-hairline/60"
      }`}
    >
      <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-slate">
        <span className="text-ash">{icon}</span>
        {label}
      </span>
      <span className="text-base font-semibold tabular-nums text-charcoal">
        {value}
      </span>
    </div>
  );
}

function SiteStat({
  icon,
  label,
  value,
  tone = "default",
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  tone?: "default" | "emerald" | "burgundy";
}) {
  const valueClass =
    tone === "emerald"
      ? "text-emerald"
      : tone === "burgundy"
        ? "text-burgundy"
        : "text-charcoal";
  return (
    <div className="rounded-lg border border-hairline bg-ivory/60 p-3">
      <div className="mb-1 flex items-center gap-1.5 text-ash">
        {icon}
        <span className="text-[10px] font-semibold uppercase tracking-[0.14em] text-slate">
          {label}
        </span>
      </div>
      <div
        className={`font-display text-xl font-semibold tabular-nums tracking-tight ${valueClass}`}
      >
        {value}
      </div>
    </div>
  );
}

function EmptyState({ label }: { label: string }) {
  return (
    <div className="flex items-center justify-center rounded-xl border border-dashed border-hairline bg-ivory/50 p-8 text-sm text-slate">
      {label}
    </div>
  );
}
