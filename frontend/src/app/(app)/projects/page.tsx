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
        <h1 className="text-2xl font-bold text-white">Projects</h1>
        <Link
          href="/projects/new"
          className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
        >
          <Plus size={16} />
          New Project
        </Link>
      </div>

      <div className="mb-4 flex items-center gap-4">
        <div className="relative flex-1">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
          <input
            type="text"
            placeholder="Search by code or name..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-md border border-slate-700 bg-slate-800 py-2 pl-9 pr-3 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
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
            <div key={i} className="h-14 animate-pulse rounded-lg bg-slate-800/50" />
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-md bg-red-500/10 p-4 text-sm text-red-400">
          Failed to load projects. Is the backend running?
        </div>
      )}

      {!isLoading && allProjects.length === 0 && (
        <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
          <p className="text-slate-500">No projects yet. Create your first project to get started.</p>
        </div>
      )}

      {!isLoading && allProjects.length > 0 && projects.length === 0 && (
        <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
          <p className="text-slate-500">No projects match your search criteria.</p>
        </div>
      )}

      {projects.length > 0 && (
        <div className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900/50 shadow-xl">
          <table className="min-w-full divide-y divide-slate-800/50">
            <thead className="bg-slate-900/80 border-b border-slate-700/50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Code
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Name
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Start Date
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Finish Date
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Priority
                </th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/50">
              {projects.map((project) => (
                <tr key={project.id} className="hover:bg-slate-800/30">
                  <td className="whitespace-nowrap px-6 py-4 text-sm font-medium text-blue-400">
                    <Link href={`/projects/${project.id}`}>{project.code}</Link>
                  </td>
                  <td className="px-6 py-4 text-sm text-slate-300">{project.name}</td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm">
                    <StatusBadge status={project.status} />
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-400">
                    {formatDate(project.plannedStartDate)}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-400">
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
                      className="text-blue-400 hover:text-blue-300"
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
                      className="text-red-400 hover:text-red-300 disabled:text-slate-500"
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
    PLANNED: "bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50",
    ACTIVE: "bg-emerald-500/10 text-emerald-400 ring-1 ring-emerald-500/20",
    INACTIVE: "bg-amber-500/10 text-amber-400 ring-1 ring-amber-500/20",
    COMPLETED: "bg-blue-500/10 text-blue-400 ring-1 ring-blue-500/20",
  };

  return (
    <span
      className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${styles[status] ?? "bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50"}`}
    >
      {status}
    </span>
  );
}
