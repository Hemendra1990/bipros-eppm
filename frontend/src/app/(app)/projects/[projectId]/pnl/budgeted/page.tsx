"use client";

import { useParams } from "next/navigation";
import { PnlView } from "@/components/financials/PnlView";

export default function PnlBudgetedPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  return (
    <PnlView
      projectId={projectId}
      scope="budgeted"
      title="P&L vs Budgeted Unit Rates"
      description="Revenue is priced at the project team's internal budgeted unit rate (BOQ.budgetedRate). Actual Cost is the project's total actual cost — DPR line costs plus activity expenses. Margin tells you whether site execution is beating the internal plan."
      revenueLabel="Budgeted Revenue"
    />
  );
}
