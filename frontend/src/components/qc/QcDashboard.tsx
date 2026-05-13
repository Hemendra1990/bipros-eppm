"use client";

import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, CheckCircle, RotateCcw, TrendingUp } from "lucide-react";
import { qcApi } from "@/lib/api/qcApi";
import { KpiTile } from "@/components/common/KpiTile";
import { EmptyState } from "@/components/common/EmptyState";
import { Badge } from "@/components/ui/badge";

interface Props {
  projectId: string;
}

export function QcDashboard({ projectId }: Props) {
  const { data, isLoading } = useQuery({
    queryKey: ["qc-dashboard", projectId],
    queryFn: () => qcApi.getDashboard(projectId),
  });

  const dash = data?.data;

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-24 animate-pulse rounded-xl bg-surface-hover" />
          ))}
        </div>
        <div className="h-48 animate-pulse rounded-xl bg-surface-hover" />
      </div>
    );
  }

  if (!dash) {
    return       <EmptyState title="Dashboard unavailable" description="Could not load QC dashboard data." />;
  }

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <KpiTile label="Total Tests" value={dash.totalTests} icon={<TrendingUp className="h-4 w-4" />} />
        <KpiTile label="Pass" value={dash.passCount} icon={<CheckCircle className="h-4 w-4 text-success" />} tone="success" />
        <KpiTile label="Fail" value={dash.failCount} icon={<AlertTriangle className="h-4 w-4 text-burgundy" />} tone="danger" />
        <KpiTile label="Repeat" value={dash.repeatCount} icon={<RotateCcw className="h-4 w-4 text-bronze-warn" />} tone="warning" />
        <KpiTile label="Pass Rate %" value={`${dash.passRate.toFixed(1)}%`} icon={<TrendingUp className="h-4 w-4" />} />
        <KpiTile label="Today" value={dash.todayTests} icon={<TrendingUp className="h-4 w-4" />} />
      </div>

      {dash.recentFails.length > 0 && (
        <div>
          <h3 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate">Recent Fails</h3>
          <div className="space-y-2">
            {dash.recentFails.map((r) => (
              <div key={r.id} className="flex items-center gap-3 rounded-md border border-burgundy/20 bg-burgundy/5 px-3 py-2 text-sm">
                <Badge variant="danger" withDot>FAIL</Badge>
                <span className="font-medium text-charcoal">{r.testTypeName}</span>
                {r.sampleRefNo && <span className="text-slate">· Ref {r.sampleRefNo}</span>}
                {r.labInspector && <span className="text-slate">· {r.labInspector}</span>}
                {r.testResult != null && (
                  <span className="tabular-nums text-slate">Result: {r.testResult}</span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {dash.byActivity.length > 0 && (
        <div>
          <h3 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate">By Activity</h3>
          <div className="overflow-x-auto rounded-md border border-hairline">
            <table className="w-full text-sm">
              <thead className="bg-ivory/60">
                <tr>
                  <th className="px-4 py-2 text-left font-semibold text-slate">Activity</th>
                  <th className="px-4 py-2 text-right font-semibold text-slate">Pass</th>
                  <th className="px-4 py-2 text-right font-semibold text-slate">Fail</th>
                  <th className="px-4 py-2 text-right font-semibold text-slate">Repeat</th>
                  <th className="px-4 py-2 text-right font-semibold text-slate">Total</th>
                </tr>
              </thead>
              <tbody>
                {dash.byActivity.map((a) => {
                  const total = a.pass + a.fail + a.repeat;
                  return (
                    <tr key={a.activityId} className="border-t border-hairline">
                      <td className="px-4 py-2 text-charcoal">{a.activityName}</td>
                      <td className="px-4 py-2 text-right text-success tabular-nums">{a.pass}</td>
                      <td className="px-4 py-2 text-right text-burgundy tabular-nums">{a.fail}</td>
                      <td className="px-4 py-2 text-right text-bronze-warn tabular-nums">{a.repeat}</td>
                      <td className="px-4 py-2 text-right font-semibold text-charcoal tabular-nums">{total}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
