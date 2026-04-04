"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, X, Trash2 } from "lucide-react";
import { riskApi } from "@/lib/api/riskApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { EmptyState } from "@/components/common/EmptyState";
import type { RiskResponse, CreateRiskRequest } from "@/lib/types";

export default function RiskPage() {
  const queryClient = useQueryClient();
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

  const { data: risksData, isLoading, error } = useQuery({
    queryKey: ["risks"],
    queryFn: () => riskApi.listRisks(undefined, 0, 50),
  });

  const rawRisks = risksData?.data;
  const risks = Array.isArray(rawRisks) ? rawRisks : (rawRisks as any)?.content ?? [];

  const createMutation = useMutation({
    mutationFn: (data: CreateRiskRequest) => riskApi.createRisk(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risks"] });
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
    onError: (err) => {
      setFormError(err instanceof Error ? err.message : "Failed to create risk");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (riskId: string) => riskApi.deleteRisk(riskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risks"] });
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
        const score = Number(String(value));
        let color = "text-green-700";
        if (score >= 15) color = "text-red-700";
        else if (score >= 8) color = "text-orange-700";
        return <span className={`font-semibold ${color}`}>{score}</span>;
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
          className="text-red-600 hover:text-red-700 disabled:text-gray-400"
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
            className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            <Plus size={16} />
            New Risk
          </button>
        }
      />

      {/* New Risk Form */}
      {showForm && (
        <div className="mb-8 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-gray-900">Create New Risk</h2>
            <button
              onClick={() => setShowForm(false)}
              className="text-gray-500 hover:text-gray-700"
            >
              <X size={20} />
            </button>
          </div>

          {formError && (
            <div className="mb-4 rounded-md bg-red-50 p-4 text-sm text-red-700">
              {formError}
            </div>
          )}

          <form onSubmit={handleFormSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700">Code *</label>
                <input
                  type="text"
                  name="code"
                  value={formData.code}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="e.g., RISK-001"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">Title *</label>
                <input
                  type="text"
                  name="title"
                  value={formData.title}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                  placeholder="Risk title"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700">Description</label>
              <textarea
                name="description"
                value={formData.description || ""}
                onChange={handleFormChange}
                rows={2}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="Risk description"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700">Category</label>
                <select
                  name="category"
                  value={formData.category}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="TECHNICAL">Technical</option>
                  <option value="RESOURCE">Resource</option>
                  <option value="SCHEDULE">Schedule</option>
                  <option value="FINANCIAL">Financial</option>
                  <option value="EXTERNAL">External</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700">Probability (1-5)</label>
                <select
                  name="probability"
                  value={formData.probability}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
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
              <label className="block text-sm font-medium text-gray-700">Impact (1-5)</label>
              <select
                name="impact"
                value={formData.impact}
                onChange={handleFormChange}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
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
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400"
              >
                {createMutation.isPending ? "Creating..." : "Create Risk"}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="rounded-md bg-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-300"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Risk Matrix Visualization */}
      <div className="mb-8 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h2 className="mb-4 text-lg font-semibold text-gray-900">Risk Matrix (5x5)</h2>
        <RiskMatrix risks={risks} />
      </div>

      {isLoading && (
        <div className="py-12 text-center text-gray-500">Loading risks...</div>
      )}

      {error && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
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
    </div>
  );
}

function RiskMatrix({ risks }: { risks: RiskResponse[] }) {
  const matrix: number[][] = Array(5)
    .fill(null)
    .map(() => Array(5).fill(0));

  risks.forEach((risk) => {
    const probIndex = Math.min(Math.max(risk.probability - 1, 0), 4);
    const impactIndex = Math.min(Math.max(risk.impact - 1, 0), 4);
    matrix[4 - impactIndex][probIndex]++;
  });

  const probLabels = ["Very Low", "Low", "Medium", "High", "Very High"];
  const impactLabels = ["Very High", "High", "Medium", "Low", "Very Low"];

  return (
    <div className="inline-block overflow-x-auto">
      <table className="border-collapse">
        <thead>
          <tr>
            <th className="w-32 border border-gray-200 bg-gray-50 p-2 text-right text-xs font-medium text-gray-700">
              Probability →
            </th>
            {probLabels.map((label) => (
              <th
                key={label}
                className="w-24 border border-gray-200 bg-gray-50 p-2 text-center text-xs font-medium text-gray-700"
              >
                {label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {matrix.map((row, rowIdx) => (
            <tr key={rowIdx}>
              <th className="border border-gray-200 bg-gray-50 p-2 text-left text-xs font-medium text-gray-700">
                {impactLabels[rowIdx]}
              </th>
              {row.map((count, colIdx) => {
                let bgColor = "bg-green-50";
                if (count > 0) {
                  const riskLevel = (4 - rowIdx) * (colIdx + 1);
                  if (riskLevel >= 15) bgColor = "bg-red-100";
                  else if (riskLevel >= 8) bgColor = "bg-yellow-100";
                  else bgColor = "bg-green-100";
                }
                return (
                  <td
                    key={`${rowIdx}-${colIdx}`}
                    className={`w-24 border border-gray-200 ${bgColor} p-2 text-center text-sm font-semibold text-gray-900`}
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
