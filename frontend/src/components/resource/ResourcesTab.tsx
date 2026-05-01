"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { projectResourceApi } from "@/lib/api/projectResourceApi";
import { ProjectResourcePool } from "./ProjectResourcePool";
import { ResourceAssignmentsTab } from "./ResourceAssignmentsTab";

export function ResourcesTab({ projectId }: { projectId: string }) {
  const [activeSubTab, setActiveSubTab] = useState<"pool" | "assignments" | null>(null);

  const { data: poolData, isLoading } = useQuery({
    queryKey: ["resource-pool", projectId],
    queryFn: () => projectResourceApi.listPool(projectId),
  });

  const pool = (() => {
    const raw = poolData?.data as unknown;
    return Array.isArray(raw) ? raw : [];
  })();

  const resolvedTab = activeSubTab ?? (pool.length === 0 ? "pool" : "assignments");

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-1 border-b border-border">
        <button
          onClick={() => setActiveSubTab("pool")}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
            resolvedTab === "pool"
              ? "border-accent text-text-primary"
              : "border-transparent text-text-secondary hover:text-text-primary hover:border-border"
          }`}
        >
          Pool
          {!isLoading && (
            <span className="ml-1.5 inline-flex items-center justify-center rounded-full bg-surface-hover px-2 py-0.5 text-xs">
              {pool.length}
            </span>
          )}
        </button>
        <button
          onClick={() => setActiveSubTab("assignments")}
          className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
            resolvedTab === "assignments"
              ? "border-accent text-text-primary"
              : "border-transparent text-text-secondary hover:text-text-primary hover:border-border"
          }`}
        >
          Assignments
        </button>
      </div>

      {resolvedTab === "pool" ? (
        <ProjectResourcePool projectId={projectId} />
      ) : (
        <ResourceAssignmentsTab projectId={projectId} />
      )}
    </div>
  );
}
