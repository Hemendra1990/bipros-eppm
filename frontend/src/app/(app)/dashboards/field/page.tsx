"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { dashboardApi } from "@/lib/api/dashboardApi";
import { activityApi } from "@/lib/api/activityApi";
import { projectApi } from "@/lib/api/projectApi";
import Link from "next/link";
import { ArrowLeft, Clock, CheckCircle, AlertCircle } from "lucide-react";

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
  items: number;
  status: string;
  hoursWorked: number;
}

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

  const projects = projectsData?.data?.content ?? [];
  const activities = activitiesData?.data?.content ?? [];

  // Mock daily worklogs
  const mockDailyWorklogs: DailyWorklog[] = [
    { date: "2025-04-04", items: 12, status: "COMPLETED", hoursWorked: 8 },
    { date: "2025-04-03", items: 15, status: "COMPLETED", hoursWorked: 8 },
    { date: "2025-04-02", items: 10, status: "COMPLETED", hoursWorked: 8 },
    { date: "2025-04-01", items: 14, status: "COMPLETED", hoursWorked: 8 },
  ];

  // Mock active sites
  const mockActiveSites = [
    {
      id: "1",
      name: "Foundation Excavation",
      workers: 25,
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
        return "text-green-600";
      case "IN_PROGRESS":
        return "text-blue-600";
      case "PENDING":
        return "text-gray-600";
      default:
        return "text-gray-600";
    }
  };

  const getActivityStatusBg = (status: string) => {
    switch (status) {
      case "COMPLETED":
        return "bg-green-100";
      case "IN_PROGRESS":
        return "bg-blue-100";
      case "PENDING":
        return "bg-gray-100";
      default:
        return "bg-gray-100";
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case "COMPLETED":
        return <CheckCircle size={16} className="text-green-600" />;
      case "IN_PROGRESS":
        return <Clock size={16} className="text-blue-600" />;
      default:
        return <AlertCircle size={16} className="text-gray-600" />;
    }
  };

  if (isLoadingConfig) {
    return (
      <div className="flex items-center justify-center p-6">
        <div className="text-gray-500">Loading dashboard...</div>
      </div>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center gap-4">
        <Link href="/dashboards">
          <button className="rounded p-1 hover:bg-gray-100">
            <ArrowLeft size={20} />
          </button>
        </Link>
        <div>
          <h1 className="text-3xl font-bold text-gray-900">
            Field Dashboard
          </h1>
          <p className="text-gray-600">
            Real-time site activities and work progress
          </p>
        </div>
      </div>

      {/* Project Selection */}
      <div className="rounded-lg border border-gray-200 bg-white p-6">
        <h2 className="mb-4 text-lg font-semibold text-gray-900">
          Select Project
        </h2>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-4">
          {projects.map((project: any) => (
            <button
              key={project.id}
              onClick={() => setSelectedProjectId(project.id)}
              className={`rounded-lg border-2 p-3 text-left transition ${
                selectedProjectId === project.id
                  ? "border-blue-500 bg-blue-50"
                  : "border-gray-200 hover:border-blue-300"
              }`}
            >
              <div className="font-medium text-gray-900">{project.name}</div>
              <div className="text-xs text-gray-500">
                {project.description}
              </div>
            </button>
          ))}
        </div>
      </div>

      {selectedProjectId && (
        <>
          {/* Daily Worklogs Summary */}
          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <h2 className="mb-4 text-lg font-semibold text-gray-900">
              Daily Worklogs
            </h2>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
              {mockDailyWorklogs.map((log) => (
                <div
                  key={log.date}
                  className="rounded-lg border border-gray-200 p-4"
                >
                  <div className="mb-3 font-medium text-gray-900">
                    {new Date(log.date).toLocaleDateString("en-US", {
                      month: "short",
                      day: "numeric",
                    })}
                  </div>
                  <div className="space-y-2">
                    <div>
                      <div className="text-xs text-gray-600">Items Done</div>
                      <div className="text-2xl font-bold text-blue-600">
                        {log.items}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-gray-600">Hours</div>
                      <div className="text-2xl font-bold text-green-600">
                        {log.hoursWorked}h
                      </div>
                    </div>
                    <div>
                      <span className="inline-block rounded-full bg-green-100 px-2 py-1 text-xs font-semibold text-green-800">
                        {log.status}
                      </span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Active Sites */}
          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <h2 className="mb-4 text-lg font-semibold text-gray-900">
              Active Sites
            </h2>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              {mockActiveSites.map((site) => (
                <div
                  key={site.id}
                  className="rounded-lg border border-gray-200 p-4"
                >
                  <h3 className="mb-3 font-medium text-gray-900">
                    {site.name}
                  </h3>
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-gray-600">Workers</span>
                      <span className="font-semibold text-gray-900">
                        {site.workers}
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-gray-600">Equipment</span>
                      <span className="font-semibold text-gray-900">
                        {site.equipment}
                      </span>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-sm text-gray-600">
                        Safety Incidents
                      </span>
                      <span
                        className={`font-semibold ${
                          site.safetyIncidents === 0
                            ? "text-green-600"
                            : "text-red-600"
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
          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <h2 className="mb-4 text-lg font-semibold text-gray-900">
              Site Activities
            </h2>
            {isLoadingActivities ? (
              <div className="text-center text-gray-500">
                Loading activities...
              </div>
            ) : activities.length === 0 ? (
              <div className="rounded-lg border border-dashed border-gray-300 py-8 text-center">
                <p className="text-gray-500">No activities available</p>
              </div>
            ) : (
              <div className="space-y-3">
                {activities.slice(0, 10).map((activity: any) => (
                  <div
                    key={activity.id}
                    className={`rounded-lg border border-gray-200 p-4 ${getActivityStatusBg(
                      activity.status
                    )}`}
                  >
                    <div className="mb-3 flex items-start justify-between">
                      <div className="flex items-start gap-3">
                        {getStatusIcon(activity.status)}
                        <div>
                          <h3 className="font-medium text-gray-900">
                            {activity.name}
                          </h3>
                          {activity.location && (
                            <p className="text-sm text-gray-600">
                              Location: {activity.location}
                            </p>
                          )}
                        </div>
                      </div>
                      <span
                        className={`inline-block rounded-full px-3 py-1 text-xs font-semibold ${
                          activity.status === "COMPLETED"
                            ? "bg-green-200 text-green-800"
                            : activity.status === "IN_PROGRESS"
                              ? "bg-blue-200 text-blue-800"
                              : "bg-gray-200 text-gray-800"
                        }`}
                      >
                        {activity.status}
                      </span>
                    </div>

                    <div className="space-y-2">
                      <div>
                        <div className="mb-1 flex items-center justify-between">
                          <span className="text-xs font-medium text-gray-600">
                            Progress
                          </span>
                          <span className="text-xs font-semibold text-gray-900">
                            {activity.progress || 0}%
                          </span>
                        </div>
                        <div className="h-2 w-full rounded-full bg-gray-300">
                          <div
                            className={`h-2 rounded-full ${
                              activity.status === "COMPLETED"
                                ? "bg-green-500"
                                : "bg-blue-500"
                            }`}
                            style={{ width: `${activity.progress || 0}%` }}
                          />
                        </div>
                      </div>

                      <div className="text-xs text-gray-600">
                        {new Date(activity.startDate).toLocaleDateString()} to{" "}
                        {new Date(activity.endDate).toLocaleDateString()}
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
