"use client";

import { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";
import { resourceApi, type ResourceAssignmentResponse } from "@/lib/api/resourceApi";
import { resourceRoleApi, type ResourceRole } from "@/lib/api/resourceRoleApi";
import { resourceHistogramApi } from "@/lib/api/resourceHistogramApi";
import { activityApi, type ActivityResponse } from "@/lib/api/activityApi";
import { projectResourceApi, type ProjectResourceResponse } from "@/lib/api/projectResourceApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { Plus, SlidersHorizontal } from "lucide-react";
import { ResourceLevelingDialog } from "./ResourceLevelingDialog";
import { formatDefaultCurrency } from "@/lib/hooks/useCurrency";
import { UdfSection } from "@/components/udf/UdfSection";
import { ResourceAssignmentTree, ViewModeToggle, type AssignmentRow } from "./ResourceAssignmentTree";

interface ResourceAssignmentRow {
  id: string;
  activityId: string;
  resourceId: string | null;
  roleId: string | null;
  projectId: string;
  resourceName: string;
  roleName: string | null;
  activityName: string;
  plannedUnits: number;
  actualUnits: number;
  remainingUnits: number;
  rateType: string;
  plannedCost: number;
  actualCost: number;
  staffed: boolean;
}

export function ResourceAssignmentsTab({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [assignMode, setAssignMode] = useState<"ROLE" | "RESOURCE">("RESOURCE");
  const [formData, setFormData] = useState({
    activityId: "",
    resourceId: "",
    roleId: "",
    plannedUnits: "",
    rateType: "STANDARD",
  });
  const [selectedResourceId, setSelectedResourceId] = useState<string>("");
  const [showLeveling, setShowLeveling] = useState(false);
  const [selectedAssignment, setSelectedAssignment] = useState<{ id: string; resourceName: string; activityName: string } | null>(null);
  const [viewMode, setViewMode] = useState<"flat" | "activity" | "resourceType">("activity");

  const { data: assignmentsData, isLoading: isLoadingAssignments } = useQuery({
    queryKey: ["resource-assignments", projectId],
    queryFn: () => resourceApi.getProjectResourceAssignments(projectId, 0, 100),
  });

  const { data: poolData } = useQuery({
    queryKey: ["resource-pool", projectId],
    queryFn: () => projectResourceApi.listPool(projectId),
  });

  const { data: rolesData } = useQuery({
    queryKey: ["resource-roles"],
    queryFn: () => resourceRoleApi.list(),
  });

  const { data: activitiesData } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 100),
  });

  const { data: histogramData, isLoading: isLoadingHistogram } = useQuery({
    queryKey: ["resource-histogram", projectId, selectedResourceId],
    queryFn: () =>
      selectedResourceId
        ? resourceHistogramApi.getHistogram(projectId, selectedResourceId)
        : Promise.resolve({ data: [], success: true } as unknown as ReturnType<typeof resourceHistogramApi.getHistogram>),
    enabled: !!selectedResourceId,
  });

  const assignMutation = useMutation({
    mutationFn: () =>
      resourceApi.createProjectResourceAssignment(projectId, {
        activityId: formData.activityId,
        resourceId: formData.resourceId || undefined,
        roleId: formData.roleId || undefined,
        projectId,
        plannedUnits: parseFloat(formData.plannedUnits),
        rateType: formData.rateType,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resource-assignments", projectId] });
      setShowForm(false);
      setFormData({
        activityId: "",
        resourceId: "",
        roleId: "",
        plannedUnits: "",
        rateType: "STANDARD",
      });
    },
  });

  const recomputeCostsMutation = useMutation({
    mutationFn: () => resourceApi.recomputeProjectAssignmentCosts(projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resource-assignments", projectId] });
    },
  });

  const assignments = useMemo<ResourceAssignmentResponse[]>(() => {
    const raw = assignmentsData?.data as unknown;
    return Array.isArray(raw)
      ? (raw as ResourceAssignmentResponse[])
      : ((raw as { content?: ResourceAssignmentResponse[] } | undefined)?.content ?? []);
  }, [assignmentsData]);

  const pool = useMemo<ProjectResourceResponse[]>(() => {
    const raw = poolData?.data as unknown;
    return Array.isArray(raw) ? (raw as ProjectResourceResponse[]) : [];
  }, [poolData]);

  const roles = useMemo<ResourceRole[]>(() => {
    const raw = rolesData?.data as unknown;
    return Array.isArray(raw)
      ? (raw as ResourceRole[])
      : ((raw as { content?: ResourceRole[] } | undefined)?.content ?? []);
  }, [rolesData]);

  const activities = useMemo<ActivityResponse[]>(() => {
    const raw = activitiesData?.data as unknown;
    return Array.isArray(raw)
      ? (raw as ActivityResponse[])
      : ((raw as { content?: ActivityResponse[] } | undefined)?.content ?? []);
  }, [activitiesData]);

  const histogramEntries = histogramData?.data ?? [];

  const assignmentRows: ResourceAssignmentRow[] = useMemo(() => {
    const poolMap = new Map(pool.map((p) => [p.resourceId, p]));
    const activityMap = new Map(activities.map((a) => [a.id, a]));

    return assignments.map((a) => {
      const poolEntry = a.resourceId ? poolMap.get(a.resourceId) : undefined;
      const activity = activityMap.get(a.activityId);
      const anyA = a as unknown as Record<string, unknown>;
      return {
        id: a.id,
        activityId: a.activityId,
        resourceId: a.resourceId ?? null,
        roleId: a.roleId ?? null,
        projectId: (anyA.projectId as string) ?? projectId,
        resourceName: poolEntry?.resourceName ?? a.resourceName ?? a.resourceId ?? "—",
        roleName: a.roleName ?? null,
        activityName: activity?.name ?? a.activityName ?? a.activityId,
        plannedUnits: a.plannedUnits,
        actualUnits: a.actualUnits,
        remainingUnits: (anyA.remainingUnits as number) ?? 0,
        rateType: (anyA.rateType as string) ?? "STANDARD",
        plannedCost: (anyA.plannedCost as number) ?? 0,
        actualCost: (anyA.actualCost as number) ?? 0,
        staffed: a.staffed ?? a.resourceId != null,
      };
    });
  }, [assignments, pool, activities, projectId]);

  const columns: ColumnDef<ResourceAssignmentRow>[] = [
    { key: "resourceName", label: "Resource Name", sortable: true },
    { key: "roleName", label: "Role", sortable: true, render: (value: unknown) => (value as string | null) ?? "—" },
    { key: "activityName", label: "Activity Name", sortable: true },
    {
      key: "staffed",
      label: "Status",
      sortable: true,
      render: (value) =>
        value ? (
          <span className="inline-flex items-center rounded-md bg-green-50 px-2 py-1 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20">
            Staffed
          </span>
        ) : (
          <span className="inline-flex items-center rounded-md bg-amber-50 px-2 py-1 text-xs font-medium text-amber-700 ring-1 ring-inset ring-amber-600/20">
            Role-only
          </span>
        ),
    },
    {
      key: "plannedUnits",
      label: "Planned Units",
      sortable: true,
      render: (value) => Number(value).toFixed(2),
    },
    {
      key: "actualUnits",
      label: "Actual Units",
      sortable: true,
      render: (value) => Number(value).toFixed(2),
    },
    {
      key: "remainingUnits",
      label: "Remaining Units",
      sortable: true,
      render: (value) => Number(value).toFixed(2),
    },
    { key: "rateType", label: "Rate Type", sortable: true },
    {
      key: "plannedCost",
      label: "Planned Cost",
      sortable: true,
      render: (value) => formatDefaultCurrency(Number(value)),
    },
    {
      key: "actualCost",
      label: "Actual Cost",
      sortable: true,
      render: (value) => formatDefaultCurrency(Number(value)),
    },
  ];

  const handleRowClick = (row: ResourceAssignmentRow | AssignmentRow) => {
    const r = row as ResourceAssignmentRow;
    setSelectedAssignment(
      selectedAssignment?.id === r.id
        ? null
        : { id: r.id, resourceName: r.resourceName, activityName: r.activityName }
    );
  };

  const resourceTypeInfos = useMemo(
    () =>
      pool.map((p) => ({
        id: p.resourceId,
        resourceTypeCode: p.resourceTypeName ?? "",
      })),
    [pool]
  );

  const canSubmit =
    formData.activityId &&
    formData.plannedUnits &&
    (assignMode === "RESOURCE" ? formData.resourceId : formData.roleId);

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3 flex-wrap">
        <button
          onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          <Plus size={16} />
          Assign Resource
        </button>
        <button
          onClick={() => setShowLeveling(true)}
          className="inline-flex items-center gap-2 rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-primary hover:bg-surface-active"
        >
          <SlidersHorizontal size={16} />
          Level Resources
        </button>
        <button
          onClick={() => recomputeCostsMutation.mutate()}
          disabled={recomputeCostsMutation.isPending}
          className="inline-flex items-center gap-2 rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-primary hover:bg-surface-active disabled:opacity-50"
          title="Recalculate planned cost for every assignment in this project from current resource rates"
        >
          {recomputeCostsMutation.isPending ? "Recomputing…" : "Recompute Costs"}
        </button>
        <div className="ml-auto">
          <ViewModeToggle viewMode={viewMode} onChange={setViewMode} />
        </div>
      </div>

      {showForm && (
        <div className="rounded-lg border border-border bg-surface/50 p-6 shadow-sm">
          <h3 className="mb-4 text-lg font-semibold text-text-primary">Assign Resource to Activity</h3>

          <div className="mb-4 flex items-center gap-2">
            <button
              type="button"
              onClick={() => setAssignMode("RESOURCE")}
              className={`rounded-md px-3 py-1.5 text-sm font-medium ${
                assignMode === "RESOURCE"
                  ? "bg-accent text-text-primary"
                  : "border border-border text-text-secondary hover:bg-surface-hover"
              }`}
            >
              Resource
            </button>
            <button
              type="button"
              onClick={() => setAssignMode("ROLE")}
              className={`rounded-md px-3 py-1.5 text-sm font-medium ${
                assignMode === "ROLE"
                  ? "bg-accent text-text-primary"
                  : "border border-border text-text-secondary hover:bg-surface-hover"
              }`}
            >
              Plan with Role
            </button>
          </div>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-text-secondary">Activity</label>
              <SearchableSelect
                value={formData.activityId}
                onChange={(val) =>
                  setFormData({ ...formData, activityId: val })
                }
                placeholder="Search activities..."
                options={activities.map((activity) => ({
                  value: activity.id,
                  label: `${activity.code} - ${activity.name}`,
                }))}
              />
            </div>

            {assignMode === "RESOURCE" ? (
              <div>
                <label className="block text-sm font-medium text-text-secondary">Resource</label>
                <SearchableSelect
                  value={formData.resourceId}
                  onChange={(val) =>
                    setFormData({ ...formData, resourceId: val })
                  }
                  placeholder="Search pooled resources..."
                  options={pool.map((p) => ({
                    value: p.resourceId,
                    label: `${p.resourceCode ?? p.resourceId} - ${p.resourceName ?? "Unknown"}`,
                  }))}
                />
                {pool.length === 0 && (
                  <p className="mt-1 text-xs text-amber-600">
                    No resources in pool. Add resources to the project pool first.
                  </p>
                )}
              </div>
            ) : (
              <div>
                <label className="block text-sm font-medium text-text-secondary">Role</label>
                <SearchableSelect
                  value={formData.roleId}
                  onChange={(val) =>
                    setFormData({ ...formData, roleId: val })
                  }
                  placeholder="Search roles..."
                  options={roles.map((role) => ({
                    value: role.id,
                    label: `${role.code} - ${role.name}`,
                  }))}
                />
              </div>
            )}

            <div>
              <label className="block text-sm font-medium text-text-secondary">Planned Units</label>
              <input
                type="number"
                value={formData.plannedUnits}
                onChange={(e) =>
                  setFormData({ ...formData, plannedUnits: e.target.value })
                }
                step="0.01"
                className="mt-1 block w-full rounded-md border-border px-3 py-2 border bg-surface/50 text-text-primary shadow-sm focus:border-accent focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary">Rate Type</label>
              <select
                value={formData.rateType}
                onChange={(e) =>
                  setFormData({ ...formData, rateType: e.target.value })
                }
                className="mt-1 block w-full rounded-md border-border px-3 py-2 border bg-surface/50 text-text-primary shadow-sm focus:border-accent focus:outline-none"
              >
                <option value="STANDARD">Standard</option>
                <option value="OVERTIME">Overtime</option>
                <option value="CPWD_SOR">CPWD SOR</option>
              </select>
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => assignMutation.mutate()}
                disabled={assignMutation.isPending || !canSubmit}
                className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-surface-active"
              >
                {assignMutation.isPending ? "Assigning..." : "Assign"}
              </button>
              <button
                onClick={() => setShowForm(false)}
                className="rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="rounded-lg border border-border bg-surface/50 p-6 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-text-primary">Resource Assignments</h3>
        </div>
        {isLoadingAssignments ? (
          <div className="text-center text-text-secondary">Loading assignments...</div>
        ) : assignmentRows.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border py-12 text-center">
            <h3 className="text-lg font-medium text-text-primary">No Assignments</h3>
            <p className="mt-2 text-text-secondary">No resource assignments yet. Create one to get started.</p>
          </div>
        ) : viewMode === "flat" ? (
          <DataTable
            columns={columns}
            data={assignmentRows}
            rowKey="id"
            searchable
            searchPlaceholder="Search resources..."
            onRowClick={handleRowClick}
          />
        ) : (
          <ResourceAssignmentTree
            assignments={assignmentRows}
            viewMode={viewMode}
            resources={resourceTypeInfos}
            onRowClick={handleRowClick}
            selectedId={selectedAssignment?.id ?? null}
          />
        )}
      </div>

      {selectedAssignment && (
        <div className="space-y-2">
          <div className="text-sm text-text-secondary">
            Custom fields for: <span className="font-medium text-accent">{selectedAssignment.resourceName} &rarr; {selectedAssignment.activityName}</span>
          </div>
          <UdfSection entityId={selectedAssignment.id} subject="RESOURCE_ASSIGNMENT" projectId={projectId} />
        </div>
      )}

      <div className="rounded-lg border border-border bg-surface/50 p-6 shadow-sm">
        <h3 className="mb-4 text-lg font-semibold text-text-primary">Resource Histogram</h3>
        <div className="mb-4">
          <label className="block text-sm font-medium text-text-secondary mb-2">Select Resource</label>
          <SearchableSelect
            value={selectedResourceId}
            onChange={(val) => setSelectedResourceId(val)}
            placeholder="Search pooled resources..."
            options={pool.map((p) => ({
              value: p.resourceId,
              label: `${p.resourceCode ?? p.resourceId} - ${p.resourceName ?? "Unknown"}`,
            }))}
          />
        </div>

        {!selectedResourceId ? (
          <div className="rounded-lg border border-dashed border-border py-12 text-center">
            <p className="text-text-secondary">Select a resource to view planned vs actual usage over time.</p>
          </div>
        ) : isLoadingHistogram ? (
          <div className="text-center text-text-secondary">Loading histogram data...</div>
        ) : histogramEntries.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border py-12 text-center">
            <p className="text-text-secondary">No histogram data available for this resource.</p>
          </div>
        ) : (
          <div className="w-full h-96">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={histogramEntries}
                margin={{ top: 20, right: 30, left: 0, bottom: 20 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                <XAxis
                  dataKey="period"
                  label={{ value: "Time Period", position: "insideBottom", offset: -10 }}
                />
                <YAxis
                  label={{ value: "Units", angle: -90, position: "insideLeft" }}
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: "#1e293b",
                    border: "1px solid #475569",
                    borderRadius: "0.375rem",
                    color: "#e2e8f0",
                  }}
                  formatter={(value) => (typeof value === "number" ? value.toFixed(2) : value)}
                />
                <Legend wrapperStyle={{ paddingTop: "16px" }} />
                <Bar dataKey="planned" fill="#3b82f6" name="Planned" />
                <Bar dataKey="actual" fill="#10b981" name="Actual" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>
      <ResourceLevelingDialog
        projectId={projectId}
        open={showLeveling}
        onClose={() => setShowLeveling(false)}
      />
    </div>
  );
}
