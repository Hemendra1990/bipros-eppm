"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { dprApi, type CreateDailyProgressReportRequest, type DailyProgressReportResponse } from "@/lib/api/dprApi";
import { projectApi } from "@/lib/api/projectApi";
import { activityApi } from "@/lib/api/activityApi";
import { chainageLabel, parseChainage } from "@/lib/format/chainage";
import { AiInsightsPanel } from "@/components/ai/AiInsightsPanel";
import { TabTip } from "@/components/common/TabTip";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { getErrorMessage } from "@/lib/utils/error";

type WeatherOption = "" | "Clear" | "Cloudy" | "Rain" | "Hot" | "Cold";
type UnitOption = "Cum" | "MT" | "Rm" | "Each" | "Sqm";

interface DprForm {
  reportDate: string;
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
  const [formData, setFormData] = useState<DprForm>(initialFormState);
  const [error, setError] = useState<string | null>(null);
  const [chainageFromError, setChainageFromError] = useState<string | null>(null);
  const [chainageToError, setChainageToError] = useState<string | null>(null);

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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!formData.activityName.trim()) {
      setError("Activity Name is required.");
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

    try {
      const request: CreateDailyProgressReportRequest = {
        reportDate: formData.reportDate,
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

      await dprApi.create(projectId, request);
      setFormData(initialFormState);
      setShowForm(false);
      setChainageFromError(null);
      setChainageToError(null);
      queryClient.invalidateQueries({ queryKey: ["dpr", projectId, from, to] });
    } catch (err: unknown) {
      setError(getErrorMessage(err, "Failed to create DPR"));
    }
  };

  if (isLoading) {
    return <div className="p-6 text-text-muted">Loading DPR...</div>;
  }

  return (
    <div className="p-6">
      <AiInsightsPanel projectId={projectId} endpoint={`/v1/projects/${projectId}/dpr/ai/insights`} />
      <TabTip
        title="Daily Progress Report"
        description="Supervisor-level record of work executed each day by chainage — quantities, activity, weather, and remarks."
      />
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-4 text-text-primary">Daily Progress Report</h1>

        <form onSubmit={handleFilterSubmit} className="flex flex-wrap items-end gap-4 mb-6">
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
            className="px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
          >
            {isFetching ? "Loading..." : "Refresh"}
          </button>
        </form>

        <button
          onClick={() => setShowForm(!showForm)}
          className="mb-6 px-4 py-2 bg-accent text-text-primary rounded-lg hover:bg-accent-hover"
        >
          {showForm ? "Cancel" : "Add DPR"}
        </button>

        {error && <div className="text-danger mb-4">{error}</div>}

        {showForm && (
          <form onSubmit={handleSubmit} className="bg-surface/50 p-4 rounded-lg border border-border mb-6 shadow-xl">
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
                <label className="block text-sm font-medium mb-1 text-text-secondary">Supervisor Name</label>
                <input
                  type="text"
                  value={formData.supervisorName}
                  onChange={(e) => setFormData({ ...formData, supervisorName: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                  required
                />
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
                <input
                  type="text"
                  value={formData.boqItemNo}
                  onChange={(e) => setFormData({ ...formData, boqItemNo: e.target.value })}
                  className="w-full px-3 py-2 border border-border bg-surface-hover text-text-primary rounded-lg"
                />
                <p className="mt-1 text-xs text-text-muted">e.g. 3.1 — auto-syncs to BOQ on save</p>
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
                Save DPR
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowForm(false);
                  setChainageFromError(null);
                  setChainageToError(null);
                }}
                className="px-4 py-2 bg-surface-active/50 text-text-secondary rounded-lg hover:bg-border"
              >
                Cancel
              </button>
            </div>
          </form>
        )}

        {/* DPR Table */}
        <div className="overflow-x-auto">
          <table className="w-full border-collapse border border-border">
            <thead>
              <tr className="bg-surface/80">
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Date</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Supervisor</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Chainage From</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Chainage To</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Activity</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Qty Executed</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Unit</th>
                <th className="border border-border px-4 py-2 text-right text-text-secondary">Cumulative Qty</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Weather</th>
                <th className="border border-border px-4 py-2 text-left text-text-secondary">Remarks</th>
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
                  <td className="border border-border px-4 py-2 text-right">{row.qtyExecuted}</td>
                  <td className="border border-border px-4 py-2">{row.unit}</td>
                  <td className="border border-border px-4 py-2 text-right">
                    {row.cumulativeQty != null ? row.cumulativeQty : "-"}
                  </td>
                  <td className="border border-border px-4 py-2">{row.weatherCondition || "-"}</td>
                  <td className="border border-border px-4 py-2">{row.remarks || "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
