"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  boqApi,
  type BoqItemResponse,
  type BoqSummaryResponse,
  type CreateBoqItemRequest,
  type UpdateBoqItemRequest,
} from "@/lib/api/boqApi";
import { TabTip } from "@/components/common/TabTip";
import { getErrorMessage } from "@/lib/utils/error";

const UNIT_OPTIONS = ["Cum", "MT", "Rm", "Each", "Sqm", "LS"] as const;

type EditableField = "qtyExecutedToDate" | "actualRate";

interface BoqForm {
  itemNo: string;
  description: string;
  unit: string;
  boqQty: string;
  boqRate: string;
  budgetedRate: string;
  qtyExecutedToDate: string;
  actualRate: string;
}

const initialFormState: BoqForm = {
  itemNo: "",
  description: "",
  unit: "Cum",
  boqQty: "",
  boqRate: "",
  budgetedRate: "",
  qtyExecutedToDate: "",
  actualRate: "",
};

function formatAmount(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return value.toLocaleString("en-IN");
}

function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return (value * 100).toFixed(2) + "%";
}

export default function BoqPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const queryClient = useQueryClient();

  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<BoqForm>(initialFormState);
  const [formError, setFormError] = useState<string | null>(null);
  const [editingCell, setEditingCell] = useState<{ itemId: string; field: EditableField } | null>(null);
  const [editingValue, setEditingValue] = useState<string>("");

  const {
    data: summaryResponse,
    isLoading,
    error: queryError,
  } = useQuery({
    queryKey: ["boq", projectId],
    queryFn: () => boqApi.list(projectId),
  });

  const summary: BoqSummaryResponse | null | undefined = summaryResponse?.data;
  const items: BoqItemResponse[] = summary?.items ?? [];

  const updateMutation = useMutation({
    mutationFn: ({ itemId, request }: { itemId: string; request: UpdateBoqItemRequest }) =>
      boqApi.update(projectId, itemId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["boq", projectId] });
    },
  });

  const createMutation = useMutation({
    mutationFn: (request: CreateBoqItemRequest) => boqApi.create(projectId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["boq", projectId] });
      setFormData(initialFormState);
      setShowForm(false);
      setFormError(null);
    },
    onError: (err: unknown) => {
      setFormError(getErrorMessage(err, "Failed to create BOQ item"));
    },
  });

  const beginEdit = (item: BoqItemResponse, field: EditableField) => {
    setEditingCell({ itemId: item.id, field });
    const current = item[field];
    setEditingValue(current === null || current === undefined ? "" : String(current));
  };

  const commitEdit = () => {
    if (!editingCell) return;
    const trimmed = editingValue.trim();
    const numericValue = trimmed === "" ? null : Number(trimmed);
    if (numericValue !== null && Number.isNaN(numericValue)) {
      setEditingCell(null);
      return;
    }
    const item = items.find((it) => it.id === editingCell.itemId);
    const previous = item ? item[editingCell.field] : null;
    if (previous === numericValue) {
      setEditingCell(null);
      return;
    }
    const request: UpdateBoqItemRequest =
      editingCell.field === "qtyExecutedToDate"
        ? { qtyExecutedToDate: numericValue }
        : { actualRate: numericValue };
    updateMutation.mutate({ itemId: editingCell.itemId, request });
    setEditingCell(null);
  };

  const cancelEdit = () => {
    setEditingCell(null);
    setEditingValue("");
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    if (!formData.itemNo.trim() || !formData.description.trim() || !formData.unit) {
      setFormError("Item No, Description and Unit are required");
      return;
    }
    const request: CreateBoqItemRequest = {
      itemNo: formData.itemNo.trim(),
      description: formData.description.trim(),
      unit: formData.unit,
      boqQty: formData.boqQty === "" ? undefined : Number(formData.boqQty),
      boqRate: formData.boqRate === "" ? undefined : Number(formData.boqRate),
      budgetedRate: formData.budgetedRate === "" ? undefined : Number(formData.budgetedRate),
      qtyExecutedToDate: formData.qtyExecutedToDate === "" ? undefined : Number(formData.qtyExecutedToDate),
      actualRate: formData.actualRate === "" ? undefined : Number(formData.actualRate),
    };
    createMutation.mutate(request);
  };

  const varianceClass = (value: number | null | undefined): string => {
    if (value === null || value === undefined || value === 0) return "";
    return value > 0 ? "text-danger" : "text-success";
  };

  if (isLoading) {
    return <div className="p-6 text-text-muted">Loading BOQ...</div>;
  }

  const errorMessage = queryError ? getErrorMessage(queryError, "Failed to load BOQ") : null;

  return (
    <div className="p-6">
      <TabTip
        title="Bill of Quantities"
        description="Plan each line item by quantity multiplied by rate, then track executed quantity and actual rate to surface cost variance against the original BOQ and the internal budget."
      />
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-text-primary">Bill of Quantities</h1>

        <button
          onClick={() => {
            setShowForm(!showForm);
            setFormError(null);
          }}
          className="mb-6 px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
        >
          {showForm ? "Cancel" : "Add BOQ Item"}
        </button>

        {errorMessage && <div className="text-danger mb-4">{errorMessage}</div>}
        {formError && <div className="text-danger mb-4">{formError}</div>}

        {showForm && (
          <form onSubmit={handleSubmit} className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Item No</label>
                <input
                  type="text"
                  value={formData.itemNo}
                  onChange={(e) => setFormData({ ...formData, itemNo: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Description</label>
                <input
                  type="text"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Unit</label>
                <select
                  value={formData.unit}
                  onChange={(e) => setFormData({ ...formData, unit: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                >
                  {UNIT_OPTIONS.map((u) => (
                    <option key={u} value={u}>
                      {u}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">BOQ Qty</label>
                <input
                  type="number"
                  step="any"
                  value={formData.boqQty}
                  onChange={(e) => setFormData({ ...formData, boqQty: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">BOQ Rate</label>
                <input
                  type="number"
                  step="any"
                  value={formData.boqRate}
                  onChange={(e) => setFormData({ ...formData, boqRate: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Budgeted Rate</label>
                <input
                  type="number"
                  step="any"
                  value={formData.budgetedRate}
                  onChange={(e) => setFormData({ ...formData, budgetedRate: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Qty Executed (optional)</label>
                <input
                  type="number"
                  step="any"
                  value={formData.qtyExecutedToDate}
                  onChange={(e) => setFormData({ ...formData, qtyExecutedToDate: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Actual Rate (optional)</label>
                <input
                  type="number"
                  step="any"
                  value={formData.actualRate}
                  onChange={(e) => setFormData({ ...formData, actualRate: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <button
                type="submit"
                disabled={createMutation.isPending}
                className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600 disabled:opacity-50"
              >
                {createMutation.isPending ? "Saving..." : "Save Item"}
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowForm(false);
                  setFormError(null);
                }}
                className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {/* BOQ Table */}
        <div className="overflow-x-auto">
          <table className="w-full border-collapse border border-border">
            <thead>
              <tr className="bg-surface/80">
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Item No.</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Description</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Unit</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">BOQ Qty</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">BOQ Rate</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">BOQ Amount</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Budgeted Rate</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Budgeted Amount</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Qty Executed</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Actual Rate</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Actual Amount</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">% Complete</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Cost Variance</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Var %</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const isEditingQty =
                  editingCell?.itemId === item.id && editingCell.field === "qtyExecutedToDate";
                const isEditingRate =
                  editingCell?.itemId === item.id && editingCell.field === "actualRate";
                return (
                  <tr key={item.id} className="hover:bg-surface-hover/30 text-text-primary">
                    <td className="border border-border px-4 py-2">{item.itemNo}</td>
                    <td className="border border-border px-4 py-2">{item.description}</td>
                    <td className="border border-border px-4 py-2">{item.unit}</td>
                    <td className="border border-border px-4 py-2 text-right">{formatAmount(item.boqQty)}</td>
                    <td className="border border-border px-4 py-2 text-right">{formatAmount(item.boqRate)}</td>
                    <td className="border border-border px-4 py-2 text-right">{formatAmount(item.boqAmount)}</td>
                    <td className="border border-border px-4 py-2 text-right">{formatAmount(item.budgetedRate)}</td>
                    <td className="border border-border px-4 py-2 text-right">{formatAmount(item.budgetedAmount)}</td>
                    <td
                      className="border border-border px-4 py-2 text-right cursor-pointer"
                      onClick={() => {
                        if (!isEditingQty) beginEdit(item, "qtyExecutedToDate");
                      }}
                    >
                      {isEditingQty ? (
                        <input
                          autoFocus
                          type="number"
                          step="any"
                          value={editingValue}
                          onChange={(e) => setEditingValue(e.target.value)}
                          onBlur={commitEdit}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") {
                              e.preventDefault();
                              commitEdit();
                            } else if (e.key === "Escape") {
                              e.preventDefault();
                              cancelEdit();
                            }
                          }}
                          className="w-full px-2 py-1 border border-border bg-surface-hover text-text-primary rounded text-right"
                        />
                      ) : (
                        formatAmount(item.qtyExecutedToDate)
                      )}
                    </td>
                    <td
                      className="border border-border px-4 py-2 text-right cursor-pointer"
                      onClick={() => {
                        if (!isEditingRate) beginEdit(item, "actualRate");
                      }}
                    >
                      {isEditingRate ? (
                        <input
                          autoFocus
                          type="number"
                          step="any"
                          value={editingValue}
                          onChange={(e) => setEditingValue(e.target.value)}
                          onBlur={commitEdit}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") {
                              e.preventDefault();
                              commitEdit();
                            } else if (e.key === "Escape") {
                              e.preventDefault();
                              cancelEdit();
                            }
                          }}
                          className="w-full px-2 py-1 border border-border bg-surface-hover text-text-primary rounded text-right"
                        />
                      ) : (
                        formatAmount(item.actualRate)
                      )}
                    </td>
                    <td className="border border-border px-4 py-2 text-right">{formatAmount(item.actualAmount)}</td>
                    <td className="border border-border px-4 py-2 text-right">{formatPercent(item.percentComplete)}</td>
                    <td className={`border border-border px-4 py-2 text-right ${varianceClass(item.costVariance)}`}>
                      {formatAmount(item.costVariance)}
                    </td>
                    <td className={`border border-border px-4 py-2 text-right ${varianceClass(item.costVariancePercent)}`}>
                      {formatPercent(item.costVariancePercent)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
            {summary && (
              <tfoot>
                <tr className="sticky bottom-0 bg-surface/95 backdrop-blur text-text-primary font-semibold">
                  <td className="border border-border px-4 py-2" colSpan={5}>
                    Grand Total
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {formatAmount(summary.boqGrandTotal)}
                  </td>
                  <td className="border border-border px-4 py-2" />
                  <td className="border border-border px-4 py-2 text-right">
                    {formatAmount(summary.budgetedGrandTotal)}
                  </td>
                  <td className="border border-border px-4 py-2" />
                  <td className="border border-border px-4 py-2" />
                  <td className="border border-border px-4 py-2 text-right">
                    {formatAmount(summary.actualGrandTotal)}
                  </td>
                  <td className="border border-border px-4 py-2 text-right">
                    {formatPercent(summary.overallPercentComplete)}
                  </td>
                  <td className={`border border-border px-4 py-2 text-right ${varianceClass(summary.grandCostVariance)}`}>
                    {formatAmount(summary.grandCostVariance)}
                  </td>
                  <td className={`border border-border px-4 py-2 text-right ${varianceClass(summary.grandCostVariancePercent)}`}>
                    {formatPercent(summary.grandCostVariancePercent)}
                  </td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      </div>
    </div>
  );
}
