"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  resourceDeploymentApi,
  type DailyResourceDeploymentResponse,
  type CreateDailyResourceDeploymentRequest,
  type DeploymentResourceType,
} from "@/lib/api/resourceDeploymentApi";
import { projectApi } from "@/lib/api/projectApi";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

type TypeFilter = "ALL" | DeploymentResourceType;

interface ResourceDeploymentForm {
  logDate: string;
  resourceType: DeploymentResourceType;
  resourceDescription: string;
  nosPlanned: number;
  nosDeployed: number;
  hoursWorked: number;
  idleHours: number;
  remarks: string;
}

const today = () => new Date().toISOString().split("T")[0];

const initialFormState: ResourceDeploymentForm = {
  logDate: today(),
  resourceType: "MANPOWER",
  resourceDescription: "",
  nosPlanned: 0,
  nosDeployed: 0,
  hoursWorked: 0,
  idleHours: 0,
  remarks: "",
};

const fmtNum = (v: number | null | undefined) =>
  v === null || v === undefined ? "—" : String(v);

export default function ResourceDeploymentPage() {
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
  const [typeFilter, setTypeFilter] = useState<TypeFilter>("ALL");
  const [appliedFrom, setAppliedFrom] = useState<string>("");
  const [appliedTo, setAppliedTo] = useState<string>("");
  const [appliedType, setAppliedType] = useState<TypeFilter>(typeFilter);

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
  const [formData, setFormData] = useState<ResourceDeploymentForm>(initialFormState);
  const [error, setError] = useState<string | null>(null);

  const {
    data: listResponse,
    isLoading,
    isError,
    error: queryError,
  } = useQuery({
    queryKey: ["resource-deployment", projectId, appliedFrom, appliedTo, appliedType],
    queryFn: () =>
      resourceDeploymentApi.list(projectId, {
        from: appliedFrom,
        to: appliedTo,
        resourceType: appliedType === "ALL" ? undefined : appliedType,
      }),
    enabled: !!projectId && !!appliedFrom && !!appliedTo,
  });

  const logs: DailyResourceDeploymentResponse[] = Array.isArray(listResponse?.data)
    ? (listResponse?.data ?? [])
    : [];

  const handleApply = () => {
    setAppliedFrom(from);
    setAppliedTo(to);
    setAppliedType(typeFilter);
  };

  const invalidate = () => {
    queryClient.invalidateQueries({
      queryKey: ["resource-deployment", projectId, appliedFrom, appliedTo, appliedType],
    });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const payload: CreateDailyResourceDeploymentRequest = {
        logDate: formData.logDate,
        resourceType: formData.resourceType,
        resourceDescription: formData.resourceDescription,
        nosPlanned: formData.nosPlanned,
        nosDeployed: formData.nosDeployed,
        hoursWorked: formData.hoursWorked,
        idleHours: formData.idleHours,
        remarks: formData.remarks || null,
      };
      await resourceDeploymentApi.create(projectId, payload);
      setFormData(initialFormState);
      setShowForm(false);
      invalidate();
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create resource deployment entry"));
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm("Delete this deployment entry?")) return;
    try {
      await resourceDeploymentApi.delete(projectId, id);
      invalidate();
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete entry"));
    }
  };

  if (isLoading && logs.length === 0) {
    return <div className="p-6 text-text-muted">Loading resource deployment...</div>;
  }

  return (
    <div className="p-6">
      <TabTip
        title="Daily Resource Deployment"
        description="Manpower & equipment — nos. planned vs deployed, hours worked, idle hours per day."
      />
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-text-primary">Daily Resource Deployment</h1>

        {/* Filter bar */}
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
          <div>
            <label className="block text-sm font-medium mb-1 text-text-secondary">Type</label>
            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value as TypeFilter)}
              className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
            >
              <option value="ALL">All</option>
              <option value="MANPOWER">MANPOWER</option>
              <option value="EQUIPMENT">EQUIPMENT</option>
            </select>
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
            {getErrorMessage(queryError, "Failed to load resource deployment")}
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
                <label className="block text-sm font-medium mb-1 text-text-secondary">Type</label>
                <select
                  value={formData.resourceType}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      resourceType: e.target.value as DeploymentResourceType,
                    })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                >
                  <option value="MANPOWER">MANPOWER</option>
                  <option value="EQUIPMENT">EQUIPMENT</option>
                </select>
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Description
                </label>
                <input
                  type="text"
                  value={formData.resourceDescription}
                  onChange={(e) =>
                    setFormData({ ...formData, resourceDescription: e.target.value })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Nos. Planned
                </label>
                <input
                  type="number"
                  min={0}
                  value={formData.nosPlanned}
                  onChange={(e) =>
                    setFormData({ ...formData, nosPlanned: parseInt(e.target.value) || 0 })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Nos. Deployed
                </label>
                <input
                  type="number"
                  min={0}
                  value={formData.nosDeployed}
                  onChange={(e) =>
                    setFormData({ ...formData, nosDeployed: parseInt(e.target.value) || 0 })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Hours Worked
                </label>
                <input
                  type="number"
                  min={0}
                  max={24}
                  step="0.1"
                  value={formData.hoursWorked}
                  onChange={(e) =>
                    setFormData({ ...formData, hoursWorked: parseFloat(e.target.value) || 0 })
                  }
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Idle Hours
                </label>
                <input
                  type="number"
                  min={0}
                  max={24}
                  step="0.1"
                  value={formData.idleHours}
                  onChange={(e) =>
                    setFormData({ ...formData, idleHours: parseFloat(e.target.value) || 0 })
                  }
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
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Type</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Description</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Nos. Planned</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Nos. Deployed</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Hours Worked</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Idle Hours</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Remarks</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Actions</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id} className="hover:bg-surface-hover/30 text-text-primary">
                  <td className="border border-border px-4 py-2">{log.logDate}</td>
                  <td className="border border-border px-4 py-2">
                    <span
                      className={`px-2 py-1 rounded text-text-primary text-sm ${
                        log.resourceType === "MANPOWER"
                          ? "bg-success/10 text-success ring-1 ring-success/20"
                          : "bg-accent/10 text-accent ring-1 ring-accent/20"
                      }`}
                    >
                      {log.resourceType}
                    </span>
                  </td>
                  <td className="border border-border px-4 py-2">{log.resourceDescription}</td>
                  <td className="border border-border px-4 py-2 text-right">
                    {fmtNum(log.nosPlanned)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {fmtNum(log.nosDeployed)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {fmtNum(log.hoursWorked)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {fmtNum(log.idleHours)}
                  </td>
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
                    colSpan={9}
                    className="border border-border px-4 py-6 text-center text-text-muted"
                  >
                    No resource deployment entries for this date range.
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
