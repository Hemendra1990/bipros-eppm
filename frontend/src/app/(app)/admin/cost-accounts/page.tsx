"use client";

import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  costApi,
  type CostAccount,
  type CreateCostAccountRequest,
  type UpdateCostAccountRequest,
} from "@/lib/api/costApi";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

interface CostAccountForm {
  code: string;
  name: string;
  description: string;
  parentId: string;
}

const emptyForm = (): CostAccountForm => ({
  code: "",
  name: "",
  description: "",
  parentId: "",
});

const formFromAccount = (a: CostAccount): CostAccountForm => ({
  code: a.code,
  name: a.name,
  description: a.description ?? "",
  parentId: a.parentId ?? "",
});

export default function CostAccountsAdminPage() {
  const queryClient = useQueryClient();

  const [editingId, setEditingId] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<CostAccountForm>(emptyForm());
  const [error, setError] = useState<string | null>(null);

  const { data, isLoading, isError, error: queryError } = useQuery({
    queryKey: ["cost-accounts"],
    queryFn: () => costApi.listCostAccounts(),
  });

  const accounts: CostAccount[] = useMemo(() => data?.data ?? [], [data]);

  const accountById = useMemo(
    () => new Map(accounts.map((a) => [a.id, a])),
    [accounts]
  );

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setError(null);
    setShowForm(true);
  };

  const openEdit = (account: CostAccount) => {
    setEditingId(account.id);
    setForm(formFromAccount(account));
    setError(null);
    setShowForm(true);
  };

  const closeForm = () => {
    setShowForm(false);
    setEditingId(null);
    setForm(emptyForm());
    setError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (editingId) {
        const payload: UpdateCostAccountRequest = {
          name: form.name.trim(),
          description: form.description.trim() || undefined,
        };
        await costApi.updateCostAccount(editingId, payload);
      } else {
        const payload: CreateCostAccountRequest = {
          code: form.code.trim().toUpperCase(),
          name: form.name.trim(),
          description: form.description.trim() || undefined,
          parentId: form.parentId || null,
        };
        await costApi.createCostAccount(payload);
      }
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["cost-accounts"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to save cost account"));
    }
  };

  const handleDelete = async (account: CostAccount) => {
    if (!window.confirm(`Delete cost account "${account.code} — ${account.name}"? This cannot be undone.`)) return;
    try {
      await costApi.deleteCostAccount(account.id);
      if (editingId === account.id) closeForm();
      queryClient.invalidateQueries({ queryKey: ["cost-accounts"] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete cost account"));
    }
  };

  const editingAccount = editingId ? accountById.get(editingId) ?? null : null;

  const parentOptions = accounts.filter((a) => a.id !== editingId);

  return (
    <div className="p-6">
      <TabTip
        title="Cost Accounts"
        description="Manage the cost account hierarchy used to classify and roll up project costs. Cost accounts can be nested (parent/child) and assigned to WBS nodes to drive budget and actuals reporting."
      />

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-bold text-text-primary">Cost Accounts</h1>
        <div className="ml-auto">
          <button
            type="button"
            onClick={openCreate}
            className="px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
          >
            + New Cost Account
          </button>
        </div>
      </div>

      {error && <div className="text-danger mb-4">{error}</div>}

      {showForm && (
        <form
          onSubmit={handleSubmit}
          className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl"
        >
          <h2 className="text-lg font-semibold text-text-primary mb-4">
            {editingId
              ? `Edit "${editingAccount?.name ?? "Cost Account"}"`
              : "New Cost Account"}
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Code</label>
              <input
                type="text"
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
                disabled={!!editingId}
                placeholder="e.g. CIVIL-STRUCT"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg disabled:opacity-60"
                required
              />
              {editingId && (
                <p className="mt-1 text-xs text-text-muted">Code cannot be changed after creation.</p>
              )}
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">Name</label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Civil Structures"
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                required
              />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium mb-1 text-text-secondary">Description</label>
              <textarea
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                placeholder="Optional description"
                rows={2}
                className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg resize-none"
              />
            </div>
            {!editingId && (
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">
                  Parent Cost Account
                </label>
                <select
                  value={form.parentId}
                  onChange={(e) => setForm({ ...form, parentId: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                >
                  <option value="">— None (top-level) —</option>
                  {parentOptions.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.code} — {a.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>
          <div className="flex gap-2 mt-4">
            <button
              type="submit"
              className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600"
            >
              {editingId ? "Save Changes" : "Create"}
            </button>
            <button
              type="button"
              onClick={closeForm}
              className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {isError && (
        <div className="text-danger mb-4">
          {getErrorMessage(queryError, "Failed to load cost accounts")}
        </div>
      )}

      <div className="overflow-x-auto">
        <table className="w-full border-collapse border border-border">
          <thead>
            <tr className="bg-surface/80">
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Code</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Name</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Description</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Parent</th>
              <th className="border border-border px-4 py-2 text-left text-text-secondary">Actions</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td
                  colSpan={5}
                  className="border border-border px-4 py-6 text-center text-text-muted"
                >
                  Loading…
                </td>
              </tr>
            )}
            {!isLoading && accounts.length === 0 && (
              <tr>
                <td
                  colSpan={5}
                  className="border border-border px-4 py-6 text-center text-text-muted"
                >
                  No cost accounts defined.
                </td>
              </tr>
            )}
            {accounts.map((account) => (
              <tr key={account.id} className="text-text-primary hover:bg-surface-hover/30">
                <td className="border border-border px-4 py-2 font-mono text-sm">{account.code}</td>
                <td className="border border-border px-4 py-2">{account.name}</td>
                <td className="border border-border px-4 py-2 text-text-secondary text-sm">
                  {account.description ?? "—"}
                </td>
                <td className="border border-border px-4 py-2 text-sm">
                  {account.parentId
                    ? accountById.has(account.parentId)
                      ? `${accountById.get(account.parentId)!.code} — ${accountById.get(account.parentId)!.name}`
                      : account.parentId
                    : "—"}
                </td>
                <td className="border border-border px-4 py-2 text-sm">
                  <button
                    onClick={() => openEdit(account)}
                    className="text-accent hover:underline mr-3"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => handleDelete(account)}
                    className="text-danger hover:underline"
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
