"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { resourceApi } from "@/lib/api/resourceApi";
import { activityApi } from "@/lib/api/activityApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { Plus } from "lucide-react";

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

  const assignMutation = useMutation({
    mutationFn: () =>
      resourceApi.createProjectResourceAssignment(projectId, {
        activityId: formData.activityId,
        resourceId: formData.resourceId,
        plannedUnits: parseFloat(formData.plannedUnits),
        rateType: formData.rateType,
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

  const assignments = assignmentsData?.data?.content ?? [];
  const resources = resourcesData?.data?.content ?? [];
  const activities = activitiesData?.data?.content ?? [];

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
    <div className="space-y-4">
      <button
        onClick={() => setShowForm(!showForm)}
        className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
      >
        <Plus size={16} />
        Assign Resource
      </button>

      {showForm && (
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <h3 className="mb-4 text-lg font-semibold text-gray-900">Assign Resource to Activity</h3>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700">Activity</label>
              <select
                value={formData.activityId}
                onChange={(e) =>
                  setFormData({ ...formData, activityId: e.target.value })
                }
                className="mt-1 block w-full rounded-md border-gray-300 px-3 py-2 border shadow-sm focus:border-blue-500 focus:outline-none"
              >
                <option value="">Select an activity</option>
                {activities.map((activity) => (
                  <option key={activity.id} value={activity.id}>
                    {activity.code} - {activity.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700">Resource</label>
              <select
                value={formData.resourceId}
                onChange={(e) =>
                  setFormData({ ...formData, resourceId: e.target.value })
                }
                className="mt-1 block w-full rounded-md border-gray-300 px-3 py-2 border shadow-sm focus:border-blue-500 focus:outline-none"
              >
                <option value="">Select a resource</option>
                {resources.map((resource) => (
                  <option key={resource.id} value={resource.id}>
                    {resource.code} - {resource.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700">Planned Units</label>
              <input
                type="number"
                value={formData.plannedUnits}
                onChange={(e) =>
                  setFormData({ ...formData, plannedUnits: e.target.value })
                }
                step="0.01"
                className="mt-1 block w-full rounded-md border-gray-300 px-3 py-2 border shadow-sm focus:border-blue-500 focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700">Rate Type</label>
              <select
                value={formData.rateType}
                onChange={(e) =>
                  setFormData({ ...formData, rateType: e.target.value })
                }
                className="mt-1 block w-full rounded-md border-gray-300 px-3 py-2 border shadow-sm focus:border-blue-500 focus:outline-none"
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
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400"
              >
                {assignMutation.isPending ? "Assigning..." : "Assign"}
              </button>
              <button
                onClick={() => setShowForm(false)}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {isLoadingAssignments ? (
        <div className="text-center text-gray-500">Loading assignments...</div>
      ) : assignments.length === 0 ? (
        <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
          <h3 className="text-lg font-medium text-gray-900">No Assignments</h3>
          <p className="mt-2 text-gray-500">No resource assignments yet. Create one to get started.</p>
        </div>
      ) : (
        <DataTable columns={columns} data={assignments} rowKey="id" />
      )}
    </div>
  );
}
