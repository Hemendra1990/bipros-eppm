"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, X, Trash2, AlertTriangle } from "lucide-react";
import { getErrorMessage } from "@/lib/utils/error";
import { riskApi } from "@/lib/api/riskApi";
import { riskTriggerApi } from "@/lib/api/riskTriggerApi";
import { apiClient } from "@/lib/api/client";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { EmptyState } from "@/components/common/EmptyState";
import { TabTip } from "@/components/common/TabTip";
import type { RiskResponse, CreateRiskRequest, ProjectResponse, ApiResponse, PagedResponse } from "@/lib/types";

export default function RiskPage() {
  const queryClient = useQueryClient();
  const [selectedProjectId, setSelectedProjectId] = useState<string>("");
  const [showForm, setShowForm] = useState(false);
  const [formError, setFormError] = useState("");

  const [formData, setFormData] = useState<CreateRiskRequest>({
    code: "",
    title: "",
    description: "",
    category: "TECHNICAL",
    probability: 1,
    impact: 1,
  });

  const { data: projectsData } = useQuery({
    queryKey: ["projects"],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<PagedResponse<ProjectResponse>>>("/v1/projects?page=0&size=50");
      return response.data.data?.content ?? [];
    },
  });

  const { data: risksData, isLoading, error } = useQuery({
    queryKey: ["risks", selectedProjectId],
    queryFn: () => riskApi.getRisksByProject(selectedProjectId, 0, 50),
    enabled: !!selectedProjectId,
  });

  const { data: triggersData } = useQuery({
    queryKey: ["risk-triggers", selectedProjectId],
    queryFn: () => riskTriggerApi.listTriggeredRisks(selectedProjectId),
    enabled: !!selectedProjectId,
  });

  const rawRisks = risksData?.data;
  const risks: RiskResponse[] = Array.isArray(rawRisks) ? rawRisks : (rawRisks as any)?.content ?? [];

  const createMutation = useMutation({
    mutationFn: (data: CreateRiskRequest) => {
      if (!selectedProjectId) {
        throw new Error("Please select a project first");
      }
      return apiClient
        .post<ApiResponse<RiskResponse>>(`/v1/projects/${selectedProjectId}/risks`, data)
        .then((r) => r.data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risks", selectedProjectId] });
      setShowForm(false);
      setFormData({
        code: "",
        title: "",
        description: "",
        category: "TECHNICAL",
        probability: 1,
        impact: 1,
      });
      setFormError("");
    },
    onError: (err: unknown) => {
      setFormError(getErrorMessage(err, "Failed to create risk"));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (riskId: string) => {
      if (!selectedProjectId) {
        throw new Error("Please select a project first");
      }
      return apiClient.delete(`/v1/projects/${selectedProjectId}/risks/${riskId}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risks", selectedProjectId] });
    },
  });

  const handleFormChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === "probability" || name === "impact" ? parseInt(value, 10) : value,
    }));
  };

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError("");

    if (!formData.code || !formData.title) {
      setFormError("Code and Title are required");
      return;
    }

    createMutation.mutate(formData);
  };

  const columns: ColumnDef<RiskResponse>[] = [
    { key: "code", label: "Code", sortable: true },
    { key: "title", label: "Title", sortable: true },
    { key: "category", label: "Category", sortable: true },
    { key: "probability", label: "Probability", sortable: true },
    { key: "impact", label: "Impact", sortable: true },
    {
      key: "score",
      label: "Score",
      sortable: true,
      render: (value) => {
        if (value === null || value === undefined) {
          return <span className="text-slate-500">-</span>;
        }
        const score = Number(String(value));
        if (isNaN(score)) {
          return <span className="text-slate-500">-</span>;
        }
        let color = "text-emerald-400";
        if (score >= 15) color = "text-red-400";
        else if (score >= 8) color = "text-amber-400";
        return <span className={`font-semibold ${color}`}>{score.toFixed(1)}</span>;
      },
    },
    {
      key: "status",
      label: "Status",
      render: (value) => {
        const statusMap: Record<string, "OPEN" | "MEDIUM" | "CLOSED"> = {
          OPEN: "OPEN",
          MITIGATED: "MEDIUM",
          CLOSED: "CLOSED",
        };
        return <StatusBadge status={statusMap[String(value)] || String(value)} />;
      },
    },
    { key: "owner", label: "Owner", sortable: true },
    {
      key: "id",
      label: "Actions",
      render: (value) => (
        <button
          onClick={() => {
            if (window.confirm("Are you sure you want to delete this risk?")) {
              deleteMutation.mutate(String(value));
            }
          }}
          disabled={deleteMutation.isPending}
          className="text-red-400 hover:text-red-300 disabled:text-slate-500"
        >
          <Trash2 size={16} />
        </button>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Risk Register"
        description="Manage project risks, probabilities, and mitigation strategies"
        actions={
          <button
            onClick={() => setShowForm(!showForm)}
            disabled={!selectedProjectId}
            className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-500"
          >
            <Plus size={16} />
            New Risk
          </button>
        }
      />

      <TabTip
        title="Risk Register"
        description="Identify, assess, and track project risks. Each risk has a probability and impact rating that determines its severity. Add mitigation plans and monitor risk status throughout the project lifecycle."
        steps={["Select a project from the dropdown", "Click 'New Risk' to add a risk entry", "Set probability (LOW/MEDIUM/HIGH) and impact", "The Risk Matrix shows risks plotted by severity"]}
      />

      {/* Project Selector */}
      <div className="mb-6 rounded-xl border border-slate-800 bg-slate-900/50 p-4 shadow-lg">
        <label className="block text-sm font-medium text-slate-300 mb-2">Select Project</label>
        <select
          value={selectedProjectId}
          onChange={(e) => setSelectedProjectId(e.target.value)}
          className="w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
        >
          <option value="">-- Choose a project --</option>
          {(Array.isArray(projectsData) ? projectsData : [])?.map((project) => (
            <option key={project.id} value={project.id}>
              {project.code} - {project.name}
            </option>
          ))}
        </select>
      </div>

      {!selectedProjectId && (
        <div className="rounded-md bg-blue-500/10 p-4 text-sm text-blue-400 mb-6">
          Please select a project above to view and manage risks.
        </div>
      )}

      {/* Triggered Risks Alert */}
      {selectedProjectId && triggersData?.data && triggersData.data.length > 0 && (
        <div className="mb-6 rounded-lg border border-red-500/30 bg-red-500/10 p-4">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-red-400 flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-semibold text-red-400">Active Risk Triggers</h3>
              <p className="text-sm text-red-300 mt-1">
                {triggersData.data.length} risk trigger(s) have been activated and require attention.
              </p>
              <div className="mt-3 space-y-2">
                {triggersData.data.map((trigger) => (
                  <div
                    key={trigger.id}
                    className={`flex items-center justify-between p-2 rounded text-sm ${
                      trigger.escalationLevel === "RED"
                        ? "bg-red-500/30 text-red-300"
                        : trigger.escalationLevel === "AMBER"
                          ? "bg-amber-500/30 text-amber-300"
                          : "bg-blue-500/30 text-blue-300"
                    }`}
                  >
                    <span>
                      <strong>{trigger.triggerType}</strong>: {trigger.triggerCondition}
                    </span>
                    <span className="font-semibold">{trigger.escalationLevel}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* New Risk Form */}
      {showForm && selectedProjectId && (
        <div className="mb-8 rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-white">Create New Risk</h2>
            <button
              onClick={() => setShowForm(false)}
              className="text-slate-400 hover:text-slate-300"
            >
              <X size={20} />
            </button>
          </div>

          {formError && (
            <div className="mb-4 rounded-md bg-red-500/10 p-4 text-sm text-red-400">
              {formError}
            </div>
          )}

          <form onSubmit={handleFormSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-300">Code *</label>
                <input
                  type="text"
                  name="code"
                  value={formData.code}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="e.g., RISK-001"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300">Title *</label>
                <input
                  type="text"
                  name="title"
                  value={formData.title}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="Risk title"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Description</label>
              <textarea
                name="description"
                value={formData.description || ""}
                onChange={handleFormChange}
                rows={2}
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="Risk description"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-300">Category</label>
                <select
                  name="category"
                  value={formData.category}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <optgroup label="General Categories">
                    <option value="TECHNICAL">Technical</option>
                    <option value="RESOURCE">Resource</option>
                    <option value="SCHEDULE">Schedule</option>
                    <option value="COST">Cost</option>
                    <option value="EXTERNAL">External</option>
                    <option value="ORGANIZATIONAL">Organizational</option>
                    <option value="PROJECT_MANAGEMENT">Project Management</option>
                    <option value="QUALITY">Quality</option>
                  </optgroup>
                  <optgroup label="India-Specific Categories">
                    <option value="LAND_ACQUISITION">Land Acquisition</option>
                    <option value="FOREST_CLEARANCE">Forest Clearance</option>
                    <option value="UTILITY_SHIFTING">Utility Shifting</option>
                    <option value="STATUTORY_CLEARANCE">Statutory Clearance</option>
                    <option value="CONTRACTOR_FINANCIAL">Contractor Financial</option>
                    <option value="MONSOON_IMPACT">Monsoon Impact</option>
                    <option value="GEOPOLITICAL">Geopolitical</option>
                    <option value="NATURAL_HAZARD">Natural Hazard</option>
                    <option value="MARKET_PRICE">Market Price</option>
                    <option value="TECHNOLOGY">Technology</option>
                  </optgroup>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-300">Probability (1-5)</label>
                <select
                  name="probability"
                  value={formData.probability}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="1">1 - Very Low</option>
                  <option value="2">2 - Low</option>
                  <option value="3">3 - Medium</option>
                  <option value="4">4 - High</option>
                  <option value="5">5 - Very High</option>
                </select>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">Impact (1-5)</label>
              <select
                name="impact"
                value={formData.impact}
                onChange={handleFormChange}
                className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              >
                <option value="1">1 - Very Low</option>
                <option value="2">2 - Low</option>
                <option value="3">3 - Medium</option>
                <option value="4">4 - High</option>
                <option value="5">5 - Very High</option>
              </select>
            </div>

            <div className="flex gap-3 pt-4">
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:bg-slate-500"
              >
                {createMutation.isPending ? "Creating..." : "Create Risk"}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="rounded-md border border-slate-700 bg-slate-900/50 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800/50"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {selectedProjectId && (
        <>
          {/* Risk Matrix Visualization */}
          <div className="mb-8 rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
            <h2 className="mb-4 text-lg font-semibold text-white">Risk Matrix (5x5)</h2>
            <RiskMatrix risks={risks} />
          </div>

          {isLoading && (
            <div className="py-12 text-center text-slate-500">Loading risks...</div>
          )}

          {error && (
            <div className="rounded-md bg-red-500/10 p-4 text-sm text-red-400">
              Failed to load risks. Is the backend running?
            </div>
          )}

          {!isLoading && risks.length === 0 && (
            <EmptyState
              title="No risks yet"
              description="Create your first risk entry to start managing project risks."
            />
          )}

          {risks.length > 0 && <DataTable columns={columns} data={risks} rowKey="id" />}
        </>
      )}
    </div>
  );
}

function RiskMatrix({ risks }: { risks: RiskResponse[] }) {
  const matrix: number[][] = Array(5)
    .fill(null)
    .map(() => Array(5).fill(0));

  const levelToIndex = (level: string | number): number => {
    if (typeof level === 'number') return Math.min(Math.max(level - 1, 0), 4);
    const map: Record<string, number> = { VERY_LOW: 0, LOW: 1, MEDIUM: 2, HIGH: 3, VERY_HIGH: 4 };
    return map[level] ?? 2;
  };

  risks.forEach((risk) => {
    const probIndex = levelToIndex(risk.probability);
    const impactIndex = levelToIndex(risk.impact);
    matrix[4 - impactIndex][probIndex]++;
  });

  const probLabels = ["Very Low", "Low", "Medium", "High", "Very High"];
  const impactLabels = ["Very High", "High", "Medium", "Low", "Very Low"];

  return (
    <div className="inline-block overflow-x-auto">
      <table className="border-collapse">
        <thead>
          <tr>
            <th className="w-32 border border-slate-700 bg-slate-800 p-2 text-right text-xs font-medium text-slate-400">
              Probability →
            </th>
            {probLabels.map((label) => (
              <th
                key={label}
                className="w-24 border border-slate-700 bg-slate-800 p-2 text-center text-xs font-medium text-slate-400"
              >
                {label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {matrix.map((row, rowIdx) => (
            <tr key={rowIdx}>
              <th className="border border-slate-700 bg-slate-800 p-2 text-left text-xs font-medium text-slate-400">
                {impactLabels[rowIdx]}
              </th>
              {row.map((count, colIdx) => {
                let bgColor = "bg-emerald-500/10";
                if (count > 0) {
                  const riskLevel = (4 - rowIdx) * (colIdx + 1);
                  if (riskLevel >= 15) bgColor = "bg-red-500/20";
                  else if (riskLevel >= 8) bgColor = "bg-amber-500/20";
                  else bgColor = "bg-emerald-500/20";
                }
                return (
                  <td
                    key={`${rowIdx}-${colIdx}`}
                    className={`w-24 border border-slate-700 ${bgColor} p-2 text-center text-sm font-semibold text-slate-300`}
                  >
                    {count > 0 && count}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
