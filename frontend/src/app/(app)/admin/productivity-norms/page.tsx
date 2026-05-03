"use client";

import { useCallback, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Trash2 } from "lucide-react";
import {
  productivityNormApi,
  type ProductivityNormResponse,
  type ProductivityNormType,
  type CreateProductivityNormRequest,
} from "@/lib/api/productivityNormApi";
import { workActivityApi } from "@/lib/api/workActivityApi";
import { resourceTypeApi } from "@/lib/api/resourceTypeApi";
import { resourceApi } from "@/lib/api/resourceApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

type Scope = "TYPE" | "RESOURCE";

interface NormForm {
  workActivityId: string;
  scope: Scope;
  resourceTypeId: string;
  resourceId: string;
  equipmentSpec: string;
  unit: string;
  outputPerManPerDay: string;
  outputPerHour: string;
  crewSize: string;
  outputPerDay: string;
  workingHoursPerDay: string;
  fuelLitresPerHour: string;
  remarks: string;
}

const initialFormState: NormForm = {
  workActivityId: "",
  scope: "TYPE",
  resourceTypeId: "",
  resourceId: "",
  equipmentSpec: "",
  unit: "",
  outputPerManPerDay: "",
  outputPerHour: "",
  crewSize: "",
  outputPerDay: "",
  workingHoursPerDay: "",
  fuelLitresPerHour: "",
  remarks: "",
};

const toNumberOrUndefined = (value: string): number | undefined => {
  if (value === "" || value === null || value === undefined) return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
};

const toIntOrUndefined = (value: string): number | undefined => {
  if (value === "" || value === null || value === undefined) return undefined;
  const parsed = parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : undefined;
};

function formatNumber(value: number | null): string {
  if (value === null || value === undefined) return "—";
  return value.toLocaleString("en-IN");
}

/**
 * Bucket norms by their scope label (Resource Type name when scoped to a type, or Resource code
 * when overriding a specific resource). Lowest-priority bucket "(unscoped)" sweeps up any norm
 * that's neither — e.g. legacy rows seeded before Phase 1.
 */
function groupNormsByScope(
  norms: ProductivityNormResponse[],
): Array<{ key: string; label: string; rows: ProductivityNormResponse[] }> {
  const map = new Map<string, { label: string; rows: ProductivityNormResponse[] }>();
  for (const n of norms) {
    const label =
      n.resourceTypeName ??
      (n.resourceCode ? `${n.resourceCode}${n.resourceName ? " — " + n.resourceName : ""}` : "(unscoped)");
    const key = n.resourceTypeId ?? n.resourceId ?? "_unscoped";
    const bucket = map.get(key) ?? { label, rows: [] };
    bucket.rows.push(n);
    map.set(key, bucket);
  }
  return Array.from(map.entries())
    .sort(([, a], [, b]) => a.label.localeCompare(b.label))
    .map(([key, value]) => ({ key, ...value }));
}

function ScopeBadge({ norm }: { norm: ProductivityNormResponse }) {
  if (norm.resourceId) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs bg-accent/10 text-accent ring-1 ring-accent/20">
        {norm.resourceCode ?? norm.resourceName}
      </span>
    );
  }
  if (norm.resourceTypeId) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs bg-info/10 text-info ring-1 ring-info/20">
        {norm.resourceTypeName}
      </span>
    );
  }
  return <span className="text-text-muted text-xs">—</span>;
}

export default function ProductivityNormsPage() {
  const [tab, setTab] = useState<ProductivityNormType>("MANPOWER");
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<NormForm>(initialFormState);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const clearFieldError = useCallback(
    (field: string) => {
      if (!fieldErrors[field]) return;
      setFieldErrors((prev) => {
        const next = { ...prev };
        delete next[field];
        return next;
      });
    },
    [fieldErrors],
  );

  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["productivity-norms", tab],
    queryFn: () => productivityNormApi.list(tab),
  });
  const norms: ProductivityNormResponse[] = data?.data ?? [];

  const { data: activitiesData } = useQuery({
    queryKey: ["work-activities", "active"],
    queryFn: () => workActivityApi.list(true),
  });
  const activities = activitiesData?.data ?? [];

  // Manpower tab matches MANPOWER or LABOR codes; Equipment tab matches EQUIPMENT or MACHINE.
  // Different DB seed generations used different spellings, so we accept both. Case-insensitive
  // so manual entries like "manpower" / "Labor" still match.
  const targetTypeCodes =
    tab === "MANPOWER" ? ["MANPOWER", "LABOR"] : ["EQUIPMENT", "MACHINE"];
  const matchesTargetCode = (code: string | null | undefined) =>
    !!code && targetTypeCodes.includes(code.toUpperCase());

  const { data: typesData } = useQuery({
    queryKey: ["resource-types"],
    queryFn: () => resourceTypeApi.list(),
  });
  const allTypes = typesData?.data ?? [];
  const typeDefs = allTypes.filter((t) => t.active && matchesTargetCode(t.code));

  const { data: resourcesData } = useQuery({
    queryKey: ["resources", "all"],
    queryFn: () => resourceApi.listResources(),
  });
  const allResources = (Array.isArray(resourcesData?.data) ? resourcesData?.data : []) ?? [];
  const filteredResources = allResources.filter((r) => matchesTargetCode(r.resourceTypeCode));

  const handleTabChange = (nextTab: ProductivityNormType) => {
    setTab(nextTab);
    setShowForm(false);
    setFormData(initialFormState);
    setError(null);
    setFieldErrors({});
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const errors: Record<string, string> = {};
    if (!formData.workActivityId) {
      errors.workActivityId = "Pick a master Work Activity";
    }
    if (!formData.unit.trim()) {
      errors.unit = "Unit is required (e.g. Sqm, Cum, MT)";
    }
    if (formData.scope === "TYPE" && !formData.resourceTypeId) {
      errors.resourceTypeId = "Pick a Resource Type for the default scope";
    }
    if (formData.scope === "RESOURCE" && !formData.resourceId) {
      errors.resourceId = "Pick a specific Resource for the override scope";
    }
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setError("Fix the highlighted fields and try again");
      return;
    }
    setFieldErrors({});

    try {
      const base: CreateProductivityNormRequest = {
        normType: tab,
        workActivityId: formData.workActivityId,
        resourceTypeId: formData.scope === "TYPE" ? formData.resourceTypeId || null : null,
        resourceId: formData.scope === "RESOURCE" ? formData.resourceId || null : null,
        unit: formData.unit,
        remarks: formData.remarks || undefined,
        outputPerDay: toNumberOrUndefined(formData.outputPerDay),
      };

      const request: CreateProductivityNormRequest =
        tab === "MANPOWER"
          ? {
              ...base,
              outputPerManPerDay: toNumberOrUndefined(formData.outputPerManPerDay),
              crewSize: toIntOrUndefined(formData.crewSize),
            }
          : {
              ...base,
              equipmentSpec: formData.equipmentSpec || undefined,
              outputPerHour: toNumberOrUndefined(formData.outputPerHour),
              workingHoursPerDay: toNumberOrUndefined(formData.workingHoursPerDay),
              fuelLitresPerHour: toNumberOrUndefined(formData.fuelLitresPerHour),
            };

      await productivityNormApi.create(request);
      setFormData(initialFormState);
      setShowForm(false);
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["productivity-norms", tab] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create productivity norm"));
    }
  };

  const handleDelete = useCallback(
    async (id: string) => {
      if (!window.confirm("Delete this productivity norm?")) return;
      try {
        await productivityNormApi.delete(id);
        queryClient.invalidateQueries({ queryKey: ["productivity-norms", tab] });
      } catch (err: unknown) {
        setError(getErrorMessage(err, "Failed to delete productivity norm"));
      }
    },
    [tab, queryClient]
  );

  const handleActivityPick = (id: string) => {
    const wa = activities.find((a) => a.id === id);
    setFormData({
      ...formData,
      workActivityId: id,
      unit: formData.unit || wa?.defaultUnit || "",
    });
  };

  const manpowerColumns: ColumnDef<ProductivityNormResponse>[] = useMemo(
    () => [
      {
        key: "workActivityName",
        label: "Activity",
        sortable: true,
        render: (_v, row) => (row.workActivityName ?? row.activityName ?? "—"),
      },
      {
        key: "scope",
        label: "Scope",
        sortable: false,
        render: (_v, row) => <ScopeBadge norm={row} />,
      },
      { key: "unit", label: "Unit", sortable: true },
      {
        key: "outputPerManPerDay",
        label: "Output / Man / Day",
        sortable: true,
        className: "text-right",
        render: (_v, row) => formatNumber(row.outputPerManPerDay),
      },
      {
        key: "crewSize",
        label: "Crew Size",
        sortable: true,
        className: "text-right",
        render: (_v, row) => formatNumber(row.crewSize),
      },
      {
        key: "outputPerDay",
        label: "Gang Output / Day",
        sortable: true,
        className: "text-right",
        render: (_v, row) => formatNumber(row.outputPerDay),
      },
      {
        key: "remarks",
        label: "Remarks",
        sortable: false,
        render: (_v, row) => row.remarks || "—",
      },
      {
        key: "actions",
        label: "Actions",
        sortable: false,
        className: "text-right",
        render: (_v, row) => (
          <button
            onClick={() => handleDelete(row.id)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm text-danger bg-danger/10 ring-1 ring-danger/20 rounded-lg hover:bg-danger/20 transition-colors"
            title="Delete"
          >
            <Trash2 size={14} />
            <span className="hidden sm:inline">Delete</span>
          </button>
        ),
      },
    ],
    [handleDelete]
  );

  const equipmentColumns: ColumnDef<ProductivityNormResponse>[] = useMemo(
    () => [
      {
        key: "equipmentSpec",
        label: "Equipment Spec",
        sortable: true,
        render: (_v, row) => row.equipmentSpec || "—",
      },
      {
        key: "workActivityName",
        label: "Activity",
        sortable: true,
        render: (_v, row) => (row.workActivityName ?? row.activityName ?? "—"),
      },
      {
        key: "scope",
        label: "Scope",
        sortable: false,
        render: (_v, row) => <ScopeBadge norm={row} />,
      },
      { key: "unit", label: "Unit", sortable: true },
      {
        key: "outputPerHour",
        label: "Output / Hour",
        sortable: true,
        className: "text-right",
        render: (_v, row) => formatNumber(row.outputPerHour),
      },
      {
        key: "workingHoursPerDay",
        label: "Working Hrs / Day",
        sortable: true,
        className: "text-right",
        render: (_v, row) => formatNumber(row.workingHoursPerDay),
      },
      {
        key: "outputPerDay",
        label: "Output / Day",
        sortable: true,
        className: "text-right",
        render: (_v, row) => formatNumber(row.outputPerDay),
      },
      {
        key: "fuelLitresPerHour",
        label: "Fuel L/Hr",
        sortable: true,
        className: "text-right",
        render: (_v, row) => formatNumber(row.fuelLitresPerHour),
      },
      {
        key: "remarks",
        label: "Remarks",
        sortable: false,
        render: (_v, row) => row.remarks || "—",
      },
      {
        key: "actions",
        label: "Actions",
        sortable: false,
        className: "text-right",
        render: (_v, row) => (
          <button
            onClick={() => handleDelete(row.id)}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm text-danger bg-danger/10 ring-1 ring-danger/20 rounded-lg hover:bg-danger/20 transition-colors"
            title="Delete"
          >
            <Trash2 size={14} />
            <span className="hidden sm:inline">Delete</span>
          </button>
        ),
      },
    ],
    [handleDelete]
  );

  if (isLoading && norms.length === 0) {
    return <div className="p-6 text-text-muted">Loading norms...</div>;
  }

  return (
    <div className="p-6">
      <TabTip
        title="Productivity Norms"
        description="Activity-wise man-day and equipment-hour output rates; the seed for resource estimates and daily-report validation."
      />

      <div className="mb-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
          <h1 className="text-3xl font-bold text-text-primary">Productivity Norms</h1>
          <button
            onClick={() => setShowForm(!showForm)}
            className="px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover transition-colors font-medium"
          >
            {showForm ? "Cancel" : "Add Norm"}
          </button>
        </div>

        {/* Tabs */}
        <div className="flex gap-2 mb-6 border-b border-border">
          <button
            onClick={() => handleTabChange("MANPOWER")}
            className={`px-4 py-2 rounded-t-lg text-sm font-medium transition-colors ${
              tab === "MANPOWER"
                ? "bg-accent text-text-primary"
                : "bg-surface-active/50 text-text-secondary hover:bg-border"
            }`}
          >
            Manpower
          </button>
          <button
            onClick={() => handleTabChange("EQUIPMENT")}
            className={`px-4 py-2 rounded-t-lg text-sm font-medium transition-colors ${
              tab === "EQUIPMENT"
                ? "bg-accent text-text-primary"
                : "bg-surface-active/50 text-text-secondary hover:bg-border"
            }`}
          >
            Equipment
          </button>
        </div>

        {error && (
          <div className="mb-4 rounded-lg border border-danger/30 bg-danger/10 px-4 py-3 text-sm text-danger">
            {error}
          </div>
        )}

        {showForm && (
          <form
            onSubmit={handleSubmit}
            className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl"
          >
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Work Activity <span className="text-danger">*</span>
                </label>
                <select
                  value={formData.workActivityId}
                  onChange={(e) => {
                    handleActivityPick(e.target.value);
                    clearFieldError("workActivityId");
                  }}
                  className={`w-full px-3 py-2 border bg-surface-hover text-text-primary rounded-lg ${
                    fieldErrors.workActivityId ? "border-danger" : "border-border"
                  }`}
                  aria-invalid={!!fieldErrors.workActivityId}
                >
                  <option value="">— select a master activity —</option>
                  {activities.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}
                      {a.defaultUnit ? ` (${a.defaultUnit})` : ""}
                    </option>
                  ))}
                </select>
                {fieldErrors.workActivityId && (
                  <p className="mt-1 text-xs text-danger">{fieldErrors.workActivityId}</p>
                )}
                <p className="text-xs text-text-muted mt-1">
                  Pick from the master library at <em>Admin → Work Activities</em>. The same activity
                  can carry different norms per resource type or specific resource.
                </p>
                {activities.length === 0 && (
                  <p className="text-xs text-text-muted mt-1">
                    No activities yet — create one in <em>Admin → Work Activities</em> first.
                  </p>
                )}
              </div>

              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Scope <span className="text-danger">*</span>
                </label>
                <div className="flex gap-4 mb-2">
                  <label className="inline-flex items-center gap-2 text-text-secondary">
                    <input
                      type="radio"
                      name="scope"
                      checked={formData.scope === "TYPE"}
                      onChange={() => setFormData({ ...formData, scope: "TYPE", resourceId: "" })}
                    />
                    All resources of type
                  </label>
                  <label className="inline-flex items-center gap-2 text-text-secondary">
                    <input
                      type="radio"
                      name="scope"
                      checked={formData.scope === "RESOURCE"}
                      onChange={() =>
                        setFormData({ ...formData, scope: "RESOURCE", resourceTypeId: "" })
                      }
                    />
                    Specific resource (override)
                  </label>
                </div>
                <p className="text-xs text-text-muted mb-2">
                  <strong>Type-level</strong> norms become the default for every resource of that
                  type (e.g. every Helper). <strong>Specific-resource</strong> norms override the
                  default for one resource only — use this when a particular crew consistently
                  outperforms or underperforms the standard. At runtime the lookup tries Specific
                  first, then falls back to Type-level.
                </p>
                {formData.scope === "TYPE" ? (
                  <>
                    <select
                      value={formData.resourceTypeId}
                      onChange={(e) => {
                        setFormData({ ...formData, resourceTypeId: e.target.value });
                        clearFieldError("resourceTypeId");
                      }}
                      className={`w-full px-3 py-2 border bg-surface-hover text-text-primary rounded-lg ${
                        fieldErrors.resourceTypeId ? "border-danger" : "border-border"
                      }`}
                      aria-invalid={!!fieldErrors.resourceTypeId}
                    >
                      <option value="">— select a resource type —</option>
                      {typeDefs.map((d) => (
                        <option key={d.id} value={d.id}>
                          {d.name}
                        </option>
                      ))}
                    </select>
                    {fieldErrors.resourceTypeId && (
                      <p className="mt-1 text-xs text-danger">{fieldErrors.resourceTypeId}</p>
                    )}
                  </>
                ) : (
                  <>
                    <select
                      value={formData.resourceId}
                      onChange={(e) => {
                        setFormData({ ...formData, resourceId: e.target.value });
                        clearFieldError("resourceId");
                      }}
                      className={`w-full px-3 py-2 border bg-surface-hover text-text-primary rounded-lg ${
                        fieldErrors.resourceId ? "border-danger" : "border-border"
                      }`}
                      aria-invalid={!!fieldErrors.resourceId}
                    >
                      <option value="">— select a specific resource —</option>
                      {filteredResources.map((r) => (
                        <option key={r.id} value={r.id}>
                          {r.code} — {r.name}
                        </option>
                      ))}
                    </select>
                    {fieldErrors.resourceId && (
                      <p className="mt-1 text-xs text-danger">{fieldErrors.resourceId}</p>
                    )}
                  </>
                )}
              </div>

              {tab === "EQUIPMENT" && (
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Equipment Spec
                  </label>
                  <input
                    type="text"
                    value={formData.equipmentSpec}
                    onChange={(e) => setFormData({ ...formData, equipmentSpec: e.target.value })}
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    placeholder='e.g. "JCB 210 (1.0 Cum Bucket)"'
                  />
                  <p className="text-xs text-text-muted mt-1">
                    Free-text description of the make / model / capacity. Useful when multiple
                    equipment types share the same Resource Type.
                  </p>
                </div>
              )}
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Unit <span className="text-danger">*</span>
                </label>
                <input
                  type="text"
                  value={formData.unit}
                  onChange={(e) => {
                    setFormData({ ...formData, unit: e.target.value });
                    clearFieldError("unit");
                  }}
                  className={`w-full px-3 py-2 border bg-surface-hover text-text-primary rounded-lg ${
                    fieldErrors.unit ? "border-danger" : "border-border"
                  }`}
                  aria-invalid={!!fieldErrors.unit}
                />
                {fieldErrors.unit && (
                  <p className="mt-1 text-xs text-danger">{fieldErrors.unit}</p>
                )}
                <p className="text-xs text-text-muted mt-1">
                  Auto-fills from the selected Work Activity. Override only if this norm uses a
                  different unit.
                </p>
              </div>

              {tab === "MANPOWER" ? (
                <>
                  <div className="md:col-span-2 p-3 rounded-lg bg-info/5 border border-info/20 text-xs text-text-muted">
                    Fill <strong>Output per Man per Day</strong> + <strong>Crew Size</strong> to
                    describe the standard gang. <strong>Output per Day</strong> is the gang&apos;s
                    combined output (= Output/Man × Crew Size). Leave it blank in the typical case;
                    fill it only when you want to pin a specific gang output that doesn&apos;t match
                    the multiplication.
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">
                      Output per Man per Day
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.outputPerManPerDay}
                      onChange={(e) =>
                        setFormData({ ...formData, outputPerManPerDay: e.target.value })
                      }
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                    <p className="text-xs text-text-muted mt-1">
                      What ONE worker produces in a normal 8-hour day. e.g. 2.5 Cum/day for hand
                      excavation, 12 Sqm/day for 12 mm plastering. CPWD / IS-7272 lists baseline
                      values; calibrate against your own daily-output history once you have data —
                      Indian site studies show real productivity typically runs 55–77% of CPWD
                      figures.
                    </p>
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">
                      Crew Size
                    </label>
                    <input
                      type="number"
                      step="1"
                      value={formData.crewSize}
                      onChange={(e) => setFormData({ ...formData, crewSize: e.target.value })}
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                    <p className="text-xs text-text-muted mt-1">
                      Standard gang size for this activity. e.g. 4 (1 mason + 3 helpers) for brick
                      masonry, 2 (1 fitter + 1 helper) for bar bending.
                    </p>
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">
                      Output per Day (optional)
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.outputPerDay}
                      onChange={(e) => setFormData({ ...formData, outputPerDay: e.target.value })}
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                    <p className="text-xs text-text-muted mt-1">
                      Leave blank to imply (Output per Man per Day) × (Crew Size). Fill only when
                      the actual gang output differs from that multiplication.
                    </p>
                  </div>
                </>
              ) : (
                <>
                  <div className="md:col-span-2 p-3 rounded-lg bg-info/5 border border-info/20 text-xs text-text-muted">
                    Enter the daily norm directly (e.g. 4 000 Sqm/Day for a Bull Dozer). The
                    per-hour breakdown below is optional — the server uses{" "}
                    <code className="px-1 bg-surface/50 rounded">outputPerDay</code> when
                    supplied; otherwise it derives it from <em>per-hour × working hours</em>.
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">
                      Output per Day <span className="text-danger">*</span>
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.outputPerDay}
                      onChange={(e) => setFormData({ ...formData, outputPerDay: e.target.value })}
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                      placeholder="e.g. 4000"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">
                      Output per Hour <span className="text-text-muted">(optional)</span>
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.outputPerHour}
                      onChange={(e) => setFormData({ ...formData, outputPerHour: e.target.value })}
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">
                      Working Hours per Day <span className="text-text-muted">(optional)</span>
                    </label>
                    <input
                      type="number"
                      step="0.1"
                      value={formData.workingHoursPerDay}
                      onChange={(e) =>
                        setFormData({ ...formData, workingHoursPerDay: e.target.value })
                      }
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                      placeholder="default 8"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1 text-text-secondary">
                      Fuel Litres per Hour
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.fuelLitresPerHour}
                      onChange={(e) =>
                        setFormData({ ...formData, fuelLitresPerHour: e.target.value })
                      }
                      className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    />
                  </div>
                </>
              )}

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
                Save Norm
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

        {/* Norms — grouped by scope (Resource Type / Specific Resource) so the layout mirrors
            the spreadsheet's S.No. → equipment-section → activities pattern. */}
        <div className="space-y-6">
          {groupNormsByScope(norms).map((group, gIdx) => (
            <div key={group.key} className="border border-border rounded-lg overflow-hidden bg-surface/30">
              <div className="bg-accent/10 text-text-primary px-4 py-2 flex items-center gap-3 font-semibold">
                <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-accent/20 text-accent text-xs font-bold">
                  {gIdx + 1}
                </span>
                <span className="uppercase tracking-wide">{group.label}</span>
                <span className="text-xs font-normal text-text-muted">
                  · {group.rows.length} {group.rows.length === 1 ? "norm" : "norms"}
                </span>
              </div>
              <DataTable
                columns={tab === "MANPOWER" ? manpowerColumns : equipmentColumns}
                data={group.rows}
                rowKey="id"
                searchable={false}
                pageSize={50}
              />
            </div>
          ))}
          {norms.length === 0 && (
            <div className="text-center text-text-muted py-12 border border-dashed border-border rounded-lg">
              No {tab.toLowerCase()} norms yet — click <strong>Add Norm</strong> above to start.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
