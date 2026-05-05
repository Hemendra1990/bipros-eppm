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

export default function ScheduleCompressionPage() {
  const params = useParams();
  const projectId = params.projectId as string;

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
        <CrashingSection mutation={crashMutation} />
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
              <div className="bg-gradient-to-br from-blue-50 to-blue-100 rounded-lg p-6 border border-blue-200">
                <div className="text-2xl font-bold">
                  {mutation.data.data?.originalDuration?.toFixed(1)} days
                </div>
                <p className="text-sm text-text-secondary">Original Duration</p>
              </div>

              <div className="bg-gradient-to-br from-green-50 to-green-100 rounded-lg p-6 border border-green-200">
                <div className="text-2xl font-bold text-success">
                  -{mutation.data.data?.durationSaved?.toFixed(1)} days
                </div>
                <p className="text-sm text-text-secondary">Potential Savings</p>
              </div>

              <div className="bg-gradient-to-br from-purple-50 to-purple-100 rounded-lg p-6 border border-purple-200">
                <div className="text-2xl font-bold text-purple-600">
                  {mutation.data.data?.compressedDuration?.toFixed(1)} days
                </div>
                <p className="text-sm text-text-secondary">Compressed Duration</p>
              </div>
            </div>

            {/* Recommendations Table */}
            {(mutation.data.data?.recommendations?.length ?? 0) > 0 && (
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
                ]}
                data={mutation.data.data?.recommendations ?? []}
              />
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
}

function CrashingSection({ mutation }: CrashingSectionProps) {
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
          <div className="space-y-4">
            {/* Summary Cards */}
            <div className="grid grid-cols-4 gap-4">
              <div className="bg-gradient-to-br from-blue-50 to-blue-100 rounded-lg p-6 border border-blue-200">
                <div className="text-2xl font-bold">
                  {mutation.data.data?.originalDuration?.toFixed(1)} days
                </div>
                <p className="text-sm text-text-secondary">Original Duration</p>
              </div>

              <div className="bg-gradient-to-br from-red-50 to-red-100 rounded-lg p-6 border border-red-200">
                <div className="text-2xl font-bold text-danger">
                  -{mutation.data.data?.durationSaved?.toFixed(1)} days
                </div>
                <p className="text-sm text-text-secondary">Potential Savings</p>
              </div>

              <div className="bg-gradient-to-br from-yellow-50 to-yellow-100 rounded-lg p-6 border border-yellow-200">
                <div className="text-2xl font-bold text-warning">
                  ${mutation.data.data?.additionalCost?.toFixed(2)}
                </div>
                <p className="text-sm text-text-secondary">Additional Cost</p>
              </div>

              <div className="bg-gradient-to-br from-purple-50 to-purple-100 rounded-lg p-6 border border-purple-200">
                <div className="text-2xl font-bold text-purple-600">
                  {mutation.data.data?.compressedDuration?.toFixed(1)} days
                </div>
                <p className="text-sm text-text-secondary">Compressed Duration</p>
              </div>
            </div>

            {/* Recommendations Table */}
            {(mutation.data.data?.recommendations?.length ?? 0) > 0 && (
              <SimpleTable
                columns={[
                  { accessorKey: "activityCode", header: "Activity Code", cell: ({ row }) => <span className="font-medium">{row.original.activityCode}</span> },
                  { accessorKey: "originalDuration", header: "Original Duration", cell: ({ row }) => `${row.original.originalDuration?.toFixed(1)} days` },
                  { accessorKey: "newDuration", header: "Crashed Duration", cell: ({ row }) => `${row.original.newDuration?.toFixed(1)} days` },
                  {
                    accessorKey: "durationSaved",
                    header: "Days Saved",
                    cell: ({ row }) => (
                      <span className="inline-block px-2 py-1 text-xs font-semibold bg-danger/10 text-danger rounded">
                        {row.original.durationSaved?.toFixed(1)} days
                      </span>
                    ),
                  },
                  {
                    accessorKey: "additionalCost",
                    header: "Cost/Day",
                    cell: ({ row }) => (
                      <span>
                        ${row.original.additionalCost && row.original.durationSaved
                          ? (row.original.additionalCost / row.original.durationSaved).toFixed(2)
                          : "N/A"}
                      </span>
                    ),
                  },
                  { accessorKey: "reason", header: "Reason", cell: ({ row }) => <span className="text-sm text-text-secondary">{row.original.reason}</span> },
                ]}
                data={mutation.data.data?.recommendations ?? []}
              />
            )}
          </div>
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
              <div className="bg-gradient-to-br from-blue-50 to-blue-100 rounded-lg p-6 border border-blue-200">
                <div className="text-sm text-text-secondary mb-2">
                  {comparisonQuery.data.data?.scenario1Name}
                </div>
                <div className="text-2xl font-bold">
                  {comparisonQuery.data.data?.duration1?.toFixed(1)} days
                </div>
                <p className="text-xs text-text-muted mt-2">Duration</p>
              </div>

              <div className="bg-gradient-to-br from-purple-50 to-purple-100 rounded-lg p-6 border border-purple-200">
                <div className="text-sm text-text-secondary mb-2">
                  {comparisonQuery.data.data?.scenario2Name}
                </div>
                <div className="text-2xl font-bold">
                  {comparisonQuery.data.data?.duration2?.toFixed(1)} days
                </div>
                <p className="text-xs text-text-muted mt-2">Duration</p>
              </div>
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
