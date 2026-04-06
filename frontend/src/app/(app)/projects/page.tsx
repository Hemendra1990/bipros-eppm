"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2 } from "lucide-react";
import Link from "next/link";
import { projectApi } from "@/lib/api/projectApi";

export default function ProjectsPage() {
  const queryClient = useQueryClient();
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

  const projects = data?.data?.content ?? [];

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

      {isLoading && (
        <div className="py-12 text-center text-slate-500">Loading projects...</div>
      )}

      {error && (
        <div className="rounded-md bg-red-500/10 p-4 text-sm text-red-400">
          Failed to load projects. Is the backend running?
        </div>
      )}

      {!isLoading && projects.length === 0 && (
        <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
          <p className="text-slate-500">No projects yet. Create your first project to get started.</p>
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
                    {project.plannedStartDate}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-400">
                    {project.plannedFinishDate}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-400">
                    {project.priority}
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
