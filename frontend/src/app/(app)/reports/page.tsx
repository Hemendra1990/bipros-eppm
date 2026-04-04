"use client";

import { useState } from "react";
import { BarChart3, TrendingUp, DollarSign, GitCompare, FileText } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";

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

export default function ReportsPage() {
  const [generatingReport, setGeneratingReport] = useState<string | null>(null);

  const handleGenerateReport = async (reportId: string) => {
    setGeneratingReport(reportId);
    // Simulate report generation
    setTimeout(() => {
      setGeneratingReport(null);
    }, 2000);
  };

  return (
    <div>
      <PageHeader
        title="Reports"
        description="Generate and view project reports and analytics"
      />

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
              disabled={generatingReport === card.id}
              className="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:bg-gray-400 transition-colors"
            >
              {generatingReport === card.id ? "Generating..." : "Generate"}
            </button>
          </div>
        ))}
      </div>

      <div className="mt-12 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h2 className="mb-4 text-lg font-semibold text-gray-900">Recent Reports</h2>
        <div className="rounded-lg border border-dashed border-gray-300 py-12 text-center">
          <p className="text-gray-500">No reports generated yet. Create your first report above.</p>
        </div>
      </div>
    </div>
  );
}
