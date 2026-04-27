"use client";

import { useState, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { Save, RotateCcw } from "lucide-react";
import {
  riskApi,
  type RiskScoringMatrixCell,
  type RiskScoringConfig,
  type ScoringMethod,
} from "@/lib/api/riskApi";
import { apiClient } from "@/lib/api/client";
import { getErrorMessage } from "@/lib/utils/error";
import type { ApiResponse, PagedResponse, ProjectResponse } from "@/lib/types";

const SCORING_METHODS: { value: ScoringMethod; label: string; description: string }[] = [
  {
    value: "HIGHEST_IMPACT",
    label: "Highest Impact",
    description: "Uses the higher of cost and schedule impact scores",
  },
  {
    value: "AVERAGE_IMPACT",
    label: "Average Impact",
    description: "Averages the cost and schedule impact scores",
  },
  {
    value: "AVERAGE_INDIVIDUAL",
    label: "Average Individual",
    description: "Computes score per impact type, then averages",
  },
];

export default function RiskScoringMatrixAdminPage() {
  const queryClient = useQueryClient();
  const [selectedProjectId, setSelectedProjectId] = useState<string>("");
  const [matrixCells, setMatrixCells] = useState<RiskScoringMatrixCell[]>([]);
  const [hasChanges, setHasChanges] = useState(false);

  const { data: projectsData } = useQuery({
    queryKey: ["projects"],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<PagedResponse<ProjectResponse>>>(
        "/v1/projects?page=0&size=100"
      );
      return response.data.data?.content ?? [];
    },
  });

  const { data: matrixData, isLoading: matrixLoading } = useQuery({
    queryKey: ["risk-scoring-matrix", selectedProjectId],
    queryFn: () => riskApi.getScoringMatrix(selectedProjectId),
    enabled: !!selectedProjectId,
  });

  const { data: configData } = useQuery({
    queryKey: ["risk-scoring-config", selectedProjectId],
    queryFn: () => riskApi.getScoringConfig(selectedProjectId),
    enabled: !!selectedProjectId,
  });

  const updateMatrixMutation = useMutation({
    mutationFn: (cells: RiskScoringMatrixCell[]) =>
      riskApi.updateScoringMatrix(selectedProjectId, cells),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risk-scoring-matrix", selectedProjectId] });
      // Risk scores are derived from the matrix; invalidate so any open list/detail re-fetches.
      queryClient.invalidateQueries({ queryKey: ["risks", selectedProjectId] });
      queryClient.invalidateQueries({ queryKey: ["risk", selectedProjectId] });
      setHasChanges(false);
      toast.success("Scoring matrix updated");
    },
    onError: (err: unknown) => toast.error(getErrorMessage(err, "Failed to update matrix")),
  });

  const updateConfigMutation = useMutation({
    mutationFn: (scoringMethod: ScoringMethod) =>
      riskApi.updateScoringConfig(selectedProjectId, scoringMethod),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["risk-scoring-config", selectedProjectId] });
      queryClient.invalidateQueries({ queryKey: ["risks", selectedProjectId] });
      queryClient.invalidateQueries({ queryKey: ["risk", selectedProjectId] });
      toast.success("Scoring method updated");
    },
    onError: (err: unknown) => toast.error(getErrorMessage(err, "Failed to update config")),
  });

  const projects: ProjectResponse[] = Array.isArray(projectsData) ? projectsData : [];
  const config: RiskScoringConfig | undefined = configData?.data ?? undefined;

  // Initialize matrix cells when data loads
  useEffect(() => {
    if (matrixData?.data) {
      setMatrixCells(matrixData.data);
    }
  }, [matrixData]);

  const handleCellChange = (probIdx: number, impactIdx: number, value: string) => {
    const score = parseInt(value, 10) || 0;
    const newCells = [...matrixCells];
    const cellIndex = newCells.findIndex(
      (c) => c.probabilityValue === probIdx + 1 && c.impactValue === impactIdx + 1
    );
    if (cellIndex >= 0) {
      newCells[cellIndex] = { ...newCells[cellIndex], score };
    } else {
      newCells.push({
        projectId: selectedProjectId,
        probabilityValue: probIdx + 1,
        impactValue: impactIdx + 1,
        score,
      });
    }
    setMatrixCells(newCells);
    setHasChanges(true);
  };

  const getCellValue = (probIdx: number, impactIdx: number): number => {
    const cell = matrixCells.find(
      (c) => c.probabilityValue === probIdx + 1 && c.impactValue === impactIdx + 1
    );
    return cell?.score || 0;
  };

  const handleSave = () => {
    updateMatrixMutation.mutate(matrixCells);
  };

  const handleReset = () => {
    if (matrixData?.data) {
      setMatrixCells(matrixData.data);
      setHasChanges(false);
    }
  };

  const probLabels = ["1 - Very Low", "2 - Low", "3 - Medium", "4 - High", "5 - Very High"];
  const impactLabels = ["1 - Very Low", "2 - Low", "3 - Medium", "4 - High", "5 - Very High"];

  // Same RAG bands the backend uses (Risk.deriveRag): ≥20 crimson, ≥12 red, ≥6 amber, else green.
  function ragBg(score: number): string {
    if (score >= 20) return "bg-rose-700/30";
    if (score >= 12) return "bg-rose-500/20";
    if (score >= 6) return "bg-amber-500/20";
    if (score > 0) return "bg-emerald-600/15";
    return "";
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-text-primary">Risk Scoring Matrix</h1>
        <p className="text-sm text-text-secondary mt-1">
          Configure the Probability × Impact scoring matrix for each project. Scores are
          looked up from this matrix when calculating risk scores.
        </p>
      </div>

      {/* Project Selector */}
      <div className="rounded-xl border border-border bg-surface/50 p-4 shadow-lg">
        <label className="block text-sm font-medium text-text-secondary mb-2">Select Project</label>
        <select
          value={selectedProjectId}
          onChange={(e) => {
            setSelectedProjectId(e.target.value);
            setHasChanges(false);
          }}
          className="w-full max-w-md rounded-md border border-border bg-surface-hover px-3 py-2 text-sm text-text-primary focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
        >
          <option value="">-- Choose a project --</option>
          {projects.map((project) => (
            <option key={project.id} value={project.id}>
              {project.code} - {project.name}
            </option>
          ))}
        </select>
      </div>

      {selectedProjectId && (
        <>
          {/* Scoring Method */}
          <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
            <h2 className="text-lg font-semibold text-text-primary mb-4">Scoring Method</h2>
            <div className="grid grid-cols-3 gap-4">
              {SCORING_METHODS.map((method) => (
                <button
                  key={method.value}
                  onClick={() => updateConfigMutation.mutate(method.value)}
                  className={`p-4 rounded-lg border text-left transition-colors ${
                    config?.scoringMethod === method.value
                      ? "border-accent bg-accent/10"
                      : "border-border hover:border-accent/50"
                  }`}
                >
                  <div className="font-medium text-sm text-text-primary">{method.label}</div>
                  <div className="text-xs text-text-secondary mt-1">{method.description}</div>
                  {config?.scoringMethod === method.value && (
                    <div className="mt-2 text-xs font-semibold text-accent">Active</div>
                  )}
                </button>
              ))}
            </div>
          </div>

          {/* Matrix Grid */}
          <div className="rounded-xl border border-border bg-surface/50 p-6 shadow-lg">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-text-primary">Scoring Matrix</h2>
              <div className="flex gap-2">
                {hasChanges && (
                  <button
                    onClick={handleReset}
                    className="inline-flex items-center gap-1.5 rounded-md border border-border bg-surface/50 px-3 py-1.5 text-xs font-semibold text-text-secondary hover:bg-surface-hover/50"
                  >
                    <RotateCcw size={12} />
                    Reset
                  </button>
                )}
                <button
                  onClick={handleSave}
                  disabled={!hasChanges || updateMatrixMutation.isPending}
                  className="inline-flex items-center gap-1.5 rounded-md bg-accent px-3 py-1.5 text-xs font-semibold text-text-primary hover:bg-accent-hover disabled:opacity-50"
                >
                  <Save size={12} />
                  {updateMatrixMutation.isPending ? "Saving..." : "Save Matrix"}
                </button>
              </div>
            </div>

            {matrixLoading ? (
              <div className="text-center py-8 text-text-muted">Loading matrix...</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="border-collapse">
                  <thead>
                    <tr>
                      <th className="w-32 border border-border bg-surface-hover p-2 text-right text-xs font-medium text-text-secondary">
                        Probability →
                      </th>
                      {impactLabels.map((label, idx) => (
                        <th
                          key={idx}
                          className="w-28 border border-border bg-surface-hover p-2 text-center text-xs font-medium text-text-secondary"
                        >
                          {label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {probLabels.map((probLabel, probIdx) => (
                      <tr key={probIdx}>
                        <th className="border border-border bg-surface-hover p-2 text-left text-xs font-medium text-text-secondary">
                          {probLabel}
                        </th>
                        {impactLabels.map((_, impactIdx) => {
                          const score = getCellValue(probIdx, impactIdx);
                          return (
                            <td
                              key={impactIdx}
                              className={`border border-border p-1 text-center ${ragBg(score)}`}
                            >
                              <input
                                type="number"
                                min={0}
                                max={999}
                                value={score}
                                onChange={(e) => handleCellChange(probIdx, impactIdx, e.target.value)}
                                aria-label={`Score for probability ${probIdx + 1}, impact ${impactIdx + 1}`}
                                className="w-full px-2 py-1 text-center text-sm font-semibold text-text-primary bg-transparent border-0 focus:outline-none focus:ring-1 focus:ring-accent rounded"
                              />
                            </td>
                          );
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div className="mt-3 flex flex-wrap items-center gap-3 text-[11px] text-text-secondary">
                  <span className="uppercase tracking-wide text-text-muted">RAG bands</span>
                  <span className="inline-flex items-center gap-1">
                    <span className="inline-block h-3 w-4 rounded-sm bg-emerald-600/40" /> Green &lt; 6
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <span className="inline-block h-3 w-4 rounded-sm bg-amber-500/50" /> Amber 6–11
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <span className="inline-block h-3 w-4 rounded-sm bg-rose-500/50" /> Red 12–19
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <span className="inline-block h-3 w-4 rounded-sm bg-rose-700/60" /> Crimson ≥ 20
                  </span>
                </div>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
