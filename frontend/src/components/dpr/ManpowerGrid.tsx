"use client";

import Link from "next/link";
import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { resourceApi } from "@/lib/api/resourceApi";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { CellInput, CellSelect, RowGrid, type RowGridColumn } from "./RowGrid";
import type {
  AssignedResourceOption,
  DprManpowerRow,
  ManpowerCategory,
  RateBasis,
} from "@/lib/types/dpr";
import { fmtMoney, fmtRate, manpowerLineCost, rateBasisSuffix } from "./dprFormulas";

const CATEGORIES: Array<{ value: ManpowerCategory; label: string }> = [
  { value: "SKILLED", label: "Skilled" },
  { value: "SEMI_SKILLED", label: "Semi-skilled" },
  { value: "UNSKILLED", label: "Unskilled" },
];

const blank = (): DprManpowerRow => ({
  resourceAssignmentId: "",
  resourceId: null,
  trade: "",
  category: null,
  nos: null,
  workingHours: null,
  otHours: null,
  unitRate: null,
  unitRateBasis: null,
  lineCost: null,
  contractorName: null,
  remarks: null,
});

interface Props {
  projectId: string;
  activityId: string | null;
  reportDate: string;
  rows: DprManpowerRow[];
  onChange: (rows: DprManpowerRow[]) => void;
}

export function ManpowerGrid({ projectId, activityId, reportDate, rows, onChange }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ["assigned-resources", projectId, activityId, "MANPOWER", reportDate],
    queryFn: () =>
      resourceApi.getAssignedResourcesByKind(projectId, activityId!, "MANPOWER", reportDate),
    enabled: !!projectId && !!activityId,
  });
  const options = useMemo(() => data?.data ?? [], [data]);
  const optionMap = useMemo(() => {
    const m = new Map<string, AssignedResourceOption>();
    for (const o of options) m.set(o.assignmentId, o);
    return m;
  }, [options]);

  const update = (idx: number, patch: Partial<DprManpowerRow>) => {
    const next = rows.slice();
    next[idx] = { ...next[idx], ...patch };
    onChange(next);
  };
  const remove = (idx: number) => onChange(rows.filter((_, i) => i !== idx));
  const add = () => onChange([...rows, blank()]);

  const handleResourcePick = (idx: number, assignmentId: string) => {
    const opt = optionMap.get(assignmentId);
    if (!opt) {
      update(idx, { resourceAssignmentId: assignmentId });
      return;
    }
    update(idx, {
      resourceAssignmentId: opt.assignmentId,
      resourceId: opt.resourceId,
      trade: opt.resourceName,
      unitRate: opt.unitRate ?? null,
      unitRateBasis: (opt.unitRateBasis as RateBasis | null) ?? null,
    });
  };

  const columns: RowGridColumn<DprManpowerRow>[] = [
    {
      key: "trade",
      label: "Trade",
      minWidth: 240,
      render: (r, i) => (
        <SearchableSelect
          options={options.map((o) => ({
            value: o.assignmentId,
            label: o.resourceCode ? `${o.resourceName} (${o.resourceCode})` : o.resourceName,
          }))}
          value={r.resourceAssignmentId}
          onChange={(v) => handleResourcePick(i, v)}
          placeholder={isLoading ? "Loading…" : "Pick assigned trade…"}
          selectedLabel={r.trade || undefined}
          loading={isLoading}
          disabled={!activityId || options.length === 0}
        />
      ),
    },
    {
      key: "category",
      label: "Category",
      minWidth: 140,
      render: (r, _i, u) => (
        <CellSelect
          value={r.category}
          onChange={(v) => u({ category: (v || null) as ManpowerCategory | null })}
          options={CATEGORIES}
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
      label: "Hours",
      minWidth: 100,
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
      key: "otHours",
      label: "OT",
      minWidth: 90,
      align: "right",
      render: (r, _i, u) => (
        <CellInput
          type="number"
          step="0.25"
          min="0"
          value={r.otHours}
          onChange={(v) => u({ otHours: v === "" ? null : Number(v) })}
        />
      ),
    },
    {
      key: "rate",
      label: "Rate",
      minWidth: 110,
      align: "right",
      render: (r) => {
        const suffix = rateBasisSuffix(r.unitRateBasis);
        return (
          <span className="tabular-nums text-slate" title={r.unitRateBasis ? `per ${r.unitRateBasis.toLowerCase()}` : undefined}>
            {fmtRate(r.unitRate)}
            {suffix && <span className="ml-1 text-xs">{suffix}</span>}
          </span>
        );
      },
    },
    {
      key: "cost",
      label: "Cost",
      minWidth: 110,
      align: "right",
      render: (r) => <span className="tabular-nums">{fmtMoney(manpowerLineCost(r, r.unitRateBasis))}</span>,
    },
    {
      key: "remarks",
      label: "Remarks",
      minWidth: 220,
      grow: 1,
      render: (r, _i, u) => (
        <CellInput
          value={r.remarks ?? ""}
          onChange={(v) => u({ remarks: v || null })}
        />
      ),
    },
  ];

  return (
    <>
      {!activityId && (
        <div className="mb-3 rounded-md border border-hairline bg-ivory/60 px-3 py-2 text-xs text-slate">
          Pick an activity above to choose its assigned manpower.
        </div>
      )}
      {activityId && !isLoading && options.length === 0 && (
        <div className="mb-3 rounded-md border border-hairline bg-ivory/60 px-3 py-2 text-xs text-slate">
          No manpower is assigned to this activity yet.{" "}
          <Link href={`/projects/${projectId}/activities/${activityId}`} className="font-semibold text-gold-deep underline">
            Assign resources
          </Link>{" "}
          to enable manpower entry.
        </div>
      )}
      <RowGrid
        title="Manpower"
        rows={rows}
        columns={columns}
        onAdd={add}
        onChange={update}
        onRemove={remove}
        emptyHint={
          activityId
            ? options.length === 0
              ? "No manpower assigned to this activity."
              : "Click Add manpower to record a deployed trade."
            : "Pick an activity first."
        }
        addLabel="Add manpower"
      />
    </>
  );
}
