"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2, Pencil } from "lucide-react";
import Link from "next/link";
import toast from "react-hot-toast";
import { resourceApi } from "@/lib/api/resourceApi";
import type { UpdateResourceRequest } from "@/lib/api/resourceApi";
import { resourceTypeApi, BASE_CATEGORY_LABEL } from "@/lib/api/resourceTypeApi";
import { calendarApi } from "@/lib/api/calendarApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { StatusBadge } from "@/components/common/StatusBadge";
import { TabTip } from "@/components/common/TabTip";
import type { ResourceResponse } from "@/lib/types";
import { notificationHelpers } from "@/lib/notificationHelpers";
import { SecretField } from "@/components/auth/SecretField";

const FINANCE_ROLES = ["ROLE_FINANCE", "ROLE_PMO", "ROLE_ADMIN"] as const;

export default function ResourcesPage() {
  const queryClient = useQueryClient();
  const [editingResource, setEditingResource] = useState<ResourceResponse | null>(null);
  const [editForm, setEditForm] = useState({ name: "", resourceTypeDefId: "", maxUnitsPerDay: 0, status: "ACTIVE" as string, hourlyRate: 0, costPerUse: 0, overtimeRate: 0, calendarId: "" });

  const { data: resourcesData, isLoading, error } = useQuery({
    queryKey: ["resources"],
    queryFn: () => resourceApi.listResources(0, 50),
  });

  const { data: resourceTypesData } = useQuery({
    queryKey: ["resource-types", "active"],
    queryFn: () => resourceTypeApi.list({ active: true }),
  });
  const allTypeDefs = resourceTypesData?.data ?? [];

  const { data: calendarsData, isLoading: calendarsLoading } = useQuery({
    queryKey: ["calendars", "all"],
    queryFn: () => calendarApi.listCalendars(),
  });
  const resourceCalendars = calendarsData?.data ?? [];

  const deleteMutation = useMutation({
    mutationFn: (resourceId: string) => resourceApi.deleteResource(resourceId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resources"] });
      toast.success("Resource deleted successfully");
    },
  });

  const deleteAllMutation = useMutation({
    mutationFn: () => resourceApi.deleteAllResources(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resources"] });
      toast.success("All resources deleted successfully");
    },
    onError: (err) => notificationHelpers.handleApiError(err, "Failed to delete all resources"),
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
      resourceTypeDefId: resource.resourceTypeDefId ?? "",
      maxUnitsPerDay: resource.maxUnitsPerDay ?? 0,
      status: resource.status ?? "ACTIVE",
      hourlyRate: resource.hourlyRate ?? 0,
      costPerUse: resource.costPerUse ?? 0,
      overtimeRate: resource.overtimeRate ?? 0,
      calendarId: resource.calendarId ?? "",
    });
  };

  const handleSaveEdit = () => {
    if (!editingResource) return;
    updateMutation.mutate({
      id: editingResource.id,
      data: {
        code: editingResource.code,
        name: editForm.name,
        resourceTypeDefId: editForm.resourceTypeDefId || undefined,
        maxUnitsPerDay: editForm.maxUnitsPerDay,
        status: editForm.status,
        hourlyRate: editForm.hourlyRate,
        costPerUse: editForm.costPerUse,
        overtimeRate: editForm.overtimeRate,
        calendarId: editForm.calendarId || undefined,
      },
    });
  };

  const rawData = resourcesData?.data;
  const resources = Array.isArray(rawData) ? rawData : (rawData as any)?.content ?? [];

  const columns: ColumnDef<ResourceResponse>[] = [
    { key: "code", label: "Code", sortable: true },
    { key: "name", label: "Name", sortable: true },
    {
      key: "resourceTypeName",
      label: "Type",
      sortable: true,
      render: (_value, row) => (
        <span className="text-sm font-medium">
          {row.resourceTypeName ?? BASE_CATEGORY_LABEL[row.resourceType] ?? String(row.resourceType ?? "—")}
        </span>
      ),
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
            className="text-text-secondary hover:text-accent"
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
            className="text-text-secondary hover:text-danger disabled:text-text-muted"
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
          <div className="flex items-center gap-2">
            <button
              onClick={() => {
                if (window.confirm("Are you sure you want to delete ALL resources? This action cannot be undone.")) {
                  deleteAllMutation.mutate();
                }
              }}
              disabled={deleteAllMutation.isPending || resources.length === 0}
              className="inline-flex items-center gap-2 rounded-md bg-danger px-4 py-2 text-sm font-medium text-white hover:bg-danger/90 disabled:opacity-50"
            >
              <Trash2 size={16} />
              {deleteAllMutation.isPending ? "Deleting..." : "Delete All"}
            </button>
            <Link
              href="/resources/new"
              className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
            >
              <Plus size={16} />
              New Resource
            </Link>
          </div>
        }
      />

      <TabTip
        title="Global Resource Pool"
        description="Define all available resources (people, equipment, materials) that can be assigned to projects. Set resource types, units, and rates here."
      />

      {isLoading && (
        <div className="py-12 text-center text-text-muted">Loading resources...</div>
      )}

      {error && (
        <div className="rounded-md bg-danger/10 p-4 text-sm text-danger">
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
          <div className="w-full max-w-md rounded-xl border border-border bg-surface p-6 shadow-xl">
            <h2 className="mb-4 text-lg font-semibold text-text-primary">
              Edit Resource: {editingResource.code}
            </h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-text-secondary">Name</label>
                <input
                  type="text"
                  value={editForm.name}
                  onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-text-secondary">Status</label>
                  <select
                    value={editForm.status}
                    onChange={(e) => setEditForm({ ...editForm, status: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  >
                    <option value="ACTIVE">Active</option>
                    <option value="INACTIVE">Inactive</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-text-secondary">Resource Type</label>
                  <select
                    value={editForm.resourceTypeDefId}
                    onChange={(e) => setEditForm({ ...editForm, resourceTypeDefId: e.target.value })}
                    className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  >
                    {allTypeDefs.length === 0 && <option value="">Loading…</option>}
                    {allTypeDefs.map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.name} ({BASE_CATEGORY_LABEL[d.baseCategory]})
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary">Calendar</label>
                <select
                  value={editForm.calendarId}
                  onChange={(e) => setEditForm({ ...editForm, calendarId: e.target.value })}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  disabled={calendarsLoading}
                >
                  {calendarsLoading && <option value="">Loading…</option>}
                  <option value="">— none —</option>
                  {resourceCalendars.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.standardWorkHoursPerDay}h / {c.standardWorkDaysPerWeek}d)
                    </option>
                  ))}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-text-secondary">Max Units/Day</label>
                  <input
                    type="number"
                    value={editForm.maxUnitsPerDay}
                    onChange={(e) => setEditForm({ ...editForm, maxUnitsPerDay: parseFloat(e.target.value) || 0 })}
                    min="0"
                    step="0.1"
                    className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  />
                </div>
                <SecretField
                  visibleTo={FINANCE_ROLES}
                  masked={
                    <div>
                      <label className="block text-sm font-medium text-text-secondary">Hourly Rate</label>
                      <div className="mt-1 rounded-md border border-dashed border-border bg-surface-hover/40 px-3 py-2 text-sm text-text-muted">
                        Restricted (Finance / PMO only)
                      </div>
                    </div>
                  }
                >
                  <div>
                    <label className="block text-sm font-medium text-text-secondary">Hourly Rate</label>
                    <input
                      type="number"
                      value={editForm.hourlyRate}
                      onChange={(e) => setEditForm({ ...editForm, hourlyRate: parseFloat(e.target.value) || 0 })}
                      min="0"
                      step="0.01"
                      className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                    />
                  </div>
                </SecretField>
              </div>
              <SecretField visibleTo={FINANCE_ROLES} masked={null}>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-text-secondary">Cost Per Use</label>
                    <input
                      type="number"
                      value={editForm.costPerUse}
                      onChange={(e) => setEditForm({ ...editForm, costPerUse: parseFloat(e.target.value) || 0 })}
                      min="0"
                      step="0.01"
                      className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-text-secondary">Overtime Rate</label>
                    <input
                      type="number"
                      value={editForm.overtimeRate}
                      onChange={(e) => setEditForm({ ...editForm, overtimeRate: parseFloat(e.target.value) || 0 })}
                      min="0"
                      step="0.01"
                      className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                    />
                  </div>
                </div>
              </SecretField>
              <div className="flex justify-end gap-2 pt-2">
                <button
                  onClick={() => setEditingResource(null)}
                  className="rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSaveEdit}
                  disabled={updateMutation.isPending}
                  className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
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
