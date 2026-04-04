"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, useSearchParams } from "next/navigation";
import { projectApi } from "@/lib/api/projectApi";
import { activityApi } from "@/lib/api/activityApi";
import { baselineApi } from "@/lib/api/baselineApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { StatusBadge } from "@/components/common/StatusBadge";
import { EmptyState } from "@/components/common/EmptyState";
import { GanttChart } from "@/components/schedule/GanttChart";
import { ResourcesTab } from "@/components/resource/ResourcesTab";
import { CostsTab } from "@/components/cost/CostsTab";
import { EvmTab } from "@/components/evm/EvmTab";
import { NetworkDiagram } from "@/components/schedule/NetworkDiagram";
import { ListTodo, Plus, Play, Trash2, Eye } from "lucide-react";
import type { ProjectResponse, ActivityResponse, WbsNodeResponse, BaselineResponse, BaselineVarianceRow } from "@/lib/types";

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
    enabled: ["network", "gantt"].includes(tab),
  });

  const { data: baselinesData, isLoading: isLoadingBaselines, refetch: refetchBaselines } = useQuery({
    queryKey: ["baselines", projectId],
    queryFn: () => baselineApi.listBaselines(projectId),
    enabled: ["baselines", "gantt"].includes(tab),
  });

  const primaryBaseline = baselinesData?.data?.find((b) => b.baselineType === "PRIMARY");

  const { data: baselineActivitiesData, isLoading: isLoadingBaselineActivities } = useQuery({
    queryKey: ["baseline-activities", projectId, primaryBaseline?.id],
    queryFn: () =>
      primaryBaseline
        ? baselineApi.getBaselineActivities(projectId, primaryBaseline.id)
        : Promise.resolve({ data: [], success: true, meta: {} } as any),
    enabled: tab === "gantt" && !!primaryBaseline,
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

  const createBaselineMutation = useMutation({
    mutationFn: (data: { name: string; baselineType: string }) =>
      baselineApi.createBaseline(projectId, data as any),
    onSuccess: () => {
      refetchBaselines();
    },
  });

  const deleteBaselineMutation = useMutation({
    mutationFn: (baselineId: string) => baselineApi.deleteBaseline(projectId, baselineId),
    onSuccess: () => {
      refetchBaselines();
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
        <GanttTab
          activities={activities}
          isLoading={isLoadingActivities || isLoadingRelationships || isLoadingBaselineActivities}
          relationships={relationshipsData?.data ?? []}
          baselineActivities={baselineActivitiesData?.data ?? []}
        />
      )}
      {tab === "network" && (
        <NetworkTab
          activities={activities}
          relationships={relationshipsData?.data ?? []}
          isLoading={isLoadingActivities || isLoadingRelationships}
        />
      )}
      {tab === "baselines" && (
        <BaselinesTab
          projectId={projectId}
          baselines={baselinesData?.data ?? []}
          isLoading={isLoadingBaselines}
          onCreateBaseline={(data) => createBaselineMutation.mutate(data)}
          isCreating={createBaselineMutation.isPending}
          onDeleteBaseline={(baselineId) => deleteBaselineMutation.mutate(baselineId)}
          isDeleting={deleteBaselineMutation.isPending}
        />
      )}
      {tab === "resources" && <ResourcesTab projectId={projectId} />}
      {tab === "costs" && <CostsTab projectId={projectId} />}
      {tab === "evm" && <EvmTab projectId={projectId} />}
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
  relationships = [],
  baselineActivities = [],
}: {
  activities: ActivityResponse[];
  isLoading: boolean;
  relationships?: Array<{ predecessorActivityId: string; successorActivityId: string; relationshipType: string }>;
  baselineActivities?: Array<{ activityId: string; baselineStartDate: string | null; baselineFinishDate: string | null }>;
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

  return (
    <GanttChart
      activities={activities}
      relationships={relationships}
      baselineActivities={baselineActivities}
    />
  );
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

function BaselinesTab({
  projectId,
  baselines,
  isLoading,
  onCreateBaseline,
  isCreating,
  onDeleteBaseline,
  isDeleting,
}: {
  projectId: string;
  baselines: BaselineResponse[];
  isLoading: boolean;
  onCreateBaseline: (data: { name: string; baselineType: string }) => void;
  isCreating: boolean;
  onDeleteBaseline: (baselineId: string) => void;
  isDeleting: boolean;
}) {
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({ name: "", baselineType: "PROJECT" });
  const [expandedBaselineId, setExpandedBaselineId] = useState<string | null>(null);
  const [varianceData, setVarianceData] = useState<Record<string, BaselineVarianceRow[]>>({});
  const [loadingVarianceId, setLoadingVarianceId] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (formData.name.trim()) {
      onCreateBaseline(formData);
      setFormData({ name: "", baselineType: "PROJECT" });
      setShowForm(false);
    }
  };

  const handleViewVariance = async (baselineId: string) => {
    if (expandedBaselineId === baselineId) {
      setExpandedBaselineId(null);
      return;
    }

    if (varianceData[baselineId]) {
      setExpandedBaselineId(baselineId);
      return;
    }

    setLoadingVarianceId(baselineId);
    try {
      const response = await baselineApi.getVariance(projectId, baselineId);
      if (response?.data) {
        setVarianceData((prev) => ({
          ...prev,
          [baselineId]: response.data!.variance,
        }));
        setExpandedBaselineId(baselineId);
      }
    } catch (error) {
      console.error("Failed to load variance data:", error);
    } finally {
      setLoadingVarianceId(null);
    }
  };

  if (isLoading) {
    return <div className="text-center text-gray-500">Loading baselines...</div>;
  }

  return (
    <div className="space-y-6">
      {!showForm && (
        <button
          onClick={() => setShowForm(true)}
          className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          <Plus size={16} />
          Create Baseline
        </button>
      )}

      {showForm && (
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="mb-4 text-lg font-semibold text-gray-900">Create New Baseline</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700">Name</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 placeholder-gray-400 focus:border-blue-500 focus:outline-none"
                placeholder="Baseline name"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">Type</label>
              <select
                value={formData.baselineType}
                onChange={(e) => setFormData({ ...formData, baselineType: e.target.value })}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:outline-none"
              >
                <option value="PROJECT">PROJECT</option>
                <option value="PRIMARY">PRIMARY</option>
                <option value="SECONDARY">SECONDARY</option>
                <option value="TERTIARY">TERTIARY</option>
              </select>
            </div>
            <div className="flex gap-3">
              <button
                type="submit"
                disabled={isCreating}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400"
              >
                {isCreating ? "Creating..." : "Create"}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {baselines.length === 0 ? (
        <EmptyState
          icon={ListTodo}
          title="No baselines"
          description="This project has no baselines yet. Create a baseline to start tracking project variance."
        />
      ) : (
        <div className="space-y-4">
          {baselines.map((baseline) => (
            <div key={baseline.id} className="rounded-lg border border-gray-200 bg-white p-6">
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-gray-900">{baseline.name}</h3>
                  <div className="mt-2 space-y-1 text-sm text-gray-600">
                    <p>Type: {baseline.baselineType}</p>
                    <p>Date: {new Date(baseline.snapshotDate).toLocaleDateString()}</p>
                    <p>Activities: {baseline.activitiesCount}</p>
                  </div>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleViewVariance(baseline.id)}
                    disabled={loadingVarianceId === baseline.id}
                    className="inline-flex items-center gap-2 rounded-md bg-gray-100 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200 disabled:bg-gray-200"
                  >
                    <Eye size={16} />
                    {expandedBaselineId === baseline.id ? "Hide Variance" : "View Variance"}
                  </button>
                  <button
                    onClick={() => {
                      if (window.confirm("Are you sure you want to delete this baseline?")) {
                        onDeleteBaseline(baseline.id);
                      }
                    }}
                    disabled={isDeleting}
                    className="inline-flex items-center gap-2 rounded-md bg-red-50 px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-100 disabled:bg-gray-200"
                  >
                    <Trash2 size={16} />
                    Delete
                  </button>
                </div>
              </div>

              {expandedBaselineId === baseline.id && varianceData[baseline.id] && (
                <div className="mt-6 border-t border-gray-200 pt-6">
                  <h4 className="mb-4 font-semibold text-gray-900">Variance Details</h4>
                  <div className="overflow-x-auto">
                    <table className="min-w-full border-collapse text-sm">
                      <thead>
                        <tr className="border-b border-gray-200 bg-gray-50">
                          <th className="px-4 py-3 text-left font-medium text-gray-700">Activity Code</th>
                          <th className="px-4 py-3 text-left font-medium text-gray-700">Activity Name</th>
                          <th className="px-4 py-3 text-right font-medium text-gray-700">Start Var (days)</th>
                          <th className="px-4 py-3 text-right font-medium text-gray-700">Finish Var (days)</th>
                          <th className="px-4 py-3 text-right font-medium text-gray-700">Duration Var</th>
                          <th className="px-4 py-3 text-right font-medium text-gray-700">Cost Var</th>
                        </tr>
                      </thead>
                      <tbody>
                        {varianceData[baseline.id].map((row, idx) => (
                          <tr key={idx} className="border-b border-gray-200 hover:bg-gray-50">
                            <td className="px-4 py-3 text-gray-900">{row.activityCode}</td>
                            <td className="px-4 py-3 text-gray-900">{row.activityName}</td>
                            <td className="px-4 py-3 text-right text-gray-900">
                              <span className={row.startVariance !== 0 ? "font-semibold text-red-600" : ""}>
                                {row.startVariance}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-right text-gray-900">
                              <span className={row.finishVariance !== 0 ? "font-semibold text-red-600" : ""}>
                                {row.finishVariance}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-right text-gray-900">
                              <span className={row.durationVariance !== 0 ? "font-semibold text-red-600" : ""}>
                                {row.durationVariance}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-right text-gray-900">
                              <span className={row.costVariance !== 0 ? "font-semibold text-red-600" : ""}>
                                {row.costVariance}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function ComingSoonTab({ tabName }: { tabName: string }) {
  return (
    <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
      <h3 className="text-lg font-medium text-gray-900 capitalize">{tabName}</h3>
      <p className="mt-2 text-gray-500">This feature is coming soon.</p>
    </div>
  );
}
