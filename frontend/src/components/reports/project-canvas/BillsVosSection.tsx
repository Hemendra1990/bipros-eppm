"use client";

import { useQuery } from "@tanstack/react-query";
import { projectInsightsApi } from "@/lib/api/projectInsightsApi";
import { KpiTile } from "@/components/common/KpiTile";
import {
  EmptyBlock,
  LoadingBlock,
  SectionCard,
  formatCrore,
  truncate,
} from "@/components/common/dashboard/primitives";

export function BillsVosSection({ projectId }: { projectId: string }) {
  const billsQuery = useQuery({
    queryKey: ["project-ra-bill-summary", projectId],
    queryFn: () => projectInsightsApi.getRaBillSummary(projectId),
    staleTime: 60_000,
  });
  const vosQuery = useQuery({
    queryKey: ["project-variation-orders", projectId],
    queryFn: () => projectInsightsApi.getVariationOrders(projectId),
    staleTime: 60_000,
  });

  if (billsQuery.isLoading || vosQuery.isLoading)
    return (
      <SectionCard title="Bills & Variation Orders">
        <LoadingBlock />
      </SectionCard>
    );

  const bills = billsQuery.data;
  const vos = vosQuery.data ?? [];

  const hasBills =
    bills &&
    (bills.totalSubmittedCrores > 0 ||
      bills.pendingApprovalCrores > 0 ||
      bills.approvedCrores > 0 ||
      bills.paidCrores > 0 ||
      (bills.bills?.length ?? 0) > 0);
  const hasVos = vos.length > 0;

  if (!hasBills && !hasVos) {
    return (
      <SectionCard title="Bills & Variation Orders">
        <EmptyBlock label="No bills or variation orders recorded" />
      </SectionCard>
    );
  }

  return (
    <SectionCard
      title="Bills & Variation Orders"
      subtitle="RA-bill lifecycle and VO register"
    >
      {hasBills && bills && (
        <>
          <div className="mb-3 grid grid-cols-2 gap-3 md:grid-cols-4 lg:grid-cols-6">
            <KpiTile label="Submitted" value={formatCrore(bills.totalSubmittedCrores)} />
            <KpiTile label="Pending approval" value={formatCrore(bills.pendingApprovalCrores)} tone="warning" />
            <KpiTile label="Approved" value={formatCrore(bills.approvedCrores)} tone="accent" />
            <KpiTile label="Paid" value={formatCrore(bills.paidCrores)} tone="success" />
            <KpiTile label="Rejected" value={formatCrore(bills.rejectedCrores)} tone="danger" />
            <KpiTile label="Retention" value={formatCrore(bills.retentionHeldCrores)} />
          </div>

          {bills.bills && bills.bills.length > 0 && (
            <div className="mb-6 overflow-x-auto">
              <h3 className="mb-2 text-sm font-medium text-text-secondary">Recent bills</h3>
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b border-border text-left uppercase tracking-wide text-text-muted">
                    <th className="px-2 py-2">Bill #</th>
                    <th className="px-2 py-2">Period</th>
                    <th className="px-2 py-2">Status</th>
                    <th className="px-2 py-2 text-right">Gross</th>
                    <th className="px-2 py-2 text-right">Net</th>
                    <th className="px-2 py-2">Submitted</th>
                    <th className="px-2 py-2">Paid</th>
                  </tr>
                </thead>
                <tbody>
                  {bills.bills.slice(0, 20).map((b) => (
                    <tr key={b.id} className="border-b border-border/50">
                      <td className="px-2 py-2 font-mono text-text-primary">{b.billNumber}</td>
                      <td className="px-2 py-2 text-text-secondary">
                        {b.billPeriodFrom ?? "—"} → {b.billPeriodTo ?? "—"}
                      </td>
                      <td className="px-2 py-2">
                        <span className="rounded-full bg-surface-hover px-2 py-0.5 text-[10px] font-semibold text-text-secondary">
                          {b.status}
                        </span>
                      </td>
                      <td className="px-2 py-2 text-right font-mono">{b.grossAmount.toLocaleString("en-IN")}</td>
                      <td className="px-2 py-2 text-right font-mono">{b.netAmount.toLocaleString("en-IN")}</td>
                      <td className="px-2 py-2">{b.submittedDate ?? "—"}</td>
                      <td className="px-2 py-2">{b.paidDate ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      <h3 className="mb-2 text-sm font-medium text-text-secondary">Variation orders</h3>
      {!hasVos ? (
        <EmptyBlock label="No variation orders" />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-border text-left uppercase tracking-wide text-text-muted">
                <th className="px-2 py-2">VO #</th>
                <th className="px-2 py-2">Description</th>
                <th className="px-2 py-2 text-right">Cost impact</th>
                <th className="px-2 py-2 text-right">Time (d)</th>
                <th className="px-2 py-2">Status</th>
                <th className="px-2 py-2">Approved</th>
              </tr>
            </thead>
            <tbody>
              {vos.map((v) => (
                <tr key={v.id} className="border-b border-border/50">
                  <td className="px-2 py-2 font-mono text-text-primary">{v.voNumber}</td>
                  <td className="px-2 py-2 text-text-primary">{truncate(v.description, 90)}</td>
                  <td
                    className={`px-2 py-2 text-right font-mono ${v.costImpactCrores < 0 ? "text-success" : "text-danger"}`}
                  >
                    {formatCrore(v.costImpactCrores)}
                  </td>
                  <td className="px-2 py-2 text-right font-mono">
                    {v.timeImpactDays ?? "—"}
                  </td>
                  <td className="px-2 py-2">
                    <span className="rounded-full bg-surface-hover px-2 py-0.5 text-[10px] font-semibold text-text-secondary">
                      {v.status}
                    </span>
                  </td>
                  <td className="px-2 py-2">{v.approvedDate ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </SectionCard>
  );
}
