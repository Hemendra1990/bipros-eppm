"use client";

import { CalendarDays, Pencil, Trash2 } from "lucide-react";
import type { QcSession, QcTestItemResponse } from "@/lib/types/qc";
import { cn } from "@/lib/utils/cn";

interface Props {
  sessions: QcSession[];
  onEdit: (session: QcSession) => void;
  onDelete: (session: QcSession) => void;
}

const OUTCOME_CLS: Record<string, string> = {
  PASS: "bg-success/10 text-success ring-1 ring-success/40",
  FAIL: "bg-burgundy/10 text-burgundy ring-1 ring-burgundy/40",
  REPEAT: "bg-bronze-warn/10 text-bronze-warn ring-1 ring-bronze-warn/40",
};

function ItemRow({ item }: { item: QcTestItemResponse }) {
  return (
    <tr className="border-b border-hairline/50 last:border-0 hover:bg-ivory/40">
      <td className="py-1.5 pl-8 pr-3 text-sm text-charcoal">{item.testTypeName}</td>
      <td className="px-3 py-1.5 text-sm text-slate">{item.sampleRefNo ?? "—"}</td>
      <td className="px-3 py-1.5 text-right tabular-nums text-sm font-medium text-charcoal">
        {item.testResult != null ? item.testResult : "—"}
      </td>
      <td className="px-3 py-1.5 text-right tabular-nums text-sm text-slate">
        {item.requiredIrc != null ? item.requiredIrc : "—"}
      </td>
      <td className="px-3 py-1.5 text-center">
        <span
          className={cn(
            "inline-block rounded px-2 py-0.5 text-xs font-semibold",
            OUTCOME_CLS[item.outcome] ?? "bg-hairline text-slate"
          )}
        >
          {item.outcome}
        </span>
      </td>
      <td className="px-3 py-1.5 text-sm text-slate">{item.labInspector ?? "—"}</td>
    </tr>
  );
}

function SessionRow({
  session,
  onEdit,
  onDelete,
}: {
  session: QcSession;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const chainage =
    session.chainageFrom && session.chainageTo
      ? `${session.chainageFrom} – ${session.chainageTo}`
      : session.chainageFrom ?? session.chainageTo ?? "—";

  const passCount = session.items.filter((i) => i.outcome === "PASS").length;
  const failCount = session.items.filter((i) => i.outcome === "FAIL").length;
  const repeatCount = session.items.filter((i) => i.outcome === "REPEAT").length;

  return (
    <>
      {/* Session header row */}
      <tr className="border-b border-hairline bg-parchment/60">
        <td
          colSpan={6}
          className="px-4 py-2"
        >
          <div className="flex flex-wrap items-center gap-3">
            <span className="font-semibold text-charcoal">{session.activityName}</span>
            <span className="inline-flex items-center gap-1 rounded-full border border-gold/40 bg-gold/10 px-2.5 py-0.5 text-xs font-medium text-gold-deep">
              <CalendarDays className="h-3 w-3" />
              {new Date(session.testDate + "T00:00:00").toLocaleDateString("en-GB", {
                day: "2-digit",
                month: "short",
                year: "numeric",
              })}
            </span>
            {chainage !== "—" && (
              <span className="text-xs text-slate">CH: {chainage}</span>
            )}
            <div className="flex gap-1.5 text-xs">
              {passCount > 0 && (
                <span className="rounded-full bg-success/10 px-2 py-0.5 text-success font-medium">
                  {passCount} Pass
                </span>
              )}
              {failCount > 0 && (
                <span className="rounded-full bg-burgundy/10 px-2 py-0.5 text-burgundy font-medium">
                  {failCount} Fail
                </span>
              )}
              {repeatCount > 0 && (
                <span className="rounded-full bg-bronze-warn/10 px-2 py-0.5 text-bronze-warn font-medium">
                  {repeatCount} Repeat
                </span>
              )}
            </div>
          </div>
        </td>
        <td className="px-3 py-2">
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={onEdit}
              className="rounded p-1.5 text-slate hover:bg-ivory hover:text-charcoal"
              aria-label="Edit session"
            >
              <Pencil className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              onClick={onDelete}
              className="rounded p-1.5 text-slate hover:bg-burgundy/10 hover:text-burgundy"
              aria-label="Delete session"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>
        </td>
      </tr>
      {/* Item rows */}
      {session.items.length === 0 ? (
        <tr className="border-b border-hairline/50">
          <td colSpan={7} className="py-2 pl-8 text-xs text-slate italic">
            No test items recorded.
          </td>
        </tr>
      ) : (
        session.items.map((item) => <ItemRow key={item.id} item={item} />)
      )}
    </>
  );
}

export function QcSessionGrid({ sessions, onEdit, onDelete }: Props) {
  if (sessions.length === 0) return null;

  return (
    <div className="overflow-x-auto rounded-lg border border-hairline bg-paper">
      <table className="w-full min-w-[720px] text-sm">
        <thead>
          <tr className="border-b border-hairline bg-charcoal/5 text-xs font-semibold uppercase tracking-wider text-slate">
            <th className="px-4 py-2.5 text-left" style={{ minWidth: 200 }}>Test Type</th>
            <th className="px-3 py-2.5 text-left" style={{ minWidth: 120 }}>Sample Ref</th>
            <th className="px-3 py-2.5 text-right" style={{ minWidth: 110 }}>Result</th>
            <th className="px-3 py-2.5 text-right" style={{ minWidth: 120 }}>Required (IRC)</th>
            <th className="px-3 py-2.5 text-center" style={{ minWidth: 100 }}>Outcome</th>
            <th className="px-3 py-2.5 text-left" style={{ minWidth: 160 }}>Lab / Inspector</th>
            <th className="w-20 px-3 py-2.5" />
          </tr>
        </thead>
        <tbody>
          {sessions.map((s) => (
            <SessionRow
              key={s.id}
              session={s}
              onEdit={() => onEdit(s)}
              onDelete={() => onDelete(s)}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}
