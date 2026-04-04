"use client";

import { useParams } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { PageHeader } from "@/components/common/PageHeader";
import { scheduleCompressionApi } from "@/lib/api/scheduleCompressionApi";
import { useState } from "react";
import { Button } from "@/components/ui/button";

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
              ? "border-b-2 border-blue-600 text-blue-600"
              : "text-gray-600 hover:text-gray-900"
          }`}
        >
          Fast-Tracking
        </button>
        <button
          onClick={() => setActiveTab("crash")}
          className={`px-4 py-2 font-medium ${
            activeTab === "crash"
              ? "border-b-2 border-blue-600 text-blue-600"
              : "text-gray-600 hover:text-gray-900"
          }`}
        >
          Crashing
        </button>
        <button
          onClick={() => setActiveTab("scenarios")}
          className={`px-4 py-2 font-medium ${
            activeTab === "scenarios"
              ? "border-b-2 border-blue-600 text-blue-600"
              : "text-gray-600 hover:text-gray-900"
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

function FastTrackingSection({ mutation }: any) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm">
      <div className="border-b border-gray-200 px-6 py-4">
        <h2 className="text-lg font-semibold">Fast-Tracking Analysis</h2>
        <p className="text-sm text-gray-600">
          Identify activities with parallel execution potential by converting FS relationships to SS
        </p>
      </div>

      <div className="p-6 space-y-4">
        <Button
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="bg-blue-600 hover:bg-blue-700"
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
                <p className="text-sm text-gray-600">Original Duration</p>
              </div>

              <div className="bg-gradient-to-br from-green-50 to-green-100 rounded-lg p-6 border border-green-200">
                <div className="text-2xl font-bold text-green-600">
                  -{mutation.data.data?.durationSaved?.toFixed(1)} days
                </div>
                <p className="text-sm text-gray-600">Potential Savings</p>
              </div>

              <div className="bg-gradient-to-br from-purple-50 to-purple-100 rounded-lg p-6 border border-purple-200">
                <div className="text-2xl font-bold text-purple-600">
                  {mutation.data.data?.compressedDuration?.toFixed(1)} days
                </div>
                <p className="text-sm text-gray-600">Compressed Duration</p>
              </div>
            </div>

            {/* Recommendations Table */}
            {mutation.data.data?.recommendations?.length > 0 && (
              <div className="overflow-x-auto border border-gray-200 rounded-lg">
                <table className="w-full">
                  <thead className="bg-gray-50 border-b border-gray-200">
                    <tr>
                      <th className="px-4 py-2 text-left text-sm font-semibold">Activity Code</th>
                      <th className="px-4 py-2 text-left text-sm font-semibold">Original Duration</th>
                      <th className="px-4 py-2 text-left text-sm font-semibold">Days Saved</th>
                      <th className="px-4 py-2 text-left text-sm font-semibold">Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mutation.data.data.recommendations.map((rec: any, idx: number) => (
                      <tr key={idx} className="border-b border-gray-200 hover:bg-gray-50">
                        <td className="px-4 py-3 font-medium">{rec.activityCode}</td>
                        <td className="px-4 py-3">{rec.originalDuration?.toFixed(1)} days</td>
                        <td className="px-4 py-3">
                          <span className="inline-block px-2 py-1 text-xs font-semibold bg-green-100 text-green-800 rounded">
                            {rec.durationSaved?.toFixed(1)} days
                          </span>
                        </td>
                        <td className="px-4 py-3 text-sm text-gray-600">{rec.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {mutation.isError && (
          <div className="text-red-500 p-3 bg-red-50 rounded border border-red-200">
            Failed to analyze fast-tracking. Please ensure a schedule has been calculated.
          </div>
        )}
      </div>
    </div>
  );
}

function CrashingSection({ mutation }: any) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm">
      <div className="border-b border-gray-200 px-6 py-4">
        <h2 className="text-lg font-semibold">Crashing Analysis</h2>
        <p className="text-sm text-gray-600">
          Identify critical activities that can be compressed by adding resources
        </p>
      </div>

      <div className="p-6 space-y-4">
        <Button
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="bg-red-600 hover:bg-red-700"
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
                <p className="text-sm text-gray-600">Original Duration</p>
              </div>

              <div className="bg-gradient-to-br from-red-50 to-red-100 rounded-lg p-6 border border-red-200">
                <div className="text-2xl font-bold text-red-600">
                  -{mutation.data.data?.durationSaved?.toFixed(1)} days
                </div>
                <p className="text-sm text-gray-600">Potential Savings</p>
              </div>

              <div className="bg-gradient-to-br from-yellow-50 to-yellow-100 rounded-lg p-6 border border-yellow-200">
                <div className="text-2xl font-bold text-yellow-600">
                  ${mutation.data.data?.additionalCost?.toFixed(2)}
                </div>
                <p className="text-sm text-gray-600">Additional Cost</p>
              </div>

              <div className="bg-gradient-to-br from-purple-50 to-purple-100 rounded-lg p-6 border border-purple-200">
                <div className="text-2xl font-bold text-purple-600">
                  {mutation.data.data?.compressedDuration?.toFixed(1)} days
                </div>
                <p className="text-sm text-gray-600">Compressed Duration</p>
              </div>
            </div>

            {/* Recommendations Table */}
            {mutation.data.data?.recommendations?.length > 0 && (
              <div className="overflow-x-auto border border-gray-200 rounded-lg">
                <table className="w-full">
                  <thead className="bg-gray-50 border-b border-gray-200">
                    <tr>
                      <th className="px-4 py-2 text-left text-sm font-semibold">Activity Code</th>
                      <th className="px-4 py-2 text-left text-sm font-semibold">Original Duration</th>
                      <th className="px-4 py-2 text-left text-sm font-semibold">Crashed Duration</th>
                      <th className="px-4 py-2 text-left text-sm font-semibold">Days Saved</th>
                      <th className="px-4 py-2 text-left text-sm font-semibold">Cost/Day</th>
                      <th className="px-4 py-2 text-left text-sm font-semibold">Reason</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mutation.data.data.recommendations.map((rec: any, idx: number) => (
                      <tr key={idx} className="border-b border-gray-200 hover:bg-gray-50">
                        <td className="px-4 py-3 font-medium">{rec.activityCode}</td>
                        <td className="px-4 py-3">{rec.originalDuration?.toFixed(1)} days</td>
                        <td className="px-4 py-3">{rec.newDuration?.toFixed(1)} days</td>
                        <td className="px-4 py-3">
                          <span className="inline-block px-2 py-1 text-xs font-semibold bg-red-100 text-red-800 rounded">
                            {rec.durationSaved?.toFixed(1)} days
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          ${rec.additionalCost && rec.durationSaved ? (rec.additionalCost / rec.durationSaved).toFixed(2) : "N/A"}
                        </td>
                        <td className="px-4 py-3 text-sm text-gray-600">{rec.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {mutation.isError && (
          <div className="text-red-500 p-3 bg-red-50 rounded border border-red-200">
            Failed to analyze crashing. Please ensure a schedule has been calculated.
          </div>
        )}
      </div>
    </div>
  );
}

function ScenarioComparisonSection({
  scenarios,
  selectedScenarios,
  setSelectedScenarios,
  comparisonQuery,
}: any) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm">
      <div className="border-b border-gray-200 px-6 py-4">
        <h2 className="text-lg font-semibold">Scenario Comparison</h2>
        <p className="text-sm text-gray-600">
          Compare two scenarios to evaluate their impacts on project duration and cost
        </p>
      </div>

      <div className="p-6 space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Scenario 1
            </label>
            <select
              value={selectedScenarios.scenario1}
              onChange={(e) =>
                setSelectedScenarios({
                  ...selectedScenarios,
                  scenario1: e.target.value,
                })
              }
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-blue-500"
            >
              <option value="">Select a scenario...</option>
              {scenarios?.map((s: any) => (
                <option key={s.id} value={s.id}>
                  {s.scenarioName}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Scenario 2
            </label>
            <select
              value={selectedScenarios.scenario2}
              onChange={(e) =>
                setSelectedScenarios({
                  ...selectedScenarios,
                  scenario2: e.target.value,
                })
              }
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-blue-500"
            >
              <option value="">Select a scenario...</option>
              {scenarios?.map((s: any) => (
                <option key={s.id} value={s.id}>
                  {s.scenarioName}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Comparison Results */}
        {comparisonQuery.data && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="bg-gradient-to-br from-blue-50 to-blue-100 rounded-lg p-6 border border-blue-200">
                <div className="text-sm text-gray-600 mb-2">
                  {comparisonQuery.data.data?.scenario1Name}
                </div>
                <div className="text-2xl font-bold">
                  {comparisonQuery.data.data?.duration1?.toFixed(1)} days
                </div>
                <p className="text-xs text-gray-500 mt-2">Duration</p>
              </div>

              <div className="bg-gradient-to-br from-purple-50 to-purple-100 rounded-lg p-6 border border-purple-200">
                <div className="text-sm text-gray-600 mb-2">
                  {comparisonQuery.data.data?.scenario2Name}
                </div>
                <div className="text-2xl font-bold">
                  {comparisonQuery.data.data?.duration2?.toFixed(1)} days
                </div>
                <p className="text-xs text-gray-500 mt-2">Duration</p>
              </div>
            </div>

            <div className="border-2 border-green-500 bg-green-50 rounded-lg p-6">
              <div className="text-sm text-gray-600 mb-2">Duration Difference</div>
              <div className="text-3xl font-bold text-green-600">
                {(comparisonQuery.data.data?.durationDifference || 0) > 0 ? "+" : ""}
                {comparisonQuery.data.data?.durationDifference?.toFixed(1)} days
              </div>
            </div>

            {comparisonQuery.data.data?.activitiesChanged > 0 && (
              <div className="bg-gray-50 p-4 rounded border border-gray-200">
                <p className="text-sm text-gray-600">
                  <strong>{comparisonQuery.data.data?.activitiesChanged}</strong> activities changed between scenarios
                </p>
              </div>
            )}
          </div>
        )}

        {comparisonQuery.isError && (
          <div className="text-red-500 p-3 bg-red-50 rounded border border-red-200">
            Failed to load scenarios. Please ensure scenarios have been created.
          </div>
        )}

        {!comparisonQuery.data && selectedScenarios.scenario1 && selectedScenarios.scenario2 && (
          <div className="text-center text-gray-500 py-8">
            Loading comparison...
          </div>
        )}
      </div>
    </div>
  );
}
