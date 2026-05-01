"use client";

import { useState, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  projectResourceApi,
  type ProjectResourceResponse,
  type PoolEntryInput,
} from "@/lib/api/projectResourceApi";
import { type ResourceResponse } from "@/lib/api/resourceApi";
import { resourceTypeApi } from "@/lib/api/resourceTypeApi";
import { resourceRoleApi } from "@/lib/api/resourceRoleApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { Plus, Trash2, X } from "lucide-react";
import { formatDefaultCurrency } from "@/lib/hooks/useCurrency";

export function ProjectResourcePool({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const [showPicker, setShowPicker] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [searchQuery, setSearchQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValues, setEditValues] = useState<{
    rateOverride: string;
    availabilityOverride: string;
    customUnit: string;
    notes: string;
  }>({ rateOverride: "", availabilityOverride: "", customUnit: "", notes: "" });
  const [confirmDelete, setConfirmDelete] = useState<ProjectResourceResponse | null>(null);

  const { data: poolData, isLoading: isLoadingPool } = useQuery({
    queryKey: ["resource-pool", projectId],
    queryFn: () => projectResourceApi.listPool(projectId),
  });

  const { data: availableData, isLoading: isLoadingAvailable } = useQuery({
    queryKey: ["resource-pool-available", projectId, typeFilter, roleFilter, searchQuery],
    queryFn: () =>
      projectResourceApi.listAvailable(projectId, {
        typeCode: typeFilter || undefined,
        roleId: roleFilter || undefined,
        q: searchQuery || undefined,
      }),
    enabled: showPicker,
  });

  const { data: typesData } = useQuery({
    queryKey: ["resource-types"],
    queryFn: () => resourceTypeApi.list(),
    enabled: showPicker,
  });

  const { data: rolesData } = useQuery({
    queryKey: ["resource-roles"],
    queryFn: () => resourceRoleApi.list(),
    enabled: showPicker,
  });

  const addMutation = useMutation({
    mutationFn: (entries: PoolEntryInput[]) =>
      projectResourceApi.addToPool(projectId, entries),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resource-pool", projectId] });
      queryClient.invalidateQueries({ queryKey: ["resource-pool-available", projectId] });
      setSelectedIds(new Set());
      setShowPicker(false);
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, patch }: { id: string; patch: { rateOverride?: number; availabilityOverride?: number; customUnit?: string; notes?: string } }) =>
      projectResourceApi.updatePoolEntry(projectId, id, patch),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resource-pool", projectId] });
      setEditingId(null);
    },
  });

  const removeMutation = useMutation({
    mutationFn: (id: string) => projectResourceApi.removeFromPool(projectId, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["resource-pool", projectId] });
      queryClient.invalidateQueries({ queryKey: ["resource-pool-available", projectId] });
      setConfirmDelete(null);
    },
    onError: (error: unknown) => {
      const err = error as { response?: { data?: { error?: { message?: string } } } };
      const msg = err?.response?.data?.error?.message ?? "Failed to remove resource from pool";
      setConfirmDelete(null);
      alert(msg);
    },
  });

  const pool = useMemo<ProjectResourceResponse[]>(() => {
    const raw = poolData?.data as unknown;
    return Array.isArray(raw) ? (raw as ProjectResourceResponse[]) : [];
  }, [poolData]);

  const available = useMemo<ResourceResponse[]>(() => {
    const raw = availableData?.data as unknown;
    return Array.isArray(raw) ? (raw as ResourceResponse[]) : [];
  }, [availableData]);

  const types = useMemo(() => {
    const raw = typesData?.data as unknown;
    return Array.isArray(raw) ? (raw as { id: string; code: string; name: string }[]) : [];
  }, [typesData]);

  const roles = useMemo(() => {
    const raw = rolesData?.data as unknown;
    return Array.isArray(raw) ? (raw as { id: string; code: string; name: string }[]) : [];
  }, [rolesData]);

  const toggleSelection = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleAdd = () => {
    const entries: PoolEntryInput[] = Array.from(selectedIds).map((resourceId) => ({
      resourceId,
    }));
    addMutation.mutate(entries);
  };

  const startEdit = (entry: ProjectResourceResponse) => {
    setEditingId(entry.id);
    setEditValues({
      rateOverride: entry.rateOverride?.toString() ?? "",
      availabilityOverride: entry.availabilityOverride?.toString() ?? "",
      customUnit: entry.customUnit ?? "",
      notes: entry.notes ?? "",
    });
  };

  const saveEdit = (id: string) => {
    updateMutation.mutate({
      id,
      patch: {
        rateOverride: editValues.rateOverride ? parseFloat(editValues.rateOverride) : undefined,
        availabilityOverride: editValues.availabilityOverride ? parseFloat(editValues.availabilityOverride) : undefined,
        customUnit: editValues.customUnit || undefined,
        notes: editValues.notes || undefined,
      },
    });
  };

  const poolColumns: ColumnDef<ProjectResourceResponse>[] = [
    { key: "resourceCode", label: "Code", sortable: true },
    { key: "resourceName", label: "Name", sortable: true },
    { key: "resourceTypeName", label: "Type", sortable: true },
    { key: "roleName", label: "Role", sortable: true, render: (v) => (v as string) ?? "—" },
    {
      key: "masterRate",
      label: "Master Rate",
      sortable: true,
      render: (v) => (v != null ? formatDefaultCurrency(Number(v)) : "—"),
    },
    {
      key: "rateOverride",
      label: "Override Rate",
      render: (value, row) => {
        const r = row as ProjectResourceResponse;
        if (editingId === r.id) {
          return (
            <input
              type="number"
              value={editValues.rateOverride}
              onChange={(e) => setEditValues({ ...editValues, rateOverride: e.target.value })}
              className="w-24 rounded border border-border bg-surface/50 px-2 py-1 text-sm text-text-primary"
              step="0.01"
              placeholder="—"
            />
          );
        }
        return value != null ? formatDefaultCurrency(Number(value)) : "—";
      },
    },
    {
      key: "availabilityOverride",
      label: "Override Avail.",
      render: (value, row) => {
        const r = row as ProjectResourceResponse;
        if (editingId === r.id) {
          return (
            <input
              type="number"
              value={editValues.availabilityOverride}
              onChange={(e) => setEditValues({ ...editValues, availabilityOverride: e.target.value })}
              className="w-20 rounded border border-border bg-surface/50 px-2 py-1 text-sm text-text-primary"
              step="0.01"
              placeholder="—"
            />
          );
        }
        return value != null ? `${Number(value)}%` : "—";
      },
    },
    {
      key: "notes",
      label: "Notes",
      render: (value, row) => {
        const r = row as ProjectResourceResponse;
        if (editingId === r.id) {
          return (
            <input
              type="text"
              value={editValues.notes}
              onChange={(e) => setEditValues({ ...editValues, notes: e.target.value })}
              className="w-32 rounded border border-border bg-surface/50 px-2 py-1 text-sm text-text-primary"
              placeholder="Notes"
            />
          );
        }
        return (value as string) ?? "—";
      },
    },
    {
      key: "actions",
      label: "Actions",
      render: (_value, row) => {
        const r = row as ProjectResourceResponse;
        if (editingId === r.id) {
          return (
            <div className="flex gap-1">
              <button
                onClick={() => saveEdit(r.id)}
                disabled={updateMutation.isPending}
                className="rounded bg-accent px-2 py-1 text-xs font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                Save
              </button>
              <button
                onClick={() => setEditingId(null)}
                className="rounded border border-border px-2 py-1 text-xs text-text-secondary hover:bg-surface-hover"
              >
                Cancel
              </button>
            </div>
          );
        }
        return (
          <div className="flex gap-1">
            <button
              onClick={() => startEdit(r)}
              className="rounded border border-border px-2 py-1 text-xs text-text-secondary hover:bg-surface-hover"
            >
              Edit
            </button>
            <button
              onClick={() => setConfirmDelete(r)}
              className="rounded border border-red-300 px-2 py-1 text-xs text-red-600 hover:bg-red-50"
            >
              <Trash2 size={12} />
            </button>
          </div>
        );
      },
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold text-text-primary">Project Resource Pool</h3>
          <p className="text-sm text-text-secondary">
            {pool.length} resource{pool.length !== 1 ? "s" : ""} in pool
          </p>
        </div>
        <button
          onClick={() => setShowPicker(!showPicker)}
          className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          <Plus size={16} />
          Add Resources
        </button>
      </div>

      {showPicker && (
        <div className="rounded-lg border border-border bg-surface/50 p-6 shadow-sm">
          <div className="flex items-center justify-between mb-4">
            <h4 className="text-md font-semibold text-text-primary">Select from Master Data</h4>
            <button onClick={() => setShowPicker(false)} className="text-text-secondary hover:text-text-primary">
              <X size={18} />
            </button>
          </div>

          <div className="mb-4 flex flex-wrap gap-3">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search by name or code..."
              className="rounded-md border border-border bg-surface/50 px-3 py-2 text-sm text-text-primary placeholder-text-secondary focus:border-accent focus:outline-none"
            />
            <SearchableSelect
              value={typeFilter}
              onChange={setTypeFilter}
              placeholder="All types"
              options={[
                { value: "", label: "All types" },
                ...types.map((t) => ({ value: t.code, label: t.name })),
              ]}
            />
            <SearchableSelect
              value={roleFilter}
              onChange={setRoleFilter}
              placeholder="All roles"
              options={[
                { value: "", label: "All roles" },
                ...roles.map((r) => ({ value: r.id, label: r.name })),
              ]}
            />
          </div>

          {isLoadingAvailable ? (
            <div className="text-center text-text-secondary py-8">Loading available resources...</div>
          ) : available.length === 0 ? (
            <div className="text-center text-text-secondary py-8">
              No available resources found. All active resources may already be in the pool.
            </div>
          ) : (
            <div className="max-h-64 overflow-y-auto space-y-1">
              {available.map((r) => (
                <label
                  key={r.id}
                  className="flex items-center gap-3 rounded-md px-3 py-2 hover:bg-surface-hover cursor-pointer"
                >
                  <input
                    type="checkbox"
                    checked={selectedIds.has(r.id)}
                    onChange={() => toggleSelection(r.id)}
                    className="h-4 w-4 rounded border-border text-accent focus:ring-accent"
                  />
                  <span className="text-sm font-medium text-text-primary">
                    {r.code} - {r.name}
                  </span>
                  <span className="text-xs text-text-secondary">
                    {r.resourceTypeName} &middot; {r.roleName}
                  </span>
                  {r.costPerUnit != null && (
                    <span className="ml-auto text-xs text-text-secondary">
                      {formatDefaultCurrency(r.costPerUnit)}
                    </span>
                  )}
                </label>
              ))}
            </div>
          )}

          <div className="mt-4 flex items-center gap-3">
            <button
              onClick={handleAdd}
              disabled={selectedIds.size === 0 || addMutation.isPending}
              className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
            >
              {addMutation.isPending
                ? "Adding..."
                : `Add to project (${selectedIds.size})`}
            </button>
            <span className="text-sm text-text-secondary">
              {selectedIds.size} selected
            </span>
          </div>
        </div>
      )}

      <div className="rounded-lg border border-border bg-surface/50 p-6 shadow-sm">
        {isLoadingPool ? (
          <div className="text-center text-text-secondary">Loading pool...</div>
        ) : pool.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border py-12 text-center">
            <h3 className="text-lg font-medium text-text-primary">No Resources in Pool</h3>
            <p className="mt-2 text-text-secondary">
              Add resources from master data to set up your project pool.
            </p>
          </div>
        ) : (
          <DataTable
            columns={poolColumns}
            data={pool}
            rowKey="id"
            searchable
            searchPlaceholder="Search pool..."
          />
        )}
      </div>

      {confirmDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="rounded-lg bg-surface p-6 shadow-xl max-w-md w-full mx-4">
            <h3 className="text-lg font-semibold text-text-primary mb-2">Remove from Pool</h3>
            <p className="text-sm text-text-secondary mb-4">
              Remove <span className="font-medium text-text-primary">{confirmDelete.resourceName}</span> from
              this project&apos;s resource pool? This will fail if the resource has active assignments.
            </p>
            <div className="flex gap-3 justify-end">
              <button
                onClick={() => setConfirmDelete(null)}
                className="rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover"
              >
                Cancel
              </button>
              <button
                onClick={() => removeMutation.mutate(confirmDelete.id)}
                disabled={removeMutation.isPending}
                className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
              >
                {removeMutation.isPending ? "Removing..." : "Remove"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
