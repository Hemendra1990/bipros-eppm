"use client";

import Link from "next/link";
import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { resourceApi } from "@/lib/api/resourceApi";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { CellInput, RowGrid, type RowGridColumn } from "./RowGrid";
import type { AssignedResourceOption, DprMaterialRow } from "@/lib/types/dpr";
import { fmtMoney, fmtRate, materialLineCost } from "./dprFormulas";

const blank = (): DprMaterialRow => ({
  resourceAssignmentId: "",
  resourceId: null,
  materialId: null,
  materialName: "",
  quantity: null,
  unit: null,
  source: null,
  batchNo: null,
  vendorName: null,
  unitRate: null,
  lineCost: null,
  remarks: null,
});

interface Props {
  projectId: string;
  activityId: string | null;
  reportDate: string;
  rows: DprMaterialRow[];
  onChange: (rows: DprMaterialRow[]) => void;
}

export function MaterialGrid({ projectId, activityId, reportDate, rows, onChange }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ["assigned-resources", projectId, activityId, "MATERIAL", reportDate],
    queryFn: () =>
      resourceApi.getAssignedResourcesByKind(projectId, activityId!, "MATERIAL", reportDate),
    enabled: !!projectId && !!activityId,
  });
  const options = useMemo(() => data?.data ?? [], [data]);
  const optionMap = useMemo(() => {
    const m = new Map<string, AssignedResourceOption>();
    for (const o of options) m.set(o.assignmentId, o);
    return m;
  }, [options]);

  const update = (idx: number, patch: Partial<DprMaterialRow>) => {
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
      materialName: opt.resourceName,
      unit: opt.unit ?? null,
      unitRate: opt.unitRate ?? null,
    });
  };

  const columns: RowGridColumn<DprMaterialRow>[] = [
    {
      key: "materialName",
      label: "Material",
      minWidth: 240,
      render: (r, i) => (
        <SearchableSelect
          options={options.map((o) => ({
            value: o.assignmentId,
            label: o.resourceCode ? `${o.resourceName} (${o.resourceCode})` : o.resourceName,
          }))}
          value={r.resourceAssignmentId}
          onChange={(v) => handlePick(i, v)}
          placeholder={isLoading ? "Loading…" : "Pick assigned material…"}
          selectedLabel={r.materialName || undefined}
          loading={isLoading}
          disabled={!activityId || options.length === 0}
        />
      ),
    },
    {
      key: "quantity",
      label: "Qty",
      minWidth: 110,
      align: "right",
      render: (r, _i, u) => (
        <CellInput
          type="number"
          step="0.001"
          min="0"
          value={r.quantity}
          onChange={(v) => u({ quantity: v === "" ? null : Number(v) })}
        />
      ),
    },
    /* {
      key: "unit",
      label: "Unit",
      minWidth: 80,
      render: (r) => <span className="text-slate">{r.unit ?? "—"}</span>,
    }, */
    {
      key: "rate",
      label: "Rate",
      minWidth: 110,
      align: "right",
      render: (r) => <span className="tabular-nums text-slate">{fmtRate(r.unitRate)}</span>,
    },
    /* {
      key: "cost",
      label: "Cost",
      minWidth: 110,
      align: "right",
      render: (r) => <span className="tabular-nums">{fmtMoney(materialLineCost(r))}</span>,
    },
    {
      key: "source",
      label: "Source",
      minWidth: 150,
      render: (r, _i, u) => (
        <CellInput
          value={r.source ?? ""}
          onChange={(v) => u({ source: v || null })}
          placeholder="Quarry / Yard"
        />
      ),
    },
    {
      key: "vendorName",
      label: "Vendor",
      minWidth: 150,
      render: (r, _i, u) => (
        <CellInput
          value={r.vendorName ?? ""}
          onChange={(v) => u({ vendorName: v || null })}
        />
      ),
    },
    {
      key: "batchNo",
      label: "Batch #",
      minWidth: 120,
      render: (r, _i, u) => (
        <CellInput
          value={r.batchNo ?? ""}
          onChange={(v) => u({ batchNo: v || null })}
        />
      ),
    },
    {
      key: "remarks",
      label: "Remarks",
      minWidth: 200,
      grow: 1,
      render: (r, _i, u) => (
        <CellInput
          value={r.remarks ?? ""}
          onChange={(v) => u({ remarks: v || null })}
        />
      ),
    }, */
  ];

  return (
    <>
      {!activityId && (
        <div className="mb-3 rounded-md border border-hairline bg-ivory/60 px-3 py-2 text-xs text-slate">
          Pick an activity above to choose its assigned material.
        </div>
      )}
      {activityId && !isLoading && options.length === 0 && (
        <div className="mb-3 rounded-md border border-hairline bg-ivory/60 px-3 py-2 text-xs text-slate">
          No material is assigned to this activity yet.{" "}
          <Link href={`/projects/${projectId}/activities/${activityId}`} className="font-semibold text-gold-deep underline">
            Assign resources
          </Link>{" "}
          first.
        </div>
      )}
      <RowGrid
        title="Material"
        rows={rows}
        columns={columns}
        onAdd={add}
        onChange={update}
        onRemove={remove}
        emptyHint={
          activityId
            ? options.length === 0
              ? "No material assigned to this activity."
              : "Click Add material to log consumption."
            : "Pick an activity first."
        }
        addLabel="Add material"
      />
    </>
  );
}
