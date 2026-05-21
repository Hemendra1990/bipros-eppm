"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { subContractorMasterApi } from "@/lib/api/subContractorMasterApi";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { CellInput, RowGrid, type RowGridColumn } from "./RowGrid";
import type { DprSubContractorRow } from "@/lib/types/dpr";

const blank = (): DprSubContractorRow => ({
  subContractorMasterId: null,
  subContractorName: "",
  subContractorCode: "",
  unitsExecuted: null,
  remarks: null,
});

interface Props {
  projectId: string;
  activityId: string | null;
  rows: DprSubContractorRow[];
  onChange: (rows: DprSubContractorRow[]) => void;
}

/**
 * Sub-contractor DPR grid. Supervisor picks from the master list and enters units executed.
 * The master list is project-agnostic — all active sub-contractors are available.
 */
export function SubContractorGrid({ activityId, rows, onChange }: Props) {
  const { data: mastersResp, isLoading } = useQuery({
    queryKey: ["sub-contractor-masters"],
    queryFn: () => subContractorMasterApi.list(),
  });

  const options = useMemo(() => {
    const masters = Array.isArray(mastersResp?.data) ? mastersResp.data : [];
    return masters
      .filter((m) => m.active)
      .map((m) => ({
        value: m.id,
        label: `${m.name} (${m.code})`,
        name: m.name,
        code: m.code,
      }));
  }, [mastersResp]);

  const update = (idx: number, patch: Partial<DprSubContractorRow>) => {
    const next = rows.slice();
    next[idx] = { ...next[idx], ...patch };
    onChange(next);
  };
  const remove = (idx: number) => onChange(rows.filter((_, i) => i !== idx));
  const add = () => onChange([...rows, blank()]);

  const handlePick = (idx: number, masterId: string) => {
    const opt = options.find((o) => o.value === masterId);
    if (!opt) return;
    update(idx, {
      subContractorMasterId: opt.value,
      subContractorName: opt.name,
      subContractorCode: opt.code,
    });
  };

  const columns: RowGridColumn<DprSubContractorRow>[] = [
    {
      key: "subContractor",
      label: "Sub-Contractor",
      minWidth: 280,
      grow: 1,
      render: (r, i) => (
        <SearchableSelect
          options={options.map((o) => ({ value: o.value, label: o.label }))}
          value={r.subContractorMasterId ?? ""}
          onChange={(v) => handlePick(i, v)}
          placeholder={isLoading ? "Loading…" : "Pick sub-contractor…"}
          loading={isLoading}
          disabled={!activityId}
        />
      ),
    },
    {
      key: "unitsExecuted",
      label: "Units Executed",
      minWidth: 160,
      align: "right",
      render: (r, _i, u) => (
        <CellInput
          type="number"
          step="0.01"
          min="0"
          value={r.unitsExecuted}
          onChange={(v) => u({ unitsExecuted: v === "" ? null : Number(v) })}
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
          type="text"
          placeholder="Notes…"
          value={r.remarks}
          onChange={(v) => u({ remarks: v || null })}
        />
      ),
    },
  ];

  return (
    <>
      {!activityId && (
        <div className="mb-3 rounded-md border border-hairline bg-ivory/60 px-3 py-2 text-xs text-slate">
          Pick an activity above to choose sub-contractor.
        </div>
      )}
      <RowGrid
        title="Sub-Contractor"
        rows={rows}
        columns={columns}
        onAdd={add}
        onChange={update}
        onRemove={remove}
        emptyHint={
          activityId
            ? "Click Add sub-contractor to record work done by an external party."
            : "Pick an activity first."
        }
        addLabel="Add sub-contractor"
      />
    </>
  );
}
