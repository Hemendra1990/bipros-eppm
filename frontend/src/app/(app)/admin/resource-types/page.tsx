"use client";

import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  resourceTypeApi,
  BASE_CATEGORY_LABEL,
  type CreateResourceTypeDefRequest,
  type ResourceTypeBaseCategory,
  type ResourceTypeDef,
  type UpdateResourceTypeDefRequest,
} from "@/lib/api/resourceTypeApi";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

interface DefForm {
  code: string;
  name: string;
  baseCategory: ResourceTypeBaseCategory;
  codePrefix: string;
  sortOrder: string;
  active: boolean;
}

const emptyForm = (): DefForm => ({
  code: "",
  name: "",
  baseCategory: "LABOR",
  codePrefix: "",
  sortOrder: "",
  active: true,
});

const formFromDef = (d: ResourceTypeDef): DefForm => ({
  code: d.code,
  name: d.name,
  baseCategory: d.baseCategory,
  codePrefix: d.codePrefix ?? "",
  sortOrder: d.sortOrder == null ? "" : String(d.sortOrder),
  active: d.active,
});

const toPayload = (form: DefForm): CreateResourceTypeDefRequest => ({
  code: form.code.trim().toUpperCase(),
  name: form.name.trim(),
  baseCategory: form.baseCategory,
  codePrefix: form.codePrefix.trim() ? form.codePrefix.trim().toUpperCase() : null,
  sortOrder: form.sortOrder.trim() === "" ? null : Number(form.sortOrder),
  active: form.active,
});

export default function ResourceTypesAdminPage() {
  const queryClient = useQueryClient();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<DefForm>(emptyForm());
  const [error, setError] = useState<string | null>(null);

  const { data, isLoading, isError, error: queryError } = useQuery({
    queryKey: ["resource-types"],
    queryFn: () => resourceTypeApi.list(),
  });

  const defs: ResourceTypeDef[] = useMemo(() => data?.data ?? [], [data]);
  const editingDef = defs.find((d) => d.id === editingId) ?? null;
  const isEditingSystemDefault = editingDef?.systemDefault === true;

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setError(null);
    setShowForm(true);
  };

  const openEdit = (def: ResourceTypeDef) => {
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
        const payload: UpdateResourceTypeDefRequest = toPayload(form);
        await resourceTypeApi.update(editingId, payload);
      } else {
        await resourceTypeApi.create(toPayload(form));
      }
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["resource-types"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to save resource type"));
    }
  };

  const handleDelete = async (def: ResourceTypeDef) => {
    if (def.systemDefault) return;
    if (!window.confirm(`Delete resource type "${def.name}"? This cannot be undone.`)) return;
    try {
      await resourceTypeApi.delete(def.id);
      if (editingId === def.id) closeForm();
      queryClient.invalidateQueries({ queryKey: ["resource-types"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete resource type"));
    }
  };

  return (
    <div className="p-6">
      <TabTip
        title="Resource Types"
        description="Manage the list of Resource Type categories shown when creating resources. The 3M defaults — Manpower, Material and Machine — are locked; admins can add custom types tagged with one of those base categories."
      />

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-bold text-text-primary">Resource Types</h1>
        <div className="ml-auto">
          <button
            type="button"
            onClick={openCreate}
            className="px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
          >
            + New Resource Type
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
            {editingId ? `Edit "${editingDef?.name ?? "Type"}"` : "New Resource Type"}
            {isEditingSystemDefault && (
              <span className="ml-2 text-xs uppercase tracking-wide text-text-muted">
                System default — code &amp; base category locked
              </span>
            )}
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Code</label>
              <input
                type="text"
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
                disabled={isEditingSystemDefault}
                placeholder="e.g. SUBCONTRACTOR"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg disabled:opacity-60"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Name</label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Sub-Contractor"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">
                Base Category
              </label>
              <select
                value={form.baseCategory}
                onChange={(e) =>
                  setForm({
                    ...form,
                    baseCategory: e.target.value as ResourceTypeBaseCategory,
                  })
                }
                disabled={isEditingSystemDefault}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg disabled:opacity-60"
              >
                <option value="LABOR">Manpower</option>
                <option value="MATERIAL">Material</option>
                <option value="NONLABOR">Machine</option>
              </select>
              <p className="mt-1 text-xs text-text-muted">
                Drives cost reporting bucket and equipment-only fields on the new-resource form.
              </p>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">
                Code Prefix
              </label>
              <input
                type="text"
                value={form.codePrefix}
                onChange={(e) => setForm({ ...form, codePrefix: e.target.value })}
                placeholder="Default LAB / MAT / EQ"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
              <p className="mt-1 text-xs text-text-muted">
                Used when a resource is created without an explicit code. Falls back to the base
                category default.
              </p>
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
                Active (shown in new-resource dropdowns)
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
          {getErrorMessage(queryError, "Failed to load resource types")}
        </div>
      )}

      <div className="overflow-x-auto">
        <table className="w-full border-collapse border border-border">
          <thead>
            <tr className="bg-surface/80">
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Code</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Name</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">
                Base Category
              </th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">
                Prefix
              </th>
              <th className="border border-border px-4 py-2 text-right text-text-secondary">
                Sort Order
              </th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">
                Status
              </th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td
                  colSpan={7}
                  className="border border-border px-4 py-6 text-center text-text-muted"
                >
                  Loading…
                </td>
              </tr>
            )}
            {!isLoading && defs.length === 0 && (
              <tr>
                <td
                  colSpan={7}
                  className="border border-border px-4 py-6 text-center text-text-muted"
                >
                  No resource types defined.
                </td>
              </tr>
            )}
            {defs.map((def) => (
              <tr key={def.id} className="text-text-primary hover:bg-surface-hover/30">
                <td className="border border-border px-4 py-2 font-mono text-sm">
                  {def.code}
                  {def.systemDefault && (
                    <span className="ml-2 text-[10px] uppercase tracking-wide text-text-muted">
                      system
                    </span>
                  )}
                </td>
                <td className="border border-border px-4 py-2">{def.name}</td>
                <td className="border border-border px-4 py-2">
                  {BASE_CATEGORY_LABEL[def.baseCategory]}
                </td>
                <td className="border border-border px-4 py-2 font-mono text-sm">
                  {def.codePrefix ?? "—"}
                </td>
                <td className="border border-border px-4 py-2 text-right">
                  {def.sortOrder ?? "—"}
                </td>
                <td className="border border-border px-4 py-2">
                  {def.active ? (
                    <span className="text-emerald-700">Active</span>
                  ) : (
                    <span className="text-text-muted">Inactive</span>
                  )}
                </td>
                <td className="border border-border px-4 py-2 text-sm">
                  <button
                    onClick={() => openEdit(def)}
                    className="text-accent hover:underline mr-3"
                  >
                    Edit
                  </button>
                  {!def.systemDefault && (
                    <button
                      onClick={() => handleDelete(def)}
                      className="text-danger hover:underline"
                    >
                      Delete
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
