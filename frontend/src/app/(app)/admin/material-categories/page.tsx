"use client";

import { VirtualDataTable, type ColumnDef } from "@/components/common/VirtualDataTable";

import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  materialCategoryMasterApi,
  type MaterialCategoryMaster,
  type MaterialCategoryMasterRequest,
} from "@/lib/api/materialCategoryMasterApi";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

interface DefForm {
  code: string;
  name: string;
  description: string;
  sortOrder: string;
  active: boolean;
}

const emptyForm = (): DefForm => ({
  code: "",
  name: "",
  description: "",
  sortOrder: "",
  active: true,
});

const formFromDef = (d: MaterialCategoryMaster): DefForm => ({
  code: d.code,
  name: d.name,
  description: d.description ?? "",
  sortOrder: d.sortOrder == null ? "" : String(d.sortOrder),
  active: d.active,
});

const toPayload = (form: DefForm): MaterialCategoryMasterRequest => ({
  code: form.code.trim().toUpperCase(),
  name: form.name.trim(),
  description: form.description.trim() ? form.description.trim() : null,
  sortOrder: form.sortOrder.trim() === "" ? null : Number(form.sortOrder),
  active: form.active,
});

export default function MaterialCategoriesAdminPage() {
  const queryClient = useQueryClient();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<DefForm>(emptyForm());
  const [error, setError] = useState<string | null>(null);

  const { data, isLoading, isError, error: queryError } = useQuery({
    queryKey: ["material-category-master"],
    queryFn: () => materialCategoryMasterApi.list(),
  });

  const defs: MaterialCategoryMaster[] = useMemo(() => data?.data ?? [], [data]);
  const editingDef = defs.find((d) => d.id === editingId) ?? null;

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setError(null);
    setShowForm(true);
  };

  const openEdit = (def: MaterialCategoryMaster) => {
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
        await materialCategoryMasterApi.update(editingId, toPayload(form));
      } else {
        await materialCategoryMasterApi.create(toPayload(form));
      }
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["material-category-master"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to save material category"));
    }
  };

  const handleDelete = async (def: MaterialCategoryMaster) => {
    if (!window.confirm(`Delete material category "${def.name}"? This cannot be undone.`)) return;
    try {
      await materialCategoryMasterApi.delete(def.id);
      if (editingId === def.id) closeForm();
      queryClient.invalidateQueries({ queryKey: ["material-category-master"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete material category"));
    }
  };

  const columns = useMemo<ColumnDef<MaterialCategoryMaster>[]>(() => [
    {
      accessorKey: "code",
      header: "Code",
      cell: ({ row }) => <span className="font-mono text-sm">{row.original.code}</span>,
    },
    {
      accessorKey: "name",
      header: "Name",
      cell: ({ row }) => <span>{row.original.name}</span>,
    },
    {
      accessorKey: "description",
      header: "Description",
      cell: ({ row }) => <span className="text-text-secondary">{row.original.description ?? "—"}</span>,
    },
    {
      accessorKey: "sortOrder",
      header: "Sort Order",
      cell: ({ row }) => <span className="text-right block">{row.original.sortOrder ?? "—"}</span>,
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
    <div className="p-6">
      <TabTip
        title="Material Categories"
        description="Material families (Cement, Steel, Aggregate, Sand, Bricks, ...). Used as one dimension of the Material Rate Master key. Each Material Rate Master row is uniquely identified by Category + Spec/Grade."
      />

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-bold text-text-primary">Material Categories</h1>
        <div className="ml-auto">
          <button
            type="button"
            onClick={openCreate}
            className="px-4 py-2 bg-accent text-accent-foreground rounded-lg hover:bg-accent-hover"
          >
            + New Material Category
          </button>
        </div>
      </div>

      {error && <div className="text-danger mb-4">{error}</div>}

      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl"
        >
          <h2 className="text-lg font-semibold text-text-primary mb-4">
            {editingId ? `Edit "${editingDef?.name ?? "Category"}"` : "New Material Category"}
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Code</label>
              <input
                type="text"
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
                placeholder="e.g. CEMENT"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Name</label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Cement"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium mb-1 text-text-secondary">
                Description
              </label>
              <textarea
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                rows={2}
                placeholder="Optional notes describing this material family"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">
                Sort Order
              </label>
              <input
                type="number"
                value={form.sortOrder}
                onChange={(e) => setForm({ ...form, sortOrder: e.target.value })}
                placeholder="Lower appears first"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div className="flex items-end">
              <label className="flex items-center gap-2 text-sm text-text-secondary">
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                Active (shown in rate-master dropdowns)
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
          {getErrorMessage(queryError, "Failed to load material categories")}
        </div>
      )}

      <VirtualDataTable
        columns={columns}
        data={defs}
        sortable
        resizable
        searchable={false}
        isLoading={isLoading}
        emptyMessage={isLoading ? "Loading…" : "No material categories defined."}
      />
    </div>
  );
}
