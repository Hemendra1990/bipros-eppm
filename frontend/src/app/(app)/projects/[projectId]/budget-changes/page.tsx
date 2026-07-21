"use client";

import { useMemo, useState } from "react";
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
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import type { ColumnDef } from "@tanstack/react-table";
import { PageHeader } from "@/components/common/PageHeader";
import { EmptyState } from "@/components/common/EmptyState";
import { getErrorMessage } from "@/lib/utils/error";
import { useAuth } from "@/lib/auth/useAuth";
import { formatBudget, budgetUnit } from "@/lib/utils/format";

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

  const [showInitForm, setShowInitForm] = useState(false);
  const [initAmount, setInitAmount] = useState<string>("");

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

  const setInitMutation = useMutation({
    mutationFn: (amount: number) => budgetApi.setInitialBudget(projectId, amount),
    onSuccess: () => {
      toast.success("Initial budget set");
      queryClient.invalidateQueries({ queryKey: ["project-budget", projectId] });
      queryClient.invalidateQueries({ queryKey: ["project", projectId] });
      setShowInitForm(false);
      setInitAmount("");
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
  const currency = budget?.budgetCurrency ?? "INR";
  const unit = budgetUnit(currency);
  const changes = useMemo(() => changesData?.data ?? [], [changesData]);
  const wbsNodes = wbsBudgetData?.data?.nodes ?? [];

  const columns = useMemo<ColumnDef<BudgetChangeLogResponse>[]>(() => {
    const cols: ColumnDef<BudgetChangeLogResponse>[] = [
    {
      accessorKey: "requestedAt",
      header: "Date",
      cell: (info) => formatInstant(info.row.original.requestedAt),
    },
    {
      accessorKey: "changeType",
      header: "Type",
      cell: (info) => {
        const row = info.row.original;
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
      accessorKey: "fromWbsNodeCode",
      header: "From WBS",
      cell: (info) => info.row.original.fromWbsNodeCode ?? "\u2014",
    },
    {
      accessorKey: "toWbsNodeCode",
      header: "To WBS",
      cell: (info) => info.row.original.toWbsNodeCode ?? "\u2014",
    },
    {
      accessorKey: "amount",
      header: "Amount",
      enableSorting: true,
      cell: (info) => {
        const row = info.row.original;
        return <span className="font-mono font-medium">{formatBudget(row.amount, currency)}</span>;
      },
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: (info) => {
        const row = info.row.original;
        const cfg = statusConfig[row.status];
        return (
          <span className={`inline-flex px-2 py-0.5 rounded text-xs font-medium ${cfg.color}`}>
            {cfg.label}
          </span>
        );
      },
    },
    {
      accessorKey: "requestedByName",
      header: "Requested By",
      cell: (info) => info.row.original.requestedByName ?? info.row.original.requestedBy.slice(0, 8),
    },
    {
      accessorKey: "reason",
      header: "Reason",
      cell: (info) => {
        const row = info.row.original;
        return (
          <span className="max-w-[200px] truncate block" title={row.reason}>
            {row.reason}
          </span>
        );
      },
    },
    ];

    if (isAdmin) {
      cols.push({
        id: "actions",
        header: "Actions",
        cell: (info) => {
          const row = info.row.original;
          return row.status === "PENDING" ? (
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
          );
        },
      });
    }

    return cols;
  }, [isAdmin, approveMutation, rejectMutation, currency]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Budget Changes"
        description="Budget change log: additions, reductions, and transfers"
        actions={
          budget?.originalBudget != null ? (
            <button
              onClick={() => setShowForm(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md hover:bg-primary/90 text-sm"
            >
              <Plus className="w-4 h-4" />
              Request Change
            </button>
          ) : (
            <button
              onClick={() => setShowInitForm(true)}
              className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md hover:bg-primary/90 text-sm"
            >
              <DollarSign className="w-4 h-4" />
              Set Initial Budget
            </button>
          )
        }
      />

      {/* Budget Summary Cards */}
      {budget && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="rounded-lg border border-border bg-surface/50 p-4 shadow-sm">
            <div className="text-xs text-text-muted mb-1">Original Budget</div>
            <div className="text-lg font-semibold text-text-primary">{formatBudget(budget.originalBudget, currency)}</div>
          </div>
          <div className="rounded-lg border border-border bg-surface/50 p-4 shadow-sm">
            <div className="text-xs text-text-muted mb-1">Current Budget</div>
            <div className="text-lg font-semibold text-text-primary">{formatBudget(budget.currentBudget, currency)}</div>
          </div>
          <div className="rounded-lg border border-border bg-surface/50 p-4 shadow-sm">
            <div className="text-xs text-text-muted mb-1">Pending Changes</div>
            <div className="text-lg font-semibold text-amber-500">{budget.pendingChangeCount}</div>
          </div>
          <div className="rounded-lg border border-border bg-surface/50 p-4 shadow-sm">
            <div className="text-xs text-text-muted mb-1">Approved Net</div>
            <div className="text-lg font-semibold text-text-primary">
              {formatBudget(budget.approvedAdditions - budget.approvedReductions, currency)}
            </div>
          </div>
        </div>
      )}

      {/* Set Initial Budget Modal */}
      {showInitForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="w-full max-w-md space-y-4 rounded-lg border border-border bg-surface p-6 shadow-2xl">
            <div>
              <h3 className="text-lg font-semibold text-text-primary">Set Initial Budget (BAC)</h3>
              <p className="mt-1 text-xs text-text-muted">
                The original Budget at Completion. This is set once and can only be modified later through approved budget change requests.
              </p>
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium text-text-primary">Amount ({unit.inputLabel})</label>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={initAmount}
                onChange={(e) => setInitAmount(e.target.value)}
                className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                placeholder={currency === "OMR" ? "e.g. 61.5" : "e.g. 250"}
                autoFocus
              />
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowInitForm(false); setInitAmount(""); }}
                className="rounded-md border border-border px-4 py-2 text-sm text-text-secondary hover:bg-surface-hover/50"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  const n = parseFloat(initAmount);
                  if (!isFinite(n) || n <= 0) {
                    toast.error(`Enter a positive amount in ${unit.inputLabel}`);
                    return;
                  }
                  setInitMutation.mutate(n);
                }}
                disabled={setInitMutation.isPending || !initAmount}
                className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:opacity-50"
              >
                {setInitMutation.isPending ? "Saving..." : "Set Budget"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Request Form Modal */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
          <div className="w-full max-w-md space-y-4 rounded-lg border border-border bg-surface p-6 shadow-2xl">
            <h3 className="text-lg font-semibold text-text-primary">Request Budget Change</h3>

            <div>
              <label className="mb-1 block text-sm font-medium text-text-primary">Change Type</label>
              <select
                value={form.changeType}
                onChange={(e) => setForm({ ...form, changeType: e.target.value as BudgetChangeType })}
                className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              >
                <option value="ADDITION">Addition</option>
                <option value="REDUCTION">Reduction</option>
                <option value="TRANSFER">Transfer</option>
              </select>
            </div>

            {(form.changeType === "TRANSFER" || form.changeType === "REDUCTION") && (
              <div>
                <label className="mb-1 block text-sm font-medium text-text-primary">From WBS Node</label>
                <select
                  value={form.fromWbsNodeId ?? ""}
                  onChange={(e) => setForm({ ...form, fromWbsNodeId: e.target.value || null })}
                  className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
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
                <label className="mb-1 block text-sm font-medium text-text-primary">To WBS Node</label>
                <select
                  value={form.toWbsNodeId ?? ""}
                  onChange={(e) => setForm({ ...form, toWbsNodeId: e.target.value || null })}
                  className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
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
              <label className="mb-1 block text-sm font-medium text-text-primary">Amount ({unit.inputLabel})</label>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={form.amount || ""}
                onChange={(e) => setForm({ ...form, amount: parseFloat(e.target.value) || 0 })}
                className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                placeholder={currency === "OMR" ? "e.g. 5" : "e.g. 50"}
              />
            </div>

            <div>
              <label className="mb-1 block text-sm font-medium text-text-primary">Reason</label>
              <textarea
                value={form.reason}
                onChange={(e) => setForm({ ...form, reason: e.target.value })}
                className="w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                rows={3}
                placeholder="Explain the reason for this budget change..."
              />
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowForm(false)}
                className="rounded-md border border-border px-4 py-2 text-sm text-text-secondary hover:bg-surface-hover/50"
              >
                Cancel
              </button>
              <button
                onClick={() => requestMutation.mutate(form)}
                disabled={!form.amount || !form.reason}
                className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:opacity-50"
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
      ) : budget?.originalBudget == null ? (
        <EmptyState
          icon={DollarSign}
          title="No budget set yet"
          description="Set the initial Budget at Completion (BAC) to start tracking budget changes and earned-value metrics."
        />
      ) : changes.length === 0 ? (
        <EmptyState
          icon={DollarSign}
          title="No budget changes"
          description="Request a budget change to get started with P6-style budget management."
        />
      ) : (
        <VirtualDataTable columns={columns} data={changes} sortable resizable />
      )}
    </div>
  );
}
