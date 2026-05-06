import { useQuery } from "@tanstack/react-query";
import { permitApi } from "@/lib/api/permitApi";
import { portfolioReportApi } from "@/lib/api/portfolioReportApi";
import type { HubBadgeKey } from "@/components/hub/hubConfig";

export type HubSummary = Record<HubBadgeKey, number>;

const ZERO: HubSummary = {
  pendingPermits: 0,
  criticalRisks: 0,
  atRiskProjects: 0,
  overdueTasks: 0,
};

/**
 * Composes existing scorecard + permit summary endpoints into a flat record of
 * counts for hub hero badges. Errors are swallowed — a missing badge is better
 * than a broken landing page.
 *
 * `overdueTasks` is intentionally always 0 here: deriving it from the activities
 * endpoint costs N+1 calls (see /dashboard fetchMetrics) which is too much for a
 * landing page. If a single-call endpoint becomes available, wire it here.
 */
export function useHubSummary(): { data: HubSummary; isLoading: boolean } {
  const scorecard = useQuery({
    queryKey: ["hub-scorecard"],
    queryFn: () => portfolioReportApi.getScorecard().catch(() => null),
    staleTime: 60_000,
  });

  const permits = useQuery({
    queryKey: ["hub-permit-summary"],
    queryFn: () => permitApi.dashboardSummary().catch(() => null),
    staleTime: 60_000,
  });

  const data: HubSummary = {
    ...ZERO,
    pendingPermits: permits.data?.pendingReview ?? 0,
    criticalRisks: scorecard.data?.openRisksCritical ?? 0,
    atRiskProjects: scorecard.data?.activeProjectsWithCriticalActivities ?? 0,
  };

  return { data, isLoading: scorecard.isLoading || permits.isLoading };
}
