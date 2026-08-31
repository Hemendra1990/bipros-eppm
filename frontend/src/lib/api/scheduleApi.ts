import { apiClient } from "./client";
import type { ApiResponse } from "../types";

export interface ScheduleResultResponse {
  id: string;
  projectId: string;
  dataDate: string;
  projectStartDate: string;
  projectFinishDate: string;
  criticalPathLength: number | null;
  totalActivities: number;
  criticalActivities: number;
  schedulingOption: string;
  calculatedAt: string;
  durationSeconds: number | null;
  status: string;
  warnings: string[];
  /**
   * Phase 1.6 status breakdown — null on cached/historical results that pre-date the
   * scheduler change. UI should render "—" rather than 0 in that case.
   */
  notStartedActivities: number | null;
  inProgressActivities: number | null;
  completedActivities: number | null;
}

// === What-If scenario simulation ===

/** One activity duration change fed into a what-if run. Positive = delay, negative = crash. */
export interface WhatIfChange {
  activityId: string;
  deltaDays: number;
}

export interface WhatIfRequest {
  scenarioLabel: string;
  changes: WhatIfChange[];
}

/** Per-activity impact row returned by a what-if run. */
export interface WhatIfActivityImpact {
  activityId: string;
  activityName: string;
  baselineFinish: string | null;
  scenarioFinish: string | null;
  shiftDays: number;
  critical: boolean;
}

export interface WhatIfResponse {
  scenarioLabel: string;
  baselineFinish: string | null;
  scenarioFinish: string | null;
  /** Working-day slip of the project finish. >0 = project finishes later; <=0 = no slip / recovered. */
  deltaWorkingDays: number;
  baselineCriticalCount: number;
  scenarioCriticalCount: number;
  newlyCritical: WhatIfActivityImpact[];
  changedActivities: WhatIfActivityImpact[];
}

export const scheduleApi = {
  /**
   * Run a non-persisted "what-if" schedule pass: apply the given duration deltas to a scratch
   * copy of the network and report how the project finish and critical path move. Nothing is
   * written back to the live schedule.
   */
  whatIf: (projectId: string, request: WhatIfRequest) =>
    apiClient
      .post<ApiResponse<WhatIfResponse>>(
        `/v1/projects/${projectId}/schedule/what-if`,
        request
      )
      .then((r) => r.data),
};
