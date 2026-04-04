"use client";

import { useQuery } from "@tanstack/react-query";
import { costApi } from "@/lib/api/costApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";

interface SummaryCard {
  label: string;
  value: string;
  color: string;
}

interface ExpenseRow {
  id: string;
  description: string;
  amount: number;
  category: string;
  expenseDate: string;
}

export function CostsTab({ projectId }: { projectId: string }) {
  const { data: summaryData, isLoading: isLoadingSummary } = useQuery({
    queryKey: ["cost-summary", projectId],
    queryFn: () => costApi.getCostSummary(projectId),
  });

  const { data: expensesData, isLoading: isLoadingExpenses } = useQuery({
    queryKey: ["expenses", projectId],
    queryFn: () => costApi.getExpensesByProject(projectId, 0, 100),
  });

  const summary = summaryData?.data;
  const expenses = expensesData?.data?.content ?? [];

  const summaryCards: SummaryCard[] = [
    {
      label: "Total Budget",
      value: `$${(summary?.totalBudget ?? 0).toFixed(2)}`,
      color: "blue",
    },
    {
      label: "Total Actual",
      value: `$${(summary?.totalActual ?? 0).toFixed(2)}`,
      color: "green",
    },
    {
      label: "Total Remaining",
      value: `$${(summary?.totalRemaining ?? 0).toFixed(2)}`,
      color: "yellow",
    },
    {
      label: "At Completion",
      value: `$${(summary?.atCompletion ?? 0).toFixed(2)}`,
      color: "purple",
    },
  ];

  const expenseColumns: ColumnDef<ExpenseRow>[] = [
    { key: "description", label: "Description", sortable: true },
    { key: "category", label: "Category", sortable: true },
    {
      key: "amount",
      label: "Amount",
      sortable: true,
      render: (value) => `$${Number(value).toFixed(2)}`,
    },
    { key: "expenseDate", label: "Date", sortable: true },
  ];

  const colorMap: Record<string, string> = {
    blue: "bg-blue-50 border-blue-200",
    green: "bg-green-50 border-green-200",
    yellow: "bg-yellow-50 border-yellow-200",
    purple: "bg-purple-50 border-purple-200",
  };

  const textColorMap: Record<string, string> = {
    blue: "text-blue-700",
    green: "text-green-700",
    yellow: "text-yellow-700",
    purple: "text-purple-700",
  };

  return (
    <div className="space-y-6">
      {isLoadingSummary ? (
        <div className="text-center text-gray-500">Loading cost summary...</div>
      ) : (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {summaryCards.map((card) => (
            <div
              key={card.label}
              className={`rounded-lg border p-4 ${colorMap[card.color]}`}
            >
              <h3 className="text-sm font-medium text-gray-700">{card.label}</h3>
              <p className={`mt-2 text-2xl font-bold ${textColorMap[card.color]}`}>
                {card.value}
              </p>
            </div>
          ))}
        </div>
      )}

      <div>
        <h3 className="mb-4 text-lg font-semibold text-gray-900">Expenses</h3>
        {isLoadingExpenses ? (
          <div className="text-center text-gray-500">Loading expenses...</div>
        ) : expenses.length === 0 ? (
          <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
            <h3 className="text-lg font-medium text-gray-900">No Expenses</h3>
            <p className="mt-2 text-gray-500">No expenses recorded yet.</p>
          </div>
        ) : (
          <DataTable columns={expenseColumns} data={expenses} rowKey="id" />
        )}
      </div>
    </div>
  );
}
