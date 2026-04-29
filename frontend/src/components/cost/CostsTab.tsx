"use client";

import { useState, useCallback, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { getErrorMessage } from "@/lib/utils/error";
import { Plus } from "lucide-react";
import Link from "next/link";
import {
  costApi,
  type ForecastMethod,
  type CashFlowForecastItem,
  type PeriodCostAggregation,
  type CreateExpenseRequest,
} from "@/lib/api/costApi";
import { projectApi } from "@/lib/api/projectApi";
import { budgetApi } from "@/lib/api/budgetApi";
import { activityApi } from "@/lib/api/activityApi";
import type { WbsNodeResponse } from "@/lib/types";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import { useCurrency } from "@/lib/hooks/useCurrency";
import { SecretField } from "@/components/auth/SecretField";

const FINANCE_ROLES = ["ROLE_FINANCE", "ROLE_PMO", "ROLE_ADMIN"] as const;
const NO_FINANCE_PLACEHOLDER = (
  <div className="rounded-lg border border-dashed border-border bg-surface-hover/40 px-4 py-6 text-center text-sm text-text-muted">
    Cost figures are restricted to Finance / PMO roles.
  </div>
);

/**
 * IC-PMS monetary values are budgeted in INR crores (1 crore = 10,000,000 INR).
 * WBS nodes carry `budgetCrores`; cost-summary/expense APIs return absolute INR.
 * We display everything in ₹cr with two-decimal precision for uniformity.
 */
const INR_PER_CRORE = 10_000_000;

function formatCrores(crores: number): string {
  // Use Indian digit grouping (lakh/crore) for readability.
  const rounded = Number.isFinite(crores) ? Math.round(crores * 100) / 100 : 0;
  return `₹${rounded.toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}cr`;
}

function formatInrAsCrores(inr: number): string {
  return formatCrores((inr || 0) / INR_PER_CRORE);
}

function formatInr(inr: number): string {
  return `₹${(inr || 0).toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

function sumBudgetCrores(nodes: WbsNodeResponse[]): number {
  // Recursive sum to cover cases where only leaves carry budgetCrores.
  let total = 0;
  for (const node of nodes) {
    if (node.budgetCrores != null) total += Number(node.budgetCrores);
    if (node.children?.length) total += sumBudgetCrores(node.children);
  }
  return total;
}

function pickTopLevelBudget(nodes: WbsNodeResponse[]): number {
  // Prefer the declared top-level budget (e.g. DMIC root 150000) and fall back
  // to summing all levels if no root has budgetCrores.
  const rootsWithBudget = nodes.filter((n) => n.budgetCrores != null);
  if (rootsWithBudget.length > 0) {
    return rootsWithBudget.reduce((s, n) => s + Number(n.budgetCrores ?? 0), 0);
  }
  return sumBudgetCrores(nodes);
}

interface SummaryCard {
  label: string;
  value: string;
  color: string;
}

interface ExpenseRow {
  id: string;
  description: string;
  actualCost: number;
  expenseCategory: string;
  actualStartDate: string | null;
  activityId: string | null;
}

const FORECAST_METHODS: { value: ForecastMethod; label: string }[] = [
  { value: "LINEAR", label: "Linear" },
  { value: "CPI_BASED", label: "CPI-Based" },
  { value: "SPI_CPI_COMPOSITE", label: "SPI×CPI Composite" },
];

export function CostsTab({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const { baseCurrency } = useCurrency();
  const [forecastMethod, setForecastMethod] = useState<ForecastMethod>("LINEAR");
  const [showExpenseForm, setShowExpenseForm] = useState(false);
  const [expenseForm, setExpenseForm] = useState<CreateExpenseRequest>({
    description: "",
    actualCost: 0,
    currency: "INR",
    actualStartDate: new Date().toISOString().split("T")[0],
    expenseCategory: "LABOR",
  });

  const createExpenseMutation = useMutation({
    mutationFn: (data: CreateExpenseRequest) => costApi.createExpense(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["expenses", projectId] });
      queryClient.invalidateQueries({ queryKey: ["cost-summary", projectId] });
      setShowExpenseForm(false);
      setExpenseForm({ description: "", actualCost: 0, currency: baseCurrency.code, actualStartDate: new Date().toISOString().split("T")[0], expenseCategory: "LABOR" });
      toast.success("Expense recorded successfully");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to create expense"));
    },
  });

  const [editingExpenseId, setEditingExpenseId] = useState<string | null>(null);

  const updateExpenseMutation = useMutation({
    mutationFn: (data: CreateExpenseRequest) => costApi.updateExpense(projectId, editingExpenseId!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["expenses", projectId] });
      queryClient.invalidateQueries({ queryKey: ["cost-summary", projectId] });
      setShowExpenseForm(false);
      setEditingExpenseId(null);
      setExpenseForm({ description: "", actualCost: 0, currency: baseCurrency.code, actualStartDate: new Date().toISOString().split("T")[0], expenseCategory: "LABOR" });
      toast.success("Expense updated successfully");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to update expense"));
    },
  });

  const deleteExpenseMutation = useMutation({
    mutationFn: (expenseId: string) => costApi.deleteExpense(projectId, expenseId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["expenses", projectId] });
      queryClient.invalidateQueries({ queryKey: ["cost-summary", projectId] });
      toast.success("Expense deleted successfully");
    },
    onError: (err: unknown) => {
      toast.error(getErrorMessage(err, "Failed to delete expense"));
    },
  });

  const { data: activitiesData } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 200),
  });

  const activities = useMemo(() => activitiesData?.data?.content ?? [], [activitiesData]);

  const handleEdit = useCallback((expense: ExpenseRow) => {
    setEditingExpenseId(expense.id);
    setExpenseForm({
      description: expense.description,
      actualCost: expense.actualCost,
      currency: baseCurrency.code,
      actualStartDate: expense.actualStartDate,
      expenseCategory: expense.expenseCategory,
      activityId: expense.activityId ?? undefined,
    });
    setShowExpenseForm(true);
  }, [baseCurrency.code]);

  const expenseColumns = useMemo<ColumnDef<ExpenseRow>[]>(() => [
    { key: "description", label: "Description", sortable: true },
    {
      key: "activityId",
      label: "Activity",
      render: (_value, row) => {
        if (!row.activityId) {
          return <span className="text-text-muted">—</span>;
        }
        const activity = activities.find((a) => a.id === row.activityId);
        const label = activity ? `${activity.code} - ${activity.name}` : row.activityId;
        return (
          <Link
            href={`/projects/${projectId}/activities/${row.activityId}`}
            className="text-accent hover:underline"
          >
            {label}
          </Link>
        );
      },
    },
    { key: "expenseCategory", label: "Category", sortable: true },
    {
      key: "actualCost",
      label: "Amount (₹)",
      sortable: true,
      render: (value) => formatInr(Number(value)),
    },
    { key: "actualStartDate", label: "Date", sortable: true },
    {
      key: "actions",
      label: "Actions",
      render: (_value, row) => (
        <div className="flex gap-2">
          <button
            onClick={() => handleEdit(row)}
            className="rounded-md border border-border px-2 py-1 text-xs text-text-secondary hover:bg-surface-hover"
          >
            Edit
          </button>
          <button
            onClick={() => {
              if (window.confirm("Delete this expense?")) {
                deleteExpenseMutation.mutate(row.id);
              }
            }}
            disabled={deleteExpenseMutation.isPending}
            className="rounded-md border border-border px-2 py-1 text-xs text-danger hover:bg-surface-hover disabled:opacity-50"
          >
            Delete
          </button>
        </div>
      ),
    },
  ], [handleEdit, deleteExpenseMutation, activities, projectId]);

  const { data: summaryData, isLoading: isLoadingSummary } = useQuery({
    queryKey: ["cost-summary", projectId],
    queryFn: () => costApi.getCostSummary(projectId),
  });

  const { data: expensesData, isLoading: isLoadingExpenses } = useQuery({
    queryKey: ["expenses", projectId],
    queryFn: () => costApi.getExpensesByProject(projectId, 0, 100),
  });

  const { data: forecastData } = useQuery({
    queryKey: ["cost-forecast", projectId, forecastMethod],
    queryFn: () => costApi.generateForecast(projectId, forecastMethod),
  });

  const { data: periodData } = useQuery({
    queryKey: ["cost-periods", projectId],
    queryFn: () => costApi.getCostPeriods(projectId),
  });

  // Pull the WBS tree so we can derive the budget from `budgetCrores` on the
  // project's WBS nodes (the legacy cost-summary endpoint returns 0 when no
  // expense rows exist even though the WBS has a plan budget).
  const { data: wbsData } = useQuery({
    queryKey: ["wbs", projectId],
    queryFn: () => projectApi.getWbsTree(projectId),
  });

  // P6-style project budget
  const { data: projectBudgetData } = useQuery({
    queryKey: ["project-budget", projectId],
    queryFn: () => budgetApi.getBudgetSummary(projectId),
  });

  const summary = summaryData?.data;
  const expenses = expensesData?.data?.content ?? [];
  const forecastItems: CashFlowForecastItem[] = forecastData?.data ?? [];
  const periodAggregations: PeriodCostAggregation[] = periodData?.data ?? [];
  const wbsTree: WbsNodeResponse[] = wbsData?.data ?? [];

  // Budget precedence: (1) WBS nodes' declared `budgetCrores` (the legacy DMIC
  // planning flow), else (2) cost-summary `totalBudget` (sum of expense
  // budgetedCost — the flow our seed script uses). Without this fallback, any
  // project that plans via expenses displays Total Budget ₹0.00cr despite
  // having real budget data.
  const wbsBudgetCrores = pickTopLevelBudget(wbsTree);
  const summaryBudgetCrores = (summary?.totalBudget ?? 0) / INR_PER_CRORE;
  const budgetCrores = wbsBudgetCrores > 0 ? wbsBudgetCrores : summaryBudgetCrores;
  // summary amounts are INR; convert to crores for display.
  const actualCrores = (summary?.totalActual ?? 0) / INR_PER_CRORE;
  const remainingCrores = Math.max(budgetCrores - actualCrores, 0);
  const atCompletionCrores = Math.max(budgetCrores, actualCrores);

  const chartData = forecastItems.map((item) => ({
    period: item.period,
    planned: item.plannedAmount || 0,
    actual: item.actualAmount || 0,
    forecast: item.forecastAmount || 0,
    cumulativePlanned: item.cumulativePlanned || 0,
    cumulativeActual: item.cumulativeActual || 0,
    cumulativeForecast: item.cumulativeForecast || 0,
  }));

  const summaryCards: SummaryCard[] = [
    {
      label: "Project Budget (P6)",
      value: projectBudgetData?.data?.currentBudget != null
        ? formatCrores(projectBudgetData.data.currentBudget / INR_PER_CRORE)
        : "Not set",
      color: "indigo",
    },
    {
      label: "Total Budget (Expenses)",
      value: formatCrores(budgetCrores),
      color: "blue",
    },
    {
      label: "Total Actual",
      value: formatCrores(actualCrores),
      color: "green",
    },
    {
      label: "Total Remaining",
      value: formatCrores(remainingCrores),
      color: "yellow",
    },
    {
      label: "At Completion",
      value: formatCrores(atCompletionCrores),
      color: "purple",
    },
  ];

  const evmCards: SummaryCard[] = summary
    ? [
        {
          label: "Cost Variance (CV)",
          value: formatInrAsCrores(summary.costVariance),
          color: summary.costVariance >= 0 ? "green" : "red",
        },
        {
          label: "CPI",
          value: summary.costPerformanceIndex != null
            ? summary.costPerformanceIndex.toFixed(4)
            : "—",
          color: summary.costPerformanceIndex != null
            ? summary.costPerformanceIndex >= 1
              ? "green"
              : "red"
            : "slate",
        },
        {
          label: "Expenses",
          value: String(summary.expenseCount),
          color: "slate",
        },
      ]
    : [];

  // PMS MasterData procurement roll-up — shown only when the project has material activity.
  const procurementCards: SummaryCard[] = summary && (summary.materialProcurementCost ?? 0) > 0
    ? [
        {
          label: "Material Procured",
          value: formatInrAsCrores(summary.materialProcurementCost ?? 0),
          color: "blue",
        },
        {
          label: "Open Stock Value",
          value: formatInrAsCrores(summary.openStockValue ?? 0),
          color: "yellow",
        },
        {
          label: "Material Issued",
          value: formatInrAsCrores(summary.materialIssuedCost ?? 0),
          color: "green",
        },
      ]
    : [];

  const accentMap: Record<string, string> = {
    blue: "border-l-4 border-l-accent",
    green: "border-l-4 border-l-success",
    yellow: "border-l-4 border-l-warning",
    purple: "border-l-4 border-l-info",
    red: "border-l-4 border-l-danger",
    indigo: "border-l-4 border-l-indigo-500",
    slate: "",
  };

  const textColorMap: Record<string, string> = {
    blue: "text-accent",
    green: "text-success",
    yellow: "text-warning",
    purple: "text-info",
    red: "text-danger",
    indigo: "text-indigo-600 dark:text-indigo-400",
    slate: "text-text-primary",
  };

  return (
    <div className="space-y-6">
      {isLoadingSummary ? (
        <div className="text-center text-text-secondary">Loading cost summary...</div>
      ) : (
        // The whole financial roll-up (budget/actual/remaining/EVM/procurement) is FINANCE/PMO-only.
        // Backend already strips the underlying fields via @JsonView; this just gives the UI a clean
        // placeholder instead of empty cards full of zeros for users who aren't entitled to see them.
        <SecretField visibleTo={FINANCE_ROLES} masked={NO_FINANCE_PLACEHOLDER}>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {summaryCards.map((card) => (
              <div
                key={card.label}
                className={`rounded-lg border border-border bg-surface-hover/40 p-4 ${accentMap[card.color]}`}
              >
                <h3 className="text-xs font-medium uppercase tracking-wide text-text-secondary">{card.label}</h3>
                <p className={`mt-2 text-2xl font-bold ${textColorMap[card.color]}`}>
                  {card.value}
                </p>
              </div>
            ))}
          </div>

          {evmCards.length > 0 && (
            <div className="grid grid-cols-3 gap-4">
              {evmCards.map((card) => (
                <div
                  key={card.label}
                  className={`rounded-lg border border-border bg-surface-hover/40 p-4 ${accentMap[card.color]}`}
                >
                  <h3 className="text-xs font-medium uppercase tracking-wide text-text-secondary">{card.label}</h3>
                  <p className={`mt-2 text-xl font-bold ${textColorMap[card.color]}`}>
                    {card.value}
                  </p>
                </div>
              ))}
            </div>
          )}

          {procurementCards.length > 0 && (
            <div>
              <h3 className="mb-2 text-sm font-semibold uppercase tracking-wide text-text-secondary">
                Material Procurement
              </h3>
              <div className="grid grid-cols-3 gap-4">
                {procurementCards.map((card) => (
                  <div
                    key={card.label}
                    className={`rounded-lg border border-border bg-surface-hover/40 p-4 ${accentMap[card.color]}`}
                  >
                    <h4 className="text-xs font-medium uppercase tracking-wide text-text-secondary">{card.label}</h4>
                    <p className={`mt-2 text-xl font-bold ${textColorMap[card.color]}`}>
                      {card.value}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </SecretField>
      )}

      {/* Cash Flow S-Curve with Forecast Method Selector */}
      <SecretField visibleTo={FINANCE_ROLES} masked={null}>
      <div className="rounded-lg border border-border bg-surface/50 p-6">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-text-primary">Cash Flow S-Curve</h3>
          <div className="flex items-center gap-2">
            <label className="text-sm text-text-secondary">Forecast Method:</label>
            <select
              value={forecastMethod}
              onChange={(e) => setForecastMethod(e.target.value as ForecastMethod)}
              className="rounded-md border border-border bg-surface-hover px-3 py-1.5 text-sm text-text-primary focus:border-accent focus:outline-none"
            >
              {FORECAST_METHODS.map((m) => (
                <option key={m.value} value={m.value}>
                  {m.label}
                </option>
              ))}
            </select>
          </div>
        </div>
        {chartData.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border py-12 text-center">
            <p className="text-text-secondary">No forecast data available. Create financial periods and expenses first.</p>
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={400}>
            <LineChart data={chartData} margin={{ top: 5, right: 30, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--grid-color)" />
              <XAxis
                dataKey="period"
                stroke="var(--text-muted)"
                style={{ fontSize: "12px" }}
              />
              <YAxis
                stroke="var(--text-muted)"
                style={{ fontSize: "12px" }}
                label={{ value: "Amount (₹cr)", angle: -90, position: "insideLeft" }}
                tickFormatter={(v) =>
                  typeof v === "number" ? (v / INR_PER_CRORE).toFixed(0) : String(v)
                }
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: "var(--surface)",
                  border: "1px solid var(--border)",
                  borderRadius: "0.5rem",
                  color: "var(--text-primary)",
                }}
                formatter={(value) =>
                  typeof value === "number"
                    ? formatInrAsCrores(value)
                    : String(value ?? "")
                }
              />
              <Legend />
              <Line
                type="monotone"
                dataKey="cumulativePlanned"
                name="Planned"
                stroke="var(--accent)"
                strokeWidth={2}
                dot={false}
              />
              <Line
                type="monotone"
                dataKey="cumulativeActual"
                name="Actual"
                stroke="var(--success)"
                strokeWidth={2}
                dot={false}
              />
              <Line
                type="monotone"
                dataKey="cumulativeForecast"
                name="Forecast"
                stroke="var(--warning)"
                strokeWidth={2}
                strokeDasharray="5 5"
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
      </SecretField>

      {/* Period-by-Period Cost Table */}
      {periodAggregations.length > 0 && (
        <SecretField visibleTo={FINANCE_ROLES} masked={null}>
        <div className="rounded-lg border border-border bg-surface/50 p-6">
          <h3 className="mb-4 text-lg font-semibold text-text-primary">
            Period Cost Breakdown
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-text-secondary">
                  <th className="px-3 py-2">Period</th>
                  <th className="px-3 py-2 text-right">Budget (₹cr)</th>
                  <th className="px-3 py-2 text-right">Actual (₹cr)</th>
                  <th className="px-3 py-2 text-right">Variance (₹cr)</th>
                  <th className="px-3 py-2 text-right">Earned Value (₹cr)</th>
                  <th className="px-3 py-2 text-right">Planned Value (₹cr)</th>
                </tr>
              </thead>
              <tbody>
                {periodAggregations.map((pa) => (
                  <tr
                    key={pa.periodId}
                    className="border-b border-border hover:bg-surface-hover/50"
                  >
                    <td className="px-3 py-2 text-text-primary">{pa.periodName}</td>
                    <td className="px-3 py-2 text-right text-accent">
                      {formatInrAsCrores(pa.budget)}
                    </td>
                    <td className="px-3 py-2 text-right text-success">
                      {formatInrAsCrores(pa.actual)}
                    </td>
                    <td
                      className={`px-3 py-2 text-right ${
                        pa.variance >= 0 ? "text-success" : "text-danger"
                      }`}
                    >
                      {formatInrAsCrores(pa.variance)}
                    </td>
                    <td className="px-3 py-2 text-right text-warning">
                      {formatInrAsCrores(pa.earnedValue)}
                    </td>
                    <td className="px-3 py-2 text-right text-info">
                      {formatInrAsCrores(pa.plannedValue)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        </SecretField>
      )}

      <SecretField visibleTo={FINANCE_ROLES} masked={null}>
      <div>
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-text-primary">Expenses</h3>
          <button
            onClick={() => {
              if (showExpenseForm) {
                setEditingExpenseId(null);
                    setExpenseForm({ description: "", actualCost: 0, currency: baseCurrency.code, actualStartDate: new Date().toISOString().split("T")[0], expenseCategory: "LABOR" });
              }
              setShowExpenseForm(!showExpenseForm);
            }}
            className="inline-flex items-center gap-2 rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover"
          >
            <Plus size={16} />
            Add Expense
          </button>
        </div>

        {showExpenseForm && (
          <div className="mb-4 rounded-lg border border-border bg-surface-hover/50 p-4">
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!expenseForm.description || !expenseForm.actualCost) return;
                if (editingExpenseId) {
                  updateExpenseMutation.mutate(expenseForm);
                } else {
                  createExpenseMutation.mutate(expenseForm);
                }
              }}
              className="grid grid-cols-2 gap-4 lg:grid-cols-4"
            >
              <div>
                <label className="block text-xs font-medium text-text-secondary">Description *</label>
                <input
                  type="text"
                  value={expenseForm.description}
                  onChange={(e) => setExpenseForm((prev) => ({ ...prev, description: e.target.value }))}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  placeholder="e.g., Concrete delivery"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-text-secondary">Amount ({baseCurrency.symbol}) *</label>
                <input
                  type="number"
                  value={expenseForm.actualCost || ""}
                  onChange={(e) => setExpenseForm((prev) => ({ ...prev, actualCost: parseFloat(e.target.value) || 0 }))}
                  min="0"
                  step="0.01"
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary placeholder-text-muted focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                  placeholder="e.g., 5000"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-text-secondary">Category</label>
                <select
                  value={expenseForm.expenseCategory}
                  onChange={(e) => setExpenseForm((prev) => ({ ...prev, expenseCategory: e.target.value }))}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                >
                  <option value="LABOR">Labor</option>
                  <option value="MATERIAL">Material</option>
                  <option value="EQUIPMENT">Equipment</option>
                  <option value="SUBCONTRACT">Subcontract</option>
                  <option value="OVERHEAD">Overhead</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-text-secondary">Date</label>
                <input
                  type="date"
                  value={expenseForm.actualStartDate ?? ""}
                  onChange={(e) => setExpenseForm((prev) => ({ ...prev, actualStartDate: e.target.value || null }))}
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-text-secondary">Activity (optional)</label>
                <select
                  value={expenseForm.activityId ?? ""}
                  onChange={(e) =>
                    setExpenseForm((prev) => ({
                      ...prev,
                      activityId: e.target.value || undefined,
                    }))
                  }
                  className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
                >
                  <option value="">(Unassigned)</option>
                  {activities.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.code} - {a.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className="col-span-full flex gap-2">
                <button
                  type="submit"
                  disabled={createExpenseMutation.isPending || updateExpenseMutation.isPending}
                  className="rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:bg-border"
                >
                  {editingExpenseId
                    ? (updateExpenseMutation.isPending ? "Updating..." : "Update Expense")
                    : (createExpenseMutation.isPending ? "Saving..." : "Save Expense")}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowExpenseForm(false);
                    setEditingExpenseId(null);
                setExpenseForm({ description: "", actualCost: 0, currency: baseCurrency.code, actualStartDate: new Date().toISOString().split("T")[0], expenseCategory: "LABOR" });
                  }}
                  className="rounded-md border border-border px-4 py-2 text-sm font-medium text-text-secondary hover:bg-surface-hover/50"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}

        {isLoadingExpenses ? (
          <div className="text-center text-text-muted">Loading expenses...</div>
        ) : expenses.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border py-12 text-center">
            <h3 className="text-lg font-medium text-text-primary">No Expenses</h3>
            <p className="mt-2 text-text-muted">No expenses recorded yet.</p>
          </div>
        ) : (
          <DataTable columns={expenseColumns} data={expenses} rowKey="id" />
        )}
      </div>
      </SecretField>
    </div>
  );
}
