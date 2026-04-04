"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { equipmentApi, type EquipmentLogResponse, type CreateEquipmentLogRequest, type EquipmentUtilizationSummary } from "@/lib/api/equipmentApi";
import { resourceApi, type ResourceResponse } from "@/lib/api/resourceApi";
import type { PagedResponse } from "@/lib/types";

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
        const pagedData = response.data as PagedResponse<EquipmentLogResponse>;
        setLogs(pagedData.content);
        setTotalElements(pagedData.pagination.totalElements);
        setPage(pageNum);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load equipment logs");
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
    } catch (err) {
      console.error("Failed to load utilization summary:", err);
    }
  };

  const loadResources = async () => {
    try {
      const response = await resourceApi.listResources(0, 100);
      if (response.data && "content" in response.data) {
        setResources((response.data as PagedResponse<ResourceResponse>).content);
      }
    } catch (err) {
      console.error("Failed to load resources:", err);
    }
  };

  useEffect(() => {
    loadEquipmentLogs();
    loadUtilization();
    loadResources();
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
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create equipment log");
    }
  };

  if (isLoading && logs.length === 0) {
    return <div className="p-6">Loading equipment logs...</div>;
  }

  return (
    <div className="p-6">
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4">Equipment Logs</h1>

        {/* Utilization Summary Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          <div className="bg-blue-50 p-4 rounded-lg border border-blue-200">
            <p className="text-sm text-gray-600 mb-1">Total Equipment</p>
            <p className="text-2xl font-bold text-blue-600">{utilization.length}</p>
          </div>
          <div className="bg-green-50 p-4 rounded-lg border border-green-200">
            <p className="text-sm text-gray-600 mb-1">Avg Utilization</p>
            <p className="text-2xl font-bold text-green-600">
              {utilization.length > 0
                ? (utilization.reduce((sum, u) => sum + u.utilizationPercentage, 0) / utilization.length).toFixed(1)
                : 0}
              %
            </p>
          </div>
          <div className="bg-red-50 p-4 rounded-lg border border-red-200">
            <p className="text-sm text-gray-600 mb-1">Total Breakdown Hours</p>
            <p className="text-2xl font-bold text-red-600">
              {utilization.reduce((sum, u) => sum + u.totalBreakdownHours, 0).toFixed(1)}h
            </p>
          </div>
          <div className="bg-yellow-50 p-4 rounded-lg border border-yellow-200">
            <p className="text-sm text-gray-600 mb-1">Total Idle Hours</p>
            <p className="text-2xl font-bold text-yellow-600">
              {utilization.reduce((sum, u) => sum + u.totalIdleHours, 0).toFixed(1)}h
            </p>
          </div>
        </div>

        <button
          onClick={() => setShowForm(!showForm)}
          className="mb-6 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          {showForm ? "Cancel" : "Add Equipment Log"}
        </button>

        {error && <div className="text-red-600 mb-4">{error}</div>}

        {showForm && (
          <form onSubmit={handleSubmit} className="bg-white p-4 rounded-lg border border-gray-300 mb-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Resource</label>
                <select
                  value={formData.resourceId}
                  onChange={(e) => setFormData({ ...formData, resourceId: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  required
                >
                  <option value="">Select a resource</option>
                  {resources.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Log Date</label>
                <input
                  type="date"
                  value={formData.logDate}
                  onChange={(e) => setFormData({ ...formData, logDate: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Deployment Site</label>
                <input
                  type="text"
                  value={formData.deploymentSite}
                  onChange={(e) => setFormData({ ...formData, deploymentSite: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Operating Hours</label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.operatingHours}
                  onChange={(e) => setFormData({ ...formData, operatingHours: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Idle Hours</label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.idleHours}
                  onChange={(e) => setFormData({ ...formData, idleHours: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Breakdown Hours</label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.breakdownHours}
                  onChange={(e) => setFormData({ ...formData, breakdownHours: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Fuel Consumed (L)</label>
                <input
                  type="number"
                  step="0.1"
                  value={formData.fuelConsumed}
                  onChange={(e) => setFormData({ ...formData, fuelConsumed: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Operator Name</label>
                <input
                  type="text"
                  value={formData.operatorName}
                  onChange={(e) => setFormData({ ...formData, operatorName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Status</label>
                <select
                  value={formData.status}
                  onChange={(e) => setFormData({ ...formData, status: e.target.value as any })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                >
                  <option value="WORKING">Working</option>
                  <option value="IDLE">Idle</option>
                  <option value="UNDER_MAINTENANCE">Under Maintenance</option>
                  <option value="BREAKDOWN">Breakdown</option>
                </select>
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1">Remarks</label>
                <textarea
                  value={formData.remarks}
                  onChange={(e) => setFormData({ ...formData, remarks: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  rows={3}
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <button type="submit" className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700">
                Save Log
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="px-4 py-2 bg-gray-400 text-white rounded-lg hover:bg-gray-500"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {/* Logs Table */}
        <div className="overflow-x-auto">
          <table className="w-full border-collapse border border-gray-300">
            <thead>
              <tr className="bg-gray-100">
                <th className="border border-gray-300 px-4 py-2 text-left">Date</th>
                <th className="border border-gray-300 px-4 py-2 text-left">Resource</th>
                <th className="border border-gray-300 px-4 py-2 text-left">Site</th>
                <th className="border border-gray-300 px-4 py-2 text-right">Operating Hrs</th>
                <th className="border border-gray-300 px-4 py-2 text-right">Idle Hrs</th>
                <th className="border border-gray-300 px-4 py-2 text-right">Breakdown Hrs</th>
                <th className="border border-gray-300 px-4 py-2 text-left">Status</th>
                <th className="border border-gray-300 px-4 py-2 text-left">Operator</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id} className="hover:bg-gray-50">
                  <td className="border border-gray-300 px-4 py-2">{log.logDate}</td>
                  <td className="border border-gray-300 px-4 py-2">{log.resourceId.substring(0, 8)}...</td>
                  <td className="border border-gray-300 px-4 py-2">{log.deploymentSite || "-"}</td>
                  <td className="border border-gray-300 px-4 py-2 text-right">{log.operatingHours || 0}</td>
                  <td className="border border-gray-300 px-4 py-2 text-right">{log.idleHours || 0}</td>
                  <td className="border border-gray-300 px-4 py-2 text-right">{log.breakdownHours || 0}</td>
                  <td className="border border-gray-300 px-4 py-2">
                    <span
                      className={`px-2 py-1 rounded text-white text-sm ${
                        log.status === "WORKING"
                          ? "bg-green-600"
                          : log.status === "IDLE"
                            ? "bg-yellow-600"
                            : log.status === "UNDER_MAINTENANCE"
                              ? "bg-blue-600"
                              : "bg-red-600"
                      }`}
                    >
                      {log.status.replace(/_/g, " ")}
                    </span>
                  </td>
                  <td className="border border-gray-300 px-4 py-2">{log.operatorName || "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalElements > 20 && (
          <div className="flex gap-2 mt-4">
            <button
              onClick={() => loadEquipmentLogs(Math.max(0, page - 1))}
              disabled={page === 0}
              className="px-4 py-2 bg-gray-300 text-gray-700 rounded disabled:opacity-50"
            >
              Previous
            </button>
            <span className="px-4 py-2">{page + 1}</span>
            <button
              onClick={() => loadEquipmentLogs(page + 1)}
              disabled={(page + 1) * 20 >= totalElements}
              className="px-4 py-2 bg-gray-300 text-gray-700 rounded disabled:opacity-50"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
