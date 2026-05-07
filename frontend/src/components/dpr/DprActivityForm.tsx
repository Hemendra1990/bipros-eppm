"use client";

import { useState, type FormEvent } from "react";
import { Briefcase, HardHat, Package, Save } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { SearchableSelect, type SelectOption } from "@/components/common/SearchableSelect";
import { chainageLabel, parseChainage } from "@/lib/format/chainage";
import type {
  DailyProgressReportResponse,
  DprApprovalStatus,
  DprBaseFields,
  DprEquipmentRow,
  DprManpowerRow,
  DprMaterialRow,
  Shift,
  Side,
} from "@/lib/types/dpr";
import { ManpowerGrid } from "./ManpowerGrid";
import { EquipmentGrid } from "./EquipmentGrid";
import { MaterialGrid } from "./MaterialGrid";
import { SafetyDelaySection } from "./SafetyDelaySection";
import { DprTotalsBar } from "./DprTotalsBar";

type Tab = "manpower" | "equipment" | "material";

interface FormState extends DprBaseFields {
  chainageFromRaw: string;
  chainageToRaw: string;
}

interface Props {
  projectId: string;
  editing: DailyProgressReportResponse | null;
  defaultDate: string;
  supervisorOptions: SelectOption[];
  /** {@code value=activityId, label=name} so we can hand the id down to the resource picker. */
  activityOptions: SelectOption[];
  /** id → name lookup used when rehydrating an editing DPR (server stores activityId; UI shows name). */
  activityNameById: Map<string, string>;
  /** lowercased name → id, used for legacy DPRs whose server payload only carries activityName. */
  activityIdByName: Map<string, string>;
  boqOptions: SelectOption[];
  onCancel: () => void;
  onSave: (payload: DprBaseFields) => Promise<void>;
}

const todayIso = () => new Date().toISOString().split("T")[0];

const SUPERVISOR_OTHER = "__other__";

const SIDE_OPTS: Array<{ value: Side; label: string }> = [
  { value: "LHS", label: "LHS" },
  { value: "RHS", label: "RHS" },
  { value: "CENTER", label: "Center" },
];
const SHIFT_OPTS: Array<{ value: Shift; label: string }> = [
  { value: "DAY", label: "Day" },
  { value: "NIGHT", label: "Night" },
];
const STATUS_OPTS: Array<{ value: DprApprovalStatus; label: string }> = [
  { value: "DRAFT", label: "Draft" },
  { value: "SUBMITTED", label: "Submitted" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
];
const UNIT_OPTS = ["Cum", "MT", "Sqm", "Rm", "Each", "R/mtr", "Nr"];
const WEATHER_OPTS = ["Clear", "Cloudy", "Rain", "Hot", "Cold", "Windy"];

const initialState = (
  editing: DailyProgressReportResponse | null,
  defaultDate: string
): FormState => {
  if (editing) {
    return {
      reportDate: editing.reportDate,
      supervisorResourceId: editing.supervisorResourceId ?? null,
      supervisorName: editing.supervisorName,
      chainageFromM: editing.chainageFromM,
      chainageToM: editing.chainageToM,
      chainageFromRaw: editing.chainageFromM != null ? chainageLabel(editing.chainageFromM) : "",
      chainageToRaw: editing.chainageToM != null ? chainageLabel(editing.chainageToM) : "",
      activityId: editing.activityId ?? null,
      activityName: editing.activityName,
      wbsNodeId: editing.wbsNodeId,
      boqItemNo: editing.boqItemNo,
      unit: editing.unit,
      qtyExecuted: editing.qtyExecuted,
      weatherCondition: editing.weatherCondition,
      remarks: editing.remarks,
      side: editing.side ?? null,
      landmark: editing.landmark,
      startTime: editing.startTime,
      endTime: editing.endTime,
      shift: editing.shift ?? null,
      approvalStatus: editing.approvalStatus ?? "DRAFT",
      contractorName: editing.contractorName,
      delayReason: editing.delayReason,
      safetyObservation: editing.safetyObservation,
      safetyIncidentType: editing.safetyIncidentType ?? "NONE",
      manpower: editing.manpower ?? [],
      equipment: editing.equipment ?? [],
      materials: editing.materials ?? [],
    };
  }
  return {
    reportDate: defaultDate || todayIso(),
    supervisorResourceId: null,
    supervisorName: "",
    chainageFromM: null,
    chainageToM: null,
    chainageFromRaw: "",
    chainageToRaw: "",
    activityId: null,
    activityName: "",
    wbsNodeId: null,
    boqItemNo: null,
    unit: "Cum",
    qtyExecuted: 0,
    weatherCondition: null,
    remarks: null,
    side: null,
    landmark: null,
    startTime: null,
    endTime: null,
    shift: "DAY",
    approvalStatus: "DRAFT",
    contractorName: null,
    delayReason: null,
    safetyObservation: null,
    safetyIncidentType: "NONE",
    manpower: [],
    equipment: [],
    materials: [],
  };
};

export function DprActivityForm({
  projectId,
  editing,
  defaultDate,
  supervisorOptions,
  activityOptions,
  activityNameById,
  activityIdByName,
  boqOptions,
  onCancel,
  onSave,
}: Props) {
  const [state, setState] = useState<FormState>(() => {
    const s = initialState(editing, defaultDate);
    // Editing path: backend may not yet carry activityId on legacy rows. Resolve from name.
    if (editing && !s.activityId && editing.activityName) {
      const id = activityIdByName.get(editing.activityName.toLowerCase());
      if (id) s.activityId = id;
    }
    return s;
  });
  const [tab, setTab] = useState<Tab>("manpower");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const patch = (delta: Partial<FormState>) => setState((s) => ({ ...s, ...delta }));

  /**
   * Activity dropdown change: when rows already exist for the previous activity, prompt to clear
   * them — they reference assignments scoped to the old activity and would fail server validation
   * after a switch. Cancelling the prompt reverts the picker.
   */
  const handleActivityChange = (newActivityId: string) => {
    const existingRows =
      (state.manpower?.length ?? 0) +
      (state.equipment?.length ?? 0) +
      (state.materials?.length ?? 0);
    if (newActivityId === state.activityId) return;
    if (existingRows > 0 && state.activityId !== null) {
      const ok = window.confirm(
        `Switching the activity will clear all ${existingRows} manpower / equipment / material row(s). Continue?`
      );
      if (!ok) return;
    }
    const name = activityNameById.get(newActivityId) ?? "";
    patch({
      activityId: newActivityId || null,
      activityName: name,
      manpower: [],
      equipment: [],
      materials: [],
    });
  };

  const supervisorPickerValue = state.supervisorResourceId || "";
  const supervisorIsOther = supervisorPickerValue === SUPERVISOR_OTHER;

  const handleSupervisorChange = (value: string) => {
    if (value === SUPERVISOR_OTHER) {
      patch({ supervisorResourceId: SUPERVISOR_OTHER, supervisorName: "" });
      return;
    }
    const match = supervisorOptions.find((s) => s.value === value);
    patch({ supervisorResourceId: value || null, supervisorName: match?.label.split(" (")[0] ?? "" });
  };

  const handleChainageBlur = (which: "from" | "to") => {
    const raw = which === "from" ? state.chainageFromRaw : state.chainageToRaw;
    if (!raw) {
      patch(which === "from" ? { chainageFromM: null } : { chainageToM: null });
      return;
    }
    const parsed = parseChainage(raw);
    if (parsed === null) {
      setError(`Chainage ${which} is invalid (expected km+metres, e.g. 145+000)`);
      return;
    }
    setError(null);
    patch(which === "from" ? { chainageFromM: parsed } : { chainageToM: parsed });
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!state.activityName.trim()) return setError("Activity name is required.");
    if (!state.supervisorName.trim()) return setError("Supervisor name is required.");
    if (!state.unit) return setError("Unit is required.");
    if (!state.qtyExecuted || state.qtyExecuted <= 0) return setError("Executed quantity must be > 0.");

    const supervisorResourceId =
      state.supervisorResourceId && state.supervisorResourceId !== SUPERVISOR_OTHER
        ? state.supervisorResourceId
        : null;

    const payload: DprBaseFields = {
      reportDate: state.reportDate,
      supervisorResourceId,
      supervisorName: state.supervisorName,
      chainageFromM: state.chainageFromM,
      chainageToM: state.chainageToM,
      activityId: state.activityId ?? null,
      activityName: state.activityName,
      wbsNodeId: state.wbsNodeId,
      boqItemNo: state.boqItemNo || null,
      unit: state.unit,
      qtyExecuted: state.qtyExecuted,
      weatherCondition: state.weatherCondition || null,
      remarks: state.remarks || null,
      side: state.side,
      landmark: state.landmark || null,
      startTime: state.startTime || null,
      endTime: state.endTime || null,
      shift: state.shift,
      approvalStatus: state.approvalStatus,
      contractorName: state.contractorName || null,
      delayReason: state.delayReason || null,
      safetyObservation: state.safetyObservation || null,
      safetyIncidentType: state.safetyIncidentType,
      manpower: state.manpower ?? [],
      equipment: state.equipment ?? [],
      materials: state.materials ?? [],
    };

    setSubmitting(true);
    try {
      await onSave(payload);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to save DPR.";
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="flex flex-wrap items-center gap-2 border-b border-hairline px-5 py-3">
        <Badge variant={editing ? "info" : "gold"} withDot>
          {state.approvalStatus ?? "DRAFT"}
        </Badge>
        {state.shift && (
          <Badge variant="neutral">{state.shift === "DAY" ? "Day shift" : "Night shift"}</Badge>
        )}
      </div>

        {/* Header */}
        <div className="grid gap-4 px-5 py-4 md:grid-cols-3">
          <Field label="Date">
            <input
              type="date"
              value={state.reportDate}
              onChange={(e) => patch({ reportDate: e.target.value })}
              className={inputCls}
              required
            />
          </Field>
          <Field label="Shift">
            <select
              value={state.shift ?? ""}
              onChange={(e) => patch({ shift: (e.target.value || null) as Shift | null })}
              className={inputCls}
            >
              <option value="">—</option>
              {SHIFT_OPTS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Approval status">
            <select
              value={state.approvalStatus ?? "DRAFT"}
              onChange={(e) =>
                patch({ approvalStatus: e.target.value as DprApprovalStatus })
              }
              className={inputCls}
            >
              {STATUS_OPTS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Supervisor" className="md:col-span-2">
            <SearchableSelect
              options={[...supervisorOptions, { value: SUPERVISOR_OTHER, label: "Other (free-text)" }]}
              value={supervisorPickerValue}
              onChange={handleSupervisorChange}
              placeholder="Search supervisor…"
            />
            {supervisorIsOther && (
              <input
                type="text"
                value={state.supervisorName}
                onChange={(e) => patch({ supervisorName: e.target.value })}
                placeholder="Supervisor name"
                className={`mt-2 ${inputCls}`}
                required
              />
            )}
          </Field>
          <Field label="Contractor">
            <input
              type="text"
              value={state.contractorName ?? ""}
              onChange={(e) => patch({ contractorName: e.target.value || null })}
              placeholder="Lead contractor"
              className={inputCls}
            />
          </Field>
          <Field label="Weather">
            <select
              value={state.weatherCondition ?? ""}
              onChange={(e) => patch({ weatherCondition: e.target.value || null })}
              className={inputCls}
            >
              <option value="">—</option>
              {WEATHER_OPTS.map((w) => (
                <option key={w} value={w}>
                  {w}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Start time">
            <input
              type="time"
              value={state.startTime ?? ""}
              onChange={(e) => patch({ startTime: e.target.value || null })}
              className={inputCls}
            />
          </Field>
          <Field label="End time">
            <input
              type="time"
              value={state.endTime ?? ""}
              onChange={(e) => patch({ endTime: e.target.value || null })}
              className={inputCls}
            />
          </Field>
        </div>

        {/* Activity */}
        <div className="border-t border-hairline px-5 py-4">
          <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-charcoal">
            <Briefcase className="h-4 w-4 text-gold-deep" />
            Activity
          </div>
          <div className="grid gap-4 md:grid-cols-3">
            <Field label="Activity name" className="md:col-span-2">
              <SearchableSelect
                options={activityOptions}
                value={state.activityId ?? ""}
                onChange={handleActivityChange}
                placeholder="Search activity…"
                selectedLabel={state.activityName || undefined}
              />
            </Field>
            <Field label="BOQ item">
              <SearchableSelect
                options={boqOptions}
                value={state.boqItemNo ?? ""}
                onChange={(v) => patch({ boqItemNo: v || null })}
                placeholder={
                  boqOptions.length ? "Optional — link to BOQ" : "No BOQ items defined"
                }
                disabled={boqOptions.length === 0}
              />
            </Field>
            <Field label="Chainage from">
              <input
                type="text"
                value={state.chainageFromRaw}
                onChange={(e) => patch({ chainageFromRaw: e.target.value })}
                onBlur={() => handleChainageBlur("from")}
                placeholder="145+000"
                className={inputCls}
              />
            </Field>
            <Field label="Chainage to">
              <input
                type="text"
                value={state.chainageToRaw}
                onChange={(e) => patch({ chainageToRaw: e.target.value })}
                onBlur={() => handleChainageBlur("to")}
                placeholder="145+200"
                className={inputCls}
              />
            </Field>
            <Field label="Side">
              <select
                value={state.side ?? ""}
                onChange={(e) => patch({ side: (e.target.value || null) as Side | null })}
                className={inputCls}
              >
                <option value="">—</option>
                {SIDE_OPTS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Landmark" className="md:col-span-3">
              <input
                type="text"
                value={state.landmark ?? ""}
                onChange={(e) => patch({ landmark: e.target.value || null })}
                placeholder="Near Main Road junction"
                className={inputCls}
              />
            </Field>
            <Field label="Executed qty">
              <input
                type="number"
                step="0.001"
                min="0"
                value={state.qtyExecuted}
                onChange={(e) => patch({ qtyExecuted: parseFloat(e.target.value) || 0 })}
                className={inputCls}
                required
              />
            </Field>
            <Field label="Unit">
              <select
                value={state.unit}
                onChange={(e) => patch({ unit: e.target.value })}
                className={inputCls}
                required
              >
                {UNIT_OPTS.map((u) => (
                  <option key={u} value={u}>
                    {u}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Remarks" className="md:col-span-3">
              <textarea
                value={state.remarks ?? ""}
                onChange={(e) => patch({ remarks: e.target.value || null })}
                rows={2}
                className={inputCls}
              />
            </Field>
          </div>
        </div>

        {/* Tabs */}
        <div className="border-t border-hairline">
          <div className="flex gap-1 border-b border-hairline px-5 pt-3">
            <TabButton active={tab === "manpower"} onClick={() => setTab("manpower")}>
              <HardHat className="h-4 w-4" /> Manpower ({state.manpower?.length ?? 0})
            </TabButton>
            <TabButton active={tab === "equipment"} onClick={() => setTab("equipment")}>
              <Briefcase className="h-4 w-4" /> Equipment ({state.equipment?.length ?? 0})
            </TabButton>
            <TabButton active={tab === "material"} onClick={() => setTab("material")}>
              <Package className="h-4 w-4" /> Material ({state.materials?.length ?? 0})
            </TabButton>
          </div>
          <div className="px-5 py-4">
            {tab === "manpower" && (
              <ManpowerGrid
                projectId={projectId}
                activityId={state.activityId ?? null}
                reportDate={state.reportDate}
                rows={state.manpower ?? []}
                onChange={(rows: DprManpowerRow[]) => patch({ manpower: rows })}
              />
            )}
            {tab === "equipment" && (
              <EquipmentGrid
                projectId={projectId}
                activityId={state.activityId ?? null}
                reportDate={state.reportDate}
                rows={state.equipment ?? []}
                onChange={(rows: DprEquipmentRow[]) => patch({ equipment: rows })}
              />
            )}
            {tab === "material" && (
              <MaterialGrid
                projectId={projectId}
                activityId={state.activityId ?? null}
                reportDate={state.reportDate}
                rows={state.materials ?? []}
                onChange={(rows: DprMaterialRow[]) => patch({ materials: rows })}
              />
            )}
          </div>
        </div>

        {/* Safety & Delay */}
        <div className="border-t border-hairline px-5 py-4">
          <SafetyDelaySection
            delayReason={state.delayReason}
            safetyObservation={state.safetyObservation}
            safetyIncidentType={state.safetyIncidentType}
            onChange={patch}
          />
        </div>

      {/* Sticky footer: totals + save */}
      <div className="sticky bottom-0 z-10 -mx-1 rounded-lg border border-hairline bg-paper/95 p-3 shadow-[0_-4px_20px_rgba(28,28,28,0.04)] backdrop-blur">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex-1 min-w-[300px]">
            <DprTotalsBar
              manpower={state.manpower ?? []}
              equipment={state.equipment ?? []}
              materials={state.materials ?? []}
              qtyExecuted={state.qtyExecuted}
              unit={state.unit}
            />
          </div>
          <div className="flex items-center gap-2">
            {error && <span className="text-sm text-burgundy">{error}</span>}
            <button
              type="button"
              onClick={onCancel}
              className="rounded-md border border-hairline bg-paper px-4 py-2 text-sm font-semibold text-charcoal hover:bg-ivory"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="inline-flex items-center gap-1.5 rounded-md bg-gold px-4 py-2 text-sm font-semibold text-gold-ink hover:bg-gold-deep disabled:opacity-60 disabled:cursor-not-allowed"
            >
              <Save className="h-4 w-4" />
              {submitting
                ? "Saving…"
                : editing
                  ? "Save changes"
                  : "Save DPR"}
            </button>
          </div>
        </div>
      </div>
    </form>
  );
}

const inputCls =
  "w-full rounded-md border border-hairline bg-paper px-3 py-2 text-sm text-charcoal focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold/40";

function Field({
  label,
  className,
  children,
}: {
  label: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={className}>
      <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate">
        {label}
      </label>
      {children}
    </div>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`-mb-px inline-flex items-center gap-1.5 border-b-2 px-3 py-2 text-sm font-semibold transition ${
        active
          ? "border-gold text-charcoal"
          : "border-transparent text-slate hover:text-charcoal"
      }`}
    >
      {children}
    </button>
  );
}
