"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogBody,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  boqApi,
  type BoqItemResponse,
  type BoqOperationDto,
  type SplitBoqItemRequest,
} from "@/lib/api/boqApi";
import { activityApi } from "@/lib/api/activityApi";
import { getErrorMessage } from "@/lib/utils/error";
import { cn } from "@/lib/utils/cn";

/**
 * Stage 4 — split a BOQ line into operations (design D1–D5), or manage an existing split.
 * Unsplit lines get the split form (mode, operation rows, live Σweight, measurement radio,
 * legacy-history weight, and the mandatory linked-activity re-pointing list — L1). Split
 * lines get the operations view: weights/targets editable (reweight, audited once frozen),
 * per-operation executed/% and coverage indicators (L9/D12), and unsplit.
 */
interface Props {
  open: boolean;
  onClose: () => void;
  projectId: string;
  item: BoqItemResponse | null;
}

export function SplitBoqDialog({ open, onClose, projectId, item }: Props) {
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-w-4xl">
        {item && (
          <DialogInner key={item.id} projectId={projectId} item={item} onClose={onClose} />
        )}
      </DialogContent>
    </Dialog>
  );
}

interface OpRow {
  opCode: string;
  name: string;
  unit: string;
  targetQty: string;
  weightPct: string;
  isMeasure: boolean;
}

function blankRow(unit: string, measure: boolean, prefillTarget?: number | null): OpRow {
  // Owner rule 2026-08-05: operations normally pass over the full line quantity, so the target
  // pre-fills with boqQty — clear it for a milestone, lower it for a partial-scope step.
  return {
    opCode: "",
    name: "",
    unit,
    targetQty: prefillTarget != null && prefillTarget > 0 ? String(prefillTarget) : "",
    weightPct: "",
    isMeasure: measure,
  };
}

function DialogInner({
  projectId,
  item,
  onClose,
}: {
  projectId: string;
  item: BoqItemResponse;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const isSplit = !!item.splitMode;

  // Every activity linked to this line — the split cannot save until each is re-pointed (L1);
  // in the operations view they drive the coverage indicators (L9/D12).
  const { data: activitiesData } = useQuery({
    queryKey: ["activities", projectId, "all-for-parent-picker"],
    queryFn: () => activityApi.listActivities(projectId, 0, 1000),
  });
  const linkedActivities = useMemo(
    () => (activitiesData?.data?.content ?? []).filter((a) => a.boqItemId === item.id),
    [activitiesData, item.id],
  );

  const { data: opsData } = useQuery({
    queryKey: ["boq-operations", projectId, item.id],
    queryFn: () => boqApi.listOperations(projectId, item.id),
    enabled: isSplit,
  });
  const ops: BoqOperationDto[] = opsData?.data ?? [];

  const invalidateAndClose = (message: string) => {
    toast.success(message);
    queryClient.invalidateQueries({ queryKey: ["boq", projectId] });
    queryClient.invalidateQueries({ queryKey: ["boq-operations", projectId, item.id] });
    queryClient.invalidateQueries({ queryKey: ["activities", projectId] });
    onClose();
  };

  return isSplit ? (
    <OperationsView
      projectId={projectId}
      item={item}
      ops={ops}
      linkedActivities={linkedActivities}
      onDone={invalidateAndClose}
      onClose={onClose}
    />
  ) : (
    <SplitForm
      projectId={projectId}
      item={item}
      linkedActivities={linkedActivities}
      onDone={invalidateAndClose}
      onClose={onClose}
    />
  );
}

// ─── Split form (unsplit line) ──────────────────────────────────────────────────────────

function SplitForm({
  projectId,
  item,
  linkedActivities,
  onDone,
  onClose,
}: {
  projectId: string;
  item: BoqItemResponse;
  linkedActivities: Array<{ id: string; code: string; name: string }>;
  onDone: (message: string) => void;
  onClose: () => void;
}) {
  const [mode, setMode] = useState<"WEIGHTED_OPERATIONS" | "QUANTITY_PARTITION">(
    "WEIGHTED_OPERATIONS",
  );
  const [rows, setRows] = useState<OpRow[]>([
    blankRow(item.unit, true, item.boqQty),
    blankRow(item.unit, false, item.boqQty),
  ]);
  const [legacyWeight, setLegacyWeight] = useState("");
  const [assignments, setAssignments] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  const weighted = mode === "WEIGHTED_OPERATIONS";
  const lineQty = item.boqQty ?? 0;
  const hasHistory = (item.qtyExecutedToDate ?? 0) > 0;
  const weightSum =
    rows.reduce((s, r) => s + (Number(r.weightPct) || 0), 0) +
    (weighted && hasHistory ? Number(legacyWeight) || 0 : 0);
  const weightOk = !weighted || Math.abs(weightSum - 100) <= 0.01;
  const measureCount = rows.filter((r) => r.isMeasure).length;
  // The measurement op's target IS the contracted qty (locked when the line has one); on a
  // qty-less line it must still be a positive quantity — it feeds billing (server rules).
  const measureTargetOk =
    !weighted || lineQty > 0 || rows.some((r) => r.isMeasure && Number(r.targetQty) > 0);
  // Partition children divide the line's remaining quantity — every target required, Σ must
  // equal boqQty minus the pre-split history (server rule BOQ_SPLIT_PARTITION_TARGET).
  const openingQty = hasHistory ? (item.qtyExecutedToDate ?? 0) : 0;
  const remainingQty = lineQty - openingQty;
  const targetSum = rows.reduce((s, r) => s + (Number(r.targetQty) || 0), 0);
  const partitionTargetsOk =
    weighted ||
    (rows.every((r) => r.targetQty !== "" && Number(r.targetQty) > 0) &&
      (lineQty <= 0 || Math.abs(targetSum - remainingQty) <= 0.01));
  // Weighted same-unit steps targeting less than the line qty cap at 100% early — legitimate
  // only for genuinely partial-scope steps, so surface them.
  const partialScopeOps = weighted
    ? rows.filter(
        (r) =>
          !r.isMeasure &&
          r.targetQty !== "" &&
          (r.unit || item.unit || "").trim().toLowerCase() ===
            (item.unit ?? "").trim().toLowerCase() &&
          lineQty > 0 &&
          Number(r.targetQty) < lineQty,
      )
    : [];
  const allAssigned = linkedActivities.every((a) => (assignments[a.id] ?? "") !== "");
  const opCodesValid =
    rows.every((r) => r.opCode.trim() !== "") &&
    new Set(rows.map((r) => r.opCode.trim())).size === rows.length;

  const canSave =
    rows.length >= 2 &&
    opCodesValid &&
    allAssigned &&
    (weighted
      ? measureCount === 1 && measureTargetOk && weightOk && (!hasHistory || legacyWeight !== "")
      : partitionTargetsOk);

  const splitMutation = useMutation({
    mutationFn: (request: SplitBoqItemRequest) => boqApi.split(projectId, item.id, request),
    onSuccess: () => onDone(`${item.itemNo} split into ${rows.length} operations`),
    onError: (err: unknown) => setError(getErrorMessage(err, "Split failed")),
  });

  const setRow = (i: number, patch: Partial<OpRow>) =>
    setRows((prev) => prev.map((r, idx) => (idx === i ? { ...r, ...patch } : r)));

  const submit = () => {
    setError(null);
    splitMutation.mutate({
      splitMode: mode,
      legacyWeight: weighted && hasHistory ? Number(legacyWeight) : null,
      operations: rows.map((r, i) => ({
        opCode: r.opCode.trim(),
        name: r.name.trim() || r.opCode.trim(),
        unit: weighted ? r.unit || item.unit : item.unit,
        // The measurement op's target IS the contracted quantity (locked in the UI).
        targetQty:
          weighted && r.isMeasure && lineQty > 0
            ? lineQty
            : r.targetQty === ""
              ? null
              : Number(r.targetQty),
        weightPct: r.weightPct === "" ? null : Number(r.weightPct),
        isMeasure: weighted && r.isMeasure,
        sortOrder: i + 1,
      })),
      activityAssignments: assignments,
    });
  };

  const inputCls =
    "w-full rounded border border-border bg-surface-hover px-2 py-1.5 text-sm text-text-primary focus:border-accent focus:outline-none";

  return (
    <>
      <DialogHeader>
        <DialogTitle>
          Split {item.itemNo} into operations
        </DialogTitle>
      </DialogHeader>
      <DialogBody className="space-y-4">
        <p className="text-xs text-text-muted">
          {item.description} · line unit <strong>{item.unit}</strong>
          {item.boqQty != null && <> · contracted qty <strong>{item.boqQty}</strong></>}
        </p>

        {/* Mode */}
        <div className="flex gap-4 text-sm">
          <label className="flex items-start gap-2">
            <input
              type="radio"
              checked={weighted}
              onChange={() => setMode("WEIGHTED_OPERATIONS")}
              className="mt-0.5"
            />
            <span>
              <strong>Weighted operations</strong>
              <span className="block text-xs text-text-muted">
                Sequential/co-product steps (screen → compact). One measurement operation bills.
              </span>
            </span>
          </label>
          <label className="flex items-start gap-2">
            <input
              type="radio"
              checked={!weighted}
              onChange={() => setMode("QUANTITY_PARTITION")}
              className="mt-0.5"
            />
            <span>
              <strong>Quantity partition</strong>
              <span className="block text-xs text-text-muted">
                Mutually-exclusive methods sharing one qty (blasting vs mechanical). All bill.
              </span>
            </span>
          </label>
        </div>

        {/* Operation rows */}
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-text-muted">
              <th className="pb-1 pr-2">Code</th>
              <th className="pb-1 pr-2">Name</th>
              <th className="pb-1 pr-2 w-20">Unit</th>
              <th className="pb-1 pr-2 w-24">Target qty</th>
              {weighted && <th className="pb-1 pr-2 w-20">Weight %</th>}
              {weighted && <th className="pb-1 pr-2 w-24">Measure</th>}
              <th className="pb-1 w-8" />
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={i} className="align-top">
                <td className="pr-2 py-1">
                  <input
                    value={r.opCode}
                    onChange={(e) => setRow(i, { opCode: e.target.value })}
                    placeholder={`${i + 1}a`}
                    className={inputCls}
                  />
                </td>
                <td className="pr-2 py-1">
                  <input
                    value={r.name}
                    onChange={(e) => setRow(i, { name: e.target.value })}
                    placeholder="e.g. Screening"
                    className={inputCls}
                  />
                </td>
                <td className="pr-2 py-1">
                  <input
                    value={weighted ? r.unit : item.unit}
                    onChange={(e) => setRow(i, { unit: e.target.value })}
                    // Partition children divide the line's own quantity — unit locked (server rule).
                    disabled={!weighted}
                    className={inputCls}
                  />
                </td>
                <td className="pr-2 py-1">
                  <input
                    type="number"
                    step="any"
                    value={weighted && r.isMeasure && lineQty > 0 ? String(lineQty) : r.targetQty}
                    onChange={(e) => setRow(i, { targetQty: e.target.value })}
                    placeholder="blank = milestone"
                    // The measurement op's target is the contracted quantity — locked to boqQty.
                    disabled={weighted && r.isMeasure && lineQty > 0}
                    title={
                      weighted && r.isMeasure && lineQty > 0
                        ? "Measurement target = the line's contracted quantity (moves automatically if the qty is revised)"
                        : undefined
                    }
                    className={inputCls}
                  />
                </td>
                {weighted && (
                  <td className="pr-2 py-1">
                    <input
                      type="number"
                      step="any"
                      value={r.weightPct}
                      onChange={(e) => setRow(i, { weightPct: e.target.value })}
                      className={inputCls}
                    />
                  </td>
                )}
                {weighted && (
                  <td className="pr-2 py-1 pt-2 text-center">
                    <input
                      type="radio"
                      name="measure-op"
                      checked={r.isMeasure}
                      onChange={() =>
                        setRows((prev) => prev.map((row, idx) => ({ ...row, isMeasure: idx === i })))
                      }
                      title="The operation whose executed qty is the line's billable quantity"
                    />
                  </td>
                )}
                <td className="py-1 pt-2">
                  {rows.length > 2 && (
                    <button
                      type="button"
                      onClick={() => setRows((prev) => prev.filter((_, idx) => idx !== i))}
                      className="text-text-muted hover:text-danger"
                      title="Remove operation"
                    >
                      ✕
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between">
          <button
            type="button"
            onClick={() => setRows((prev) => [...prev, blankRow(item.unit, false, item.boqQty)])}
            className="text-sm text-accent hover:underline"
          >
            + Add operation
          </button>
          {weighted ? (
            <span className={cn("text-sm font-medium", weightOk ? "text-success" : "text-danger")}>
              Σ weight = {weightSum.toFixed(2)} {weightOk ? "✓" : "(must be 100)"}
            </span>
          ) : (
            <span
              className={cn(
                "text-sm font-medium",
                partitionTargetsOk ? "text-success" : "text-danger",
              )}
            >
              Σ target = {targetSum.toFixed(2)}{" "}
              {partitionTargetsOk
                ? "✓"
                : lineQty > 0
                  ? openingQty > 0
                    ? `(must be ${lineQty} − ${openingQty} done = ${remainingQty})`
                    : `(must equal the line qty ${lineQty})`
                  : "(every operation needs a positive target)"}
            </span>
          )}
        </div>
        {weighted && measureCount !== 1 && (
          <p className="text-xs text-danger">
            Pick exactly one measurement operation — its executed quantity becomes the line&apos;s
            billable quantity, so it must use the line&apos;s unit ({item.unit}).
          </p>
        )}
        {weighted && measureCount === 1 && !measureTargetOk && (
          <p className="text-xs text-danger">
            The measurement operation needs a positive target quantity — it feeds billing and
            cannot be a milestone.
          </p>
        )}
        {partialScopeOps.length > 0 && (
          <p className="text-xs text-warning">
            {partialScopeOps.map((r) => r.opCode.trim() || "(unnamed)").join(", ")}: target below
            the line qty ({lineQty}) — that step reaches 100% before the full quantity is done.
            Keep it only for a genuinely partial-scope step (e.g. rock trenching where rock
            exists).
          </p>
        )}

        {/* Legacy history weight (§7.3) */}
        {weighted && hasHistory && (
          <div className="rounded border border-warning/40 bg-warning/10 p-3 text-sm">
            <label className="block font-medium">
              Weight of work already done ({item.qtyExecutedToDate} {item.unit} before the split)
            </label>
            <input
              type="number"
              step="any"
              value={legacyWeight}
              onChange={(e) => setLegacyWeight(e.target.value)}
              className={cn(inputCls, "mt-1 w-32")}
            />
            <p className="mt-1 text-xs text-text-muted">
              A &quot;LEGACY&quot; operation absorbs the pre-split history at this share of the
              line&apos;s value. It counts into the Σ weight above.
            </p>
          </div>
        )}

        {/* Linked-activity re-pointing (L1) */}
        {linkedActivities.length > 0 && (
          <div className="rounded border border-border p-3">
            <p className="mb-2 text-sm font-medium">
              Assign each linked activity to an operation (required)
            </p>
            {linkedActivities.map((a) => (
              <div key={a.id} className="mb-1 flex items-center gap-2 text-sm">
                <span className="w-1/2 truncate" title={`${a.code} — ${a.name}`}>
                  {a.code} — {a.name}
                </span>
                <select
                  value={assignments[a.id] ?? ""}
                  onChange={(e) =>
                    setAssignments((prev) => ({ ...prev, [a.id]: e.target.value }))
                  }
                  className={cn(inputCls, "w-1/2")}
                >
                  <option value="">— pick operation —</option>
                  {rows
                    .filter((r) => r.opCode.trim() !== "")
                    .map((r) => (
                      <option key={r.opCode} value={r.opCode.trim()}>
                        {r.opCode.trim()} {r.name ? `— ${r.name}` : ""}
                      </option>
                    ))}
                </select>
              </div>
            ))}
          </div>
        )}

        {error && <p className="text-sm text-danger">{error}</p>}
      </DialogBody>
      <DialogFooter>
        <button
          type="button"
          onClick={onClose}
          className="rounded-lg bg-surface-active/50 px-4 py-2 text-sm text-text-secondary hover:bg-border"
        >
          Cancel
        </button>
        <button
          type="button"
          disabled={!canSave || splitMutation.isPending}
          onClick={submit}
          className="rounded-lg bg-accent px-4 py-2 text-sm text-accent-foreground hover:bg-accent-hover disabled:opacity-50"
        >
          {splitMutation.isPending ? "Splitting…" : "Split line"}
        </button>
      </DialogFooter>
    </>
  );
}

// ─── Operations view (split line) ───────────────────────────────────────────────────────

function OperationsView({
  projectId,
  item,
  ops,
  linkedActivities,
  onDone,
  onClose,
}: {
  projectId: string;
  item: BoqItemResponse;
  ops: BoqOperationDto[];
  linkedActivities: Array<{ id: string; code: string; name: string; boqOperationId?: string | null }>;
  onDone: (message: string) => void;
  onClose: () => void;
}) {
  const [edits, setEdits] = useState<Record<string, { weightPct?: string; targetQty?: string }>>({});
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  const weighted = item.splitMode === "WEIGHTED_OPERATIONS";
  const activitiesByOp = useMemo(() => {
    const m = new Map<string, number>();
    for (const a of linkedActivities) {
      if (a.boqOperationId) m.set(a.boqOperationId, (m.get(a.boqOperationId) ?? 0) + 1);
    }
    return m;
  }, [linkedActivities]);

  const val = (op: BoqOperationDto, field: "weightPct" | "targetQty"): string => {
    const edited = op.id ? edits[op.id]?.[field] : undefined;
    if (edited !== undefined) return edited;
    const raw = op[field];
    return raw === null || raw === undefined ? "" : String(raw);
  };

  const opPct = (op: BoqOperationDto): string => {
    const executed = op.executedQty ?? 0;
    if (op.targetQty === null || op.targetQty === undefined) {
      return executed > 0 ? "100%" : "0%";   // milestone — binary
    }
    if (op.targetQty <= 0) return "0%";
    return `${Math.min((executed / op.targetQty) * 100, 100).toFixed(1)}%`;
  };

  const lineQty = item.boqQty ?? 0;
  const weightSum = ops.reduce((s, op) => {
    const v = op.id ? edits[op.id]?.weightPct : undefined;
    return s + (v !== undefined ? Number(v) || 0 : op.weightPct ?? 0);
  }, 0);
  const weightOk = !weighted || Math.abs(weightSum - 100) <= 0.01;
  // The MEASURE op's target must stay positive — blanking it would make it a milestone
  // (server rejects; block client-side for a friendlier error).
  const measureTargetOk = !weighted || ops.every((op) => {
    if (!op.isMeasure) return true;
    const v = op.id ? edits[op.id]?.targetQty : undefined;
    const target = v !== undefined ? Number(v) : op.targetQty ?? 0;
    return Number.isFinite(target) && target > 0;
  });
  // Owner rule 2026-08-05: the measurement target IS the contracted qty. Splits made before
  // the rule (or after a qty revision) can disagree — Save re-aligns them (self-heal).
  const measureOp = ops.find((op) => op.isMeasure);
  const needsMeasureHeal =
    weighted && lineQty > 0 && !!measureOp && (measureOp.targetQty ?? 0) !== lineQty;
  // Partition twin: every non-legacy target required, Σ = boqQty − pre-split history.
  const legacyTargetSum = ops
    .filter((op) => op.isLegacy)
    .reduce((s, op) => s + (op.targetQty ?? 0), 0);
  const partitionTargetSum = ops
    .filter((op) => !op.isLegacy)
    .reduce((s, op) => {
      const v = op.id ? edits[op.id]?.targetQty : undefined;
      return s + (v !== undefined ? Number(v) || 0 : op.targetQty ?? 0);
    }, 0);
  const partitionRemaining = lineQty - legacyTargetSum;
  const partitionTargetsOk =
    weighted ||
    (ops
      .filter((op) => !op.isLegacy)
      .every((op) => {
        const v = op.id ? edits[op.id]?.targetQty : undefined;
        const t = v !== undefined ? (v === "" ? null : Number(v)) : op.targetQty;
        return t != null && t > 0;
      }) &&
      (lineQty <= 0 || Math.abs(partitionTargetSum - partitionRemaining) <= 0.01));

  const hasEdits = Object.keys(edits).length > 0;

  const reweightMutation = useMutation({
    mutationFn: () =>
      boqApi.reweight(projectId, item.id, {
        splitMode: (item.splitMode ?? "WEIGHTED_OPERATIONS") as "WEIGHTED_OPERATIONS" | "QUANTITY_PARTITION",
        operations: ops
          .filter((op) => op.id && (edits[op.id] || (op.isMeasure && needsMeasureHeal)))
          .map((op) => ({
            opCode: op.opCode,
            weightPct:
              edits[op.id!]?.weightPct !== undefined ? Number(edits[op.id!]!.weightPct) : op.weightPct,
            // Measurement target is pinned to the contracted qty (input locked; heals old splits).
            targetQty:
              weighted && op.isMeasure && lineQty > 0
                ? lineQty
                : edits[op.id!]?.targetQty !== undefined
                  ? edits[op.id!]!.targetQty === ""
                    ? null
                    : Number(edits[op.id!]!.targetQty)
                  : op.targetQty,
          })),
        activityAssignments: {},
        reason: reason || null,
      }),
    onSuccess: () => onDone(`${item.itemNo} operations updated`),
    onError: (err: unknown) => setError(getErrorMessage(err, "Update failed")),
  });

  const unsplitMutation = useMutation({
    mutationFn: () => boqApi.unsplit(projectId, item.id),
    onSuccess: () => onDone(`${item.itemNo} unsplit — back to a flat line`),
    onError: (err: unknown) => setError(getErrorMessage(err, "Unsplit failed")),
  });

  const inputCls =
    "w-full rounded border border-border bg-surface-hover px-2 py-1 text-sm text-text-primary focus:border-accent focus:outline-none";

  return (
    <>
      <DialogHeader>
        <DialogTitle>
          Operations of {item.itemNo}
          <span className="ml-2 align-middle text-xs font-normal text-text-muted">
            {weighted ? "weighted operations" : "quantity partition"}
            {item.earnedFraction != null && ` · earned ${(item.earnedFraction * 100).toFixed(2)}%`}
          </span>
        </DialogTitle>
      </DialogHeader>
      <DialogBody className="space-y-3">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-text-muted">
              <th className="pb-1 pr-2">Code</th>
              <th className="pb-1 pr-2">Name</th>
              {weighted && <th className="pb-1 pr-2 w-24">Weight %</th>}
              <th className="pb-1 pr-2 w-28">Target</th>
              <th className="pb-1 pr-2 w-24">Executed</th>
              <th className="pb-1 pr-2 w-16">Op %</th>
              <th className="pb-1">Coverage</th>
            </tr>
          </thead>
          <tbody>
            {ops.map((op) => {
              const legacy = !!op.isLegacy;
              const covering = op.id ? activitiesByOp.get(op.id) ?? 0 : 0;
              return (
                <tr key={op.id ?? op.opCode} className="align-top border-t border-border/50">
                  <td className="py-1.5 pr-2 font-medium">
                    {op.opCode}
                    {op.isMeasure && (
                      <span className="ml-1 rounded bg-accent/15 px-1 py-0.5 text-[10px] font-semibold text-accent">
                        MEASURE
                      </span>
                    )}
                    {legacy && (
                      <span className="ml-1 rounded bg-surface-active px-1 py-0.5 text-[10px] font-semibold text-text-muted">
                        LEGACY
                      </span>
                    )}
                  </td>
                  <td className="py-1.5 pr-2">{op.name}</td>
                  {weighted && (
                    <td className="py-1 pr-2">
                      <input
                        type="number"
                        step="any"
                        value={val(op, "weightPct")}
                        onChange={(e) =>
                          op.id &&
                          setEdits((prev) => ({
                            ...prev,
                            [op.id!]: { ...prev[op.id!], weightPct: e.target.value },
                          }))
                        }
                        className={inputCls}
                      />
                    </td>
                  )}
                  <td className="py-1 pr-2">
                    <div className="flex items-center gap-1">
                      <input
                        type="number"
                        step="any"
                        value={
                          weighted && op.isMeasure && lineQty > 0
                            ? String(lineQty)
                            : val(op, "targetQty")
                        }
                        onChange={(e) =>
                          op.id &&
                          setEdits((prev) => ({
                            ...prev,
                            [op.id!]: { ...prev[op.id!], targetQty: e.target.value },
                          }))
                        }
                        className={inputCls}
                        // Measurement target = the contracted quantity (moves with qty revisions).
                        disabled={legacy || (weighted && !!op.isMeasure && lineQty > 0)}
                        title={
                          weighted && op.isMeasure && lineQty > 0
                            ? "Measurement target = the line's contracted quantity"
                            : undefined
                        }
                      />
                      <span className="text-xs text-text-muted">{op.unit}</span>
                    </div>
                  </td>
                  <td className="py-1.5 pr-2">{op.executedQty ?? 0}</td>
                  <td className="py-1.5 pr-2">{opPct(op)}</td>
                  <td className="py-1.5 text-xs">
                    {legacy ? (
                      <span className="text-text-muted">pre-split history</span>
                    ) : covering > 0 ? (
                      <span className="text-success">
                        {covering} activit{covering === 1 ? "y" : "ies"}
                      </span>
                    ) : op.isMeasure ? (
                      <span className="text-danger">
                        no activity — billing stays at zero until one covers this operation
                      </span>
                    ) : (
                      <span className="text-warning">no activity yet</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>

        {weighted && (
          <p className={cn("text-sm font-medium", weightOk ? "text-success" : "text-danger")}>
            Σ weight = {weightSum.toFixed(2)} {weightOk ? "✓" : "(must be 100)"}
          </p>
        )}
        {!weighted && (
          <p
            className={cn(
              "text-sm font-medium",
              partitionTargetsOk ? "text-success" : "text-danger",
            )}
          >
            Σ target = {partitionTargetSum.toFixed(2)}{" "}
            {partitionTargetsOk
              ? "✓"
              : lineQty > 0
                ? legacyTargetSum > 0
                  ? `(must be ${lineQty} − ${legacyTargetSum} pre-split = ${partitionRemaining})`
                  : `(must equal the line qty ${lineQty})`
                : "(every operation needs a positive target)"}
          </p>
        )}
        {!measureTargetOk && (
          <p className="text-xs text-danger">
            The measurement operation&apos;s target must stay a positive quantity — it feeds billing.
          </p>
        )}
        {needsMeasureHeal && (
          <p className="text-xs text-warning">
            The measurement operation&apos;s target ({measureOp?.targetQty}) doesn&apos;t match the
            line quantity ({lineQty}) — Save will align it and the line % will recompute.
          </p>
        )}

        {(hasEdits || needsMeasureHeal) && (
          <div>
            <input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Reason for the change (required once DPRs are recorded against the split)"
              className={cn(inputCls, "py-1.5")}
            />
          </div>
        )}

        {error && <p className="text-sm text-danger">{error}</p>}
      </DialogBody>
      <DialogFooter className="justify-between">
        <button
          type="button"
          disabled={unsplitMutation.isPending}
          onClick={() => {
            if (window.confirm(
              `Unsplit ${item.itemNo}? Its operations are removed and the line returns to plain `
                + "quantity tracking. Only possible while no DPRs are attributed to operations.",
            )) {
              unsplitMutation.mutate();
            }
          }}
          className="rounded-lg border border-danger/40 px-4 py-2 text-sm text-danger hover:bg-danger/10 disabled:opacity-50"
        >
          Unsplit
        </button>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg bg-surface-active/50 px-4 py-2 text-sm text-text-secondary hover:bg-border"
          >
            Close
          </button>
          <button
            type="button"
            disabled={
              !(hasEdits || needsMeasureHeal) ||
              !weightOk ||
              !measureTargetOk ||
              !partitionTargetsOk ||
              reweightMutation.isPending
            }
            onClick={() => { setError(null); reweightMutation.mutate(); }}
            className="rounded-lg bg-accent px-4 py-2 text-sm text-accent-foreground hover:bg-accent-hover disabled:opacity-50"
          >
            {reweightMutation.isPending ? "Saving…" : "Save changes"}
          </button>
        </div>
      </DialogFooter>
    </>
  );
}
