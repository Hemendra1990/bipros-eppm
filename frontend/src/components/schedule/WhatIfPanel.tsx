"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { FlaskConical, Play, ArrowRight, AlertTriangle } from "lucide-react";
import { activityApi, type ActivityResponse } from "@/lib/api/activityApi";
import { scheduleApi, type WhatIfResponse } from "@/lib/api/scheduleApi";
import { getErrorMessage } from "@/lib/utils/error";
import { Card, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input, Field, Label, FieldHint } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { SearchableSelect, type SelectOption } from "@/components/common/SearchableSelect";

function formatDate(value: string | null | undefined) {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

/**
 * What-If scenario panel. Lets a planner pick one activity, nudge its duration (positive = delay,
 * negative = crash) and simulate the effect on the project finish and critical path — without
 * writing anything back to the live schedule. Backed by POST /schedule/what-if.
 */
export function WhatIfPanel({ projectId }: { projectId: string }) {
  const [activityId, setActivityId] = useState("");
  const [deltaDaysInput, setDeltaDaysInput] = useState("");

  // Shares the activities cache key with the page so we don't re-fetch.
  const { data: activitiesData, isLoading: isLoadingActivities } = useQuery({
    queryKey: ["activities", projectId],
    queryFn: () => activityApi.listActivities(projectId, 0, 500),
    enabled: !!projectId,
  });

  const activities = (activitiesData?.data?.content ?? []) as ActivityResponse[];
  const options: SelectOption[] = useMemo(
    () => activities.map((a) => ({ value: a.id, label: `${a.code} — ${a.name}` })),
    [activities]
  );
  const selectedActivity = activities.find((a) => a.id === activityId) ?? null;

  const deltaDays = Number.parseInt(deltaDaysInput, 10);
  const hasValidDelta = Number.isFinite(deltaDays) && deltaDays !== 0;
  const canSimulate = !!activityId && hasValidDelta;

  const scenarioLabel = selectedActivity
    ? `${deltaDays > 0 ? "Delay" : "Crash"} ${selectedActivity.code} by ${Math.abs(deltaDays)}d`
    : "What-if scenario";

  const mutation = useMutation<WhatIfResponse, unknown>({
    mutationFn: async () => {
      const resp = await scheduleApi.whatIf(projectId, {
        scenarioLabel,
        changes: [{ activityId, deltaDays }],
      });
      if (!resp.data) throw new Error(resp.error?.message ?? "What-if returned no result");
      return resp.data;
    },
  });

  const result = mutation.data;

  return (
    <Card variant="accent" className="mt-6">
      <CardHeader className="flex items-start gap-3">
        <FlaskConical size={20} className="mt-0.5 shrink-0 text-gold" />
        <div>
          <CardTitle>What-If Analysis</CardTitle>
          <CardDescription>
            Simulate delaying or crashing an activity and see the impact on the project finish and
            critical path. Nothing is saved to the live schedule.
          </CardDescription>
        </div>
      </CardHeader>

      {/* Inputs */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
        <Field className="flex-1 min-w-0">
          <Label>Activity</Label>
          <SearchableSelect
            options={options}
            value={activityId}
            onChange={setActivityId}
            placeholder={isLoadingActivities ? "Loading activities…" : "Select an activity…"}
            disabled={isLoadingActivities}
          />
        </Field>

        <Field className="sm:w-56">
          <Label>Delay (+) / Crash (−) days</Label>
          <Input
            type="number"
            inputMode="numeric"
            step={1}
            value={deltaDaysInput}
            onChange={(e) => setDeltaDaysInput(e.target.value)}
            placeholder="e.g. 5 or -3"
          />
        </Field>

        <Button
          type="button"
          onClick={() => mutation.mutate()}
          disabled={!canSimulate || mutation.isPending}
          className="sm:mb-0"
        >
          <Play size={16} />
          {mutation.isPending ? "Simulating…" : "Simulate"}
        </Button>
      </div>

      <FieldHint className="mt-2">
        Positive days push the activity out (delay); negative days shorten it (crash / acceleration).
      </FieldHint>

      {/* Error */}
      {mutation.isError && (
        <div className="mt-4 flex items-center gap-2 rounded-[10px] border border-burgundy/30 bg-burgundy/10 px-3 py-2.5 text-sm text-burgundy">
          <AlertTriangle size={16} className="shrink-0" />
          {getErrorMessage(mutation.error, "What-if simulation failed")}
        </div>
      )}

      {/* Result */}
      {result && !mutation.isPending && <WhatIfResult result={result} />}
    </Card>
  );
}

function WhatIfResult({ result }: { result: WhatIfResponse }) {
  const slips = result.deltaWorkingDays > 0;
  const criticalGrew = result.scenarioCriticalCount > result.baselineCriticalCount;

  return (
    <div className="mt-5 border-t border-hairline pt-5">
      {/* Finish date shift + delta badge */}
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div className="flex items-center gap-3 text-sm">
          <div>
            <div className="text-xs font-semibold uppercase tracking-wide text-slate">
              Baseline finish
            </div>
            <div className="font-display text-lg font-semibold text-charcoal">
              {formatDate(result.baselineFinish)}
            </div>
          </div>
          <ArrowRight size={18} className="mt-4 shrink-0 text-slate" />
          <div>
            <div className="text-xs font-semibold uppercase tracking-wide text-slate">
              Scenario finish
            </div>
            <div className="font-display text-lg font-semibold text-charcoal">
              {formatDate(result.scenarioFinish)}
            </div>
          </div>
        </div>

        <div
          className={
            "flex flex-col items-center rounded-xl border px-5 py-3 text-center " +
            (slips
              ? "border-burgundy/30 bg-burgundy/10 text-burgundy"
              : "border-emerald/30 bg-emerald/10 text-emerald")
          }
        >
          <div className="font-display text-2xl font-bold leading-none">
            {slips ? `+${result.deltaWorkingDays}` : result.deltaWorkingDays} days
          </div>
          <div className="mt-1 text-xs font-semibold uppercase tracking-wide">
            {slips ? "Finish slips" : "No slip / recovered"}
          </div>
        </div>
      </div>

      {/* Critical-path counts */}
      <div className="mt-5 flex flex-wrap items-center gap-3">
        <span className="text-xs font-semibold uppercase tracking-wide text-slate">
          Critical activities
        </span>
        <Badge variant="neutral">Baseline: {result.baselineCriticalCount}</Badge>
        <Badge variant={criticalGrew ? "danger" : "neutral"}>
          Scenario: {result.scenarioCriticalCount}
        </Badge>
        {criticalGrew && (
          <span className="text-xs font-medium text-burgundy">
            +{result.scenarioCriticalCount - result.baselineCriticalCount} newly critical
          </span>
        )}
      </div>

      {/* Newly critical list */}
      {result.newlyCritical.length > 0 && (
        <div className="mt-4">
          <div className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate">
            Newly critical ({result.newlyCritical.length})
          </div>
          <ul className="divide-y divide-hairline overflow-hidden rounded-[10px] border border-hairline">
            {result.newlyCritical.map((a) => (
              <li
                key={a.activityId}
                className="flex items-center justify-between gap-3 bg-ivory px-3 py-2 text-sm"
              >
                <span className="min-w-0 truncate text-charcoal">{a.activityName}</span>
                <span className="flex shrink-0 items-center gap-2">
                  <span className="text-xs text-slate">{formatDate(a.scenarioFinish)}</span>
                  {a.shiftDays !== 0 && (
                    <Badge variant={a.shiftDays > 0 ? "danger" : "success"}>
                      {a.shiftDays > 0 ? `+${a.shiftDays}` : a.shiftDays}d
                    </Badge>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {result.newlyCritical.length === 0 && (
        <p className="mt-4 text-sm text-slate">
          No activities became newly critical under this scenario.
        </p>
      )}
    </div>
  );
}
