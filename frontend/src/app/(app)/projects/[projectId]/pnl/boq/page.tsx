"use client";

import { useParams } from "next/navigation";
import { PnlView } from "@/components/financials/PnlView";

export default function PnlBoqPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  return (
    <PnlView
      projectId={projectId}
      scope="boq"
      title="P&L vs BOQ Rates"
      description="Revenue is priced at the contract BOQ rate (BOQ.boqRate) — the rate the client pays. Actual Cost is the project's total actual cost — DPR line costs plus activity expenses. Margin tells you whether revenue is covering cost."
      revenueLabel="BOQ Revenue"
    />
  );
}
