"use client";

import { VirtualDataTable, type ColumnDef } from "@/components/common/VirtualDataTable";

import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  manpowerRateMasterApi,
  type ManpowerRateMaster,
  type ManpowerRateMasterRequest,
} from "@/lib/api/manpowerRateMasterApi";
import { resourceRoleApi, type ResourceRole } from "@/lib/api/resourceRoleApi";
import {
  manpowerCategoryMasterApi,
  type ManpowerCategoryMaster,
} from "@/lib/api/manpowerCategoryMasterApi";
import { gradeMasterApi, type GradeMaster } from "@/lib/api/gradeMasterApi";
import { resourceTypeApi, type ResourceType } from "@/lib/api/resourceTypeApi";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { TabTip } from "@/components/common/TabTip";
import { rateUnitOptionsWithFallback } from "@/lib/constants/resourceUnits";
import { getErrorMessage } from "@/lib/utils/error";

interface DefForm {
  roleId: string;
  categoryId: string;
  gradeId: string;
  unit: string;
  rate: string;
  active: boolean;
}

const emptyForm = (): DefForm => ({
  roleId: "",
  categoryId: "",
  gradeId: "",
  unit: "Day",
  rate: "",
  active: true,
});

const formFromDef = (d: ManpowerRateMaster): DefForm => ({
  roleId: d.roleId,
  categoryId: d.categoryId,
  gradeId: d.gradeId,
  unit: d.unit,
  rate: String(d.rate),
  active: d.active,
});

const toPayload = (form: DefForm): ManpowerRateMasterRequest => ({
  roleId: form.roleId,
  categoryId: form.categoryId,
  gradeId: form.gradeId,
  unit: form.unit.trim(),
  rate: Number(form.rate),
  active: form.active,
});

export function ManpowerRateSection() {
  const queryClient = useQueryClient();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<DefForm>(emptyForm());
  const [error, setError] = useState<string | null>(null);

  const { data: rateData, isLoading, isError, error: queryError } = useQuery({
    queryKey: ["manpower-rate-master"],
    queryFn: () => manpowerRateMasterApi.list(),
  });

  const { data: roleData } = useQuery({
    queryKey: ["resource-roles"],
    queryFn: () => resourceRoleApi.list(),
  });

  const { data: categoryData } = useQuery({
    queryKey: ["manpower-categories"],
    queryFn: () => manpowerCategoryMasterApi.list(),
  });

  const { data: gradeData } = useQuery({
    queryKey: ["grade-master"],
    queryFn: () => gradeMasterApi.list(),
  });

  const { data: typeData } = useQuery({
    queryKey: ["resource-types"],
    queryFn: () => resourceTypeApi.list(),
  });

  const rates: ManpowerRateMaster[] = useMemo(() => rateData?.data ?? [], [rateData]);
  const allRoles: ResourceRole[] = useMemo(() => roleData?.data ?? [], [roleData]);
  const allCategories: ManpowerCategoryMaster[] = useMemo(
    () => categoryData?.data ?? [],
    [categoryData],
  );
  const grades: GradeMaster[] = useMemo(() => gradeData?.data ?? [], [gradeData]);
  const types: ResourceType[] = useMemo(() => typeData?.data ?? [], [typeData]);

  const laborTypeId = useMemo(
    () => types.find((t) => t.code === "LABOR")?.id ?? "",
    [types],
  );
  const manpowerRoles = useMemo(
    () => (laborTypeId ? allRoles.filter((r) => r.resourceTypeId === laborTypeId) : allRoles),
    [allRoles, laborTypeId],
  );

  const topCategories = useMemo(
    () => allCategories.filter((c) => !c.parentId).sort((a, b) => a.sortOrder - b.sortOrder),
    [allCategories],
  );

  const unitOptions = rateUnitOptionsWithFallback(form.unit);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setError(null);
    setShowForm(true);
  };

  const openEdit = (def: ManpowerRateMaster) => {
    setEditingId(def.id);
    setForm(formFromDef(def));
    setError(null);
    setShowForm(true);
  };

  const closeForm = () => {
    setShowForm(false);
    setEditingId(null);
    setForm(emptyForm());
    setError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (editingId) {
        await manpowerRateMasterApi.update(editingId, toPayload(form));
      } else {
        await manpowerRateMasterApi.create(toPayload(form));
      }
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["manpower-rate-master"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to save manpower rate"));
    }
  };

  const handleDelete = async (def: ManpowerRateMaster) => {
    if (!window.confirm(
      `Delete rate for ${def.roleName} / ${def.categoryName} / Grade ${def.gradeCode}? ` +
      `Resources currently linked to this row will keep their cached unit and rate but lose the link.`)) return;
    try {
      await manpowerRateMasterApi.delete(def.id);
      if (editingId === def.id) closeForm();
      queryClient.invalidateQueries({ queryKey: ["manpower-rate-master"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete manpower rate"));
    }
  };

  const columns = useMemo<ColumnDef<ManpowerRateMaster>[]>(() => [
    {
      accessorKey: "roleName",
      header: "Role",
      cell: ({ row }) => <span>{row.original.roleName ?? "—"}</span>,
    },
    {
      accessorKey: "categoryName",
      header: "Category",
      cell: ({ row }) => <span>{row.original.categoryName ?? "—"}</span>,
    },
    {
      accessorKey: "gradeCode",
      header: "Grade",
      cell: ({ row }) => <span className="font-mono text-sm">{row.original.gradeCode ?? "—"}</span>,
    },
    {
      accessorKey: "unit",
      header: "Unit",
      cell: ({ row }) => <span>{row.original.unit}</span>,
    },
    {
      accessorKey: "rate",
      header: "Rate",
      cell: ({ row }) => <span className="text-right block font-mono">{row.original.rate}</span>,
    },
    {
      accessorKey: "active",
      header: "Status",
      cell: ({ row }) =>
        row.original.active ? (
          <span className="text-emerald-700">Active</span>
        ) : (
          <span className="text-text-muted">Inactive</span>
        ),
    },
    {
      id: "actions",
      header: "Actions",
      cell: ({ row }) => (
        <div className="text-sm">
          <button
            onClick={() => openEdit(row.original)}
            className="text-accent hover:underline mr-3"
          >
            Edit
          </button>
          <button
            onClick={() => handleDelete(row.original)}
            className="text-danger hover:underline"
          >
            Delete
          </button>
        </div>
      ),
    },
  ], []);

  return (
    <div>
      <TabTip
        title="Manpower Rate Master"
        description="Rate book for manpower — one row per (Role + Category + Grade) combination. Each row's Unit and Rate auto-fill onto every Manpower Resource that points at it. Revising a rate cascades to all linked resources."
      />

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h2 className="text-2xl font-bold text-text-primary">Manpower Rate Master</h2>
        <div className="ml-auto">
          <button
            type="button"
            onClick={openCreate}
            className="px-4 py-2 bg-accent text-accent-foreground rounded-lg hover:bg-accent-hover"
          >
            + New Rate
          </button>
        </div>
      </div>

      {error && <div className="text-danger mb-4">{error}</div>}

      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl"
        >
          <h3 className="text-lg font-semibold text-text-primary mb-4">
            {editingId ? "Edit Manpower Rate" : "New Manpower Rate"}
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Role *</label>
              <SearchableSelect
                value={form.roleId}
                onChange={(v) => setForm({ ...form, roleId: v })}
                placeholder="— pick a role —"
                options={manpowerRoles.map((r) => ({
                  value: r.id,
                  label: `${r.name} (${r.code})`,
                }))}
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Grade *</label>
              <select
                value={form.gradeId}
                onChange={(e) => setForm({ ...form, gradeId: e.target.value })}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              >
                <option value="">— pick a grade —</option>
                {grades.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.code} — {g.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Category *</label>
              <select
                value={form.categoryId}
                onChange={(e) => setForm({ ...form, categoryId: e.target.value })}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              >
                <option value="">— pick a category —</option>
                {topCategories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Unit *</label>
              <select
                value={form.unit}
                onChange={(e) => setForm({ ...form, unit: e.target.value })}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              >
                {unitOptions.map((u) => (
                  <option key={u} value={u}>
                    {u}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Rate *</label>
              <input
                type="number"
                step="0.01"
                min="0"
                value={form.rate}
                onChange={(e) => setForm({ ...form, rate: e.target.value })}
                placeholder="e.g. 80"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              />
            </div>
            <div className="md:col-span-2 flex items-center gap-3">
              <label className="flex items-center gap-2 text-sm text-text-secondary">
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                Active (shown in Resource form rate-master pickers)
              </label>
            </div>
          </div>
          <div className="flex gap-2 mt-4">
            <button
              type="submit"
              className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600"
            >
              {editingId ? "Save Changes" : "Create"}
            </button>
            <button
              type="button"
              onClick={closeForm}
              className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {isError && (
        <div className="text-danger mb-4">
          {getErrorMessage(queryError, "Failed to load manpower rates")}
        </div>
      )}

      <VirtualDataTable
        columns={columns}
        data={rates}
        sortable
        resizable
        searchable
        isLoading={isLoading}
        emptyMessage={isLoading ? "Loading…" : "No manpower rates defined."}
      />
    </div>
  );
}
