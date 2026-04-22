"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { userApi } from "@/lib/api/userApi";
import { PageHeader } from "@/components/common/PageHeader";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import type { UserResponse } from "@/lib/types";
import { getErrorMessage } from "@/lib/utils/error";
import toast from "react-hot-toast";
import { Shield, ShieldCheck, UserCheck, UserX } from "lucide-react";

const AVAILABLE_ROLES = ["ROLE_ADMIN", "ROLE_MANAGER", "ROLE_USER", "ROLE_VIEWER"];

function RoleBadge({ role }: { role: string }) {
  const label = role.replace("ROLE_", "");
  const colors: Record<string, string> = {
    ADMIN: "bg-red-500/20 text-danger",
    MANAGER: "bg-amber-500/20 text-warning",
    USER: "bg-blue-500/20 text-accent",
    VIEWER: "bg-surface-active/50 text-text-secondary",
  };
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${colors[label] ?? "bg-surface-active/50 text-text-secondary"}`}>
      {label}
    </span>
  );
}

function RoleEditor({ user, onClose }: { user: UserResponse; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [selectedRoles, setSelectedRoles] = useState<string[]>(user.roles);

  const mutation = useMutation({
    mutationFn: () => userApi.updateUserRoles(user.id, { roles: selectedRoles }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      toast.success(`Roles updated for ${user.username}`);
      onClose();
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to update roles"));
    },
  });

  const toggleRole = (role: string) => {
    setSelectedRoles((prev) =>
      prev.includes(role) ? prev.filter((r) => r !== role) : [...prev, role]
    );
  };

  return (
    <div className="flex items-center gap-2">
      {AVAILABLE_ROLES.map((role) => (
        <button
          key={role}
          onClick={() => toggleRole(role)}
          className={`rounded-full px-2 py-0.5 text-xs font-medium transition-colors ${
            selectedRoles.includes(role)
              ? "bg-accent text-text-primary"
              : "bg-surface-hover text-text-secondary hover:bg-surface-active"
          }`}
        >
          {role.replace("ROLE_", "")}
        </button>
      ))}
      <button
        onClick={() => mutation.mutate()}
        disabled={mutation.isPending}
        className="ml-2 rounded bg-green-600 px-2 py-0.5 text-xs text-text-primary hover:bg-green-500 disabled:opacity-50"
      >
        {mutation.isPending ? "..." : "Save"}
      </button>
      <button
        onClick={onClose}
        className="rounded bg-surface-active px-2 py-0.5 text-xs text-text-secondary hover:bg-border"
      >
        Cancel
      </button>
    </div>
  );
}

export default function UsersPage() {
  const queryClient = useQueryClient();
  const [editingUserId, setEditingUserId] = useState<string | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ["users"],
    queryFn: () => userApi.listUsers(),
  });

  const toggleMutation = useMutation({
    mutationFn: ({ userId, enabled }: { userId: string; enabled: boolean }) =>
      userApi.toggleUserEnabled(userId, enabled),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      toast.success("User status updated");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to update user status"));
    },
  });

  const users = data?.data?.content ?? [];

  const columns: ColumnDef<UserResponse>[] = [
    { key: "username", label: "Username", sortable: true },
    { key: "email", label: "Email", sortable: true },
    {
      key: "firstName",
      label: "Name",
      sortable: true,
      render: (_value, row) => `${row.firstName ?? ""} ${row.lastName ?? ""}`.trim() || "-",
    },
    {
      key: "roles",
      label: "Roles",
      render: (_value, row) =>
        editingUserId === row.id ? (
          <RoleEditor user={row} onClose={() => setEditingUserId(null)} />
        ) : (
          <div className="flex items-center gap-1">
            {row.roles.map((role) => (
              <RoleBadge key={role} role={role} />
            ))}
            <button
              onClick={() => setEditingUserId(row.id)}
              className="ml-2 text-text-muted hover:text-accent transition-colors"
              title="Edit roles"
            >
              <Shield size={14} />
            </button>
          </div>
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
      render: (_value, row) => (
        <button
          onClick={() =>
            toggleMutation.mutate({ userId: row.id, enabled: !row.enabled })
          }
          disabled={toggleMutation.isPending}
          className={`flex items-center gap-1 rounded px-2 py-1 text-xs font-medium transition-colors ${
            row.enabled
              ? "text-danger hover:bg-danger/10"
              : "text-success hover:bg-success/10"
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
    </div>
  );
}
