"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import {
  Plus, X, Trash2, AlertTriangle, BookOpen, TrendingDown, TrendingUp, Minus, Eye,
} from "lucide-react";
import { getErrorMessage } from "@/lib/utils/error";
import { riskApi, type RiskResponse, type RiskRag, type CreateRiskRequest } from "@/lib/api/riskApi";
import { riskTriggerApi } from "@/lib/api/riskTriggerApi";
import {
  riskTemplateApi,
  INDUSTRY_LABEL,
  deriveIndustryFromProjectCategory,
  type Industry,
  type RiskTemplate,
} from "@/lib/api/riskTemplateApi";
import { riskCategoryApi } from "@/lib/api/riskCategoryApi";
import { apiClient } from "@/lib/api/client";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import type { ApiResponse, ProjectResponse } from "@/lib/types";
import { AiInsightsPanel } from "@/components/ai/AiInsightsPanel";
import { useAuth } from "@/lib/auth/useAuth";

const RISK_INTERNAL_ROLES = ["ROLE_PMO", "ROLE_PROJECT_MANAGER", "ROLE_ADMIN", "ROLE_FINANCE"] as const;

// Light mode = solid pastel bg + dark text. Dark mode = deep bg + pastel text.
const ragColors: Record<RiskRag, string> = {
  CRIMSON: "bg-fuchsia-100 text-fuchsia-900 dark:bg-fuchsia-900 dark:text-fuchsia-200",
  RED: "bg-red-100 text-red-900 dark:bg-red-900 dark:text-red-200",
  AMBER: "bg-amber-100 text-amber-900 dark:bg-amber-900 dark:text-amber-200",
  GREEN: "bg-emerald-100 text-emerald-900 dark:bg-emerald-900 dark:text-emerald-200",
  OPPORTUNITY: "bg-blue-100 text-blue-900 dark:bg-blue-900 dark:text-blue-200",
};

const trendIcons = {
  WORSENING: TrendingDown,
  STABLE: Minus,
  IMPROVING: TrendingUp,
};

function computeRag(r: Partial<RiskResponse>): RiskRag | null {
  if (r.rag) return r.rag;
  const score = r.riskScore;
  if (score == null) return null;
  if (r.isOpportunity) return "OPPORTUNITY";
  if (score >= 20) return "CRIMSON";
  if (score >= 12) return "RED";
  if (score >= 6) return "AMBER";
  return "GREEN";
}

export default function ProjectRisksPage() {
  const params = useParams();
  const router = useRouter();
  const projectId = params.projectId as string;
  const queryClient = useQueryClient();
  const { hasAnyRole } = useAuth();
  const canSeeRiskInternals = hasAnyRole(RISK_INTERNAL_ROLES);

  const [showForm, setShowForm] = useState(false);
  const [formError, setFormError] = useState("");
  const [showLibrary, setShowLibrary] = useState(false);
  const [selectedTemplateIds, setSelectedTemplateIds] = useState<string[]>([]);
  const [libraryRecommendedOnly, setLibraryRecommendedOnly] = useState(true);
  const [libraryError, setLibraryError] = useState<string | null>(null);

  const [formData, setFormData] = useState<CreateRiskRequest>({
    title: "",
    description: "",
    probability: 1,
    impactCost: 1,
    impactSchedule: 1,
    riskType: "THREAT",
  });

  const { data: projectData } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => apiClient.get<ApiResponse<ProjectResponse>>(`/v1/projects/${projectId}`).then((r) => r.data),
  });

  const { data: risksData, isLoading, error } = useQuery({
    queryKey: ["risks", projectId],
    queryFn: () => riskApi.listRisks(projectId),
  });

  const { data: categoriesData } = useQuery({
    queryKey: ["risk-categories", "active"],
    queryFn: () => riskCategoryApi.listCategories(),
  });
  const categories = categoriesData?.data ?? [];

  const { data: triggersData } = useQuery({
    queryKey: ["risk-triggers", projectId],
    queryFn: () => riskTriggerApi.listTriggeredRisks(projectId),
  });

  const rawRisks = risksData?.data;
  const risks: RiskResponse[] = Array.isArray(rawRisks)
    ? rawRisks
    : ((rawRisks as { content?: RiskResponse[] } | undefined)?.content ?? []);

  const project = projectData?.data ?? null;
  const recommendedIndustry: Industry = deriveIndustryFromProjectCategory(project?.category);
  const projectCategoryFilter = project?.category ?? null;

  const { data: libraryData, isLoading: libraryLoading } = useQuery({
    queryKey: ["risk-templates", "for-project", recommendedIndustry, projectCategoryFilter, libraryRecommendedOnly],
    queryFn: () => riskTemplateApi.list(libraryRecommendedOnly
      ? { industry: recommendedIndustry, projectCategory: projectCategoryFilter ?? undefined, active: true }
      : { active: true }),
    enabled: showLibrary,
  });
  const libraryTemplates: RiskTemplate[] = libraryData?.data ?? [];

  const copyMutation = useMutation({
    mutationFn: (templateIds: string[]) => riskTemplateApi.copyToProject(projectId, templateIds),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risks", projectId] });
      setShowLibrary(false);
      setSelectedTemplateIds([]);
      setLibraryError(null);
    },
    onError: (err: unknown) => {
      setLibraryError(getErrorMessage(err, "Failed to copy templates"));
    },
  });

  const toggleTemplateSelected = (id: string) => {
    setSelectedTemplateIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);
  };

  const createMutation = useMutation({
    mutationFn: (data: CreateRiskRequest) => riskApi.createRisk(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risks", projectId] });
      setShowForm(false);
      setFormData({
        title: "",
        description: "",
        probability: 1,
        impactCost: 1,
        impactSchedule: 1,
        riskType: "THREAT",
      });
      setFormError("");
      toast.success("Risk created");
    },
    onError: (err: unknown) => {
      setFormError(getErrorMessage(err, "Failed to create risk"));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (riskId: string) => riskApi.deleteRisk(projectId, riskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risks", projectId] });
      toast.success("Risk deleted");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to delete risk"));
    },
  });

  const handleFormChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === "probability" || name === "impact" || name === "impactCost" || name === "impactSchedule"
        ? parseInt(value, 10) : value,
    }));
  };

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError("");
    if (!formData.title) {
      setFormError("Title is required");
      return;
    }
    createMutation.mutate(formData);
  };

  const crimsonCount = risks.filter((r) => computeRag(r) === "CRIMSON").length;
  const redCount = risks.filter((r) => computeRag(r) === "RED").length;
  const amberCount = risks.filter((r) => computeRag(r) === "AMBER").length;
  const greenCount = risks.filter((r) => computeRag(r) === "GREEN").length;
  const opportunityCount = risks.filter((r) => computeRag(r) === "OPPORTUNITY").length;

  const columns: ColumnDef<RiskResponse>[] = [
    { key: "code", label: "Code", sortable: true },
    {
      key: "title",
      label: "Title",
      sortable: true,
      render: (_val, row) => (
        <button
          onClick={() => router.push(`/projects/${projectId}/risks/${row.id}`)}
          className="text-accent hover:underline text-left font-medium"
        >
          {row.title}
        </button>
      ),
    },
    {
      key: "riskType",
      label: "Type",
      render: (val) => (
        <span
          className={`px-2 py-0.5 rounded text-xs font-medium ${
            val === "OPPORTUNITY"
              ? "bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-200"
              : "bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-200"
          }`}
        >
          {val === "OPPORTUNITY" ? "Opportunity" : "Threat"}
        </span>
      ),
    },
    {
      key: "category",
      label: "Category",
      sortable: true,
      // Backend returns RiskCategorySummary { code, name, ... } or null.
      render: (_val, row) => {
        const cat = row.category;
        if (!cat) return <span className="text-text-muted">—</span>;
        return (
          <span title={cat.name} className="text-text-primary">
            {cat.code}
          </span>
        );
      },
    },
    ...(canSeeRiskInternals ? [
      { key: "probability" as const, label: "Probability", sortable: true },
      { key: "impactCost" as const, label: "IC", sortable: true },
      { key: "impactSchedule" as const, label: "IS", sortable: true },
      {
        key: "riskScore" as const,
        label: "Score",
        sortable: true,
        render: (val: unknown) => {
          const score = Number(String(val));
          if (isNaN(score)) return <span className="text-text-muted">-</span>;
          let color = "text-success";
          if (score >= 15) color = "text-danger";
          else if (score >= 8) color = "text-warning";
          return <span className={`font-semibold ${color}`}>{score.toFixed(0)}</span>;
        },
      },
    ] : []),
    {
      key: "rag",
      label: "RAG",
      render: (_val, row) => {
        const rag = computeRag(row);
        if (!rag) return <span className="text-text-muted">—</span>;
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
      render: (val) => {
        const trend = String(val);
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
      render: (val) => {
        const status = String(val).replace(/_/g, " ");
        return <span className="text-xs">{status}</span>;
      },
    },
    {
      key: "id",
      label: "Actions",
      render: (val) => (
        <div className="flex items-center gap-2">
          <button
            onClick={() => router.push(`/projects/${projectId}/risks/${String(val)}`)}
            className="text-text-secondary hover:text-accent"
            title="View details"
          >
            <Eye size={14} />
          </button>
          <button
            onClick={() => {
              if (window.confirm("Are you sure you want to delete this risk?")) {
                deleteMutation.mutate(String(val));
              }
            }}
            disabled={deleteMutation.isPending}
            className="text-danger hover:text-danger disabled:text-text-muted"
            title="Delete"
          >
            <Trash2 size={14} />
          </button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <AiInsightsPanel
        projectId={projectId}
        endpoint={`/v1/projects/${projectId}/risks/ai/insights`}
      />
      <PageHeader
        title="Risk Register"
        description="Manage project risks, probabilities, and mitigation strategies"
        actions={
          <div className="flex items-center gap-2">
            <button
              onClick={() => {
                setSelectedTemplateIds([]);
                setLibraryError(null);
                setShowLibrary(true);
              }}
              className="inline-flex items-center gap-2 rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-primary hover:bg-surface-hover"
            >
              <BookOpen size={16} />
              Add from Library
            </button>
            <button
              onClick={() => setShowForm(!showForm)}
              className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
            >
              <Plus size={16} />
              New Risk
            </button>
          </div>
        }
      />

      {/* RAG Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        <div className="rounded-lg border border-fuchsia-300 bg-fuchsia-50 p-4 dark:border-fuchsia-800 dark:bg-fuchsia-900/20">
          <p className="text-xs text-text-secondary uppercase">Crimson</p>
          <p className="text-2xl font-bold text-fuchsia-700 dark:text-fuchsia-300">{crimsonCount}</p>
        </div>
        <div className="rounded-lg border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-danger/10">
          <p className="text-xs text-text-secondary uppercase">Red</p>
          <p className="text-2xl font-bold text-red-700 dark:text-danger">{redCount}</p>
        </div>
        <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 dark:border-amber-800 dark:bg-warning/10">
          <p className="text-xs text-text-secondary uppercase">Amber</p>
          <p className="text-2xl font-bold text-amber-700 dark:text-warning">{amberCount}</p>
        </div>
        <div className="rounded-lg border border-emerald-300 bg-emerald-50 p-4 dark:border-emerald-800 dark:bg-success/10">
          <p className="text-xs text-text-secondary uppercase">Green</p>
          <p className="text-2xl font-bold text-emerald-700 dark:text-success">{greenCount}</p>
        </div>
        <div className="rounded-lg border border-blue-300 bg-blue-50 p-4 dark:border-blue-800 dark:bg-blue-900/20">
          <p className="text-xs text-text-secondary uppercase">Opportunities</p>
          <p className="text-2xl font-bold text-blue-700 dark:text-blue-300">{opportunityCount}</p>
        </div>
      </div>

      {/* Triggered Risks Alert */}
      {triggersData?.data && triggersData.data.length > 0 && (
        <div className="rounded-lg border border-danger/30 bg-danger/10 p-4">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-danger flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-semibold text-danger">Active Risk Triggers</h3>
              <p className="text-sm text-danger mt-1">
                {triggersData.data.length} risk trigger(s) have been activated and require attention.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Create Form */}
      {showForm && (
        <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-text-primary">Create New Risk</h2>
            <button onClick={() => setShowForm(false)} className="text-text-secondary hover:text-text-primary">
              <X size={20} />
            </button>
          </div>

          {formError && (
            <div className="mb-4 rounded-md bg-danger/10 p-4 text-sm text-danger">{formError}</div>
          )}

          <form onSubmit={handleFormSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-text-secondary">Title *</label>
                <input
                  type="text"
                  name="title"
                  value={formData.title}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  placeholder="Risk title"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary">Type</label>
                <select
                  name="riskType"
                  value={formData.riskType}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                >
                  <option value="THREAT">Threat</option>
                  <option value="OPPORTUNITY">Opportunity</option>
                </select>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary">Description</label>
              <textarea
                name="description"
                value={formData.description || ""}
                onChange={handleFormChange}
                rows={2}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                placeholder="Risk description"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary">Category</label>
              <select
                name="categoryId"
                value={formData.categoryId || ""}
                onChange={(e) =>
                  setFormData((prev) => ({
                    ...prev,
                    categoryId: e.target.value || undefined,
                  }))
                }
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              >
                <option value="">— Uncategorised —</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.code} — {c.name}
                  </option>
                ))}
              </select>
              {categories.length === 0 && (
                <p className="mt-1 text-xs text-text-muted">
                  No risk categories defined. Configure them under Admin → Risk Categories.
                </p>
              )}
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium text-text-secondary">Probability (1-5)</label>
                <select
                  name="probability"
                  value={formData.probability}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                >
                  <option value="1">1 - Very Low</option>
                  <option value="2">2 - Low</option>
                  <option value="3">3 - Medium</option>
                  <option value="4">4 - High</option>
                  <option value="5">5 - Very High</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary">Cost Impact (1-5)</label>
                <select
                  name="impactCost"
                  value={formData.impactCost}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                >
                  <option value="1">1 - Very Low</option>
                  <option value="2">2 - Low</option>
                  <option value="3">3 - Medium</option>
                  <option value="4">4 - High</option>
                  <option value="5">5 - Very High</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary">Schedule Impact (1-5)</label>
                <select
                  name="impactSchedule"
                  value={formData.impactSchedule}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                >
                  <option value="1">1 - Very Low</option>
                  <option value="2">2 - Low</option>
                  <option value="3">3 - Medium</option>
                  <option value="4">4 - High</option>
                  <option value="5">5 - Very High</option>
                </select>
              </div>
            </div>

            <div className="flex gap-3 pt-4">
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                {createMutation.isPending ? "Creating..." : "Create Risk"}
              </button>
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Risk Table */}
      {isLoading ? (
        <div className="text-center py-12 text-text-secondary">Loading risks...</div>
      ) : error ? (
        <div className="rounded-md bg-danger/10 p-4 text-sm text-danger">
          Failed to load risks. Is the backend running?
        </div>
      ) : risks.length === 0 ? (
        <EmptyState
          icon={AlertTriangle}
          title="No Risks Found"
          description="Add risks and opportunities to this project's register"
        />
      ) : (
        <DataTable columns={columns} data={risks} rowKey="id" />
      )}

      {/* Library Modal */}
      {showLibrary && (
        <LibraryModal
          templates={libraryTemplates}
          loading={libraryLoading}
          recommendedIndustry={recommendedIndustry}
          recommendedOnly={libraryRecommendedOnly}
          onToggleRecommendedOnly={() => setLibraryRecommendedOnly((v) => !v)}
          selectedIds={selectedTemplateIds}
          onToggleId={toggleTemplateSelected}
          onCopy={() => copyMutation.mutate(selectedTemplateIds)}
          onClose={() => setShowLibrary(false)}
          isCopying={copyMutation.isPending}
          error={libraryError}
        />
      )}
    </div>
  );
}

function LibraryModal({
  templates, loading, recommendedIndustry, recommendedOnly, onToggleRecommendedOnly,
  selectedIds, onToggleId, onCopy, onClose, isCopying, error,
}: {
  templates: RiskTemplate[];
  loading: boolean;
  recommendedIndustry: Industry;
  recommendedOnly: boolean;
  onToggleRecommendedOnly: () => void;
  selectedIds: string[];
  onToggleId: (id: string) => void;
  onCopy: () => void;
  onClose: () => void;
  isCopying: boolean;
  error: string | null;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="w-full max-w-3xl max-h-[85vh] overflow-hidden rounded-xl border border-border bg-surface shadow-xl flex flex-col">
        <div className="flex items-center justify-between border-b border-border p-4">
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Add Risks from Library</h2>
            <p className="text-xs text-text-muted mt-0.5">
              {recommendedOnly
                ? `Showing risks recommended for ${INDUSTRY_LABEL[recommendedIndustry]} projects.`
                : "Showing all active risks."}
            </p>
          </div>
          <button onClick={onClose} className="text-text-secondary hover:text-text-primary">
            <X size={20} />
          </button>
        </div>

        <div className="flex items-center gap-3 border-b border-border bg-surface/40 px-4 py-2 text-sm">
          <button
            type="button"
            onClick={onToggleRecommendedOnly}
            className={`rounded px-3 py-1 ${recommendedOnly ? "bg-accent text-text-primary" : "bg-surface-hover text-text-secondary"}`}
          >
            Recommended
          </button>
          <button
            type="button"
            onClick={onToggleRecommendedOnly}
            className={`rounded px-3 py-1 ${!recommendedOnly ? "bg-accent text-text-primary" : "bg-surface-hover text-text-secondary"}`}
          >
            All
          </button>
          <span className="ml-auto text-xs text-text-muted">{selectedIds.length} selected</span>
        </div>

        {error && <div className="px-4 py-2 text-sm text-danger">{error}</div>}

        <div className="flex-1 overflow-y-auto p-4">
          {loading && <div className="text-text-muted py-6 text-center">Loading library…</div>}
          {!loading && templates.length === 0 && (
            <div className="text-text-muted py-6 text-center">No matching templates.</div>
          )}
          {!loading && templates.length > 0 && (
            <ul className="space-y-2">
              {templates.map((t) => {
                const checked = selectedIds.includes(t.id);
                return (
                  <li
                    key={t.id}
                    className={`flex gap-3 rounded border p-3 cursor-pointer ${
                      checked ? "border-accent bg-accent/10" : "border-border bg-surface/40 hover:bg-surface-hover/30"
                    }`}
                    onClick={() => onToggleId(t.id)}
                  >
                    <input type="checkbox" checked={checked} onChange={() => onToggleId(t.id)} onClick={(e) => e.stopPropagation()} className="mt-1" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 text-sm">
                        <span className="font-mono text-xs text-text-muted">{t.code}</span>
                        <span className="font-medium text-text-primary">{t.title}</span>
                      </div>
                      {t.description && <p className="mt-1 text-xs text-text-secondary">{t.description}</p>}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border p-4">
          <button type="button" onClick={onClose} className="rounded border border-border bg-surface/50 px-4 py-2 text-sm text-text-secondary hover:bg-surface-hover">
            Cancel
          </button>
          <button
            type="button"
            disabled={selectedIds.length === 0 || isCopying}
            onClick={onCopy}
            className="rounded bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
          >
            {isCopying ? "Copying…" : selectedIds.length === 0 ? "Select risks to copy" : `Copy ${selectedIds.length} selected`}
          </button>
        </div>
      </div>
    </div>
  );
}
