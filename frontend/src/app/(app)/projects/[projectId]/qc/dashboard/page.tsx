"use client";

import { useParams } from "next/navigation";
import { QcDashboard } from "@/components/qc/QcDashboard";
import { TabTip } from "@/components/common/TabTip";

export default function QcDashboardPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  return (
    <div className="p-6">
      <TabTip
        title="QC Dashboard"
        description="Project-level quality control summary — pass rates, recent failures, and activity-level breakdowns."
      />
      <div className="mb-6">
        <h1 className="font-display text-3xl font-bold text-charcoal">QC Dashboard</h1>
      </div>
      <QcDashboard projectId={projectId} />
    </div>
  );
}
