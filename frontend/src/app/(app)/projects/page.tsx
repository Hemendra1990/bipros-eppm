"use client";

import { useState, useMemo } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search, Trash2 } from "lucide-react";
import Link from "next/link";
import { projectApi } from "@/lib/api/projectApi";
import { formatDate, getPriorityInfo } from "@/lib/utils/format";

const STATUS_OPTIONS = ["All", "PLANNED", "ACTIVE", "INACTIVE", "COMPLETED"] as const;

export default function ProjectsPage() {
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("All");

  const { data, isLoading, error } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectApi.listProjects(0, 50),
  });

  const deleteMutation = useMutation({
    mutationFn: (projectId: string) => projectApi.deleteProject(projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
    },
  });

  const allProjects = data?.data?.content ?? [];

  const projects = useMemo(() => {
    let filtered = allProjects;
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      filtered = filtered.filter(
        (p) =>
          p.code.toLowerCase().includes(q) ||
          p.name.toLowerCase().includes(q)
      );
    }
    if (statusFilter !== "All") {
      filtered = filtered.filter((p) => p.status === statusFilter);
    }
    return filtered;
  }, [allProjects, searchQuery, statusFilter]);

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-text-primary">Projects</h1>
        <Link
          href="/projects/new"
          className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
        >
          <Plus size={16} />
          New Project
        </Link>
      </div>

      <div className="mb-4 flex items-center gap-4">
        <div className="relative flex-1">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            type="text"
            placeholder="Search by code or name..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-md border border-border bg-surface-hover py-2 pl-9 pr-3 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {s === "All" ? "All Statuses" : s}
            </option>
          ))}
        </select>
      </div>

      {isLoading && (
        <div className="space-y-3">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="h-14 animate-pulse rounded-lg bg-surface-hover/50" />
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-md bg-danger/10 p-4 text-sm text-danger">
          Failed to load projects. Is the backend running?
        </div>
      )}

      {!isLoading && allProjects.length === 0 && (
        <div className="rounded-lg border border-dashed border-border py-12 text-center">
          <p className="text-text-muted">No projects yet. Create your first project to get started.</p>
        </div>
      )}

      {!isLoading && allProjects.length > 0 && projects.length === 0 && (
        <div className="rounded-lg border border-dashed border-border py-12 text-center">
          <p className="text-text-muted">No projects match your search criteria.</p>
        </div>
      )}

      {projects.length > 0 && (
        <div className="overflow-hidden rounded-xl border border-border bg-surface/50 shadow-xl">
          <table className="min-w-full divide-y divide-border/50">
            <thead className="bg-surface/80 border-b border-border/50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">
                  Code
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">
                  Name
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">
                  Start Date
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">
                  Finish Date
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">
                  Priority
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-secondary">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {projects.map((project) => (
                <tr key={project.id} className="hover:bg-surface-hover/30">
                  <td className="whitespace-nowrap px-6 py-4 text-sm font-medium text-accent">
                    <Link href={`/projects/${project.id}`}>{project.code}</Link>
                  </td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{project.name}</td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm">
                    <StatusBadge status={project.status} />
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-text-secondary">
                    {formatDate(project.plannedStartDate)}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-text-secondary">
                    {formatDate(project.plannedFinishDate)}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm">
                    <span className={getPriorityInfo(project.priority).color}>
                      {getPriorityInfo(project.priority).label}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm flex items-center gap-3">
                    <Link
                      href={`/projects/${project.id}`}
                      className="text-accent hover:text-blue-300"
                    >
                      Edit
                    </Link>
                    <button
                      onClick={() => {
                        if (window.confirm("Are you sure you want to delete this project?")) {
                          deleteMutation.mutate(project.id);
                        }
                      }}
                      disabled={deleteMutation.isPending}
                      className="text-danger hover:text-danger disabled:text-text-muted"
                    >
                      <Trash2 size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    PLANNED: "bg-surface-active/50 text-text-secondary ring-1 ring-border/50",
    ACTIVE: "bg-success/10 text-success ring-1 ring-success/20",
    INACTIVE: "bg-warning/10 text-warning ring-1 ring-amber-500/20",
    COMPLETED: "bg-accent/10 text-accent ring-1 ring-accent/20",
  };

  return (
    <span
      className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${styles[status] ?? "bg-surface-active/50 text-text-secondary ring-1 ring-border/50"}`}
    >
      {status}
    </span>
  );
}
