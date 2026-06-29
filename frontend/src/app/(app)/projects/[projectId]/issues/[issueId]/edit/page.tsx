"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle, CalendarDays, CheckCircle2, Clock, History, UserRound } from "lucide-react";
import { dprIssueApi, type UpdateDprIssueRequest } from "@/lib/api/dprIssueApi";
import { activityApi } from "@/lib/api/activityApi";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { Badge } from "@/components/ui/badge";
import {
  CATEGORY_OPTIONS,
  SEVERITY_OPTIONS,
  STATUS_OPTIONS,
  SEVERITY_VARIANT,
  STATUS_VARIANT,
  statusLabel,
} from "@/components/dpr/IssueBadges";
import { useIssueAssignees } from "@/components/dpr/useIssueAssignees";
import {
  IssueFormShell,
  SectionHeading,
  FieldLabel,
  InlineError,
  MetaStat,
  AccentPanel,
  fieldInput,
  STATUS_DOT,
} from "@/components/dpr/issueFormUi";
import { ResourceAvatar } from "@/components/resource/supervisor-assign/ResourceAvatar";
import { getErrorMessage } from "@/lib/utils/error";
import type { IssueCategory, IssueSeverity, IssueStatus } from "@/lib/types/dpr";

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
      queryClient.invalidateQueries({ queryKey: ["dpr-issue-history", projectId, issueId] });
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
    <div className="mx-auto max-w-3xl space-y-5 py-2">
      <IssueFormShell
        severity={form.severity}
        kicker="Edit Issue"
        title={form.title || "Untitled issue"}
        pills={
          <>
            <Badge variant={SEVERITY_VARIANT[form.severity]}>
              {SEVERITY_OPTIONS.find((o) => o.value === form.severity)?.label ?? form.severity}
            </Badge>
            <Badge variant={STATUS_VARIANT[form.status]} withDot>
              {statusLabel(form.status)}
            </Badge>
          </>
        }
      >
        {/* Read-only context strip */}
        <div className="grid grid-cols-2 gap-x-4 gap-y-4 border-b border-border bg-surface-hover/40 px-6 py-4 sm:grid-cols-4 sm:px-8">
          <MetaStat
            icon={<UserRound className="h-4 w-4" />}
            label="Logged by"
            value={issue?.supervisorName ?? "—"}
          />
          <MetaStat
            icon={<CalendarDays className="h-4 w-4" />}
            label="Report date"
            value={fmtDate(issue?.reportDate)}
          />
          <MetaStat
            icon={<Clock className="h-4 w-4" />}
            label="Opened"
            value={fmtDate(issue?.openedAt)}
          />
          <MetaStat
            icon={<CheckCircle2 className="h-4 w-4" />}
            label="Resolved"
            value={fmtDate(issue?.resolvedAt)}
          />
        </div>

        <form onSubmit={handleSubmit}>
          <div className="space-y-7 px-6 py-6 sm:px-8">
            {formError && (
              <div className="flex items-start gap-2 rounded-lg border border-danger/30 bg-danger/10 px-3.5 py-2.5 text-sm text-danger">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{formError}</span>
              </div>
            )}

            <div className="space-y-5">
              <SectionHeading>Details</SectionHeading>

              <div>
                <FieldLabel required>Title</FieldLabel>
                <input
                  type="text"
                  maxLength={150}
                  value={form.title}
                  onChange={(e) => set("title", e.target.value)}
                  className={fieldInput}
                />
                <InlineError message={errors.title} />
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                <div>
                  <FieldLabel required>Category</FieldLabel>
                  <SearchableSelect
                    options={CATEGORY_OPTIONS}
                    value={form.category}
                    onChange={(v) => set("category", v as IssueCategory)}
                    placeholder="Select category"
                    className="mt-1.5"
                  />
                </div>
                <div>
                  <FieldLabel required>Severity</FieldLabel>
                  <SearchableSelect
                    options={SEVERITY_OPTIONS}
                    value={form.severity}
                    onChange={(v) => set("severity", v as IssueSeverity)}
                    placeholder="Select severity"
                    className="mt-1.5"
                  />
                </div>
                <div>
                  <FieldLabel required>Status</FieldLabel>
                  <SearchableSelect
                    options={STATUS_OPTIONS}
                    value={form.status}
                    onChange={(v) => set("status", v as IssueStatus)}
                    placeholder="Select status"
                    className="mt-1.5"
                  />
                </div>
              </div>

              <div>
                <FieldLabel>Description</FieldLabel>
                <textarea
                  maxLength={2000}
                  value={form.description}
                  onChange={(e) => set("description", e.target.value)}
                  rows={3}
                  className={fieldInput}
                />
              </div>
            </div>

            <div className="space-y-5">
              <SectionHeading>Assignment &amp; context</SectionHeading>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <FieldLabel>Activity</FieldLabel>
                  <SearchableSelect
                    options={activityOptions}
                    value={form.activityId}
                    onChange={handleActivityChange}
                    placeholder="Search activities…"
                    loading={activitiesLoading}
                    selectedLabel={
                      form.activityId
                        ? activityOptions.find((o) => o.value === form.activityId)?.label ??
                          form.activityName
                        : undefined
                    }
                    className="mt-1.5"
                  />
                </div>
                <div>
                  <FieldLabel required={assigneeRequired}>Assigned To</FieldLabel>
                  <SearchableSelect
                    options={assigneeOptions}
                    value={form.assignedToUserId}
                    onChange={handleAssigneeChange}
                    placeholder="Select a project team member…"
                    loading={assigneesLoading}
                    selectedLabel={form.assignedToName || undefined}
                    className="mt-1.5"
                  />
                  {form.assignedToUserId && (
                    <div className="mt-2 inline-flex items-center gap-2 rounded-full border border-border bg-surface-hover/70 py-1 pl-1 pr-3">
                      <ResourceAvatar
                        id={form.assignedToUserId}
                        name={form.assignedToName}
                        size="sm"
                      />
                      <span className="text-sm text-text-primary">{form.assignedToName}</span>
                    </div>
                  )}
                  <InlineError message={errors.assignedTo} />
                </div>
              </div>
            </div>

            {showResolution && (
              <div className="space-y-3">
                <SectionHeading>Resolution</SectionHeading>
                <AccentPanel tone="emerald">
                  <FieldLabel required>Resolution Notes</FieldLabel>
                  <textarea
                    maxLength={1000}
                    value={form.resolutionNotes}
                    onChange={(e) => set("resolutionNotes", e.target.value)}
                    rows={3}
                    placeholder="How was this issue resolved?"
                    className={fieldInput}
                  />
                  <InlineError message={errors.resolutionNotes} />
                </AccentPanel>
              </div>
            )}
          </div>

          <div className="flex justify-end gap-3 border-t border-border bg-surface-hover/50 px-6 py-4 sm:px-8">
            <button
              type="button"
              onClick={() => router.push(`/projects/${projectId}/issues`)}
              className="rounded-lg border border-border bg-surface px-4 py-2 text-sm font-medium text-text-secondary transition-colors hover:bg-surface-hover"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              className="rounded-lg bg-accent px-5 py-2 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-accent-hover disabled:opacity-50"
            >
              {mutation.isPending ? "Saving…" : "Save Changes"}
            </button>
          </div>
        </form>
      </IssueFormShell>

      {/* Status history timeline */}
      <div className="rounded-2xl border border-border bg-surface p-6 shadow-sm sm:p-7">
        <div className="flex items-center gap-2">
          <History className="h-4 w-4 text-text-muted" />
          <h2 className="text-sm font-semibold text-text-primary">Status history</h2>
        </div>
        {history.length === 0 ? (
          <p className="mt-3 text-sm text-text-muted">No status changes recorded yet.</p>
        ) : (
          <ol className="mt-4 space-y-0">
            {history.map((h, i) => {
              const actor = (h.actorUserId && nameByUserId.get(h.actorUserId)) || "System";
              const last = i === history.length - 1;
              return (
                <li key={h.id} className="relative flex gap-4 pb-5 last:pb-0">
                  {!last && (
                    <span
                      className="absolute left-[7px] top-5 bottom-0 w-px bg-border"
                      aria-hidden
                    />
                  )}
                  <span
                    className="relative mt-1 h-3.5 w-3.5 shrink-0 rounded-full ring-4 ring-surface"
                    style={{ backgroundColor: STATUS_DOT[h.toStatus] }}
                    aria-hidden
                  />
                  <div className="min-w-0 flex-1">
                    <div className="text-sm text-text-primary">
                      {h.fromStatus ? (
                        <>
                          <span className="text-text-secondary">{statusLabel(h.fromStatus)}</span>
                          <span className="mx-1.5 text-text-muted">→</span>
                          <span className="font-semibold">{statusLabel(h.toStatus)}</span>
                        </>
                      ) : (
                        <>
                          Created as <span className="font-semibold">{statusLabel(h.toStatus)}</span>
                        </>
                      )}
                    </div>
                    <div className="mt-1 flex items-center gap-1.5 text-xs text-text-muted">
                      <ResourceAvatar id={h.actorUserId ?? "system"} name={actor} size="sm" />
                      <span className="font-medium text-text-secondary">{actor}</span>
                      <span>·</span>
                      <span>{new Date(h.createdAt).toLocaleString()}</span>
                    </div>
                    {h.reason && (
                      <div className="mt-1.5 rounded-md border border-border bg-surface-hover/50 px-3 py-2 text-sm text-text-secondary">
                        {h.reason}
                      </div>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>
        )}
      </div>
    </div>
  );
}
