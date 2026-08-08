"use client";

import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { FileText, Loader2 } from "lucide-react";
import { PageHeader } from "@/components/common/PageHeader";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils/cn";
import { dprReportApi } from "@/lib/api/dprReportApi";

/**
 * Stored Daily DPR Reports — the in-app view the "DPR report ready" bell notification links to
 * (?report=<id> preselects one). Each row is exactly what was (or would be) emailed: same HTML
 * body, with the delivery outcome (SENT / PREVIEW / FAILED) alongside.
 */
export default function DprReportsPage() {
  const params = useParams<{ projectId: string }>();
  const projectId = params.projectId;
  const search = useSearchParams();
  const preselect = search.get("report");
  const [selectedId, setSelectedId] = useState<string | null>(preselect);

  const { data: listData, isLoading } = useQuery({
    queryKey: ["dpr-reports", projectId],
    queryFn: () => dprReportApi.list(projectId),
  });
  const reports = listData?.data ?? [];

  // Default to the newest report when nothing is preselected.
  useEffect(() => {
    if (!selectedId && reports.length > 0) setSelectedId(reports[0].id);
  }, [selectedId, reports]);

  const { data: detailData, isFetching: detailLoading } = useQuery({
    queryKey: ["dpr-report", projectId, selectedId],
    queryFn: () => dprReportApi.get(projectId, selectedId!),
    enabled: !!selectedId,
  });
  const detail = detailData?.data;

  const deliveryTone = (s: string | null | undefined) =>
    s === "SENT" ? "bg-emerald-100 text-emerald-700"
      : s === "PREVIEW" ? "bg-amber-100 text-amber-700"
      : s === "FAILED" ? "bg-red-100 text-red-700"
      : "bg-gray-100 text-gray-600";

  return (
    <div className="px-6 pb-10">
      <PageHeader
        title="Daily DPR Reports"
        description="Every generated report, exactly as it was (or would be) emailed — with its delivery outcome. Scheduling and recipients live in Admin → Settings (dpr_report_* keys)."
      />
      <div className="grid gap-4 lg:grid-cols-[300px_1fr]">
        <Card variant="flat" className="p-3 lg:self-start">
          {isLoading ? (
            <div className="flex items-center gap-2 p-3 text-sm text-slate">
              <Loader2 size={14} className="animate-spin" /> Loading…
            </div>
          ) : reports.length === 0 ? (
            <div className="p-4 text-center text-sm text-slate">
              <FileText size={22} className="mx-auto mb-2" />
              No reports generated yet. Enable <span className="font-mono text-[12px]">dpr_report_enabled</span> in
              Admin → Settings, or trigger a test send.
            </div>
          ) : (
            <ul className="space-y-1">
              {reports.map((r) => (
                <li key={r.id}>
                  <button
                    type="button"
                    onClick={() => setSelectedId(r.id)}
                    className={cn(
                      "w-full rounded-lg px-3 py-2 text-left text-sm hover:bg-surface-hover",
                      selectedId === r.id && "bg-surface-hover ring-1 ring-border",
                    )}
                  >
                    <div className="font-medium text-charcoal">
                      {new Date(r.generatedAt).toLocaleString()}
                    </div>
                    <div className="mt-0.5 flex flex-wrap items-center gap-1 text-[11px] text-slate">
                      <span>{r.windowLabel ?? "—"}</span>
                      <span>· {r.trigger}</span>
                      <span className={cn("rounded px-1.5 py-0.5 font-semibold", deliveryTone(r.deliveryStatus))}>
                        {r.deliveryStatus ?? r.status}
                      </span>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card variant="flat" className="p-4">
          {!selectedId ? null : detailLoading || !detail ? (
            <div className="flex items-center gap-2 p-3 text-sm text-slate">
              <Loader2 size={14} className="animate-spin" /> Loading report…
            </div>
          ) : (
            <div>
              <div className="mb-3 flex flex-wrap items-center gap-2 text-sm">
                <span className={cn("rounded px-2 py-0.5 text-[11px] font-semibold", deliveryTone(detail.deliveryStatus))}>
                  {detail.deliveryStatus ?? detail.status}
                </span>
                <span className="text-slate">
                  {detail.deliveredTo ? `to ${detail.deliveredTo}` : "no email recipients"}
                </span>
                {detail.errorMessage && (
                  <span className="text-red-600">— {detail.errorMessage}</span>
                )}
              </div>
              {detail.htmlBody ? (
                <iframe
                  title="Daily DPR Report"
                  sandbox=""
                  srcDoc={detail.htmlBody}
                  className="h-[70vh] w-full rounded-lg border border-border bg-white"
                />
              ) : (
                <p className="p-4 text-sm text-slate">
                  This run produced no report body{detail.errorMessage ? " — see the error above" : ""}.
                </p>
              )}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
