"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  materialConsumptionApi,
  type MaterialConsumptionLogResponse,
  type CreateMaterialConsumptionLogRequest,
} from "@/lib/api/materialConsumptionApi";
import { projectApi } from "@/lib/api/projectApi";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

const UNITS = ["Cum", "MT", "Bag", "Rm", "Each"] as const;

interface MaterialConsumptionForm {
  logDate: string;
  materialName: string;
  unit: string;
  openingStock: number;
  received: number;
  consumed: number;
  wastagePercent: string; // optional — keep as string so empty stays empty
  issuedBy: string;
  receivedBy: string;
  remarks: string;
}

const today = () => new Date().toISOString().split("T")[0];

const initialFormState: MaterialConsumptionForm = {
  logDate: today(),
  materialName: "",
  unit: "Cum",
  openingStock: 0,
  received: 0,
  consumed: 0,
  wastagePercent: "",
  issuedBy: "",
  receivedBy: "",
  remarks: "",
};

const fmtNum = (v: number | null | undefined) =>
  v === null || v === undefined ? "—" : String(v);

export default function MaterialConsumptionPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const queryClient = useQueryClient();

  const { data: projectData } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
    enabled: !!projectId,
  });
  const project = projectData?.data;

  const [from, setFrom] = useState<string>("");
  const [to, setTo] = useState<string>("");
  const [appliedFrom, setAppliedFrom] = useState<string>("");
  const [appliedTo, setAppliedTo] = useState<string>("");

  useEffect(() => {
    if (!project) return;
    if (appliedFrom === "" && project.plannedStartDate) {
      setFrom(project.plannedStartDate);
      setAppliedFrom(project.plannedStartDate);
    }
    if (appliedTo === "" && project.plannedFinishDate) {
      setTo(project.plannedFinishDate);
      setAppliedTo(project.plannedFinishDate);
    }
  }, [project, appliedFrom, appliedTo]);

  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<MaterialConsumptionForm>(initialFormState);
  const [error, setError] = useState<string | null>(null);

  const {
    data: listResponse,
    isLoading,
    isError,
    error: queryError,
  } = useQuery({
    queryKey: ["material-consumption", projectId, appliedFrom, appliedTo],
    queryFn: () =>
      materialConsumptionApi.list(projectId, { from: appliedFrom, to: appliedTo }),
    enabled: !!projectId && !!appliedFrom && !!appliedTo,
  });

  const logs: MaterialConsumptionLogResponse[] = Array.isArray(listResponse?.data)
    ? (listResponse?.data ?? [])
    : [];

  const handleApply = () => {
    setAppliedFrom(from);
    setAppliedTo(to);
  };

  const invalidate = () => {
    queryClient.invalidateQueries({
      queryKey: ["material-consumption", projectId, appliedFrom, appliedTo],
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const payload: CreateMaterialConsumptionLogRequest = {
        logDate: formData.logDate,
        materialName: formData.materialName,
        unit: formData.unit,
        openingStock: formData.openingStock,
        received: formData.received,
        consumed: formData.consumed,
        wastagePercent:
          formData.wastagePercent === "" ? null : Number(formData.wastagePercent),
        issuedBy: formData.issuedBy || null,
        receivedBy: formData.receivedBy || null,
        remarks: formData.remarks || null,
      };
      await materialConsumptionApi.create(projectId, payload);
      setFormData(initialFormState);
      setShowForm(false);
      invalidate();
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create material consumption log"));
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm("Delete this material consumption entry?")) return;
    try {
      await materialConsumptionApi.delete(projectId, id);
      invalidate();
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete entry"));
    }
  };

  if (isLoading && logs.length === 0) {
    return <div className="p-6 text-text-muted">Loading material consumption...</div>;
  }

  return (
    <div className="p-6">
      <TabTip
        title="Material Consumption"
        description="Daily store-keeper log — opening / received / consumed / closing stock with wastage."
      />
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-text-primary">Material Consumption</h1>

        {/* Date filter bar */}
        <div className="flex flex-wrap items-end gap-3 mb-6">
          <div>
            <label className="block text-sm font-medium mb-1 text-text-secondary">From</label>
            <input
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
            />
          </div>
          <div>
            <label className="block text-sm font-medium mb-1 text-text-secondary">To</label>
            <input
              type="date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
            />
          </div>
          <button
            onClick={handleApply}
            className="px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
          >
            Apply
          </button>
        </div>

        <button
          onClick={() => setShowForm(!showForm)}
          className="mb-6 px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
        >
          {showForm ? "Cancel" : "Add Entry"}
        </button>

        {error && <div className="text-danger mb-4">{error}</div>}
        {isError && (
          <div className="text-danger mb-4">
            {getErrorMessage(queryError, "Failed to load material consumption")}
          </div>
        )}

        {showForm && (
          <form
            onSubmit={handleSubmit}
            className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl"
          >
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Date</label>
                <input
                  type="date"
                  value={formData.logDate}
                  onChange={(e) => setFormData({ ...formData, logDate: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Material Name
                </label>
                <input
                  type="text"
                  value={formData.materialName}
                  onChange={(e) => setFormData({ ...formData, materialName: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Unit</label>
                <select
                  value={formData.unit}
                  onChange={(e) => setFormData({ ...formData, unit: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                >
                  {UNITS.map((u) => (
                    <option key={u} value={u}>
                      {u}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Opening Stock
                </label>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  value={formData.openingStock}
                  onChange={(e) =>
                    setFormData({ ...formData, openingStock: parseFloat(e.target.value) || 0 })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Received
                </label>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  value={formData.received}
                  onChange={(e) =>
                    setFormData({ ...formData, received: parseFloat(e.target.value) || 0 })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Consumed
                </label>
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  value={formData.consumed}
                  onChange={(e) =>
                    setFormData({ ...formData, consumed: parseFloat(e.target.value) || 0 })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Wastage %
                </label>
                <input
                  type="number"
                  min={0}
                  max={100}
                  step="0.01"
                  value={formData.wastagePercent}
                  onChange={(e) =>
                    setFormData({ ...formData, wastagePercent: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Issued By
                </label>
                <input
                  type="text"
                  value={formData.issuedBy}
                  onChange={(e) => setFormData({ ...formData, issuedBy: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Received By
                </label>
                <input
                  type="text"
                  value={formData.receivedBy}
                  onChange={(e) => setFormData({ ...formData, receivedBy: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Remarks
                </label>
                <textarea
                  value={formData.remarks}
                  onChange={(e) => setFormData({ ...formData, remarks: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  rows={3}
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <button
                type="submit"
                className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600"
              >
                Save Entry
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

        {/* Logs Table */}
        <div className="overflow-x-auto">
          <table className="w-full border-collapse border border-border">
            <thead>
              <tr className="bg-surface/80">
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Date</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Material</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Unit</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Opening</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Received</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Consumed</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Closing</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Wastage %</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Issued By</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Received By</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Remarks</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Actions</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id} className="hover:bg-surface-hover/30 text-text-primary">
                  <td className="border border-border px-4 py-2">{log.logDate}</td>
                  <td className="border border-border px-4 py-2">{log.materialName}</td>
                  <td className="border border-border px-4 py-2">{log.unit}</td>
                  <td className="border border-border px-4 py-2 text-right">
                    {fmtNum(log.openingStock)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {fmtNum(log.received)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {fmtNum(log.consumed)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {fmtNum(log.closingStock)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {log.wastagePercent === null || log.wastagePercent === undefined
                      ? "—"
                      : `${log.wastagePercent.toFixed(2)}%`}
                  </td>
                  <td className="border border-border px-4 py-2">{log.issuedBy ?? "—"}</td>
                  <td className="border border-border px-4 py-2">{log.receivedBy ?? "—"}</td>
                  <td className="border border-border px-4 py-2">{log.remarks ?? "—"}</td>
                  <td className="border border-border px-4 py-2">
                    <button
                      onClick={() => handleDelete(log.id)}
                      className="px-2 py-1 bg-danger/10 text-danger ring-1 ring-red-500/20 rounded text-sm hover:bg-danger/20"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {logs.length === 0 && !isLoading && (
                <tr>
                  <td
                    colSpan={12}
                    className="border border-border px-4 py-6 text-center text-text-muted"
                  >
                    No material consumption entries for this date range.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
