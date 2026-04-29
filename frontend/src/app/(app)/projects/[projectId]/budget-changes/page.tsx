"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { Plus, Check, X, DollarSign, TrendingUp, TrendingDown, ArrowLeftRight } from "lucide-react";
import {
  budgetApi,
  type BudgetChangeLogResponse,
  type CreateBudgetChangeRequest,
  type BudgetChangeType,
} from "@/lib/api/budgetApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { getErrorMessage } from "@/lib/utils/error";
import { useAuth } from "@/lib/auth/useAuth";

const changeTypeConfig: Record<BudgetChangeType, { label: string; icon: typeof Plus; color: string }> = {
  ADDITION: { label: "Addition", icon: TrendingUp, color: "text-emerald-600 bg-emerald-50 dark:bg-emerald-900/30 dark:text-emerald-300" },
  REDUCTION: { label: "Reduction", icon: TrendingDown, color: "text-red-600 bg-red-50 dark:bg-red-900/30 dark:text-red-300" },
  TRANSFER: { label: "Transfer", icon: ArrowLeftRight, color: "text-blue-600 bg-blue-50 dark:bg-blue-900/30 dark:text-blue-300" },
};

const statusConfig: Record<string, { label: string; color: string }> = {
  PENDING: { label: "Pending", color: "text-amber-700 bg-amber-50 dark:bg-amber-900/30 dark:text-amber-300" },
  APPROVED: { label: "Approved", color: "text-emerald-700 bg-emerald-50 dark:bg-emerald-900/30 dark:text-emerald-300" },
  REJECTED: { label: "Rejected", color: "text-red-700 bg-red-50 dark:bg-red-900/30 dark:text-red-300" },
};

function formatCrores(v: number | null | undefined): string {
  if (v == null) return "\u2014";
  return new Intl.NumberFormat("en-IN", { maximumFractionDigits: 2 }).format(v) + " cr";
}

function formatInstant(s: string | null | undefined): string {
  if (!s) return "\u2014";
  return new Date(s).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

export default function BudgetChangesPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const queryClient = useQueryClient();
  const { hasAnyRole } = useAuth();
  const isAdmin = hasAnyRole(["ADMIN"]);

  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<CreateBudgetChangeRequest>({
    changeType: "ADDITION",
    amount: 0,
    reason: "",
    fromWbsNodeId: null,
    toWbsNodeId: null,
  });

  const { data: budgetData } = useQuery({
    queryKey: ["project-budget", projectId],
    queryFn: () => budgetApi.getBudgetSummary(projectId),
  });

  const { data: changesData, isLoading } = useQuery({
    queryKey: ["budget-changes", projectId],
    queryFn: () => budgetApi.getChangeLog(projectId),
  });

  const { data: wbsBudgetData } = useQuery({
    queryKey: ["wbs-budget-summary", projectId],
    queryFn: () => budgetApi.getWbsBudgetSummary(projectId),
  });

  const requestMutation = useMutation({
    mutationFn: (data: CreateBudgetChangeRequest) => budgetApi.requestChange(projectId, data),
    onSuccess: () => {
      toast.success("Budget change requested");
      queryClient.invalidateQueries({ queryKey: ["budget-changes", projectId] });
      queryClient.invalidateQueries({ queryKey: ["project-budget", projectId] });
      setShowForm(false);
      setForm({ changeType: "ADDITION", amount: 0, reason: "", fromWbsNodeId: null, toWbsNodeId: null });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const approveMutation = useMutation({
    mutationFn: (changeId: string) => budgetApi.approveChange(projectId, changeId),
    onSuccess: () => {
      toast.success("Change approved");
      queryClient.invalidateQueries({ queryKey: ["budget-changes", projectId] });
      queryClient.invalidateQueries({ queryKey: ["project-budget", projectId] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ changeId, reason }: { changeId: string; reason?: string }) =>
      budgetApi.rejectChange(projectId, changeId, reason),
    onSuccess: () => {
      toast.success("Change rejected");
      queryClient.invalidateQueries({ queryKey: ["budget-changes", projectId] });
      queryClient.invalidateQueries({ queryKey: ["project-budget", projectId] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });

  const budget = budgetData?.data;
  const changes = changesData?.data ?? [];
  const wbsNodes = wbsBudgetData?.data?.nodes ?? [];

  const columns: ColumnDef<BudgetChangeLogResponse>[] = [
    {
      key: "requestedAt",
      label: "Date",
      render: (_val, row) => formatInstant(row.requestedAt),
    },
    {
      key: "changeType",
      label: "Type",
      render: (_val, row) => {
        const cfg = changeTypeConfig[row.changeType];
        const Icon = cfg.icon;
        return (
          <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium ${cfg.color}`}>
            <Icon className="w-3 h-3" />
            {cfg.label}
          </span>
        );
      },
    },
    {
      key: "fromWbsNodeCode",
      label: "From WBS",
      render: (_val, row) => row.fromWbsNodeCode ?? "\u2014",
    },
    {
      key: "toWbsNodeCode",
      label: "To WBS",
      render: (_val, row) => row.toWbsNodeCode ?? "\u2014",
    },
    {
      key: "amount",
      label: "Amount",
      sortable: true,
      render: (_val, row) => <span className="font-mono font-medium">{formatCrores(row.amount)}</span>,
    },
    {
      key: "status",
      label: "Status",
      render: (_val, row) => {
        const cfg = statusConfig[row.status];
        return (
          <span className={`inline-flex px-2 py-0.5 rounded text-xs font-medium ${cfg.color}`}>
            {cfg.label}
          </span>
        );
      },
    },
    {
      key: "requestedByName",
      label: "Requested By",
      render: (_val, row) => row.requestedByName ?? row.requestedBy.slice(0, 8),
    },
    {
      key: "reason",
      label: "Reason",
      render: (_val, row) => (
        <span className="max-w-[200px] truncate block" title={row.reason}>
          {row.reason}
        </span>
      ),
    },
  ];

  if (isAdmin) {
    columns.push({
      key: "actions",
      label: "Actions",
      render: (_val, row) =>
        row.status === "PENDING" ? (
          <div className="flex gap-1">
            <button
              onClick={() => approveMutation.mutate(row.id)}
              className="p-1 text-emerald-600 hover:bg-emerald-50 rounded"
              title="Approve"
            >
              <Check className="w-4 h-4" />
            </button>
            <button
              onClick={() => {
                const reason = prompt("Rejection reason (optional):");
                rejectMutation.mutate({ changeId: row.id, reason: reason ?? undefined });
              }}
              className="p-1 text-red-600 hover:bg-red-50 rounded"
              title="Reject"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        ) : (
          <span className="text-xs text-text-muted">
            {row.decidedByName ?? (row.decidedBy ? row.decidedBy.slice(0, 8) : "")}
          </span>
        ),
    });
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Budget Changes"
        description="P6-style budget change log: additions, reductions, and transfers"
        actions={
          budget?.originalBudget != null ? (
            <button
              onClick={() => setShowForm(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md hover:bg-primary/90 text-sm"
            >
              <Plus className="w-4 h-4" />
              Request Change
            </button>
          ) : undefined
        }
      />

      {/* Budget Summary Cards */}
      {budget && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="rounded-lg border bg-card p-4">
            <div className="text-xs text-text-muted mb-1">Original Budget</div>
            <div className="text-lg font-semibold">{formatCrores(budget.originalBudget)}</div>
          </div>
          <div className="rounded-lg border bg-card p-4">
            <div className="text-xs text-text-muted mb-1">Current Budget</div>
            <div className="text-lg font-semibold">{formatCrores(budget.currentBudget)}</div>
          </div>
          <div className="rounded-lg border bg-card p-4">
            <div className="text-xs text-text-muted mb-1">Pending Changes</div>
            <div className="text-lg font-semibold text-amber-600">{budget.pendingChangeCount}</div>
          </div>
          <div className="rounded-lg border bg-card p-4">
            <div className="text-xs text-text-muted mb-1">Approved Net</div>
            <div className="text-lg font-semibold">
              {formatCrores(budget.approvedAdditions - budget.approvedReductions)}
            </div>
          </div>
        </div>
      )}

      {/* Request Form Modal */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-card rounded-lg shadow-xl w-full max-w-md p-6 space-y-4">
            <h3 className="text-lg font-semibold">Request Budget Change</h3>

            <div>
              <label className="block text-sm font-medium mb-1">Change Type</label>
              <select
                value={form.changeType}
                onChange={(e) => setForm({ ...form, changeType: e.target.value as BudgetChangeType })}
                className="w-full rounded-md border bg-background px-3 py-2 text-sm"
              >
                <option value="ADDITION">Addition</option>
                <option value="REDUCTION">Reduction</option>
                <option value="TRANSFER">Transfer</option>
              </select>
            </div>

            {(form.changeType === "TRANSFER" || form.changeType === "REDUCTION") && (
              <div>
                <label className="block text-sm font-medium mb-1">From WBS Node</label>
                <select
                  value={form.fromWbsNodeId ?? ""}
                  onChange={(e) => setForm({ ...form, fromWbsNodeId: e.target.value || null })}
                  className="w-full rounded-md border bg-background px-3 py-2 text-sm"
                >
                  <option value="">Select WBS...</option>
                  {wbsNodes.map((n) => (
                    <option key={n.wbsNodeId} value={n.wbsNodeId}>
                      {"  ".repeat((n.wbsLevel ?? 1) - 1)}
                      {n.code} &mdash; {n.name}
                    </option>
                  ))}
                </select>
              </div>
            )}

            {(form.changeType === "TRANSFER" || form.changeType === "ADDITION") && (
              <div>
                <label className="block text-sm font-medium mb-1">To WBS Node</label>
                <select
                  value={form.toWbsNodeId ?? ""}
                  onChange={(e) => setForm({ ...form, toWbsNodeId: e.target.value || null })}
                  className="w-full rounded-md border bg-background px-3 py-2 text-sm"
                >
                  <option value="">Select WBS...</option>
                  {wbsNodes.map((n) => (
                    <option key={n.wbsNodeId} value={n.wbsNodeId}>
                      {"  ".repeat((n.wbsLevel ?? 1) - 1)}
                      {n.code} &mdash; {n.name}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div>
              <label className="block text-sm font-medium mb-1">Amount (crores)</label>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={form.amount || ""}
                onChange={(e) => setForm({ ...form, amount: parseFloat(e.target.value) || 0 })}
                className="w-full rounded-md border bg-background px-3 py-2 text-sm"
                placeholder="e.g. 500"
              />
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">Reason</label>
              <textarea
                value={form.reason}
                onChange={(e) => setForm({ ...form, reason: e.target.value })}
                className="w-full rounded-md border bg-background px-3 py-2 text-sm"
                rows={3}
                placeholder="Explain the reason for this budget change..."
              />
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowForm(false)}
                className="px-4 py-2 text-sm rounded-md border hover:bg-accent"
              >
                Cancel
              </button>
              <button
                onClick={() => requestMutation.mutate(form)}
                disabled={!form.amount || !form.reason}
                className="px-4 py-2 text-sm bg-primary text-primary-foreground rounded-md hover:bg-primary/90 disabled:opacity-50"
              >
                Submit
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Changes Table */}
      {isLoading ? (
        <div className="text-center text-text-muted py-8">Loading...</div>
      ) : changes.length === 0 ? (
        <EmptyState
          icon={DollarSign}
          title="No budget changes"
          description="Request a budget change to get started with P6-style budget management."
        />
      ) : (
        <DataTable columns={columns} data={changes} rowKey="id" />
      )}
    </div>
  );
}
