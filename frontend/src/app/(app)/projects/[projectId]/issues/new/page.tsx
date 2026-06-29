"use client";

import { useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { dprIssueApi } from "@/lib/api/dprIssueApi";
import { activityApi } from "@/lib/api/activityApi";
import type { CreateDprIssueRequest } from "@/lib/types/dpr";
import { PageHeader } from "@/components/common/PageHeader";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { CATEGORY_OPTIONS, SEVERITY_OPTIONS, STATUS_OPTIONS } from "@/components/dpr/IssueBadges";
import { useIssueAssignees } from "@/components/dpr/useIssueAssignees";
import type { IssueCategory, IssueSeverity, IssueStatus } from "@/lib/types/dpr";
import { getErrorMessage } from "@/lib/utils/error";

const inputCls =
  "mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none text-sm";
const errCls = "mt-1 text-xs text-danger";

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
  const assigneeRequired = ASSIGNEE_REQUIRED.includes(status);

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PageHeader title="New Issue" description="Log a field issue directly against this project." />

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
            value={state.title}
            onChange={(e) => set("title", e.target.value)}
            placeholder="Brief summary of the issue"
            className={inputCls}
          />
          {errors.title && <p className={errCls}>{errors.title}</p>}
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Category *</label>
            <SearchableSelect
              options={CATEGORY_OPTIONS}
              value={state.category ?? ""}
              onChange={(v) => set("category", v as IssueCategory)}
              placeholder="Select category"
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Severity *</label>
            <SearchableSelect
              options={SEVERITY_OPTIONS}
              value={state.severity ?? ""}
              onChange={(v) => set("severity", v as IssueSeverity)}
              placeholder="Select severity"
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Status *</label>
            <SearchableSelect
              options={STATUS_OPTIONS}
              value={state.status ?? "OPEN"}
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
            value={state.description ?? ""}
            onChange={(e) => set("description", e.target.value || null)}
            rows={3}
            placeholder="Detailed description of the issue…"
            className={inputCls}
          />
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Activity (optional)</label>
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
              className="mt-1"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary">Report Date</label>
            <input
              type="date"
              value={state.reportDate ?? today}
              onChange={(e) => set("reportDate", e.target.value)}
              className={inputCls}
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary">
            Assigned To{assigneeRequired ? " *" : ""}
          </label>
          <SearchableSelect
            options={assigneeOptions}
            value={state.assignedToUserId ?? ""}
            onChange={handleAssigneeChange}
            placeholder="Select a project team member…"
            loading={assigneesLoading}
            selectedLabel={state.assignedToName ?? undefined}
            className="mt-1"
          />
          {errors.assignedTo && <p className={errCls}>{errors.assignedTo}</p>}
        </div>

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
            {mutation.isPending ? "Saving…" : "Log Issue"}
          </button>
        </div>
      </form>
    </div>
  );
}
