"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { X } from "lucide-react";
import { resourceApi } from "@/lib/api/resourceApi";
import { resourceRoleApi, type ResourceRole } from "@/lib/api/resourceRoleApi";
import { activityApi, type ActivityResponse } from "@/lib/api/activityApi";
import { projectResourceApi, type ProjectResourceResponse } from "@/lib/api/projectResourceApi";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { getErrorMessage } from "@/lib/utils/error";

interface Props {
  projectId: string;
  /** When provided, the Activity picker is hidden and the form is locked to this activity. */
  activityId?: string;
  defaultMode?: "RESOURCE" | "ROLE";
  onSuccess?: () => void;
  onCancel?: () => void;
}

interface ResourceLine {
  resourceId: string;
  plannedUnits: string;
}

/**
 * Shared resource-assignment form. Used on the Resources tab (Activity picker visible),
 * the Activity Detail drawer (locked to a single activity), and the full Activity Detail page.
 *
 * RESOURCE mode supports picking many resources at once, each with its own Planned Units.
 * ROLE mode is single-select with one Planned Units.
 */
export function ResourceAssignmentForm({
  projectId,
  activityId,
  defaultMode = "RESOURCE",
  onSuccess,
  onCancel,
}: Props) {
  const queryClient = useQueryClient();
  const lockedActivity = !!activityId;

  const [assignMode, setAssignMode] = useState<"ROLE" | "RESOURCE">(defaultMode);
  const [pickedActivityId, setPickedActivityId] = useState(activityId ?? "");
  const [resourceLines, setResourceLines] = useState<ResourceLine[]>([]);
  const [roleId, setRoleId] = useState("");
  const [rolePlannedUnits, setRolePlannedUnits] = useState("");
  const [rateType, setRateType] = useState("STANDARD");
  /**
   * Type filter for the pooled-resource picker. Defaults to ALL so existing users see no
   * change unless they pick a type. The dropdown filters to entries whose
   * {@code resourceTypeName} matches the chosen kind — pool comes back with the localized
   * name (Labor / Equipment / Material), not the code.
   */
  const [typeFilter, setTypeFilter] = useState<"ALL" | "LABOR" | "EQUIPMENT" | "MATERIAL">("ALL");

  const { data: poolData } = useQuery({
    queryKey: ["resource-pool", projectId],
    queryFn: () => projectResourceApi.listPool(projectId),
  });
  const { data: rolesData } = useQuery({
    queryKey: ["resource-roles"],
    queryFn: () => resourceRoleApi.list(),
  });
  const { data: activitiesData } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 100),
    enabled: !lockedActivity,
  });

  const pool = useMemo<ProjectResourceResponse[]>(() => {
    const raw = poolData?.data;
    return Array.isArray(raw) ? raw : [];
  }, [poolData]);

  const roles = useMemo<ResourceRole[]>(() => {
    const raw = rolesData?.data as unknown;
    return Array.isArray(raw)
      ? (raw as ResourceRole[])
      : ((raw as { content?: ResourceRole[] } | undefined)?.content ?? []);
  }, [rolesData]);

  const activities = useMemo<ActivityResponse[]>(() => {
    const raw = activitiesData?.data;
    if (!raw) return [];
    if (Array.isArray(raw)) return raw as ActivityResponse[];
    return (raw as { content?: ActivityResponse[] }).content ?? [];
  }, [activitiesData]);

  const effectiveActivityId = activityId ?? pickedActivityId;

  /** Counts per type — drives the "Labor 39 / Equipment 34 / Material 23" pill labels. */
  const typeCounts = useMemo(() => {
    const c = { ALL: pool.length, LABOR: 0, EQUIPMENT: 0, MATERIAL: 0 } as Record<
      "ALL" | "LABOR" | "EQUIPMENT" | "MATERIAL",
      number
    >;
    for (const p of pool) {
      const code = poolKindOf(p);
      if (code) c[code]++;
    }
    return c;
  }, [pool]);

  /** Pool filtered by the active type pill. */
  const filteredPool = useMemo(() => {
    if (typeFilter === "ALL") return pool;
    return pool.filter((p) => poolKindOf(p) === typeFilter);
  }, [pool, typeFilter]);

  const poolById = useMemo(() => {
    const m = new Map<string, ProjectResourceResponse>();
    for (const p of pool) m.set(p.resourceId, p);
    return m;
  }, [pool]);

  const assignMutation = useMutation({
    mutationFn: async () => {
      if (assignMode === "ROLE") {
        return resourceApi.createProjectResourceAssignment(projectId, {
          activityId: effectiveActivityId,
          roleId: roleId || undefined,
          projectId,
          plannedUnits: parseFloat(rolePlannedUnits),
          rateType,
        });
      }
      // RESOURCE mode — fire one assignment per resource line in parallel. Each line carries
      // its own plannedUnits. Promise.all rejects on the first failure; any earlier resolved
      // assignments stay (they're committed server-side) and the user can retry the rest.
      return Promise.all(
        resourceLines.map((line) =>
          resourceApi.createProjectResourceAssignment(projectId, {
            activityId: effectiveActivityId,
            resourceId: line.resourceId,
            projectId,
            plannedUnits: parseFloat(line.plannedUnits),
            rateType,
          })
        )
      );
    },
    onSuccess: () => {
      // Prefix-invalidate every assignment query in flight: the Resources tab uses
      // ["resource-assignments", projectId], the activity detail page uses
      // ["resource-assignments", "activity", projectId, activityId], and the drawer uses
      // ["activity-assignments", projectId, activityId]. A bare prefix flushes the first two.
      queryClient.invalidateQueries({ queryKey: ["resource-assignments"] });
      if (lockedActivity) {
        queryClient.invalidateQueries({
          queryKey: ["activity-assignments", projectId, activityId],
        });
      }
      const count = assignMode === "RESOURCE" ? resourceLines.length : 1;
      toast.success(count > 1 ? `${count} resources assigned` : "Resource assigned");
      onSuccess?.();
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to assign resource"));
    },
  });

  const allLinesValid =
    resourceLines.length > 0 &&
    resourceLines.every((l) => {
      const n = parseFloat(l.plannedUnits);
      return Number.isFinite(n) && n > 0;
    });

  const canSubmit =
    !!effectiveActivityId &&
    (assignMode === "RESOURCE"
      ? allLinesValid
      : !!roleId && !!rolePlannedUnits && parseFloat(rolePlannedUnits) > 0);

  const addResource = (id: string) => {
    if (!id || resourceLines.some((l) => l.resourceId === id)) return;
    setResourceLines([...resourceLines, { resourceId: id, plannedUnits: "" }]);
  };
  const removeResource = (id: string) => {
    setResourceLines(resourceLines.filter((l) => l.resourceId !== id));
  };
  const updateLineUnits = (id: string, value: string) => {
    setResourceLines(
      resourceLines.map((l) => (l.resourceId === id ? { ...l, plannedUnits: value } : l))
    );
  };

  return (
    <div className="rounded-lg border border-border bg-surface/50 p-6 shadow-sm">
      <h3 className="mb-4 text-lg font-semibold text-text-primary">
        {lockedActivity ? "Assign Resource" : "Assign Resource to Activity"}
      </h3>

      <div className="mb-4 flex items-center gap-2">
        <button
          type="button"
          onClick={() => setAssignMode("RESOURCE")}
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            assignMode === "RESOURCE"
              ? "bg-accent text-accent-foreground"
              : "border border-border text-text-secondary hover:bg-surface-hover"
          }`}
        >
          Resource
        </button>
        <button
          type="button"
          onClick={() => setAssignMode("ROLE")}
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            assignMode === "ROLE"
              ? "bg-accent text-accent-foreground"
              : "border border-border text-text-secondary hover:bg-surface-hover"
          }`}
        >
          Plan with Role
        </button>
      </div>

      <div className="space-y-4">
        {!lockedActivity && (
          <div>
            <label className="block text-sm font-medium text-text-secondary">Activity</label>
            <SearchableSelect
              value={pickedActivityId}
              onChange={setPickedActivityId}
              placeholder="Search activities..."
              options={activities.map((activity) => ({
                value: activity.id,
                label: `${activity.code} - ${activity.name}`,
              }))}
            />
          </div>
        )}

        {assignMode === "RESOURCE" ? (
          <div>
            <label className="block text-sm font-medium text-text-secondary">
              Resources
              <span className="ml-1 text-xs font-normal text-text-muted">
                (set Planned Units per resource)
              </span>
            </label>

            <ResourceTypeFilter
              value={typeFilter}
              onChange={setTypeFilter}
              counts={typeCounts}
            />

            {resourceLines.length > 0 && (
              <div className="mt-2 space-y-2">
                {resourceLines.map((line) => {
                  const meta = poolById.get(line.resourceId);
                  const label = meta
                    ? `${meta.resourceCode ?? line.resourceId} — ${meta.resourceName ?? "Unknown"}`
                    : line.resourceId;
                  const unit = meta?.customUnit ?? "units";
                  const n = parseFloat(line.plannedUnits);
                  const invalid =
                    line.plannedUnits !== "" && (!Number.isFinite(n) || n <= 0);
                  return (
                    <div
                      key={line.resourceId}
                      className="flex items-center gap-2 rounded-md border border-border bg-surface/40 px-3 py-2"
                    >
                      <span className="flex-1 truncate text-sm text-text-primary">{label}</span>
                      <div className="flex items-center gap-1">
                        <input
                          type="number"
                          min={0}
                          step="0.01"
                          value={line.plannedUnits}
                          onChange={(e) => updateLineUnits(line.resourceId, e.target.value)}
                          placeholder="Units"
                          className={`w-24 rounded-md border bg-surface-hover px-2 py-1 text-sm text-text-primary focus:outline-none focus:ring-1 ${
                            invalid
                              ? "border-danger focus:border-danger focus:ring-danger"
                              : "border-border focus:border-accent focus:ring-accent"
                          }`}
                        />
                        <span className="text-xs text-text-muted">{unit}</span>
                      </div>
                      <button
                        type="button"
                        onClick={() => removeResource(line.resourceId)}
                        className="rounded-md p-1 text-text-secondary hover:bg-surface-hover hover:text-text-primary"
                        aria-label={`Remove ${label}`}
                      >
                        <X size={14} />
                      </button>
                    </div>
                  );
                })}
              </div>
            )}

            <div className="mt-2">
              <SearchableSelect
                // Drive value to "" so the picker resets after each selection — selections
                // accumulate in the rows above.
                value=""
                onChange={addResource}
                placeholder={
                  resourceLines.length > 0
                    ? "Add another resource..."
                    : typeFilter === "ALL"
                      ? "Search pooled resources..."
                      : `Search ${typeFilter.toLowerCase()} resources...`
                }
                options={filteredPool
                  .filter((p) => !resourceLines.some((l) => l.resourceId === p.resourceId))
                  .map((p) => ({
                    value: p.resourceId,
                    label: `${p.resourceCode ?? p.resourceId} — ${p.resourceName ?? "Unknown"}${p.resourceTypeName ? ` · ${p.resourceTypeName}` : ""}`,
                  }))}
              />
            </div>
            {pool.length === 0 ? (
              <p className="mt-1 text-xs text-amber-600">
                No resources in pool. Add resources to the project pool first.
              </p>
            ) : filteredPool.length === 0 ? (
              <p className="mt-1 text-xs text-amber-600">
                No {typeFilter.toLowerCase()} resources in this project's pool.
              </p>
            ) : null}
          </div>
        ) : (
          <>
            <div>
              <label className="block text-sm font-medium text-text-secondary">Role</label>
              <SearchableSelect
                value={roleId}
                onChange={setRoleId}
                placeholder="Search roles..."
                options={roles.map((role) => ({
                  value: role.id,
                  label: `${role.code} - ${role.name}`,
                }))}
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary">Planned Units</label>
              <input
                type="number"
                value={rolePlannedUnits}
                onChange={(e) => setRolePlannedUnits(e.target.value)}
                step="0.01"
                className="mt-1 block w-full rounded-md border-border px-3 py-2 border bg-surface/50 text-text-primary shadow-sm focus:border-accent focus:outline-none"
              />
            </div>
          </>
        )}

        <div>
          <label className="block text-sm font-medium text-text-secondary">Rate Type</label>
          <select
            value={rateType}
            onChange={(e) => setRateType(e.target.value)}
            className="mt-1 block w-full rounded-md border-border px-3 py-2 border bg-surface/50 text-text-primary shadow-sm focus:border-accent focus:outline-none"
          >
            <option value="STANDARD">Standard</option>
            <option value="OVERTIME">Overtime</option>
            <option value="CPWD_SOR">CPWD SOR</option>
          </select>
        </div>

        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => assignMutation.mutate()}
            disabled={assignMutation.isPending || !canSubmit}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:bg-surface-active"
          >
            {(() => {
              const n = assignMode === "RESOURCE" ? resourceLines.length : 1;
              if (assignMutation.isPending) return n > 1 ? `Assigning ${n}...` : "Assigning...";
              return n > 1 ? `Assign ${n} resources` : "Assign";
            })()}
          </button>
          {onCancel && (
            <button
              type="button"
              onClick={onCancel}
              className="rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
            >
              Cancel
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Map a {@link ProjectResourceResponse} to one of the three system kinds, falling back to null
 * for custom resource types. Pool entries carry {@code resourceTypeName} (the localized name —
 * "Labor", "Equipment", "Material") rather than the code; we case-fold and prefix-match so
 * "Labour" still resolves to LABOR if seeded that way.
 */
function poolKindOf(
  p: ProjectResourceResponse,
): "LABOR" | "EQUIPMENT" | "MATERIAL" | null {
  const name = (p.resourceTypeName ?? "").trim().toLowerCase();
  if (name.startsWith("lab") || name === "manpower") return "LABOR";
  if (name.startsWith("equip")) return "EQUIPMENT";
  if (name.startsWith("mat")) return "MATERIAL";
  return null;
}

/**
 * Pill bar above the pooled-resource picker. Lets the user narrow the dropdown to one
 * kind (Labor / Equipment / Material) before searching, instead of scrolling through a
 * mixed list of 73+ resources. Each pill carries its current count for quick scanning.
 */
function ResourceTypeFilter({
  value,
  onChange,
  counts,
}: {
  value: "ALL" | "LABOR" | "EQUIPMENT" | "MATERIAL";
  onChange: (v: "ALL" | "LABOR" | "EQUIPMENT" | "MATERIAL") => void;
  counts: Record<"ALL" | "LABOR" | "EQUIPMENT" | "MATERIAL", number>;
}) {
  const pills: { key: "ALL" | "LABOR" | "EQUIPMENT" | "MATERIAL"; label: string }[] = [
    { key: "ALL", label: "All" },
    { key: "LABOR", label: "Manpower" },
    { key: "EQUIPMENT", label: "Equipment" },
    { key: "MATERIAL", label: "Material" },
  ];
  return (
    <div className="mt-2 flex flex-wrap gap-2">
      {pills.map((p) => {
        const active = value === p.key;
        return (
          <button
            key={p.key}
            type="button"
            onClick={() => onChange(p.key)}
            className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors ${
              active
                ? "bg-accent text-accent-foreground"
                : "border border-border bg-surface-hover text-text-secondary hover:bg-surface-active"
            }`}
          >
            {p.label}
            <span
              className={`rounded-full px-1.5 text-[10px] ${
                active ? "bg-black/20 text-accent-foreground" : "bg-surface-active text-text-muted"
              }`}
            >
              {counts[p.key]}
            </span>
          </button>
        );
      })}
    </div>
  );
}
