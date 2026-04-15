"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, useSearchParams } from "next/navigation";
import { getErrorMessage } from "@/lib/utils/error";
import { formatDate, getPriorityInfo } from "@/lib/utils/format";
import { projectApi } from "@/lib/api/projectApi";
import { activityApi } from "@/lib/api/activityApi";
import { baselineApi, type BaselineActivityResponse, type BaselineDetailResponse } from "@/lib/api/baselineApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { StatusBadge } from "@/components/common/StatusBadge";
import { EmptyState } from "@/components/common/EmptyState";
import { GanttChart } from "@/components/schedule/GanttChart";
import { ResourcesTab } from "@/components/resource/ResourcesTab";
import { CostsTab } from "@/components/cost/CostsTab";
import { EvmTab } from "@/components/evm/EvmTab";
import { NetworkDiagram } from "@/components/schedule/NetworkDiagram";
import { ListTodo, Plus, Play, Trash2, Eye, FileText } from "lucide-react";
import toast from "react-hot-toast";
import { apiClient } from "@/lib/api/client";
import { wbsTemplateApi } from "@/lib/api/wbsTemplateApi";
import { TabTip } from "@/components/common/TabTip";
import { VarianceDashboard } from "@/components/baseline/VarianceDashboard";
import { ScheduleComparisonTable } from "@/components/baseline/ScheduleComparisonTable";
import type { ProjectResponse, ActivityResponse, WbsNodeResponse, BaselineResponse, BaselineVarianceRow, ApiResponse } from "@/lib/types";
import type { WbsTemplateResponse } from "@/lib/types";
import type { AxiosResponse } from "axios";

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

  const { data: baselineDetailData, isLoading: isLoadingBaselineActivities } = useQuery({
    queryKey: ["baseline-detail", projectId, primaryBaseline?.id],
    queryFn: () =>
      primaryBaseline
        ? baselineApi.getBaseline(projectId, primaryBaseline.id)
        : Promise.resolve({ data: null, error: null, meta: { timestamp: "", version: "" } } as unknown as ApiResponse<BaselineDetailResponse>),
    enabled: tab === "gantt" && !!primaryBaseline,
  });

  const scheduleMutation = useMutation({
    mutationFn: () => activityApi.triggerSchedule(projectId, "RETAINED_LOGIC"),
    onSuccess: () => {
      refetchActivities();
      setScheduleError("");
    },
    onError: (err: unknown) => {
      setScheduleError(getErrorMessage(err, "Failed to trigger schedule"));
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
      baselineApi.createBaseline(projectId, data as { name: string; baselineType: "PROJECT" | "PRIMARY" | "SECONDARY" | "TERTIARY"; description?: string }),
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
  const criticalPathIds = new Set((criticalPathData?.data ?? []).map((a: ActivityResponse) => a.id));

  const activityColumns: ColumnDef<ActivityResponse>[] = [
    { key: "code", label: "Code", sortable: true },
    { key: "name", label: "Name", sortable: true },
    { key: "originalDuration", label: "Duration (days)", sortable: true },
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
    {
      key: "plannedStartDate",
      label: "Planned Start",
      sortable: true,
      render: (value) => formatDate(value as string | null),
    },
    {
      key: "plannedFinishDate",
      label: "Planned Finish",
      sortable: true,
      render: (value) => formatDate(value as string | null),
    },
    {
      key: "id",
      label: "Actions",
      render: (value, row: ActivityResponse) => (
        <div className="flex gap-2">
          <Link
            href={`/projects/${projectId}/activities/${value}`}
            className="text-blue-400 hover:text-blue-300 text-sm font-medium"
          >
            Edit
          </Link>
          <button
            onClick={() => {
              if (window.confirm("Are you sure you want to delete this activity?")) {
                deleteActivityMutation.mutate(String(value));
              }
            }}
            className="text-red-400 hover:text-red-300 text-sm font-medium"
          >
            Delete
          </button>
        </div>
      ),
    },
  ];

  if (isLoadingProject) {
    return (
      <div className="space-y-4">
        <div className="h-8 w-48 animate-pulse rounded bg-slate-800/50" />
        <div className="grid grid-cols-2 gap-6">
          <div className="h-28 animate-pulse rounded-xl bg-slate-800/50" />
          <div className="h-28 animate-pulse rounded-xl bg-slate-800/50" />
        </div>
        <div className="h-32 animate-pulse rounded-xl bg-slate-800/50" />
      </div>
    );
  }

  if (!project) {
    return <div className="text-center text-red-400">Project not found</div>;
  }

  const tabTips: Record<string, { title: string; description: string; steps?: string[] }> = {
    overview: {
      title: "Project Overview",
      description: "Your project's summary card showing key details. This is where you see status, dates, and priority at a glance.",
    },
    wbs: {
      title: "Work Breakdown Structure (WBS)",
      description: "Break your project into a tree of manageable work packages. Every activity must belong to a WBS node. Think of it as organizing your project like folders in a file system.",
      steps: ["Create top-level phases (e.g., Design, Construction, Handover)", "Add sub-packages under each phase", "Assign activities to the appropriate WBS node"],
    },
    activities: {
      title: "Activities — Your Project's Tasks",
      description: "Activities are the actual work items your team performs. Each has a code, name, duration, and dates. After creating activities, click 'Run Schedule' to calculate the Critical Path (CPM).",
      steps: ["Click '+ New Activity' to create tasks", "Set duration, dates, and WBS node", "Click 'Run Schedule' to calculate early/late dates and float", "Activities with 0 float are on the Critical Path — any delay there delays the whole project"],
    },
    gantt: {
      title: "Gantt Chart — Visual Timeline",
      description: "See all activities as horizontal bars on a calendar. Arrows show dependencies (which task must finish before another starts). Red bars = Critical Path. Use the zoom slider to adjust the view.",
    },
    resources: {
      title: "Resource Management",
      description: "Assign people, equipment, and materials to activities. Track who is working on what and when. The histogram shows resource usage over time to spot over-allocation.",
      steps: ["Click 'Assign Resource' to link a resource to this project", "View the Resource Histogram to check for over-allocation", "Level resources if needed to balance workloads"],
    },
    costs: {
      title: "Cost Tracking",
      description: "Monitor your project budget. Budget = what you planned to spend. Actual = what you've spent so far. The S-Curve chart shows spending over time.",
      steps: ["Budget is set via cost accounts linked to WBS nodes", "Actual costs come from recorded expenses on activities", "Cash Flow S-Curve visualizes planned vs actual spending trends"],
    },
    evm: {
      title: "Earned Value Management (EVM)",
      description: "EVM answers: Are we on schedule? Are we on budget? Using 3 key values: PV (Planned Value = budgeted cost of scheduled work), EV (Earned Value = budgeted cost of completed work), AC (Actual Cost = what you actually spent).",
      steps: ["Click 'Calculate EVM' to compute metrics", "SPI > 1.0 = ahead of schedule, < 1.0 = behind", "CPI > 1.0 = under budget, < 1.0 = over budget", "EAC = Estimate At Completion (predicted final cost)"],
    },
  };

  const currentTip = tabTips[tab];

  return (
    <div>
      {currentTip && <TabTip title={currentTip.title} description={currentTip.description} steps={currentTip.steps} />}
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
        <WbsTab wbsTree={wbsTree} isLoading={isLoadingWbs} projectId={projectId} />
      )}
      {tab === "gantt" && (
        <GanttTab
          activities={activities}
          isLoading={isLoadingActivities || isLoadingRelationships || isLoadingBaselineActivities}
          relationships={relationshipsData?.data ?? []}
          baselineActivities={(baselineDetailData?.data?.activities ?? []).map((a: BaselineActivityResponse) => ({
            activityId: a.activityId,
            baselineStartDate: a.earlyStart,
            baselineFinishDate: a.earlyFinish,
          }))}
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
  const queryClient = useQueryClient();
  const [isTransitioning, setIsTransitioning] = useState(false);

  const statusTransitions: Record<string, { label: string; value: string }[]> = {
    PLANNED: [{ label: "Activate Project", value: "ACTIVE" }],
    ACTIVE: [
      { label: "Complete Project", value: "COMPLETED" },
      { label: "Deactivate Project", value: "INACTIVE" },
    ],
    INACTIVE: [{ label: "Reactivate Project", value: "ACTIVE" }],
    COMPLETED: [],
  };

  const availableTransitions = statusTransitions[project.status] ?? [];

  const handleStatusTransition = async (newStatus: string) => {
    setIsTransitioning(true);
    try {
      await projectApi.updateProject(project.id, { status: newStatus });
      queryClient.invalidateQueries({ queryKey: ["project", project.id] });
      toast.success(`Project status changed to ${newStatus}`);
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Failed to update project status"));
    } finally {
      setIsTransitioning(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <h3 className="text-sm font-medium text-slate-400">Code</h3>
          <p className="mt-2 text-2xl font-bold text-white">{project.code}</p>
        </div>
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <h3 className="text-sm font-medium text-slate-400">Status</h3>
          <div className="mt-2 flex items-center gap-3">
            <StatusBadge status={project.status} />
            {availableTransitions.length > 0 && (
              <div className="flex gap-2">
                {availableTransitions.map((t) => (
                  <button
                    key={t.value}
                    onClick={() => handleStatusTransition(t.value)}
                    disabled={isTransitioning}
                    className="rounded-md border border-slate-700 px-3 py-1 text-xs font-medium text-slate-300 hover:bg-slate-800/50 disabled:opacity-50"
                  >
                    {isTransitioning ? "..." : t.label}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
        <h3 className="text-sm font-medium text-slate-400">Description</h3>
        <p className="mt-2 text-white">{project.description || "No description"}</p>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <h3 className="text-sm font-medium text-slate-400">Planned Start Date</h3>
          <p className="mt-2 text-lg text-white">{formatDate(project.plannedStartDate)}</p>
        </div>
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <h3 className="text-sm font-medium text-slate-400">Planned Finish Date</h3>
          <p className="mt-2 text-lg text-white">{formatDate(project.plannedFinishDate)}</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <h3 className="text-sm font-medium text-slate-400">Priority</h3>
          <p className={`mt-2 text-lg font-medium ${getPriorityInfo(project.priority).color}`}>
            {getPriorityInfo(project.priority).label} ({project.priority})
          </p>
        </div>
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <h3 className="text-sm font-medium text-slate-400">Data Date</h3>
          <p className="mt-2 text-lg text-white">{project.dataDate ? formatDate(project.dataDate) : "Not set"}</p>
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

  const enrichedColumns: ColumnDef<ActivityResponse & { isCritical: boolean }>[] = columns.map((col) => {
    if (col.key === "code") {
      return {
        ...col,
        render: (value, row: ActivityResponse & { isCritical: boolean }) => (
          <span className={row.isCritical ? "font-bold text-red-400" : ""}>
            {String(value)}
          </span>
        ),
      };
    }
    if (col.key === "name") {
      return {
        ...col,
        render: (value, row: ActivityResponse & { isCritical: boolean }) => (
          <span className={row.isCritical ? "font-bold text-red-400" : ""}>
            {String(value)}
          </span>
        ),
      };
    }
    return col;
  });

  if (isLoading) {
    return <div className="text-center text-slate-500">Loading activities...</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex gap-3">
        <Link
          href={`/projects/${projectId}/activities/new`}
          className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
        >
          <Plus size={16} />
          New Activity
        </Link>
        <button
          onClick={onRunSchedule}
          disabled={isScheduling}
          className="inline-flex items-center gap-2 rounded-md bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500 disabled:bg-slate-500"
        >
          <Play size={16} />
          {isScheduling ? "Running..." : "Run Schedule"}
        </button>
      </div>

      {scheduleError && (
        <div className="rounded-md bg-red-500/10 p-4 text-sm text-red-400">{scheduleError}</div>
      )}

      {criticalPathIds.size > 0 && (
        <div className="rounded-md bg-amber-500/10 p-4 text-sm text-amber-400">
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
    return <div className="text-center text-slate-500">Loading activities...</div>;
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

function WbsTab({ wbsTree, isLoading, projectId }: { wbsTree: WbsNodeResponse[]; isLoading: boolean; projectId: string }) {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [showTemplateSelector, setShowTemplateSelector] = useState(false);
  const [parentNode, setParentNode] = useState<{ id: string; code: string; name: string } | null>(null);
  const [formData, setFormData] = useState({ code: "", name: "" });

  const { data: templatesData } = useQuery({
    queryKey: ["wbs-templates"],
    queryFn: () => wbsTemplateApi.listTemplates(),
    enabled: showTemplateSelector,
  });

  const applyTemplateMutation = useMutation({
    mutationFn: (templateId: string) => wbsTemplateApi.applyTemplate(templateId, projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["wbs", projectId] });
      setShowTemplateSelector(false);
      toast.success("WBS template applied successfully");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to apply template"));
    },
  });

  const createMutation = useMutation({
    mutationFn: (data: { code: string; name: string; projectId: string; parentNodeId?: string }) =>
      apiClient.post<AxiosResponse<ApiResponse<WbsNodeResponse>>>(`/v1/projects/${projectId}/wbs`, data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["wbs", projectId] });
      setFormData({ code: "", name: "" });
      setParentNode(null);
      setShowForm(false);
    },
    onError: (err: unknown) => {
      alert(getErrorMessage(err, "Failed to create WBS node"));
    },
  });

  const handleAddChild = (node: WbsNodeResponse) => {
    setParentNode({ id: node.id, code: node.code, name: node.name });
    setFormData({ code: "", name: "" });
    setShowForm(true);
  };

  const handleAddRoot = () => {
    setParentNode(null);
    setFormData({ code: "", name: "" });
    setShowForm(!showForm);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.code || !formData.name) return;
    createMutation.mutate({
      code: formData.code,
      name: formData.name,
      projectId,
      parentNodeId: parentNode?.id,
    });
  };

  if (isLoading) {
    return <div className="text-center text-slate-500">Loading WBS...</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end gap-2">
        <button
          onClick={() => setShowTemplateSelector(!showTemplateSelector)}
          className="inline-flex items-center gap-2 rounded-md border border-slate-700 bg-slate-800/50 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800 hover:text-white"
        >
          <FileText size={16} />
          Apply Template
        </button>
        <button
          onClick={handleAddRoot}
          className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
        >
          <Plus size={16} />
          Add WBS Node
        </button>
      </div>

      {showTemplateSelector && (
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-4 shadow-lg">
          <h3 className="text-sm font-semibold text-white mb-3">Select a WBS Template to Apply</h3>
          <p className="text-xs text-slate-500 mb-4">This will create WBS nodes from the template structure. Existing nodes will not be affected.</p>
          {!templatesData?.data || (Array.isArray(templatesData.data) && templatesData.data.length === 0) ? (
            <p className="text-sm text-slate-500">No templates available. Create templates in Settings &gt; WBS Templates.</p>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {(Array.isArray(templatesData.data) ? templatesData.data : []).map((template: WbsTemplateResponse) => (
                <button
                  key={template.id}
                  onClick={() => {
                    if (confirm(`Apply template "${template.name}" to this project? This will create WBS nodes from the template.`)) {
                      applyTemplateMutation.mutate(template.id);
                    }
                  }}
                  disabled={applyTemplateMutation.isPending}
                  className="rounded-lg border border-slate-700 bg-slate-800/50 p-4 text-left hover:border-blue-500/50 hover:bg-slate-800 transition-colors disabled:opacity-50"
                >
                  <div className="text-sm font-medium text-white">{template.name}</div>
                  <div className="text-xs text-slate-400 mt-1">{template.assetClass}</div>
                  {template.description && (
                    <div className="text-xs text-slate-500 mt-2 line-clamp-2">{template.description}</div>
                  )}
                </button>
              ))}
            </div>
          )}
          <div className="mt-3">
            <button onClick={() => setShowTemplateSelector(false)} className="text-sm text-slate-400 hover:text-white">Cancel</button>
          </div>
        </div>
      )}

      {showForm && (
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-4 shadow-lg">
          <form onSubmit={handleSubmit} className="space-y-3">
            <div className="text-sm text-slate-400">
              {parentNode
                ? <>Adding child under: <span className="font-medium text-blue-400">{parentNode.code} — {parentNode.name}</span></>
                : "Adding top-level WBS node"
              }
            </div>
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Code (e.g., PHASE-1)"
                value={formData.code}
                onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                className="flex-1 rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none"
                required
                autoFocus
              />
              <input
                type="text"
                placeholder="Name (e.g., Design Phase)"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="flex-1 rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none"
                required
              />
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-600"
              >
                {createMutation.isPending ? "Creating..." : "Create"}
              </button>
              <button
                type="button"
                onClick={() => { setShowForm(false); setParentNode(null); }}
                className="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300 hover:bg-slate-800"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {wbsTree.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
          <h3 className="text-lg font-medium text-white">No WBS Structure</h3>
          <p className="mt-2 text-slate-500">Click &quot;Add WBS Node&quot; above to create your first work package.</p>
        </div>
      ) : (
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <h2 className="mb-4 text-lg font-semibold text-white">Work Breakdown Structure</h2>
          <WbsTree nodes={wbsTree} onAddChild={handleAddChild} />
        </div>
      )}
    </div>
  );
}

function wbsStatusColor(status: string | null | undefined): string {
  switch (status) {
    case "COMPLETED":
      return "bg-emerald-500/20 text-emerald-300";
    case "IN_PROGRESS":
    case "ACTIVE":
      return "bg-blue-500/20 text-blue-300";
    case "AT_RISK":
      return "bg-amber-500/20 text-amber-300";
    case "DELAYED":
      return "bg-red-500/20 text-red-300";
    case "NOT_STARTED":
      return "bg-slate-500/20 text-slate-300";
    default:
      return "bg-slate-500/20 text-slate-300";
  }
}

function WbsTree({ nodes, level = 0, onAddChild }: { nodes: WbsNodeResponse[]; level?: number; onAddChild: (node: WbsNodeResponse) => void }) {
  return (
    <ul className="space-y-2">
      {nodes.map((node) => (
        <li key={node.id}>
          <div style={{ marginLeft: `${level * 24}px` }} className="group flex flex-wrap items-center gap-2">
            <span className="text-sm font-medium text-blue-400">{node.code}</span>
            <span className="text-sm text-slate-300">{node.name}</span>
            {node.wbsType && (
              <span className="rounded bg-slate-700/60 px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-slate-300">
                {node.wbsType}
              </span>
            )}
            {node.phase && (
              <span className="rounded bg-indigo-500/20 px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-indigo-300">
                {node.phase}
              </span>
            )}
            {node.wbsStatus && (
              <span className={`rounded px-1.5 py-0.5 text-[10px] uppercase tracking-wide ${wbsStatusColor(node.wbsStatus)}`}>
                {node.wbsStatus.replace(/_/g, " ")}
              </span>
            )}
            {node.budgetCrores != null && (
              <span className="text-xs text-emerald-400">₹{node.budgetCrores}cr</span>
            )}
            {node.plannedStart && node.plannedFinish && (
              <span className="text-xs text-slate-500">
                {node.plannedStart} → {node.plannedFinish}
              </span>
            )}
            {node.summaryDuration && (
              <span className="ml-1 text-xs text-slate-500">({node.summaryDuration}d)</span>
            )}
            <button
              onClick={() => onAddChild(node)}
              className="ml-2 rounded p-0.5 text-slate-600 opacity-0 group-hover:opacity-100 hover:bg-emerald-500/10 hover:text-emerald-400 transition-opacity"
              title="Add child node"
            >
              <Plus size={14} />
            </button>
          </div>
          {node.children && node.children.length > 0 && (
            <WbsTree nodes={node.children} level={level + 1} onAddChild={onAddChild} />
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
    return <div className="text-center text-slate-500">Loading network diagram...</div>;
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
  const [comparisonBaselineId, setComparisonBaselineId] = useState<string | null>(null);
  const [comparisonData, setComparisonData] = useState<Record<string, any[]>>({});
  const [loadingComparisonId, setLoadingComparisonId] = useState<string | null>(null);

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
          [baselineId]: response.data!,
        }));
        setExpandedBaselineId(baselineId);
      }
    } catch (error) {
      console.error("Failed to load variance data:", error);
    } finally {
      setLoadingVarianceId(null);
    }
  };

  const handleCompareSchedule = async (baselineId: string) => {
    if (comparisonBaselineId === baselineId) {
      setComparisonBaselineId(null);
      return;
    }

    if (comparisonData[baselineId]) {
      setComparisonBaselineId(baselineId);
      return;
    }

    setLoadingComparisonId(baselineId);
    try {
      const response = await baselineApi.getScheduleComparison(projectId, baselineId);
      if (response?.data) {
        setComparisonData((prev) => ({
          ...prev,
          [baselineId]: response.data!,
        }));
        setComparisonBaselineId(baselineId);
      }
    } catch (error) {
      console.error("Failed to load schedule comparison data:", error);
    } finally {
      setLoadingComparisonId(null);
    }
  };

  if (isLoading) {
    return <div className="text-center text-slate-500">Loading baselines...</div>;
  }

  return (
    <div className="space-y-6">
      {!showForm && (
        <button
          onClick={() => setShowForm(true)}
          className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
        >
          <Plus size={16} />
          Create Baseline
        </button>
      )}

      {showForm && (
        <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <h3 className="mb-4 text-lg font-semibold text-white">Create New Baseline</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-300">Name</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none"
                placeholder="Baseline name"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300">Type</label>
              <select
                value={formData.baselineType}
                onChange={(e) => setFormData({ ...formData, baselineType: e.target.value })}
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white focus:border-blue-500 focus:outline-none"
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
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-500"
              >
                {isCreating ? "Creating..." : "Create"}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="rounded-md border border-slate-700 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800/50"
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
            <div key={baseline.id} className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-white">{baseline.name}</h3>
                  <div className="mt-2 space-y-1 text-sm text-slate-400">
                    <p>Type: {baseline.baselineType}</p>
                    <p>Date: {new Date(baseline.baselineDate).toLocaleDateString()}</p>
                    <p>Activities: {baseline.totalActivities}</p>
                    {baseline.totalCost > 0 && <p>Total Cost: ${baseline.totalCost.toLocaleString()}</p>}
                  </div>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleViewVariance(baseline.id)}
                    disabled={loadingVarianceId === baseline.id}
                    className="inline-flex items-center gap-2 rounded-md bg-slate-800/50 px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700/50 disabled:bg-slate-500"
                  >
                    <Eye size={16} />
                    {expandedBaselineId === baseline.id ? "Hide Variance" : "View Variance"}
                  </button>
                  <button
                    onClick={() => handleCompareSchedule(baseline.id)}
                    disabled={loadingComparisonId === baseline.id}
                    className="inline-flex items-center gap-2 rounded-md bg-blue-500/10 px-3 py-2 text-sm font-medium text-blue-400 hover:bg-blue-500/20 disabled:bg-slate-500"
                  >
                    <Eye size={16} />
                    {comparisonBaselineId === baseline.id ? "Hide Compare" : "Compare"}
                  </button>
                  <button
                    onClick={() => {
                      if (window.confirm("Are you sure you want to delete this baseline?")) {
                        onDeleteBaseline(baseline.id);
                      }
                    }}
                    disabled={isDeleting}
                    className="inline-flex items-center gap-2 rounded-md bg-red-500/10 px-3 py-2 text-sm font-medium text-red-400 hover:bg-red-500/20 disabled:bg-slate-500"
                  >
                    <Trash2 size={16} />
                    Delete
                  </button>
                </div>
              </div>

              {expandedBaselineId === baseline.id && varianceData[baseline.id] && (
                <div className="mt-6 border-t border-slate-800 pt-6">
                  <h4 className="mb-4 font-semibold text-white">Variance Dashboard</h4>
                  <VarianceDashboard data={varianceData[baseline.id]} />
                </div>
              )}

              {comparisonBaselineId === baseline.id && comparisonData[baseline.id] && (
                <div className="mt-6 border-t border-slate-800 pt-6">
                  <h4 className="mb-4 font-semibold text-white">Schedule Comparison</h4>
                  <ScheduleComparisonTable data={comparisonData[baseline.id]} />
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
    <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
      <h3 className="text-lg font-medium text-white capitalize">{tabName}</h3>
      <p className="mt-2 text-slate-500">This feature is coming soon.</p>
    </div>
  );
}
