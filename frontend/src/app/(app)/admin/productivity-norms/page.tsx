"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  productivityNormApi,
  type ProductivityNormResponse,
  type ProductivityNormType,
  type CreateProductivityNormRequest,
} from "@/lib/api/productivityNormApi";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

interface NormForm {
  equipmentSpec: string;
  activityName: string;
  unit: string;
  outputPerManPerDay: string;
  outputPerHour: string;
  crewSize: string;
  outputPerDay: string;
  workingHoursPerDay: string;
  fuelLitresPerHour: string;
  remarks: string;
}

const initialFormState: NormForm = {
  equipmentSpec: "",
  activityName: "",
  unit: "",
  outputPerManPerDay: "",
  outputPerHour: "",
  crewSize: "",
  outputPerDay: "",
  workingHoursPerDay: "",
  fuelLitresPerHour: "",
  remarks: "",
};

const toNumberOrUndefined = (value: string): number | undefined => {
  if (value === "" || value === null || value === undefined) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
};

const toIntOrUndefined = (value: string): number | undefined => {
  if (value === "" || value === null || value === undefined) return undefined;
  const parsed = parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
};

export default function ProductivityNormsPage() {
  const [tab, setTab] = useState<ProductivityNormType>("MANPOWER");
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<NormForm>(initialFormState);
  const [error, setError] = useState<string | null>(null);

  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["productivity-norms", tab],
    queryFn: () => productivityNormApi.list(tab),
  });

  const norms: ProductivityNormResponse[] = data?.data ?? [];

  const handleTabChange = (nextTab: ProductivityNormType) => {
    setTab(nextTab);
    setShowForm(false);
    setFormData(initialFormState);
    setError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const base: CreateProductivityNormRequest = {
        normType: tab,
        activityName: formData.activityName,
        unit: formData.unit,
        remarks: formData.remarks || undefined,
        outputPerDay: toNumberOrUndefined(formData.outputPerDay),
      };

      const request: CreateProductivityNormRequest =
        tab === "MANPOWER"
          ? {
              ...base,
              outputPerManPerDay: toNumberOrUndefined(formData.outputPerManPerDay),
              crewSize: toIntOrUndefined(formData.crewSize),
            }
          : {
              ...base,
              equipmentSpec: formData.equipmentSpec || undefined,
              outputPerHour: toNumberOrUndefined(formData.outputPerHour),
              workingHoursPerDay: toNumberOrUndefined(formData.workingHoursPerDay),
              fuelLitresPerHour: toNumberOrUndefined(formData.fuelLitresPerHour),
            };

      await productivityNormApi.create(request);
      setFormData(initialFormState);
      setShowForm(false);
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["productivity-norms", tab] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create productivity norm"));
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm("Delete this productivity norm?")) return;
    try {
      await productivityNormApi.delete(id);
      queryClient.invalidateQueries({ queryKey: ["productivity-norms", tab] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete productivity norm"));
    }
  };

  if (isLoading && norms.length === 0) {
    return <div className="p-6 text-text-muted">Loading norms...</div>;
  }

  return (
    <div className="p-6">
      <TabTip
        title="Productivity Norms"
        description="Activity-wise man-day and equipment-hour output rates; the seed for resource estimates and daily-report validation."
      />
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-text-primary">Productivity Norms</h1>

        {/* Tabs */}
        <div className="flex gap-2 mb-6 border-b border-border">
          <button
            onClick={() => handleTabChange("MANPOWER")}
            className={`px-4 py-2 rounded-t-lg ${
              tab === "MANPOWER"
                ? "bg-accent text-text-primary"
                : "bg-surface-active/50 text-text-secondary hover:bg-border"
            }`}
          >
            Manpower
          </button>
          <button
            onClick={() => handleTabChange("EQUIPMENT")}
            className={`px-4 py-2 rounded-t-lg ${
              tab === "EQUIPMENT"
                ? "bg-accent text-text-primary"
                : "bg-surface-active/50 text-text-secondary hover:bg-border"
            }`}
          >
            Equipment
          </button>
        </div>

        <button
          onClick={() => setShowForm(!showForm)}
          className="mb-6 px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
        >
          {showForm ? "Cancel" : "Add Norm"}
        </button>

        {error && <div className="text-danger mb-4">{error}</div>}

        {showForm && (
          <form onSubmit={handleSubmit} className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {tab === "EQUIPMENT" && (
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">Equipment Spec</label>
                  <input
                    type="text"
                    value={formData.equipmentSpec}
                    onChange={(e) => setFormData({ ...formData, equipmentSpec: e.target.value })}
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  />
                </div>
              )}
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Activity</label>
                <input
                  type="text"
                  value={formData.activityName}
                  onChange={(e) => setFormData({ ...formData, activityName: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Unit</label>
                <input
                  type="text"
                  value={formData.unit}
                  onChange={(e) => setFormData({ ...formData, unit: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>

              {tab === "MANPOWER" ? (
                <>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">Output per Man per Day</label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.outputPerManPerDay}
                      onChange={(e) => setFormData({ ...formData, outputPerManPerDay: e.target.value })}
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">Crew Size</label>
                    <input
                      type="number"
                      step="1"
                      value={formData.crewSize}
                      onChange={(e) => setFormData({ ...formData, crewSize: e.target.value })}
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">Output per Day (optional)</label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.outputPerDay}
                      onChange={(e) => setFormData({ ...formData, outputPerDay: e.target.value })}
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                  </div>
                </>
              ) : (
                <>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">Output per Hour</label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.outputPerHour}
                      onChange={(e) => setFormData({ ...formData, outputPerHour: e.target.value })}
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">Working Hours per Day</label>
                    <input
                      type="number"
                      step="0.1"
                      value={formData.workingHoursPerDay}
                      onChange={(e) => setFormData({ ...formData, workingHoursPerDay: e.target.value })}
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">Output per Day (optional)</label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.outputPerDay}
                      onChange={(e) => setFormData({ ...formData, outputPerDay: e.target.value })}
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">Fuel Litres per Hour</label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.fuelLitresPerHour}
                      onChange={(e) => setFormData({ ...formData, fuelLitresPerHour: e.target.value })}
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                  </div>
                </>
              )}

              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-text-secondary">Remarks</label>
                <textarea
                  value={formData.remarks}
                  onChange={(e) => setFormData({ ...formData, remarks: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  rows={3}
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <button type="submit" className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600">
                Save Norm
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {/* Norms Table */}
        <div className="overflow-x-auto">
          {tab === "MANPOWER" ? (
            <table className="w-full border-collapse border border-border">
              <thead>
                <tr className="bg-surface/80">
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Activity</th>
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Unit</th>
                  <th className="border border-border px-4 py-2 text-right text-text-secondary">Output / Man / Day</th>
                  <th className="border border-border px-4 py-2 text-right text-text-secondary">Crew Size</th>
                  <th className="border border-border px-4 py-2 text-right text-text-secondary">Gang Output / Day</th>
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Remarks</th>
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Actions</th>
                </tr>
              </thead>
              <tbody>
                {norms.map((norm) => (
                  <tr key={norm.id} className="hover:bg-surface-hover/30 text-text-primary">
                    <td className="border border-border px-4 py-2">{norm.activityName}</td>
                    <td className="border border-border px-4 py-2">{norm.unit}</td>
                    <td className="border border-border px-4 py-2 text-right">{norm.outputPerManPerDay ?? "-"}</td>
                    <td className="border border-border px-4 py-2 text-right">{norm.crewSize ?? "-"}</td>
                    <td className="border border-border px-4 py-2 text-right">{norm.outputPerDay ?? "-"}</td>
                    <td className="border border-border px-4 py-2">{norm.remarks || "-"}</td>
                    <td className="border border-border px-4 py-2">
                      <button
                        onClick={() => handleDelete(norm.id)}
                        className="px-3 py-1 bg-danger/10 text-danger ring-1 ring-red-500/20 rounded hover:bg-danger/20"
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <table className="w-full border-collapse border border-border">
              <thead>
                <tr className="bg-surface/80">
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Equipment Spec</th>
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Activity</th>
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Unit</th>
                  <th className="border border-border px-4 py-2 text-right text-text-secondary">Output / Hour</th>
                  <th className="border border-border px-4 py-2 text-right text-text-secondary">Working Hrs / Day</th>
                  <th className="border border-border px-4 py-2 text-right text-text-secondary">Output / Day</th>
                  <th className="border border-border px-4 py-2 text-right text-text-secondary">Fuel L/Hr</th>
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Remarks</th>
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Actions</th>
                </tr>
              </thead>
              <tbody>
                {norms.map((norm) => (
                  <tr key={norm.id} className="hover:bg-surface-hover/30 text-text-primary">
                    <td className="border border-border px-4 py-2">{norm.equipmentSpec || "-"}</td>
                    <td className="border border-border px-4 py-2">{norm.activityName}</td>
                    <td className="border border-border px-4 py-2">{norm.unit}</td>
                    <td className="border border-border px-4 py-2 text-right">{norm.outputPerHour ?? "-"}</td>
                    <td className="border border-border px-4 py-2 text-right">{norm.workingHoursPerDay ?? "-"}</td>
                    <td className="border border-border px-4 py-2 text-right">{norm.outputPerDay ?? "-"}</td>
                    <td className="border border-border px-4 py-2 text-right">{norm.fuelLitresPerHour ?? "-"}</td>
                    <td className="border border-border px-4 py-2">{norm.remarks || "-"}</td>
                    <td className="border border-border px-4 py-2">
                      <button
                        onClick={() => handleDelete(norm.id)}
                        className="px-3 py-1 bg-danger/10 text-danger ring-1 ring-red-500/20 rounded hover:bg-danger/20"
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
