"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { raBillApi, type CreateRaBillRequest } from "@/lib/api/raBillApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { Button } from "@/components/ui/button";
import { Plus } from "lucide-react";
import { TabTip } from "@/components/common/TabTip";
import { formatDefaultCurrency } from "@/lib/hooks/useCurrency";

interface RaBillRow {
  id: string;
  billNumber: string;
  billPeriodFrom: string;
  billPeriodTo: string;
  netAmount: number;
  status: string;
}

export default function RaBillsPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const queryClient = useQueryClient();
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectedBillId, setSelectedBillId] = useState<string | null>(null);

  const { data: billsData, isLoading: isLoadingBills } = useQuery({
    queryKey: ["ra-bills", projectId],
    queryFn: () => raBillApi.getRaBillsByProject(projectId),
  });

  const { data: billItemsData, isLoading: isLoadingItems } = useQuery({
    queryKey: ["ra-bill-items", selectedBillId],
    queryFn: () =>
      selectedBillId ? raBillApi.getRaBillItems(selectedBillId) : null,
    enabled: !!selectedBillId,
  });

  const createBillMutation = useMutation({
    mutationFn: (request: CreateRaBillRequest) =>
      raBillApi.createRaBill(projectId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ra-bills", projectId] });
      setShowCreateForm(false);
    },
  });

  const bills = billsData?.data ?? [];
  const billItems = billItemsData?.data ?? [];

  const billColumns: ColumnDef<RaBillRow>[] = [
    { key: "billNumber", label: "Bill Number", sortable: true },
    { key: "billPeriodFrom", label: "Period From", sortable: true },
    { key: "billPeriodTo", label: "Period To", sortable: true },
    {
      key: "netAmount",
      label: "Net Amount",
      sortable: true,
      render: (value) => formatDefaultCurrency(Number(value)),
    },
    { key: "status", label: "Status", sortable: true },
  ];

  const handleCreateBill = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);

    const request: CreateRaBillRequest = {
      projectId,
      contractId: formData.get("contractId") as string || undefined,
      billNumber: formData.get("billNumber") as string,
      billPeriodFrom: formData.get("billPeriodFrom") as string,
      billPeriodTo: formData.get("billPeriodTo") as string,
      grossAmount: Number(formData.get("grossAmount")),
      deductions: Number(formData.get("deductions")) || 0,
      netAmount: Number(formData.get("netAmount")),
      remarks: formData.get("remarks") as string || undefined,
    };

    createBillMutation.mutate(request);
  };

  return (
    <div className="space-y-6 p-6">
      <TabTip
        title="Running Account Bills (RA Bills)"
        description="RA Bills are periodic payment certificates for contractors. Each bill covers measured work done during a period and goes through submission, certification, and approval stages."
      />
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-white">RA Bills</h1>
        <Button
          onClick={() => setShowCreateForm(!showCreateForm)}
          className="gap-2"
        >
          <Plus size={20} />
          Create RA Bill
        </Button>
      </div>

      {showCreateForm && (
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 shadow-xl">
          <h2 className="mb-4 text-lg font-semibold text-white">
            Create New RA Bill
          </h2>
          <form onSubmit={handleCreateBill} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-300">
                  Bill Number
                </label>
                <input
                  type="text"
                  name="billNumber"
                  required
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 text-white placeholder-slate-500 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300">
                  Contract ID (Optional)
                </label>
                <input
                  type="text"
                  name="contractId"
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 text-white placeholder-slate-500 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-300">
                  Bill Period From
                </label>
                <input
                  type="date"
                  name="billPeriodFrom"
                  required
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 text-white placeholder-slate-500 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300">
                  Bill Period To
                </label>
                <input
                  type="date"
                  name="billPeriodTo"
                  required
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 text-white placeholder-slate-500 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                />
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-300">
                  Gross Amount
                </label>
                <input
                  type="number"
                  name="grossAmount"
                  step="0.01"
                  required
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 text-white placeholder-slate-500 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300">
                  Deductions
                </label>
                <input
                  type="number"
                  name="deductions"
                  step="0.01"
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 text-white placeholder-slate-500 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-300">
                  Net Amount
                </label>
                <input
                  type="number"
                  name="netAmount"
                  step="0.01"
                  required
                  className="mt-1 block w-full rounded-md border border-slate-700 bg-slate-800 text-white placeholder-slate-500 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300">
                Remarks (Optional)
              </label>
              <textarea
                name="remarks"
                rows={3}
                className="mt-1 block w-full rounded-md border-slate-700 shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm"
              />
            </div>

            <div className="flex gap-2">
              <button
                type="submit"
                disabled={createBillMutation.isPending}
                className="rounded-md bg-blue-600 px-4 py-2 text-white hover:bg-blue-500 disabled:opacity-50"
              >
                {createBillMutation.isPending ? "Creating..." : "Create Bill"}
              </button>
              <button
                type="button"
                onClick={() => setShowCreateForm(false)}
                className="rounded-md bg-slate-700/50 px-4 py-2 text-slate-300 hover:bg-slate-600"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 shadow-xl">
        <h2 className="mb-4 text-lg font-semibold text-white">
          RA Bills List
        </h2>
        {isLoadingBills ? (
          <div className="text-center text-slate-500">Loading RA Bills...</div>
        ) : bills.length === 0 ? (
          <div className="rounded-lg border border-dashed border-slate-700 py-12 text-center">
            <h3 className="text-lg font-medium text-white">No RA Bills</h3>
            <p className="mt-2 text-slate-500">
              No RA bills created yet. Create one to get started.
            </p>
          </div>
        ) : (
          <DataTable
            columns={billColumns}
            data={bills}
            rowKey="id"
            onRowClick={(bill) => setSelectedBillId(bill.id)}
          />
        )}
      </div>

      {selectedBillId && (
        <div className="rounded-lg border border-slate-800 bg-slate-900/50 p-6 shadow-xl">
          <h2 className="mb-4 text-lg font-semibold text-white">
            Bill Items
          </h2>
          {isLoadingItems ? (
            <div className="text-center text-slate-500">
              Loading bill items...
            </div>
          ) : billItems.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-700 py-8 text-center">
              <p className="text-slate-500">No items in this bill yet.</p>
            </div>
          ) : (
            <div className="space-y-4">
              {billItems.map((item) => (
                <div
                  key={item.id}
                  className="flex justify-between border-b border-slate-700 pb-4"
                >
                  <div>
                    <p className="font-medium text-white">{item.itemCode}</p>
                    <p className="text-sm text-slate-500">{item.description}</p>
                  </div>
                  <div className="text-right">
                    <p className="font-medium text-white">
                      ${Number(item.amount).toFixed(2)}
                    </p>
                    {item.unit && (
                      <p className="text-sm text-slate-500">Unit: {item.unit}</p>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
