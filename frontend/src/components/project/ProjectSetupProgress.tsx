"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Check, Circle, X } from "lucide-react";
import { activityApi } from "@/lib/api/activityApi";
import type { ProjectResponse } from "@/lib/types";

interface Props {
  projectId: string;
  project: ProjectResponse;
  poolSize: number;
}

interface ChecklistItem {
  key: string;
  label: string;
  done: boolean;
  href: string;
}

const dismissKey = (projectId: string) => `project-setup-progress-dismissed:${projectId}`;

export function ProjectSetupProgress({ projectId, project, poolSize }: Props) {
  const [dismissed, setDismissed] = useState<boolean | null>(null);

  useEffect(() => {
    if (typeof window === "undefined") return;
    // Reading localStorage on mount: localStorage is external state that's
    // unavailable during SSR, so we must hydrate it via an effect.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setDismissed(window.localStorage.getItem(dismissKey(projectId)) === "1");
  }, [projectId]);

  const { data: activitiesData } = useQuery({
    queryKey: ["activities-count", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 1),
  });

  const activityCount = activitiesData?.data?.totalElements ?? 0;
  const hasBaseline = Boolean(project.activeBaselineId ?? project.primaryBaselineId);

  const items: ChecklistItem[] = [
    {
      key: "created",
      label: "Project created",
      done: true,
      href: `/projects/${projectId}`,
    },
    {
      key: "team",
      label: poolSize > 0 ? `Team assigned (${poolSize})` : "Add team & resources",
      done: poolSize > 0,
      href: `/projects/${projectId}?tab=resources`,
    },
    {
      key: "activities",
      label: activityCount > 0 ? `Activities planned (${activityCount})` : "Plan activities",
      done: activityCount > 0,
      href: `/projects/${projectId}/activities`,
    },
    {
      key: "baseline",
      label: hasBaseline ? "Baseline taken" : "Take a baseline",
      done: hasBaseline,
      href: `/projects/${projectId}?tab=baselines`,
    },
  ];

  const doneCount = items.filter((i) => i.done).length;

  const handleDismiss = () => {
    if (typeof window === "undefined") return;
    window.localStorage.setItem(dismissKey(projectId), "1");
    setDismissed(true);
  };

  // Hide while resolving dismissal state, after dismissed, or when fully complete.
  if (dismissed === null || dismissed || doneCount === items.length) return null;

  return (
    <div className="rounded-xl border border-border bg-surface/50 p-5 shadow-lg">
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-baseline gap-3">
          <h3 className="text-sm font-medium uppercase tracking-wider text-text-secondary">
            Project Setup
          </h3>
          <span className="text-xs text-text-muted">
            {doneCount} of {items.length} done
          </span>
        </div>
        <button
          type="button"
          onClick={handleDismiss}
          className="rounded-md p-1 text-text-secondary hover:bg-surface-hover hover:text-text-primary"
          aria-label="Dismiss setup checklist"
        >
          <X size={14} />
        </button>
      </div>
      <div className="grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-4">
        {items.map((item) => (
          <Link
            key={item.key}
            href={item.href}
            className="group flex items-center gap-2 rounded-md py-1 text-sm transition-colors"
          >
            {item.done ? (
              <Check size={16} className="flex-shrink-0 text-success" strokeWidth={2.5} />
            ) : (
              <Circle size={16} className="flex-shrink-0 text-text-muted" />
            )}
            <span
              className={
                item.done
                  ? "text-text-secondary line-through decoration-success/60"
                  : "text-text-primary group-hover:text-accent group-hover:underline"
              }
            >
              {item.label}
            </span>
          </Link>
        ))}
      </div>
    </div>
  );
}
