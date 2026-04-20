"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2, Pencil } from "lucide-react";
import Link from "next/link";
import toast from "react-hot-toast";
import { resourceApi } from "@/lib/api/resourceApi";
import type { UpdateResourceRequest } from "@/lib/api/resourceApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { StatusBadge } from "@/components/common/StatusBadge";
import { TabTip } from "@/components/common/TabTip";
import type { ResourceResponse } from "@/lib/types";
import { notificationHelpers } from "@/lib/notificationHelpers";

export default function ResourcesPage() {
  const queryClient = useQueryClient();
  const [editingResource, setEditingResource] = useState<ResourceResponse | null>(null);
  const [editForm, setEditForm] = useState({ name: "", resourceType: "LABOR" as string, maxUnitsPerDay: 0, status: "ACTIVE" as string, hourlyRate: 0, costPerUse: 0, overtimeRate: 0 });

  const { data: resourcesData, isLoading, error } = useQuery({
    queryKey: ["resources"],
    queryFn: () => resourceApi.listResources(0, 50),
  });

  const deleteMutation = useMutation({
    mutationFn: (resourceId: string) => resourceApi.deleteResource(resourceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resources"] });
      toast.success("Resource deleted successfully");
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateResourceRequest }) =>
      resourceApi.updateResource(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resources"] });
      setEditingResource(null);
      toast.success("Resource updated successfully");
    },
    onError: (err) => notificationHelpers.handleApiError(err, "Failed to update resource"),
  });

  const handleEdit = (resource: ResourceResponse) => {
    setEditingResource(resource);
    setEditForm({
      name: resource.name,
      resourceType: resource.resourceType ?? "LABOR",
      maxUnitsPerDay: resource.maxUnitsPerDay ?? 0,
      status: resource.status ?? "ACTIVE",
      hourlyRate: resource.hourlyRate ?? 0,
      costPerUse: resource.costPerUse ?? 0,
      overtimeRate: resource.overtimeRate ?? 0,
    });
  };

  const handleSaveEdit = () => {
    if (!editingResource) return;
    updateMutation.mutate({
      id: editingResource.id,
      data: {
        code: editingResource.code,
        name: editForm.name,
        resourceType: editForm.resourceType as "LABOR" | "NONLABOR" | "MATERIAL",
        maxUnitsPerDay: editForm.maxUnitsPerDay,
        status: editForm.status,
        hourlyRate: editForm.hourlyRate,
        costPerUse: editForm.costPerUse,
        overtimeRate: editForm.overtimeRate,
      },
    });
  };

  const rawData = resourcesData?.data;
  const resources = Array.isArray(rawData) ? rawData : (rawData as any)?.content ?? [];

  const columns: ColumnDef<ResourceResponse>[] = [
    { key: "code", label: "Code", sortable: true },
    { key: "name", label: "Name", sortable: true },
    {
      key: "resourceType",
      label: "Type",
      sortable: true,
      render: (value) => <span className="text-sm font-medium">{String(value)}</span>,
    },
    {
      key: "status",
      label: "Status",
      render: (value) => <StatusBadge status={String(value)} />,
    },
    { key: "maxUnitsPerDay", label: "Max Units/Day", sortable: true },
    {
      key: "id",
      label: "Actions",
      render: (_value, row) => (
        <div className="flex items-center gap-2">
          <button
            onClick={() => handleEdit(row)}
            className="text-slate-400 hover:text-blue-400"
            title="Edit resource"
          >
            <Pencil size={16} />
          </button>
          <button
            onClick={() => {
              if (window.confirm("Are you sure you want to delete this resource?")) {
                deleteMutation.mutate(String(row.id));
              }
            }}
            disabled={deleteMutation.isPending}
            className="text-slate-400 hover:text-red-400 disabled:text-slate-500"
            title="Delete resource"
          >
            <Trash2 size={16} />
          </button>
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Resources"
        description="Manage labor, nonlabor, and material resources"
        actions={
          <Link
            href="/resources/new"
            className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
          >
            <Plus size={16} />
            New Resource
          </Link>
        }
      />

      <TabTip
        title="Global Resource Pool"
        description="Define all available resources (people, equipment, materials) that can be assigned to projects. Set resource types, units, and rates here."
      />

      {isLoading && (
        <div className="py-12 text-center text-slate-500">Loading resources...</div>
      )}

      {error && (
        <div className="rounded-md bg-red-500/10 p-4 text-sm text-red-400">
          Failed to load resources. Is the backend running?
        </div>
      )}

      {!isLoading && resources.length === 0 && (
        <EmptyState
          title="No resources yet"
          description="Create your first resource to get started with resource management."
        />
      )}

      {resources.length > 0 && <DataTable columns={columns} data={resources} rowKey="id" searchable searchPlaceholder="Search resources..." />}

      {/* Edit Resource Modal */}
      {editingResource && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
          <div className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 p-6 shadow-xl">
            <h2 className="mb-4 text-lg font-semibold text-white">
              Edit Resource: {editingResource.code}
            </h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-300">Name</label>
                <input
                  type="text"
                  value={editForm.name}
                  onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-300">Status</label>
                  <select
                    value={editForm.status}
                    onChange={(e) => setEditForm({ ...editForm, status: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  >
                    <option value="ACTIVE">Active</option>
                    <option value="INACTIVE">Inactive</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-300">Resource Type</label>
                  <select
                    value={editForm.resourceType}
                    onChange={(e) => setEditForm({ ...editForm, resourceType: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  >
                    <option value="LABOR">Labor</option>
                    <option value="NONLABOR">Nonlabor</option>
                    <option value="MATERIAL">Material</option>
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-300">Max Units/Day</label>
                  <input
                    type="number"
                    value={editForm.maxUnitsPerDay}
                    onChange={(e) => setEditForm({ ...editForm, maxUnitsPerDay: parseFloat(e.target.value) || 0 })}
                    min="0"
                    step="0.1"
                    className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-300">Hourly Rate</label>
                  <input
                    type="number"
                    value={editForm.hourlyRate}
                    onChange={(e) => setEditForm({ ...editForm, hourlyRate: parseFloat(e.target.value) || 0 })}
                    min="0"
                    step="0.01"
                    className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-300">Cost Per Use</label>
                  <input
                    type="number"
                    value={editForm.costPerUse}
                    onChange={(e) => setEditForm({ ...editForm, costPerUse: parseFloat(e.target.value) || 0 })}
                    min="0"
                    step="0.01"
                    className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-300">Overtime Rate</label>
                  <input
                    type="number"
                    value={editForm.overtimeRate}
                    onChange={(e) => setEditForm({ ...editForm, overtimeRate: parseFloat(e.target.value) || 0 })}
                    min="0"
                    step="0.01"
                    className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  onClick={() => setEditingResource(null)}
                  className="rounded-md border border-slate-700 bg-slate-900/50 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800/50"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSaveEdit}
                  disabled={updateMutation.isPending}
                  className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:opacity-50"
                >
                  {updateMutation.isPending ? "Saving..." : "Save Changes"}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
