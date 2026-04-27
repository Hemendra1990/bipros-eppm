"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, Pencil, Trash2, X, Tag } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import {
  projectCategoryApi,
  type ProjectCategoryMasterResponse,
  type CreateProjectCategoryMasterRequest,
  type UpdateProjectCategoryMasterRequest,
} from "@/lib/api/projectCategoryApi";
import { notificationHelpers } from "@/lib/notificationHelpers";

export default function ProjectCategoriesAdminPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingCategory, setEditingCategory] = useState<ProjectCategoryMasterResponse | null>(null);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  // Form state
  const [formCode, setFormCode] = useState("");
  const [formName, setFormName] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formActive, setFormActive] = useState(true);
  const [formSortOrder, setFormSortOrder] = useState(0);

  const { data: categoriesData, isLoading } = useQuery({
    queryKey: ["project-categories-all"],
    queryFn: () => projectCategoryApi.listAll(),
  });

  const categories = categoriesData?.data ?? [];

  const resetForm = () => {
    setFormCode("");
    setFormName("");
    setFormDescription("");
    setFormActive(true);
    setFormSortOrder(0);
    setEditingCategory(null);
    setError("");
  };

  const openCreateForm = () => {
    resetForm();
    setShowForm(true);
  };

  const openEditForm = (category: ProjectCategoryMasterResponse) => {
    setEditingCategory(category);
    setFormCode(category.code);
    setFormName(category.name);
    setFormDescription(category.description ?? "");
    setFormActive(category.active);
    setFormSortOrder(category.sortOrder);
    setShowForm(true);
  };

  const createMutation = useMutation({
    mutationFn: (data: CreateProjectCategoryMasterRequest) => projectCategoryApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["project-categories-all"] });
      queryClient.invalidateQueries({ queryKey: ["project-categories"] });
      setShowForm(false);
      resetForm();
      notificationHelpers.creationSuccess("Category");
    },
    onError: (err: unknown) => {
      const msg = err instanceof Error ? err.message : "Failed to create category";
      setError(msg);
      notificationHelpers.handleApiError(err, "Failed to create category");
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateProjectCategoryMasterRequest }) =>
      projectCategoryApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["project-categories-all"] });
      queryClient.invalidateQueries({ queryKey: ["project-categories"] });
      setShowForm(false);
      resetForm();
      notificationHelpers.updateSuccess("Category");
    },
    onError: (err: unknown) => {
      const msg = err instanceof Error ? err.message : "Failed to update category";
      setError(msg);
      notificationHelpers.handleApiError(err, "Failed to update category");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => projectCategoryApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["project-categories-all"] });
      queryClient.invalidateQueries({ queryKey: ["project-categories"] });
      notificationHelpers.deletionSuccess("Category");
    },
    onError: (err: unknown) => {
      notificationHelpers.handleApiError(err, "Failed to delete category");
    },
  });

  const handleSave = async () => {
    if (!formName.trim()) {
      setError("Name is required");
      return;
    }
    if (!editingCategory && !formCode.trim()) {
      setError("Code is required");
      return;
    }
    setSaving(true);
    setError("");

    try {
      if (editingCategory) {
        await updateMutation.mutateAsync({
          id: editingCategory.id,
          data: {
            name: formName,
            description: formDescription || undefined,
            active: formActive,
            sortOrder: formSortOrder,
          },
        });
      } else {
        await createMutation.mutateAsync({
          code: formCode,
          name: formName,
          description: formDescription || undefined,
          active: formActive,
          sortOrder: formSortOrder,
        });
      }
    } catch {
      // Error handled by mutation onError
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Are you sure you want to delete this category?")) return;
    await deleteMutation.mutateAsync(id);
  };

  const inputClass =
    "mt-1 block w-full rounded-md border border-border bg-surface-hover/50 px-3 py-2 text-text-primary placeholder-gray-500 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent text-sm";

  return (
    <div>
      <PageHeader
        title="Project Categories"
        description="Manage configurable project category master data"
        actions={
          <button
            onClick={openCreateForm}
            className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
          >
            <Plus size={16} />
            New Category
          </button>
        }
      />

      {error && !showForm && (
        <div className="mb-4 rounded-md bg-danger/10 p-3 text-sm text-danger">{error}</div>
      )}

      {/* Create/Edit Form */}
      {showForm && (
        <div className="mb-6 rounded-lg border border-border bg-surface/80 p-6">
          <div className="mb-4 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-text-primary">
              {editingCategory ? "Edit Category" : "New Category"}
            </h3>
            <button
              onClick={() => {
                setShowForm(false);
                resetForm();
              }}
              className="rounded p-1 text-text-secondary hover:text-text-primary"
            >
              <X size={16} />
            </button>
          </div>

          {error && (
            <div className="mb-4 rounded-md bg-danger/10 p-3 text-sm text-danger">{error}</div>
          )}

          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-medium text-text-secondary">
                Code {editingCategory ? "" : "*"}
              </label>
              <input
                type="text"
                value={formCode}
                onChange={(e) => setFormCode(e.target.value)}
                disabled={!!editingCategory}
                placeholder="e.g., HIGHWAY"
                className={inputClass}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-text-secondary">Name *</label>
              <input
                type="text"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
                placeholder="e.g., Highway"
                className={inputClass}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-text-secondary">Sort Order</label>
              <input
                type="number"
                value={formSortOrder}
                onChange={(e) => setFormSortOrder(parseInt(e.target.value, 10) || 0)}
                className={inputClass}
              />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-medium text-text-secondary">Description</label>
              <input
                type="text"
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
                placeholder="Optional description"
                className={inputClass}
              />
            </div>
            <div className="flex items-end gap-3">
              <label className="flex items-center gap-2 text-sm text-text-secondary">
                <input
                  type="checkbox"
                  checked={formActive}
                  onChange={(e) => setFormActive(e.target.checked)}
                  className="rounded border-border"
                />
                Active
              </label>
            </div>
          </div>

          <div className="mt-4 flex gap-3">
            <button
              onClick={handleSave}
              disabled={saving}
              className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border"
            >
              {saving ? "Saving..." : editingCategory ? "Update Category" : "Create Category"}
            </button>
            <button
              onClick={() => {
                setShowForm(false);
                resetForm();
              }}
              className="rounded-md bg-surface-active/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-active"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Categories List */}
      {isLoading && (
        <div className="py-12 text-center text-text-muted">Loading categories...</div>
      )}

      {!isLoading && categories.length === 0 && !showForm && (
        <EmptyState
          title="No categories defined"
          description="Project categories allow you to classify projects dynamically. Create one to get started."
        />
      )}

      {categories.length > 0 && (
        <div className="space-y-2">
          {categories.map((category) => (
            <div
              key={category.id}
              className="flex items-center justify-between rounded-lg border border-border bg-surface/50 px-5 py-4"
            >
              <div className="flex items-center gap-4">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-surface-hover">
                  <Tag size={16} className="text-accent" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-text-primary">{category.name}</span>
                    <span className="rounded bg-surface-hover px-1.5 py-0.5 text-xs text-text-secondary">
                      {category.code}
                    </span>
                    {!category.active && (
                      <span className="rounded bg-danger/10 px-1.5 py-0.5 text-xs text-danger">
                        Inactive
                      </span>
                    )}
                  </div>
                  {category.description && (
                    <p className="mt-0.5 text-xs text-text-muted">{category.description}</p>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-1">
                <button
                  onClick={() => openEditForm(category)}
                  className="rounded p-2 text-text-muted hover:bg-surface-hover hover:text-accent"
                >
                  <Pencil size={14} />
                </button>
                <button
                  onClick={() => handleDelete(category.id)}
                  className="rounded p-2 text-text-muted hover:bg-danger/10 hover:text-danger"
                >
                  <Trash2 size={14} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
