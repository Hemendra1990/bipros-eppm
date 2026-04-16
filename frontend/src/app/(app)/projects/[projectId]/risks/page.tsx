"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { getErrorMessage } from "@/lib/utils/error";
import { riskApi } from "@/lib/api/riskApi";
import type { RiskResponse, RiskRag, CreateRiskRequest } from "@/lib/api/riskApi";
import { PageHeader } from "@/components/common/PageHeader";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/common/EmptyState";
import { AlertTriangle, Plus, TrendingDown, TrendingUp, Minus } from "lucide-react";

const ragColors: Record<RiskRag, string> = {
  CRIMSON: "bg-fuchsia-900 text-fuchsia-200",
  RED: "bg-red-900 text-red-200",
  AMBER: "bg-amber-900 text-amber-200",
  GREEN: "bg-emerald-900 text-emerald-200",
  OPPORTUNITY: "bg-blue-900 text-blue-200",
};

const trendIcons = {
  WORSENING: TrendingDown,
  STABLE: Minus,
  IMPROVING: TrendingUp,
};

const columns: ColumnDef<RiskResponse>[] = [
  { key: "code", label: "Code" },
  { key: "title", label: "Title" },
  { key: "category", label: "Category" },
  {
    key: "riskScore",
    label: "Score",
    render: (val: unknown) => {
      const score = val as number;
      return <span className="font-mono font-bold">{score?.toFixed(0)}</span>;
    },
  },
  {
    key: "rag",
    label: "RAG",
    render: (val: unknown) => {
      const rag = val as RiskRag;
      return (
        <span className={`px-2 py-1 rounded text-xs font-bold ${ragColors[rag] || ""}`}>
          {rag}
        </span>
      );
    },
  },
  {
    key: "trend",
    label: "Trend",
    render: (val: unknown) => {
      const trend = val as string;
      const Icon = trendIcons[trend as keyof typeof trendIcons] || Minus;
      return (
        <span className="flex items-center gap-1 text-sm">
          <Icon className="w-4 h-4" />
          {trend}
        </span>
      );
    },
  },
  {
    key: "status",
    label: "Status",
    render: (val: unknown) => {
      const status = (val as string).replace(/_/g, " ");
      return <span className="text-xs">{status}</span>;
    },
  },
  {
    key: "residualRiskScore",
    label: "Residual",
    render: (val: unknown) => {
      const score = val as number;
      return <span className="font-mono text-slate-400">{score?.toFixed(1)}</span>;
    },
  },
];

export default function RisksPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [formData, setFormData] = useState<CreateRiskRequest>({
    code: "",
    title: "",
    category: "TECHNICAL",
    probability: 1,
    impactCost: 1,
    impactSchedule: 1,
  });
  const queryClient = useQueryClient();

  const { data: risks = [], isLoading } = useQuery({
    queryKey: ["risks", projectId],
    queryFn: () => riskApi.getRisksByProject(projectId),
    select: (response) => response.data?.content || [],
  });

  const createMutation = useMutation({
    mutationFn: (data: CreateRiskRequest) => riskApi.createRisk(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risks", projectId] });
      setShowCreateForm(false);
      toast.success("Risk created");
    },
    onError: (err) => toast.error(getErrorMessage(err)),
  });

  const crimsonCount = risks.filter((r: RiskResponse) => r.rag === "CRIMSON").length;
  const redCount = risks.filter((r: RiskResponse) => r.rag === "RED").length;
  const opportunityCount = risks.filter((r: RiskResponse) => r.rag === "OPPORTUNITY").length;

  return (
    <div className="space-y-6">
      <PageHeader
        title="Risk Register"
        description="IC-PMS M7 — risk and opportunity register with RAG scoring"
      />

      {/* Summary cards */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        <div className="bg-fuchsia-900/20 rounded-lg border border-fuchsia-800 p-4">
          <p className="text-xs text-slate-400 uppercase">Crimson</p>
          <p className="text-2xl font-bold text-fuchsia-300">{crimsonCount}</p>
        </div>
        <div className="bg-red-900/20 rounded-lg border border-red-800 p-4">
          <p className="text-xs text-slate-400 uppercase">Red</p>
          <p className="text-2xl font-bold text-red-300">{redCount}</p>
        </div>
        <div className="bg-amber-900/20 rounded-lg border border-amber-800 p-4">
          <p className="text-xs text-slate-400 uppercase">Amber</p>
          <p className="text-2xl font-bold text-amber-300">
            {risks.filter((r: RiskResponse) => r.rag === "AMBER").length}
          </p>
        </div>
        <div className="bg-emerald-900/20 rounded-lg border border-emerald-800 p-4">
          <p className="text-xs text-slate-400 uppercase">Green</p>
          <p className="text-2xl font-bold text-emerald-300">
            {risks.filter((r: RiskResponse) => r.rag === "GREEN").length}
          </p>
        </div>
        <div className="bg-blue-900/20 rounded-lg border border-blue-800 p-4">
          <p className="text-xs text-slate-400 uppercase">Opportunities</p>
          <p className="text-2xl font-bold text-blue-300">{opportunityCount}</p>
        </div>
      </div>

      {/* Actions */}
      <div className="flex justify-end">
        <Button onClick={() => setShowCreateForm(!showCreateForm)}>
          <Plus className="w-4 h-4 mr-2" />
          Add Risk
        </Button>
      </div>

      {/* Create Form */}
      {showCreateForm && (
        <div className="bg-slate-900/50 rounded-lg border border-slate-800 p-6 space-y-4">
          <h3 className="text-lg font-semibold text-white">New Risk</h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm text-slate-400 mb-1">Code</label>
              <input
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded text-sm text-white"
                value={formData.code}
                onChange={(e) => setFormData({ ...formData, code: e.target.value })}
                placeholder="DMIC-RSK-025"
              />
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Title</label>
              <input
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded text-sm text-white"
                value={formData.title}
                onChange={(e) => setFormData({ ...formData, title: e.target.value })}
              />
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Category</label>
              <select
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded text-sm text-white"
                value={formData.category}
                onChange={(e) => setFormData({ ...formData, category: e.target.value as CreateRiskRequest["category"] })}
              >
                {["TECHNICAL","COMMERCIAL","ENVIRONMENTAL","REGULATORY","FINANCIAL","SCHEDULE","SAFETY","POLITICAL","SOCIAL","LAND_ACQUISITION","SUPPLY_CHAIN","DESIGN","CONSTRUCTION","GEOTECHNICAL","MONSOON_WEATHER"].map((c) => (
                  <option key={c} value={c}>{c.replace(/_/g, " ")}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Probability (1-5)</label>
              <input
                type="number"
                min={1}
                max={5}
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded text-sm text-white"
                value={formData.probability}
                onChange={(e) => setFormData({ ...formData, probability: +e.target.value })}
              />
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Impact Cost (1-5)</label>
              <input
                type="number"
                min={1}
                max={5}
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded text-sm text-white"
                value={formData.impactCost}
                onChange={(e) => setFormData({ ...formData, impactCost: +e.target.value })}
              />
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Impact Schedule (1-5)</label>
              <input
                type="number"
                min={1}
                max={5}
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded text-sm text-white"
                value={formData.impactSchedule}
                onChange={(e) => setFormData({ ...formData, impactSchedule: +e.target.value })}
              />
            </div>
          </div>
          <div className="flex gap-2">
            <Button onClick={() => createMutation.mutate(formData)} disabled={createMutation.isPending}>
              {createMutation.isPending ? "Creating..." : "Create"}
            </Button>
            <Button variant="outline" onClick={() => setShowCreateForm(false)}>Cancel</Button>
          </div>
        </div>
      )}

      {/* Risk Table */}
      {isLoading ? (
        <div className="text-center py-12 text-slate-400">Loading risks...</div>
      ) : risks.length === 0 ? (
        <EmptyState
          icon={AlertTriangle}
          title="No Risks Found"
          description="Add risks and opportunities to this project's register"
        />
      ) : (
        <DataTable columns={columns} data={risks} rowKey="id" />
      )}
    </div>
  );
}
