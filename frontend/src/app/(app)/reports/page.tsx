"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { BarChart3, TrendingUp, DollarSign, GitCompare, FileText, Download } from "lucide-react";
import { LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";
import { PageHeader } from "@/components/common/PageHeader";
import { TabTip } from "@/components/common/TabTip";
import { reportApi } from "@/lib/api/reportApi";
import { reportDataApi } from "@/lib/api/reportDataApi";
import { projectApi } from "@/lib/api/projectApi";
import { resourceApi, type ResourceResponse } from "@/lib/api/resourceApi";
import toast from "react-hot-toast";
import { MonthlyProgressReport } from "@/components/reports/MonthlyProgressReport";
import { EvmReport } from "@/components/reports/EvmReport";
import { CashFlowReport } from "@/components/reports/CashFlowReport";
import { ContractStatusReport } from "@/components/reports/ContractStatusReport";
import { RiskRegisterReport } from "@/components/reports/RiskRegisterReport";
import { ResourceUtilizationReport } from "@/components/reports/ResourceUtilizationReport";
import type { ProjectResponse } from "@/lib/types";

interface ReportCard {
  id: string;
  icon: React.ReactNode;
  title: string;
  description: string;
}

const reportCards: ReportCard[] = [
  {
    id: "s-curve",
    icon: <TrendingUp size={32} />,
    title: "S-Curve",
    description: "Planned vs Actual vs Earned value over time",
  },
  {
    id: "resource-histogram",
    icon: <BarChart3 size={32} />,
    title: "Resource Histogram",
    description: "Resource allocation and utilization by date",
  },
  {
    id: "cash-flow",
    icon: <DollarSign size={32} />,
    title: "Cash Flow",
    description: "Project cash inflows and outflows",
  },
  {
    id: "schedule-comparison",
    icon: <GitCompare size={32} />,
    title: "Schedule Comparison",
    description: "Compare baseline vs current schedule",
  },
  {
    id: "custom-reports",
    icon: <FileText size={32} />,
    title: "Custom Reports",
    description: "Create and manage custom project reports",
  },
];

interface SCurveChartData {
  period: string;
  pv: number;
  ev: number;
  ac: number;
}

interface HistogramChartData {
  date: string;
  [key: string]: string | number;
}

interface CashFlowChartData {
  period: string;
  income: number;
  expense: number;
}

interface AllocationItem {
  date: string;
  allocated: Record<string, number>;
}

interface ScheduleComparisonChartData {
  activityCode: string;
  activityName: string;
  baselineStart: string | null;
  baselineFinish: string | null;
  currentStart: string | null;
  currentFinish: string | null;
  startVarianceDays: number;
  finishVarianceDays: number;
}

interface ReportChartData {
  scurve?: SCurveChartData[];
  histogram?: HistogramChartData[];
  cashflow?: CashFlowChartData[];
  scheduleComparison?: ScheduleComparisonChartData[];
  customReports?: Array<{ id: string; name: string; type: string; createdAt: string }>;
}

export default function ReportsPage() {
  const [activeTab, setActiveTab] = useState<"classic" | "standard">("standard");
  const [generatingReport, setGeneratingReport] = useState<string | null>(null);
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null);
  const [selectedResourceId, setSelectedResourceId] = useState<string | null>(null);
  const [reportData, setReportData] = useState<ReportChartData>({});
  const [downloadingReport, setDownloadingReport] = useState<string | null>(null);

  const { data: projectsData } = useQuery({
    queryKey: ["projects"],
    queryFn: () => projectApi.listProjects(0, 100),
  });

  const { data: resourcesData } = useQuery({
    queryKey: ["resources"],
    queryFn: () => resourceApi.listResources(0, 100),
  });

  // Standard reports queries. The backend /v1/reports/* endpoints currently 500
  // for every report type, so we disable retry to avoid 6*3 = 18 network errors
  // and surface a clean empty state below instead of crashing the page.
  const { data: monthlyProgressData, isLoading: monthlyProgressLoading, isError: monthlyProgressError } = useQuery({
    queryKey: ["report-monthly-progress", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getMonthlyProgress(selectedProjectId, new Date().toISOString().slice(0, 7)) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
    retry: false,
  });

  const { data: evmReportData, isLoading: evmReportLoading, isError: evmReportError } = useQuery({
    queryKey: ["report-evm", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getEvmReport(selectedProjectId) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
    retry: false,
  });

  const { data: cashFlowData, isLoading: cashFlowLoading, isError: cashFlowError } = useQuery({
    queryKey: ["report-cash-flow", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getCashFlowReport(selectedProjectId) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
    retry: false,
  });

  const { data: contractStatusData, isLoading: contractStatusLoading, isError: contractStatusError } = useQuery({
    queryKey: ["report-contract-status", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getContractStatus(selectedProjectId) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
    retry: false,
  });

  const { data: riskRegisterData, isLoading: riskRegisterLoading, isError: riskRegisterError } = useQuery({
    queryKey: ["report-risk-register", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getRiskRegister(selectedProjectId) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
    retry: false,
  });

  const { data: resourceUtilizationData, isLoading: resourceUtilizationLoading, isError: resourceUtilizationError } = useQuery({
    queryKey: ["report-resource-utilization", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getResourceUtilization(selectedProjectId) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
    retry: false,
  });

  // True when every standard-report query resolved (success or failure) and
  // none of them returned data — used to render the empty-state card.
  const standardReportsAllSettled =
    !!selectedProjectId &&
    !monthlyProgressLoading &&
    !evmReportLoading &&
    !cashFlowLoading &&
    !contractStatusLoading &&
    !riskRegisterLoading &&
    !resourceUtilizationLoading;

  const standardReportsAllEmpty =
    standardReportsAllSettled &&
    !monthlyProgressData &&
    !evmReportData &&
    !cashFlowData &&
    !contractStatusData &&
    !riskRegisterData &&
    !resourceUtilizationData;

  const standardReportsAllFailed =
    standardReportsAllEmpty &&
    monthlyProgressError &&
    evmReportError &&
    cashFlowError &&
    contractStatusError &&
    riskRegisterError &&
    resourceUtilizationError;

  const projects = projectsData?.data?.content ?? [];
  // resourceApi.listResources returns a flat array (List<ResourceResponse>),
  // not a paged response. Be defensive in case that ever changes.
  const rawResources = resourcesData?.data as unknown;
  const resources: ResourceResponse[] = Array.isArray(rawResources)
    ? (rawResources as ResourceResponse[])
    : ((rawResources as { content?: ResourceResponse[] } | undefined)?.content ?? []);

  const handleGenerateReport = async (reportId: string) => {
    setGeneratingReport(reportId);
    try {
      if (reportId === "s-curve" && selectedProjectId) {
        const response = await reportApi.generateSCurve(selectedProjectId);
        if (response.data) {
          const data = response.data;
          const chartData: SCurveChartData[] = data.periods.map((period: string, idx: number) => ({
            period,
            pv: data.plangedCumulativeValue[idx] ?? 0,
            ev: data.earnedCumulativeValue[idx] ?? 0,
            ac: data.actualCumulativeValue[idx] ?? 0,
          }));
          setReportData({ scurve: chartData });
        }
      } else if (reportId === "resource-histogram" && selectedProjectId) {
        const response = await reportApi.generateResourceHistogram(
          selectedProjectId,
          selectedResourceId || undefined
        );
        if (response.data) {
          const data = response.data;
          const chartData: HistogramChartData[] = data.allocations.map((a: AllocationItem) => ({
            date: a.date,
            ...a.allocated,
          }));
          setReportData({ histogram: chartData });
        }
      } else if (reportId === "cash-flow" && selectedProjectId) {
        const response = await reportApi.generateCashFlow(selectedProjectId);
        if (response.data) {
          const data = response.data;
          const chartData: CashFlowChartData[] = data.periods.map((period: string, idx: number) => ({
            period,
            income: data.inflows[idx] ?? 0,
            expense: data.outflows[idx] ?? 0,
          }));
          setReportData({ cashflow: chartData });
        }
      } else if (reportId === "schedule-comparison" && selectedProjectId) {
        const response = await reportApi.generateScheduleComparison(selectedProjectId);
        if (response.data) {
          const data = response.data as { activities?: ScheduleComparisonChartData[] };
          setReportData({ scheduleComparison: data.activities ?? [] });
        }
      } else if (reportId === "custom-reports" && selectedProjectId) {
        const response = await reportApi.listCustomReports(selectedProjectId);
        if (response.data) {
          setReportData({ customReports: response.data as any });
        }
      }
    } catch (error) {
      toast.error("Failed to generate report");
    } finally {
      setGeneratingReport(null);
    }
  };

  const downloadReport = async (format: "EXCEL" | "PDF") => {
    setDownloadingReport(format);
    try {
      const response = await reportApi.executeReport(format);
      if (response.data?.id) {
        const downloadResponse = await reportApi.downloadReport(response.data.id);
        const url = window.URL.createObjectURL(new Blob([downloadResponse.data]));
        const link = document.createElement("a");
        link.href = url;
        link.download = `report-${response.data.id}.${format === "EXCEL" ? "xlsx" : "pdf"}`;
        link.click();
        window.URL.revokeObjectURL(url);
      }
    } catch (error) {
      console.error("Failed to download report:", error);
    } finally {
      setDownloadingReport(null);
    }
  };

  return (
    <div>
      <PageHeader
        title="Reports"
        description="Generate and view project reports and analytics"
      />

      <TabTip
        title="Reports"
        description="Generate standard project reports including Monthly Progress, EVM Analysis, Cash Flow, Resource Utilization, Risk Register, and Contract Status reports."
      />

      <div className="mb-8 rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
        <h2 className="mb-4 text-lg font-semibold text-text-primary">Report Filters</h2>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-text-secondary">Project</label>
            <select
              value={selectedProjectId || ""}
              onChange={(e) => {
                setSelectedProjectId(e.target.value || null);
                setSelectedResourceId(null);
              }}
              className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
            >
              <option value="">Select a project...</option>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.code} - {p.name}
                </option>
              ))}
            </select>
          </div>
          {selectedProjectId && (
            <div>
              <label className="block text-sm font-medium text-text-secondary">Resource (Optional)</label>
              <select
                value={selectedResourceId || ""}
                onChange={(e) => setSelectedResourceId(e.target.value || null)}
                className="mt-1 block w-full rounded-md border border-border bg-surface-hover px-3 py-2 text-text-primary focus:border-accent focus:outline-none"
              >
                <option value="">All resources</option>
                {resources.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.code} - {r.name}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
      </div>

      {/* Report Tabs */}
      <div className="mb-8">
        <div className="flex gap-4 border-b border-border">
          <button
            onClick={() => setActiveTab("standard")}
            className={`px-4 py-3 font-medium ${
              activeTab === "standard"
                ? "border-b-2 border-accent text-accent"
                : "text-text-secondary hover:text-text-primary"
            }`}
          >
            Standard Reports
          </button>
          <button
            onClick={() => setActiveTab("classic")}
            className={`px-4 py-3 font-medium ${
              activeTab === "classic"
                ? "border-b-2 border-accent text-accent"
                : "text-text-secondary hover:text-text-primary"
            }`}
          >
            Classic Reports
          </button>
        </div>
      </div>

      {/* Standard Reports Tab */}
      {activeTab === "standard" && (
        <div className="space-y-8">
          {monthlyProgressData && !monthlyProgressLoading && (
            <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
              <MonthlyProgressReport data={monthlyProgressData as any} />
            </div>
          )}

          {evmReportData && !evmReportLoading && (
            <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
              <EvmReport data={evmReportData as any} />
            </div>
          )}

          {cashFlowData && !cashFlowLoading && (
            <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
              <CashFlowReport data={cashFlowData as any} />
            </div>
          )}

          {contractStatusData && !contractStatusLoading && (
            <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
              <ContractStatusReport data={contractStatusData as any} />
            </div>
          )}

          {riskRegisterData && !riskRegisterLoading && (
            <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
              <RiskRegisterReport data={riskRegisterData as any} />
            </div>
          )}

          {resourceUtilizationData && !resourceUtilizationLoading && (
            <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
              <ResourceUtilizationReport data={resourceUtilizationData as any} />
            </div>
          )}

          {!selectedProjectId && (
            <div className="rounded-lg border border-warning/30 bg-warning/10 p-6 text-center">
              <p className="text-warning">Select a project to view standard reports</p>
            </div>
          )}

          {selectedProjectId && (monthlyProgressLoading || evmReportLoading || cashFlowLoading || contractStatusLoading || riskRegisterLoading || resourceUtilizationLoading) && (
            <div className="rounded-lg border border-accent/30 bg-accent/10 p-6 text-center">
              <p className="text-accent">Loading reports...</p>
            </div>
          )}

          {standardReportsAllFailed && (
            <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 p-6 text-center">
              <p className="mb-1 text-rose-300 font-medium">Standard reports are temporarily unavailable</p>
              <p className="text-sm text-rose-200/70">
                The reporting service returned errors for every report type. This usually means
                the backend /v1/reports/* endpoints are offline or misconfigured. Check the
                Classic Reports tab below, or retry once the service is back online.
              </p>
            </div>
          )}

          {standardReportsAllEmpty && !standardReportsAllFailed && (
            <div className="rounded-lg border border-border bg-surface-hover/40 p-6 text-center">
              <p className="text-text-secondary">No standard reports available for this project</p>
            </div>
          )}
        </div>
      )}

      {/* Classic Reports Tab */}
      {activeTab === "classic" && (
        <>
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {reportCards.map((card) => (
              <div
                key={card.id}
                className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg hover:shadow-xl transition-shadow"
              >
                <div className="mb-4 text-text-muted">{card.icon}</div>
                <h3 className="mb-2 text-lg font-semibold text-text-primary">{card.title}</h3>
                <p className="mb-6 text-sm text-text-secondary">{card.description}</p>
                <button
                  onClick={() => handleGenerateReport(card.id)}
                  disabled={generatingReport === card.id || !selectedProjectId}
                  className="w-full rounded-md bg-accent px-4 py-2 text-sm font-medium text-text-primary hover:bg-accent-hover disabled:opacity-50 transition-colors"
                >
                  {generatingReport === card.id ? "Generating..." : "Generate"}
                </button>
              </div>
            ))}
          </div>
        </>
      )}

      {/* Classic Report Output */}
      {activeTab === "classic" && (reportData.scurve || reportData.histogram || reportData.cashflow || reportData.scheduleComparison || reportData.customReports) && (
        <div className="mt-8 rounded-lg border border-border bg-surface/50 p-6 shadow-sm">
          <h2 className="mb-6 text-lg font-semibold text-text-primary">Generated Report</h2>
          {reportData.scurve && (
            <div className="mb-8">
              <h3 className="mb-4 font-semibold text-text-secondary">S-Curve Analysis</h3>
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={reportData.scurve}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="period" />
                  <YAxis />
                  <Tooltip contentStyle={{ backgroundColor: "#1e293b", border: "1px solid #334155", borderRadius: "8px", color: "#e2e8f0" }} />
                  <Legend />
                  <Line type="monotone" dataKey="pv" stroke="#3b82f6" name="Planned Value" />
                  <Line type="monotone" dataKey="ev" stroke="#10b981" name="Earned Value" />
                  <Line type="monotone" dataKey="ac" stroke="#ef4444" name="Actual Cost" />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
          {reportData.histogram && (
            <div className="mb-8">
              <h3 className="mb-4 font-semibold text-text-secondary">Resource Histogram</h3>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={reportData.histogram}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis />
                  <Tooltip contentStyle={{ backgroundColor: "#1e293b", border: "1px solid #334155", borderRadius: "8px", color: "#e2e8f0" }} />
                  <Legend />
                  {reportData.histogram.length > 0 &&
                    Object.keys(reportData.histogram[0])
                      .filter((k) => k !== "date")
                      .map((key, idx) => (
                        <Bar key={key} dataKey={key} fill={["#3b82f6", "#10b981", "#f59e0b"][idx % 3]} />
                      ))}
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
          {reportData.cashflow && (
            <div className="mb-6">
              <h3 className="mb-4 font-semibold text-text-secondary">Cash Flow Analysis</h3>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={reportData.cashflow}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="period" />
                  <YAxis />
                  <Tooltip contentStyle={{ backgroundColor: "#1e293b", border: "1px solid #334155", borderRadius: "8px", color: "#e2e8f0" }} />
                  <Legend />
                  <Bar dataKey="income" fill="#10b981" name="Income" />
                  <Bar dataKey="expense" fill="#ef4444" name="Expense" />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
          {reportData.scheduleComparison && (
            <div className="mb-6">
              <h3 className="mb-4 font-semibold text-text-secondary">Schedule Comparison</h3>
              {reportData.scheduleComparison.length === 0 ? (
                <p className="text-sm text-text-secondary">No schedule comparison data available. Create a baseline first.</p>
              ) : (
                <div className="overflow-auto">
                  <table className="w-full text-sm text-left">
                    <thead>
                      <tr className="border-b border-border text-text-secondary">
                        <th className="px-3 py-2">Activity</th>
                        <th className="px-3 py-2">Baseline Start</th>
                        <th className="px-3 py-2">Current Start</th>
                        <th className="px-3 py-2">Start Var (days)</th>
                        <th className="px-3 py-2">Baseline Finish</th>
                        <th className="px-3 py-2">Current Finish</th>
                        <th className="px-3 py-2">Finish Var (days)</th>
                      </tr>
                    </thead>
                    <tbody>
                      {reportData.scheduleComparison.map((row) => (
                        <tr key={row.activityCode} className="border-b border-border text-text-secondary">
                          <td className="px-3 py-2 font-mono">{row.activityCode}</td>
                          <td className="px-3 py-2">{row.baselineStart ?? "N/A"}</td>
                          <td className="px-3 py-2">{row.currentStart ?? "N/A"}</td>
                          <td className={`px-3 py-2 font-mono ${row.startVarianceDays > 0 ? "text-danger" : row.startVarianceDays < 0 ? "text-success" : ""}`}>
                            {row.startVarianceDays > 0 ? `+${row.startVarianceDays}` : row.startVarianceDays}
                          </td>
                          <td className="px-3 py-2">{row.baselineFinish ?? "N/A"}</td>
                          <td className="px-3 py-2">{row.currentFinish ?? "N/A"}</td>
                          <td className={`px-3 py-2 font-mono ${row.finishVarianceDays > 0 ? "text-danger" : row.finishVarianceDays < 0 ? "text-success" : ""}`}>
                            {row.finishVarianceDays > 0 ? `+${row.finishVarianceDays}` : row.finishVarianceDays}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}
          {reportData.customReports && (
            <div className="mb-6">
              <h3 className="mb-4 font-semibold text-text-secondary">Custom Reports</h3>
              {reportData.customReports.length === 0 ? (
                <p className="text-sm text-text-secondary">No custom reports configured for this project.</p>
              ) : (
                <div className="space-y-2">
                  {reportData.customReports.map((report) => (
                    <div key={report.id} className="flex items-center justify-between rounded-lg border border-border bg-surface-hover/50 px-4 py-3">
                      <div>
                        <p className="font-medium text-text-primary">{report.name}</p>
                        <p className="text-xs text-text-secondary">{report.type} &middot; Created {report.createdAt}</p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Export Reports */}
      {activeTab === "classic" && (
        <div className="mt-8 rounded-lg border border-border bg-surface/50 p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-text-primary">Export Reports</h2>
          <div className="flex gap-3">
            <button
              onClick={() => downloadReport("EXCEL")}
              disabled={downloadingReport === "EXCEL"}
              className="inline-flex items-center gap-2 rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-text-primary hover:bg-green-600 disabled:bg-border"
            >
              <Download size={16} />
              {downloadingReport === "EXCEL" ? "Exporting..." : "Export to Excel"}
            </button>
            <button
              onClick={() => downloadReport("PDF")}
              disabled={downloadingReport === "PDF"}
              className="inline-flex items-center gap-2 rounded-md bg-danger px-4 py-2 text-sm font-medium text-text-primary hover:bg-danger disabled:bg-border"
            >
              <Download size={16} />
              {downloadingReport === "PDF" ? "Exporting..." : "Export to PDF"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
