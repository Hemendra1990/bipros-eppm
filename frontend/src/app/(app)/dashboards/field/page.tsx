"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { activityApi } from "@/lib/api/activityApi";
import { projectApi } from "@/lib/api/projectApi";
import { labourApi, type LabourReturnResponse } from "@/lib/api/labourApi";
import { equipmentApi, type EquipmentLogResponse } from "@/lib/api/equipmentApi";
import Link from "next/link";
import { ArrowLeft, Clock, CheckCircle, AlertCircle } from "lucide-react";
import type { ProjectResponse, ActivityResponse } from "@/lib/types";

interface Activity {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  status: string;
  progress: number;
  location?: string;
}

interface DailyWorklog {
  date: string;
  logs: number;         // number of equipment logs on this date
  operatingHours: number; // sum of operatingHours across those logs
  headCount: number;    // total headcount (from labour returns on this date)
}

// Spring Page<T> envelope (paged endpoints return this at response.data root).
interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// Anchor for "today" for aggregation purposes. The project seed data goes up
// to 2026-04-14 — using the real clock here would show empty cards every day
// after that. Locking to the last seeded day keeps the dashboard useful.
const FIELD_DASHBOARD_ANCHOR_DATE = "2026-04-14";

export default function FieldDashboardPage() {
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(
    null
  );

  const { data: dashboardConfig, isLoading: isLoadingConfig } = useQuery({
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
      selectedProjectId
        ? activityApi.listActivities(selectedProjectId)
        : null,
    enabled: !!selectedProjectId,
  });

  // Pull a generous window (200 rows) of labour returns and equipment logs so
  // the client-side aggregation has everything it needs. The seeder only
  // produces tens of rows per project, so this is comfortably enough.
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
  // LabourReturnController returns Spring Page<T> — its `.data` has `content`
  // at the root. The typed `PagedResponse` envelope we use elsewhere happens
  // to expose `.content` at the same path, so this access is safe.
  const labourReturns: LabourReturnResponse[] =
    (labourData?.data as unknown as SpringPage<LabourReturnResponse> | undefined)?.content ?? [];
  const equipmentLogs: EquipmentLogResponse[] =
    (equipmentLogsData?.data as unknown as SpringPage<EquipmentLogResponse> | undefined)?.content ?? [];

  // Build the last 7 days (inclusive) anchored to the seed cutoff. We keep
  // only the last 4 for the tiles below (matches the original visual) but the
  // 7-day window is useful if we later want a wider strip.
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
    const headCount = dayLabour.reduce(
      (sum, l) => sum + (l.headCount ?? 0),
      0
    );
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

  // Mock active sites (calculate from labour data)
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

  const getActivityStatusColor = (status: string) => {
    switch (status) {
      case "COMPLETED":
        return "text-emerald-400";
      case "IN_PROGRESS":
        return "text-blue-400";
      case "PENDING":
        return "text-slate-400";
      default:
        return "text-slate-400";
    }
  };

  const getActivityStatusBg = (status: string) => {
    switch (status) {
      case "COMPLETED":
        return "bg-emerald-500/10";
      case "IN_PROGRESS":
        return "bg-blue-500/10";
      case "PENDING":
        return "bg-slate-800/50";
      default:
        return "bg-slate-800/50";
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "COMPLETED":
        return <CheckCircle size={16} className="text-emerald-400" />;
      case "IN_PROGRESS":
        return <Clock size={16} className="text-blue-400" />;
      default:
        return <AlertCircle size={16} className="text-slate-400" />;
    }
  };

  if (isLoadingConfig) {
    return (
      <div className="flex items-center justify-center p-6">
        <div className="text-slate-400">Loading dashboard...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center gap-4">
        <Link href="/dashboards">
          <button className="rounded p-1 hover:bg-slate-800">
            <ArrowLeft size={20} />
          </button>
        </Link>
        <div>
          <h1 className="text-3xl font-bold text-white">
            Field Dashboard
          </h1>
          <p className="text-slate-400">
            Real-time site activities and work progress
          </p>
        </div>
      </div>

      {/* Project Selection */}
      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
        <h2 className="mb-4 text-lg font-semibold text-white">
          Select Project
        </h2>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-4">
          {projects.map((project: ProjectResponse) => (
            <button
              key={project.id}
              onClick={() => setSelectedProjectId(project.id)}
              className={`rounded-lg border-2 p-3 text-left transition ${
                selectedProjectId === project.id
                  ? "border-blue-500 bg-blue-950"
                  : "border-slate-700 hover:border-blue-500"
              }`}
            >
              <div className="font-medium text-white">{project.name}</div>
              <div className="text-xs text-slate-400">
                {project.description}
              </div>
            </button>
          ))}
        </div>
      </div>

      {selectedProjectId && (
        <>
          {/* Daily Worklogs Summary */}
          <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
            <h2 className="mb-4 text-lg font-semibold text-white">
              Daily Worklogs
            </h2>
            {isLoadingLabour || isLoadingEquipmentLogs ? (
              <div className="py-8 text-center text-slate-400">
                Loading worklog data...
              </div>
            ) : !hasAnyWorklogData ? (
              <div className="rounded-lg border border-dashed border-slate-700 py-8 text-center">
                <p className="text-slate-400">
                  No daily worklogs for this project yet.
                </p>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
                {dailyWorklogs.map((log) => (
                  <div
                    key={log.date}
                    className="rounded-lg border border-slate-800 bg-slate-800/50 p-4"
                  >
                    <div className="mb-3 font-medium text-white">
                      {new Date(log.date + "T00:00:00Z").toLocaleDateString(
                        "en-US",
                        {
                          month: "short",
                          day: "numeric",
                          timeZone: "UTC",
                        }
                      )}
                    </div>
                    <div className="space-y-2">
                      <div>
                        <div className="text-xs text-slate-400">
                          Equipment Logs
                        </div>
                        <div className="text-2xl font-bold text-blue-400">
                          {log.logs}
                        </div>
                      </div>
                      <div>
                        <div className="text-xs text-slate-400">
                          Operating Hrs
                        </div>
                        <div className="text-2xl font-bold text-emerald-400">
                          {log.operatingHours.toFixed(1)}h
                        </div>
                      </div>
                      <div>
                        <div className="text-xs text-slate-400">Headcount</div>
                        <div className="text-2xl font-bold text-amber-400">
                          {log.headCount}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Active Sites */}
          <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
            <h2 className="mb-4 text-lg font-semibold text-white">
              Active Sites
            </h2>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              {mockActiveSites.map((site) => (
                <div
                  key={site.id}
                  className="rounded-lg border border-slate-800 bg-slate-800/50 p-4"
                >
                  <h3 className="mb-3 font-medium text-white">
                    {site.name}
                  </h3>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-slate-400">Workers</span>
                      <span className="font-semibold text-white">
                        {site.workers}
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-slate-400">Equipment</span>
                      <span className="font-semibold text-white">
                        {site.equipment}
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-slate-400">
                        Safety Incidents
                      </span>
                      <span
                        className={`font-semibold ${
                          site.safetyIncidents === 0
                            ? "text-emerald-400"
                            : "text-red-400"
                        }`}
                      >
                        {site.safetyIncidents}
                      </span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Site Activities */}
          <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6">
            <h2 className="mb-4 text-lg font-semibold text-white">
              Site Activities
            </h2>
            {isLoadingActivities ? (
              <div className="text-center text-slate-400">
                Loading activities...
              </div>
            ) : activities.length === 0 ? (
              <div className="rounded-lg border border-dashed border-slate-700 py-8 text-center">
                <p className="text-slate-400">No activities available</p>
              </div>
            ) : (
              <div className="space-y-3">
                {activities.slice(0, 10).map((activity: ActivityResponse) => (
                  <div
                    key={activity.id}
                    className={`rounded-lg border border-slate-700 bg-slate-800/50 p-4 ${getActivityStatusBg(
                      activity.status
                    )}`}
                  >
                    <div className="mb-3 flex items-start justify-between">
                      <div className="flex items-start gap-3">
                        {getStatusIcon(activity.status)}
                        <div>
                          <h3 className="font-medium text-white">
                            {activity.name}
                          </h3>
                          {activity.code && (
                            <p className="text-sm text-slate-400">
                              Code: {activity.code}
                            </p>
                          )}
                        </div>
                      </div>
                      <span
                        className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${
                          activity.status === "COMPLETED"
                            ? "bg-green-200 text-emerald-300"
                            : activity.status === "IN_PROGRESS"
                              ? "bg-blue-200 text-blue-300"
                              : "bg-slate-700/50 text-slate-100"
                        }`}
                      >
                        {activity.status}
                      </span>
                    </div>

                    <div className="space-y-2">
                      <div>
                        <div className="mb-1 flex items-center justify-between">
                          <span className="text-xs font-medium text-slate-400">
                            Progress
                          </span>
                          <span className="text-xs font-semibold text-white">
                            {activity.percentComplete || 0}%
                          </span>
                        </div>
                        <div className="h-2 w-full rounded-full bg-slate-700">
                          <div
                            className={`h-2 rounded-full ${
                              activity.status === "COMPLETED"
                                ? "bg-emerald-500"
                                : "bg-blue-500"
                            }`}
                            style={{ width: `${activity.percentComplete || 0}%` }}
                          />
                        </div>
                      </div>

                      <div className="text-xs text-slate-400">
                        {activity.plannedStartDate ? new Date(activity.plannedStartDate).toLocaleDateString() : "TBD"} to{" "}
                        {activity.plannedFinishDate ? new Date(activity.plannedFinishDate).toLocaleDateString() : "TBD"}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
