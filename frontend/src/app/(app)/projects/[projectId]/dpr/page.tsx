"use client";

import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  dprApi,
  type CreateDailyProgressReportRequest,
  type DailyProgressReportResponse,
  type UpdateDailyProgressReportRequest,
} from "@/lib/api/dprApi";
import { projectApi } from "@/lib/api/projectApi";
import { activityApi } from "@/lib/api/activityApi";
import { boqApi } from "@/lib/api/boqApi";
import { resourceApi } from "@/lib/api/resourceApi";
import { chainageLabel, parseChainage } from "@/lib/format/chainage";
import { AiInsightsPanel } from "@/components/ai/AiInsightsPanel";
import { TabTip } from "@/components/common/TabTip";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { getErrorMessage } from "@/lib/utils/error";
import { useStickyMeasure } from "@/hooks/useStickyMeasure";

type WeatherOption = "" | "Clear" | "Cloudy" | "Rain" | "Hot" | "Cold";
type UnitOption = "Cum" | "MT" | "Rm" | "Each" | "Sqm";

const SUPERVISOR_OTHER = "__other__";

interface DprForm {
  reportDate: string;
  supervisorResourceId: string;
  supervisorName: string;
  chainageFromRaw: string;
  chainageFromM: number | null;
  chainageToRaw: string;
  chainageToM: number | null;
  activityName: string;
  unit: UnitOption;
  qtyExecuted: number;
  boqItemNo: string;
  weatherCondition: WeatherOption;
  remarks: string;
}

const today = () => new Date().toISOString().split("T")[0];

const initialFormState: DprForm = {
  reportDate: today(),
  supervisorResourceId: "",
  supervisorName: "",
  chainageFromRaw: "",
  chainageFromM: null,
  chainageToRaw: "",
  chainageToM: null,
  activityName: "",
  unit: "Cum",
  qtyExecuted: 0,
  boqItemNo: "",
  weatherCondition: "",
  remarks: "",
};

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
  const activityOptions =
    activitiesData?.data?.content.map((a) => ({ value: a.name, label: a.name })) ?? [];

  const { data: boqData } = useQuery({
    queryKey: ["boq", projectId],
    queryFn: () => boqApi.list(projectId),
    enabled: !!projectId,
  });
  const boqOptions =
    boqData?.data?.items
      ?.slice()
      .sort((a, b) =>
        a.itemNo.localeCompare(b.itemNo, undefined, { numeric: true, sensitivity: "base" })
      )
      .map((i) => ({
        value: i.itemNo,
        label: `${i.itemNo} — ${i.description}${i.unit ? ` (${i.unit})` : ""}`,
      })) ?? [];

  const { data: supervisorData } = useQuery({
    queryKey: ["eligibleSupervisors", projectId],
    queryFn: () => resourceApi.getEligibleSupervisors(projectId),
    enabled: !!projectId,
  });
  const supervisors = supervisorData?.data ?? [];
  const supervisorOptions = [
    ...supervisors.map((s) => ({
      value: s.id,
      label: s.roleName ? `${s.name} (${s.roleName})` : s.name,
    })),
    { value: SUPERVISOR_OTHER, label: "Other (free-text)" },
  ];

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
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formData, setFormData] = useState<DprForm>(initialFormState);
  const [error, setError] = useState<string | null>(null);
  const [chainageFromError, setChainageFromError] = useState<string | null>(null);
  const [chainageToError, setChainageToError] = useState<string | null>(null);
  const { ref: stickyHeaderRef, height: upperH } = useStickyMeasure<HTMLDivElement>();
  const stickyTheadTop = `calc(var(--tab-nav-h, 53px) + ${upperH}px)`;
  const formRef = useRef<HTMLFormElement | null>(null);

  const {
    data: listData,
    isLoading,
    isFetching,
  } = useQuery({
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

  const handleChainageBlur = (which: "from" | "to") => {
    if (which === "from") {
      if (!formData.chainageFromRaw) {
        setFormData((f) => ({ ...f, chainageFromM: null }));
        setChainageFromError(null);
        return;
      }
      const parsed = parseChainage(formData.chainageFromRaw);
      if (parsed === null) {
        setChainageFromError("Invalid chainage (expected km+metres, e.g. 145+000)");
        setFormData((f) => ({ ...f, chainageFromM: null }));
      } else {
        setChainageFromError(null);
        setFormData((f) => ({ ...f, chainageFromM: parsed }));
      }
    } else {
      if (!formData.chainageToRaw) {
        setFormData((f) => ({ ...f, chainageToM: null }));
        setChainageToError(null);
        return;
      }
      const parsed = parseChainage(formData.chainageToRaw);
      if (parsed === null) {
        setChainageToError("Invalid chainage (expected km+metres, e.g. 145+000)");
        setFormData((f) => ({ ...f, chainageToM: null }));
      } else {
        setChainageToError(null);
        setFormData((f) => ({ ...f, chainageToM: parsed }));
      }
    }
  };

  const handleSupervisorChange = (value: string) => {
    if (value === SUPERVISOR_OTHER) {
      setFormData((f) => ({ ...f, supervisorResourceId: SUPERVISOR_OTHER, supervisorName: "" }));
      return;
    }
    const match = supervisors.find((s) => s.id === value);
    setFormData((f) => ({
      ...f,
      supervisorResourceId: value,
      supervisorName: match?.name ?? "",
    }));
  };

  const beginEdit = (row: DailyProgressReportResponse) => {
    setEditingId(row.id);
    const supervisorIdInOptions =
      row.supervisorResourceId && supervisors.some((s) => s.id === row.supervisorResourceId)
        ? row.supervisorResourceId
        : row.supervisorResourceId
          ? row.supervisorResourceId // unknown id — keep verbatim, dropdown shows blank but name preserved
          : SUPERVISOR_OTHER;
    setFormData({
      reportDate: row.reportDate,
      supervisorResourceId: supervisorIdInOptions ?? SUPERVISOR_OTHER,
      supervisorName: row.supervisorName,
      chainageFromRaw: row.chainageFromM != null ? chainageLabel(row.chainageFromM) : "",
      chainageFromM: row.chainageFromM,
      chainageToRaw: row.chainageToM != null ? chainageLabel(row.chainageToM) : "",
      chainageToM: row.chainageToM,
      activityName: row.activityName,
      unit: (row.unit as UnitOption) ?? "Cum",
      qtyExecuted: row.qtyExecuted,
      boqItemNo: row.boqItemNo ?? "",
      weatherCondition: (row.weatherCondition as WeatherOption) ?? "",
      remarks: row.remarks ?? "",
    });
    setShowForm(true);
    setChainageFromError(null);
    setChainageToError(null);
    setError(null);
    // Scroll the form into view — page has a tall AI Insights panel above, so scrolling
    // window-to-top would land on that and miss the form entirely.
    if (typeof window !== "undefined") {
      requestAnimationFrame(() => {
        formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      });
    }
  };

  const cancelForm = () => {
    setEditingId(null);
    setShowForm(false);
    setFormData(initialFormState);
    setChainageFromError(null);
    setChainageToError(null);
    setError(null);
  };

  const handleDelete = async (row: DailyProgressReportResponse) => {
    if (!confirm(`Delete DPR row dated ${row.reportDate} (${row.activityName})? BOQ qty will be rolled back.`)) {
      return;
    }
    try {
      await dprApi.delete(projectId, row.id);
      queryClient.invalidateQueries({ queryKey: ["dpr", projectId, from, to] });
      queryClient.invalidateQueries({ queryKey: ["boq", projectId] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to delete DPR"));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!formData.activityName.trim()) {
      setError("Activity Name is required.");
      return;
    }
    if (!formData.supervisorName.trim()) {
      setError("Supervisor name is required (pick from the list or choose Other and type a name).");
      return;
    }
    if (formData.chainageFromRaw && formData.chainageFromM === null) {
      setError("Chainage From is invalid.");
      return;
    }
    if (formData.chainageToRaw && formData.chainageToM === null) {
      setError("Chainage To is invalid.");
      return;
    }
    if (chainageFromError || chainageToError) {
      setError("Fix chainage errors before saving.");
      return;
    }

    const supervisorResourceId =
      formData.supervisorResourceId && formData.supervisorResourceId !== SUPERVISOR_OTHER
        ? formData.supervisorResourceId
        : null;

    try {
      const payload: CreateDailyProgressReportRequest | UpdateDailyProgressReportRequest = {
        reportDate: formData.reportDate,
        supervisorResourceId,
        supervisorName: formData.supervisorName,
        chainageFromM: formData.chainageFromM ?? undefined,
        chainageToM: formData.chainageToM ?? undefined,
        activityName: formData.activityName,
        unit: formData.unit,
        qtyExecuted: formData.qtyExecuted,
        boqItemNo: formData.boqItemNo || undefined,
        weatherCondition: formData.weatherCondition || undefined,
        remarks: formData.remarks || undefined,
      };

      if (editingId) {
        await dprApi.update(projectId, editingId, payload);
      } else {
        await dprApi.create(projectId, payload);
      }
      cancelForm();
      queryClient.invalidateQueries({ queryKey: ["dpr", projectId, from, to] });
      queryClient.invalidateQueries({ queryKey: ["boq", projectId] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, editingId ? "Failed to update DPR" : "Failed to create DPR"));
    }
  };

  if (isLoading) {
    return <div className="p-6 text-text-muted">Loading DPR...</div>;
  }

  const supervisorPickerValue = formData.supervisorResourceId || "";
  const supervisorIsOther = supervisorPickerValue === SUPERVISOR_OTHER;

  return (
    <div className="p-6">
      <AiInsightsPanel projectId={projectId} endpoint={`/v1/projects/${projectId}/dpr/ai/insights`} />
      <TabTip
        title="Daily Progress Report"
        description="Supervisor-level record of work executed each day by chainage — quantities, activity, weather, and remarks."
      />
      <div className="mb-8">
        <div
          ref={stickyHeaderRef}
          className="sticky top-[var(--tab-nav-h,53px)] z-20 -mx-6 px-6 pt-2 pb-3 bg-ivory border-b border-border"
        >
          <h1 className="text-3xl font-bold mb-4 text-text-primary">Daily Progress Report</h1>

          <form onSubmit={handleFilterSubmit} className="flex flex-wrap items-end gap-4 mb-3">
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">From</label>
              <input
                type="date"
                value={fromInput}
                onChange={(e) => setFromInput(e.target.value)}
                className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1 text-text-secondary">To</label>
              <input
                type="date"
                value={toInput}
                onChange={(e) => setToInput(e.target.value)}
                className="px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
              />
            </div>
            <button
              type="submit"
              className="px-4 py-2 bg-accent text-accent-foreground rounded-lg hover:bg-accent-hover"
            >
              {isFetching ? "Loading..." : "Refresh"}
            </button>
          </form>

          <button
            onClick={() => {
              if (showForm) {
                cancelForm();
              } else {
                setEditingId(null);
                setFormData(initialFormState);
                setShowForm(true);
              }
            }}
            className="px-4 py-2 bg-accent text-accent-foreground rounded-lg hover:bg-accent-hover"
          >
            {showForm ? "Cancel" : "Add DPR"}
          </button>
        </div>

        {error && <div className="text-danger mt-4 mb-4">{error}</div>}

        {showForm && (
          <form ref={formRef} onSubmit={handleSubmit} className="bg-surface/50 p-4 rounded-lg border border-border mt-4 mb-6 shadow-xl">
            <div className="mb-3 text-sm text-text-secondary">
              {editingId ? "Editing DPR row — saving will rebalance any BOQ qty deltas." : "Adding new DPR row."}
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Date</label>
                <input
                  type="date"
                  value={formData.reportDate}
                  onChange={(e) => setFormData({ ...formData, reportDate: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Supervisor</label>
                <SearchableSelect
                  options={supervisorOptions}
                  value={supervisorPickerValue}
                  onChange={handleSupervisorChange}
                  placeholder={
                    supervisorOptions.length === 1
                      ? "No supervisors in roster — pick Other"
                      : "Search supervisor…"
                  }
                  className="w-full"
                />
                {supervisorIsOther && (
                  <input
                    type="text"
                    value={formData.supervisorName}
                    onChange={(e) => setFormData({ ...formData, supervisorName: e.target.value })}
                    placeholder="Type the supervisor's name"
                    className="mt-2 w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                    required
                  />
                )}
                {!supervisorIsOther && formData.supervisorName && (
                  <p className="mt-1 text-xs text-text-muted">{formData.supervisorName}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Chainage From</label>
                <input
                  type="text"
                  placeholder="145+000"
                  value={formData.chainageFromRaw}
                  onChange={(e) => setFormData({ ...formData, chainageFromRaw: e.target.value })}
                  onBlur={() => handleChainageBlur("from")}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
                {chainageFromError ? (
                  <p className="mt-1 text-xs text-danger">{chainageFromError}</p>
                ) : formData.chainageFromM !== null ? (
                  <p className="mt-1 text-xs text-text-muted">Preview: {chainageLabel(formData.chainageFromM)}</p>
                ) : null}
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Chainage To</label>
                <input
                  type="text"
                  placeholder="145+200"
                  value={formData.chainageToRaw}
                  onChange={(e) => setFormData({ ...formData, chainageToRaw: e.target.value })}
                  onBlur={() => handleChainageBlur("to")}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
                {chainageToError ? (
                  <p className="mt-1 text-xs text-danger">{chainageToError}</p>
                ) : formData.chainageToM !== null ? (
                  <p className="mt-1 text-xs text-text-muted">Preview: {chainageLabel(formData.chainageToM)}</p>
                ) : null}
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Activity Name</label>
                <SearchableSelect
                  options={activityOptions}
                  value={formData.activityName}
                  onChange={(value) => setFormData({ ...formData, activityName: value })}
                  placeholder="Search activity..."
                  className="w-full"
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Unit</label>
                <select
                  value={formData.unit}
                  onChange={(e) => setFormData({ ...formData, unit: e.target.value as UnitOption })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                >
                  <option value="Cum">Cum</option>
                  <option value="MT">MT</option>
                  <option value="Rm">Rm</option>
                  <option value="Each">Each</option>
                  <option value="Sqm">Sqm</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Qty Executed</label>
                <input
                  type="number"
                  step="0.001"
                  min="0.001"
                  value={formData.qtyExecuted}
                  onChange={(e) => setFormData({ ...formData, qtyExecuted: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">BOQ Item No.</label>
                <SearchableSelect
                  options={boqOptions}
                  value={formData.boqItemNo}
                  onChange={(value) => setFormData({ ...formData, boqItemNo: value })}
                  placeholder={boqOptions.length ? "Search BOQ item…" : "No BOQ items defined for this project"}
                  disabled={boqOptions.length === 0}
                  className="w-full"
                />
                <p className="mt-1 text-xs text-text-muted">
                  Optional — links the executed qty back to BOQ {"→"} % complete and RA bill.
                </p>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 text-text-secondary">Weather</label>
                <select
                  value={formData.weatherCondition}
                  onChange={(e) => setFormData({ ...formData, weatherCondition: e.target.value as WeatherOption })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                >
                  <option value="">—</option>
                  <option value="Clear">Clear</option>
                  <option value="Cloudy">Cloudy</option>
                  <option value="Rain">Rain</option>
                  <option value="Hot">Hot</option>
                  <option value="Cold">Cold</option>
                </select>
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium mb-1 text-text-secondary">Remarks</label>
                <textarea
                  value={formData.remarks}
                  onChange={(e) => setFormData({ ...formData, remarks: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  rows={3}
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <button type="submit" className="px-4 py-2 bg-green-600 text-text-primary rounded-lg hover:bg-green-600">
                {editingId ? "Save changes" : "Save DPR"}
              </button>
              <button
                type="button"
                onClick={cancelForm}
                className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {/* DPR Table */}
        <div className="mt-4">
          <table className="w-full border-collapse border border-border">
            <thead>
              <tr className="bg-surface">
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-left text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">Date</th>
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-left text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">Supervisor</th>
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-left text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">Chainage From</th>
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-left text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">Chainage To</th>
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-left text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">Activity</th>
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-left text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">BOQ Item</th>
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-right text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">Qty Executed</th>
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-left text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">Unit</th>
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-right text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">Cumulative Qty</th>
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-left text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">Weather</th>
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-left text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">Remarks</th>
                <th style={{ top: stickyTheadTop }} className="sticky z-10 bg-surface border border-border px-4 py-2 text-left text-text-secondary shadow-[inset_0_-1px_0_var(--color-border)]">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="hover:bg-surface-hover/30 text-text-primary">
                  <td className="border border-border px-4 py-2">{row.reportDate}</td>
                  <td className="border border-border px-4 py-2">{row.supervisorName}</td>
                  <td className="border border-border px-4 py-2">{chainageLabel(row.chainageFromM)}</td>
                  <td className="border border-border px-4 py-2">{chainageLabel(row.chainageToM)}</td>
                  <td className="border border-border px-4 py-2">{row.activityName}</td>
                  <td className="border border-border px-4 py-2">{row.boqItemNo || "-"}</td>
                  <td className="border border-border px-4 py-2 text-right">{row.qtyExecuted}</td>
                  <td className="border border-border px-4 py-2">{row.unit}</td>
                  <td className="border border-border px-4 py-2 text-right">
                    {row.cumulativeQty != null ? row.cumulativeQty : "-"}
                  </td>
                  <td className="border border-border px-4 py-2">{row.weatherCondition || "-"}</td>
                  <td className="border border-border px-4 py-2">{row.remarks || "-"}</td>
                  <td className="border border-border px-4 py-2">
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => beginEdit(row)}
                        className="px-2 py-1 text-xs bg-accent text-text-primary rounded hover:bg-accent-hover"
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(row)}
                        className="px-2 py-1 text-xs bg-red-600 text-white rounded hover:bg-red-700"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
