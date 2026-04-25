"use client";

import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  roleApi,
  type RoleResponse,
  type CreateRoleRequest,
  type RoleResourceType,
  type UserResourceRoleResponse,
  type AssignUserToRoleRequest,
} from "@/lib/api/roleApi";
import { resourceTypeApi, BASE_CATEGORY_LABEL } from "@/lib/api/resourceTypeApi";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

type TypeFilter = "ALL" | RoleResourceType;

interface RoleForm {
  code: string;
  name: string;
  description: string;
  resourceTypeDefId: string;
  rateUnit: string;
  budgetedRate: string;
  actualRate: string;
  rateRemarks: string;
}

interface AssignForm {
  userId: string;
  assignedFrom: string;
  assignedTo: string;
  remarks: string;
}

const initialRoleForm = (): RoleForm => ({
  code: "",
  name: "",
  description: "",
  resourceTypeDefId: "",
  rateUnit: "Day",
  budgetedRate: "",
  actualRate: "",
  rateRemarks: "",
});

const initialAssignForm = (): AssignForm => ({
  userId: "",
  assignedFrom: new Date().toISOString().split("T")[0],
  assignedTo: "",
  remarks: "",
});

const toNumberOrNull = (value: string): number | null => {
  if (value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
};

const formatCurrency = (value: number | null | undefined): string => {
  if (value == null) return "—";
  return value.toLocaleString("en-IN");
};

const formatPercent = (value: number | null | undefined): string => {
  if (value == null) return "—";
  return `${(value * 100).toFixed(2)}%`;
};

function UsersCountCell({ roleId }: { roleId: string }) {
  const { data } = useQuery({
    queryKey: ["role-users", roleId],
    queryFn: () => roleApi.listUsers(roleId),
  });
  const users = data?.data ?? [];
  return <>{users.length}</>;
}

export default function ResourceRolesPage() {
  const queryClient = useQueryClient();

  const [typeFilter, setTypeFilter] = useState<TypeFilter>("ALL");
  const [selectedRoleId, setSelectedRoleId] = useState<string | null>(null);

  const [showRoleForm, setShowRoleForm] = useState(false);
  const [roleForm, setRoleForm] = useState<RoleForm>(initialRoleForm());

  const [showAssignForm, setShowAssignForm] = useState(false);
  const [assignForm, setAssignForm] = useState<AssignForm>(initialAssignForm());

  const [error, setError] = useState<string | null>(null);

  const {
    data: rolesData,
    isLoading: rolesLoading,
    isError: rolesError,
    error: rolesQueryError,
    refetch: refetchRoles,
    isFetching: rolesFetching,
  } = useQuery({
    queryKey: ["roles", typeFilter],
    queryFn: () => roleApi.list(typeFilter === "ALL" ? undefined : typeFilter),
  });

  const roles: RoleResponse[] = rolesData?.data ?? [];

  const { data: typeDefsData } = useQuery({
    queryKey: ["resource-types", "active"],
    queryFn: () => resourceTypeApi.list({ active: true }),
  });
  const typeDefs = useMemo(() => typeDefsData?.data ?? [], [typeDefsData]);

  // Derived default — first time the form opens with no selection, fall back to the seeded
  // Manpower def (or the first available). Computed at render time so we don't need a setState
  // effect.
  const defaultTypeDefId = useMemo(() => {
    if (typeDefs.length === 0) return "";
    return (typeDefs.find((d) => d.code === "MANPOWER") ?? typeDefs[0]).id;
  }, [typeDefs]);
  const effectiveTypeDefId = roleForm.resourceTypeDefId || defaultTypeDefId;
  const selectedRole = roles.find((r) => r.id === selectedRoleId) ?? null;

  const {
    data: usersData,
    isLoading: usersLoading,
    isError: usersError,
    error: usersQueryError,
  } = useQuery({
    queryKey: ["role-users", selectedRoleId],
    queryFn: () => roleApi.listUsers(selectedRoleId!),
    enabled: !!selectedRoleId,
  });

  const users: UserResourceRoleResponse[] = usersData?.data ?? [];

  const handleCreateRole = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const payload: CreateRoleRequest = {
        code: roleForm.code.trim(),
        name: roleForm.name.trim(),
        description: roleForm.description.trim() || null,
        resourceTypeDefId: effectiveTypeDefId || undefined,
        rateUnit: roleForm.rateUnit.trim() || null,
        budgetedRate: toNumberOrNull(roleForm.budgetedRate),
        actualRate: toNumberOrNull(roleForm.actualRate),
        rateRemarks: roleForm.rateRemarks.trim() || null,
      };
      await roleApi.create(payload);
      setRoleForm(initialRoleForm());
      setShowRoleForm(false);
      queryClient.invalidateQueries({ queryKey: ["roles"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create role"));
    }
  };

  const handleDeleteRole = async (id: string) => {
    if (!window.confirm("Delete this role? Users assigned will lose their role mapping.")) return;
    try {
      await roleApi.delete(id);
      if (selectedRoleId === id) setSelectedRoleId(null);
      queryClient.invalidateQueries({ queryKey: ["roles"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete role"));
    }
  };

  const handleAssignUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRoleId) return;
    setError(null);
    try {
      const payload: AssignUserToRoleRequest = {
        userId: assignForm.userId.trim(),
        assignedFrom: assignForm.assignedFrom || null,
        assignedTo: assignForm.assignedTo || null,
        remarks: assignForm.remarks.trim() || null,
      };
      await roleApi.assignUser(selectedRoleId, payload);
      setAssignForm(initialAssignForm());
      setShowAssignForm(false);
      queryClient.invalidateQueries({ queryKey: ["role-users", selectedRoleId] });
      queryClient.invalidateQueries({ queryKey: ["role-users"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to assign user"));
    }
  };

  const handleUnassign = async (assignmentId: string) => {
    if (!selectedRoleId) return;
    if (!window.confirm("Unassign this user from the role?")) return;
    try {
      await roleApi.unassignUser(selectedRoleId, assignmentId);
      queryClient.invalidateQueries({ queryKey: ["role-users", selectedRoleId] });
      queryClient.invalidateQueries({ queryKey: ["role-users"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to unassign user"));
    }
  };

  return (
    <div className="p-6">
      <TabTip
        title="Resource Roles"
        description="Manpower roles with budgeted & actual day-rates. Each role can have many users assigned."
      />

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-bold text-text-primary">Resource Roles</h1>
        <div className="ml-auto flex items-center gap-3">
          <label className="text-sm text-text-secondary">Type</label>
          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value as TypeFilter)}
            className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
          >
            <option value="ALL">All</option>
            <option value="LABOR">Manpower</option>
            <option value="MATERIAL">Material</option>
            <option value="NONLABOR">Machine</option>
          </select>
        </div>
      </div>

      {error && <div className="text-danger mb-4">{error}</div>}

      <div className="flex flex-col lg:flex-row gap-6">
        {/* Left column — roles table */}
        <div className="lg:w-2/3">
          <button
            onClick={() => {
              setShowRoleForm(!showRoleForm);
              setError(null);
            }}
            className="mb-4 px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
          >
            {showRoleForm ? "Cancel" : "Add Role"}
          </button>

          {showRoleForm && (
            <form
              onSubmit={handleCreateRole}
              className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl"
            >
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Code
                  </label>
                  <input
                    type="text"
                    value={roleForm.code}
                    onChange={(e) => setRoleForm({ ...roleForm, code: e.target.value })}
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Name
                  </label>
                  <input
                    type="text"
                    value={roleForm.name}
                    onChange={(e) => setRoleForm({ ...roleForm, name: e.target.value })}
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    required
                  />
                </div>
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Description
                  </label>
                  <input
                    type="text"
                    value={roleForm.description}
                    onChange={(e) =>
                      setRoleForm({ ...roleForm, description: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Resource Type
                  </label>
                  <select
                    value={effectiveTypeDefId}
                    onChange={(e) =>
                      setRoleForm({ ...roleForm, resourceTypeDefId: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    required
                  >
                    {typeDefs.length === 0 && <option value="">Loading…</option>}
                    {typeDefs.map((d) => (
                      <option key={d.id} value={d.id}>
                        {d.name} ({BASE_CATEGORY_LABEL[d.baseCategory]})
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Rate Unit
                  </label>
                  <input
                    type="text"
                    value={roleForm.rateUnit}
                    onChange={(e) =>
                      setRoleForm({ ...roleForm, rateUnit: e.target.value })
                    }
                    placeholder="Day / Hour"
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Budgeted Rate
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    value={roleForm.budgetedRate}
                    onChange={(e) =>
                      setRoleForm({ ...roleForm, budgetedRate: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Actual Rate
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    value={roleForm.actualRate}
                    onChange={(e) =>
                      setRoleForm({ ...roleForm, actualRate: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  />
                </div>
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Rate Remarks
                  </label>
                  <input
                    type="text"
                    value={roleForm.rateRemarks}
                    onChange={(e) =>
                      setRoleForm({ ...roleForm, rateRemarks: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  />
                </div>
              </div>
              <div className="flex gap-2 mt-4">
                <button
                  type="submit"
                  className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600"
                >
                  Save Role
                </button>
                <button
                  type="button"
                  onClick={() => setShowRoleForm(false)}
                  className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
                >
                  Cancel
                </button>
              </div>
            </form>
          )}

          {rolesError && (() => {
            const msg = getErrorMessage(rolesQueryError, "Failed to load roles");
            const isNetwork = msg === "Network Error";
            return (
              <div className="mb-4 rounded-md bg-danger/10 border border-danger/30 p-3 text-sm">
                <div className="text-danger font-medium">
                  {isNetwork ? "Couldn't reach the API" : "Failed to load roles"}
                </div>
                <div className="text-text-secondary mt-1">
                  {isNetwork
                    ? "The browser couldn't reach the backend. The server may have been restarted while this tab was open. Click Retry, or refresh the page."
                    : msg}
                </div>
                <button
                  type="button"
                  onClick={() => refetchRoles()}
                  disabled={rolesFetching}
                  className="mt-2 px-3 py-1 text-xs font-medium bg-accent text-text-primary rounded-md hover:bg-accent-hover disabled:opacity-50"
                >
                  {rolesFetching ? "Retrying…" : "Retry"}
                </button>
              </div>
            );
          })()}

          <div className="overflow-x-auto">
            <table className="w-full border-collapse border border-border">
              <thead>
                <tr className="bg-surface/80">
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Code</th>
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Name</th>
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Type</th>
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Rate Unit</th>
                  <th className="border border-border px-4 py-2 text-right text-text-secondary">Budgeted Rate</th>
                  <th className="border border-border px-4 py-2 text-right text-text-secondary">Actual Rate</th>
                  <th className="border border-border px-4 py-2 text-right text-text-secondary">Variance</th>
                  <th className="border border-border px-4 py-2 text-right text-text-secondary">Users</th>
                  <th className="border border-border px-4 py-2 text-left text-text-secondary">Actions</th>
                </tr>
              </thead>
              <tbody>
                {rolesLoading && (
                  <tr>
                    <td colSpan={9} className="border border-border px-4 py-6 text-center text-text-muted">
                      Loading roles…
                    </td>
                  </tr>
                )}
                {!rolesLoading && roles.length === 0 && (
                  <tr>
                    <td colSpan={9} className="border border-border px-4 py-6 text-center text-text-muted">
                      No roles.
                    </td>
                  </tr>
                )}
                {roles.map((role) => {
                  const isSelected = role.id === selectedRoleId;
                  return (
                    <tr
                      key={role.id}
                      onClick={() => setSelectedRoleId(role.id)}
                      className={`cursor-pointer text-text-primary ${
                        isSelected
                          ? "bg-accent/10 ring-1 ring-accent/30"
                          : "hover:bg-surface-hover/30"
                      }`}
                    >
                      <td className="border border-border px-4 py-2">{role.code}</td>
                      <td className="border border-border px-4 py-2">{role.name}</td>
                      <td className="border border-border px-4 py-2">
                        {role.resourceTypeName ?? BASE_CATEGORY_LABEL[role.resourceType] ?? role.resourceType}
                      </td>
                      <td className="border border-border px-4 py-2">{role.rateUnit ?? "—"}</td>
                      <td className="border border-border px-4 py-2 text-right">
                        {formatCurrency(role.budgetedRate)}
                      </td>
                      <td className="border border-border px-4 py-2 text-right">
                        {formatCurrency(role.actualRate)}
                      </td>
                      <td className="border border-border px-4 py-2 text-right">
                        {role.rateVariancePercent != null
                          ? formatPercent(role.rateVariancePercent)
                          : formatCurrency(role.rateVariance)}
                      </td>
                      <td className="border border-border px-4 py-2 text-right">
                        {isSelected ? <UsersCountCell roleId={role.id} /> : "—"}
                      </td>
                      <td className="border border-border px-4 py-2">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDeleteRole(role.id);
                          }}
                          className="text-danger hover:underline text-sm"
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        {/* Right column — users for selected role */}
        <div className="lg:w-1/3">
          <div className="bg-surface/50 p-4 rounded-lg border border-border shadow-xl">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-text-primary">
                {selectedRole ? selectedRole.name : "Users"}
              </h2>
              {selectedRole && (
                <button
                  onClick={() => {
                    setShowAssignForm(!showAssignForm);
                    setError(null);
                  }}
                  className="px-3 py-1 text-sm bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
                >
                  {showAssignForm ? "Cancel" : "Assign User"}
                </button>
              )}
            </div>

            {!selectedRole && (
              <p className="text-sm text-text-muted">
                Select a role from the table to view its users.
              </p>
            )}

            {selectedRole && showAssignForm && (
              <form
                onSubmit={handleAssignUser}
                className="mb-4 space-y-3 bg-surface-hover/40 p-3 rounded-lg border border-border"
              >
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    User ID (UUID)
                  </label>
                  <input
                    type="text"
                    value={assignForm.userId}
                    onChange={(e) =>
                      setAssignForm({ ...assignForm, userId: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Assigned From
                  </label>
                  <input
                    type="date"
                    value={assignForm.assignedFrom}
                    onChange={(e) =>
                      setAssignForm({ ...assignForm, assignedFrom: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Assigned To
                  </label>
                  <input
                    type="date"
                    value={assignForm.assignedTo}
                    onChange={(e) =>
                      setAssignForm({ ...assignForm, assignedTo: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1 text-text-secondary">
                    Remarks
                  </label>
                  <input
                    type="text"
                    value={assignForm.remarks}
                    onChange={(e) =>
                      setAssignForm({ ...assignForm, remarks: e.target.value })
                    }
                    className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  />
                </div>
                <div className="flex gap-2">
                  <button
                    type="submit"
                    className="px-3 py-1 text-sm bg-green-600 text-text-primary rounded-lg hover:bg-green-600"
                  >
                    Assign
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowAssignForm(false)}
                    className="px-3 py-1 text-sm bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
                  >
                    Cancel
                  </button>
                </div>
              </form>
            )}

            {selectedRole && usersLoading && (
              <p className="text-sm text-text-muted">Loading users…</p>
            )}
            {selectedRole && usersError && (
              <p className="text-sm text-danger">
                {getErrorMessage(usersQueryError, "Failed to load users")}
              </p>
            )}
            {selectedRole && !usersLoading && users.length === 0 && (
              <p className="text-sm text-text-muted">No users assigned.</p>
            )}

            {selectedRole && users.length > 0 && (
              <ul className="space-y-3">
                {users.map((u) => (
                  <li
                    key={u.id}
                    className="p-3 rounded-lg border border-border bg-surface-hover/30"
                  >
                    <div className="text-sm font-mono text-text-primary break-all">
                      {u.userId}
                    </div>
                    <div className="text-xs text-text-muted mt-1">
                      {(u.assignedFrom ?? "—")} → {(u.assignedTo ?? "—")}
                    </div>
                    {u.remarks && (
                      <div className="text-xs text-text-secondary mt-1">{u.remarks}</div>
                    )}
                    <button
                      onClick={() => handleUnassign(u.id)}
                      className="mt-2 text-danger hover:underline text-xs"
                    >
                      Unassign
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
