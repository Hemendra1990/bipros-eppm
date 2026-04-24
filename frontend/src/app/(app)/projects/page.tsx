"use client";

import { useState, useMemo } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus, Search, Trash2, Edit2, Download } from "lucide-react";
import Link from "next/link";
import { projectApi } from "@/lib/api/projectApi";
import { formatDate, getPriorityInfo } from "@/lib/utils/format";
import { Badge } from "@/components/ui/badge";

const STATUS_OPTIONS = ["All", "PLANNED", "ACTIVE", "INACTIVE", "COMPLETED"] as const;
const PRIORITY_OPTIONS = ["All", "CRITICAL", "HIGH", "MEDIUM", "LOW"] as const;

function statusVariant(status: string) {
  switch (status) {
    case "ACTIVE": return "success" as const;
    case "PLANNED": return "gold" as const;
    case "COMPLETED": return "info" as const;
    case "INACTIVE": return "warning" as const;
    default: return "neutral" as const;
  }
}

export default function ProjectsPage() {
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("All");
  const [priorityFilter, setPriorityFilter] = useState<string>("All");

  const { data, isLoading, error } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectApi.listProjects(0, 50),
  });

  const deleteMutation = useMutation({
    mutationFn: (projectId: string) => projectApi.deleteProject(projectId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["projects"] }),
  });

  const allProjects = data?.data?.content ?? [];

  const projects = useMemo(() => {
    let out = allProjects;
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      out = out.filter(
        (p) => p.code.toLowerCase().includes(q) || p.name.toLowerCase().includes(q)
      );
    }
    if (statusFilter !== "All") out = out.filter((p) => p.status === statusFilter);
    if (priorityFilter !== "All") {
      out = out.filter(
        (p) => (p.priority ?? "").toString().toUpperCase() === priorityFilter
      );
    }
    return out;
  }, [allProjects, searchQuery, statusFilter, priorityFilter]);

  const counts = useMemo(() => {
    const active = allProjects.filter((p) => p.status === "ACTIVE").length;
    const planned = allProjects.filter((p) => p.status === "PLANNED").length;
    const completed = allProjects.filter((p) => p.status === "COMPLETED").length;
    return { active, planned, completed };
  }, [allProjects]);

  return (
    <div>
      {/* Page head */}
      <div className="mb-8 flex items-start justify-between gap-6">
        <div>
          <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep mb-1.5">
            {counts.active} active · {counts.planned} planned · {counts.completed} completed
          </div>
          <h1 className="font-display text-[38px] font-semibold leading-[1.08] tracking-tight text-charcoal"
              style={{ fontVariationSettings: "'opsz' 144" }}>
            Projects
          </h1>
          <p className="mt-2 max-w-[560px] text-sm text-slate leading-relaxed">
            All projects in your portfolio. Filter, drill in, or spin up a new programme.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            className="inline-flex h-10 items-center gap-1.5 rounded-[10px] border border-gold bg-paper px-4 text-sm font-semibold text-gold-deep hover:bg-ivory"
          >
            <Download size={14} strokeWidth={1.5} />
            Export CSV
          </button>
          <Link
            href="/projects/new"
            className="inline-flex h-10 items-center gap-1.5 rounded-[10px] bg-gold px-4 text-sm font-semibold text-paper transition-all duration-200 hover:bg-gold-deep hover:shadow-[0_4px_14px_rgba(212,175,55,0.3)] hover:-translate-y-px"
          >
            <Plus size={14} strokeWidth={2.5} />
            New project
          </Link>
        </div>
      </div>

      {/* Toolbar */}
      <div className="mb-5 flex flex-wrap items-center gap-2.5">
        <div className="flex h-10 flex-1 max-w-[340px] items-center gap-2 rounded-[10px] border border-hairline bg-paper px-3">
          <Search size={15} className="text-ash" strokeWidth={1.5} />
          <input
            type="text"
            placeholder="Search by code or name…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="flex-1 border-none bg-transparent text-sm text-charcoal placeholder:text-ash outline-none"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="h-10 rounded-[10px] border border-hairline bg-paper pl-3.5 pr-8 text-sm font-medium text-charcoal focus:border-gold focus:outline-none focus:shadow-[0_0_0_3px_rgba(212,175,55,0.18)]"
        >
          {STATUS_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {s === "All" ? "All statuses" : s}
            </option>
          ))}
        </select>
        <select
          value={priorityFilter}
          onChange={(e) => setPriorityFilter(e.target.value)}
          className="h-10 rounded-[10px] border border-hairline bg-paper pl-3.5 pr-8 text-sm font-medium text-charcoal focus:border-gold focus:outline-none focus:shadow-[0_0_0_3px_rgba(212,175,55,0.18)]"
        >
          {PRIORITY_OPTIONS.map((p) => (
            <option key={p} value={p}>
              {p === "All" ? "All priorities" : p.charAt(0) + p.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
      </div>

      {/* Active filter chips */}
      {(statusFilter !== "All" || priorityFilter !== "All") && (
        <div className="mb-3 flex items-center gap-1.5">
          {statusFilter !== "All" && (
            <button
              onClick={() => setStatusFilter("All")}
              className="inline-flex items-center gap-1.5 rounded-md border border-[#E8D68A] bg-gold-tint px-2.5 py-1 text-[11px] font-semibold text-gold-ink hover:bg-[#EFDD94]"
            >
              Status: {statusFilter} <span aria-hidden>✕</span>
            </button>
          )}
          {priorityFilter !== "All" && (
            <button
              onClick={() => setPriorityFilter("All")}
              className="inline-flex items-center gap-1.5 rounded-md border border-[#E8D68A] bg-gold-tint px-2.5 py-1 text-[11px] font-semibold text-gold-ink hover:bg-[#EFDD94]"
            >
              Priority: {priorityFilter} <span aria-hidden>✕</span>
            </button>
          )}
          <button
            onClick={() => { setStatusFilter("All"); setPriorityFilter("All"); }}
            className="ml-1 text-[11px] font-semibold text-gold-deep hover:text-gold-ink"
          >
            Clear all
          </button>
        </div>
      )}

      {isLoading && (
        <div className="space-y-3">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="h-14 animate-pulse rounded-lg bg-parchment" />
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-xl border border-[#E5C4C4] bg-[#F5E2E2] p-4 text-sm text-burgundy">
          Failed to load projects. Is the backend running?
        </div>
      )}

      {!isLoading && allProjects.length === 0 && (
        <div className="rounded-xl border border-dashed border-hairline bg-paper py-12 text-center">
          <p className="text-sm text-slate">No projects yet. Create your first project to get started.</p>
        </div>
      )}

      {!isLoading && allProjects.length > 0 && projects.length === 0 && (
        <div className="rounded-xl border border-dashed border-hairline bg-paper py-12 text-center">
          <p className="text-sm text-slate">No projects match your filters.</p>
        </div>
      )}

      {projects.length > 0 && (
        <>
          <div className="overflow-hidden rounded-xl border border-hairline bg-paper">
            <table className="w-full border-collapse text-sm">
              <thead className="border-b border-hairline bg-ivory">
                <tr>
                  {["Code","Name","Status","Start","Finish","Priority","Actions"].map((h) => (
                    <th
                      key={h}
                      className={`px-4 py-3 text-left text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep ${h === "Actions" ? "text-right" : ""}`}
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {projects.map((project) => {
                  const priority = getPriorityInfo(project.priority);
                  return (
                    <tr
                      key={project.id}
                      className="border-b border-[#F4EDD8] transition-colors last:border-b-0 hover:bg-ivory"
                    >
                      <td className="px-4 py-3.5">
                        <Link
                          href={`/projects/${project.id}`}
                          className="font-mono text-[12px] font-medium text-gold-deep hover:text-gold-ink"
                        >
                          {project.code}
                        </Link>
                      </td>
                      <td className="px-4 py-3.5 font-semibold text-charcoal">{project.name}</td>
                      <td className="px-4 py-3.5">
                        <Badge variant={statusVariant(project.status)} withDot>
                          {project.status}
                        </Badge>
                      </td>
                      <td className="px-4 py-3.5 text-slate">{formatDate(project.plannedStartDate)}</td>
                      <td className="px-4 py-3.5 text-slate">{formatDate(project.plannedFinishDate)}</td>
                      <td className={`px-4 py-3.5 font-semibold ${priority.color}`}>{priority.label}</td>
                      <td className="px-4 py-3.5">
                        <div className="flex items-center justify-end gap-1">
                          <Link
                            href={`/projects/${project.id}`}
                            className="rounded-md p-1.5 text-slate transition-colors hover:bg-parchment hover:text-gold-deep"
                            aria-label="Edit"
                          >
                            <Edit2 size={14} strokeWidth={1.5} />
                          </Link>
                          <button
                            onClick={() => {
                              if (window.confirm("Are you sure you want to delete this project?")) {
                                deleteMutation.mutate(project.id);
                              }
                            }}
                            disabled={deleteMutation.isPending}
                            className="rounded-md p-1.5 text-slate transition-colors hover:bg-parchment hover:text-burgundy disabled:cursor-not-allowed disabled:opacity-50"
                            aria-label="Delete"
                          >
                            <Trash2 size={14} strokeWidth={1.5} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="pt-3 text-center text-xs text-slate">
            Showing <span className="font-semibold text-charcoal">{projects.length} of {allProjects.length}</span>
          </div>
        </>
      )}
    </div>
  );
}
