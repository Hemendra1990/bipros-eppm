"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { Plus, ShieldCheck, UserCheck, UserX } from "lucide-react";

import { profileApi } from "@/lib/api/profileApi";
import { userApi } from "@/lib/api/userApi";
import { PageHeader } from "@/components/common/PageHeader";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import type { UserResponse } from "@/lib/types";
import { getErrorMessage } from "@/lib/utils/error";
import { CreateUserDialog } from "./CreateUserDialog";

function ProfileBadge({ name }: { name: string | null | undefined }) {
  if (!name) {
    return <span className="text-xs italic text-text-muted">No profile</span>;
  }
  return (
    <span className="inline-block rounded-full bg-blue-500/15 px-2 py-0.5 text-xs font-medium text-accent">
      {name}
    </span>
  );
}

function ProfilePicker({
  user,
  profiles,
  onSaved,
}: {
  user: UserResponse;
  profiles: { id: string; name: string }[];
  onSaved: () => void;
}) {
  const queryClient = useQueryClient();
  const [value, setValue] = useState<string>(user.profileId ?? "");

  const mutation = useMutation({
    mutationFn: (next: string | null) => userApi.assignProfile(user.id, next),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      toast.success(`Profile updated for ${user.username}`);
      onSaved();
    },
    onError: (err: unknown) => toast.error(getErrorMessage(err, "Failed to assign profile")),
  });

  return (
    <div className="flex items-center gap-2">
      <select
        value={value}
        onChange={(e) => setValue(e.target.value)}
        className="rounded border border-border bg-surface-hover px-2 py-1 text-xs text-text-primary"
      >
        <option value="">— No profile —</option>
        {profiles.map((p) => (
          <option key={p.id} value={p.id}>{p.name}</option>
        ))}
      </select>
      <button
        type="button"
        disabled={mutation.isPending || value === (user.profileId ?? "")}
        onClick={() => mutation.mutate(value || null)}
        className="rounded bg-accent px-2 py-1 text-xs font-medium text-white hover:bg-accent/90 disabled:opacity-50"
      >
        {mutation.isPending ? "..." : "Save"}
      </button>
      <button
        type="button"
        onClick={onSaved}
        className="rounded bg-surface-hover px-2 py-1 text-xs text-text-secondary hover:bg-surface-active"
      >
        Cancel
      </button>
    </div>
  );
}

export default function UsersPage() {
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [editingProfileId, setEditingProfileId] = useState<string | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ["users"],
    queryFn: () => userApi.listUsers(),
  });

  const { data: profilesResponse } = useQuery({
    queryKey: ["profiles"],
    queryFn: () => profileApi.listProfiles(),
  });
  const profiles = (profilesResponse?.data ?? []).map((p) => ({ id: p.id, name: p.name }));

  const toggleMutation = useMutation({
    mutationFn: ({ userId, enabled }: { userId: string; enabled: boolean }) =>
      userApi.toggleUserEnabled(userId, enabled),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      toast.success("User status updated");
    },
    onError: (err: unknown) => toast.error(getErrorMessage(err, "Failed to update user status")),
  });

  const users = data?.data?.content ?? [];

  const columns: ColumnDef<UserResponse>[] = [
    { key: "username", label: "Username", sortable: true },
    { key: "email", label: "Email", sortable: true },
    {
      key: "firstName",
      label: "Name",
      sortable: true,
      render: (_v, row) => `${row.firstName ?? ""} ${row.lastName ?? ""}`.trim() || "-",
    },
    {
      key: "profileName",
      label: "Profile",
      render: (_v, row) =>
        editingProfileId === row.id ? (
          <ProfilePicker
            user={row}
            profiles={profiles}
            onSaved={() => setEditingProfileId(null)}
          />
        ) : (
          <button
            type="button"
            onClick={() => setEditingProfileId(row.id)}
            className="cursor-pointer hover:opacity-80"
            title="Click to change profile"
          >
            <ProfileBadge name={row.profileName} />
          </button>
        ),
    },
    {
      key: "enabled",
      label: "Status",
      render: (value) => (
        <span
          className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${
            value ? "bg-success/20 text-success" : "bg-red-500/20 text-danger"
          }`}
        >
          {value ? <ShieldCheck size={12} /> : null}
          {value ? "Active" : "Disabled"}
        </span>
      ),
    },
    {
      key: "id",
      label: "Actions",
      render: (_v, row) => (
        <button
          onClick={() => toggleMutation.mutate({ userId: row.id, enabled: !row.enabled })}
          disabled={toggleMutation.isPending}
          className={`flex items-center gap-1 rounded px-2 py-1 text-xs font-medium transition-colors ${
            row.enabled ? "text-danger hover:bg-danger/10" : "text-success hover:bg-success/10"
          }`}
          title={row.enabled ? "Disable user" : "Enable user"}
        >
          {row.enabled ? <UserX size={14} /> : <UserCheck size={14} />}
          {row.enabled ? "Disable" : "Enable"}
        </button>
      ),
    },
  ];

  return (
    <div>
      <PageHeader
        title="User Management"
        description="Manage users, roles, and access permissions"
        actions={
          <button
            type="button"
            onClick={() => setCreateOpen(true)}
            className="inline-flex items-center gap-2 rounded-md bg-accent px-3 py-2 text-sm font-medium text-white hover:bg-accent/90"
          >
            <Plus size={16} /> New User
          </button>
        }
      />

      {error && (
        <div className="mb-4 rounded-md bg-danger/10 p-4 text-sm text-danger">
          {getErrorMessage(error, "Failed to load users")}
        </div>
      )}

      <div className="rounded-xl border border-border bg-surface/50 shadow-lg">
        {isLoading ? (
          <div className="p-8 text-center text-text-secondary">Loading users...</div>
        ) : (
          <DataTable columns={columns} data={users} rowKey="id" />
        )}
      </div>

      <CreateUserDialog open={createOpen} onOpenChange={setCreateOpen} />
    </div>
  );
}
