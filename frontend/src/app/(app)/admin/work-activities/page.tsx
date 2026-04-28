"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  workActivityApi,
  type CreateWorkActivityRequest,
  type WorkActivityResponse,
} from "@/lib/api/workActivityApi";
import { TabTip } from "@/components/common/TabTip";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";
import { getErrorMessage } from "@/lib/utils/error";

interface ActivityForm {
  code: string;
  name: string;
  defaultUnit: string;
  discipline: string;
  description: string;
  sortOrder: string;
  active: boolean;
}

const initialFormState: ActivityForm = {
  code: "",
  name: "",
  defaultUnit: "",
  discipline: "",
  description: "",
  sortOrder: "",
  active: true,
};

const toIntOrUndefined = (value: string): number | undefined => {
  if (value === "" || value === null || value === undefined) return undefined;
  const parsed = parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
};

export default function WorkActivitiesPage() {
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formData, setFormData] = useState<ActivityForm>(initialFormState);
  const [error, setError] = useState<string | null>(null);
  const [confirmDialog, setConfirmDialog] = useState<{
    open: boolean;
    title: string;
    message: string;
    onConfirm: () => void;
  }>({ open: false, title: "", message: "", onConfirm: () => {} });

  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["work-activities"],
    queryFn: () => workActivityApi.list(),
  });

  const activities: WorkActivityResponse[] = data?.data ?? [];

  const resetForm = () => {
    setFormData(initialFormState);
    setEditingId(null);
    setShowForm(false);
    setError(null);
  };

  const handleEdit = (activity: WorkActivityResponse) => {
    setEditingId(activity.id);
    setFormData({
      code: activity.code,
      name: activity.name,
      defaultUnit: activity.defaultUnit ?? "",
      discipline: activity.discipline ?? "",
      description: activity.description ?? "",
      sortOrder: activity.sortOrder?.toString() ?? "",
      active: activity.active,
    });
    setShowForm(true);
    setError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const request: CreateWorkActivityRequest = {
        code: formData.code || undefined,
        name: formData.name,
        defaultUnit: formData.defaultUnit || undefined,
        discipline: formData.discipline || undefined,
        description: formData.description || undefined,
        sortOrder: toIntOrUndefined(formData.sortOrder),
        active: formData.active,
      };
      if (editingId) {
        await workActivityApi.update(editingId, request);
      } else {
        await workActivityApi.create(request);
      }
      resetForm();
      queryClient.invalidateQueries({ queryKey: ["work-activities"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to save work activity"));
    }
  };

  const handleDelete = (id: string) => {
    setConfirmDialog({
      open: true,
      title: "Delete Work Activity",
      message: "Are you sure you want to delete this work activity? This action cannot be undone.",
      onConfirm: async () => {
        setConfirmDialog((d) => ({ ...d, open: false }));
        try {
          await workActivityApi.delete(id);
          queryClient.invalidateQueries({ queryKey: ["work-activities"] });
        } catch (err: unknown) {
          setError(getErrorMessage(err, "Failed to delete work activity"));
        }
      },
    });
  };

  const handleDeleteAll = () => {
    setConfirmDialog({
      open: true,
      title: "Delete All Work Activities",
      message:
        "This will permanently remove all work activities that are not referenced by productivity norms. Activities linked to norms will be skipped.",
      onConfirm: async () => {
        setConfirmDialog((d) => ({ ...d, open: false }));
        try {
          await workActivityApi.deleteAll();
          queryClient.invalidateQueries({ queryKey: ["work-activities"] });
        } catch (err: unknown) {
          setError(getErrorMessage(err, "Failed to delete work activities"));
        }
      },
    });
  };

  if (isLoading && activities.length === 0) {
    return <div className="p-6 text-text-muted">Loading work activities...</div>;
  }

  return (
    <div className="p-6">
      <TabTip
        title="Work Activities"
        description="Master library of unit-of-work definitions (Clearing & Grubbing, Subgrade Preparation, …). Productivity Norms attach to one of these and to a resource type or specific resource — same activity can have a different norm per resource."
      />
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-text-primary">Work Activities</h1>

        <div className="flex gap-3 mb-6">
          <button
            onClick={() => (showForm ? resetForm() : setShowForm(true))}
            className="px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
          >
            {showForm ? "Cancel" : "Add Activity"}
          </button>
          {activities.length > 0 && (
            <button
              onClick={handleDeleteAll}
              className="px-4 py-2 bg-danger text-text-primary rounded-lg hover:bg-red-600"
            >
              Delete All
            </button>
          )}
        </div>

        {error && <div className="text-danger mb-4">{error}</div>}

        {showForm && (
          <form
            onSubmit={handleSubmit}
            className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl"
          >
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Code <span className="text-text-muted">(auto-generated from name when blank)</span>
                </label>
                <input
                  type="text"
                  value={formData.code}
                  onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  maxLength={50}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Name <span className="text-danger">*</span>
                </label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                  maxLength={150}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Default Unit
                </label>
                <input
                  type="text"
                  value={formData.defaultUnit}
                  onChange={(e) => setFormData({ ...formData, defaultUnit: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  placeholder="e.g. Sqm, Cum, MT"
                  maxLength={20}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Discipline
                </label>
                <input
                  type="text"
                  value={formData.discipline}
                  onChange={(e) => setFormData({ ...formData, discipline: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  placeholder="earthwork / pavement / structures"
                  maxLength={50}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Sort Order
                </label>
                <input
                  type="number"
                  step="1"
                  value={formData.sortOrder}
                  onChange={(e) => setFormData({ ...formData, sortOrder: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div className="flex items-end">
                <label className="inline-flex items-center gap-2 text-text-secondary">
                  <input
                    type="checkbox"
                    checked={formData.active}
                    onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                    className="h-4 w-4"
                  />
                  Active
                </label>
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Description
                </label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  rows={3}
                  maxLength={500}
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <button
                type="submit"
                className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600"
              >
                {editingId ? "Update Activity" : "Save Activity"}
              </button>
              <button
                type="button"
                onClick={resetForm}
                className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        <div className="overflow-x-auto">
          <table className="w-full border-collapse border border-border">
            <thead>
              <tr className="bg-surface/80">
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Code</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Name</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Default Unit</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Discipline</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Sort</th>
                <th className="border border-border px-4 py-2 text-center text-text-secondary">Active</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Actions</th>
              </tr>
            </thead>
            <tbody>
              {activities.map((a) => (
                <tr key={a.id} className="hover:bg-surface-hover/30 text-text-primary">
                  <td className="border border-border px-4 py-2 font-mono text-sm">{a.code}</td>
                  <td className="border border-border px-4 py-2">{a.name}</td>
                  <td className="border border-border px-4 py-2">{a.defaultUnit ?? "-"}</td>
                  <td className="border border-border px-4 py-2">{a.discipline ?? "-"}</td>
                  <td className="border border-border px-4 py-2 text-right">{a.sortOrder ?? "-"}</td>
                  <td className="border border-border px-4 py-2 text-center">
                    {a.active ? "✓" : "—"}
                  </td>
                  <td className="border border-border px-4 py-2">
                    <div className="flex gap-2">
                      <button
                        onClick={() => handleEdit(a)}
                        className="px-3 py-1 bg-accent/10 text-accent ring-1 ring-accent/20 rounded hover:bg-accent/20"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => handleDelete(a.id)}
                        className="px-3 py-1 bg-danger/10 text-danger ring-1 ring-red-500/20 rounded hover:bg-danger/20"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {activities.length === 0 && (
                <tr>
                  <td colSpan={7} className="border border-border px-4 py-8 text-center text-text-muted">
                    No work activities yet — add one to start defining productivity norms.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <ConfirmDialog
        open={confirmDialog.open}
        title={confirmDialog.title}
        message={confirmDialog.message}
        onConfirm={confirmDialog.onConfirm}
        onCancel={() => setConfirmDialog((d) => ({ ...d, open: false }))}
      />
    </div>
  );
}
