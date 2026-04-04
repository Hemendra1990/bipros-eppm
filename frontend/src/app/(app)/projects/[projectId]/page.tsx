"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, useSearchParams } from "next/navigation";
import { projectApi } from "@/lib/api/projectApi";
import { activityApi } from "@/lib/api/activityApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { StatusBadge } from "@/components/common/StatusBadge";
import { EmptyState } from "@/components/common/EmptyState";
import { GanttChart } from "@/components/schedule/GanttChart";
import { NetworkDiagram } from "@/components/schedule/NetworkDiagram";
import { ListTodo, Plus, Play, Trash2 } from "lucide-react";
import type { ProjectResponse, ActivityResponse, WbsNodeResponse } from "@/lib/types";

export default function ProjectDetailPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const projectId = params.projectId as string;
  const tab = searchParams.get("tab") || "overview";
  const [scheduleError, setScheduleError] = useState("");

  const { data: projectData, isLoading: isLoadingProject } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
  });

  const { data: activitiesData, isLoading: isLoadingActivities, refetch: refetchActivities } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 100),
    enabled: ["activities", "gantt", "network"].includes(tab),
  });

  const { data: wbsData, isLoading: isLoadingWbs } = useQuery({
    queryKey: ["wbs", projectId],
    queryFn: () => projectApi.getWbsTree(projectId),
    enabled: tab === "wbs",
  });

  const { data: criticalPathData } = useQuery({
    queryKey: ["critical-path", projectId],
    queryFn: () => activityApi.getCriticalPath(projectId),
    enabled: tab === "activities",
  });

  const { data: relationshipsData, isLoading: isLoadingRelationships } = useQuery({
    queryKey: ["relationships", projectId],
    queryFn: () => activityApi.getRelationships(projectId),
    enabled: tab === "network",
  });

  const scheduleMutation = useMutation({
    mutationFn: () => activityApi.triggerSchedule(projectId, "RETAINED_LOGIC"),
    onSuccess: () => {
      refetchActivities();
      setScheduleError("");
    },
    onError: (err) => {
      setScheduleError(err instanceof Error ? err.message : "Failed to trigger schedule");
    },
  });

  const deleteActivityMutation = useMutation({
    mutationFn: (activityId: string) => activityApi.deleteActivity(projectId, activityId),
    onSuccess: () => {
      refetchActivities();
    },
  });

  const project = projectData?.data;
  const activities = activitiesData?.data?.content ?? [];
  const wbsTree = wbsData?.data ?? [];
  const criticalPathIds = new Set((criticalPathData?.data ?? []).map((a: any) => a.id));

  const activityColumns: ColumnDef<ActivityResponse>[] = [
    { key: "code", label: "Code", sortable: true },
    { key: "name", label: "Name", sortable: true },
    { key: "duration", label: "Duration (days)", sortable: true },
    {
      key: "percentComplete",
      label: "% Complete",
      sortable: true,
      render: (value) => `${String(value)}%`,
    },
    {
      key: "status",
      label: "Status",
      render: (value) => <StatusBadge status={String(value)} />,
    },
    { key: "totalFloat", label: "Float (days)", sortable: true },
    { key: "plannedStartDate", label: "Planned Start", sortable: true },
    { key: "plannedFinishDate", label: "Planned Finish", sortable: true },
    {
      key: "id",
      label: "Actions",
      render: (value, row: any) => (
        <div className="flex gap-2">
          <Link
            href={`/projects/${projectId}/activities/${value}`}
            className="text-blue-600 hover:text-blue-700 text-sm font-medium"
          >
            Edit
          </Link>
          <button
            onClick={() => {
              if (window.confirm("Are you sure you want to delete this activity?")) {
                deleteActivityMutation.mutate(String(value));
              }
            }}
            className="text-red-600 hover:text-red-700 text-sm font-medium"
          >
            Delete
          </button>
        </div>
      ),
    },
  ];

  if (isLoadingProject) {
    return <div className="text-center text-gray-500">Loading...</div>;
  }

  if (!project) {
    return <div className="text-center text-red-500">Project not found</div>;
  }

  return (
    <div>
      {tab === "overview" && <OverviewTab project={project} />}
      {tab === "activities" && (
        <ActivitiesTab
          projectId={projectId}
          activities={activities}
          isLoading={isLoadingActivities}
          columns={activityColumns}
          criticalPathIds={criticalPathIds}
          onRunSchedule={() => scheduleMutation.mutate()}
          isScheduling={scheduleMutation.isPending}
          scheduleError={scheduleError}
        />
      )}
      {tab === "wbs" && (
        <WbsTab wbsTree={wbsTree} isLoading={isLoadingWbs} />
      )}
      {tab === "gantt" && (
        <GanttTab activities={activities} isLoading={isLoadingActivities} />
      )}
      {tab === "network" && (
        <NetworkTab
          activities={activities}
          relationships={relationshipsData?.data ?? []}
          isLoading={isLoadingActivities || isLoadingRelationships}
        />
      )}
      {["resources", "costs", "evm"].includes(tab) && (
        <ComingSoonTab tabName={tab} />
      )}
    </div>
  );
}

function OverviewTab({ project }: { project: ProjectResponse }) {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Code</h3>
          <p className="mt-2 text-2xl font-bold text-gray-900">{project.code}</p>
        </div>
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Status</h3>
          <div className="mt-2">
            <StatusBadge status={project.status} />
          </div>
        </div>
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-6">
        <h3 className="text-sm font-medium text-gray-700">Description</h3>
        <p className="mt-2 text-gray-900">{project.description || "No description"}</p>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Planned Start Date</h3>
          <p className="mt-2 text-lg text-gray-900">{project.plannedStartDate}</p>
        </div>
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Planned Finish Date</h3>
          <p className="mt-2 text-lg text-gray-900">{project.plannedFinishDate}</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Priority</h3>
          <p className="mt-2 text-lg text-gray-900">{project.priority}</p>
        </div>
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="text-sm font-medium text-gray-700">Data Date</h3>
          <p className="mt-2 text-lg text-gray-900">{project.dataDate || "Not set"}</p>
        </div>
      </div>
    </div>
  );
}

function ActivitiesTab({
  projectId,
  activities,
  isLoading,
  columns,
  criticalPathIds,
  onRunSchedule,
  isScheduling,
  scheduleError,
}: {
  projectId: string;
  activities: ActivityResponse[];
  isLoading: boolean;
  columns: ColumnDef<ActivityResponse>[];
  criticalPathIds: Set<string>;
  onRunSchedule: () => void;
  isScheduling: boolean;
  scheduleError: string;
}) {
  const highlightedActivities = activities.map((a) => ({
    ...a,
    isCritical: criticalPathIds.has(a.id),
  }));

  const enrichedColumns: ColumnDef<ActivityResponse>[] = columns.map((col) => {
    if (col.key === "code") {
      return {
        ...col,
        render: (value, row: any) => (
          <span className={row.isCritical ? "font-bold text-red-600" : ""}>
            {String(value)}
          </span>
        ),
      };
    }
    if (col.key === "name") {
      return {
        ...col,
        render: (value, row: any) => (
          <span className={row.isCritical ? "font-bold text-red-600" : ""}>
            {String(value)}
          </span>
        ),
      };
    }
    return col;
  });

  if (isLoading) {
    return <div className="text-center text-gray-500">Loading activities...</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex gap-3">
        <Link
          href={`/projects/${projectId}/activities/new`}
          className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          <Plus size={16} />
          New Activity
        </Link>
        <button
          onClick={onRunSchedule}
          disabled={isScheduling}
          className="inline-flex items-center gap-2 rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:bg-gray-400"
        >
          <Play size={16} />
          {isScheduling ? "Running..." : "Run Schedule"}
        </button>
      </div>

      {scheduleError && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">{scheduleError}</div>
      )}

      {criticalPathIds.size > 0 && (
        <div className="rounded-md bg-yellow-50 p-4 text-sm text-yellow-800">
          <strong>Critical activities (shown in red):</strong> {criticalPathIds.size} activities
          on critical path
        </div>
      )}

      {activities.length === 0 ? (
        <EmptyState
          icon={ListTodo}
          title="No activities"
          description="This project has no activities yet. Create activities to start planning."
        />
      ) : (
        <DataTable columns={enrichedColumns} data={highlightedActivities} rowKey="id" />
      )}
    </div>
  );
}

function GanttTab({
  activities,
  isLoading,
}: {
  activities: ActivityResponse[];
  isLoading: boolean;
}) {
  if (isLoading) {
    return <div className="text-center text-gray-500">Loading activities...</div>;
  }

  if (activities.length === 0) {
    return (
      <EmptyState
        icon={ListTodo}
        title="No activities"
        description="This project has no activities yet. Create activities to display the Gantt chart."
      />
    );
  }

  return <GanttChart activities={activities} />;
}

function WbsTab({ wbsTree, isLoading }: { wbsTree: WbsNodeResponse[]; isLoading: boolean }) {
  if (isLoading) {
    return <div className="text-center text-gray-500">Loading WBS...</div>;
  }

  if (wbsTree.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
        <h3 className="text-lg font-medium text-gray-900">No WBS Structure</h3>
        <p className="mt-2 text-gray-500">The WBS tree is being loaded...</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 className="mb-4 text-lg font-semibold text-gray-900">Work Breakdown Structure</h2>
      <WbsTree nodes={wbsTree} />
    </div>
  );
}

function WbsTree({ nodes, level = 0 }: { nodes: WbsNodeResponse[]; level?: number }) {
  return (
    <ul className="space-y-2">
      {nodes.map((node) => (
        <li key={node.id}>
          <div style={{ marginLeft: `${level * 24}px` }} className="flex items-center gap-2">
            <span className="text-sm font-medium text-gray-700">{node.code}</span>
            <span className="text-sm text-gray-600">{node.name}</span>
            {node.summaryDuration && (
              <span className="ml-2 text-xs text-gray-500">({node.summaryDuration} days)</span>
            )}
          </div>
          {node.children && node.children.length > 0 && (
            <WbsTree nodes={node.children} level={level + 1} />
          )}
        </li>
      ))}
    </ul>
  );
}

function NetworkTab({
  activities,
  relationships,
  isLoading,
}: {
  activities: ActivityResponse[];
  relationships: Array<{
    predecessorActivityId: string;
    successorActivityId: string;
    relationshipType: string;
  }>;
  isLoading: boolean;
}) {
  if (isLoading) {
    return <div className="text-center text-gray-500">Loading network diagram...</div>;
  }

  if (activities.length === 0) {
    return (
      <EmptyState
        icon={ListTodo}
        title="No activities"
        description="This project has no activities yet. Create activities to display the network diagram."
      />
    );
  }

  return <NetworkDiagram activities={activities} relationships={relationships} />;
}

function ComingSoonTab({ tabName }: { tabName: string }) {
  return (
    <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
      <h3 className="text-lg font-medium text-gray-900 capitalize">{tabName}</h3>
      <p className="mt-2 text-gray-500">This feature is coming soon.</p>
    </div>
  );
}
