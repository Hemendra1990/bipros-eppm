"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { BarChart3, TrendingUp, DollarSign, GitCompare, FileText, Download } from "lucide-react";
import { LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts";
import { PageHeader } from "@/components/common/PageHeader";
import { reportApi } from "@/lib/api/reportApi";
import { reportDataApi } from "@/lib/api/reportDataApi";
import { projectApi } from "@/lib/api/projectApi";
import { resourceApi } from "@/lib/api/resourceApi";
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

interface ReportChartData {
  scurve?: SCurveChartData[];
  histogram?: HistogramChartData[];
  cashflow?: CashFlowChartData[];
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
    queryKey: ["resources", selectedProjectId],
    queryFn: () => selectedProjectId ? resourceApi.getProjectResourceAssignments(selectedProjectId, 0, 100) : Promise.resolve(null),
    enabled: !!selectedProjectId,
  });

  // Standard reports queries
  const { data: monthlyProgressData, isLoading: monthlyProgressLoading } = useQuery({
    queryKey: ["report-monthly-progress", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getMonthlyProgress(selectedProjectId, new Date().toISOString().slice(0, 7)) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
  });

  const { data: evmReportData, isLoading: evmReportLoading } = useQuery({
    queryKey: ["report-evm", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getEvmReport(selectedProjectId) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
  });

  const { data: cashFlowData, isLoading: cashFlowLoading } = useQuery({
    queryKey: ["report-cash-flow", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getCashFlowReport(selectedProjectId) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
  });

  const { data: contractStatusData, isLoading: contractStatusLoading } = useQuery({
    queryKey: ["report-contract-status", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getContractStatus(selectedProjectId) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
  });

  const { data: riskRegisterData, isLoading: riskRegisterLoading } = useQuery({
    queryKey: ["report-risk-register", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getRiskRegister(selectedProjectId) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
  });

  const { data: resourceUtilizationData, isLoading: resourceUtilizationLoading } = useQuery({
    queryKey: ["report-resource-utilization", selectedProjectId],
    queryFn: () => selectedProjectId ? reportDataApi.getResourceUtilization(selectedProjectId) : Promise.resolve(null),
    enabled: !!selectedProjectId && activeTab === "standard",
  });

  const projects = projectsData?.data?.content ?? [];
  const resources = resourcesData?.data?.content ?? [];

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
          const chartData: HistogramChartData[] = data.allocations.map((a: any) => ({
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
      }
    } catch (error) {
      console.error("Failed to generate report:", error);
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

      <div className="mb-8 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h2 className="mb-4 text-lg font-semibold text-gray-900">Report Filters</h2>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="block text-sm font-medium text-gray-700">Project</label>
            <select
              value={selectedProjectId || ""}
              onChange={(e) => {
                setSelectedProjectId(e.target.value || null);
                setSelectedResourceId(null);
              }}
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:outline-none"
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
              <label className="block text-sm font-medium text-gray-700">Resource (Optional)</label>
              <select
                value={selectedResourceId || ""}
                onChange={(e) => setSelectedResourceId(e.target.value || null)}
                className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:outline-none"
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
        <div className="flex gap-4 border-b border-gray-200">
          <button
            onClick={() => setActiveTab("standard")}
            className={`px-4 py-3 font-medium ${
              activeTab === "standard"
                ? "border-b-2 border-blue-600 text-blue-600"
                : "text-gray-600 hover:text-gray-900"
            }`}
          >
            Standard Reports
          </button>
          <button
            onClick={() => setActiveTab("classic")}
            className={`px-4 py-3 font-medium ${
              activeTab === "classic"
                ? "border-b-2 border-blue-600 text-blue-600"
                : "text-gray-600 hover:text-gray-900"
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
            <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
              <MonthlyProgressReport data={monthlyProgressData as any} />
            </div>
          )}

          {evmReportData && !evmReportLoading && (
            <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
              <EvmReport data={evmReportData as any} />
            </div>
          )}

          {cashFlowData && !cashFlowLoading && (
            <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
              <CashFlowReport data={cashFlowData as any} />
            </div>
          )}

          {contractStatusData && !contractStatusLoading && (
            <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
              <ContractStatusReport data={contractStatusData as any} />
            </div>
          )}

          {riskRegisterData && !riskRegisterLoading && (
            <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
              <RiskRegisterReport data={riskRegisterData as any} />
            </div>
          )}

          {resourceUtilizationData && !resourceUtilizationLoading && (
            <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
              <ResourceUtilizationReport data={resourceUtilizationData as any} />
            </div>
          )}

          {!selectedProjectId && (
            <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-6 text-center">
              <p className="text-yellow-700">Select a project to view standard reports</p>
            </div>
          )}

          {selectedProjectId && !monthlyProgressData && (monthlyProgressLoading || evmReportLoading || cashFlowLoading || contractStatusLoading || riskRegisterLoading || resourceUtilizationLoading) && (
            <div className="rounded-lg border border-blue-200 bg-blue-50 p-6 text-center">
              <p className="text-blue-700">Loading reports...</p>
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
                className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm hover:shadow-md transition-shadow"
              >
                <div className="mb-4 text-gray-400">{card.icon}</div>
                <h3 className="mb-2 text-lg font-semibold text-gray-900">{card.title}</h3>
                <p className="mb-6 text-sm text-gray-600">{card.description}</p>
                <button
                  onClick={() => handleGenerateReport(card.id)}
                  disabled={generatingReport === card.id || !selectedProjectId}
                  className="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400 transition-colors"
                >
                  {generatingReport === card.id ? "Generating..." : "Generate"}
                </button>
              </div>
            ))}
          </div>
        </>
      )}

      {/* Classic Report Output */}
      {activeTab === "classic" && (reportData.scurve || reportData.histogram || reportData.cashflow) && (
        <div className="mt-8 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <h2 className="mb-6 text-lg font-semibold text-gray-900">Generated Report</h2>
          {reportData.scurve && (
            <div className="mb-8">
              <h3 className="mb-4 font-semibold text-gray-700">S-Curve Analysis</h3>
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={reportData.scurve}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="period" />
                  <YAxis />
                  <Tooltip />
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
              <h3 className="mb-4 font-semibold text-gray-700">Resource Histogram</h3>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={reportData.histogram}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis />
                  <Tooltip />
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
              <h3 className="mb-4 font-semibold text-gray-700">Cash Flow Analysis</h3>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={reportData.cashflow}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="period" />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="income" fill="#10b981" name="Income" />
                  <Bar dataKey="expense" fill="#ef4444" name="Expense" />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      )}

      {/* Export Reports */}
      {activeTab === "classic" && (
        <div className="mt-8 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-gray-900">Export Reports</h2>
          <div className="flex gap-3">
            <button
              onClick={() => downloadReport("EXCEL")}
              disabled={downloadingReport === "EXCEL"}
              className="inline-flex items-center gap-2 rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:bg-gray-400"
            >
              <Download size={16} />
              {downloadingReport === "EXCEL" ? "Exporting..." : "Export to Excel"}
            </button>
            <button
              onClick={() => downloadReport("PDF")}
              disabled={downloadingReport === "PDF"}
              className="inline-flex items-center gap-2 rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:bg-gray-400"
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
