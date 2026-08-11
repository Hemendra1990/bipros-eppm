"use client";

import { useState, useEffect, useMemo } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { LayoutDashboard, Pencil, Trash2, Plus } from "lucide-react";
import toast from "react-hot-toast";
import { dprIssueApi, type DprIssueFilters, type UpdateDprIssueRequest } from "@/lib/api/dprIssueApi";
import type { DprIssueRow, IssueStatus, IssueSeverity, IssueCategory } from "@/lib/types/dpr";
import { Badge } from "@/components/ui/badge";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { Drawer } from "@/components/common/Drawer";
import {
  SEVERITY_VARIANT,
  STATUS_VARIANT,
  SEVERITY_OPTIONS,
  STATUS_OPTIONS,
  CATEGORY_OPTIONS,
  categoryLabel,
  statusLabel,
} from "@/components/dpr/IssueBadges";
import { IssueForm } from "@/components/dpr/IssueForm";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { ResourceAvatar } from "@/components/resource/supervisor-assign/ResourceAvatar";
import { getErrorMessage } from "@/lib/utils/error";

const inputCls =
  "rounded-md border border-border bg-surface-hover px-3 py-1.5 text-sm text-text-primary focus:border-accent focus:outline-none";

const TERMINAL: IssueStatus[] = ["RESOLVED", "CLOSED"];

/** Instants (openedAt/resolvedAt/closedAt) render as date + clock time. */
function fmtDateTime(iso?: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? "—"
    : d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

type IssueSortKey = "openedAt" | "resolvedAt";

export default function ProjectIssuesPage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const queryClient = useQueryClient();

  const [filters, setFilters] = useState<DprIssueFilters>({});
  const [statusMenu, setStatusMenu] = useState<string | null>(null);
  const [searchInput, setSearchInput] = useState("");
  const [sort, setSort] = useState<{ key: IssueSortKey | null; dir: "asc" | "desc" }>({
    key: null,
    dir: "desc",
  });

  // Create/edit happen in a right-side drawer launched from this page.
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<DprIssueRow | null>(null);

  const openNew = () => {
    setEditing(null);
    setDrawerOpen(true);
  };
  const openEdit = (row: DprIssueRow) => {
    setEditing(row);
    setDrawerOpen(true);
  };

  useEffect(() => {
    const t = setTimeout(() => {
      setFilters((f) => ({ ...f, q: searchInput.trim() || undefined }));
    }, 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  const { data, isLoading } = useQuery({
    queryKey: ["dpr-issues", projectId, filters],
    queryFn: () => dprIssueApi.list(projectId, filters),
    enabled: !!projectId,
  });

  const patchMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateDprIssueRequest }) =>
      dprIssueApi.patch(projectId, id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["dpr-issues", projectId] });
      setStatusMenu(null);
    },
    onError: (err) => toast.error(getErrorMessage(err)),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => dprIssueApi.remove(projectId, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["dpr-issues", projectId] });
      toast.success("Issue deleted");
    },
    onError: (err) => toast.error(getErrorMessage(err)),
  });

  const rows: DprIssueRow[] = data?.data ?? [];

  // Client-side sort on the timestamp columns; null stamps always sort last.
  const sortedRows = useMemo(() => {
    if (!sort.key) return rows;
    const k = sort.key;
    const mult = sort.dir === "asc" ? 1 : -1;
    return [...rows].sort((a, b) => {
      const av = a[k] ? new Date(a[k] as string).getTime() : null;
      const bv = b[k] ? new Date(b[k] as string).getTime() : null;
      if (av === null && bv === null) return 0;
      if (av === null) return 1;
      if (bv === null) return -1;
      return (av - bv) * mult;
    });
  }, [rows, sort]);

  const toggleSort = (key: IssueSortKey) =>
    setSort((s) =>
      s.key === key ? { key, dir: s.dir === "desc" ? "asc" : "desc" } : { key, dir: "desc" },
    );

  const setFilter = <K extends keyof DprIssueFilters>(k: K, v: DprIssueFilters[K]) =>
    setFilters((f) => ({ ...f, [k]: v || undefined }));

  const clearFilters = () => {
    setFilters({});
    setSearchInput("");
  };
  const hasFilters = Object.values(filters).some(Boolean);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Project Issues"
        description="Field issues logged from DPR submissions or raised directly against this project."
        actions={
          <div className="flex items-center gap-2">
            <Link
              href={`/projects/${projectId}/issues/dashboard`}
              className="inline-flex items-center gap-1.5 rounded-md border border-border bg-surface-hover px-3 py-2 text-sm font-medium text-text-secondary hover:bg-surface"
            >
              <LayoutDashboard className="h-4 w-4" />
              Dashboard
            </Link>
            <button
              type="button"
              onClick={openNew}
              className="inline-flex items-center gap-1.5 rounded-md bg-accent px-4 py-2 text-sm font-medium text-white hover:bg-accent/90"
            >
              <Plus className="h-4 w-4" />
              New Issue
            </button>
          </div>
        }
      />

      {/* Filter bar */}
      <div className="flex flex-wrap items-center gap-2">
        <input
          type="text"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder="Search title or description…"
          className={`${inputCls} w-64`}
        />
        <SearchableSelect
          options={[{ value: "", label: "All statuses" }, ...STATUS_OPTIONS]}
          value={filters.status ?? ""}
          onChange={(v) => setFilter("status", v as IssueStatus || undefined)}
          placeholder="Status"
          className="w-40"
        />

        <SearchableSelect
          options={[{ value: "", label: "All severities" }, ...SEVERITY_OPTIONS]}
          value={filters.severity ?? ""}
          onChange={(v) => setFilter("severity", v as IssueSeverity || undefined)}
          placeholder="Severity"
          className="w-36"
        />

        <SearchableSelect
          options={[{ value: "", label: "All categories" }, ...CATEGORY_OPTIONS]}
          value={filters.category ?? ""}
          onChange={(v) => setFilter("category", v as IssueCategory || undefined)}
          placeholder="Category"
          className="w-48"
        />

        <SearchableSelect
          options={[
            { value: "", label: "Intervention: all" },
            { value: "true", label: "Intervention required" },
          ]}
          value={filters.interventionRequired ? "true" : ""}
          onChange={(v) => setFilter("interventionRequired", v === "true" ? true : undefined)}
          placeholder="Intervention"
          className="w-48"
        />

        <input
          type="date"
          value={filters.dateFrom ?? ""}
          onChange={(e) => setFilter("dateFrom", e.target.value)}
          className={inputCls}
          title="From date"
        />
        <input
          type="date"
          value={filters.dateTo ?? ""}
          onChange={(e) => setFilter("dateTo", e.target.value)}
          className={inputCls}
          title="To date"
        />

        {hasFilters && (
          <button
            onClick={clearFilters}
            className="text-sm text-text-muted underline hover:text-text-primary"
          >
            Clear
          </button>
        )}
      </div>

      {isLoading ? (
        <div className="text-text-secondary text-sm">Loading…</div>
      ) : rows.length === 0 ? (
        <EmptyState
          title="No issues logged yet"
          description="Raise an issue directly or log one during DPR submission."
        />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border">
          <table className="min-w-full divide-y divide-border text-sm">
            <thead className="bg-surface-hover">
              <tr>
                {["Title", "Category", "Severity", "Status", "Assigned To", "Date", "Act by"].map((h) => (
                  <th
                    key={h}
                    className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-text-muted"
                  >
                    {h}
                  </th>
                ))}
                {(["openedAt", "resolvedAt"] as const).map((k) => (
                  <th
                    key={k}
                    onClick={() => toggleSort(k)}
                    className="cursor-pointer select-none px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-text-muted hover:text-text-primary"
                    title="Click to sort"
                  >
                    {k === "openedAt" ? "Opened" : "Resolved"}
                    {sort.key === k ? (sort.dir === "desc" ? " ↓" : " ↑") : ""}
                  </th>
                ))}
                {["Activity", ""].map((h) => (
                  <th
                    key={h || "actions"}
                    className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-text-muted"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-border bg-surface">
              {sortedRows.map((row) => (
                <tr key={row.id} className="hover:bg-surface-hover">
                  <td className="px-4 py-3 font-medium text-text-primary max-w-xs truncate">
                    {row.title}
                    {row.interventionRequired && (
                      <Badge variant="danger" className="ml-2 align-middle">
                        Intervention
                      </Badge>
                    )}
                  </td>
                  <td className="px-4 py-3 text-text-secondary whitespace-nowrap">
                    {categoryLabel(row.category)}
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap">
                    <Badge variant={SEVERITY_VARIANT[row.severity]}>
                      {row.severity}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap relative">
                    <button
                      onClick={() =>
                        setStatusMenu((prev) => (prev === row.id ? null : row.id!))
                      }
                      className="focus:outline-none"
                      title="Click to change status"
                    >
                      <Badge variant={STATUS_VARIANT[row.status]} withDot>
                        {statusLabel(row.status)}
                      </Badge>
                    </button>
                    {statusMenu === row.id && (
                      <div className="absolute z-20 mt-1 w-40 rounded-md border border-border bg-surface shadow-lg">
                        {STATUS_OPTIONS.map((opt) => (
                          <button
                            key={opt.value}
                            onClick={() => {
                              setStatusMenu(null);
                              if (TERMINAL.includes(opt.value)) {
                                openEdit(row);
                                return;
                              }
                              patchMutation.mutate({ id: row.id!, body: { status: opt.value, hseIncidentType: row.hseIncidentType ?? null } });
                            }}
                            className="block w-full px-3 py-2 text-left text-sm hover:bg-surface-hover text-text-primary"
                          >
                            {opt.label}
                          </button>
                        ))}
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-3 text-text-secondary whitespace-nowrap">
                    {row.assignedToName ? (
                      <span className="inline-flex items-center gap-2">
                        {row.assignedToUserId && (
                          <ResourceAvatar
                            id={row.assignedToUserId}
                            name={row.assignedToName}
                            size="sm"
                          />
                        )}
                        <span>{row.assignedToName}</span>
                      </span>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className="px-4 py-3 text-text-muted whitespace-nowrap">
                    {row.reportDate
                      ? new Date(row.reportDate).toLocaleDateString()
                      : "—"}
                  </td>
                  <td
                    className={`px-4 py-3 whitespace-nowrap ${
                      row.dueDate &&
                      new Date(row.dueDate) < new Date(new Date().toDateString()) &&
                      row.status !== "CLOSED" &&
                      row.status !== "RESOLVED" &&
                      row.status !== "CANCELLED"
                        ? "font-semibold text-danger"
                        : "text-text-muted"
                    }`}
                  >
                    {row.dueDate ? new Date(row.dueDate).toLocaleDateString() : "—"}
                  </td>
                  <td className="px-4 py-3 text-text-muted whitespace-nowrap">
                    {fmtDateTime(row.openedAt)}
                  </td>
                  <td className="px-4 py-3 text-text-muted whitespace-nowrap">
                    {fmtDateTime(row.resolvedAt)}
                  </td>
                  <td className="px-4 py-3 text-text-muted whitespace-nowrap max-w-[160px] truncate">
                    {row.activityName ?? "—"}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => openEdit(row)}
                        className="text-text-muted hover:text-accent"
                        title="Edit issue"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        onClick={() => {
                          if (confirm("Delete this issue?")) {
                            deleteMutation.mutate(row.id!);
                          }
                        }}
                        className="text-text-muted hover:text-danger"
                        title="Delete issue"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Close status dropdown on outside click */}
      {statusMenu && (
        <div
          className="fixed inset-0 z-10"
          onClick={() => setStatusMenu(null)}
        />
      )}

      <Drawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        title={editing ? "Edit Issue" : "New Issue"}
        widthClass="max-w-2xl"
      >
        {drawerOpen && (
          <IssueForm
            key={editing?.id ?? "new"}
            projectId={projectId}
            issue={editing}
            onSaved={() => setDrawerOpen(false)}
            onCancel={() => setDrawerOpen(false)}
          />
        )}
      </Drawer>
    </div>
  );
}
