"use client";

import { useState, useMemo, useCallback } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search, RotateCcw, ChevronLeft } from "lucide-react";
import Link from "next/link";
import toast from "react-hot-toast";
import { projectApi } from "@/lib/api/projectApi";
import { getErrorMessage } from "@/lib/utils/error";
import { ConfirmDialog } from "@/components/common/ConfirmDialog";

function formatArchivedAt(value: string | null | undefined) {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export default function ArchivedProjectsPage() {
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState("");
  const [restoreConfirm, setRestoreConfirm] = useState<{ open: boolean; projectId: string | null; projectName: string }>({
    open: false,
    projectId: null,
    projectName: "",
  });

  const { data, isLoading, error } = useQuery({
    queryKey: ["projects", "archived"],
    queryFn: () => projectApi.listArchivedProjects(0, 50),
  });

  const restoreMutation = useMutation({
    mutationFn: (projectId: string) => projectApi.restoreProject(projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["projects"] });
      queryClient.invalidateQueries({ queryKey: ["projects", "archived"] });
      toast.success("Project restored");
    },
    onError: (err) => {
      toast.error(getErrorMessage(err, "Failed to restore project"));
    },
  });

  const confirmRestore = useCallback(() => {
    if (restoreConfirm.projectId) {
      restoreMutation.mutate(restoreConfirm.projectId);
    }
    setRestoreConfirm({ open: false, projectId: null, projectName: "" });
  }, [restoreConfirm.projectId, restoreMutation]);

  const cancelRestore = useCallback(() => {
    setRestoreConfirm({ open: false, projectId: null, projectName: "" });
  }, []);

  const allProjects = data?.data?.content ?? [];

  const projects = useMemo(() => {
    if (!searchQuery) return allProjects;
    const q = searchQuery.toLowerCase();
    return allProjects.filter(
      (p) => p.code.toLowerCase().includes(q) || p.name.toLowerCase().includes(q)
    );
  }, [allProjects, searchQuery]);

  return (
    <div>
      {/* Page head */}
      <div className="mb-8 flex items-start justify-between gap-6">
        <div>
          <Link
            href="/projects"
            className="mb-1.5 inline-flex items-center gap-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-gold-deep hover:text-gold-ink"
          >
            <ChevronLeft size={12} strokeWidth={2} />
            Back to projects
          </Link>
          <h1
            className="font-display text-[38px] font-semibold leading-[1.08] tracking-tight text-charcoal"
            style={{ fontVariationSettings: "'opsz' 144" }}
          >
            Archived projects
          </h1>
          <p className="mt-2 max-w-[560px] text-sm text-slate leading-relaxed">
            Projects you've archived. Their data — activities, baselines, costs — is preserved.
            Restore one to bring it back to your main list.
          </p>
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
      </div>

      {isLoading && (
        <div className="space-y-3">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-14 animate-pulse rounded-lg bg-parchment" />
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-xl border border-burgundy/30 bg-burgundy/10 p-4 text-sm text-burgundy">
          Failed to load archived projects. Is the backend running?
        </div>
      )}

      {!isLoading && allProjects.length === 0 && (
        <div className="rounded-xl border border-dashed border-hairline bg-paper py-12 text-center">
          <p className="text-sm text-slate">No archived projects.</p>
        </div>
      )}

      {!isLoading && allProjects.length > 0 && projects.length === 0 && (
        <div className="rounded-xl border border-dashed border-hairline bg-paper py-12 text-center">
          <p className="text-sm text-slate">No archived projects match your search.</p>
        </div>
      )}

      {projects.length > 0 && (
        <>
          <div className="overflow-hidden rounded-xl border border-hairline bg-paper">
            <table className="w-full border-collapse text-sm">
              <thead className="border-b border-hairline bg-ivory">
                <tr>
                  {["Code", "Name", "Archived on", "Actions"].map((h) => (
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
                {projects.map((project) => (
                  <tr
                    key={project.id}
                    className="border-b border-hairline transition-colors last:border-b-0 hover:bg-ivory"
                  >
                    <td className="px-4 py-3.5">
                      <span className="font-mono text-[12px] font-medium text-slate">{project.code}</span>
                    </td>
                    <td className="px-4 py-3.5 font-semibold text-charcoal">{project.name}</td>
                    <td className="px-4 py-3.5 text-slate">{formatArchivedAt(project.archivedAt)}</td>
                    <td className="px-4 py-3.5">
                      <div className="flex items-center justify-end">
                        <button
                          onClick={() =>
                            setRestoreConfirm({ open: true, projectId: project.id, projectName: project.name })
                          }
                          disabled={restoreMutation.isPending}
                          className="inline-flex items-center gap-1.5 rounded-md border border-hairline bg-paper px-2.5 py-1.5 text-[12px] font-semibold text-gold-deep transition-colors hover:border-gold hover:bg-ivory disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          <RotateCcw size={13} strokeWidth={1.75} />
                          Restore
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="pt-3 text-center text-xs text-slate">
            Showing <span className="font-semibold text-charcoal">{projects.length} of {allProjects.length}</span>
          </div>
        </>
      )}

      <ConfirmDialog
        open={restoreConfirm.open}
        title="Restore project?"
        message={
          restoreConfirm.projectName
            ? `"${restoreConfirm.projectName}" will reappear in your active project list with its previous status.`
            : "This project will reappear in your active project list with its previous status."
        }
        confirmLabel="Restore"
        cancelLabel="Cancel"
        variant="info"
        onConfirm={confirmRestore}
        onCancel={cancelRestore}
      />
    </div>
  );
}
