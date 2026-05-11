"use client";

import { useState } from "react";
import {
  Briefcase,
  ChevronDown,
  ChevronRight,
  HardHat,
  Image as ImageIcon,
  MapPin,
  Package,
  Pencil,
  Trash2,
} from "lucide-react";
import { Badge, type BadgeVariant } from "@/components/ui/badge";
import { chainageLabel } from "@/lib/format/chainage";
import type { DailyProgressReportResponse, DprApprovalStatus } from "@/lib/types/dpr";

interface Props {
  row: DailyProgressReportResponse;
  onEdit: () => void;
  onDelete: () => void;
}

const STATUS_VARIANT: Record<DprApprovalStatus, BadgeVariant> = {
  DRAFT: "neutral",
  SUBMITTED: "info",
  APPROVED: "success",
  REJECTED: "danger",
};

const SIDE_LABEL: Record<string, string> = {
  LHS: "LHS",
  RHS: "RHS",
  CENTER: "Center",
};

const fmt = (n: number | null | undefined, digits = 2) =>
  typeof n === "number" && isFinite(n)
    ? n.toLocaleString(undefined, { maximumFractionDigits: digits })
    : "—";

export function DprActivityCard({ row, onEdit, onDelete }: Props) {
  const [open, setOpen] = useState(false);
  const status = row.approvalStatus ?? "DRAFT";
  const manpowerCount = (row.manpower ?? []).reduce((a, m) => a + (m.nos ?? 0), 0);
  const equipmentCount = (row.equipment ?? []).reduce((a, e) => a + (e.nos ?? 0), 0);
  const materialCount = (row.materials ?? []).length;
  const photoCount = (row.attachments ?? []).length;
  const hasAnyChip =
    row.qtyExecuted != null ||
    manpowerCount > 0 ||
    equipmentCount > 0 ||
    materialCount > 0 ||
    photoCount > 0;

  return (
    <div className="rounded-lg border border-hairline bg-paper">
      <div className="flex flex-wrap items-center gap-3 px-4 py-3">
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          className="flex flex-1 items-center gap-3 text-left"
        >
          <span className="flex-none text-slate">
            {open ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="truncate font-semibold text-charcoal">{row.activityName}</span>
              <Badge variant={STATUS_VARIANT[status]} withDot>
                {status}
              </Badge>
              {row.side && <Badge variant="neutral">{SIDE_LABEL[row.side] ?? row.side}</Badge>}
              {row.boqItemNo && <Badge variant="gold">BOQ {row.boqItemNo}</Badge>}
              {(row.chainageFromM != null || row.chainageToM != null) && (
                <span className="inline-flex items-center gap-1 text-xs text-slate">
                  <MapPin className="h-3 w-3" />
                  {chainageLabel(row.chainageFromM)} → {chainageLabel(row.chainageToM)}
                </span>
              )}
            </div>
            {hasAnyChip && (
              <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                {row.qtyExecuted != null && (
                  <span className="inline-flex items-center gap-1 rounded-full border border-hairline bg-parchment px-2 py-0.5 text-xs font-semibold text-charcoal tabular-nums">
                    {fmt(row.qtyExecuted)} {row.unit}
                  </span>
                )}
                {manpowerCount > 0 && (
                  <span className="inline-flex items-center gap-1 rounded-full border border-hairline bg-ivory px-2 py-0.5 text-xs text-slate">
                    <HardHat className="h-3 w-3" /> {manpowerCount}
                  </span>
                )}
                {equipmentCount > 0 && (
                  <span className="inline-flex items-center gap-1 rounded-full border border-hairline bg-ivory px-2 py-0.5 text-xs text-slate">
                    <Briefcase className="h-3 w-3" /> {equipmentCount}
                  </span>
                )}
                {materialCount > 0 && (
                  <span className="inline-flex items-center gap-1 rounded-full border border-hairline bg-ivory px-2 py-0.5 text-xs text-slate">
                    <Package className="h-3 w-3" /> {materialCount}
                  </span>
                )}
                {photoCount > 0 && (
                  <span className="inline-flex items-center gap-1 rounded-full border border-hairline bg-ivory px-2 py-0.5 text-xs text-slate">
                    <ImageIcon className="h-3 w-3" /> {photoCount}
                  </span>
                )}
              </div>
            )}
          </div>
        </button>
        <div className="flex flex-none items-center gap-1">
          <button
            type="button"
            onClick={onEdit}
            className="rounded-md p-1.5 text-slate hover:bg-ivory hover:text-charcoal"
            aria-label="Edit"
          >
            <Pencil className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={onDelete}
            className="rounded-md p-1.5 text-slate hover:bg-burgundy/10 hover:text-burgundy"
            aria-label="Delete"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </div>
      {open && (
        <div className="space-y-3 border-t border-hairline px-4 py-3">
          {row.cumulativeQty != null && (
            <div className="text-xs text-slate">
              <span className="font-semibold text-charcoal">Cumulative:</span>{" "}
              <span className="tabular-nums">{fmt(row.cumulativeQty)} {row.unit}</span>
            </div>
          )}
          {row.landmark && (
            <div className="text-xs text-slate">
              <span className="font-semibold text-charcoal">Landmark:</span> {row.landmark}
            </div>
          )}
          {row.remarks && (
            <div className="rounded-md bg-ivory/60 p-2 text-xs text-charcoal">
              <span className="font-semibold">Remarks: </span>
              {row.remarks}
            </div>
          )}

          <DetailTable
            title="Manpower"
            empty="No manpower"
            headers={["Trade", "Cat", "Nos", "Hours", "OT", "Contractor"]}
            rows={(row.manpower ?? []).map((m) => [
              m.trade,
              m.category ?? "—",
              fmt(m.nos, 0),
              fmt(m.workingHours),
              fmt(m.otHours),
              m.contractorName ?? "—",
            ])}
          />
          <DetailTable
            title="Equipment / PMV"
            empty="No equipment"
            headers={["Type", "Fleet #", "Nos", "Hrs", "Idle", "B/D", "Fuel L", "Status"]}
            rows={(row.equipment ?? []).map((e) => [
              e.equipmentType,
              e.fleetNo ?? "—",
              fmt(e.nos, 0),
              fmt(e.workingHours),
              fmt(e.idleHours),
              fmt(e.breakdownHours),
              fmt(e.fuelLitres),
              e.availabilityStatus ?? "—",
            ])}
          />
          <DetailTable
            title="Material"
            empty="No material"
            headers={["Material", "Qty", "Unit", "Source", "Vendor"]}
            rows={(row.materials ?? []).map((m) => [
              m.materialName,
              fmt(m.quantity, 3),
              m.unit ?? "—",
              m.source ?? "—",
              m.vendorName ?? "—",
            ])}
          />

          {(row.delayReason || row.safetyObservation || row.safetyIncidentType === "INCIDENT" ||
            row.safetyIncidentType === "NEAR_MISS") && (
            <div className="rounded-md border border-burgundy/20 bg-burgundy/5 p-3 text-xs text-charcoal">
              <div className="mb-1 font-semibold text-burgundy">Safety & Delay</div>
              {row.safetyIncidentType && row.safetyIncidentType !== "NONE" && (
                <div>Incident: {row.safetyIncidentType.replace("_", " ")}</div>
              )}
              {row.delayReason && <div>Delay: {row.delayReason}</div>}
              {row.safetyObservation && <div>Observation: {row.safetyObservation}</div>}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function DetailTable({
  title,
  empty,
  headers,
  rows,
}: {
  title: string;
  empty: string;
  headers: string[];
  rows: Array<Array<string | number>>;
}) {
  if (rows.length === 0) {
    return (
      <div className="text-xs text-ash">
        <span className="font-semibold text-slate">{title}:</span> {empty}
      </div>
    );
  }
  return (
    <div>
      <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate">{title}</div>
      <div className="overflow-x-auto rounded-md border border-hairline">
        <table className="w-full text-xs">
          <thead className="bg-ivory/60">
            <tr>
              {headers.map((h) => (
                <th key={h} className="px-2 py-1 text-left font-semibold text-slate">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={i} className="border-t border-hairline">
                {r.map((c, j) => (
                  <td key={j} className="px-2 py-1 text-charcoal">
                    {c}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
