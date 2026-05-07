"use client";

import { useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { dprApi } from "@/lib/api/dprApi";
import type {
  DailyProgressReportResponse,
  DprBaseFields,
} from "@/lib/types/dpr";
import { projectApi } from "@/lib/api/projectApi";
import { activityApi } from "@/lib/api/activityApi";
import { boqApi } from "@/lib/api/boqApi";
import { resourceApi } from "@/lib/api/resourceApi";
import { AiInsightsPanel } from "@/components/ai/AiInsightsPanel";
import { Drawer } from "@/components/common/Drawer";
import { TabTip } from "@/components/common/TabTip";
import { DprActivityForm } from "@/components/dpr/DprActivityForm";
import type { SelectOption } from "@/components/common/SearchableSelect";
import { DprDayList } from "@/components/dpr/DprDayList";
import { getErrorMessage } from "@/lib/utils/error";
import { useStickyMeasure } from "@/hooks/useStickyMeasure";

const todayIso = () => new Date().toISOString().split("T")[0];

export default function DprPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const queryClient = useQueryClient();

  const { data: projectData } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
    enabled: !!projectId,
  });
  const project = projectData?.data;

  const { data: activitiesData } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 1000),
    enabled: !!projectId,
  });
  // value=id so the form has a deterministic FK to fetch the activity's resource assignments;
  // label still shows the activity name. Build a side index name→id so we can resolve the id
  // for legacy DPRs whose payload only carries activityName.
  const activityIndex = useMemo(() => {
    const opts: SelectOption[] = [];
    const byName = new Map<string, string>();
    const byId = new Map<string, string>();
    // activityId → assigned supervisor (from Activity.responsibleResourceId snapshot). Used by
    // the form to cross-filter the supervisor/activity pickers and auto-fill on activity pick.
    const supervisorByActivityId = new Map<string, { id: string; name: string } | null>();
    const seen = new Set<string>();
    for (const a of activitiesData?.data?.content ?? []) {
      if (seen.has(a.id)) continue;
      seen.add(a.id);
      opts.push({ value: a.id, label: a.name });
      if (!byName.has(a.name.toLowerCase())) byName.set(a.name.toLowerCase(), a.id);
      byId.set(a.id, a.name);
      supervisorByActivityId.set(
        a.id,
        a.responsibleResourceId
          ? { id: a.responsibleResourceId, name: a.responsibleResourceName ?? "" }
          : null
      );
    }
    return { opts, byName, byId, supervisorByActivityId };
  }, [activitiesData]);
  const activityOptions = activityIndex.opts;

  const { data: boqData } = useQuery({
    queryKey: ["boq", projectId],
    queryFn: () => boqApi.list(projectId),
    enabled: !!projectId,
  });
  const boqOptions = useMemo(
    () =>
      boqData?.data?.items
        ?.slice()
        .sort((a, b) =>
          a.itemNo.localeCompare(b.itemNo, undefined, { numeric: true, sensitivity: "base" })
        )
        .map((i) => ({
          value: i.itemNo,
          label: `${i.itemNo} — ${i.description}${i.unit ? ` (${i.unit})` : ""}`,
        })) ?? [],
    [boqData]
  );

  const { data: supervisorData } = useQuery({
    queryKey: ["eligibleSupervisors", projectId],
    queryFn: () => resourceApi.getEligibleSupervisors(projectId),
    enabled: !!projectId,
  });
  const supervisorOptions = useMemo(
    () =>
      (supervisorData?.data ?? []).map((s) => ({
        value: s.id,
        label: s.roleName ? `${s.name} (${s.roleName})` : s.name,
      })),
    [supervisorData]
  );

  const [fromInput, setFromInput] = useState<string>("");
  const [toInput, setToInput] = useState<string>("");
  const [from, setFrom] = useState<string>("");
  const [to, setTo] = useState<string>("");

  useEffect(() => {
    if (!project) return;
    if (from === "" && project.plannedStartDate) {
      setFromInput(project.plannedStartDate);
      setFrom(project.plannedStartDate);
    }
    if (to === "" && project.plannedFinishDate) {
      setToInput(project.plannedFinishDate);
      setTo(project.plannedFinishDate);
    }
  }, [project, from, to]);

  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<DailyProgressReportResponse | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const { ref: stickyHeaderRef } = useStickyMeasure<HTMLDivElement>();

  const { data: listData, isLoading, isFetching } = useQuery({
    queryKey: ["dpr", projectId, from, to],
    queryFn: () => dprApi.list(projectId, { from, to }),
    enabled: !!projectId && !!from && !!to,
  });

  const rows: DailyProgressReportResponse[] = listData?.data ?? [];

  const handleFilterSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFrom(fromInput);
    setTo(toInput);
  };

  const openNew = () => {
    setEditing(null);
    setShowForm(true);
    setPageError(null);
  };

  const openEdit = (row: DailyProgressReportResponse) => {
    setEditing(row);
    setShowForm(true);
    setPageError(null);
  };

  const closeForm = () => {
    setShowForm(false);
    setEditing(null);
  };

  const handleSave = async (payload: DprBaseFields) => {
    if (editing) {
      await dprApi.update(projectId, editing.id, payload);
    } else {
      await dprApi.create(projectId, payload);
    }
    closeForm();
    queryClient.invalidateQueries({ queryKey: ["dpr", projectId, from, to] });
    queryClient.invalidateQueries({ queryKey: ["boq", projectId] });
  };

  const handleDelete = async (row: DailyProgressReportResponse) => {
    if (
      !confirm(
        `Delete DPR for ${row.activityName} on ${row.reportDate}? Linked BOQ qty will be rolled back.`
      )
    ) {
      return;
    }
    try {
      await dprApi.delete(projectId, row.id);
      queryClient.invalidateQueries({ queryKey: ["dpr", projectId, from, to] });
      queryClient.invalidateQueries({ queryKey: ["boq", projectId] });
    } catch (err: unknown) {
      setPageError(getErrorMessage(err, "Failed to delete DPR"));
    }
  };

  if (isLoading) {
    return <div className="p-6 text-slate">Loading DPR…</div>;
  }

  return (
    <div className="p-6">
      <AiInsightsPanel
        projectId={projectId}
        endpoint={`/v1/projects/${projectId}/dpr/ai/insights`}
      />
      <TabTip
        title="Daily Progress Report"
        description="Activity-level record of work executed each day — chainage, executed quantity, deployed manpower / equipment / material, weather, and remarks."
      />
      <div className="mb-6">
        <div
          ref={stickyHeaderRef}
          className="sticky top-[var(--tab-nav-h,53px)] z-20 -mx-6 mb-4 border-b border-hairline bg-paper/95 px-6 pt-2 pb-3 backdrop-blur"
        >
          <div className="flex flex-wrap items-end justify-between gap-3">
            <h1 className="font-display text-3xl font-bold text-charcoal">Daily Progress Report</h1>
            <button
              onClick={openNew}
              className="inline-flex items-center gap-1.5 rounded-md bg-gold px-4 py-2 text-sm font-semibold text-gold-ink hover:bg-gold-deep transition"
            >
              <Plus className="h-4 w-4" /> Add Activity
            </button>
          </div>
          <form onSubmit={handleFilterSubmit} className="mt-3 flex flex-wrap items-end gap-3">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate">
                From
              </label>
              <input
                type="date"
                value={fromInput}
                onChange={(e) => setFromInput(e.target.value)}
                className="rounded-md border border-hairline bg-paper px-3 py-2 text-sm text-charcoal focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold/40"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate">
                To
              </label>
              <input
                type="date"
                value={toInput}
                onChange={(e) => setToInput(e.target.value)}
                className="rounded-md border border-hairline bg-paper px-3 py-2 text-sm text-charcoal focus:border-gold focus:outline-none focus:ring-1 focus:ring-gold/40"
              />
            </div>
            <button
              type="submit"
              className="rounded-md border border-hairline bg-paper px-4 py-2 text-sm font-semibold text-charcoal hover:bg-ivory"
            >
              {isFetching ? "Loading…" : "Refresh"}
            </button>
          </form>
        </div>

        {pageError && <div className="mb-4 text-sm text-burgundy">{pageError}</div>}

        <Drawer
          open={showForm}
          onClose={closeForm}
          title={editing ? "Edit Activity" : "Add Activity"}
          widthClass="max-w-7xl"
        >
          <DprActivityForm
            // Re-mount when switching between edit targets (or new vs. edit) so the form's
            // lazy useState initializer reseeds. Without this, clicking Edit on row B while
            // the form for row A is open would keep A's children visible.
            key={editing?.id ?? "new"}
            projectId={projectId}
            editing={editing}
            defaultDate={editing?.reportDate ?? from ?? todayIso()}
            supervisorOptions={supervisorOptions}
            activityOptions={activityOptions}
            activityNameById={activityIndex.byId}
            activityIdByName={activityIndex.byName}
            supervisorByActivityId={activityIndex.supervisorByActivityId}
            boqOptions={boqOptions}
            onCancel={closeForm}
            onSave={handleSave}
          />
        </Drawer>

        <DprDayList rows={rows} onEdit={openEdit} onDelete={handleDelete} />
      </div>
    </div>
  );
}
