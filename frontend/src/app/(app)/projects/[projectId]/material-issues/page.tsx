"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { materialCatalogueApi, materialIssueApi } from "@/lib/api/materialCatalogueApi";
import { activityApi } from "@/lib/api/activityApi";
import { userApi, type UserSummary } from "@/lib/api/userApi";
import type { CreateMaterialIssueRequest, MaterialIssueResponse } from "@/lib/types";
import { formatDate } from "@/lib/utils/format";
import { getErrorMessage } from "@/lib/utils/error";

/**
 * Material Issues (store) — issue-slip register. "Issue" here is store terminology: material
 * handed OUT of the store to a person (nothing to do with the problems Issues tab). Each slip
 * decrements the stock register and, with issued-to, powers the supervisor-wise issued
 * material report (MAT-04).
 */

// Same directory roles the storekeeper daily-log pickers use.
const ISSUED_TO_ROLES = [
  "STOREKEEPER",
  "SUPERVISOR",
  "FOREMAN",
  "SITE_ENGINEER",
  "SITE_MANAGER",
  "PROJECT_MANAGER",
];

interface FormState {
  materialId: string;
  issueDate: string;
  quantity: string;
  issuedToUserId: string;
  activityId: string;
  wastageQuantity: string;
  remarks: string;
}

const emptyForm = (): FormState => ({
  materialId: "",
  issueDate: new Date().toISOString().split("T")[0],
  quantity: "",
  issuedToUserId: "",
  activityId: "",
  wastageQuantity: "",
  remarks: "",
});

export default function MaterialIssuesPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const queryClient = useQueryClient();

  const { data: issuesData, isLoading, error } = useQuery({
    queryKey: ["material-issues", projectId],
    queryFn: () => materialIssueApi.listByProject(projectId),
    enabled: !!projectId,
  });
  const issues: MaterialIssueResponse[] = useMemo(
    () => issuesData?.data ?? [],
    [issuesData],
  );

  const { data: materialsData } = useQuery({
    queryKey: ["materials", projectId],
    queryFn: () => materialCatalogueApi.listByProject(projectId),
    enabled: !!projectId,
  });
  const materials = useMemo(() => materialsData?.data ?? [], [materialsData]);
  const materialById = useMemo(
    () => new Map(materials.map((m) => [m.id, m])),
    [materials],
  );

  const { data: users } = useQuery<UserSummary[]>({
    queryKey: ["users", "by-roles", ISSUED_TO_ROLES],
    queryFn: () => userApi.listByRoles(ISSUED_TO_ROLES),
  });
  const userById = useMemo(
    () => new Map((users ?? []).map((u) => [u.id, u])),
    [users],
  );

  const { data: activitiesData } = useQuery({
    queryKey: ["activities", projectId, "for-material-issues"],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
    enabled: !!projectId,
  });
  const activities = activitiesData?.data?.content ?? [];
  const activityById = useMemo(
    () => new Map(activities.map((a) => [a.id, a])),
    [activities],
  );

  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [formError, setFormError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: (body: CreateMaterialIssueRequest) =>
      materialIssueApi.create(projectId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["material-issues", projectId] });
      queryClient.invalidateQueries({ queryKey: ["stock-register", projectId] });
      setForm(emptyForm());
      setFormOpen(false);
      setFormError(null);
    },
    onError: (e) => setFormError(getErrorMessage(e)),
  });

  const handleSubmit = () => {
    if (!form.materialId) return setFormError("Pick the material being issued.");
    const qty = Number(form.quantity);
    if (!form.quantity || Number.isNaN(qty) || qty <= 0)
      return setFormError("Quantity must be a positive number.");
    if (!form.issueDate) return setFormError("Issue date is required.");
    setFormError(null);
    createMutation.mutate({
      materialId: form.materialId,
      issueDate: form.issueDate,
      quantity: qty,
      issuedToUserId: form.issuedToUserId || null,
      activityId: form.activityId || null,
      wastageQuantity: form.wastageQuantity ? Number(form.wastageQuantity) : null,
      remarks: form.remarks || null,
    });
  };

  const userLabel = (id: string | null) => {
    if (!id) return "—";
    const u = userById.get(id);
    return u ? u.name || u.username : "(unknown user)";
  };

  return (
    <div className="space-y-4 p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Material Issues (Store)</h1>
          <p className="text-sm text-text-muted">
            Issue slips — material handed out of the store to a person. Each slip lowers the stock
            register and feeds the supervisor-wise issued material report. (Not the problems
            &ldquo;Issues&rdquo; tab.)
          </p>
        </div>
        <button
          type="button"
          onClick={() => setFormOpen((v) => !v)}
          className="rounded bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90"
        >
          {formOpen ? "Close" : "+ New issue slip"}
        </button>
      </div>

      {materials.length === 0 && (
        <div className="rounded-lg border border-amber-300 bg-amber-500/10 px-3 py-2 text-sm text-amber-800">
          The Material Catalogue is empty — create the materials first (Master Data → Material
          Catalogue), then record receipts (GRNs) and issue slips against them.
        </div>
      )}

      {formOpen && (
        <div className="space-y-3 rounded-lg border border-border bg-surface p-4">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
            <label className="text-sm">
              <span className="mb-1 block text-text-muted">Material *</span>
              <select
                value={form.materialId}
                onChange={(e) => setForm({ ...form, materialId: e.target.value })}
                className="w-full rounded border border-border bg-background px-2 py-1.5"
              >
                <option value="">Select material…</option>
                {materials.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.code} — {m.name}
                    {m.unit ? ` (${m.unit})` : ""}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-sm">
              <span className="mb-1 block text-text-muted">Issue date *</span>
              <input
                type="date"
                value={form.issueDate}
                onChange={(e) => setForm({ ...form, issueDate: e.target.value })}
                className="w-full rounded border border-border bg-background px-2 py-1.5"
              />
            </label>
            <label className="text-sm">
              <span className="mb-1 block text-text-muted">Quantity *</span>
              <input
                type="number"
                min="0"
                step="any"
                value={form.quantity}
                onChange={(e) => setForm({ ...form, quantity: e.target.value })}
                className="w-full rounded border border-border bg-background px-2 py-1.5"
              />
            </label>
            <label className="text-sm">
              <span className="mb-1 block text-text-muted">Issued to</span>
              <select
                value={form.issuedToUserId}
                onChange={(e) => setForm({ ...form, issuedToUserId: e.target.value })}
                className="w-full rounded border border-border bg-background px-2 py-1.5"
              >
                <option value="">(not recorded)</option>
                {(users ?? []).map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name || u.username}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-sm">
              <span className="mb-1 block text-text-muted">Activity (optional)</span>
              <select
                value={form.activityId}
                onChange={(e) => setForm({ ...form, activityId: e.target.value })}
                className="w-full rounded border border-border bg-background px-2 py-1.5"
              >
                <option value="">(none)</option>
                {activities.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name || a.code}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-sm">
              <span className="mb-1 block text-text-muted">Wastage qty (optional)</span>
              <input
                type="number"
                min="0"
                step="any"
                value={form.wastageQuantity}
                onChange={(e) => setForm({ ...form, wastageQuantity: e.target.value })}
                className="w-full rounded border border-border bg-background px-2 py-1.5"
              />
            </label>
            <label className="text-sm md:col-span-3">
              <span className="mb-1 block text-text-muted">Remarks</span>
              <input
                type="text"
                maxLength={500}
                value={form.remarks}
                onChange={(e) => setForm({ ...form, remarks: e.target.value })}
                className="w-full rounded border border-border bg-background px-2 py-1.5"
              />
            </label>
          </div>
          {formError && (
            <div className="rounded border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-700">
              {formError}
            </div>
          )}
          <button
            type="button"
            onClick={handleSubmit}
            disabled={createMutation.isPending}
            className="rounded bg-primary px-4 py-1.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
          >
            {createMutation.isPending ? "Saving…" : "Record issue slip"}
          </button>
        </div>
      )}

      {error && (
        <div className="rounded border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-700">
          {getErrorMessage(error)}
        </div>
      )}

      <div className="overflow-x-auto rounded-lg border border-border bg-surface">
        <table className="min-w-full text-sm">
          <thead className="bg-surface-active text-xs uppercase tracking-wide text-text-muted">
            <tr>
              <th className="px-2 py-2 text-left">Challan #</th>
              <th className="px-2 py-2 text-left">Date</th>
              <th className="px-2 py-2 text-left">Material</th>
              <th className="px-2 py-2 text-right">Quantity</th>
              <th className="px-2 py-2 text-left">Issued to</th>
              <th className="px-2 py-2 text-left">Activity</th>
              <th className="px-2 py-2 text-right">Wastage</th>
              <th className="px-2 py-2 text-left">Remarks</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={8} className="px-3 py-6 text-center text-text-muted">
                  Loading…
                </td>
              </tr>
            )}
            {!isLoading && issues.length === 0 && (
              <tr>
                <td colSpan={8} className="px-3 py-6 text-center text-text-muted">
                  No issue slips recorded yet.
                </td>
              </tr>
            )}
            {issues.map((i) => {
              const material = materialById.get(i.materialId);
              const activity = i.activityId ? activityById.get(i.activityId) : undefined;
              return (
                <tr key={i.id} className="border-t border-border hover:bg-surface-active/40">
                  <td className="whitespace-nowrap px-2 py-1.5 font-mono text-xs">
                    {i.challanNumber}
                  </td>
                  <td className="whitespace-nowrap px-2 py-1.5">{formatDate(i.issueDate)}</td>
                  <td className="px-2 py-1.5">
                    {material ? `${material.code} — ${material.name}` : "(unknown material)"}
                  </td>
                  <td className="whitespace-nowrap px-2 py-1.5 text-right">
                    {i.quantity?.toLocaleString()}
                    {material?.unit ? ` ${material.unit}` : ""}
                  </td>
                  <td className="px-2 py-1.5">{userLabel(i.issuedToUserId)}</td>
                  <td className="px-2 py-1.5">
                    {activity ? activity.name || activity.code : "—"}
                  </td>
                  <td className="whitespace-nowrap px-2 py-1.5 text-right">
                    {i.wastageQuantity ?? "—"}
                  </td>
                  <td className="px-2 py-1.5">{i.remarks ?? "—"}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
