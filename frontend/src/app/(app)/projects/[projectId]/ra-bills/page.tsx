"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  raBillApi,
  type CreateRaBillRequest,
  type RaBill,
  type SatelliteGate,
} from "@/lib/api/raBillApi";
import { DataTable, type ColumnDef } from "@/components/common/DataTable";
import { Button } from "@/components/ui/button";
import { Plus } from "lucide-react";
import { TabTip } from "@/components/common/TabTip";

const gateBadge = (gate?: SatelliteGate | null) => {
  if (!gate) return "bg-surface-active/40 text-text-secondary";
  switch (gate) {
    case "PASS":
      return "bg-success/20 text-success border border-success/40";
    case "HOLD_VARIANCE":
      return "bg-amber-500/20 text-warning border border-warning/40";
    case "RED_VARIANCE":
    case "HOLD_SATELLITE_DISPUTE":
      return "bg-red-500/20 text-danger border border-danger/40";
    default:
      return "bg-surface-active/40 text-text-secondary";
  }
};

const statusBadge = (status: string) => {
  switch (status) {
    case "PAID":
    case "PAID_PMC_OVERRIDE":
      return "bg-success/20 text-success";
    case "APPROVED":
    case "CERTIFIED":
      return "bg-blue-500/20 text-blue-300";
    case "PMC_REVIEW_PENDING":
    case "SUBMITTED":
      return "bg-indigo-500/20 text-indigo-300";
    case "HOLD_SATELLITE_DISPUTE":
      return "bg-amber-500/20 text-warning";
    case "REJECTED":
      return "bg-red-500/20 text-danger";
    default:
      return "bg-surface-active/40 text-text-secondary";
  }
};

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

  const billColumns: ColumnDef<RaBill>[] = [
    { key: "billNumber", label: "Bill Number", sortable: true },
    { key: "wbsPackageCode", label: "Package", sortable: true },
    { key: "billPeriodFrom", label: "From", sortable: true },
    { key: "billPeriodTo", label: "To", sortable: true },
    {
      key: "grossAmount",
      label: "Gross",
      sortable: true,
      render: (value) => `₹${Number(value).toLocaleString("en-IN")}`,
    },
    {
      key: "netAmount",
      label: "Net",
      sortable: true,
      render: (value) => `₹${Number(value).toLocaleString("en-IN")}`,
    },
    {
      key: "contractorClaimedPercent",
      label: "Claim %",
      sortable: true,
      render: (value) =>
        value != null ? `${Number(value).toFixed(1)}%` : "—",
    },
    {
      key: "aiSatellitePercent",
      label: "AI %",
      sortable: true,
      render: (value) =>
        value != null ? `${Number(value).toFixed(1)}%` : "—",
    },
    {
      key: "satelliteGate",
      label: "Satellite Gate",
      sortable: true,
      render: (value, row) => {
        const gate = value as SatelliteGate | null | undefined;
        if (!gate) return <span className="text-text-muted">—</span>;
        const variance = row.satelliteGateVariance;
        return (
          <span
            className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-medium ${gateBadge(gate)}`}
          >
            {gate.replace(/_/g, " ")}
            {variance != null && ` (Δ${Number(variance).toFixed(1)}%)`}
          </span>
        );
      },
    },
    {
      key: "status",
      label: "Status",
      sortable: true,
      render: (value) => (
        <span
          className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-medium ${statusBadge(String(value))}`}
        >
          {String(value).replace(/_/g, " ")}
        </span>
      ),
    },
  ];

  const handleCreateBill = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);

    const num = (name: string) => {
      const v = formData.get(name);
      return v ? Number(v) : undefined;
    };

    const gross = Number(formData.get("grossAmount"));
    const mob = num("mobAdvanceRecovery") ?? 0;
    const ret = num("retention5Pct") ?? 0;
    const tds = num("tds2Pct") ?? 0;
    const gst = num("gst18Pct") ?? 0;
    const deductions = mob + ret + tds + gst;

    const request: CreateRaBillRequest = {
      projectId,
      contractId: (formData.get("contractId") as string) || undefined,
      wbsPackageCode: (formData.get("wbsPackageCode") as string) || undefined,
      billNumber: formData.get("billNumber") as string,
      billPeriodFrom: formData.get("billPeriodFrom") as string,
      billPeriodTo: formData.get("billPeriodTo") as string,
      grossAmount: gross,
      deductions,
      mobAdvanceRecovery: mob || undefined,
      retention5Pct: ret || undefined,
      tds2Pct: tds || undefined,
      gst18Pct: gst || undefined,
      netAmount: gross - deductions,
      contractorClaimedPercent: num("contractorClaimedPercent"),
      remarks: (formData.get("remarks") as string) || undefined,
    };

    createBillMutation.mutate(request);
  };

  const inputCls =
    "mt-1 block w-full rounded-md border border-border bg-surface-hover text-text-primary placeholder-text-muted shadow-sm focus:border-accent focus:ring-accent sm:text-sm";

  return (
    <div className="space-y-6 p-6">
      <TabTip
        title="Running Account Bills (RA Bills)"
        description="RA Bills are periodic payment certificates for contractors. The Satellite Gate compares contractor-claimed progress against AI-derived satellite progress — variance >5% holds the bill, >10% is a hard stop."
      />
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-text-primary">RA Bills</h1>
        <Button
          onClick={() => setShowCreateForm(!showCreateForm)}
          className="gap-2"
        >
          <Plus size={20} />
          Create RA Bill
        </Button>
      </div>

      {showCreateForm && (
        <div className="rounded-lg border border-border bg-surface/50 p-6 shadow-xl">
          <h2 className="mb-4 text-lg font-semibold text-text-primary">
            Create New RA Bill
          </h2>
          <form onSubmit={handleCreateBill} className="space-y-4">
            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Bill Number
                </label>
                <input
                  type="text"
                  name="billNumber"
                  required
                  placeholder="DMIC-N03-P01-RA-001"
                  className={inputCls}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  WBS Package Code
                </label>
                <input
                  type="text"
                  name="wbsPackageCode"
                  placeholder="DMIC-N03-P01"
                  className={inputCls}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Contract ID (Optional)
                </label>
                <input type="text" name="contractId" className={inputCls} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Bill Period From
                </label>
                <input
                  type="date"
                  name="billPeriodFrom"
                  required
                  className={inputCls}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Bill Period To
                </label>
                <input
                  type="date"
                  name="billPeriodTo"
                  required
                  className={inputCls}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Gross Amount (₹)
                </label>
                <input
                  type="number"
                  name="grossAmount"
                  step="0.01"
                  required
                  className={inputCls}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-text-secondary">
                  Contractor Claimed Progress %
                </label>
                <input
                  type="number"
                  name="contractorClaimedPercent"
                  step="0.01"
                  min="0"
                  max="100"
                  placeholder="e.g. 49.5"
                  className={inputCls}
                />
              </div>
            </div>

            <div className="rounded-md border border-border bg-background/40 p-4">
              <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-text-secondary">
                Deduction Breakdown (CPWD standard)
              </p>
              <div className="grid grid-cols-4 gap-4">
                <div>
                  <label className="block text-xs text-text-secondary">
                    Mob Advance (10%)
                  </label>
                  <input
                    type="number"
                    name="mobAdvanceRecovery"
                    step="0.01"
                    className={inputCls}
                  />
                </div>
                <div>
                  <label className="block text-xs text-text-secondary">
                    Retention (5%)
                  </label>
                  <input
                    type="number"
                    name="retention5Pct"
                    step="0.01"
                    className={inputCls}
                  />
                </div>
                <div>
                  <label className="block text-xs text-text-secondary">
                    TDS (2%)
                  </label>
                  <input
                    type="number"
                    name="tds2Pct"
                    step="0.01"
                    className={inputCls}
                  />
                </div>
                <div>
                  <label className="block text-xs text-text-secondary">
                    GST (18%)
                  </label>
                  <input
                    type="number"
                    name="gst18Pct"
                    step="0.01"
                    className={inputCls}
                  />
                </div>
              </div>
              <p className="mt-2 text-xs text-text-muted">
                Net amount and total deductions are derived from the four
                breakdowns.
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-text-secondary">
                Remarks (Optional)
              </label>
              <textarea
                name="remarks"
                rows={3}
                className={inputCls}
              />
            </div>

            <div className="flex gap-2">
              <button
                type="submit"
                disabled={createBillMutation.isPending}
                className="rounded-md bg-accent px-4 py-2 text-text-primary hover:bg-accent-hover disabled:opacity-50"
              >
                {createBillMutation.isPending ? "Creating..." : "Create Bill"}
              </button>
              <button
                type="button"
                onClick={() => setShowCreateForm(false)}
                className="rounded-md bg-surface-active/50 px-4 py-2 text-text-secondary hover:bg-border"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="rounded-lg border border-border bg-surface/50 p-6 shadow-xl">
        <h2 className="mb-4 text-lg font-semibold text-text-primary">RA Bills List</h2>
        {isLoadingBills ? (
          <div className="text-center text-text-muted">Loading RA Bills...</div>
        ) : bills.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border py-12 text-center">
            <h3 className="text-lg font-medium text-text-primary">No RA Bills</h3>
            <p className="mt-2 text-text-muted">
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
        <div className="rounded-lg border border-border bg-surface/50 p-6 shadow-xl">
          <h2 className="mb-4 text-lg font-semibold text-text-primary">Bill Items</h2>
          {isLoadingItems ? (
            <div className="text-center text-text-muted">
              Loading bill items...
            </div>
          ) : billItems.length === 0 ? (
            <div className="rounded-lg border border-dashed border-border py-8 text-center">
              <p className="text-text-muted">No items in this bill yet.</p>
            </div>
          ) : (
            <div className="space-y-4">
              {billItems.map((item) => (
                <div
                  key={item.id}
                  className="flex justify-between border-b border-border pb-4"
                >
                  <div>
                    <p className="font-medium text-text-primary">{item.itemCode}</p>
                    <p className="text-sm text-text-muted">{item.description}</p>
                  </div>
                  <div className="text-right">
                    <p className="font-medium text-text-primary">
                      ₹{Number(item.amount).toLocaleString("en-IN")}
                    </p>
                    {item.unit && (
                      <p className="text-sm text-text-muted">Unit: {item.unit}</p>
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
