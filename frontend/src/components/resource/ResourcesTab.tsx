"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";
import { resourceApi } from "@/lib/api/resourceApi";
import { resourceHistogramApi } from "@/lib/api/resourceHistogramApi";
import { activityApi } from "@/lib/api/activityApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { Plus, SlidersHorizontal } from "lucide-react";
import { ResourceLevelingDialog } from "./ResourceLevelingDialog";

interface ResourceAssignmentRow {
  id: string;
  activityId: string;
  resourceId: string;
  projectId: string;
  resourceName: string;
  activityName: string;
  plannedUnits: number;
  actualUnits: number;
  remainingUnits: number;
  rateType: string;
  plannedCost: number;
  actualCost: number;
}

export function ResourcesTab({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState({
    activityId: "",
    resourceId: "",
    plannedUnits: "",
    rateType: "FIXED",
  });
  const [selectedResourceId, setSelectedResourceId] = useState<string>("");
  const [showLeveling, setShowLeveling] = useState(false);

  const { data: assignmentsData, isLoading: isLoadingAssignments } = useQuery({
    queryKey: ["resource-assignments", projectId],
    queryFn: () => resourceApi.getProjectResourceAssignments(projectId, 0, 100),
  });

  const { data: resourcesData, isLoading: isLoadingResources } = useQuery({
    queryKey: ["resources"],
    queryFn: () => resourceApi.listResources(0, 100),
  });

  const { data: activitiesData, isLoading: isLoadingActivities } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 100),
  });

  const { data: histogramData, isLoading: isLoadingHistogram } = useQuery({
    queryKey: ["resource-histogram", projectId, selectedResourceId],
    queryFn: () =>
      selectedResourceId
        ? resourceHistogramApi.getHistogram(projectId, selectedResourceId)
        : Promise.resolve({ data: [], success: true } as any),
    enabled: !!selectedResourceId,
  });

  const assignMutation = useMutation({
    mutationFn: () =>
      resourceApi.createProjectResourceAssignment(projectId, {
        activityId: formData.activityId,
        resourceId: formData.resourceId,
        plannedUnits: parseFloat(formData.plannedUnits),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resource-assignments", projectId] });
      setShowForm(false);
      setFormData({
        activityId: "",
        resourceId: "",
        plannedUnits: "",
        rateType: "FIXED",
      });
    },
  });

  const rawAssignments = assignmentsData?.data;
  const assignments = Array.isArray(rawAssignments) ? rawAssignments : rawAssignments?.content ?? [];
  const rawResources = resourcesData?.data;
  const resources = Array.isArray(rawResources) ? rawResources : rawResources?.content ?? [];
  const rawActivities = activitiesData?.data;
  const activities = Array.isArray(rawActivities) ? rawActivities : rawActivities?.content ?? [];
  const histogramEntries = histogramData?.data ?? [];

  const columns: ColumnDef<ResourceAssignmentRow>[] = [
    { key: "resourceName", label: "Resource Name", sortable: true },
    { key: "activityName", label: "Activity Name", sortable: true },
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
      render: (value) => `$${Number(value).toFixed(2)}`,
    },
    {
      key: "actualCost",
      label: "Actual Cost",
      sortable: true,
      render: (value) => `$${Number(value).toFixed(2)}`,
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <button
          onClick={() => setShowForm(!showForm)}
          className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
        >
          <Plus size={16} />
          Assign Resource
        </button>
        <button
          onClick={() => setShowLeveling(true)}
          className="inline-flex items-center gap-2 rounded-md border border-slate-700 bg-slate-800 px-4 py-2 text-sm font-medium text-slate-200 hover:bg-slate-700"
        >
          <SlidersHorizontal size={16} />
          Level Resources
        </button>
      </div>

      {showForm && (
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 shadow-sm">
          <h3 className="mb-4 text-lg font-semibold text-white">Assign Resource to Activity</h3>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-300">Activity</label>
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

            <div>
              <label className="block text-sm font-medium text-slate-300">Resource</label>
              <SearchableSelect
                value={formData.resourceId}
                onChange={(val) =>
                  setFormData({ ...formData, resourceId: val })
                }
                placeholder="Search resources..."
                options={resources.map((resource) => ({
                  value: resource.id,
                  label: `${resource.code} - ${resource.name}`,
                }))}
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Planned Units</label>
              <input
                type="number"
                value={formData.plannedUnits}
                onChange={(e) =>
                  setFormData({ ...formData, plannedUnits: e.target.value })
                }
                step="0.01"
                className="mt-1 block w-full rounded-md border-slate-700 px-3 py-2 border bg-slate-900/50 text-white shadow-sm focus:border-blue-500 focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Rate Type</label>
              <select
                value={formData.rateType}
                onChange={(e) =>
                  setFormData({ ...formData, rateType: e.target.value })
                }
                className="mt-1 block w-full rounded-md border-slate-700 px-3 py-2 border bg-slate-900/50 text-white shadow-sm focus:border-blue-500 focus:outline-none"
              >
                <option value="FIXED">Fixed</option>
                <option value="HOURLY">Hourly</option>
                <option value="DAILY">Daily</option>
              </select>
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => assignMutation.mutate()}
                disabled={
                  assignMutation.isPending ||
                  !formData.activityId ||
                  !formData.resourceId ||
                  !formData.plannedUnits
                }
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-700"
              >
                {assignMutation.isPending ? "Assigning..." : "Assign"}
              </button>
              <button
                onClick={() => setShowForm(false)}
                className="rounded-md border border-slate-700 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800/50"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 shadow-sm">
        <h3 className="mb-4 text-lg font-semibold text-white">Resource Assignments</h3>
        {isLoadingAssignments ? (
          <div className="text-center text-slate-400">Loading assignments...</div>
        ) : assignments.length === 0 ? (
          <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
            <h3 className="text-lg font-medium text-white">No Assignments</h3>
            <p className="mt-2 text-slate-400">No resource assignments yet. Create one to get started.</p>
          </div>
        ) : (
          <DataTable columns={columns} data={assignments as unknown as ResourceAssignmentRow[]} rowKey="id" />
        )}
      </div>

      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 shadow-sm">
        <h3 className="mb-4 text-lg font-semibold text-white">Resource Histogram</h3>
        <div className="mb-4">
          <label className="block text-sm font-medium text-slate-300 mb-2">Select Resource</label>
          <SearchableSelect
            value={selectedResourceId}
            onChange={(val) => setSelectedResourceId(val)}
            placeholder="Search resources..."
            options={resources.map((resource) => ({
              value: resource.id,
              label: `${resource.code} - ${resource.name}`,
            }))}
          />
        </div>

        {!selectedResourceId ? (
          <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
            <p className="text-slate-400">Select a resource to view planned vs actual usage over time.</p>
          </div>
        ) : isLoadingHistogram ? (
          <div className="text-center text-slate-400">Loading histogram data...</div>
        ) : histogramEntries.length === 0 ? (
          <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
            <p className="text-slate-400">No histogram data available for this resource.</p>
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
