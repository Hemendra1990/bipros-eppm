"use client";

import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle, Building2, Factory, GitBranch, Globe2, Hammer, HardHat,
  Heart, Landmark, Layers, Lock, MoreVertical, Pencil, Plus, Search,
  Shield, Sparkles, TrainTrack, Trash2, TrendingUp, Wheat, X,
  Anchor, FlaskConical, Mountain, Pickaxe, Plane, Radio, Ship, Truck, Wrench,
} from "lucide-react";
import {
  riskTemplateApi,
  INDUSTRY_LABEL,
  type CreateRiskTemplateRequest,
  type Industry,
  type RiskTemplate,
} from "@/lib/api/riskTemplateApi";
import { riskCategoryApi } from "@/lib/api/riskCategoryApi";
import { SearchableSelect, type SelectOption } from "@/components/common/SearchableSelect";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

/** Per-industry visual identity (icon + accent colour) — picked to read at a glance. */
const INDUSTRY_VISUAL: Record<Industry, { icon: typeof Hammer; tint: string; ring: string }> = {
  ROAD:                 { icon: TrainTrack,   tint: "bg-amber-500/15 text-amber-300",      ring: "ring-amber-500/30" },
  BRIDGE:               { icon: GitBranch,    tint: "bg-orange-500/15 text-orange-300",    ring: "ring-orange-500/30" },
  BUILDING:             { icon: Building2,    tint: "bg-sky-500/15 text-sky-300",          ring: "ring-sky-500/30" },
  CONSTRUCTION_GENERAL: { icon: HardHat,      tint: "bg-yellow-500/15 text-yellow-300",    ring: "ring-yellow-500/30" },
  REFINERY:             { icon: Factory,      tint: "bg-rose-500/15 text-rose-300",        ring: "ring-rose-500/30" },
  OIL_GAS:              { icon: FlaskConical, tint: "bg-red-500/15 text-red-300",          ring: "ring-red-500/30" },
  RAILWAY:              { icon: Truck,        tint: "bg-violet-500/15 text-violet-300",    ring: "ring-violet-500/30" },
  METRO:                { icon: TrainTrack,   tint: "bg-indigo-500/15 text-indigo-300",    ring: "ring-indigo-500/30" },
  POWER:                { icon: TrendingUp,   tint: "bg-fuchsia-500/15 text-fuchsia-300",  ring: "ring-fuchsia-500/30" },
  WATER:                { icon: Anchor,       tint: "bg-cyan-500/15 text-cyan-300",        ring: "ring-cyan-500/30" },
  MINING:               { icon: Pickaxe,      tint: "bg-stone-500/15 text-stone-300",      ring: "ring-stone-500/30" },
  MANUFACTURING:        { icon: Wrench,       tint: "bg-blue-500/15 text-blue-300",        ring: "ring-blue-500/30" },
  PHARMA:               { icon: FlaskConical, tint: "bg-pink-500/15 text-pink-300",        ring: "ring-pink-500/30" },
  IT:                   { icon: Sparkles,     tint: "bg-emerald-500/15 text-emerald-300",  ring: "ring-emerald-500/30" },
  TELECOM:              { icon: Radio,        tint: "bg-teal-500/15 text-teal-300",        ring: "ring-teal-500/30" },
  BANKING_FINANCE:      { icon: Landmark,     tint: "bg-lime-500/15 text-lime-300",        ring: "ring-lime-500/30" },
  HEALTHCARE:           { icon: Heart,        tint: "bg-red-400/15 text-red-200",          ring: "ring-red-400/30" },
  AGRICULTURE:          { icon: Wheat,        tint: "bg-green-500/15 text-green-300",      ring: "ring-green-500/30" },
  AEROSPACE_DEFENSE:    { icon: Plane,        tint: "bg-slate-500/15 text-slate-300",      ring: "ring-slate-500/30" },
  MARITIME:             { icon: Ship,         tint: "bg-blue-400/15 text-blue-200",        ring: "ring-blue-400/30" },
  MASS_EVENT:           { icon: Mountain,     tint: "bg-purple-500/15 text-purple-300",    ring: "ring-purple-500/30" },
  GENERIC:              { icon: Globe2,       tint: "bg-zinc-500/15 text-zinc-300",        ring: "ring-zinc-500/30" },
};

/** Risk-score → RAG colour band. Mirrors the backend deriveRag rules. */
function ragColor(prob: number | null, impact: number | null): { bg: string; text: string; label: string } {
  if (prob == null || impact == null) return { bg: "bg-zinc-500/10", text: "text-zinc-400", label: "—" };
  const score = prob * impact;
  if (score >= 20) return { bg: "bg-rose-700/30",   text: "text-rose-200",  label: "CRIMSON" };
  if (score >= 12) return { bg: "bg-red-600/25",    text: "text-red-200",   label: "RED" };
  if (score >= 6)  return { bg: "bg-amber-500/25",  text: "text-amber-100", label: "AMBER" };
  return                  { bg: "bg-emerald-600/20",text: "text-emerald-200", label: "GREEN" };
}

const ROAD_PROJECT_CATEGORIES = [
  "HIGHWAY",
  "EXPRESSWAY",
  "STATE_HIGHWAY",
  "RURAL_ROAD",
  "URBAN_ROAD",
  "OTHER",
];

interface TemplateForm {
  code: string;
  title: string;
  description: string;
  industry: Industry;
  applicableProjectCategories: string[];
  categoryId: string;
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
  categoryId: "",
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
  categoryId: t.category?.id ?? "",
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
  categoryId: form.categoryId || null,
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
  const [searchQuery, setSearchQuery] = useState("");
  const [filterCategoryTypeId, setFilterCategoryTypeId] = useState<string>("");
  const [sortBy, setSortBy] = useState<"code" | "title" | "score">("code");

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

  // Master list of risk categories for the form's category select.
  const { data: categoryListData } = useQuery({
    queryKey: ["risk-categories-all-for-template-form"],
    queryFn: () => riskCategoryApi.listCategories(),
  });
  const allCategories = useMemo(() => categoryListData?.data ?? [], [categoryListData]);
  const categoryOptions: SelectOption[] = useMemo(
    () =>
      allCategories
        .slice()
        .sort((a, b) => {
          const t = a.type.code.localeCompare(b.type.code);
          return t !== 0 ? t : a.name.localeCompare(b.name);
        })
        .map((c) => ({
          value: c.id,
          label: `${c.type.code} → ${c.name}  (${c.industry} · ${c.code})`,
        })),
    [allCategories]
  );

  const templates: RiskTemplate[] = useMemo(() => data?.data ?? [], [data]);

  // Distinct category types in current result set, for the type filter dropdown.
  const presentTypes = useMemo(() => {
    const m = new Map<string, { code: string; name: string }>();
    for (const t of templates) {
      const ty = t.category?.type;
      if (ty && !m.has(ty.code)) m.set(ty.code, { code: ty.code, name: ty.name });
    }
    return Array.from(m.values()).sort((a, b) => a.name.localeCompare(b.name));
  }, [templates]);

  // Search + type-filter + sort applied client-side over the (industry/active)-filtered server result.
  const filteredTemplates = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const out = templates.filter((t) => {
      if (filterCategoryTypeId && t.category?.type?.code !== filterCategoryTypeId) return false;
      if (!q) return true;
      return (
        t.code.toLowerCase().includes(q) ||
        t.title.toLowerCase().includes(q) ||
        (t.description ?? "").toLowerCase().includes(q) ||
        (t.mitigationGuidance ?? "").toLowerCase().includes(q) ||
        (t.category?.name ?? "").toLowerCase().includes(q) ||
        (t.category?.code ?? "").toLowerCase().includes(q)
      );
    });
    out.sort((a, b) => {
      if (sortBy === "title") return a.title.localeCompare(b.title);
      if (sortBy === "score") {
        const sa = (a.defaultProbability ?? 0) * Math.max(a.defaultImpactCost ?? 0, a.defaultImpactSchedule ?? 0);
        const sb = (b.defaultProbability ?? 0) * Math.max(b.defaultImpactCost ?? 0, b.defaultImpactSchedule ?? 0);
        return sb - sa;
      }
      return a.code.localeCompare(b.code);
    });
    return out;
  }, [templates, searchQuery, filterCategoryTypeId, sortBy]);

  const summary = useMemo(() => ({
    total: templates.length,
    active: templates.filter((t) => t.active).length,
    system: templates.filter((t) => t.systemDefault).length,
    custom: templates.filter((t) => !t.systemDefault).length,
  }), [templates]);
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

      {/* ─────── Summary tiles ─────── */}
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="rounded-xl border border-border bg-surface/60 px-4 py-3">
          <div className="text-xs uppercase tracking-wide text-text-muted">Total</div>
          <div className="mt-1 text-2xl font-bold text-text-primary">{summary.total}</div>
        </div>
        <div className="rounded-xl border border-border bg-surface/60 px-4 py-3">
          <div className="text-xs uppercase tracking-wide text-text-muted">Active</div>
          <div className="mt-1 text-2xl font-bold text-emerald-300">{summary.active}</div>
        </div>
        <div className="rounded-xl border border-border bg-surface/60 px-4 py-3">
          <div className="text-xs uppercase tracking-wide text-text-muted">System default</div>
          <div className="mt-1 flex items-center gap-2 text-2xl font-bold text-text-primary">
            <Lock size={16} className="text-text-muted" /> {summary.system}
          </div>
        </div>
        <div className="rounded-xl border border-border bg-surface/60 px-4 py-3">
          <div className="text-xs uppercase tracking-wide text-text-muted">Custom</div>
          <div className="mt-1 text-2xl font-bold text-text-primary">{summary.custom}</div>
        </div>
      </div>

      {/* ─────── Toolbar ─────── */}
      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center">
        <div className="relative flex-1">
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by code, title, category, mitigation…"
            className="w-full rounded-lg border border-border bg-surface-hover py-2 pl-9 pr-9 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery("")}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-text-muted hover:text-text-primary"
              aria-label="Clear search"
            >
              <X size={14} />
            </button>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <select
            value={filterIndustry}
            onChange={(e) => setFilterIndustry(e.target.value as Industry | "")}
            className="rounded border border-border bg-surface-hover px-3 py-2 text-text-primary"
          >
            <option value="">All industries</option>
            {Object.entries(INDUSTRY_LABEL).map(([code, label]) => (
              <option key={code} value={code}>{label}</option>
            ))}
          </select>
          <select
            value={filterCategoryTypeId}
            onChange={(e) => setFilterCategoryTypeId(e.target.value)}
            disabled={presentTypes.length === 0}
            className="rounded border border-border bg-surface-hover px-3 py-2 text-text-primary disabled:opacity-50"
          >
            <option value="">All types</option>
            {presentTypes.map((t) => (
              <option key={t.code} value={t.code}>{t.name}</option>
            ))}
          </select>
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as "code" | "title" | "score")}
            className="rounded border border-border bg-surface-hover px-3 py-2 text-text-primary"
          >
            <option value="code">Sort: Code</option>
            <option value="title">Sort: Title</option>
            <option value="score">Sort: Risk score (high → low)</option>
          </select>
          <label className="flex items-center gap-2 rounded border border-border bg-surface-hover px-3 py-2 text-text-secondary">
            <input
              type="checkbox"
              checked={filterActive}
              onChange={(e) => setFilterActive(e.target.checked)}
            />
            Active only
          </label>
        </div>
      </div>

      {/* Active-filter chips — quick-clear */}
      {(searchQuery || filterIndustry || filterCategoryTypeId) && (
        <div className="mb-4 flex flex-wrap gap-2 text-xs">
          {searchQuery && (
            <button onClick={() => setSearchQuery("")} className="inline-flex items-center gap-1 rounded-full bg-accent/15 px-3 py-1 text-accent hover:bg-accent/25">
              “{searchQuery}” <X size={12} />
            </button>
          )}
          {filterIndustry && (
            <button onClick={() => setFilterIndustry("")} className="inline-flex items-center gap-1 rounded-full bg-accent/15 px-3 py-1 text-accent hover:bg-accent/25">
              {INDUSTRY_LABEL[filterIndustry]} <X size={12} />
            </button>
          )}
          {filterCategoryTypeId && (
            <button onClick={() => setFilterCategoryTypeId("")} className="inline-flex items-center gap-1 rounded-full bg-accent/15 px-3 py-1 text-accent hover:bg-accent/25">
              {presentTypes.find((t) => t.code === filterCategoryTypeId)?.name ?? filterCategoryTypeId} <X size={12} />
            </button>
          )}
          <span className="ml-2 self-center text-text-muted">
            {filteredTemplates.length} of {templates.length} shown
          </span>
        </div>
      )}

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
              <SearchableSelect
                options={categoryOptions}
                value={form.categoryId}
                onChange={(v) => setForm({ ...form, categoryId: v })}
                placeholder="Search risk categories…"
              />
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

      {/* ─────── Card grid ─────── */}
      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-44 animate-pulse rounded-2xl border border-border bg-surface/40" />
          ))}
        </div>
      ) : filteredTemplates.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-surface/30 px-6 py-16 text-center">
          <AlertTriangle size={32} className="mx-auto mb-3 text-text-muted" />
          <p className="text-sm text-text-muted">No risk templates match these filters.</p>
          <button
            onClick={() => { setSearchQuery(""); setFilterIndustry(""); setFilterCategoryTypeId(""); }}
            className="mt-3 text-sm text-accent hover:underline"
          >
            Clear filters
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {filteredTemplates.map((t) => {
            const visual = INDUSTRY_VISUAL[t.industry] ?? INDUSTRY_VISUAL.GENERIC;
            const Icon = visual.icon;
            const rag = ragColor(t.defaultProbability, Math.max(t.defaultImpactCost ?? 0, t.defaultImpactSchedule ?? 0) || null);
            return (
              <div
                key={t.id}
                className={`group relative flex flex-col overflow-hidden rounded-2xl border border-border bg-surface/60 p-4 transition-all hover:border-accent/60 hover:shadow-lg hover:shadow-accent/5 ${!t.active ? "opacity-60" : ""}`}
              >
                {/* Top RAG band */}
                <div className={`absolute inset-x-0 top-0 h-1 ${rag.bg}`} />

                {/* Header: industry icon, code, system lock, active dot */}
                <div className="mb-3 flex items-start justify-between gap-3">
                  <div className="flex min-w-0 items-center gap-2">
                    <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ring-1 ${visual.tint} ${visual.ring}`}>
                      <Icon size={18} />
                    </div>
                    <div className="min-w-0">
                      <p className="truncate font-mono text-xs text-text-secondary">{t.code}</p>
                      <p className="text-[10px] uppercase tracking-wide text-text-muted">{INDUSTRY_LABEL[t.industry]}</p>
                    </div>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    {t.systemDefault && (
                      <span title="System default — code/industry locked, cannot delete" className="inline-flex items-center gap-1 rounded-full bg-zinc-500/15 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-zinc-300">
                        <Lock size={10} /> System
                      </span>
                    )}
                    <span
                      title={t.active ? "Active" : "Inactive"}
                      className={`h-2 w-2 rounded-full ${t.active ? "bg-emerald-400" : "bg-zinc-600"}`}
                    />
                  </div>
                </div>

                {/* Title */}
                <h3 className="mb-1 line-clamp-2 text-sm font-semibold text-text-primary" title={t.title}>
                  {t.title}
                </h3>

                {/* Description */}
                {t.description && (
                  <p className="mb-3 line-clamp-2 text-xs text-text-secondary" title={t.description}>
                    {t.description}
                  </p>
                )}

                {/* Category chip */}
                {t.category && (
                  <div className="mb-3 inline-flex max-w-full items-center gap-1.5 self-start rounded-md border border-border bg-surface px-2 py-1 text-[11px]">
                    <Layers size={11} className="text-text-muted" />
                    <span className="truncate text-text-muted">{t.category.type.code}</span>
                    <span className="text-text-muted">›</span>
                    <span className="truncate text-text-primary">{t.category.name}</span>
                  </div>
                )}

                {/* Project category chips */}
                {t.applicableProjectCategories.length > 0 && (
                  <div className="mb-3 flex flex-wrap gap-1">
                    {t.applicableProjectCategories.slice(0, 4).map((c) => (
                      <span key={c} className="rounded-full bg-surface-hover px-2 py-0.5 text-[10px] text-text-muted">
                        {c}
                      </span>
                    ))}
                    {t.applicableProjectCategories.length > 4 && (
                      <span className="rounded-full bg-surface-hover px-2 py-0.5 text-[10px] text-text-muted">
                        +{t.applicableProjectCategories.length - 4}
                      </span>
                    )}
                  </div>
                )}

                {/* Footer: P-IC-IS pill + RAG label + actions */}
                <div className="mt-auto flex items-center justify-between border-t border-border/50 pt-3">
                  <div className="flex items-center gap-2 text-[11px]">
                    <span className="rounded-md bg-surface px-2 py-1 font-mono text-text-secondary" title="Probability / Impact-Cost / Impact-Schedule">
                      P{t.defaultProbability ?? "—"} · IC{t.defaultImpactCost ?? "—"} · IS{t.defaultImpactSchedule ?? "—"}
                    </span>
                    <span className={`rounded-md px-2 py-1 text-[10px] font-bold uppercase tracking-wide ${rag.bg} ${rag.text}`}>
                      {rag.label}
                    </span>
                  </div>
                  <div className="flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                    <button
                      onClick={() => openEdit(t)}
                      title="Edit"
                      className="rounded p-1.5 text-text-secondary hover:bg-surface-hover hover:text-text-primary"
                    >
                      <Pencil size={14} />
                    </button>
                    {!t.systemDefault && (
                      <button
                        onClick={() => handleDelete(t)}
                        title="Delete"
                        className="rounded p-1.5 text-text-secondary hover:bg-surface-hover hover:text-danger"
                      >
                        <Trash2 size={14} />
                      </button>
                    )}
                  </div>
                </div>

                {/* Mitigation guidance — peek on hover via expanded title */}
                {t.mitigationGuidance && (
                  <div className="mt-2 hidden rounded-md border border-border/60 bg-surface/40 p-2 text-[11px] text-text-secondary group-hover:block" title={t.mitigationGuidance}>
                    <p className="mb-0.5 text-[10px] font-semibold uppercase tracking-wide text-text-muted">Mitigation</p>
                    <p className="line-clamp-3">{t.mitigationGuidance}</p>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
