"use client";

import { useParams } from "next/navigation";
import { useMutation, useQuery, UseQueryResult } from "@tanstack/react-query";
import { PageHeader } from "@/components/common/PageHeader";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import {
  scheduleCompressionApi,
  CompressionAnalysisResponse,
  CompressionRecommendation,
  ScheduleScenarioResponse,
  ScenarioComparisonResponse,
} from "@/lib/api/scheduleCompressionApi";
import { ApiResponse } from "@/lib/types";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { SimpleTable } from "@/components/common/SimpleTable";
import type { ColumnDef } from "@tanstack/react-table";
import { KpiTile } from "@/components/common/KpiTile";
import { useProjectCurrency, type ProjectCurrency } from "@/lib/currency/ProjectCurrencyProvider";
import { formatDate } from "@/lib/utils/format";

const days = (n?: number | null) =>
  n == null || Math.abs(n) < 0.05 ? "0 days" : `${n.toFixed(1)} days`;

export default function ScheduleCompressionPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const { money, moneyCompact } = useProjectCurrency();

  const [activeTab, setActiveTab] = useState<"fast-track" | "crash" | "scenarios">("fast-track");
  const [selectedScenarios, setSelectedScenarios] = useState<{
    scenario1: string;
    scenario2: string;
  }>({ scenario1: "", scenario2: "" });

  // Fast-track analysis
  const fastTrackMutation = useMutation({
    mutationFn: () => scheduleCompressionApi.analyzeFastTrack(projectId),
  });

  // Crashing analysis
  const crashMutation = useMutation({
    mutationFn: () => scheduleCompressionApi.analyzeCrashing(projectId),
  });

  // Scenario queries
  const { data: scenarios } = useQuery({
    queryKey: ["scenarios", projectId],
    queryFn: () => scheduleCompressionApi.listScenarios(projectId),
    enabled: !!projectId,
  });

  // Comparison query
  const comparisonQuery = useQuery({
    queryKey: ["scenario-comparison", projectId, selectedScenarios.scenario1, selectedScenarios.scenario2],
    queryFn: () =>
      selectedScenarios.scenario1 && selectedScenarios.scenario2
        ? scheduleCompressionApi.compareScenarios(
            projectId,
            selectedScenarios.scenario1,
            selectedScenarios.scenario2
          )
        : null,
    enabled: !!projectId && !!selectedScenarios.scenario1 && !!selectedScenarios.scenario2,
  });

  return (
    <div className="space-y-6">
      <PageHeader title="Schedule Compression Tools" description="Analyze fast-tracking, crashing, and scenario comparisons" />

      {/* Tab Navigation */}
      <div className="flex gap-2 border-b">
        <button
          onClick={() => setActiveTab("fast-track")}
          className={`px-4 py-2 font-medium ${
            activeTab === "fast-track"
              ? "border-b-2 border-blue-600 text-accent"
              : "text-text-secondary hover:text-text-primary"
          }`}
        >
          Fast-Tracking
        </button>
        <button
          onClick={() => setActiveTab("crash")}
          className={`px-4 py-2 font-medium ${
            activeTab === "crash"
              ? "border-b-2 border-blue-600 text-accent"
              : "text-text-secondary hover:text-text-primary"
          }`}
        >
          Crashing
        </button>
        <button
          onClick={() => setActiveTab("scenarios")}
          className={`px-4 py-2 font-medium ${
            activeTab === "scenarios"
              ? "border-b-2 border-blue-600 text-accent"
              : "text-text-secondary hover:text-text-primary"
          }`}
        >
          Scenario Comparison
        </button>
      </div>

      {/* Fast-Tracking Section */}
      {activeTab === "fast-track" && (
        <FastTrackingSection mutation={fastTrackMutation} />
      )}

      {/* Crashing Section */}
      {activeTab === "crash" && (
        <CrashingSection mutation={crashMutation} money={money} moneyCompact={moneyCompact} />
      )}

      {/* Scenario Comparison Section */}
      {activeTab === "scenarios" && (
        <ScenarioComparisonSection
          scenarios={scenarios?.data || []}
          selectedScenarios={selectedScenarios}
          setSelectedScenarios={setSelectedScenarios}
          comparisonQuery={comparisonQuery}
        />
      )}
    </div>
  );
}

interface FastTrackingSectionProps {
  mutation: ReturnType<typeof useMutation<ApiResponse<CompressionAnalysisResponse>>>;
}

function FastTrackingSection({ mutation }: FastTrackingSectionProps) {
  return (
    <div className="bg-surface/50 rounded-lg border border-border shadow-sm">
      <div className="border-b border-border px-6 py-4">
        <h2 className="text-lg font-semibold">Fast-Tracking Analysis</h2>
        <p className="text-sm text-text-secondary">
          Identify activities with parallel execution potential by converting FS relationships to SS
        </p>
      </div>

      <div className="p-6 space-y-4">
        <Button
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="bg-accent hover:bg-accent-hover"
        >
          {mutation.isPending ? "Analyzing..." : "Analyze Fast-Tracking"}
        </Button>

        {mutation.data && (
          <div className="space-y-4">
            {/* Summary Cards */}
            <div className="grid grid-cols-3 gap-4">
              <KpiTile label="Original Duration" value={days(mutation.data.data?.originalDuration)} />
              <KpiTile
                label="Potential Savings"
                tone="success"
                value={days(mutation.data.data?.durationSaved)}
                delta={{ value: "saved", direction: "down" }}
              />
              <KpiTile label="Compressed Duration" tone="accent" value={days(mutation.data.data?.compressedDuration)} />
            </div>

            {/* Recommendations Table or Empty State */}
            {(mutation.data.data?.recommendations?.length ?? 0) > 0 ? (
              <SimpleTable
                columns={[
                  { accessorKey: "activityCode", header: "Activity Code", cell: ({ row }) => <span className="font-medium">{row.original.activityCode}</span> },
                  { accessorKey: "originalDuration", header: "Original Duration", cell: ({ row }) => `${row.original.originalDuration?.toFixed(1)} days` },
                  {
                    accessorKey: "durationSaved",
                    header: "Days Saved",
                    cell: ({ row }) => (
                      <span className="inline-block px-2 py-1 text-xs font-semibold bg-success/10 text-success rounded">
                        {row.original.durationSaved?.toFixed(1)} days
                      </span>
                    ),
                  },
                  { accessorKey: "reason", header: "Reason", cell: ({ row }) => <span className="text-sm text-text-secondary">{row.original.reason}</span> },
                ] as ColumnDef<CompressionRecommendation>[]}
                data={mutation.data.data?.recommendations ?? []}
              />
            ) : (
              <div className="rounded border border-border bg-surface/80 p-4 text-sm text-text-secondary">
                No fast-tracking opportunity — fast-tracking needs Finish-to-Start links between critical
                activities, and this project has none defined.
              </div>
            )}
          </div>
        )}

        {mutation.isError && (
          <div className="text-red-500 p-3 bg-danger/10 rounded border border-red-200">
            Failed to analyze fast-tracking. Please ensure a schedule has been calculated.
          </div>
        )}
      </div>
    </div>
  );
}

interface CrashingSectionProps {
  mutation: ReturnType<typeof useMutation<ApiResponse<CompressionAnalysisResponse>>>;
  money: ProjectCurrency["money"];
  moneyCompact: ProjectCurrency["moneyCompact"];
}

function CrashingSection({ mutation, money, moneyCompact }: CrashingSectionProps) {
  const d = mutation.data?.data;
  const noCompression = d?.durationSaved != null && d.durationSaved < 0.05;

  return (
    <div className="bg-surface/50 rounded-lg border border-border shadow-sm">
      <div className="border-b border-border px-6 py-4">
        <h2 className="text-lg font-semibold">Crashing Analysis</h2>
        <p className="text-sm text-text-secondary">
          Identify critical activities that can be compressed by adding resources
        </p>
      </div>

      <div className="p-6 space-y-4">
        <Button
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="bg-danger hover:bg-danger"
        >
          {mutation.isPending ? "Analyzing..." : "Analyze Crashing"}
        </Button>

        {mutation.data && (
          noCompression ? (
            <div className="space-y-4">
              <div className="max-w-xs">
                <KpiTile label="Current Finish" value={formatDate(d?.originalFinishDate)} />
              </div>
              <div className="rounded border border-border bg-surface/80 p-4 text-sm text-text-secondary">
                No further compression possible — the project finish is already at its limit (driven by completed work or activities already at their crash limit).
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              {/* Summary Cards */}
              <div className="grid grid-cols-4 gap-4">
                <KpiTile label="Current Finish" value={formatDate(d?.originalFinishDate)} />
                <KpiTile label="Achievable Finish" tone="accent" value={formatDate(d?.compressedFinishDate)} />
                <KpiTile label="Time Saved" tone="success" value={days(d?.durationSaved)} delta={{ value: "saved", direction: "down" }} />
                <KpiTile label="Additional Cost" tone="warning" value={d?.additionalCost != null ? moneyCompact(d.additionalCost) : "—"} />
              </div>

              <p className="text-xs text-text-muted">
                Crashing is applied across all the activities that drive the finish, then re-checked — so the total time saved reflects the real project finish, not the sum of the rows below.
              </p>

              {/* Recommendations Table */}
              {(d?.recommendations?.length ?? 0) > 0 && (
                <SimpleTable
                  columns={[
                    { accessorKey: "activityCode", header: "Activity Code", cell: ({ row }) => <span className="font-medium">{row.original.activityCode}</span> },
                    { accessorKey: "originalDuration", header: "Original Duration", cell: ({ row }) => `${row.original.originalDuration?.toFixed(1)} days` },
                    { accessorKey: "newDuration", header: "Crashed To", cell: ({ row }) => `${row.original.newDuration?.toFixed(1)} days` },
                    {
                      accessorKey: "durationSaved",
                      header: "Days Crashed",
                      cell: ({ row }) => (
                        <span className="inline-block px-2 py-1 text-xs font-semibold bg-danger/10 text-danger rounded">
                          {row.original.durationSaved?.toFixed(1)} days
                        </span>
                      ),
                    },
                    {
                      accessorKey: "additionalCost",
                      header: "Cost",
                      cell: ({ row }) =>
                        row.original.additionalCost != null
                          ? money(row.original.additionalCost)
                          : "—",
                    },
                    { accessorKey: "reason", header: "Reason", cell: ({ row }) => <span className="text-sm text-text-secondary">{row.original.reason}</span> },
                  ] as ColumnDef<CompressionRecommendation>[]}
                  data={d?.recommendations ?? []}
                />
              )}
            </div>
          )
        )}

        {mutation.isError && (
          <div className="text-red-500 p-3 bg-danger/10 rounded border border-red-200">
            Failed to analyze crashing. Please ensure a schedule has been calculated.
          </div>
        )}
      </div>
    </div>
  );
}

interface ScenarioComparisonSectionProps {
  scenarios: ScheduleScenarioResponse[];
  selectedScenarios: {
    scenario1: string;
    scenario2: string;
  };
  setSelectedScenarios: (value: {
    scenario1: string;
    scenario2: string;
  }) => void;
  comparisonQuery: UseQueryResult<ApiResponse<ScenarioComparisonResponse> | null, unknown>;
}

function ScenarioComparisonSection({
  scenarios,
  selectedScenarios,
  setSelectedScenarios,
  comparisonQuery,
}: ScenarioComparisonSectionProps) {
  return (
    <div className="bg-surface/50 rounded-lg border border-border shadow-sm">
      <div className="border-b border-border px-6 py-4">
        <h2 className="text-lg font-semibold">Scenario Comparison</h2>
        <p className="text-sm text-text-secondary">
          Compare two scenarios to evaluate their impacts on project duration and cost
        </p>
      </div>

      <div className="p-6 space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-2">
              Scenario 1
            </label>
            <SearchableSelect
              value={selectedScenarios.scenario1}
              onChange={(val) =>
                setSelectedScenarios({
                  ...selectedScenarios,
                  scenario1: val,
                })
              }
              placeholder="Search scenarios..."
              options={(scenarios?.map((s: ScheduleScenarioResponse) => ({
                value: s.id,
                label: s.scenarioName || s.scenarioName,
              })) || [])}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-text-secondary mb-2">
              Scenario 2
            </label>
            <SearchableSelect
              value={selectedScenarios.scenario2}
              onChange={(val) =>
                setSelectedScenarios({
                  ...selectedScenarios,
                  scenario2: val,
                })
              }
              placeholder="Search scenarios..."
              options={(scenarios?.map((s: ScheduleScenarioResponse) => ({
                value: s.id,
                label: s.scenarioName || s.scenarioName,
              })) || [])}
            />
          </div>
        </div>

        {/* Comparison Results */}
        {comparisonQuery.data && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <KpiTile
                label={comparisonQuery.data.data?.scenario1Name ?? "Scenario 1"}
                value={days(comparisonQuery.data.data?.duration1)}
              />
              <KpiTile
                label={comparisonQuery.data.data?.scenario2Name ?? "Scenario 2"}
                tone="accent"
                value={days(comparisonQuery.data.data?.duration2)}
              />
            </div>

            <div className="border-2 border-green-500 bg-success/10 rounded-lg p-6">
              <div className="text-sm text-text-secondary mb-2">Duration Difference</div>
              <div className="text-3xl font-bold text-success">
                {(comparisonQuery.data.data?.durationDifference || 0) > 0 ? "+" : ""}
                {comparisonQuery.data.data?.durationDifference?.toFixed(1)} days
              </div>
            </div>

            {(comparisonQuery.data?.data?.activitiesChanged ?? 0) > 0 && (
              <div className="bg-surface/80 p-4 rounded border border-border">
                <p className="text-sm text-text-secondary">
                  <strong>{comparisonQuery.data?.data?.activitiesChanged}</strong> activities changed between scenarios
                </p>
              </div>
            )}
          </div>
        )}

        {comparisonQuery.isError && (
          <div className="text-red-500 p-3 bg-danger/10 rounded border border-red-200">
            Failed to load scenarios. Please ensure scenarios have been created.
          </div>
        )}

        {!comparisonQuery.data && selectedScenarios.scenario1 && selectedScenarios.scenario2 && (
          <div className="text-center text-text-muted py-8">
            Loading comparison...
          </div>
        )}
      </div>
    </div>
  );
}
