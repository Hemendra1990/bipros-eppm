"use client";

import { useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import type { ColumnDef } from "@tanstack/react-table";
import {
  boqApi,
  type BoqItemResponse,
  type BoqSummaryResponse,
  type CreateBoqItemRequest,
  type UpdateBoqItemRequest,
} from "@/lib/api/boqApi";
import { projectApi } from "@/lib/api/projectApi";
import { TabTip } from "@/components/common/TabTip";
import { SplitBoqDialog } from "@/components/boq/SplitBoqDialog";
import { VirtualDataTable } from "@/components/common/VirtualDataTable";
import { SearchableSelect, type SelectOption } from "@/components/common/SearchableSelect";
import { AlertBanner } from "@/components/common/AlertBanner";
import { boqStatusVariant } from "@/components/common/StatusBadge";
import { getErrorMessage } from "@/lib/utils/error";
import { cn } from "@/lib/utils/cn";
import { unitOptionsWithFallback, STANDARD_UNITS } from "@/lib/constants/units";
import { useProjectCurrency } from "@/lib/currency/ProjectCurrencyProvider";

type EditableField = "qtyExecutedToDate" | "actualRate";

function unbilledOverrunValue(item: BoqItemResponse): number {
  const qty = item.qtyExecutedToDate ?? 0;
  const boqQty = item.boqQty ?? 0;
  const rate = item.boqRate ?? 0;
  if (qty <= boqQty) return 0;
  return (qty - boqQty) * rate;
}

interface BoqForm {
  itemNo: string;
  description: string;
  unit: string;
  wbsNodeId: string;
  boqQty: string;
  boqRate: string;
  budgetedRate: string;
  qtyExecutedToDate: string;
  actualRate: string;
}

const initialFormState: BoqForm = {
  itemNo: "",
  description: "",
  unit: "",
  wbsNodeId: "",
  boqQty: "",
  boqRate: "",
  budgetedRate: "",
  qtyExecutedToDate: "",
  actualRate: "",
};

/** Sentinel for the "unassigned" choice in the WBS filter dropdown. */
const WBS_FILTER_NONE = "__none__";

function formatAmount(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return value.toLocaleString("en-IN");
}

function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return (value * 100).toFixed(2) + "%";
}

function varianceClass(value: number | null | undefined): string {
  if (value === null || value === undefined || value === 0) return "";
  return value > 0 ? "text-danger" : "text-success";
}

export default function BoqPage() {
  const params = useParams();
  const router = useRouter();
  const projectId = params.projectId as string;
  const queryClient = useQueryClient();
  const { money } = useProjectCurrency();
  // Money columns (rates, amounts, variance) render with the project's currency symbol;
  // quantity columns keep the bare-number formatAmount (no symbol).
  const formatMoney = (value: number | null | undefined): string =>
    value === null || value === undefined ? "—" : money(value, { decimals: 0 });
  // Actual Rate is shown with 3 decimals so fine-grained site rates aren't rounded to whole units.
  const formatRate = (value: number | null | undefined): string =>
    value === null || value === undefined ? "—" : money(value, { decimals: 3 });

  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<BoqForm>(initialFormState);
  const [formError, setFormError] = useState<string | null>(null);
  const [editingCell, setEditingCell] = useState<{ itemId: string; field: EditableField } | null>(null);
  const [editingValue, setEditingValue] = useState<string>("");
  const [overrunOnly, setOverrunOnly] = useState(false);
  // Row whose WBS cell is in edit mode (inline SearchableSelect), and the table filter.
  const [editingWbsItemId, setEditingWbsItemId] = useState<string | null>(null);
  const [wbsFilter, setWbsFilter] = useState<string>("");
  // Stage 4: line whose split/operations dialog is open.
  const [splitDialogItem, setSplitDialogItem] = useState<BoqItemResponse | null>(null);

  const {
    data: summaryResponse,
    isLoading,
    error: queryError,
  } = useQuery({
    queryKey: ["boq", projectId],
    queryFn: () => boqApi.list(projectId),
  });

  // WBS nodes for the column labels, the Add-form picker and the filter.
  const { data: wbsResponse } = useQuery({
    queryKey: ["wbs", projectId],
    queryFn: () => projectApi.getWbsTree(projectId),
  });
  const wbsNodes = useMemo(() => wbsResponse?.data ?? [], [wbsResponse]);
  const wbsById = useMemo(() => {
    const m = new Map<string, { code: string; name: string }>();
    for (const n of wbsNodes) m.set(n.id, { code: n.code, name: n.name });
    return m;
  }, [wbsNodes]);
  const wbsOptions: SelectOption[] = useMemo(
    () => wbsNodes.map((n) => ({ value: n.id, label: `${n.code} — ${n.name}` })),
    [wbsNodes],
  );

  const summary: BoqSummaryResponse | null | undefined = summaryResponse?.data;
  const allItems: BoqItemResponse[] = summary?.items ?? [];
  const overrunItems = allItems.filter((i) => i.status === "OVERRUN");
  const overrunCount = overrunItems.length;
  const overrunUnbilled = overrunItems.reduce((sum, item) => sum + unbilledOverrunValue(item), 0);
  const overrunScoped = overrunOnly ? overrunItems : allItems;
  const items =
    wbsFilter === ""
      ? overrunScoped
      : overrunScoped.filter((i) =>
          wbsFilter === WBS_FILTER_NONE ? !i.wbsNodeId : i.wbsNodeId === wbsFilter,
        );

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
      wbsNodeId: formData.wbsNodeId || undefined,
      boqQty: formData.boqQty === "" ? undefined : Number(formData.boqQty),
      boqRate: formData.boqRate === "" ? undefined : Number(formData.boqRate),
      budgetedRate: formData.budgetedRate === "" ? undefined : Number(formData.budgetedRate),
      qtyExecutedToDate: formData.qtyExecutedToDate === "" ? undefined : Number(formData.qtyExecutedToDate),
      actualRate: formData.actualRate === "" ? undefined : Number(formData.actualRate),
    };
    createMutation.mutate(request);
  };

  // WBS cell — label when idle, inline SearchableSelect on click (same interaction
  // pattern as the editable number cells). Clearing the select unlinks the item.
  const renderWbsCell = (item: BoqItemResponse) => {
    if (editingWbsItemId === item.id) {
      return (
        <div onClick={(e) => e.stopPropagation()}>
          <SearchableSelect
            options={wbsOptions}
            value={item.wbsNodeId ?? ""}
            onChange={(v) => {
              setEditingWbsItemId(null);
              if ((item.wbsNodeId ?? "") !== v) {
                // Empty selection = unlink. The backend treats wbsNodeId:null as "leave
                // unchanged", so unlinking needs the explicit clearWbsNode flag.
                updateMutation.mutate({
                  itemId: item.id,
                  request: v ? { wbsNodeId: v } : { clearWbsNode: true },
                });
              }
            }}
            placeholder="Pick WBS…"
          />
        </div>
      );
    }
    const wbs = item.wbsNodeId ? wbsById.get(item.wbsNodeId) : null;
    return (
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          setEditingWbsItemId(item.id);
        }}
        className="block w-full text-left hover:text-gold focus:text-gold focus:outline-none"
        title="Click to assign WBS"
      >
        {wbs ? `${wbs.code} — ${wbs.name}` : <span className="text-text-secondary">—</span>}
      </button>
    );
  };

  const renderEditableNumberCell = (item: BoqItemResponse, field: EditableField) => {
    // Stage 4: a split line's executed qty is DERIVED from its operations — the backend
    // rejects manual writes (BOQ_SPLIT_QTY_DERIVED), so don't offer the edit.
    if (field === "qtyExecutedToDate" && item.splitMode) {
      return (
        <span title="Derived from the line's operations — record quantity via DPRs">
          {formatAmount(item[field])}
        </span>
      );
    }
    const isEditing = editingCell?.itemId === item.id && editingCell.field === field;
    if (isEditing) {
      return (
        <input
          autoFocus
          type="number"
          step="any"
          value={editingValue}
          onChange={(e) => setEditingValue(e.target.value)}
          onBlur={commitEdit}
          onClick={(e) => e.stopPropagation()}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              commitEdit();
            } else if (e.key === "Escape") {
              e.preventDefault();
              cancelEdit();
            }
          }}
          className="w-full rounded border border-gold/40 bg-paper px-2 py-1 text-left text-sm text-charcoal focus:outline-none focus:ring-1 focus:ring-gold dark:text-[#F5F2E8]"
        />
      );
    }
    return (
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          beginEdit(item, field);
        }}
        className="block w-full text-left hover:text-gold focus:text-gold focus:outline-none"
        title="Click to edit"
      >
        {field === "actualRate" ? formatRate(item[field]) : formatAmount(item[field])}
      </button>
    );
  };

  const columns = useMemo<ColumnDef<BoqItemResponse>[]>(
    () => [
      {
        accessorKey: "itemNo",
        header: "Item No.",
        size: 90,
        meta: { className: "font-medium" },
        cell: (info) => {
          const row = info.row.original;
          return (
            <span>
              {row.itemNo}
              {row.splitMode && (
                <span
                  className="ml-1 rounded bg-accent/15 px-1 py-0.5 text-[10px] font-semibold text-accent"
                  title={
                    row.splitMode === "QUANTITY_PARTITION"
                      ? "Split: quantity partition"
                      : "Split: weighted operations"
                  }
                >
                  SPLIT
                </span>
              )}
            </span>
          );
        },
      },
      {
        id: "operations",
        header: "Ops",
        size: 92,
        enableSorting: false,
        cell: (info) => {
          const row = info.row.original;
          return (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setSplitDialogItem(row);
              }}
              className={cn(
                "rounded border px-2 py-0.5 text-[11px] font-medium",
                row.splitMode
                  ? "border-accent/40 text-accent hover:bg-accent/10"
                  : "border-border text-text-secondary hover:bg-surface-hover",
              )}
              title={
                row.splitMode
                  ? "View / edit this line's operations"
                  : "Split this line into operations (screening, compaction, …)"
              }
            >
              {row.splitMode ? "Operations…" : "Split…"}
            </button>
          );
        },
      },
      {
        accessorKey: "chapter",
        header: "Chapter",
        size: 110,
        cell: (info) => {
          const v = info.getValue() as string | null | undefined;
          return v ? v : <span className="text-text-secondary">—</span>;
        },
      },
      {
        accessorKey: "description",
        header: "Description",
        size: 280,
        meta: { className: "whitespace-normal" },
      },
      {
        id: "wbs",
        // accessorFn (not accessorKey) so the table's global search and sorting operate on
        // the visible "code — name" label rather than the raw UUID.
        accessorFn: (row) => {
          const wbs = row.wbsNodeId ? wbsById.get(row.wbsNodeId) : null;
          return wbs ? `${wbs.code} — ${wbs.name}` : "";
        },
        header: "WBS",
        size: 170,
        meta: { className: "whitespace-normal" },
        cell: (info) => renderWbsCell(info.row.original),
      },
      {
        accessorKey: "unit",
        header: "Unit",
        size: 70,
      },
      {
        accessorKey: "status",
        header: "Status",
        size: 110,
        cell: (info) => {
          const status = info.getValue() as string | null | undefined;
          if (!status) return <span className="text-text-secondary">—</span>;
          return (
            <span
              className={cn(
                "inline-block rounded-full px-2 py-0.5 text-[11px] font-medium",
                boqStatusVariant(status)
              )}
            >
              {status.replace("_", " ")}
            </span>
          );
        },
      },
      {
        accessorKey: "boqQty",
        header: () => <span className="flex flex-col leading-tight">BOQ Qty<span className="text-[10px] font-normal text-text-muted">(from client)</span></span>,
        size: 90,
        meta: { className: "text-left" },
        cell: (info) => formatAmount(info.getValue() as number | null | undefined),
      },
      {
        accessorKey: "boqRate",
        header: () => <span className="flex flex-col leading-tight">BOQ Rate<span className="text-[10px] font-normal text-text-muted">(from client)</span></span>,
        size: 100,
        meta: { className: "text-left" },
        cell: (info) => formatMoney(info.getValue() as number | null | undefined),
      },
      {
        accessorKey: "boqAmount",
        header: "BOQ Amount",
        size: 120,
        meta: { className: "text-left" },
        cell: (info) => formatMoney(info.getValue() as number | null | undefined),
      },
      {
        accessorKey: "budgetedRate",
        header: "Budgeted Rate",
        size: 120,
        meta: { className: "text-left" },
        cell: (info) => formatMoney(info.getValue() as number | null | undefined),
      },
      {
        accessorKey: "budgetedAmount",
        header: "Budgeted Amt",
        size: 130,
        meta: { className: "text-left" },
        cell: (info) => formatMoney(info.getValue() as number | null | undefined),
      },
      {
        accessorKey: "qtyExecutedToDate",
        header: "Qty Executed",
        size: 120,
        meta: { className: "text-left" },
        cell: (info) => renderEditableNumberCell(info.row.original, "qtyExecutedToDate"),
      },
      {
        accessorKey: "actualRate",
        header: "Actual Rate",
        size: 110,
        meta: { className: "text-left" },
        cell: (info) => renderEditableNumberCell(info.row.original, "actualRate"),
      },
      {
        accessorKey: "actualAmount",
        header: "Actual Amount",
        size: 130,
        meta: { className: "text-left" },
        cell: (info) => formatMoney(info.getValue() as number | null | undefined),
      },
      {
        accessorKey: "percentComplete",
        header: "% Complete",
        size: 110,
        meta: { className: "text-left" },
        cell: (info) => formatPercent(info.getValue() as number | null | undefined),
      },
      {
        accessorKey: "costVariance",
        header: "Cost Variance",
        size: 130,
        meta: { className: "text-left" },
        cell: (info) => {
          const v = info.getValue() as number | null | undefined;
          return <span className={varianceClass(v)}>{formatMoney(v)}</span>;
        },
      },
      {
        accessorKey: "costVariancePercent",
        header: "Var %",
        size: 90,
        meta: { className: "text-left" },
        cell: (info) => {
          const v = info.getValue() as number | null | undefined;
          return <span className={varianceClass(v)}>{formatPercent(v)}</span>;
        },
      },
    ],
    // editingCell/editingValue/editingWbsItemId captured via closures inside the cell
    // renderers — must re-build columns when they change so the inputs re-render.
    // money is included so cells re-format once the currency master resolves.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [editingCell, editingValue, editingWbsItemId, wbsById, wbsOptions, money]
  );

  // Grand-total quantities — Σ across all items. Units are heterogeneous
  // (Km / Nos / m³), so these are plain summed numbers, not a single unit.
  const boqQtyGrandTotal = summary
    ? summary.items.reduce((sum, i) => sum + (i.boqQty ?? 0), 0)
    : 0;
  const qtyExecutedGrandTotal = summary
    ? summary.items.reduce((sum, i) => sum + (i.qtyExecutedToDate ?? 0), 0)
    : 0;

  const grandTotalFooter = summary ? (
<tr className="text-text-primary dark:text-[#F5F2E8] font-semibold">
  <td className="px-4 py-3" colSpan={7}>
        Grand Total
      </td>
      <td className="px-4 py-3 text-left">{formatAmount(boqQtyGrandTotal)}</td>
      <td className="px-4 py-3" />
      <td className="px-4 py-3 text-left">{formatMoney(summary.boqGrandTotal)}</td>
      <td className="px-4 py-3" />
      <td className="px-4 py-3 text-left">{formatMoney(summary.budgetedGrandTotal)}</td>
      <td className="px-4 py-3 text-left">{formatAmount(qtyExecutedGrandTotal)}</td>
      <td className="px-4 py-3" />
      <td className="px-4 py-3 text-left">{formatMoney(summary.actualGrandTotal)}</td>
      <td className="px-4 py-3 text-left">{formatPercent(summary.overallPercentComplete)}</td>
      <td className={cn("px-4 py-3 text-left", varianceClass(summary.grandCostVariance))}>
        {formatMoney(summary.grandCostVariance)}
      </td>
      <td className={cn("px-4 py-3 text-left", varianceClass(summary.grandCostVariancePercent))}>
        {formatPercent(summary.grandCostVariancePercent)}
      </td>
    </tr>
  ) : null;

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

        {overrunCount > 0 && (
          <AlertBanner
            tone="warning"
            message={
              <>
                <strong>{overrunCount}</strong>{" "}
                {overrunCount === 1 ? "item has" : "items have"} overrun their contracted
                quantities.{" "}
                <strong>{money(overrunUnbilled)}</strong>{" "}
                of work executed cannot be billed until a Variation Order is approved.
              </>
            }
            actions={
              <>
                <button
                  type="button"
                  onClick={() => setOverrunOnly((v) => !v)}
                  className="px-3 py-1.5 text-xs rounded border border-warning/40 bg-warning/10 hover:bg-warning/20 text-warning font-medium"
                >
                  {overrunOnly ? "Show all items" : "Show only overrun"}
                </button>
                <button
                  type="button"
                  onClick={() => router.push(`/projects/${projectId}/variation-orders`)}
                  className="px-3 py-1.5 text-xs rounded bg-warning text-white font-medium hover:bg-warning/90"
                >
                  Create VO
                </button>
              </>
            }
          />
        )}

        <div className="mb-6 flex flex-wrap items-center gap-3">
          <button
            onClick={() => {
              setShowForm(!showForm);
              setFormError(null);
            }}
            className="px-4 py-2 bg-accent text-accent-foreground rounded-lg hover:bg-accent-hover"
          >
            {showForm ? "Cancel" : "Add BOQ Item"}
          </button>
          <div className="w-72">
            <SearchableSelect
              options={[
                { value: WBS_FILTER_NONE, label: "Unassigned (no WBS)" },
                ...wbsOptions,
              ]}
              value={wbsFilter}
              onChange={setWbsFilter}
              placeholder="Filter by WBS — all items"
            />
          </div>
          {wbsFilter !== "" && (
            <span className="text-xs text-text-muted">
              {items.length} of {overrunScoped.length} items
            </span>
          )}
        </div>

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
                  <option value="">— select a unit —</option>
                  {unitOptionsWithFallback(formData.unit).map((u) => (
                    <option key={u} value={u}>
                      {u}
                      {!(STANDARD_UNITS as readonly string[]).includes(u) ? " (legacy)" : ""}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">WBS Node (optional)</label>
                <SearchableSelect
                  options={wbsOptions}
                  value={formData.wbsNodeId}
                  onChange={(v) => setFormData({ ...formData, wbsNodeId: v })}
                  placeholder="— none —"
                />
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
                className="px-4 py-2 bg-success text-white rounded-lg hover:bg-success/90 disabled:opacity-50"
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

        <VirtualDataTable
          data={items}
          columns={columns}
          getRowId={(row) => row.id}
          searchable
          sortable
          resizable
          maxHeight={640}
          emptyMessage={overrunOnly ? "No overrun items." : "No BOQ items yet. Click “Add BOQ Item” to start."}
          footer={grandTotalFooter}
        />

        {/* Stage 4: split lifecycle dialog (split form / operations view). */}
        <SplitBoqDialog
          open={splitDialogItem !== null}
          onClose={() => setSplitDialogItem(null)}
          projectId={projectId}
          item={splitDialogItem}
        />
      </div>
    </div>
  );
}
