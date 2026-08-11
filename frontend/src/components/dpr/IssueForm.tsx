"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle, CalendarDays, CheckCircle2, Clock, History, Lock, UserRound } from "lucide-react";
import { dprIssueApi, type UpdateDprIssueRequest } from "@/lib/api/dprIssueApi";
import type { CreateDprIssueRequest, DprIssueRow } from "@/lib/types/dpr";
import { activityApi } from "@/lib/api/activityApi";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { Badge } from "@/components/ui/badge";
import {
  CATEGORY_OPTIONS,
  HSE_INCIDENT_TYPE_OPTIONS,
  SEVERITY_OPTIONS,
  STATUS_OPTIONS,
  SEVERITY_VARIANT,
  STATUS_VARIANT,
  statusLabel,
} from "@/components/dpr/IssueBadges";
import { useIssueAssignees } from "@/components/dpr/useIssueAssignees";
import {
  SectionHeading,
  FieldLabel,
  InlineError,
  MetaStat,
  AccentPanel,
  fieldInput,
  SEVERITY_ACCENT,
  STATUS_DOT,
} from "@/components/dpr/issueFormUi";
import { ResourceAvatar } from "@/components/resource/supervisor-assign/ResourceAvatar";
import { getErrorMessage } from "@/lib/utils/error";
import type { HseIncidentType, IssueCategory, IssueSeverity, IssueStatus } from "@/lib/types/dpr";

const ASSIGNEE_REQUIRED: IssueStatus[] = ["IN_PROGRESS", "BLOCKED", "RESOLVED", "CLOSED"];
const TERMINAL: IssueStatus[] = ["RESOLVED", "CLOSED"];
const today = () => new Date().toISOString().slice(0, 10);

interface FormState {
  title: string;
  description: string;
  category: IssueCategory;
  hseIncidentType: HseIncidentType | "";
  severity: IssueSeverity;
  status: IssueStatus;
  activityId: string;
  activityName: string;
  assignedToUserId: string;
  assignedToName: string;
  resolutionNotes: string;
  reportDate: string;
  statusChangeReason: string;
  interventionRequired: boolean;
  dueDate: string;
}

function initialState(issue: DprIssueRow | null): FormState {
  return {
    title: issue?.title ?? "",
    description: issue?.description ?? "",
    category: issue?.category ?? "OTHER",
    hseIncidentType: issue?.hseIncidentType ?? "",
    severity: issue?.severity ?? "MEDIUM",
    status: issue?.status ?? "OPEN",
    activityId: issue?.activityId ?? "",
    activityName: issue?.activityName ?? "",
    assignedToUserId: issue?.assignedToUserId ?? "",
    assignedToName: issue?.assignedToName ?? "",
    resolutionNotes: issue?.resolutionNotes ?? "",
    reportDate: issue?.reportDate ?? today(),
    statusChangeReason: "",
    interventionRequired: issue?.interventionRequired ?? false,
    dueDate: issue?.dueDate ?? "",
  };
}

function fmtDate(iso?: string | null): string {
  return iso ? new Date(iso).toLocaleDateString() : "—";
}

/** Lifecycle instants (opened/resolved/closed) show the clock time, not just the date. */
function fmtDateTime(iso?: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? "—"
    : d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export interface IssueFormProps {
  projectId: string;
  /** null → create mode; a row → edit mode. */
  issue: DprIssueRow | null;
  onSaved: () => void;
  onCancel: () => void;
}

/**
 * The single Issue create/edit surface, used both inside the list-page Drawer and
 * by the standalone route pages. Mode is driven by `issue` (null = create). Scrolls
 * within its container; the action bar sits at the bottom of the scroll region.
 */
export function IssueForm({ projectId, issue, onSaved, onCancel }: IssueFormProps) {
  const isEdit = !!issue;
  const queryClient = useQueryClient();

  const [form, setForm] = useState<FormState>(() => initialState(issue));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const { options: assigneeOptions, nameByUserId, isLoading: assigneesLoading } =
    useIssueAssignees(projectId);

  const { data: activitiesData, isLoading: activitiesLoading } = useQuery({
    queryKey: ["activities", projectId, "all"],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
    enabled: !!projectId,
  });

  const { data: historyData } = useQuery({
    queryKey: ["dpr-issue-history", projectId, issue?.id],
    queryFn: () => dprIssueApi.history(projectId, issue!.id!),
    enabled: isEdit && !!issue?.id,
  });

  const activityOptions = useMemo(
    () =>
      (activitiesData?.data?.content ?? []).map((a) => ({
        value: a.id,
        label: `${a.code} — ${a.name}`,
      })),
    [activitiesData]
  );

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) =>
    setForm((s) => ({ ...s, [k]: v }));

  const handleActivityChange = (activityId: string) => {
    if (!activityId) {
      setForm((s) => ({ ...s, activityId: "", activityName: "" }));
      return;
    }
    const activity = (activitiesData?.data?.content ?? []).find((a) => a.id === activityId);
    setForm((s) => ({ ...s, activityId, activityName: activity?.name ?? "" }));
  };

  const handleAssigneeChange = (userId: string) => {
    if (!userId) {
      setForm((s) => ({ ...s, assignedToUserId: "", assignedToName: "" }));
      return;
    }
    const label = assigneeOptions.find((o) => o.value === userId)?.label ?? "";
    setForm((s) => ({ ...s, assignedToUserId: userId, assignedToName: label }));
  };

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["dpr-issues", projectId] });
    if (issue?.id) {
      queryClient.invalidateQueries({ queryKey: ["dpr-issue", projectId, issue.id] });
      queryClient.invalidateQueries({ queryKey: ["dpr-issue-history", projectId, issue.id] });
    }
  };

  const mutation = useMutation({
    mutationFn: () => {
      if (isEdit) {
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
          hseIncidentType:
            form.category === "SAFETY" || form.category === "ENVIRONMENTAL"
              ? form.hseIncidentType || null
              : null,
          statusChangeReason:
            issue && form.status !== issue.status && !TERMINAL.includes(form.status)
              ? form.statusChangeReason || null
              : null,
          interventionRequired: form.interventionRequired,
          dueDate: form.dueDate || null,
        };
        return dprIssueApi.patch(projectId, issue!.id!, body);
      }
      const body: CreateDprIssueRequest = {
        title: form.title,
        description: form.description || null,
        category: form.category,
        severity: form.severity,
        status: form.status,
        assignedToUserId: form.assignedToUserId || null,
        assignedToName: form.assignedToName || null,
        activityId: form.activityId || null,
        activityName: form.activityName || null,
        hseIncidentType:
          form.category === "SAFETY" || form.category === "ENVIRONMENTAL"
            ? form.hseIncidentType || null
            : null,
        reportDate: form.reportDate || today(),
        interventionRequired: form.interventionRequired,
        dueDate: form.dueDate || null,
      };
      return dprIssueApi.create(projectId, body);
    },
    onSuccess: () => {
      invalidate();
      onSaved();
    },
    onError: (err) => setFormError(getErrorMessage(err)),
  });

  const validate = (): boolean => {
    const next: Record<string, string> = {};
    if (!form.title.trim()) next.title = "Title is required.";
    if (ASSIGNEE_REQUIRED.includes(form.status) && !form.assignedToUserId) {
      next.assignedTo = "Assigned To is required for this status.";
    }
    if (TERMINAL.includes(form.status) && !form.resolutionNotes.trim()) {
      next.resolutionNotes = "Resolution notes are required to resolve or close.";
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    if (!validate()) return;
    mutation.mutate();
  };

  const showResolution = TERMINAL.includes(form.status);
  const assigneeRequired = ASSIGNEE_REQUIRED.includes(form.status);
  const statusChanged = isEdit && !!issue && form.status !== issue.status;
  // Terminal moves already capture a reason via the required Resolution Notes.
  const showStatusReason = statusChanged && !TERMINAL.includes(form.status);
  const history = historyData?.data ?? [];

  return (
    <form onSubmit={handleSubmit} className="flex min-h-full flex-col">
      <div
        className="h-1 w-full shrink-0"
        style={{ backgroundColor: SEVERITY_ACCENT[form.severity] }}
        aria-hidden
      />

      <div className="flex-1 space-y-7 px-5 py-5 sm:px-6">
        {/* live status / severity pills */}
        <div className="flex items-center justify-end gap-2">
          <Badge variant={SEVERITY_VARIANT[form.severity]}>
            {SEVERITY_OPTIONS.find((o) => o.value === form.severity)?.label ?? form.severity}
          </Badge>
          <Badge variant={STATUS_VARIANT[form.status]} withDot>
            {statusLabel(form.status)}
          </Badge>
        </div>

        {isEdit && (
          <div className="grid grid-cols-2 gap-x-4 gap-y-4 rounded-xl border border-border bg-surface-hover/40 px-4 py-3.5 sm:grid-cols-5">
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
              value={fmtDateTime(issue?.openedAt)}
            />
            <MetaStat
              icon={<CheckCircle2 className="h-4 w-4" />}
              label="Resolved"
              value={fmtDateTime(issue?.resolvedAt)}
            />
            <MetaStat
              icon={<Lock className="h-4 w-4" />}
              label="Closed"
              value={fmtDateTime(issue?.closedAt)}
            />
          </div>
        )}

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
              placeholder="Brief summary of the issue"
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

          {(form.category === "SAFETY" || form.category === "ENVIRONMENTAL") && (
            <div>
              <FieldLabel>HSE incident type</FieldLabel>
              <SearchableSelect
                options={HSE_INCIDENT_TYPE_OPTIONS}
                value={form.hseIncidentType}
                onChange={(v) => set("hseIncidentType", v as HseIncidentType | "")}
                placeholder="Select HSE incident type"
                className="mt-1.5"
              />
              <p className="mt-1.5 text-xs text-text-muted">
                Classifies this issue for the project HSE statistics. Leave blank if it is not a
                reportable HSE incident.
              </p>
            </div>
          )}

          <div>
            <FieldLabel>Description</FieldLabel>
            <textarea
              maxLength={2000}
              value={form.description}
              onChange={(e) => set("description", e.target.value)}
              rows={3}
              placeholder="Detailed description of the issue…"
              className={fieldInput}
            />
          </div>

          <label className="flex cursor-pointer select-none items-start gap-2.5">
            <input
              type="checkbox"
              checked={form.interventionRequired}
              onChange={(e) => set("interventionRequired", e.target.checked)}
              className="mt-0.5 h-4 w-4 accent-accent"
            />
            <span>
              <span className="text-sm font-medium text-text-primary">
                Next-level intervention required
              </span>
              <span className="block text-xs text-text-muted">
                Flags this issue for project control to assign a responsible person.
              </span>
            </span>
          </label>

          {showStatusReason && (
            <AccentPanel tone="gold">
              <FieldLabel>Reason for status change</FieldLabel>
              <textarea
                maxLength={1000}
                value={form.statusChangeReason}
                onChange={(e) => set("statusChangeReason", e.target.value)}
                rows={2}
                placeholder={`Why is this issue moving to "${statusLabel(form.status)}"? (optional)`}
                className={fieldInput}
              />
              <p className="mt-1.5 text-xs text-text-muted">
                Recorded on the status-history timeline.
              </p>
            </AccentPanel>
          )}
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
            {isEdit ? (
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
                    <ResourceAvatar id={form.assignedToUserId} name={form.assignedToName} size="sm" />
                    <span className="text-sm text-text-primary">{form.assignedToName}</span>
                  </div>
                )}
                <InlineError message={errors.assignedTo} />
              </div>
            ) : (
              <div>
                <FieldLabel>Report Date</FieldLabel>
                <input
                  type="date"
                  value={form.reportDate}
                  onChange={(e) => set("reportDate", e.target.value)}
                  className={fieldInput}
                />
              </div>
            )}
            <div>
              <FieldLabel>Act by (due date)</FieldLabel>
              <input
                type="date"
                value={form.dueDate}
                onChange={(e) => set("dueDate", e.target.value)}
                className={fieldInput}
              />
            </div>
          </div>

          {!isEdit && (
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
                  <ResourceAvatar id={form.assignedToUserId} name={form.assignedToName} size="sm" />
                  <span className="text-sm text-text-primary">{form.assignedToName}</span>
                </div>
              )}
              <InlineError message={errors.assignedTo} />
            </div>
          )}
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

        {isEdit && (
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <History className="h-4 w-4 text-text-muted" />
              <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-text-muted">
                Status history
              </span>
            </div>
            {history.length === 0 ? (
              <p className="text-sm text-text-muted">No status changes recorded yet.</p>
            ) : (
              <ol className="space-y-0">
                {history.map((h, i) => {
                  const actor = (h.actorUserId && nameByUserId.get(h.actorUserId)) || "System";
                  const last = i === history.length - 1;
                  return (
                    <li key={h.id} className="relative flex gap-4 pb-5 last:pb-0">
                      {!last && (
                        <span className="absolute left-[7px] top-5 bottom-0 w-px bg-border" aria-hidden />
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
        )}
      </div>

      <div className="sticky bottom-0 flex justify-end gap-3 border-t border-border bg-surface/95 px-5 py-4 backdrop-blur sm:px-6">
        <button
          type="button"
          onClick={onCancel}
          className="rounded-lg border border-border bg-surface px-4 py-2 text-sm font-medium text-text-secondary transition-colors hover:bg-surface-hover"
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={mutation.isPending}
          className="rounded-lg bg-accent px-5 py-2 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-accent-hover disabled:opacity-50"
        >
          {mutation.isPending ? "Saving…" : isEdit ? "Save Changes" : "Log Issue"}
        </button>
      </div>
    </form>
  );
}
