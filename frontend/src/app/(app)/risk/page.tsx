"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, X, Trash2, AlertTriangle, BookOpen } from "lucide-react";
import { getErrorMessage } from "@/lib/utils/error";
import { riskApi, type RiskResponse } from "@/lib/api/riskApi";
import { riskTriggerApi } from "@/lib/api/riskTriggerApi";
import {
  riskTemplateApi,
  INDUSTRY_LABEL,
  deriveIndustryFromProjectCategory,
  type Industry,
  type RiskTemplate,
} from "@/lib/api/riskTemplateApi";
import { apiClient } from "@/lib/api/client";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusBadge } from "@/components/common/StatusBadge";
import { EmptyState } from "@/components/common/EmptyState";
import { TabTip } from "@/components/common/TabTip";
import type { CreateRiskRequest, ProjectResponse, ApiResponse, PagedResponse } from "@/lib/types";

export default function RiskPage() {
  const queryClient = useQueryClient();
  const [selectedProjectId, setSelectedProjectId] = useState<string>("");
  const [showForm, setShowForm] = useState(false);
  const [formError, setFormError] = useState("");
  const [showLibrary, setShowLibrary] = useState(false);
  const [selectedTemplateIds, setSelectedTemplateIds] = useState<string[]>([]);
  const [libraryRecommendedOnly, setLibraryRecommendedOnly] = useState(true);
  const [libraryError, setLibraryError] = useState<string | null>(null);

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
    queryFn: () => riskApi.getRisksByProject(selectedProjectId),
    enabled: !!selectedProjectId,
  });

  const { data: triggersData } = useQuery({
    queryKey: ["risk-triggers", selectedProjectId],
    queryFn: () => riskTriggerApi.listTriggeredRisks(selectedProjectId),
    enabled: !!selectedProjectId,
  });

  const rawRisks = risksData?.data;
  const risks: RiskResponse[] = Array.isArray(rawRisks)
    ? rawRisks
    : ((rawRisks as { content?: RiskResponse[] } | undefined)?.content ?? []);

  const projects: ProjectResponse[] = Array.isArray(projectsData) ? projectsData : [];
  const selectedProject = projects.find((p) => p.id === selectedProjectId) ?? null;
  const recommendedIndustry: Industry = deriveIndustryFromProjectCategory(selectedProject?.category);
  const projectCategoryFilter = selectedProject?.category ?? null;

  const { data: libraryData, isLoading: libraryLoading } = useQuery({
    queryKey: ["risk-templates", "for-project", recommendedIndustry, projectCategoryFilter, libraryRecommendedOnly],
    queryFn: () => riskTemplateApi.list(libraryRecommendedOnly
      ? { industry: recommendedIndustry, projectCategory: projectCategoryFilter ?? undefined, active: true }
      : { active: true }),
    enabled: showLibrary,
  });
  const libraryTemplates: RiskTemplate[] = libraryData?.data ?? [];

  const copyMutation = useMutation({
    mutationFn: (templateIds: string[]) => {
      if (!selectedProjectId) throw new Error("Please select a project first");
      return riskTemplateApi.copyToProject(selectedProjectId, templateIds);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risks", selectedProjectId] });
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
          return <span className="text-text-muted">-</span>;
        }
        const score = Number(String(value));
        if (isNaN(score)) {
          return <span className="text-text-muted">-</span>;
        }
        let color = "text-success";
        if (score >= 15) color = "text-danger";
        else if (score >= 8) color = "text-warning";
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
      key: "analysisQuality",
      label: "Analysis",
      render: (_value, row) => <AnalysisQualityBadge quality={row.analysisQuality} />,
    },
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
          className="text-danger hover:text-danger disabled:text-text-muted"
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
          <div className="flex items-center gap-2">
            <button
              onClick={() => {
                setSelectedTemplateIds([]);
                setLibraryError(null);
                setShowLibrary(true);
              }}
              disabled={!selectedProjectId}
              className="inline-flex items-center gap-2 rounded-md border border-border bg-surface/50 px-4 py-2 text-sm font-medium text-text-primary hover:bg-surface-hover disabled:opacity-50"
            >
              <BookOpen size={16} />
              Add from Library
            </button>
            <button
              onClick={() => setShowForm(!showForm)}
              disabled={!selectedProjectId}
              className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
            >
              <Plus size={16} />
              New Risk
            </button>
          </div>
        }
      />

      <TabTip
        title="Risk Register"
        description="Identify, assess, and track project risks. Each risk has a probability and impact rating that determines its severity. Add mitigation plans and monitor risk status throughout the project lifecycle."
        steps={["Select a project from the dropdown", "Click 'New Risk' to add a risk entry", "Set probability (LOW/MEDIUM/HIGH) and impact", "The Risk Matrix shows risks plotted by severity"]}
      />

      {/* Project Selector */}
      <div className="mb-6 rounded-xl border border-border bg-surface/50 p-4 shadow-lg">
        <label className="block text-sm font-medium text-text-secondary mb-2">Select Project</label>
        <select
          value={selectedProjectId}
          onChange={(e) => setSelectedProjectId(e.target.value)}
          className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
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
        <div className="rounded-md bg-accent/10 p-4 text-sm text-accent mb-6">
          Please select a project above to view and manage risks.
        </div>
      )}

      {/* Triggered Risks Alert */}
      {selectedProjectId && triggersData?.data && triggersData.data.length > 0 && (
        <div className="mb-6 rounded-lg border border-danger/30 bg-danger/10 p-4">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-danger flex-shrink-0 mt-0.5" />
            <div>
              <h3 className="font-semibold text-danger">Active Risk Triggers</h3>
              <p className="text-sm text-danger mt-1">
                {triggersData.data.length} risk trigger(s) have been activated and require attention.
              </p>
              <div className="mt-3 space-y-2">
                {triggersData.data.map((trigger) => (
                  <div
                    key={trigger.id}
                    className={`flex items-center justify-between p-2 rounded text-sm ${
                      trigger.escalationLevel === "RED"
                        ? "bg-red-500/30 text-danger"
                        : trigger.escalationLevel === "AMBER"
                          ? "bg-amber-500/30 text-warning"
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
        <div className="mb-8 rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-text-primary">Create New Risk</h2>
            <button
              onClick={() => setShowForm(false)}
              className="text-text-secondary hover:text-text-secondary"
            >
              <X size={20} />
            </button>
          </div>

          {formError && (
            <div className="mb-4 rounded-md bg-danger/10 p-4 text-sm text-danger">
              {formError}
            </div>
          )}

          <form onSubmit={handleFormSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-text-secondary">Code *</label>
                <input
                  type="text"
                  name="code"
                  value={formData.code}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  placeholder="e.g., RISK-001"
                />
              </div>

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

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-text-secondary">Category</label>
                <select
                  name="category"
                  value={formData.category}
                  onChange={handleFormChange}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
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
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary">Impact (1-5)</label>
              <select
                name="impact"
                value={formData.impact}
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

      {selectedProjectId && (
        <>
          {/* Risk Matrix Visualization */}
          <div className="mb-8 rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
            <h2 className="mb-4 text-lg font-semibold text-text-primary">Risk Matrix (5x5)</h2>
            <RiskMatrix risks={risks} />
          </div>

          {isLoading && (
            <div className="py-12 text-center text-text-muted">Loading risks...</div>
          )}

          {error && (
            <div className="rounded-md bg-danger/10 p-4 text-sm text-danger">
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

      {showLibrary && selectedProjectId && (
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

function AnalysisQualityBadge({
  quality,
}: {
  quality?: { level: "NOT_ANALYSED" | "PARTIALLY_ANALYSED" | "WELL_ANALYSED"; score: number; criteria: Record<string, boolean> };
}) {
  if (!quality) return <span className="text-text-muted">—</span>;
  const styles: Record<string, string> = {
    WELL_ANALYSED: "bg-green-500/20 text-green-400",
    PARTIALLY_ANALYSED: "bg-amber-500/20 text-amber-400",
    NOT_ANALYSED: "bg-red-500/20 text-red-400",
  };
  const labels: Record<string, string> = {
    WELL_ANALYSED: "Well analysed",
    PARTIALLY_ANALYSED: "Partially analysed",
    NOT_ANALYSED: "Not analysed",
  };
  const missing = Object.entries(quality.criteria)
    .filter(([, v]) => !v)
    .map(([k]) => CRITERION_LABEL[k] ?? k);
  const tooltip = missing.length > 0
    ? `${quality.score}/4 — missing: ${missing.join(", ")}`
    : `${quality.score}/4 — all criteria met`;
  return (
    <span
      title={tooltip}
      className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-medium ${styles[quality.level]}`}
    >
      {labels[quality.level]} {quality.score}/4
    </span>
  );
}

const CRITERION_LABEL: Record<string, string> = {
  hasOwner: "owner",
  hasRating: "P/I rating",
  hasDescription: "description ≥ 50 chars",
  hasResponse: "response with type+responsible",
};

function LibraryModal({
  templates,
  loading,
  recommendedIndustry,
  recommendedOnly,
  onToggleRecommendedOnly,
  selectedIds,
  onToggleId,
  onCopy,
  onClose,
  isCopying,
  error,
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
                ? `Showing risks recommended for ${INDUSTRY_LABEL[recommendedIndustry]} projects of this category.`
                : "Showing all active risks across every industry."}
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
            className={`rounded px-3 py-1 ${
              recommendedOnly ? "bg-accent text-text-primary" : "bg-surface-hover text-text-secondary"
            }`}
          >
            Recommended
          </button>
          <button
            type="button"
            onClick={onToggleRecommendedOnly}
            className={`rounded px-3 py-1 ${
              !recommendedOnly ? "bg-accent text-text-primary" : "bg-surface-hover text-text-secondary"
            }`}
          >
            All
          </button>
          <span className="ml-auto text-xs text-text-muted">{selectedIds.length} selected</span>
        </div>

        {error && <div className="px-4 py-2 text-sm text-danger">{error}</div>}

        <div className="flex-1 overflow-y-auto p-4">
          {loading && <div className="text-text-muted py-6 text-center">Loading library…</div>}
          {!loading && templates.length === 0 && (
            <div className="text-text-muted py-6 text-center">
              No matching templates. Try toggling &ldquo;All&rdquo; above.
            </div>
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
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => onToggleId(t.id)}
                      onClick={(e) => e.stopPropagation()}
                      className="mt-1"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 text-sm">
                        <span className="font-mono text-xs text-text-muted">{t.code}</span>
                        <span className="font-medium text-text-primary">{t.title}</span>
                        <span className="ml-auto text-xs text-text-muted">
                          P{t.defaultProbability ?? "—"} / IC{t.defaultImpactCost ?? "—"} / IS{t.defaultImpactSchedule ?? "—"}
                        </span>
                      </div>
                      {t.description && (
                        <p className="mt-1 text-xs text-text-secondary">{t.description}</p>
                      )}
                      <div className="mt-1 flex flex-wrap gap-2 text-[10px] uppercase tracking-wide text-text-muted">
                        <span>{INDUSTRY_LABEL[t.industry]}</span>
                        {t.category && <span>· {t.category}</span>}
                        {t.applicableProjectCategories.length > 0 && (
                          <span>· {t.applicableProjectCategories.join(", ")}</span>
                        )}
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-border p-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded border border-border bg-surface/50 px-4 py-2 text-sm text-text-secondary hover:bg-surface-hover"
          >
            Cancel
          </button>
          <button
            type="button"
            disabled={selectedIds.length === 0 || isCopying}
            onClick={onCopy}
            className="rounded bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50"
          >
            {isCopying
              ? "Copying…"
              : selectedIds.length === 0
                ? "Select risks to copy"
                : `Copy ${selectedIds.length} selected`}
          </button>
        </div>
      </div>
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
            <th className="w-32 border border-border bg-surface-hover p-2 text-right text-xs font-medium text-text-secondary">
              Probability →
            </th>
            {probLabels.map((label) => (
              <th
                key={label}
                className="w-24 border border-border bg-surface-hover p-2 text-center text-xs font-medium text-text-secondary"
              >
                {label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {matrix.map((row, rowIdx) => (
            <tr key={rowIdx}>
              <th className="border border-border bg-surface-hover p-2 text-left text-xs font-medium text-text-secondary">
                {impactLabels[rowIdx]}
              </th>
              {row.map((count, colIdx) => {
                let bgColor = "bg-success/10";
                if (count > 0) {
                  const riskLevel = (4 - rowIdx) * (colIdx + 1);
                  if (riskLevel >= 15) bgColor = "bg-red-500/20";
                  else if (riskLevel >= 8) bgColor = "bg-amber-500/20";
                  else bgColor = "bg-success/20";
                }
                return (
                  <td
                    key={`${rowIdx}-${colIdx}`}
                    className={`w-24 border border-border ${bgColor} p-2 text-center text-sm font-semibold text-text-secondary`}
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
