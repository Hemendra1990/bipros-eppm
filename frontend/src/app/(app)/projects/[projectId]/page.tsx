"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, useSearchParams, useRouter } from "next/navigation";
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
import { ListTodo, Plus, Play, Trash2, Eye, FileText, ChevronRight, ArrowRight, ChevronDown, Folder, FolderOpen, File, RefreshCw, List, FolderTree } from "lucide-react";
import { UdfSection } from "@/components/udf/UdfSection";
import { dashboardApi, type KpiSnapshot, type KpiDefinition } from "@/lib/api/dashboardApi";
import { Breadcrumb } from "@/components/common/Breadcrumb";
import toast from "react-hot-toast";
import { apiClient } from "@/lib/api/client";
import { wbsTemplateApi } from "@/lib/api/wbsTemplateApi";
import { TabTip } from "@/components/common/TabTip";
import { VarianceDashboard } from "@/components/baseline/VarianceDashboard";
import { formatDefaultCurrency } from "@/lib/hooks/useCurrency";
import { ScheduleComparisonTable } from "@/components/baseline/ScheduleComparisonTable";
import type { ProjectResponse, ActivityResponse, WbsNodeResponse, BaselineResponse, BaselineVarianceRow, ApiResponse } from "@/lib/types";
import type { WbsTemplateResponse } from "@/lib/types";
import type { AxiosResponse } from "axios";

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default function ProjectDetailPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const router = useRouter();
  const projectId = params.projectId as string;
  const isUuid = UUID_REGEX.test(projectId);
  const tab = searchParams.get("tab") || "overview";
  const [scheduleError, setScheduleError] = useState("");

  // If projectId is not a UUID, try to resolve it as a project code
  const { data: codeResolveData, isLoading: isResolvingCode } = useQuery({
    queryKey: ["project-by-code", projectId],
    queryFn: async () => {
      const result = await projectApi.listProjects(0, 200);
      const projects = result.data?.content ?? [];
      return projects.find((p) => p.code.toLowerCase() === projectId.toLowerCase()) ?? null;
    },
    enabled: !isUuid,
  });

  // Redirect to UUID-based URL when code resolves
  if (!isUuid && codeResolveData) {
    router.replace(`/projects/${codeResolveData.id}${tab !== "overview" ? `?tab=${tab}` : ""}`);
    return null;
  }

  const { data: projectData, isLoading: isLoadingProject } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
    enabled: isUuid,
  });

  const { data: activitiesData, isLoading: isLoadingActivities, refetch: refetchActivities } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 100),
    enabled: ["activities", "gantt", "network"].includes(tab),
  });

  const { data: wbsData, isLoading: isLoadingWbs } = useQuery({
    queryKey: ["wbs", projectId],
    queryFn: () => projectApi.getWbsTree(projectId),
    enabled: ["wbs", "activities"].includes(tab),
  });

  const { data: criticalPathData } = useQuery({
    queryKey: ["critical-path", projectId],
    queryFn: () => activityApi.getCriticalPath(projectId),
    enabled: tab === "activities",
  });

  const { data: relationshipsData, isLoading: isLoadingRelationships } = useQuery({
    queryKey: ["relationships", projectId],
    queryFn: () => activityApi.getRelationships(projectId),
    enabled: ["activities", "network", "gantt"].includes(tab),
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
      queryClient.invalidateQueries({ queryKey: ["criticalPath", projectId] });
      setScheduleError("");
      toast.success("Schedule calculated successfully");
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to trigger schedule");
      setScheduleError(msg);
      toast.error(msg);
    },
  });

  const deleteActivityMutation = useMutation({
    mutationFn: (activityId: string) => activityApi.deleteActivity(projectId, activityId),
    onSuccess: () => {
      refetchActivities();
      toast.success("Activity deleted");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to delete activity"));
    },
  });

  const createBaselineMutation = useMutation({
    mutationFn: (data: { name: string; baselineType: string }) =>
      baselineApi.createBaseline(projectId, data as { name: string; baselineType: "PROJECT" | "PRIMARY" | "SECONDARY" | "TERTIARY"; description?: string }),
    onSuccess: () => {
      refetchBaselines();
      toast.success("Baseline created successfully");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to create baseline"));
    },
  });

  const deleteBaselineMutation = useMutation({
    mutationFn: (baselineId: string) => baselineApi.deleteBaseline(projectId, baselineId),
    onSuccess: () => {
      refetchBaselines();
      toast.success("Baseline deleted");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to delete baseline"));
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
      key: "earlyStartDate",
      label: "ES",
      sortable: true,
      render: (value) => formatDate(value as string | null),
    },
    {
      key: "earlyFinishDate",
      label: "EF",
      sortable: true,
      render: (value) => formatDate(value as string | null),
    },
    {
      key: "lateStartDate",
      label: "LS",
      sortable: true,
      render: (value) => formatDate(value as string | null),
    },
    {
      key: "lateFinishDate",
      label: "LF",
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
            className="text-accent hover:text-blue-300 text-sm font-medium"
          >
            Edit
          </Link>
          <button
            onClick={() => {
              if (window.confirm("Are you sure you want to delete this activity?")) {
                deleteActivityMutation.mutate(String(value));
              }
            }}
            className="text-danger hover:text-danger text-sm font-medium"
          >
            Delete
          </button>
        </div>
      ),
    },
  ];

  if (isLoadingProject || isResolvingCode) {
    return (
      <div className="space-y-4">
        <div className="h-8 w-48 animate-pulse rounded bg-surface-hover/50" />
        <div className="grid grid-cols-2 gap-6">
          <div className="h-28 animate-pulse rounded-xl bg-surface-hover/50" />
          <div className="h-28 animate-pulse rounded-xl bg-surface-hover/50" />
        </div>
        <div className="h-32 animate-pulse rounded-xl bg-surface-hover/50" />
      </div>
    );
  }

  if (!project) {
    return (
      <div className="py-12 text-center">
        <p className="text-danger">Project not found</p>
        {!isUuid && (
          <p className="mt-2 text-sm text-text-muted">
            No project with code &ldquo;{projectId}&rdquo; was found. Try using the project&apos;s UUID or navigate from the projects list.
          </p>
        )}
      </div>
    );
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
      <div className="mb-4">
        <Breadcrumb items={[
          { label: "Projects", href: "/projects" },
          { label: project.name, href: `/projects/${projectId}`, active: true },
        ]} />
      </div>
      {currentTip && <TabTip title={currentTip.title} description={currentTip.description} steps={currentTip.steps} />}
      {tab === "overview" && <OverviewTab project={project} projectId={projectId} />}
      {tab === "activities" && (
        <ActivitiesTab
          projectId={projectId}
          activities={activities}
          isLoading={isLoadingActivities}
          isLoadingWbs={isLoadingWbs}
          wbsTree={wbsTree}
          columns={activityColumns}
          criticalPathIds={criticalPathIds}
          onRunSchedule={() => scheduleMutation.mutate()}
          isScheduling={scheduleMutation.isPending}
          scheduleError={scheduleError}
          relationships={relationshipsData?.data ?? []}
          onDeleteActivity={(id) => deleteActivityMutation.mutate(id)}
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
          projectId={projectId}
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

function OverviewTab({ project, projectId }: { project: ProjectResponse; projectId: string }) {
  const queryClient = useQueryClient();
  const [isTransitioning, setIsTransitioning] = useState(false);

  const { data: kpiSnapshotsData, isLoading: isLoadingKpis } = useQuery({
    queryKey: ["project-kpis", projectId],
    queryFn: () => dashboardApi.getProjectKpiSnapshots(projectId),
  });

  const { data: kpiDefsData } = useQuery({
    queryKey: ["kpi-definitions"],
    queryFn: () => dashboardApi.getKpiDefinitions(),
  });

  const calculateKpisMutation = useMutation({
    mutationFn: () => dashboardApi.calculateProjectKpis(projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["project-kpis", projectId] });
      toast.success("KPIs recalculated");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to calculate KPIs"));
    },
  });

  const kpiSnapshots = Array.isArray(kpiSnapshotsData?.data) ? kpiSnapshotsData.data : [];
  const rawKpiDefs = kpiDefsData?.data;
  const kpiDefs = Array.isArray(rawKpiDefs) ? rawKpiDefs : (rawKpiDefs as unknown as { content?: KpiDefinition[] })?.content ?? [];
  const kpiDefMap = new Map(kpiDefs.map((d: KpiDefinition) => [d.id, d]));

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
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="text-sm font-medium text-text-secondary">Code</h3>
          <p className="mt-2 text-2xl font-bold text-text-primary">{project.code}</p>
        </div>
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="text-sm font-medium text-text-secondary">Status</h3>
          <div className="mt-2 flex items-center gap-3">
            <StatusBadge status={project.status} />
            {availableTransitions.length > 0 && (
              <div className="flex gap-2">
                {availableTransitions.map((t) => (
                  <button
                    key={t.value}
                    onClick={() => handleStatusTransition(t.value)}
                    disabled={isTransitioning}
                    className="rounded-md border border-border px-3 py-1 text-xs font-medium text-text-secondary hover:bg-surface-hover/50 disabled:opacity-50"
                  >
                    {isTransitioning ? "..." : t.label}
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
        <h3 className="text-sm font-medium text-text-secondary">Description</h3>
        <p className="mt-2 text-text-primary">{project.description || "No description"}</p>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="text-sm font-medium text-text-secondary">Planned Start Date</h3>
          <p className="mt-2 text-lg text-text-primary">{formatDate(project.plannedStartDate)}</p>
        </div>
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="text-sm font-medium text-text-secondary">Planned Finish Date</h3>
          <p className="mt-2 text-lg text-text-primary">{formatDate(project.plannedFinishDate)}</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="text-sm font-medium text-text-secondary">Priority</h3>
          <p className={`mt-2 text-lg font-medium ${getPriorityInfo(project.priority).color}`}>
            {getPriorityInfo(project.priority).label} ({project.priority})
          </p>
        </div>
        <DataDateCard project={project} />
      </div>

      {/* KPI Mini-Dashboard */}
      <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-medium text-text-secondary uppercase tracking-wider">Key Performance Indicators</h3>
          <button
            onClick={() => calculateKpisMutation.mutate()}
            disabled={calculateKpisMutation.isPending}
            className="inline-flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-hover/50 disabled:opacity-50"
          >
            <RefreshCw size={12} className={calculateKpisMutation.isPending ? "animate-spin" : ""} />
            {calculateKpisMutation.isPending ? "Calculating..." : "Recalculate"}
          </button>
        </div>
        {isLoadingKpis ? (
          <div className="grid grid-cols-3 gap-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-20 animate-pulse rounded-lg bg-surface-hover/50" />
            ))}
          </div>
        ) : kpiSnapshots.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border py-8 text-center">
            <p className="text-text-secondary text-sm">No KPI data yet. Click &quot;Recalculate&quot; to generate KPI snapshots.</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
            {kpiSnapshots.map((kpi: KpiSnapshot) => {
              const def = kpiDefMap.get(kpi.kpiDefinitionId);
              const statusColor = kpi.status === "GREEN" ? "text-success" : kpi.status === "AMBER" ? "text-warning" : "text-danger";
              const statusBg = kpi.status === "GREEN" ? "bg-success/10 border-success/20" : kpi.status === "AMBER" ? "bg-warning/10 border-warning/20" : "bg-danger/10 border-danger/20";
              return (
                <div key={kpi.id} className={`rounded-lg border p-4 ${statusBg}`}>
                  <p className="text-xs font-medium text-text-secondary truncate">{def?.name ?? kpi.kpiDefinitionId}</p>
                  <p className={`mt-1 text-2xl font-bold ${statusColor}`}>
                    {typeof kpi.value === "number" ? kpi.value.toFixed(2) : kpi.value}
                    {def?.unit ? <span className="ml-1 text-sm font-normal text-text-muted">{def.unit}</span> : null}
                  </p>
                  <p className="mt-1 text-xs text-text-muted">
                    {kpi.calculatedAt ? new Date(kpi.calculatedAt).toLocaleDateString() : ""}
                  </p>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <UdfSection entityId={projectId} subject="PROJECT" projectId={projectId} />
    </div>
  );
}

function DataDateCard({ project }: { project: ProjectResponse }) {
  const queryClient = useQueryClient();
  const [isEditing, setIsEditing] = useState(false);
  const [dateValue, setDateValue] = useState(project.dataDate ?? "");
  const [isSaving, setIsSaving] = useState(false);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await projectApi.updateProject(project.id, { dataDate: dateValue || undefined });
      queryClient.invalidateQueries({ queryKey: ["project", project.id] });
      toast.success("Data date updated");
      setIsEditing(false);
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Failed to update data date"));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
      <h3 className="text-sm font-medium text-text-secondary">Data Date</h3>
      {isEditing ? (
        <div className="mt-2 flex items-center gap-2">
          <input
            type="date"
            value={dateValue}
            onChange={(e) => setDateValue(e.target.value)}
            className="rounded-md border border-border bg-surface-hover px-3 py-1.5 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
          />
          <button
            onClick={handleSave}
            disabled={isSaving}
            className="rounded-md bg-accent px-3 py-1.5 text-xs font-medium text-text-primary hover:bg-accent-hover disabled:bg-border"
          >
            {isSaving ? "..." : "Save"}
          </button>
          <button
            onClick={() => { setIsEditing(false); setDateValue(project.dataDate ?? ""); }}
            className="rounded-md border border-border px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-surface-hover/50"
          >
            Cancel
          </button>
        </div>
      ) : (
        <div className="mt-2 flex items-center gap-3">
          <p className="text-lg text-text-primary">{project.dataDate ? formatDate(project.dataDate) : "Not set"}</p>
          <button
            onClick={() => setIsEditing(true)}
            className="rounded-md border border-border px-3 py-1 text-xs font-medium text-text-secondary hover:bg-surface-hover/50"
          >
            Set
          </button>
        </div>
      )}
    </div>
  );
}

type EnrichedActivity = ActivityResponse & { isCritical: boolean; predCount: number; succCount: number };

function ActivitiesTab({
  projectId,
  activities,
  isLoading,
  isLoadingWbs,
  wbsTree,
  columns,
  criticalPathIds,
  onRunSchedule,
  isScheduling,
  scheduleError,
  relationships = [],
  onDeleteActivity,
}: {
  projectId: string;
  activities: ActivityResponse[];
  isLoading: boolean;
  isLoadingWbs: boolean;
  wbsTree: WbsNodeResponse[];
  columns: ColumnDef<ActivityResponse>[];
  criticalPathIds: Set<string>;
  onRunSchedule: () => void;
  isScheduling: boolean;
  scheduleError: string;
  relationships?: Array<{ id?: string; predecessorActivityId: string; successorActivityId: string; relationshipType: string }>;
  onDeleteActivity?: (id: string) => void;
}) {
  const [viewMode, setViewMode] = useState<"list" | "tree">("tree");

  // Build dependency count map
  const predCountMap = new Map<string, number>();
  const succCountMap = new Map<string, number>();
  for (const rel of relationships) {
    predCountMap.set(rel.successorActivityId, (predCountMap.get(rel.successorActivityId) ?? 0) + 1);
    succCountMap.set(rel.predecessorActivityId, (succCountMap.get(rel.predecessorActivityId) ?? 0) + 1);
  }

  const highlightedActivities = activities.map((a) => ({
    ...a,
    isCritical: criticalPathIds.has(a.id),
    predCount: predCountMap.get(a.id) ?? 0,
    succCount: succCountMap.get(a.id) ?? 0,
  }));

  const enrichedColumns: ColumnDef<EnrichedActivity>[] = columns.map((col) => {
    if (col.key === "code") {
      return {
        ...col,
        render: (value, row: ActivityResponse & { isCritical: boolean }) => (
          <span className={row.isCritical ? "font-bold text-danger" : ""}>
            {String(value)}
          </span>
        ),
      };
    }
    if (col.key === "name") {
      return {
        ...col,
        render: (value, row: ActivityResponse & { isCritical: boolean }) => (
          <span className={row.isCritical ? "font-bold text-danger" : ""}>
            {String(value)}
          </span>
        ),
      };
    }
    return col;
  });

  // Add dependency counts column
  enrichedColumns.push({
    key: "predCount" as string,
    label: "Deps",
    render: (_value, row) => {
      const p = row.predCount;
      const s = row.succCount;
      if (p === 0 && s === 0) return <span className="text-text-muted">—</span>;
      return (
        <span className="text-xs text-text-secondary">
          {p > 0 && <span className="text-accent">{p}P</span>}
          {p > 0 && s > 0 && " / "}
          {s > 0 && <span className="text-success">{s}S</span>}
        </span>
      );
    },
  });

  const isBusy = isLoading || (viewMode === "tree" && isLoadingWbs);

  if (isBusy) {
    return (
      <div className="text-center text-text-muted">
        {viewMode === "tree" ? "Loading activities and WBS..." : "Loading activities..."}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <Link
          href={`/projects/${projectId}/activities/new`}
          className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          <Plus size={16} />
          New Activity
        </Link>
        <button
          onClick={onRunSchedule}
          disabled={isScheduling}
          className="inline-flex items-center gap-2 rounded-md bg-success px-4 py-2 text-sm font-medium text-text-primary hover:bg-success/80 disabled:opacity-50"
        >
          <Play size={16} />
          {isScheduling ? "Running..." : "Run Schedule"}
        </button>

        {/* View Mode Toggle */}
        <div className="ml-auto inline-flex rounded-lg border border-border bg-surface/60 p-0.5">
          <button
            onClick={() => setViewMode("list")}
            className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
              viewMode === "list"
                ? "bg-accent text-text-primary"
                : "text-text-secondary hover:bg-surface-hover/50 hover:text-text-primary"
            }`}
          >
            <List size={14} />
            List
          </button>
          <button
            onClick={() => setViewMode("tree")}
            className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
              viewMode === "tree"
                ? "bg-accent text-text-primary"
                : "text-text-secondary hover:bg-surface-hover/50 hover:text-text-primary"
            }`}
          >
            <FolderTree size={14} />
            WBS Tree
          </button>
        </div>
      </div>

      {scheduleError && (
        <div className="rounded-md bg-danger/10 p-4 text-sm text-danger">{scheduleError}</div>
      )}

      {criticalPathIds.size > 0 && (
        <div className="rounded-md bg-warning/10 p-4 text-sm text-warning">
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
      ) : viewMode === "tree" ? (
        <ActivitiesWbsTreeTable
          wbsNodes={wbsTree}
          activities={highlightedActivities}
          enrichedColumns={enrichedColumns}
          projectId={projectId}
          onDeleteActivity={onDeleteActivity}
        />
      ) : (
        <DataTable columns={enrichedColumns} data={highlightedActivities} rowKey="id" searchable searchPlaceholder="Search activities..." />
      )}
    </div>
  );
}

interface TreeNodeData {
  id: string;
  code: string;
  name: string;
  type: "wbs" | "activity";
  children?: TreeNodeData[];
  activity?: ActivityResponse & { isCritical: boolean; predCount: number; succCount: number };
}

function buildActivityWbsTree(
  wbsNodes: WbsNodeResponse[],
  activities: Array<ActivityResponse & { isCritical: boolean; predCount: number; succCount: number }>
): TreeNodeData[] {
  const activityMap = new Map<string, Array<ActivityResponse & { isCritical: boolean; predCount: number; succCount: number }>>();
  for (const a of activities) {
    const list = activityMap.get(a.wbsNodeId) ?? [];
    list.push(a);
    activityMap.set(a.wbsNodeId, list);
  }

  function mapWbsNode(wbs: WbsNodeResponse): TreeNodeData {
    const childWbsNodes = wbs.children?.map(mapWbsNode) ?? [];
    const childActivities = (activityMap.get(wbs.id) ?? [])
      .slice()
      .sort((a, b) => a.code.localeCompare(b.code));

    const activityNodes: TreeNodeData[] = childActivities.map((activity) => ({
      id: `activity-${activity.id}`,
      code: activity.code,
      name: activity.name,
      type: "activity",
      activity,
    }));

    return {
      id: `wbs-${wbs.id}`,
      code: wbs.code,
      name: wbs.name,
      type: "wbs",
      children: [...childWbsNodes, ...activityNodes],
    };
  }

  return wbsNodes.map(mapWbsNode);
}

function hasVisibleContent(node: TreeNodeData): boolean {
  if (node.type === "activity") return true;
  if (!node.children || node.children.length === 0) return false;
  return node.children.some(hasVisibleContent);
}

function filterEmptyNodes(nodes: TreeNodeData[]): TreeNodeData[] {
  return nodes
    .filter(hasVisibleContent)
    .map((node) => {
      if (node.type === "activity") return node;
      return {
        ...node,
        children: node.children ? filterEmptyNodes(node.children) : undefined,
      };
    });
}

function ActivitiesWbsTreeTable({
  wbsNodes,
  activities,
  enrichedColumns,
  projectId,
  onDeleteActivity,
}: {
  wbsNodes: WbsNodeResponse[];
  activities: Array<ActivityResponse & { isCritical: boolean; predCount: number; succCount: number }>;
  enrichedColumns: ColumnDef<EnrichedActivity>[];
  projectId: string;
  onDeleteActivity?: (id: string) => void;
}) {
  const tree = React.useMemo(() => {
    const rawTree = buildActivityWbsTree(wbsNodes, activities);
    return filterEmptyNodes(rawTree);
  }, [wbsNodes, activities]);

  const [expanded, setExpanded] = React.useState<Record<string, boolean>>(() => {
    const initial: Record<string, boolean> = {};
    const expandAll = (nodes: TreeNodeData[]) => {
      for (const node of nodes) {
        if (node.children && node.children.length > 0) {
          initial[node.id] = true;
          expandAll(node.children);
        }
      }
    };
    expandAll(tree);
    return initial;
  });

  const toggle = (id: string) => {
    setExpanded((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  if (tree.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-border py-12 text-center">
        <h3 className="text-lg font-medium text-text-primary">No WBS Structure</h3>
        <p className="mt-2 text-text-secondary">
          No WBS nodes or activities found for this project.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border bg-surface/50 shadow-sm">
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="border-b border-border bg-surface/80">
            <tr>
              {enrichedColumns.map((col) => (
                <th
                  key={String(col.key)}
                  className="px-4 py-3 text-left text-sm font-semibold text-text-secondary whitespace-nowrap"
                >
                  {col.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-border/50">
            {tree.map((node) => (
              <TreeRow
                key={node.id}
                node={node}
                depth={0}
                expanded={expanded}
                toggle={toggle}
                enrichedColumns={enrichedColumns}
                projectId={projectId}
                onDeleteActivity={onDeleteActivity}
              />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function TreeRow({
  node,
  depth,
  expanded,
  toggle,
  enrichedColumns,
  projectId,
  onDeleteActivity,
}: {
  node: TreeNodeData;
  depth: number;
  expanded: Record<string, boolean>;
  toggle: (id: string) => void;
  enrichedColumns: ColumnDef<EnrichedActivity>[];
  projectId: string;
  onDeleteActivity?: (id: string) => void;
}) {
  const isWbs = node.type === "wbs";
  const hasChildren = node.children && node.children.length > 0;
  const isExpanded = expanded[node.id] ?? false;

  if (isWbs) {
    return (
      <React.Fragment key={node.id}>
        <tr className="hover:bg-surface-hover/30">
          <td className="px-4 py-3 text-sm" colSpan={enrichedColumns.length}>
            <div className="flex items-center gap-2" style={{ paddingLeft: `${depth * 24}px` }}>
              {hasChildren ? (
                <button
                  onClick={() => toggle(node.id)}
                  className="p-0.5 hover:bg-surface-hover rounded shrink-0"
                >
                  {isExpanded ? (
                    <ChevronDown size={16} className="text-text-muted" />
                  ) : (
                    <ChevronRight size={16} className="text-text-muted" />
                  )}
                </button>
              ) : (
                <div className="w-[22px] shrink-0" />
              )}
              <FolderOpen size={16} className="text-accent shrink-0" />
              <span className="font-semibold text-text-primary">{node.code}</span>
              <span className="text-text-secondary">{node.name}</span>
              {hasChildren && (
                <span className="ml-1 text-xs text-text-muted bg-surface-hover px-1.5 py-0.5 rounded-full">
                  {node.children?.length ?? 0}
                </span>
              )}
            </div>
          </td>
        </tr>
        {isExpanded &&
          hasChildren &&
          node.children?.map((child) => (
            <TreeRow
              key={child.id}
              node={child}
              depth={depth + 1}
              expanded={expanded}
              toggle={toggle}
              enrichedColumns={enrichedColumns}
              projectId={projectId}
              onDeleteActivity={onDeleteActivity}
            />
          ))}
      </React.Fragment>
    );
  }

  // Activity row
  const activity = node.activity!;
  return (
    <tr key={node.id} className="hover:bg-surface/80">
      {enrichedColumns.map((col) => {
        const value = (activity as unknown as Record<string, unknown>)[col.key];
        return (
          <td
            key={String(col.key)}
            className="px-4 py-3 text-sm whitespace-nowrap"
          >
            <div style={{ paddingLeft: col.key === "code" ? `${depth * 24 + 24}px` : undefined }}>
              {col.render ? col.render(value, activity) : String(value ?? "-")}
            </div>
          </td>
        );
      })}
    </tr>
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
    return <div className="text-center text-text-muted">Loading activities...</div>;
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
  const [selectedWbs, setSelectedWbs] = useState<{ id: string; code: string; name: string } | null>(null);

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
    mutationFn: (data: { code: string; name: string; projectId: string; parentId?: string }) =>
      apiClient.post<AxiosResponse<ApiResponse<WbsNodeResponse>>>(`/v1/projects/${projectId}/wbs`, data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["wbs", projectId] });
      setFormData({ code: "", name: "" });
      setParentNode(null);
      setShowForm(false);
      toast.success("WBS node created");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to create WBS node"));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (nodeId: string) =>
      apiClient.delete(`/v1/projects/${projectId}/wbs/${nodeId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["wbs", projectId] });
      toast.success("WBS node deleted");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to delete WBS node"));
    },
  });

  const handleDelete = (node: WbsNodeResponse) => {
    if (confirm(`Delete "${node.code} — ${node.name}"${node.children?.length ? " and all its children" : ""}?`)) {
      deleteMutation.mutate(node.id);
    }
  };

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
      parentId: parentNode?.id,
    });
  };

  if (isLoading) {
    return <div className="text-center text-text-muted">Loading WBS...</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end gap-2">
        <button
          onClick={() => setShowTemplateSelector(!showTemplateSelector)}
          className="inline-flex items-center gap-2 rounded-md border border-border bg-surface-hover/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover hover:text-text-primary"
        >
          <FileText size={16} />
          Apply Template
        </button>
        <button
          onClick={handleAddRoot}
          className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          <Plus size={16} />
          Add WBS Node
        </button>
      </div>

      {showTemplateSelector && (
        <div className="rounded-xl border border-border bg-surface/50 p-4 shadow-lg">
          <h3 className="text-sm font-semibold text-text-primary mb-3">Select a WBS Template to Apply</h3>
          <p className="text-xs text-text-muted mb-4">This will create WBS nodes from the template structure. Existing nodes will not be affected.</p>
          {!templatesData?.data || (Array.isArray(templatesData.data) && templatesData.data.length === 0) ? (
            <p className="text-sm text-text-muted">No templates available. Create templates in Settings &gt; WBS Templates.</p>
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
                  className="rounded-lg border border-border bg-surface-hover/50 p-4 text-left hover:border-accent/50 hover:bg-surface-hover transition-colors disabled:opacity-50"
                >
                  <div className="text-sm font-medium text-text-primary">{template.name}</div>
                  <div className="text-xs text-text-secondary mt-1">{template.assetClass}</div>
                  {template.description && (
                    <div className="text-xs text-text-muted mt-2 line-clamp-2">{template.description}</div>
                  )}
                </button>
              ))}
            </div>
          )}
          <div className="mt-3">
            <button onClick={() => setShowTemplateSelector(false)} className="text-sm text-text-secondary hover:text-text-primary">Cancel</button>
          </div>
        </div>
      )}

      {showForm && (
        <div className="rounded-xl border border-border bg-surface/50 p-4 shadow-lg">
          <form onSubmit={handleSubmit} className="space-y-3">
            <div className="text-sm text-text-secondary">
              {parentNode
                ? <>Adding child under: <span className="font-medium text-accent">{parentNode.code} — {parentNode.name}</span></>
                : "Adding top-level WBS node"
              }
            </div>
            <div className="flex gap-2">
              <input
                type="text"
                placeholder="Code (e.g., PHASE-1)"
                value={formData.code}
                onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                className="flex-1 rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
                required
                autoFocus
              />
              <input
                type="text"
                placeholder="Name (e.g., Design Phase)"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="flex-1 rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
                required
              />
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border"
              >
                {createMutation.isPending ? "Creating..." : "Create"}
              </button>
              <button
                type="button"
                onClick={() => { setShowForm(false); setParentNode(null); }}
                className="rounded-md border border-border px-3 py-2 text-sm text-text-secondary hover:bg-surface-hover"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {wbsTree.length === 0 ? (
        <div className="rounded-lg border border-dashed border-border py-12 text-center">
          <h3 className="text-lg font-medium text-text-primary">No WBS Structure</h3>
          <p className="mt-2 text-text-muted">Click &quot;Add WBS Node&quot; above to create your first work package.</p>
        </div>
      ) : (
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h2 className="mb-4 text-lg font-semibold text-text-primary">Work Breakdown Structure</h2>
          <WbsTree
            nodes={wbsTree}
            onAddChild={handleAddChild}
            onDelete={handleDelete}
            onSelect={(node) => setSelectedWbs(selectedWbs?.id === node.id ? null : { id: node.id, code: node.code, name: node.name })}
            selectedId={selectedWbs?.id ?? null}
          />
        </div>
      )}

      {selectedWbs && (
        <div className="space-y-2">
          <div className="text-sm text-text-secondary">
            Custom fields for: <span className="font-medium text-accent">{selectedWbs.code} — {selectedWbs.name}</span>
          </div>
          <UdfSection entityId={selectedWbs.id} subject="WBS" projectId={projectId} />
        </div>
      )}
    </div>
  );
}

function WbsTree({
  nodes,
  level = 0,
  onAddChild,
  onDelete,
  onSelect,
  selectedId,
  isLast = [],
}: {
  nodes: WbsNodeResponse[];
  level?: number;
  onAddChild: (node: WbsNodeResponse) => void;
  onDelete: (node: WbsNodeResponse) => void;
  onSelect?: (node: WbsNodeResponse) => void;
  selectedId?: string | null;
  isLast?: boolean[];
}) {
  const [expanded, setExpanded] = useState<Record<string, boolean>>(() => {
    const initial: Record<string, boolean> = {};
    const expand = (items: WbsNodeResponse[]) => {
      for (const n of items) {
        if (n.children?.length) {
          initial[n.id] = true;
          expand(n.children);
        }
      }
    };
    if (level === 0) expand(nodes);
    return initial;
  });

  const toggle = (id: string) => {
    setExpanded((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  return (
    <div className={level === 0 ? "font-mono text-sm" : ""}>
      {nodes.map((node, index) => {
        const hasChildren = node.children && node.children.length > 0;
        const isOpen = expanded[node.id] ?? false;
        const isLastNode = index === nodes.length - 1;

        return (
          <div key={node.id}>
            <div
              onClick={() => onSelect?.(node)}
              className={`group flex items-center hover:bg-surface-hover/50 rounded-md py-1 pr-2 transition-colors cursor-pointer ${selectedId === node.id ? "bg-accent/10 ring-1 ring-blue-500/30" : ""}`}
            >
              {/* Tree guide lines */}
              {Array.from({ length: level }).map((_, i) => (
                <span key={i} className="inline-flex w-6 justify-center flex-shrink-0">
                  {isLast[i] ? (
                    <span className="w-px" />
                  ) : (
                    <span className="w-px bg-surface-active h-full min-h-[28px]" />
                  )}
                </span>
              ))}

              {/* Branch connector */}
              {level > 0 && (
                <span className="inline-flex w-6 items-center justify-center flex-shrink-0 text-text-muted">
                  {isLastNode ? "└" : "├"}
                  <span className="inline-block w-2 h-px bg-surface-active" />
                </span>
              )}

              {/* Expand/collapse toggle */}
              <button
                onClick={() => hasChildren && toggle(node.id)}
                className={`flex-shrink-0 w-5 h-5 flex items-center justify-center rounded ${
                  hasChildren
                    ? "text-text-secondary hover:text-text-primary hover:bg-surface-active cursor-pointer"
                    : "text-transparent cursor-default"
                }`}
              >
                {hasChildren ? (
                  isOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />
                ) : null}
              </button>

              {/* Folder / File icon */}
              <span className="flex-shrink-0 mr-2">
                {hasChildren ? (
                  isOpen ? (
                    <FolderOpen size={16} className="text-warning" />
                  ) : (
                    <Folder size={16} className="text-amber-500" />
                  )
                ) : (
                  <File size={16} className="text-text-muted" />
                )}
              </span>

              {/* Node content */}
              <span className="font-semibold text-accent mr-2 flex-shrink-0">{node.code}</span>
              <span className="text-text-secondary truncate">{node.name}</span>

              {/* Duration / percent badges */}
              {node.summaryDuration != null && (
                <span className="ml-2 rounded-full bg-surface-hover px-2 py-0.5 text-xs text-text-secondary flex-shrink-0">
                  {node.summaryDuration}d
                </span>
              )}
              {node.summaryPercentComplete != null && (
                <span className="ml-1 rounded-full bg-emerald-900/40 px-2 py-0.5 text-xs text-success flex-shrink-0">
                  {node.summaryPercentComplete}%
                </span>
              )}

              {/* Action buttons */}
              <span className="ml-auto flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">
                <button
                  onClick={() => onAddChild(node)}
                  className="rounded p-1 text-text-muted hover:bg-success/10 hover:text-success"
                  title="Add child node"
                >
                  <Plus size={14} />
                </button>
                <button
                  onClick={() => onDelete(node)}
                  className="rounded p-1 text-text-muted hover:bg-danger/10 hover:text-danger"
                  title="Delete node"
                >
                  <Trash2 size={14} />
                </button>
              </span>
            </div>

            {/* Render children if expanded */}
            {hasChildren && isOpen && (
              <WbsTree
                nodes={node.children}
                level={level + 1}
                onAddChild={onAddChild}
                onDelete={onDelete}
                onSelect={onSelect}
                selectedId={selectedId}
                isLast={[...isLast, isLastNode]}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}

function NetworkTab({
  projectId,
  activities,
  relationships,
  isLoading,
}: {
  projectId: string;
  activities: ActivityResponse[];
  relationships: Array<{
    predecessorActivityId: string;
    successorActivityId: string;
    relationshipType: string;
  }>;
  isLoading: boolean;
}) {
  const router = useRouter();

  if (isLoading) {
    return <div className="text-center text-text-muted">Loading network diagram...</div>;
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

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-text-secondary">
          {relationships.length} relationship(s) defined
        </p>
        <button
          onClick={() => router.push(`/projects/${projectId}/relationships`)}
          className="flex items-center gap-1 rounded-md bg-accent/20 px-4 py-2 text-sm font-medium text-accent hover:bg-accent/30 transition-colors"
        >
          Manage Relationships
          <ArrowRight size={14} />
        </button>
      </div>
      <NetworkDiagram activities={activities} relationships={relationships} />
    </div>
  );
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
    return <div className="text-center text-text-muted">Loading baselines...</div>;
  }

  return (
    <div className="space-y-6">
      {!showForm && (
        <button
          onClick={() => setShowForm(true)}
          className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          <Plus size={16} />
          Create Baseline
        </button>
      )}

      {showForm && (
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <h3 className="mb-4 text-lg font-semibold text-text-primary">Create New Baseline</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-text-secondary">Name</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none"
                placeholder="Baseline name"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Type</label>
              <select
                value={formData.baselineType}
                onChange={(e) => setFormData({ ...formData, baselineType: e.target.value })}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
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
                className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                {isCreating ? "Creating..." : "Create"}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
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
            <div key={baseline.id} className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-text-primary">{baseline.name}</h3>
                  <div className="mt-2 space-y-1 text-sm text-text-secondary">
                    <p>Type: {baseline.baselineType}</p>
                    <p>Date: {new Date(baseline.baselineDate).toLocaleDateString()}</p>
                    <p>Activities: {baseline.totalActivities}</p>
                    {baseline.totalCost > 0 && <p>Total Cost: {formatDefaultCurrency(baseline.totalCost)}</p>}
                  </div>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleViewVariance(baseline.id)}
                    disabled={loadingVarianceId === baseline.id}
                    className="inline-flex items-center gap-2 rounded-md bg-surface-hover/50 px-3 py-2 text-sm font-medium text-text-secondary hover:bg-surface-active/50 disabled:opacity-50"
                  >
                    <Eye size={16} />
                    {expandedBaselineId === baseline.id ? "Hide Variance" : "View Variance"}
                  </button>
                  <button
                    onClick={() => handleCompareSchedule(baseline.id)}
                    disabled={loadingComparisonId === baseline.id}
                    className="inline-flex items-center gap-2 rounded-md bg-accent/10 px-3 py-2 text-sm font-medium text-accent hover:bg-accent-hover/20 disabled:opacity-50"
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
                    className="inline-flex items-center gap-2 rounded-md bg-danger/10 px-3 py-2 text-sm font-medium text-danger hover:bg-danger/20 disabled:opacity-50"
                  >
                    <Trash2 size={16} />
                    Delete
                  </button>
                </div>
              </div>

              {expandedBaselineId === baseline.id && varianceData[baseline.id] && (
                <div className="mt-6 border-t border-border pt-6">
                  <h4 className="mb-4 font-semibold text-text-primary">Variance Dashboard</h4>
                  <VarianceDashboard data={varianceData[baseline.id]} />
                </div>
              )}

              {comparisonBaselineId === baseline.id && comparisonData[baseline.id] && (
                <div className="mt-6 border-t border-border pt-6">
                  <h4 className="mb-4 font-semibold text-text-primary">Schedule Comparison</h4>
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
    <div className="rounded-lg border border-dashed border-border py-12 text-center">
      <h3 className="text-lg font-medium text-text-primary capitalize">{tabName}</h3>
      <p className="mt-2 text-text-muted">This feature is coming soon.</p>
    </div>
  );
}
