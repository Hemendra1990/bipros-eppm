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
        <h1 className="text-2xl font-bold text-gray-900">Projects</h1>
        <Link
          href="/projects/new"
          className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          <Plus size={16} />
          New Project
        </Link>
      </div>

      {isLoading && (
        <div className="py-12 text-center text-gray-500">Loading projects...</div>
      )}

      {error && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          Failed to load projects. Is the backend running?
        </div>
      )}

      {!isLoading && projects.length === 0 && (
        <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
          <p className="text-gray-500">No projects yet. Create your first project to get started.</p>
        </div>
      )}

      {projects.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Code
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Name
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Start Date
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Finish Date
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Priority
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {projects.map((project) => (
                <tr key={project.id} className="hover:bg-gray-50">
                  <td className="whitespace-nowrap px-6 py-4 text-sm font-medium text-blue-600">
                    <Link href={`/projects/${project.id}`}>{project.code}</Link>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-900">{project.name}</td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm">
                    <StatusBadge status={project.status} />
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                    {project.plannedStartDate}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                    {project.plannedFinishDate}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm text-gray-500">
                    {project.priority}
                  </td>
                  <td className="whitespace-nowrap px-6 py-4 text-sm">
                    <button
                      onClick={() => {
                        if (window.confirm("Are you sure you want to delete this project?")) {
                          deleteMutation.mutate(project.id);
                        }
                      }}
                      disabled={deleteMutation.isPending}
                      className="text-red-600 hover:text-red-700 disabled:text-gray-400"
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
    PLANNED: "bg-gray-100 text-gray-700",
    ACTIVE: "bg-green-100 text-green-700",
    INACTIVE: "bg-yellow-100 text-yellow-700",
    COMPLETED: "bg-blue-100 text-blue-700",
  };

  return (
    <span
      className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${styles[status] ?? "bg-gray-100 text-gray-700"}`}
    >
      {status}
    </span>
  );
}
