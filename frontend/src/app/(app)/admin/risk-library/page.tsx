"use client";

import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  riskTemplateApi,
  INDUSTRY_LABEL,
  type CreateRiskTemplateRequest,
  type Industry,
  type RiskTemplate,
} from "@/lib/api/riskTemplateApi";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

const ROAD_PROJECT_CATEGORIES = [
  "HIGHWAY",
  "EXPRESSWAY",
  "STATE_HIGHWAY",
  "RURAL_ROAD",
  "URBAN_ROAD",
  "OTHER",
];

const RISK_CATEGORIES = [
  "TECHNICAL", "EXTERNAL", "ORGANIZATIONAL", "PROJECT_MANAGEMENT",
  "SCHEDULE", "COST", "RESOURCE", "QUALITY",
  "LAND_ACQUISITION", "FOREST_CLEARANCE", "UTILITY_SHIFTING",
  "STATUTORY_CLEARANCE", "CONTRACTOR_FINANCIAL", "MONSOON_IMPACT",
  "GEOPOLITICAL", "NATURAL_HAZARD", "MARKET_PRICE", "TECHNOLOGY",
];

interface TemplateForm {
  code: string;
  title: string;
  description: string;
  industry: Industry;
  applicableProjectCategories: string[];
  category: string;
  defaultProbability: string;
  defaultImpactCost: string;
  defaultImpactSchedule: string;
  mitigationGuidance: string;
  isOpportunity: boolean;
  sortOrder: string;
  active: boolean;
}

const emptyForm = (): TemplateForm => ({
  code: "",
  title: "",
  description: "",
  industry: "GENERIC",
  applicableProjectCategories: [],
  category: "",
  defaultProbability: "3",
  defaultImpactCost: "3",
  defaultImpactSchedule: "3",
  mitigationGuidance: "",
  isOpportunity: false,
  sortOrder: "",
  active: true,
});

const formFromTemplate = (t: RiskTemplate): TemplateForm => ({
  code: t.code,
  title: t.title,
  description: t.description ?? "",
  industry: t.industry,
  applicableProjectCategories: [...t.applicableProjectCategories],
  category: t.category ?? "",
  defaultProbability: t.defaultProbability == null ? "" : String(t.defaultProbability),
  defaultImpactCost: t.defaultImpactCost == null ? "" : String(t.defaultImpactCost),
  defaultImpactSchedule: t.defaultImpactSchedule == null ? "" : String(t.defaultImpactSchedule),
  mitigationGuidance: t.mitigationGuidance ?? "",
  isOpportunity: t.isOpportunity,
  sortOrder: t.sortOrder == null ? "" : String(t.sortOrder),
  active: t.active,
});

const toPayload = (form: TemplateForm): CreateRiskTemplateRequest => ({
  code: form.code.trim().toUpperCase(),
  title: form.title.trim(),
  description: form.description.trim() || null,
  industry: form.industry,
  applicableProjectCategories: form.applicableProjectCategories,
  category: (form.category || null) as CreateRiskTemplateRequest["category"],
  defaultProbability: form.defaultProbability === "" ? null : Number(form.defaultProbability),
  defaultImpactCost: form.defaultImpactCost === "" ? null : Number(form.defaultImpactCost),
  defaultImpactSchedule: form.defaultImpactSchedule === "" ? null : Number(form.defaultImpactSchedule),
  mitigationGuidance: form.mitigationGuidance.trim() || null,
  isOpportunity: form.isOpportunity,
  sortOrder: form.sortOrder === "" ? null : Number(form.sortOrder),
  active: form.active,
});

export default function RiskLibraryAdminPage() {
  const queryClient = useQueryClient();

  const [filterIndustry, setFilterIndustry] = useState<Industry | "">("");
  const [filterActive, setFilterActive] = useState<boolean>(true);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<TemplateForm>(emptyForm());
  const [error, setError] = useState<string | null>(null);

  const { data, isLoading, isError, error: queryError } = useQuery({
    queryKey: ["risk-templates", filterIndustry, filterActive],
    queryFn: () =>
      riskTemplateApi.list({
        industry: filterIndustry || undefined,
        active: filterActive ? true : undefined,
      }),
  });

  const templates: RiskTemplate[] = useMemo(() => data?.data ?? [], [data]);
  const editingTemplate = templates.find((t) => t.id === editingId) ?? null;
  const isEditingSystemDefault = editingTemplate?.systemDefault === true;

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setError(null);
    setShowForm(true);
  };

  const openEdit = (t: RiskTemplate) => {
    setEditingId(t.id);
    setForm(formFromTemplate(t));
    setError(null);
    setShowForm(true);
  };

  const closeForm = () => {
    setShowForm(false);
    setEditingId(null);
    setForm(emptyForm());
    setError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (editingId) {
        await riskTemplateApi.update(editingId, toPayload(form));
      } else {
        await riskTemplateApi.create(toPayload(form));
      }
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["risk-templates"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to save risk template"));
    }
  };

  const handleDelete = async (t: RiskTemplate) => {
    if (t.systemDefault) return;
    if (!window.confirm(`Delete risk template "${t.title}"? This cannot be undone.`)) return;
    try {
      await riskTemplateApi.delete(t.id);
      if (editingId === t.id) closeForm();
      queryClient.invalidateQueries({ queryKey: ["risk-templates"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete risk template"));
    }
  };

  const toggleProjectCategory = (category: string) => {
    setForm((prev) => ({
      ...prev,
      applicableProjectCategories: prev.applicableProjectCategories.includes(category)
        ? prev.applicableProjectCategories.filter((c) => c !== category)
        : [...prev.applicableProjectCategories, category],
    }));
  };

  return (
    <div className="p-6">
      <TabTip
        title="Risk Library"
        description="Curated, reusable risks tagged by industry and project category. Project Managers pull from this library via the 'Add from Library' button on the risk register so they don't start with a blank list. The seeded set (system) is locked against deletion; admins can add custom rows alongside."
      />

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-bold text-text-primary">Risk Library</h1>
        <div className="ml-auto">
          <button
            type="button"
            onClick={openCreate}
            className="px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
          >
            + New Risk Template
          </button>
        </div>
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-3 text-sm">
        <label className="flex items-center gap-2 text-text-secondary">
          Industry
          <select
            value={filterIndustry}
            onChange={(e) => setFilterIndustry(e.target.value as Industry | "")}
            className="px-2 py-1 border border-border bg-surface-hover text-text-primary rounded"
          >
            <option value="">All</option>
            {Object.entries(INDUSTRY_LABEL).map(([code, label]) => (
              <option key={code} value={code}>{label}</option>
            ))}
          </select>
        </label>

        <label className="flex items-center gap-2 text-text-secondary">
          <input
            type="checkbox"
            checked={filterActive}
            onChange={(e) => setFilterActive(e.target.checked)}
          />
          Active only
        </label>
      </div>

      {error && <div className="text-danger mb-4">{error}</div>}

      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl"
        >
          <h2 className="text-lg font-semibold text-text-primary mb-4">
            {editingId ? `Edit "${editingTemplate?.title ?? "Template"}"` : "New Risk Template"}
            {isEditingSystemDefault && (
              <span className="ml-2 text-xs uppercase tracking-wide text-text-muted">
                System default — code &amp; industry locked
              </span>
            )}
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Code</label>
              <input
                type="text"
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
                disabled={isEditingSystemDefault}
                placeholder="e.g. ROAD-LAND-001"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg disabled:opacity-60"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Title</label>
              <input
                type="text"
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                placeholder="Land acquisition delays"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium mb-1 text-text-secondary">Description</label>
              <textarea
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                rows={2}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Industry</label>
              <select
                value={form.industry}
                onChange={(e) => setForm({ ...form, industry: e.target.value as Industry })}
                disabled={isEditingSystemDefault}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg disabled:opacity-60"
              >
                {Object.entries(INDUSTRY_LABEL).map(([code, label]) => (
                  <option key={code} value={code}>{label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Risk Category</label>
              <select
                value={form.category}
                onChange={(e) => setForm({ ...form, category: e.target.value })}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              >
                <option value="">—</option>
                {RISK_CATEGORIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium mb-1 text-text-secondary">
                Applicable Project Categories
              </label>
              <div className="flex flex-wrap gap-3 text-sm text-text-secondary">
                {ROAD_PROJECT_CATEGORIES.map((c) => (
                  <label key={c} className="flex items-center gap-1">
                    <input
                      type="checkbox"
                      checked={form.applicableProjectCategories.includes(c)}
                      onChange={() => toggleProjectCategory(c)}
                    />
                    {c}
                  </label>
                ))}
              </div>
              <p className="mt-1 text-xs text-text-muted">
                Leave all unchecked to make this template apply to any project of the chosen industry.
              </p>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Probability (1-5)</label>
              <input
                type="number" min={1} max={5}
                value={form.defaultProbability}
                onChange={(e) => setForm({ ...form, defaultProbability: e.target.value })}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Impact: Cost (1-5)</label>
              <input
                type="number" min={1} max={5}
                value={form.defaultImpactCost}
                onChange={(e) => setForm({ ...form, defaultImpactCost: e.target.value })}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Impact: Schedule (1-5)</label>
              <input
                type="number" min={1} max={5}
                value={form.defaultImpactSchedule}
                onChange={(e) => setForm({ ...form, defaultImpactSchedule: e.target.value })}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Sort Order</label>
              <input
                type="number"
                value={form.sortOrder}
                onChange={(e) => setForm({ ...form, sortOrder: e.target.value })}
                placeholder="Lower appears first"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium mb-1 text-text-secondary">Mitigation Guidance</label>
              <textarea
                value={form.mitigationGuidance}
                onChange={(e) => setForm({ ...form, mitigationGuidance: e.target.value })}
                rows={2}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                placeholder="Suggested response shown to PMs in the Add-from-Library modal."
              />
            </div>
            <div className="flex items-end gap-4">
              <label className="flex items-center gap-2 text-sm text-text-secondary">
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                Active
              </label>
              <label className="flex items-center gap-2 text-sm text-text-secondary">
                <input
                  type="checkbox"
                  checked={form.isOpportunity}
                  onChange={(e) => setForm({ ...form, isOpportunity: e.target.checked })}
                />
                Opportunity (upside)
              </label>
            </div>
          </div>
          <div className="flex gap-2 mt-4">
            <button
              type="submit"
              className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600"
            >
              {editingId ? "Save Changes" : "Create"}
            </button>
            <button
              type="button"
              onClick={closeForm}
              className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {isError && (
        <div className="text-danger mb-4">
          {getErrorMessage(queryError, "Failed to load risk templates")}
        </div>
      )}

      <div className="overflow-x-auto">
        <table className="w-full border-collapse border border-border">
          <thead>
            <tr className="bg-surface/80">
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Code</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Title</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Industry</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Project Categories</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Category</th>
              <th className="border border-border px-4 py-2 text-right text-text-secondary">P / IC / IS</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Status</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Actions</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={8} className="border border-border px-4 py-6 text-center text-text-muted">
                  Loading…
                </td>
              </tr>
            )}
            {!isLoading && templates.length === 0 && (
              <tr>
                <td colSpan={8} className="border border-border px-4 py-6 text-center text-text-muted">
                  No risk templates match these filters.
                </td>
              </tr>
            )}
            {templates.map((t) => (
              <tr key={t.id} className="text-text-primary hover:bg-surface-hover/30">
                <td className="border border-border px-4 py-2 font-mono text-sm">
                  {t.code}
                  {t.systemDefault && (
                    <span className="ml-2 text-[10px] uppercase tracking-wide text-text-muted">system</span>
                  )}
                </td>
                <td className="border border-border px-4 py-2">{t.title}</td>
                <td className="border border-border px-4 py-2 text-sm">
                  {INDUSTRY_LABEL[t.industry]}
                </td>
                <td className="border border-border px-4 py-2 text-xs text-text-muted">
                  {t.applicableProjectCategories.length === 0
                    ? <span className="italic">any</span>
                    : t.applicableProjectCategories.join(", ")}
                </td>
                <td className="border border-border px-4 py-2 text-xs">{t.category ?? "—"}</td>
                <td className="border border-border px-4 py-2 text-right font-mono text-sm">
                  {(t.defaultProbability ?? "—")} / {(t.defaultImpactCost ?? "—")} / {(t.defaultImpactSchedule ?? "—")}
                </td>
                <td className="border border-border px-4 py-2">
                  {t.active ? (
                    <span className="text-emerald-700">Active</span>
                  ) : (
                    <span className="text-text-muted">Inactive</span>
                  )}
                </td>
                <td className="border border-border px-4 py-2 text-sm">
                  <button onClick={() => openEdit(t)} className="text-accent hover:underline mr-3">Edit</button>
                  {!t.systemDefault && (
                    <button onClick={() => handleDelete(t)} className="text-danger hover:underline">Delete</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
