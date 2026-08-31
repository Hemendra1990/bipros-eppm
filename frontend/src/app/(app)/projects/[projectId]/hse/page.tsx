"use client";

import { Fragment, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, ShieldCheck } from "lucide-react";
import { TabTip } from "@/components/common/TabTip";
import { EmptyState } from "@/components/common/EmptyState";
import { Drawer } from "@/components/common/Drawer";
import { fieldInput } from "@/components/dpr/issueFormUi";
import { useAuthStore } from "@/lib/state/store";
import {
  hseApi,
  type HseStatisticsResponse,
  type UpdateProjectHseMetricsRequest,
} from "@/lib/api/hseApi";
import { getErrorMessage } from "@/lib/utils/error";

/** Thousands-separated formatting for HSE counts/exposure figures. These are NOT money — no
 *  currency symbol and no crore/lakh scaling. */
function fmtNum(n: number | null | undefined): string {
  if (n == null || Number.isNaN(n)) return "—";
  return n.toLocaleString("en-US", { maximumFractionDigits: 2 });
}

function fmtDate(iso: string | null | undefined): string {
  return iso ? new Date(iso + "T00:00:00").toLocaleDateString() : "—";
}

interface StatGroup {
  heading: string;
  rows: Array<{ label: string; value: string }>;
}

function buildGroups(s: HseStatisticsResponse): StatGroup[] {
  return [
    {
      heading: "Man-hours & days",
      rows: [
        { label: "Total Man Hours Worked", value: fmtNum(s.manHoursWorked) },
        { label: "Total Man Hours without LTI", value: fmtNum(s.manHoursWithoutLti) },
        { label: "Total Project Days Worked", value: fmtNum(s.projectDaysWorked) },
        { label: "Total Project Days without LTI", value: fmtNum(s.projectDaysWithoutLti) },
      ],
    },
    {
      heading: "Exposure",
      rows: [{ label: "KM distance Driven", value: fmtNum(s.kmDistanceDriven) }],
    },
    {
      heading: "Incidents",
      rows: [
        { label: "Medical Treatment Case (MTC)", value: fmtNum(s.mtcCount) },
        { label: "Property/Asset Damage", value: fmtNum(s.propertyDamageCount) },
        { label: "Near Miss Case (NMC)", value: fmtNum(s.nearMissCount) },
        { label: "Fatality", value: fmtNum(s.fatalityCount) },
      ],
    },
  ];
}

export default function HsePage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const canEdit = useAuthStore((s) => s.hasPermission("DPR.UPDATE"));
  const [editOpen, setEditOpen] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ["hse-statistics", projectId],
    queryFn: () => hseApi.statistics(projectId),
    enabled: !!projectId,
  });
  const stats = data?.data;

  return (
    <div className="p-6">
      <TabTip
        title="HSE"
        description="Health, Safety & Environment statistics for this project — man-hours and days worked (and since the last lost-time injury), vehicle kilometres driven, and classified incident counts. Figures are derived from approved DPRs and safety issues; KM is entered manually."
      />

      <div className="mb-4 flex items-center justify-between gap-4">
        <h1 className="font-display text-3xl font-bold text-charcoal">HSE</h1>
        {canEdit && (
          <button
            type="button"
            onClick={() => setEditOpen(true)}
            className="inline-flex items-center gap-2 rounded-lg border border-border bg-surface px-3 py-2 text-sm font-medium text-text-secondary transition-colors hover:bg-surface-hover"
          >
            <Pencil className="h-4 w-4" />
            Edit HSE inputs
          </button>
        )}
      </div>

      {isLoading ? (
        <div className="text-sm text-text-muted">Loading…</div>
      ) : !stats ? (
        <EmptyState
          icon={ShieldCheck}
          title="No HSE data yet"
          description="Once this project has approved DPRs and classified safety issues, its HSE statistics will appear here."
        />
      ) : (
        <div className="space-y-6">
          <div className="overflow-hidden rounded-lg border border-border">
            <table className="w-full text-sm">
              <tbody>
                {buildGroups(stats).map((group) => (
                  <Fragment key={group.heading}>
                    <tr className="bg-surface-hover text-left text-xs font-semibold uppercase tracking-wide text-text-secondary">
                      <th colSpan={2} className="px-4 py-2">
                        {group.heading}
                      </th>
                    </tr>
                    {group.rows.map((row) => (
                      <tr key={row.label} className="border-t border-border">
                        <td className="px-4 py-2.5 text-text-primary">{row.label}</td>
                        <td className="px-4 py-2.5 text-right font-medium tabular-nums text-text-primary">
                          {row.value}
                        </td>
                      </tr>
                    ))}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>

          {stats.indirectManHours > 0 && (
            <p className="text-xs text-text-muted">
              Man-hours worked = Direct (site DPR) {fmtNum(stats.directManHours)} + Indirect
              (office) {fmtNum(stats.indirectManHours)}.
            </p>
          )}

          <p className="text-xs text-text-muted">
            Last lost-time injury (LTI): {fmtDate(stats.lastLtiDate)} · Calendar hours/day used for
            the man-hour fallback: {fmtNum(stats.calendarHoursPerDay)}
          </p>
        </div>
      )}

      {canEdit && (
        <HseInputsDrawer
          projectId={projectId}
          open={editOpen}
          onClose={() => setEditOpen(false)}
        />
      )}
    </div>
  );
}

function HseInputsDrawer({
  projectId,
  open,
  onClose,
}: {
  projectId: string;
  open: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [km, setKm] = useState("");
  const [indirect, setIndirect] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const { data: metricsData, isLoading: metricsLoading } = useQuery({
    queryKey: ["hse-metrics", projectId],
    queryFn: () => hseApi.getMetrics(projectId),
    enabled: open && !!projectId,
  });

  // Hydrate the local inputs whenever a fresh metrics row arrives.
  const metrics = metricsData?.data;
  useEffect(() => {
    if (!metrics) return;
    setKm(metrics.kmDistanceDriven != null ? String(metrics.kmDistanceDriven) : "");
    setIndirect(metrics.indirectManHours != null ? String(metrics.indirectManHours) : "");
  }, [metrics]);

  const mutation = useMutation({
    mutationFn: () => {
      const body: UpdateProjectHseMetricsRequest = {
        kmDistanceDriven: km.trim() === "" ? 0 : Number(km),
        indirectManHours: indirect.trim() === "" ? 0 : Number(indirect),
      };
      return hseApi.putMetrics(projectId, body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["hse-statistics", projectId] });
      queryClient.invalidateQueries({ queryKey: ["hse-metrics", projectId] });
      onClose();
    },
    onError: (err) => setFormError(getErrorMessage(err)),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    mutation.mutate();
  };

  return (
    <Drawer open={open} onClose={onClose} title="Edit HSE inputs" widthClass="max-w-md">
      <form onSubmit={handleSubmit} className="flex min-h-full flex-col">
        <div className="flex-1 space-y-5 px-5 py-5">
          {formError && (
            <div className="rounded-lg border border-danger/30 bg-danger/10 px-3.5 py-2.5 text-sm text-danger">
              {formError}
            </div>
          )}

          <div>
            <label className="block text-[13px] font-medium text-text-secondary" htmlFor="hse-km">
              KM distance driven
            </label>
            <input
              id="hse-km"
              type="number"
              min={0}
              step="any"
              value={km}
              onChange={(e) => setKm(e.target.value)}
              placeholder="0"
              className={fieldInput}
            />
            <p className="mt-1.5 text-xs text-text-muted">
              Cumulative vehicle-kilometres driven by the project fleet (road-safety exposure).
              Distinct from project chainage.
            </p>
          </div>

          <div>
            <label
              className="block text-[13px] font-medium text-text-secondary"
              htmlFor="hse-indirect"
            >
              Indirect man-hours (office / support staff)
            </label>
            <input
              id="hse-indirect"
              type="number"
              min={0}
              step="any"
              value={indirect}
              onChange={(e) => setIndirect(e.target.value)}
              placeholder="0"
              className={fieldInput}
            />
            <p className="mt-1.5 text-xs text-text-muted">
              Cumulative man-hours for office / support staff (PM, planning, cost, draughting,
              QA/QS) who work off-site and aren&apos;t captured in daily DPRs. Added on top of the
              DPR-derived site man-hours.
            </p>
          </div>
        </div>

        <div className="sticky bottom-0 flex justify-end gap-3 border-t border-border bg-surface/95 px-5 py-4">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-border bg-surface px-4 py-2 text-sm font-medium text-text-secondary transition-colors hover:bg-surface-hover"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={mutation.isPending || metricsLoading}
            className="rounded-lg bg-accent px-5 py-2 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-accent-hover disabled:opacity-50"
          >
            {mutation.isPending ? "Saving…" : "Save"}
          </button>
        </div>
      </form>
    </Drawer>
  );
}
