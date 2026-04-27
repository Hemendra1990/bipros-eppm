"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { equipmentApi, type EquipmentLogResponse, type CreateEquipmentLogRequest, type EquipmentUtilizationSummary } from "@/lib/api/equipmentApi";
import { resourceApi, type ResourceResponse } from "@/lib/api/resourceApi";
import { TabTip } from "@/components/common/TabTip";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { getErrorMessage } from "@/lib/utils/error";
import type { PagedResponse } from "@/lib/types";
import { useQuery } from "@tanstack/react-query";

// Spring's native Page<T> serialises with these fields at the root of the
// response body (no `pagination` sub-object). The paged endpoints in
// EquipmentLogController / LabourReturnController return ApiResponse<Page<T>>.
interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

interface EquipmentLogForm {
  resourceId: string;
  logDate: string;
  deploymentSite: string;
  operatingHours: number;
  idleHours: number;
  breakdownHours: number;
  fuelConsumed: number;
  operatorName: string;
  remarks: string;
  status: "WORKING" | "IDLE" | "UNDER_MAINTENANCE" | "BREAKDOWN";
}

const initialFormState: EquipmentLogForm = {
  resourceId: "",
  logDate: new Date().toISOString().split("T")[0],
  deploymentSite: "",
  operatingHours: 0,
  idleHours: 0,
  breakdownHours: 0,
  fuelConsumed: 0,
  operatorName: "",
  remarks: "",
  status: "WORKING",
};

export default function EquipmentLogsPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  const [logs, setLogs] = useState<EquipmentLogResponse[]>([]);
  const [utilization, setUtilization] = useState<EquipmentUtilizationSummary[]>([]);
  const [resources, setResources] = useState<ResourceResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<EquipmentLogForm>(initialFormState);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const loadEquipmentLogs = async (pageNum = 0) => {
    try {
      setIsLoading(true);
      const response = await equipmentApi.getLogsByProject(projectId, pageNum, 20);
      if (response.data) {
        // Backend returns Spring Page<T>: { content, totalElements, ... } at the root.
        const pagedData = response.data as unknown as SpringPage<EquipmentLogResponse>;
        setLogs(pagedData.content ?? []);
        setTotalElements(pagedData.totalElements ?? 0);
        setPage(pageNum);
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to load equipment logs"));
    } finally {
      setIsLoading(false);
    }
  };

  const loadUtilization = async () => {
    try {
      const response = await equipmentApi.getUtilizationSummary(projectId);
      if (response.data) {
        setUtilization(response.data);
      }
    } catch (err: unknown) {
      console.error(getErrorMessage(err, "Failed to load utilization summary"));
    }
  };

  // Load resources via react-query so the equipment dropdown is populated and
  // the table can render friendly resource names. Enabled once logs are loaded
  // (so the name map can be joined on the log rows).
  const {
    data: resourcesQueryData,
  } = useQuery({
    queryKey: ["resources-for-equipment-logs"],
    queryFn: () => resourceApi.listResources(0, 500),
    enabled: !isLoading,
  });

  useEffect(() => {
    if (!resourcesQueryData) return;
    // Backend returns a flat array; fall back to paged envelope just in case.
    const raw = resourcesQueryData.data as unknown;
    if (Array.isArray(raw)) {
      setResources(raw as ResourceResponse[]);
    } else if (raw && typeof raw === "object" && "content" in raw) {
      setResources((raw as PagedResponse<ResourceResponse>).content);
    }
  }, [resourcesQueryData]);

  // Build a lookup so the Resource column can render `${code} — ${name}`.
  const resourceById = new Map<string, { code: string; name: string }>();
  for (const r of resources) {
    resourceById.set(r.id, { code: r.code, name: r.name });
  }

  useEffect(() => {
    loadEquipmentLogs();
    loadUtilization();
  }, [projectId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const request: CreateEquipmentLogRequest = {
        resourceId: formData.resourceId,
        projectId,
        logDate: formData.logDate,
        deploymentSite: formData.deploymentSite || undefined,
        operatingHours: formData.operatingHours || undefined,
        idleHours: formData.idleHours || undefined,
        breakdownHours: formData.breakdownHours || undefined,
        fuelConsumed: formData.fuelConsumed || undefined,
        operatorName: formData.operatorName || undefined,
        remarks: formData.remarks || undefined,
        status: formData.status,
      };

      await equipmentApi.createLog(projectId, request);
      setFormData(initialFormState);
      setShowForm(false);
      loadEquipmentLogs();
      loadUtilization();
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create equipment log"));
    }
  };

  if (isLoading && logs.length === 0) {
    return <div className="p-6 text-text-muted">Loading equipment logs...</div>;
  }

  return (
    <div className="p-6">
      <TabTip
        title="Equipment Logs"
        description="Track equipment deployed on site — utilization hours, breakdown incidents, and deployment location. Helps monitor equipment productivity."
      />
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-text-primary">Equipment Logs</h1>

        {/* Utilization Summary Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          <div className="bg-accent/10 p-4 rounded-lg border border-accent/20">
            <p className="text-sm text-text-secondary mb-1">Total Equipment</p>
            <p className="text-2xl font-bold text-accent">{utilization.length}</p>
          </div>
          <div className="bg-success/10 p-4 rounded-lg border border-success/20">
            <p className="text-sm text-text-secondary mb-1">Avg Utilization</p>
            <p className="text-2xl font-bold text-success">
              {utilization.length > 0
                ? (utilization.reduce((sum, u) => sum + u.utilizationPercentage, 0) / utilization.length).toFixed(1)
                : 0}
              %
            </p>
          </div>
          <div className="bg-danger/10 p-4 rounded-lg border border-danger/20">
            <p className="text-sm text-text-secondary mb-1">Total Breakdown Hours</p>
            <p className="text-2xl font-bold text-danger">
              {utilization.reduce((sum, u) => sum + u.totalBreakdownHours, 0).toFixed(1)}h
            </p>
          </div>
          <div className="bg-warning/10 p-4 rounded-lg border border-warning/20">
            <p className="text-sm text-text-secondary mb-1">Total Idle Hours</p>
            <p className="text-2xl font-bold text-warning">
              {utilization.reduce((sum, u) => sum + u.totalIdleHours, 0).toFixed(1)}h
            </p>
          </div>
        </div>

        <button
          onClick={() => setShowForm(!showForm)}
          className="mb-6 px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
        >
          {showForm ? "Cancel" : "Add Equipment Log"}
        </button>

        {error && <div className="text-danger mb-4">{error}</div>}

        {showForm && (
          <form onSubmit={handleSubmit} className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Resource</label>
                <SearchableSelect
                  value={formData.resourceId}
                  onChange={(val) => setFormData({ ...formData, resourceId: val })}
                  placeholder="Search equipment..."
                  options={resources.map((r) => ({
                    value: r.id,
                    label: `${r.code} - ${r.name}`,
                  }))}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Log Date</label>
                <input
                  type="date"
                  value={formData.logDate}
                  onChange={(e) => setFormData({ ...formData, logDate: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Deployment Site</label>
                <input
                  type="text"
                  value={formData.deploymentSite}
                  onChange={(e) => setFormData({ ...formData, deploymentSite: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Operating Hours</label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.operatingHours}
                  onChange={(e) => setFormData({ ...formData, operatingHours: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Idle Hours</label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.idleHours}
                  onChange={(e) => setFormData({ ...formData, idleHours: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Breakdown Hours</label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.breakdownHours}
                  onChange={(e) => setFormData({ ...formData, breakdownHours: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Fuel Consumed (L)</label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.fuelConsumed}
                  onChange={(e) => setFormData({ ...formData, fuelConsumed: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Operator Name</label>
                <input
                  type="text"
                  value={formData.operatorName}
                  onChange={(e) => setFormData({ ...formData, operatorName: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Status</label>
                <select
                  value={formData.status}
                  onChange={(e) => setFormData({ ...formData, status: e.target.value as any })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                >
                  <option value="WORKING">Working</option>
                  <option value="IDLE">Idle</option>
                  <option value="UNDER_MAINTENANCE">Under Maintenance</option>
                  <option value="BREAKDOWN">Breakdown</option>
                </select>
              </div>
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
                Save Log
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
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Resource</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Site</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Operating Hrs</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Idle Hrs</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Breakdown Hrs</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary" title="operatingHours / 8">Days</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary" title="operating / (operating + idle + breakdown)">% Util</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Status</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Operator</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => {
                const res = resourceById.get(log.resourceId);
                const resourceLabel = res
                  ? `${res.code} \u2014 ${res.name}`
                  : `${log.resourceId.substring(0, 8)}...`;
                const op = log.operatingHours ?? 0;
                const idle = log.idleHours ?? 0;
                const bd = log.breakdownHours ?? 0;
                const totalHours = op + idle + bd;
                const days = op / 8;
                const utilPct = totalHours > 0 ? (op / totalHours) * 100 : null;
                const utilBand =
                  utilPct === null
                    ? "text-text-muted"
                    : utilPct >= 80
                      ? "text-success font-semibold"
                      : utilPct >= 50
                        ? "text-warning font-semibold"
                        : "text-danger font-semibold";
                return (
                <tr key={log.id} className="hover:bg-surface-hover/30 text-text-primary">
                  <td className="border border-border px-4 py-2">{log.logDate}</td>
                  <td className="border border-border px-4 py-2">{resourceLabel}</td>
                  <td className="border border-border px-4 py-2">{log.deploymentSite || "-"}</td>
                  <td className="border border-border px-4 py-2 text-right">{op}</td>
                  <td className="border border-border px-4 py-2 text-right">{idle}</td>
                  <td className="border border-border px-4 py-2 text-right">{bd}</td>
                  <td className="border border-border px-4 py-2 text-right">
                    {days > 0 ? days.toFixed(2) : "\u2014"}
                  </td>
                  <td className={`border border-border px-4 py-2 text-right ${utilBand}`}>
                    {utilPct === null ? "\u2014" : `${utilPct.toFixed(1)}%`}
                  </td>
                  <td className="border border-border px-4 py-2">
                    <span
                      className={`px-2 py-1 rounded text-text-primary text-sm ${
                        log.status === "WORKING"
                          ? "bg-success/10 text-success ring-1 ring-success/20"
                          : log.status === "IDLE"
                            ? "bg-warning/10 text-warning ring-1 ring-amber-500/20"
                            : log.status === "UNDER_MAINTENANCE"
                              ? "bg-accent/10 text-accent ring-1 ring-accent/20"
                              : "bg-danger/10 text-danger ring-1 ring-red-500/20"
                      }`}
                    >
                      {log.status.replace(/_/g, " ")}
                    </span>
                  </td>
                  <td className="border border-border px-4 py-2">{log.operatorName || "-"}</td>
                </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalElements > 20 && (
          <div className="flex gap-2 mt-4">
            <button
              onClick={() => loadEquipmentLogs(Math.max(0, page - 1))}
              disabled={page === 0}
              className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded disabled:opacity-50"
            >
              Previous
            </button>
            <span className="px-4 py-2 text-text-secondary">{page + 1}</span>
            <button
              onClick={() => loadEquipmentLogs(page + 1)}
              disabled={(page + 1) * 20 >= totalElements}
              className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded disabled:opacity-50"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
