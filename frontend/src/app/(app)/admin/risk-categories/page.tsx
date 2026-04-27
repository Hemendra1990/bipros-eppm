"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { Pencil, Plus, Shield, Tag, Trash2, X } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { notificationHelpers } from "@/lib/notificationHelpers";
import {
  riskCategoryApi,
  INDUSTRIES,
  type Industry,
  type RiskCategoryTypeResponse,
  type RiskCategoryMasterResponse,
} from "@/lib/api/riskCategoryApi";

/**
 * Hierarchical admin: left pane lists Risk Category Types, right pane lists Categories
 * under the selected Type. System-default rows can be deactivated but never deleted.
 *
 * Industry filter on each category narrows what the project-side cascading select shows
 * (project industry X → categories with industry X or GENERIC).
 */
export default function RiskCategoriesAdminPage() {
  const queryClient = useQueryClient();

  // ─────────── shared state ───────────
  const [selectedTypeId, setSelectedTypeId] = useState<string>("");
  const [error, setError] = useState("");

  // ─────────── Type form state ───────────
  const [showTypeForm, setShowTypeForm] = useState(false);
  const [editingType, setEditingType] = useState<RiskCategoryTypeResponse | null>(null);
  const [typeCode, setTypeCode] = useState("");
  const [typeName, setTypeName] = useState("");
  const [typeDescription, setTypeDescription] = useState("");
  const [typeActive, setTypeActive] = useState(true);
  const [typeSortOrder, setTypeSortOrder] = useState(0);

  // ─────────── Category form state ───────────
  const [showCategoryForm, setShowCategoryForm] = useState(false);
  const [editingCategory, setEditingCategory] = useState<RiskCategoryMasterResponse | null>(null);
  const [catCode, setCatCode] = useState("");
  const [catName, setCatName] = useState("");
  const [catDescription, setCatDescription] = useState("");
  const [catTypeId, setCatTypeId] = useState("");
  const [catIndustry, setCatIndustry] = useState<Industry>("GENERIC");
  const [catActive, setCatActive] = useState(true);
  const [catSortOrder, setCatSortOrder] = useState(0);

  // ─────────── queries ───────────
  const { data: typesData, isLoading: typesLoading } = useQuery({
    queryKey: ["risk-category-types-all"],
    queryFn: () => riskCategoryApi.listAllTypes(),
  });
  const types = useMemo(() => typesData?.data ?? [], [typesData]);

  const { data: categoriesData, isLoading: categoriesLoading } = useQuery({
    queryKey: ["risk-categories-by-type", selectedTypeId],
    queryFn: () => riskCategoryApi.listCategories({ typeId: selectedTypeId }),
    enabled: !!selectedTypeId,
  });
  const categories = useMemo(() => categoriesData?.data ?? [], [categoriesData]);

  // ─────────── mutations: types ───────────
  const createType = useMutation({
    mutationFn: () =>
      riskCategoryApi.createType({
        code: typeCode.trim().toUpperCase(),
        name: typeName.trim(),
        description: typeDescription.trim() || undefined,
        active: typeActive,
        sortOrder: typeSortOrder,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risk-category-types-all"] });
      queryClient.invalidateQueries({ queryKey: ["risk-category-types"] });
      resetTypeForm();
      setShowTypeForm(false);
      toast.success("Risk category type created");
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : "Failed to create type");
      notificationHelpers.handleApiError(err, "Failed to create type");
    },
  });

  const updateType = useMutation({
    mutationFn: () => {
      if (!editingType) throw new Error("No type selected for edit");
      return riskCategoryApi.updateType(editingType.id, {
        name: typeName.trim(),
        description: typeDescription.trim() || undefined,
        active: typeActive,
        sortOrder: typeSortOrder,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risk-category-types-all"] });
      queryClient.invalidateQueries({ queryKey: ["risk-category-types"] });
      resetTypeForm();
      setShowTypeForm(false);
      toast.success("Risk category type updated");
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : "Failed to update type");
      notificationHelpers.handleApiError(err, "Failed to update type");
    },
  });

  const deleteType = useMutation({
    mutationFn: (id: string) => riskCategoryApi.deleteType(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risk-category-types-all"] });
      queryClient.invalidateQueries({ queryKey: ["risk-category-types"] });
      setSelectedTypeId("");
      toast.success("Risk category type deleted");
    },
    onError: (err) => notificationHelpers.handleApiError(err, "Failed to delete type"),
  });

  // ─────────── mutations: categories ───────────
  const createCategory = useMutation({
    mutationFn: () =>
      riskCategoryApi.createCategory({
        code: catCode.trim().toUpperCase(),
        name: catName.trim(),
        description: catDescription.trim() || undefined,
        typeId: catTypeId,
        industry: catIndustry,
        active: catActive,
        sortOrder: catSortOrder,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risk-categories-by-type"] });
      queryClient.invalidateQueries({ queryKey: ["risk-categories"] });
      queryClient.invalidateQueries({ queryKey: ["risk-categories-all-for-template-form"] });
      resetCategoryForm();
      setShowCategoryForm(false);
      toast.success("Risk category created");
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : "Failed to create category");
      notificationHelpers.handleApiError(err, "Failed to create category");
    },
  });

  const updateCategory = useMutation({
    mutationFn: () => {
      if (!editingCategory) throw new Error("No category selected for edit");
      return riskCategoryApi.updateCategory(editingCategory.id, {
        name: catName.trim(),
        description: catDescription.trim() || undefined,
        typeId: catTypeId,
        industry: catIndustry,
        active: catActive,
        sortOrder: catSortOrder,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risk-categories-by-type"] });
      queryClient.invalidateQueries({ queryKey: ["risk-categories"] });
      queryClient.invalidateQueries({ queryKey: ["risk-categories-all-for-template-form"] });
      resetCategoryForm();
      setShowCategoryForm(false);
      toast.success("Risk category updated");
    },
    onError: (err) => {
      setError(err instanceof Error ? err.message : "Failed to update category");
      notificationHelpers.handleApiError(err, "Failed to update category");
    },
  });

  const deleteCategory = useMutation({
    mutationFn: (id: string) => riskCategoryApi.deleteCategory(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risk-categories-by-type"] });
      queryClient.invalidateQueries({ queryKey: ["risk-categories"] });
      toast.success("Risk category deleted");
    },
    onError: (err) => notificationHelpers.handleApiError(err, "Failed to delete category"),
  });

  // ─────────── helpers ───────────
  const resetTypeForm = () => {
    setEditingType(null);
    setTypeCode("");
    setTypeName("");
    setTypeDescription("");
    setTypeActive(true);
    setTypeSortOrder(0);
    setError("");
  };

  const resetCategoryForm = () => {
    setEditingCategory(null);
    setCatCode("");
    setCatName("");
    setCatDescription("");
    setCatTypeId(selectedTypeId);
    setCatIndustry("GENERIC");
    setCatActive(true);
    setCatSortOrder(0);
    setError("");
  };

  const openCreateType = () => {
    resetTypeForm();
    setShowTypeForm(true);
  };

  const openEditType = (t: RiskCategoryTypeResponse) => {
    setEditingType(t);
    setTypeCode(t.code);
    setTypeName(t.name);
    setTypeDescription(t.description ?? "");
    setTypeActive(t.active);
    setTypeSortOrder(t.sortOrder);
    setError("");
    setShowTypeForm(true);
  };

  const openCreateCategory = () => {
    resetCategoryForm();
    setCatTypeId(selectedTypeId);
    setShowCategoryForm(true);
  };

  const openEditCategory = (c: RiskCategoryMasterResponse) => {
    setEditingCategory(c);
    setCatCode(c.code);
    setCatName(c.name);
    setCatDescription(c.description ?? "");
    setCatTypeId(c.type.id);
    setCatIndustry(c.industry);
    setCatActive(c.active);
    setCatSortOrder(c.sortOrder);
    setError("");
    setShowCategoryForm(true);
  };

  const handleSaveType = () => {
    if (!typeName.trim()) {
      setError("Name is required");
      return;
    }
    if (!editingType && !typeCode.trim()) {
      setError("Code is required");
      return;
    }
    if (editingType) updateType.mutate();
    else createType.mutate();
  };

  const handleSaveCategory = () => {
    if (!catName.trim()) {
      setError("Name is required");
      return;
    }
    if (!catTypeId) {
      setError("Type is required");
      return;
    }
    if (!editingCategory && !catCode.trim()) {
      setError("Code is required");
      return;
    }
    if (editingCategory) updateCategory.mutate();
    else createCategory.mutate();
  };

  const selectedType = types.find((t) => t.id === selectedTypeId) ?? null;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Risk Categories"
        description="Hierarchical master of Risk Category Types and Categories. Each category is tagged with an Industry; a project of industry X sees categories tagged X plus GENERIC. Seeded rows are protected from deletion."
        actions={
          <button
            onClick={openCreateType}
            className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
          >
            <Plus size={16} /> New Type
          </button>
        }
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[320px_1fr] lg:h-[calc(100vh-220px)]">
        {/* ─────── Types pane ─────── */}
        <div className="flex min-h-0 flex-col overflow-hidden rounded-xl border border-border bg-surface/50">
          <div className="shrink-0 border-b border-border px-4 py-3 text-sm font-semibold text-text-primary">
            Types ({types.length})
          </div>
          {typesLoading ? (
            <div className="p-4 text-sm text-text-muted">Loading…</div>
          ) : types.length === 0 ? (
            <EmptyState icon={Tag} title="No types yet" description="Create your first Risk Category Type." />
          ) : (
            <ul className="flex-1 divide-y divide-border overflow-y-auto">
              {types.map((t) => (
                <li
                  key={t.id}
                  className={
                    "cursor-pointer px-4 py-3 hover:bg-surface-hover/50 " +
                    (t.id === selectedTypeId ? "bg-surface-hover/70" : "")
                  }
                  onClick={() => setSelectedTypeId(t.id)}
                >
                  <div className="flex items-center justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-text-primary">{t.name}</p>
                      <p className="font-mono text-xs text-text-muted">{t.code}</p>
                    </div>
                    <div className="flex items-center gap-1 text-xs text-text-muted">
                      <span className="rounded bg-surface px-2 py-0.5">{t.childCount}</span>
                      {t.systemDefault && <Shield size={12} className="text-text-muted" aria-label="System default" />}
                      {!t.active && <span className="rounded bg-warning/10 px-1.5 py-0.5 text-warning">off</span>}
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          openEditType(t);
                        }}
                        className="ml-1 rounded p-1 text-text-secondary hover:bg-surface hover:text-text-primary"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          if (window.confirm(`Delete type ${t.name}?`)) deleteType.mutate(t.id);
                        }}
                        disabled={t.systemDefault || t.childCount > 0}
                        title={
                          t.systemDefault
                            ? "System default — cannot delete"
                            : t.childCount > 0
                            ? "Has categories — delete or move them first"
                            : ""
                        }
                        className="rounded p-1 text-text-secondary hover:bg-surface hover:text-danger disabled:cursor-not-allowed disabled:opacity-30"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* ─────── Categories pane ─────── */}
        <div className="flex min-h-0 flex-col overflow-hidden rounded-xl border border-border bg-surface/50">
          <div className="flex shrink-0 items-center justify-between border-b border-border px-4 py-3">
            <div className="text-sm font-semibold text-text-primary">
              {selectedType ? `Categories in ${selectedType.name}` : "Pick a type to see its categories"}
            </div>
            {selectedTypeId && (
              <button
                onClick={openCreateCategory}
                className="inline-flex items-center gap-2 rounded-md border border-border bg-surface-hover px-3 py-1.5 text-xs font-medium text-text-primary hover:bg-surface"
              >
                <Plus size={14} /> New Category
              </button>
            )}
          </div>
          {!selectedTypeId ? (
            <EmptyState icon={Tag} title="No type selected" description="Click a type on the left." />
          ) : categoriesLoading ? (
            <div className="p-4 text-sm text-text-muted">Loading…</div>
          ) : categories.length === 0 ? (
            <EmptyState icon={Tag} title="No categories yet" description="Create the first category in this type." />
          ) : (
            <div className="flex-1 overflow-y-auto">
            <table className="w-full text-sm">
              <thead className="sticky top-0 z-10 border-b border-border bg-surface text-text-secondary">
                <tr>
                  <th className="px-4 py-2 text-left">Code</th>
                  <th className="px-4 py-2 text-left">Name</th>
                  <th className="px-4 py-2 text-left">Industry</th>
                  <th className="px-4 py-2 text-left">Active</th>
                  <th className="px-4 py-2 text-left">Default</th>
                  <th className="px-4 py-2 text-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {categories.map((c) => (
                  <tr key={c.id} className="border-b border-border/60 hover:bg-surface-hover/30">
                    <td className="px-4 py-2 font-mono text-xs">{c.code}</td>
                    <td className="px-4 py-2">{c.name}</td>
                    <td className="px-4 py-2 text-xs">{c.industry}</td>
                    <td className="px-4 py-2 text-xs">{c.active ? "Yes" : "No"}</td>
                    <td className="px-4 py-2 text-xs">{c.systemDefault ? <Shield size={14} className="text-text-muted" /> : ""}</td>
                    <td className="px-4 py-2 text-right">
                      <button
                        onClick={() => openEditCategory(c)}
                        className="rounded p-1 text-text-secondary hover:bg-surface-hover hover:text-text-primary"
                      >
                        <Pencil size={14} />
                      </button>
                      <button
                        onClick={() => {
                          if (window.confirm(`Delete category ${c.name}?`)) deleteCategory.mutate(c.id);
                        }}
                        disabled={c.systemDefault}
                        title={c.systemDefault ? "System default — cannot delete" : ""}
                        className="ml-1 rounded p-1 text-text-secondary hover:bg-surface-hover hover:text-danger disabled:cursor-not-allowed disabled:opacity-30"
                      >
                        <Trash2 size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          )}
        </div>
      </div>

      {/* ─────── Type form modal ─────── */}
      {showTypeForm && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-6">
          <div className="w-full max-w-md rounded-xl border border-border bg-surface p-6 shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-base font-semibold text-text-primary">
                {editingType ? `Edit Type: ${editingType.code}` : "New Risk Category Type"}
              </h3>
              <button onClick={() => setShowTypeForm(false)} className="text-text-secondary hover:text-text-primary">
                <X size={18} />
              </button>
            </div>
            {error && <div className="mb-3 rounded bg-danger/10 px-3 py-2 text-xs text-danger">{error}</div>}
            <div className="space-y-3">
              <div>
                <label className="mb-1 block text-xs text-text-secondary">Code (UPPER_SNAKE)</label>
                <input
                  value={typeCode}
                  onChange={(e) => setTypeCode(e.target.value)}
                  disabled={!!editingType}
                  className="w-full rounded border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary disabled:opacity-60"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-text-secondary">Name</label>
                <input
                  value={typeName}
                  onChange={(e) => setTypeName(e.target.value)}
                  className="w-full rounded border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-text-secondary">Description</label>
                <textarea
                  value={typeDescription}
                  onChange={(e) => setTypeDescription(e.target.value)}
                  rows={3}
                  className="w-full rounded border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs text-text-secondary">Sort Order</label>
                  <input
                    type="number"
                    value={typeSortOrder}
                    onChange={(e) => setTypeSortOrder(Number(e.target.value) || 0)}
                    className="w-full rounded border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary"
                  />
                </div>
                <div className="flex items-end">
                  <label className="flex items-center gap-2 text-sm text-text-secondary">
                    <input
                      type="checkbox"
                      checked={typeActive}
                      onChange={(e) => setTypeActive(e.target.checked)}
                    />
                    Active
                  </label>
                </div>
              </div>
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <button
                onClick={() => setShowTypeForm(false)}
                className="rounded border border-border px-3 py-1.5 text-sm text-text-secondary hover:bg-surface-hover"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveType}
                disabled={createType.isPending || updateType.isPending}
                className="rounded bg-accent px-3 py-1.5 text-sm text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ─────── Category form modal ─────── */}
      {showCategoryForm && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-6">
          <div className="w-full max-w-md rounded-xl border border-border bg-surface p-6 shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <h3 className="text-base font-semibold text-text-primary">
                {editingCategory ? `Edit Category: ${editingCategory.code}` : "New Risk Category"}
              </h3>
              <button onClick={() => setShowCategoryForm(false)} className="text-text-secondary hover:text-text-primary">
                <X size={18} />
              </button>
            </div>
            {error && <div className="mb-3 rounded bg-danger/10 px-3 py-2 text-xs text-danger">{error}</div>}
            <div className="space-y-3">
              <div>
                <label className="mb-1 block text-xs text-text-secondary">Code (UPPER-SNAKE-WITH-HYPHENS)</label>
                <input
                  value={catCode}
                  onChange={(e) => setCatCode(e.target.value)}
                  disabled={!!editingCategory}
                  className="w-full rounded border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary disabled:opacity-60"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-text-secondary">Name</label>
                <input
                  value={catName}
                  onChange={(e) => setCatName(e.target.value)}
                  className="w-full rounded border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-text-secondary">Description</label>
                <textarea
                  value={catDescription}
                  onChange={(e) => setCatDescription(e.target.value)}
                  rows={3}
                  className="w-full rounded border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs text-text-secondary">Type</label>
                  <select
                    value={catTypeId}
                    onChange={(e) => setCatTypeId(e.target.value)}
                    className="w-full rounded border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary"
                  >
                    <option value="">— Select Type —</option>
                    {types.map((t) => (
                      <option key={t.id} value={t.id}>{t.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="mb-1 block text-xs text-text-secondary">Industry</label>
                  <select
                    value={catIndustry}
                    onChange={(e) => setCatIndustry(e.target.value as Industry)}
                    className="w-full rounded border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary"
                  >
                    {INDUSTRIES.map((i) => (
                      <option key={i} value={i}>{i}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-xs text-text-secondary">Sort Order</label>
                  <input
                    type="number"
                    value={catSortOrder}
                    onChange={(e) => setCatSortOrder(Number(e.target.value) || 0)}
                    className="w-full rounded border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary"
                  />
                </div>
                <div className="flex items-end">
                  <label className="flex items-center gap-2 text-sm text-text-secondary">
                    <input
                      type="checkbox"
                      checked={catActive}
                      onChange={(e) => setCatActive(e.target.checked)}
                    />
                    Active
                  </label>
                </div>
              </div>
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <button
                onClick={() => setShowCategoryForm(false)}
                className="rounded border border-border px-3 py-1.5 text-sm text-text-secondary hover:bg-surface-hover"
              >
                Cancel
              </button>
              <button
                onClick={handleSaveCategory}
                disabled={createCategory.isPending || updateCategory.isPending}
                className="rounded bg-accent px-3 py-1.5 text-sm text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
