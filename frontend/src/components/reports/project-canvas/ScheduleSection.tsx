"use client";

import { useQuery } from "@tanstack/react-query";
import { projectInsightsApi } from "@/lib/api/projectInsightsApi";
import { KpiTile } from "@/components/common/KpiTile";
import { ragFromScore } from "@/lib/utils/rag";
import {
  EmptyBlock,
  LoadingBlock,
  SectionCard,
} from "@/components/common/dashboard/primitives";

export function ScheduleSection({ projectId }: { projectId: string }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["project-schedule-quality", projectId],
    queryFn: () => projectInsightsApi.getScheduleQuality(projectId),
    staleTime: 60_000,
  });

  if (isLoading)
    return (
      <SectionCard title="Schedule & Time Analysis">
        <LoadingBlock />
      </SectionCard>
    );
  if (isError || !data)
    return (
      <SectionCard title="Schedule & Time Analysis">
        <EmptyBlock label="Unavailable" />
      </SectionCard>
    );

  const rag = ragFromScore(data.overallHealthPct);
  const healthTone = rag === "GREEN" ? "success" : rag === "AMBER" ? "warning" : "danger";

  return (
    <SectionCard
      title="Schedule & Time Analysis"
      subtitle="DCMA-14 health, critical path, and baseline quality"
    >
      <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4">
        <KpiTile
          label="Schedule health"
          value={`${data.overallHealthPct.toFixed(0)}%`}
          tone={healthTone}
        />
        <KpiTile label="Critical path" value={`${data.criticalPathLength} tasks`} />
        <KpiTile label="BEI (actual)" value={data.beiActual.toFixed(2)} hint={`target ${data.beiRequired}`} />
        <KpiTile
          label="Missed tasks"
          value={data.missedTasksCount}
          tone={data.missedTasksCount > 0 ? "danger" : "success"}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div>
          <h3 className="mb-2 text-sm font-medium text-text-secondary">DCMA-14 checks</h3>
          <dl className="grid grid-cols-2 gap-2 text-xs">
            <DlRow label="Missing logic" value={data.missingLogicCount} danger={data.missingLogicCount > 0} />
            <DlRow label="Leads (negative lag)" value={data.leadRelationshipsCount} danger={data.leadRelationshipsCount > 0} />
            <DlRow label="Lags" value={data.lagsCount} />
            <DlRow label="FS relationships %" value={`${data.fsRelationshipPct.toFixed(0)}%`} danger={data.fsRelationshipPct < 90} />
            <DlRow label="Hard constraints" value={data.hardConstraintsCount} danger={data.hardConstraintsCount > 0} />
            <DlRow label="High float (>44d)" value={data.highFloatCount} danger={data.highFloatCount > 0} />
            <DlRow label="Negative float" value={data.negativeFloatCount} danger={data.negativeFloatCount > 0} />
            <DlRow label="Invalid dates" value={data.invalidDatesCount} danger={data.invalidDatesCount > 0} />
            <DlRow
              label="Tasks without resources"
              value={data.resourceAllocationIssues}
              danger={data.resourceAllocationIssues > 0}
            />
            <DlRow
              label="Critical path identified"
              value={data.criticalPathTestOk ? "Yes" : "No"}
              danger={!data.criticalPathTestOk}
            />
          </dl>
        </div>

        <div>
          <h3 className="mb-2 text-sm font-medium text-text-secondary">Failing checks</h3>
          {data.failingChecks.length === 0 ? (
            <div className="rounded-md border border-success/30 bg-success/10 p-4 text-sm text-success">
              All DCMA checks pass
            </div>
          ) : (
            <ul className="space-y-1.5">
              {data.failingChecks.map((f) => (
                <li
                  key={f}
                  className="flex items-start gap-2 rounded-md border border-danger/20 bg-danger/5 p-2 text-xs text-danger"
                >
                  <span>•</span>
                  <span className="text-text-primary">{f}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </SectionCard>
  );
}

function DlRow({
  label,
  value,
  danger = false,
}: {
  label: string;
  value: string | number;
  danger?: boolean;
}) {
  return (
    <>
      <dt className="text-text-secondary">{label}</dt>
      <dd className={`text-right font-mono ${danger ? "text-danger" : "text-text-primary"}`}>{value}</dd>
    </>
  );
}
