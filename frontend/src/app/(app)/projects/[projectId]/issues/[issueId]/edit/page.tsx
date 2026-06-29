"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { dprIssueApi, type UpdateDprIssueRequest } from "@/lib/api/dprIssueApi";
import { activityApi } from "@/lib/api/activityApi";
import { PageHeader } from "@/components/common/PageHeader";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import {
  CATEGORY_OPTIONS,
  SEVERITY_OPTIONS,
  STATUS_OPTIONS,
  statusLabel,
} from "@/components/dpr/IssueBadges";
import { useIssueAssignees } from "@/components/dpr/useIssueAssignees";
import { getErrorMessage } from "@/lib/utils/error";
import type { IssueCategory, IssueSeverity, IssueStatus } from "@/lib/types/dpr";

const inputCls =
  "mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none text-sm";
const errCls = "mt-1 text-xs text-danger";

const ASSIGNEE_REQUIRED: IssueStatus[] = ["IN_PROGRESS", "BLOCKED", "RESOLVED", "CLOSED"];
const TERMINAL: IssueStatus[] = ["RESOLVED", "CLOSED"];

interface FormState {
  title: string;
  description: string;
  category: IssueCategory;
  severity: IssueSeverity;
  status: IssueStatus;
  activityId: string;
  activityName: string;
  assignedToUserId: string;
  assignedToName: string;
  resolutionNotes: string;
}

function fmtDate(iso?: string | null): string {
  return iso ? new Date(iso).toLocaleDateString() : "—";
}

export default function EditIssuePage() {
  const params = useParams<{ projectId: string; issueId: string }>();
  const { projectId, issueId } = params;
  const router = useRouter();
  const queryClient = useQueryClient();

  const [form, setForm] = useState<FormState | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const { data: issueData, isLoading: issueLoading } = useQuery({
    queryKey: ["dpr-issue", projectId, issueId],
    queryFn: () => dprIssueApi.get(projectId, issueId),
    enabled: !!projectId && !!issueId,
  });

  const { data: historyData } = useQuery({
    queryKey: ["dpr-issue-history", projectId, issueId],
    queryFn: () => dprIssueApi.history(projectId, issueId),
    enabled: !!projectId && !!issueId,
  });

  const { options: assigneeOptions, nameByUserId, isLoading: assigneesLoading } =
    useIssueAssignees(projectId);

  const { data: activitiesData, isLoading: activitiesLoading } = useQuery({
    queryKey: ["activities", projectId, "all"],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
    enabled: !!projectId,
  });

  const activityOptions = useMemo(
    () =>
      (activitiesData?.data?.content ?? []).map((a) => ({
        value: a.id,
        label: `${a.code} — ${a.name}`,
      })),
    [activitiesData]
  );

  useEffect(() => {
    const issue = issueData?.data;
    if (!issue || form) return;
    setForm({
      title: issue.title,
      description: issue.description ?? "",
      category: issue.category,
      severity: issue.severity,
      status: issue.status,
      activityId: issue.activityId ?? "",
      activityName: issue.activityName ?? "",
      assignedToUserId: issue.assignedToUserId ?? "",
      assignedToName: issue.assignedToName ?? "",
      resolutionNotes: issue.resolutionNotes ?? "",
    });
  }, [issueData, form]);

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) =>
    setForm((s) => (s ? { ...s, [k]: v } : s));

  const handleActivityChange = (activityId: string) => {
    if (!activityId) {
      setForm((s) => (s ? { ...s, activityId: "", activityName: "" } : s));
      return;
    }
    const activity = (activitiesData?.data?.content ?? []).find((a) => a.id === activityId);
    setForm((s) => (s ? { ...s, activityId, activityName: activity?.name ?? "" } : s));
  };

  const handleAssigneeChange = (userId: string) => {
    if (!userId) {
      setForm((s) => (s ? { ...s, assignedToUserId: "", assignedToName: "" } : s));
      return;
    }
    const label = assigneeOptions.find((o) => o.value === userId)?.label ?? "";
    setForm((s) => (s ? { ...s, assignedToUserId: userId, assignedToName: label } : s));
  };

  const mutation = useMutation({
    mutationFn: (body: UpdateDprIssueRequest) => dprIssueApi.patch(projectId, issueId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["dpr-issues", projectId] });
      queryClient.invalidateQueries({ queryKey: ["dpr-issue", projectId, issueId] });
      router.push(`/projects/${projectId}/issues`);
    },
    onError: (err) => setFormError(getErrorMessage(err)),
  });

  const validate = (f: FormState): boolean => {
    const next: Record<string, string> = {};
    if (!f.title.trim()) next.title = "Title is required.";
    if (ASSIGNEE_REQUIRED.includes(f.status) && !f.assignedToUserId) {
      next.assignedTo = "Assigned To is required for this status.";
    }
    if (TERMINAL.includes(f.status) && !f.resolutionNotes.trim()) {
      next.resolutionNotes = "Resolution notes are required to resolve or close.";
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form) return;
    setFormError(null);
    if (!validate(form)) return;
    const body: UpdateDprIssueRequest = {
      title: form.title,
      description: form.description || null,
      category: form.category,
      severity: form.severity,
      status: form.status,
      assignedToUserId: form.assignedToUserId || null,
      assignedToName: form.assignedToName || null,
      resolutionNotes: form.resolutionNotes || null,
      activityId: form.activityId || null,
      activityName: form.activityName || null,
    };
    mutation.mutate(body);
  };

  if (issueLoading || !form) {
    return <div className="p-6 text-sm text-text-muted">Loading…</div>;
  }

  const issue = issueData?.data;
  const showResolution = TERMINAL.includes(form.status);
  const assigneeRequired = ASSIGNEE_REQUIRED.includes(form.status);
  const history = historyData?.data ?? [];

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PageHeader title="Edit Issue" description="Update the details of this issue." />

      {/* Read-only context strip */}
      <div className="grid grid-cols-2 gap-3 rounded-lg border border-border bg-surface-hover px-4 py-3 text-sm sm:grid-cols-4">
        <div>
          <div className="text-xs uppercase tracking-wide text-text-muted">Logged by</div>
          <div className="text-text-primary">{issue?.supervisorName ?? "—"}</div>
        </div>
        <div>
          <div className="text-xs uppercase tracking-wide text-text-muted">Report date</div>
          <div className="text-text-primary">{fmtDate(issue?.reportDate)}</div>
        </div>
        <div>
          <div className="text-xs uppercase tracking-wide text-text-muted">Opened</div>
          <div className="text-text-primary">{fmtDate(issue?.openedAt)}</div>
        </div>
        <div>
          <div className="text-xs uppercase tracking-wide text-text-muted">Resolved</div>
          <div className="text-text-primary">{fmtDate(issue?.resolvedAt)}</div>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="space-y-5 rounded-lg border border-border bg-surface p-6">
        {formError && (
          <div className="rounded-md border border-danger/30 bg-danger/10 px-3 py-2 text-sm text-danger">
            {formError}
          </div>
        )}

        <div>
          <label className="block text-sm font-medium text-text-secondary">Title *</label>
          <input
            type="text"
            maxLength={150}
            value={form.title}
            onChange={(e) => set("title", e.target.value)}
            className={inputCls}
          />
          {errors.title && <p className={errCls}>{errors.title}</p>}
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Category *</label>
            <SearchableSelect
              options={CATEGORY_OPTIONS}
              value={form.category}
              onChange={(v) => set("category", v as IssueCategory)}
              placeholder="Select category"
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Severity *</label>
            <SearchableSelect
              options={SEVERITY_OPTIONS}
              value={form.severity}
              onChange={(v) => set("severity", v as IssueSeverity)}
              placeholder="Select severity"
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Status *</label>
            <SearchableSelect
              options={STATUS_OPTIONS}
              value={form.status}
              onChange={(v) => set("status", v as IssueStatus)}
              placeholder="Select status"
              className="mt-1"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">Description</label>
          <textarea
            maxLength={2000}
            value={form.description}
            onChange={(e) => set("description", e.target.value)}
            rows={3}
            className={inputCls}
          />
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Activity</label>
            <SearchableSelect
              options={activityOptions}
              value={form.activityId}
              onChange={handleActivityChange}
              placeholder="Search activities…"
              loading={activitiesLoading}
              selectedLabel={
                form.activityId
                  ? activityOptions.find((o) => o.value === form.activityId)?.label ?? form.activityName
                  : undefined
              }
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">
              Assigned To{assigneeRequired ? " *" : ""}
            </label>
            <SearchableSelect
              options={assigneeOptions}
              value={form.assignedToUserId}
              onChange={handleAssigneeChange}
              placeholder="Select a project team member…"
              loading={assigneesLoading}
              selectedLabel={form.assignedToName || undefined}
              className="mt-1"
            />
            {errors.assignedTo && <p className={errCls}>{errors.assignedTo}</p>}
          </div>
        </div>

        {showResolution && (
          <div>
            <label className="block text-sm font-medium text-text-secondary">Resolution Notes *</label>
            <textarea
              maxLength={1000}
              value={form.resolutionNotes}
              onChange={(e) => set("resolutionNotes", e.target.value)}
              rows={2}
              placeholder="How was this issue resolved?"
              className={inputCls}
            />
            {errors.resolutionNotes && <p className={errCls}>{errors.resolutionNotes}</p>}
          </div>
        )}

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={() => router.push(`/projects/${projectId}/issues`)}
            className="rounded-md border border-border bg-surface-hover px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending}
            className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90 disabled:opacity-50"
          >
            {mutation.isPending ? "Saving…" : "Save Changes"}
          </button>
        </div>
      </form>

      {/* Status history timeline */}
      <div className="rounded-lg border border-border bg-surface p-6">
        <h2 className="text-sm font-semibold text-text-primary">Status history</h2>
        {history.length === 0 ? (
          <p className="mt-2 text-sm text-text-muted">No status changes recorded yet.</p>
        ) : (
          <ol className="mt-3 space-y-3">
            {history.map((h) => (
              <li key={h.id} className="flex gap-3 text-sm">
                <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-accent" />
                <div>
                  <div className="text-text-primary">
                    {h.fromStatus ? `${statusLabel(h.fromStatus)} → ` : "Created as "}
                    <span className="font-medium">{statusLabel(h.toStatus)}</span>
                  </div>
                  <div className="text-xs text-text-muted">
                    {(h.actorUserId && nameByUserId.get(h.actorUserId)) || "System"} ·{" "}
                    {new Date(h.createdAt).toLocaleString()}
                  </div>
                  {h.reason && <div className="mt-0.5 text-text-secondary">{h.reason}</div>}
                </div>
              </li>
            ))}
          </ol>
        )}
      </div>
    </div>
  );
}
