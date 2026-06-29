"use client";

import { useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle } from "lucide-react";
import { dprIssueApi } from "@/lib/api/dprIssueApi";
import { activityApi } from "@/lib/api/activityApi";
import type { CreateDprIssueRequest } from "@/lib/types/dpr";
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
  fieldInput,
} from "@/components/dpr/issueFormUi";
import { ResourceAvatar } from "@/components/resource/supervisor-assign/ResourceAvatar";
import type { IssueCategory, IssueSeverity, IssueStatus } from "@/lib/types/dpr";
import { getErrorMessage } from "@/lib/utils/error";

const today = new Date().toISOString().slice(0, 10);

const ASSIGNEE_REQUIRED: IssueStatus[] = ["IN_PROGRESS", "BLOCKED", "RESOLVED", "CLOSED"];

export default function NewProjectIssuePage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const router = useRouter();
  const queryClient = useQueryClient();

  const [state, setState] = useState<CreateDprIssueRequest>({
    title: "",
    category: "OTHER",
    severity: "MEDIUM",
    status: "OPEN",
    reportDate: today,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const { options: assigneeOptions, isLoading: assigneesLoading } = useIssueAssignees(projectId);

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

  const set = <K extends keyof CreateDprIssueRequest>(k: K, v: CreateDprIssueRequest[K]) =>
    setState((s) => ({ ...s, [k]: v }));

  const handleActivityChange = (activityId: string) => {
    if (!activityId) {
      setState((s) => ({ ...s, activityId: null, activityName: null }));
      return;
    }
    const activity = (activitiesData?.data?.content ?? []).find((a) => a.id === activityId);
    setState((s) => ({ ...s, activityId, activityName: activity?.name ?? null }));
  };

  const handleAssigneeChange = (userId: string) => {
    if (!userId) {
      setState((s) => ({ ...s, assignedToUserId: null, assignedToName: null }));
      return;
    }
    const label = assigneeOptions.find((o) => o.value === userId)?.label ?? null;
    setState((s) => ({ ...s, assignedToUserId: userId, assignedToName: label }));
  };

  const mutation = useMutation({
    mutationFn: (body: CreateDprIssueRequest) => dprIssueApi.create(projectId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["dpr-issues", projectId] });
      router.push(`/projects/${projectId}/issues`);
    },
    onError: (err) => setFormError(getErrorMessage(err)),
  });

  const validate = (): boolean => {
    const next: Record<string, string> = {};
    if (!state.title.trim()) next.title = "Title is required.";
    const status = state.status ?? "OPEN";
    if (ASSIGNEE_REQUIRED.includes(status) && !state.assignedToUserId) {
      next.assignedTo = "Assigned To is required for this status.";
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    if (!validate()) return;
    mutation.mutate(state);
  };

  const status = state.status ?? "OPEN";
  const severity = state.severity ?? "MEDIUM";
  const assigneeRequired = ASSIGNEE_REQUIRED.includes(status);

  return (
    <div className="mx-auto max-w-3xl py-2">
      <IssueFormShell
        severity={severity}
        kicker="New Issue"
        title="Log a field issue"
        pills={
          <>
            <Badge variant={SEVERITY_VARIANT[severity]}>
              {SEVERITY_OPTIONS.find((o) => o.value === severity)?.label ?? severity}
            </Badge>
            <Badge variant={STATUS_VARIANT[status]} withDot>
              {statusLabel(status)}
            </Badge>
          </>
        }
      >
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
                  value={state.title}
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
                    value={state.category ?? ""}
                    onChange={(v) => set("category", v as IssueCategory)}
                    placeholder="Select category"
                    className="mt-1.5"
                  />
                </div>
                <div>
                  <FieldLabel required>Severity</FieldLabel>
                  <SearchableSelect
                    options={SEVERITY_OPTIONS}
                    value={state.severity ?? ""}
                    onChange={(v) => set("severity", v as IssueSeverity)}
                    placeholder="Select severity"
                    className="mt-1.5"
                  />
                </div>
                <div>
                  <FieldLabel required>Status</FieldLabel>
                  <SearchableSelect
                    options={STATUS_OPTIONS}
                    value={state.status ?? "OPEN"}
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
                  value={state.description ?? ""}
                  onChange={(e) => set("description", e.target.value || null)}
                  rows={3}
                  placeholder="Detailed description of the issue…"
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
                    value={state.activityId ?? ""}
                    onChange={handleActivityChange}
                    placeholder="Search activities…"
                    loading={activitiesLoading}
                    selectedLabel={
                      state.activityId
                        ? activityOptions.find((o) => o.value === state.activityId)?.label
                        : undefined
                    }
                    className="mt-1.5"
                  />
                </div>
                <div>
                  <FieldLabel>Report Date</FieldLabel>
                  <input
                    type="date"
                    value={state.reportDate ?? today}
                    onChange={(e) => set("reportDate", e.target.value)}
                    className={fieldInput}
                  />
                </div>
              </div>

              <div>
                <FieldLabel required={assigneeRequired}>Assigned To</FieldLabel>
                <SearchableSelect
                  options={assigneeOptions}
                  value={state.assignedToUserId ?? ""}
                  onChange={handleAssigneeChange}
                  placeholder="Select a project team member…"
                  loading={assigneesLoading}
                  selectedLabel={state.assignedToName ?? undefined}
                  className="mt-1.5"
                />
                {state.assignedToUserId && (
                  <div className="mt-2 inline-flex items-center gap-2 rounded-full border border-border bg-surface-hover/70 py-1 pl-1 pr-3">
                    <ResourceAvatar
                      id={state.assignedToUserId}
                      name={state.assignedToName ?? ""}
                      size="sm"
                    />
                    <span className="text-sm text-text-primary">{state.assignedToName}</span>
                  </div>
                )}
                <InlineError message={errors.assignedTo} />
              </div>
            </div>
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
              {mutation.isPending ? "Saving…" : "Log Issue"}
            </button>
          </div>
        </form>
      </IssueFormShell>
    </div>
  );
}
