"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { Pencil, Plus, ShieldCheck, Trash2 } from "lucide-react";

import { profileApi } from "@/lib/api/profileApi";
import { PageHeader } from "@/components/common/PageHeader";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { getErrorMessage } from "@/lib/utils/error";
import type { ProfileResponse } from "@/lib/types";

export default function ProfilesPage() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ["profiles"],
    queryFn: () => profileApi.listProfiles(),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => profileApi.deleteProfile(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["profiles"] });
      toast.success("Profile deleted");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to delete profile"));
    },
  });

  const profiles = data?.data ?? [];

  const columns: ColumnDef<ProfileResponse>[] = [
    {
      key: "name",
      label: "Name",
      sortable: true,
      render: (_v, row) => (
        <div className="flex flex-col">
          <button
            onClick={() => router.push(`/admin/profiles/${row.id}`)}
            className="text-left font-medium text-text-primary hover:text-accent"
          >
            {row.name}
          </button>
          <span className="text-xs text-text-muted">{row.code}</span>
        </div>
      ),
    },
    {
      key: "description",
      label: "Description",
      render: (_v, row) => (
        <span className="text-sm text-text-secondary">{row.description ?? "—"}</span>
      ),
    },
    {
      key: "permissions",
      label: "Permissions",
      render: (_v, row) => (
        <span className="inline-block rounded-full bg-surface-active/40 px-2 py-0.5 text-xs text-text-secondary">
          {row.permissions.length}
        </span>
      ),
    },
    {
      key: "legacyRoleName",
      label: "Maps to Role",
      render: (_v, row) => (
        <span className="inline-block rounded-full bg-blue-500/15 px-2 py-0.5 text-xs font-medium text-accent">
          {row.legacyRoleName}
        </span>
      ),
    },
    {
      key: "systemDefault",
      label: "System",
      render: (value) =>
        value ? (
          <span className="inline-flex items-center gap-1 rounded-full bg-success/15 px-2 py-0.5 text-xs font-medium text-success">
            <ShieldCheck size={12} /> Default
          </span>
        ) : (
          <span className="text-xs text-text-muted">Custom</span>
        ),
    },
    {
      key: "id",
      label: "Actions",
      render: (_v, row) => (
        <div className="flex items-center gap-2">
          <button
            onClick={() => router.push(`/admin/profiles/${row.id}`)}
            className="flex items-center gap-1 rounded px-2 py-1 text-xs font-medium text-accent hover:bg-accent/10"
            title="Edit profile"
          >
            <Pencil size={14} /> Edit
          </button>
          <button
            disabled={row.systemDefault || deleteMutation.isPending}
            onClick={() => {
              if (window.confirm(`Delete profile "${row.name}"?`)) {
                deleteMutation.mutate(row.id);
              }
            }}
            className="flex items-center gap-1 rounded px-2 py-1 text-xs font-medium text-danger hover:bg-danger/10 disabled:cursor-not-allowed disabled:text-text-muted disabled:hover:bg-transparent"
            title={row.systemDefault ? "System defaults cannot be deleted" : "Delete profile"}
          >
            <Trash2 size={14} /> Delete
          </button>
        </div>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Permission Profiles"
        description="Each user is assigned exactly one profile. Profiles bundle the actions a user can perform across the application."
        actions={
          <Link
            href="/admin/profiles/new"
            className="inline-flex items-center gap-2 rounded-md bg-accent px-3 py-2 text-sm font-medium text-white hover:bg-accent/90"
          >
            <Plus size={16} /> New Profile
          </Link>
        }
      />

      {error && (
        <div className="mb-4 rounded-md bg-danger/10 p-4 text-sm text-danger">
          {getErrorMessage(error, "Failed to load profiles")}
        </div>
      )}

      <div className="rounded-xl border border-border bg-surface/50 shadow-lg">
        {isLoading ? (
          <div className="p-8 text-center text-text-secondary">Loading profiles…</div>
        ) : (
          <DataTable columns={columns} data={profiles} rowKey="id" />
        )}
      </div>
    </div>
  );
}
