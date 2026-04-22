"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { activityApi, type GlobalChangeRequest } from "@/lib/api/activityApi";

interface GlobalChangeForm {
  filterField: string;
  filterValue: string;
  updateField: string;
  updateValue: string;
  operation: "SET" | "ADD" | "SUBTRACT";
}

const initialFormState: GlobalChangeForm = {
  filterField: "status",
  filterValue: "NOT_STARTED",
  updateField: "originalDuration",
  updateValue: "0",
  operation: "SET",
};

const filterFieldOptions = [
  { value: "status", label: "Status" },
  { value: "code", label: "Code (contains)" },
  { value: "name", label: "Name (contains)" },
  { value: "isCritical", label: "Is Critical" },
];

const updateFieldOptions = [
  { value: "status", label: "Status" },
  { value: "originalDuration", label: "Original Duration" },
  { value: "remainingDuration", label: "Remaining Duration" },
  { value: "percentComplete", label: "Percent Complete" },
];

const statusOptions = [
  "NOT_STARTED",
  "IN_PROGRESS",
  "COMPLETED",
  "SUSPENDED",
  "COMPLETED_EARLY",
  "COMPLETED_LATE",
];

export default function GlobalChangePage() {
  const params = useParams();
  const projectId = params.projectId as string;

  const [formData, setFormData] = useState<GlobalChangeForm>(initialFormState);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [matchingCount, setMatchingCount] = useState<number | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    try {
      setIsLoading(true);
      const result = await activityApi.applyGlobalChange(projectId, formData as GlobalChangeRequest);
      if (result.data?.updatedCount !== undefined) {
        setSuccess(
          `Global change applied successfully. ${result.data.updatedCount} activities updated.`
        );
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to apply global change");
    } finally {
      setIsLoading(false);
    }
  };

  const handleReset = () => {
    setFormData(initialFormState);
    setError(null);
    setSuccess(null);
    setMatchingCount(null);
  };

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="text-3xl font-bold">Global Change</h1>
        <p className="mt-2 text-text-secondary">
          Apply batch updates to activities matching specific criteria
        </p>
      </div>

      {error && <div className="rounded bg-danger/10 p-4 text-danger">{error}</div>}
      {success && <div className="rounded bg-success/10 p-4 text-success">{success}</div>}

      <div className="rounded border border-border bg-surface/50 p-6">
        <form onSubmit={handleSubmit} className="space-y-6">
          <fieldset className="space-y-4 border-b border-border pb-6">
            <legend className="text-lg font-semibold">Filter Criteria</legend>

            <div>
              <label className="block text-sm font-medium">Filter Field *</label>
              <select
                required
                value={formData.filterField}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    filterField: e.target.value,
                    filterValue: "",
                  })
                }
                className="mt-1 w-full rounded border border-border px-3 py-2"
              >
                {filterFieldOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium">Filter Value *</label>
              {formData.filterField === "status" ? (
                <select
                  required
                  value={formData.filterValue}
                  onChange={(e) => setFormData({ ...formData, filterValue: e.target.value })}
                  className="mt-1 w-full rounded border border-border px-3 py-2"
                >
                  <option value="">-- Select Status --</option>
                  {statusOptions.map((status) => (
                    <option key={status} value={status}>
                      {status.replace(/_/g, " ")}
                    </option>
                  ))}
                </select>
              ) : formData.filterField === "isCritical" ? (
                <select
                  required
                  value={formData.filterValue}
                  onChange={(e) => setFormData({ ...formData, filterValue: e.target.value })}
                  className="mt-1 w-full rounded border border-border px-3 py-2"
                >
                  <option value="">-- Select --</option>
                  <option value="true">True</option>
                  <option value="false">False</option>
                </select>
              ) : (
                <input
                  type="text"
                  required
                  value={formData.filterValue}
                  onChange={(e) => setFormData({ ...formData, filterValue: e.target.value })}
                  placeholder="Enter filter value"
                  className="mt-1 w-full rounded border border-border px-3 py-2"
                />
              )}
            </div>
          </fieldset>

          <fieldset className="space-y-4 border-b border-border pb-6">
            <legend className="text-lg font-semibold">Update Action</legend>

            <div>
              <label className="block text-sm font-medium">Update Field *</label>
              <select
                required
                value={formData.updateField}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    updateField: e.target.value,
                    updateValue: "",
                  })
                }
                className="mt-1 w-full rounded border border-border px-3 py-2"
              >
                {updateFieldOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium">Operation *</label>
                <select
                  required
                  value={formData.operation}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      operation: e.target.value as "SET" | "ADD" | "SUBTRACT",
                    })
                  }
                  className="mt-1 w-full rounded border border-border px-3 py-2"
                >
                  <option value="SET">Set</option>
                  <option value="ADD">Add</option>
                  <option value="SUBTRACT">Subtract</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium">Value *</label>
                {formData.updateField === "status" ? (
                  <select
                    required
                    value={formData.updateValue}
                    onChange={(e) => setFormData({ ...formData, updateValue: e.target.value })}
                    className="mt-1 w-full rounded border border-border px-3 py-2"
                  >
                    <option value="">-- Select Status --</option>
                    {statusOptions.map((status) => (
                      <option key={status} value={status}>
                        {status.replace(/_/g, " ")}
                      </option>
                    ))}
                  </select>
                ) : (
                  <input
                    type="number"
                    required
                    value={formData.updateValue}
                    onChange={(e) => setFormData({ ...formData, updateValue: e.target.value })}
                    placeholder="Enter value"
                    className="mt-1 w-full rounded border border-border px-3 py-2"
                  />
                )}
              </div>
            </div>
          </fieldset>

          <div className="rounded bg-accent/10 p-4">
            <p className="text-sm text-blue-300">
              <strong>Preview:</strong> This change will update the <strong>{formData.updateField}</strong> field
              using <strong>{formData.operation}</strong> operation for all activities where{" "}
              <strong>{formData.filterField}</strong> equals <strong>{formData.filterValue}</strong>.
            </p>
          </div>

          <div className="flex gap-2">
            <button
              type="submit"
              disabled={isLoading}
              className="rounded bg-green-600 px-6 py-2 text-text-primary hover:bg-green-600 disabled:opacity-50"
            >
              {isLoading ? "Applying..." : "Apply Global Change"}
            </button>
            <button
              type="button"
              onClick={handleReset}
              className="rounded bg-border px-6 py-2 text-text-primary hover:bg-surface-active"
            >
              Reset
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
