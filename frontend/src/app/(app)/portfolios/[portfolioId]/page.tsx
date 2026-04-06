"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { portfolioApi, type OptimizationResultResponse, type WhatIfResponse } from "@/lib/api/portfolioApi";
import { projectApi } from "@/lib/api/projectApi";
import type { PortfolioResponse, PortfolioProjectResponse, ProjectResponse } from "@/lib/types";
import { SearchableSelect } from "@/components/common/SearchableSelect";
import { ArrowLeft, Plus, Calculator, Zap, FlaskConical, GitCompare } from "lucide-react";
import Link from "next/link";

type TabType = "projects" | "scoring" | "optimization" | "scenarios";

export default function PortfolioDetailPage() {
  const params = useParams();
  const portfolioId = params.portfolioId as string;

  const [portfolio, setPortfolio] = useState<PortfolioResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [activeTab, setActiveTab] = useState<TabType>("projects");
  const [projects, setProjects] = useState<PortfolioProjectResponse[]>([]);
  const [allProjects, setAllProjects] = useState<ProjectResponse[]>([]);
  const [showAddProject, setShowAddProject] = useState(false);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [optimizationResult, setOptimizationResult] = useState<OptimizationResultResponse | null>(null);
  const [budgetLimit, setBudgetLimit] = useState("");
  const [optimizing, setOptimizing] = useState(false);
  const [whatIfResult, setWhatIfResult] = useState<WhatIfResponse | null>(null);
  const [whatIfProjectId, setWhatIfProjectId] = useState("");
  const [whatIfAction, setWhatIfAction] = useState<"add" | "remove">("remove");
  const [runningWhatIf, setRunningWhatIf] = useState(false);

  const loadPortfolioData = useCallback(async () => {
    try {
      setLoading(true);
      const [portfolioRes, projectsRes, allProjectsRes] = await Promise.all([
        portfolioApi.getPortfolio(portfolioId),
        portfolioApi.getPortfolioProjects(portfolioId),
        projectApi.listProjects(),
      ]);

      if (portfolioRes.data) setPortfolio(portfolioRes.data);
      if (projectsRes.data) setProjects(projectsRes.data);
      if (allProjectsRes.data?.content) setAllProjects(allProjectsRes.data.content);
    } catch {
      setError("Failed to load portfolio data");
    } finally {
      setLoading(false);
    }
  }, [portfolioId]);

  useEffect(() => {
    loadPortfolioData();
  }, [loadPortfolioData]);

  const handleAddProject = async () => {
    if (!selectedProjectId) {
      setError("Please select a project");
      return;
    }

    try {
      await portfolioApi.addProjectToPortfolio(portfolioId, selectedProjectId);
      setSelectedProjectId("");
      setShowAddProject(false);
      loadPortfolioData();
    } catch {
      setError("Failed to add project to portfolio");
    }
  };

  const handleRemoveProject = async (projectId: string) => {
    if (!confirm("Remove this project from the portfolio?")) return;

    try {
      await portfolioApi.removeProjectFromPortfolio(portfolioId, projectId);
      loadPortfolioData();
    } catch {
      setError("Failed to remove project");
    }
  };

  const handleCalculateRanking = async () => {
    try {
      await portfolioApi.calculatePortfolioRanking(portfolioId);
      setError("");
      // Reload data after calculation
      loadPortfolioData();
    } catch {
      setError("Failed to calculate ranking");
    }
  };

  if (loading) {
    return <div className="text-center text-slate-500">Loading portfolio...</div>;
  }

  if (!portfolio) {
    return <div className="text-center text-slate-500">Portfolio not found</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Link
          href="/portfolios"
          className="flex items-center gap-1 rounded px-2 py-1 text-sm text-slate-400 hover:bg-slate-800/50"
        >
          <ArrowLeft size={18} />
          Back
        </Link>
        <div>
          <h1 className="text-3xl font-bold text-white">{portfolio.name}</h1>
          <p className="text-sm text-slate-400">{portfolio.code}</p>
        </div>
      </div>

      {error && (
        <div className="rounded-md bg-red-500/10 p-3 text-sm text-red-400">
          {error}
        </div>
      )}

      <div className="border-b border-slate-800">
        <div className="flex gap-4">
          {(["projects", "scoring", "optimization", "scenarios"] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-2 font-medium transition-colors ${
                activeTab === tab
                  ? "border-b-2 border-blue-500 text-blue-400"
                  : "text-slate-400 hover:text-slate-200"
              }`}
            >
              {tab.charAt(0).toUpperCase() + tab.slice(1)}
            </button>
          ))}
        </div>
      </div>

      {activeTab === "projects" && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-white">Portfolio Projects</h2>
            <button
              onClick={() => setShowAddProject(!showAddProject)}
              className="flex items-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-500"
            >
              <Plus size={16} />
              Add Project
            </button>
          </div>

          {showAddProject && (
            <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-4 shadow-lg">
              <div className="flex gap-2">
                <div className="flex-1">
                  <SearchableSelect
                    value={selectedProjectId}
                    onChange={(val) => setSelectedProjectId(val)}
                    placeholder="Search projects..."
                    options={allProjects
                      .filter((p) => !projects.some((pp) => pp.projectId === p.id))
                      .map((p) => ({
                        value: p.id,
                        label: `${p.code} - ${p.name}`,
                      }))}
                  />
                </div>
                <button
                  onClick={handleAddProject}
                  className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500"
                >
                  Add
                </button>
                <button
                  onClick={() => setShowAddProject(false)}
                  className="rounded-md border border-slate-700 bg-slate-900/50 px-4 py-2 text-sm font-medium text-slate-300 hover:bg-slate-800/50"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}

          {projects.length === 0 ? (
            <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-8 text-center shadow-lg">
              <p className="text-slate-500">No projects in this portfolio yet.</p>
            </div>
          ) : (
            <div className="space-y-2">
              {projects.map((project) => (
                <div
                  key={project.projectId}
                  className="flex items-center justify-between rounded-xl border border-slate-800 bg-slate-900/50 p-4 shadow-lg"
                >
                  <div>
                    <p className="font-medium text-white">{project.projectName}</p>
                    <p className="text-sm text-slate-400">{project.projectCode}</p>
                  </div>
                  <button
                    onClick={() => handleRemoveProject(project.projectId)}
                    className="rounded px-2 py-1 text-sm text-red-400 hover:bg-red-500/10"
                  >
                    Remove
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {activeTab === "scoring" && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-white">Scoring & Ranking</h2>
            <button
              onClick={handleCalculateRanking}
              className="flex items-center gap-2 rounded-md bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-500"
            >
              <Calculator size={16} />
              Calculate Ranking
            </button>
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
            <p className="text-slate-400">
              Scoring criteria and project rankings will be displayed here.
            </p>
          </div>
        </div>
      )}

      {activeTab === "optimization" && (
        <div className="space-y-6">
          {/* Budget-Constrained Optimization */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
            <div className="flex items-center gap-2 mb-4">
              <Zap size={20} className="text-amber-400" />
              <h3 className="text-lg font-semibold text-white">Portfolio Optimization</h3>
            </div>
            <p className="mb-4 text-sm text-slate-400">
              Select the best combination of projects that maximizes weighted score within your budget constraint (greedy knapsack algorithm).
            </p>
            <div className="flex items-end gap-3 mb-4">
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Budget Limit</label>
                <input
                  type="number"
                  value={budgetLimit}
                  onChange={(e) => setBudgetLimit(e.target.value)}
                  placeholder="e.g. 1000000"
                  className="rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white placeholder-slate-500 focus:border-blue-500 focus:outline-none"
                />
              </div>
              <button
                onClick={async () => {
                  setOptimizing(true);
                  try {
                    const res = await portfolioApi.optimizePortfolio(portfolioId, {
                      budgetLimit: budgetLimit ? Number(budgetLimit) : undefined,
                      mandatoryProjectIds: [],
                    });
                    setOptimizationResult(res.data ?? null);
                  } catch {
                    setError("Optimization failed");
                  } finally {
                    setOptimizing(false);
                  }
                }}
                disabled={optimizing}
                className="flex items-center gap-2 rounded-md bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-500 disabled:bg-slate-700 disabled:text-slate-400"
              >
                <Zap size={16} />
                {optimizing ? "Optimizing..." : "Run Optimization"}
              </button>
            </div>

            {optimizationResult && (
              <div className="space-y-4">
                <div className="grid grid-cols-4 gap-3">
                  <div className="rounded-lg border border-slate-700 bg-slate-800/50 p-3">
                    <div className="text-xs text-slate-400">Selected</div>
                    <div className="mt-1 text-lg font-bold text-green-300">{optimizationResult.totalSelected}</div>
                  </div>
                  <div className="rounded-lg border border-slate-700 bg-slate-800/50 p-3">
                    <div className="text-xs text-slate-400">Excluded</div>
                    <div className="mt-1 text-lg font-bold text-red-300">{optimizationResult.totalExcluded}</div>
                  </div>
                  <div className="rounded-lg border border-slate-700 bg-slate-800/50 p-3">
                    <div className="text-xs text-slate-400">Total Score</div>
                    <div className="mt-1 text-lg font-bold text-white">{optimizationResult.totalScore.toFixed(2)}</div>
                  </div>
                  <div className="rounded-lg border border-slate-700 bg-slate-800/50 p-3">
                    <div className="text-xs text-slate-400">Remaining Budget</div>
                    <div className="mt-1 text-lg font-bold text-blue-300">
                      {optimizationResult.remainingBudget != null
                        ? optimizationResult.remainingBudget.toLocaleString()
                        : "N/A"}
                    </div>
                  </div>
                </div>

                {optimizationResult.messages.length > 0 && (
                  <div className="rounded-lg border border-slate-800 bg-slate-900/70 p-3 max-h-48 overflow-y-auto">
                    <h4 className="text-xs font-semibold text-slate-400 mb-2">Optimization Log</h4>
                    {optimizationResult.messages.map((msg, i) => (
                      <div key={i} className="text-xs text-slate-400 py-0.5">{msg}</div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* What-If Analysis */}
          <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
            <div className="flex items-center gap-2 mb-4">
              <FlaskConical size={20} className="text-purple-400" />
              <h3 className="text-lg font-semibold text-white">What-If Analysis</h3>
            </div>
            <p className="mb-4 text-sm text-slate-400">
              Simulate adding or removing a project to see the impact on portfolio score and budget.
            </p>
            <div className="flex items-end gap-3 mb-4">
              <div className="flex-1">
                <label className="block text-xs font-medium text-slate-400 mb-1">Project</label>
                <SearchableSelect
                  value={whatIfProjectId}
                  onChange={(val) => setWhatIfProjectId(val)}
                  placeholder="Select project..."
                  options={allProjects.map((p) => ({
                    value: p.id,
                    label: `${p.code} - ${p.name}`,
                  }))}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-400 mb-1">Action</label>
                <select
                  value={whatIfAction}
                  onChange={(e) => setWhatIfAction(e.target.value as "add" | "remove")}
                  className="rounded-md border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white focus:border-blue-500 focus:outline-none"
                >
                  <option value="add">Add Project</option>
                  <option value="remove">Remove Project</option>
                </select>
              </div>
              <button
                onClick={async () => {
                  if (!whatIfProjectId) return;
                  setRunningWhatIf(true);
                  try {
                    const res = await portfolioApi.whatIfAnalysis(portfolioId, {
                      projectId: whatIfProjectId,
                      addProject: whatIfAction === "add",
                      budgetLimit: budgetLimit ? Number(budgetLimit) : undefined,
                    });
                    setWhatIfResult(res.data ?? null);
                  } catch {
                    setError("What-if analysis failed");
                  } finally {
                    setRunningWhatIf(false);
                  }
                }}
                disabled={runningWhatIf || !whatIfProjectId}
                className="flex items-center gap-2 rounded-md bg-purple-600 px-4 py-2 text-sm font-medium text-white hover:bg-purple-500 disabled:bg-slate-700 disabled:text-slate-400"
              >
                <FlaskConical size={16} />
                {runningWhatIf ? "Analyzing..." : "Simulate"}
              </button>
            </div>

            {whatIfResult && (
              <div className="grid grid-cols-3 gap-3">
                <div className="rounded-lg border border-slate-700 bg-slate-800/50 p-3">
                  <div className="text-xs text-slate-400">Score Impact</div>
                  <div className={`mt-1 text-lg font-bold ${whatIfResult.scoreDelta >= 0 ? "text-green-300" : "text-red-300"}`}>
                    {whatIfResult.scoreDelta >= 0 ? "+" : ""}{whatIfResult.scoreDelta.toFixed(2)}
                  </div>
                  <div className="text-xs text-slate-500">
                    {whatIfResult.scoreBefore.toFixed(2)} → {whatIfResult.scoreAfter.toFixed(2)}
                  </div>
                </div>
                <div className="rounded-lg border border-slate-700 bg-slate-800/50 p-3">
                  <div className="text-xs text-slate-400">Budget Impact</div>
                  <div className={`mt-1 text-lg font-bold ${whatIfResult.budgetDelta <= 0 ? "text-green-300" : "text-yellow-300"}`}>
                    {whatIfResult.budgetDelta >= 0 ? "+" : ""}{whatIfResult.budgetDelta}
                  </div>
                </div>
                <div className="rounded-lg border border-slate-700 bg-slate-800/50 p-3">
                  <div className="text-xs text-slate-400">Within Budget</div>
                  <div className={`mt-1 text-lg font-bold ${whatIfResult.withinBudget ? "text-green-300" : "text-red-300"}`}>
                    {whatIfResult.withinBudget ? "Yes" : "No"}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {activeTab === "scenarios" && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-white">Scenarios</h2>
            <div className="flex gap-2">
              <button className="flex items-center gap-2 rounded-md border border-slate-700 bg-slate-800/50 px-3 py-2 text-sm font-medium text-slate-300 hover:bg-slate-700">
                <GitCompare size={16} />
                Compare Scenarios
              </button>
              <button className="flex items-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-500">
                <Plus size={16} />
                New Scenario
              </button>
            </div>
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-900/50 p-6 shadow-lg">
            <p className="text-slate-400">
              Create scenarios to model different project selections and compare their impact on portfolio performance.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
