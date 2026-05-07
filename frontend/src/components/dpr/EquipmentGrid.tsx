"use client";

import Link from "next/link";
import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { resourceApi } from "@/lib/api/resourceApi";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { CellInput, CellSelect, RowGrid, type RowGridColumn } from "./RowGrid";
import type {
  AssignedResourceOption,
  DprEquipmentRow,
  EquipmentAvailability,
  EquipmentOwnership,
} from "@/lib/types/dpr";
import { equipmentLineCost, fmtMoney, fmtRate } from "./dprFormulas";

const OWNERSHIP: Array<{ value: EquipmentOwnership; label: string }> = [
  { value: "OWNED", label: "Owned" },
  { value: "HIRED", label: "Hired" },
  { value: "SUBCONTRACTOR", label: "Subcontractor" },
];

const AVAILABILITY: Array<{ value: EquipmentAvailability; label: string }> = [
  { value: "AVAILABLE", label: "Available" },
  { value: "UTILIZED", label: "Utilized" },
  { value: "IDLE", label: "Idle" },
  { value: "BREAKDOWN", label: "Breakdown" },
];

const blank = (): DprEquipmentRow => ({
  resourceAssignmentId: "",
  resourceId: null,
  equipmentType: "",
  fleetNo: null,
  ownership: null,
  nos: null,
  workingHours: null,
  idleHours: null,
  breakdownHours: null,
  fuelLitres: null,
  unitRate: null,
  lineCost: null,
  operatorName: null,
  availabilityStatus: null,
  remarks: null,
});

interface Props {
  projectId: string;
  activityId: string | null;
  reportDate: string;
  rows: DprEquipmentRow[];
  onChange: (rows: DprEquipmentRow[]) => void;
}

export function EquipmentGrid({ projectId, activityId, reportDate, rows, onChange }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ["assigned-resources", projectId, activityId, "EQUIPMENT", reportDate],
    queryFn: () =>
      resourceApi.getAssignedResourcesByKind(projectId, activityId!, "EQUIPMENT", reportDate),
    enabled: !!projectId && !!activityId,
  });
  const options = useMemo(() => data?.data ?? [], [data]);
  const optionMap = useMemo(() => {
    const m = new Map<string, AssignedResourceOption>();
    for (const o of options) m.set(o.assignmentId, o);
    return m;
  }, [options]);

  const update = (idx: number, patch: Partial<DprEquipmentRow>) => {
    const next = rows.slice();
    next[idx] = { ...next[idx], ...patch };
    onChange(next);
  };
  const remove = (idx: number) => onChange(rows.filter((_, i) => i !== idx));
  const add = () => onChange([...rows, blank()]);

  const handlePick = (idx: number, assignmentId: string) => {
    const opt = optionMap.get(assignmentId);
    if (!opt) {
      update(idx, { resourceAssignmentId: assignmentId });
      return;
    }
    update(idx, {
      resourceAssignmentId: opt.assignmentId,
      resourceId: opt.resourceId,
      equipmentType: opt.resourceName,
      unitRate: opt.unitRate ?? null,
    });
  };

  const columns: RowGridColumn<DprEquipmentRow>[] = [
    {
      key: "equipmentType",
      label: "Equipment",
      minWidth: 220,
      render: (r, i) => (
        <SearchableSelect
          options={options.map((o) => ({
            value: o.assignmentId,
            label: o.resourceCode ? `${o.resourceName} (${o.resourceCode})` : o.resourceName,
          }))}
          value={r.resourceAssignmentId}
          onChange={(v) => handlePick(i, v)}
          placeholder={isLoading ? "Loading…" : "Pick assigned equipment…"}
          selectedLabel={r.equipmentType || undefined}
          loading={isLoading}
          disabled={!activityId || options.length === 0}
        />
      ),
    },
    {
      key: "fleetNo",
      label: "Fleet #",
      minWidth: 120,
      render: (r, _i, u) => (
        <CellInput
          value={r.fleetNo ?? ""}
          onChange={(v) => u({ fleetNo: v || null })}
          placeholder="Exc-38"
        />
      ),
    },
    {
      key: "ownership",
      label: "Type",
      minWidth: 140,
      render: (r, _i, u) => (
        <CellSelect
          value={r.ownership}
          onChange={(v) => u({ ownership: (v || null) as EquipmentOwnership | null })}
          options={OWNERSHIP}
        />
      ),
    },
    {
      key: "nos",
      label: "Nos",
      minWidth: 90,
      align: "right",
      render: (r, _i, u) => (
        <CellInput
          type="number"
          min="0"
          value={r.nos}
          onChange={(v) => u({ nos: v === "" ? null : Number(v) })}
        />
      ),
    },
    {
      key: "workingHours",
      label: "Hrs",
      minWidth: 95,
      align: "right",
      render: (r, _i, u) => (
        <CellInput
          type="number"
          step="0.25"
          min="0"
          value={r.workingHours}
          onChange={(v) => u({ workingHours: v === "" ? null : Number(v) })}
        />
      ),
    },
    {
      key: "idleHours",
      label: "Idle",
      minWidth: 95,
      align: "right",
      render: (r, _i, u) => (
        <CellInput
          type="number"
          step="0.25"
          min="0"
          value={r.idleHours}
          onChange={(v) => u({ idleHours: v === "" ? null : Number(v) })}
        />
      ),
    },
    {
      key: "breakdownHours",
      label: "B/D",
      minWidth: 95,
      align: "right",
      render: (r, _i, u) => (
        <CellInput
          type="number"
          step="0.25"
          min="0"
          value={r.breakdownHours}
          onChange={(v) => u({ breakdownHours: v === "" ? null : Number(v) })}
        />
      ),
    },
    {
      key: "fuelLitres",
      label: "Fuel (L)",
      minWidth: 105,
      align: "right",
      render: (r, _i, u) => (
        <CellInput
          type="number"
          step="0.1"
          min="0"
          value={r.fuelLitres}
          onChange={(v) => u({ fuelLitres: v === "" ? null : Number(v) })}
        />
      ),
    },
    {
      key: "rate",
      label: "Rate",
      minWidth: 100,
      align: "right",
      render: (r) => <span className="tabular-nums text-slate">{fmtRate(r.unitRate)}</span>,
    },
    {
      key: "cost",
      label: "Cost",
      minWidth: 110,
      align: "right",
      render: (r) => <span className="tabular-nums">{fmtMoney(equipmentLineCost(r, "HOUR"))}</span>,
    },
    {
      key: "availabilityStatus",
      label: "Status",
      minWidth: 140,
      render: (r, _i, u) => (
        <CellSelect
          value={r.availabilityStatus}
          onChange={(v) =>
            u({ availabilityStatus: (v || null) as EquipmentAvailability | null })
          }
          options={AVAILABILITY}
        />
      ),
    },
    {
      key: "operatorName",
      label: "Operator",
      minWidth: 160,
      grow: 1,
      render: (r, _i, u) => (
        <CellInput
          value={r.operatorName ?? ""}
          onChange={(v) => u({ operatorName: v || null })}
          placeholder="Optional"
        />
      ),
    },
  ];

  return (
    <>
      {!activityId && (
        <div className="mb-3 rounded-md border border-hairline bg-ivory/60 px-3 py-2 text-xs text-slate">
          Pick an activity above to choose its assigned equipment.
        </div>
      )}
      {activityId && !isLoading && options.length === 0 && (
        <div className="mb-3 rounded-md border border-hairline bg-ivory/60 px-3 py-2 text-xs text-slate">
          No equipment is assigned to this activity yet.{" "}
          <Link href={`/projects/${projectId}/activities/${activityId}`} className="font-semibold text-gold-deep underline">
            Assign resources
          </Link>{" "}
          first.
        </div>
      )}
      <RowGrid
        title="Equipment / PMV"
        rows={rows}
        columns={columns}
        onAdd={add}
        onChange={update}
        onRemove={remove}
        emptyHint={
          activityId
            ? options.length === 0
              ? "No equipment assigned to this activity."
              : "Click Add equipment to log a fleet number."
            : "Pick an activity first."
        }
        addLabel="Add equipment"
      />
    </>
  );
}
