"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { portfolioApi } from "@/lib/api/portfolioApi";
import { projectApi } from "@/lib/api/projectApi";
import type { PortfolioResponse, PortfolioProjectResponse, ProjectResponse } from "@/lib/types";
import { ArrowLeft, Plus, Calculator } from "lucide-react";
import Link from "next/link";

type TabType = "projects" | "scoring" | "scenarios";

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

  useEffect(() => {
    loadPortfolioData();
  }, [portfolioId]);

  const loadPortfolioData = async () => {
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
  };

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
    return <div className="text-center text-gray-500">Loading portfolio...</div>;
  }

  if (!portfolio) {
    return <div className="text-center text-gray-500">Portfolio not found</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Link
          href="/portfolios"
          className="flex items-center gap-1 rounded px-2 py-1 text-sm text-gray-600 hover:bg-gray-100"
        >
          <ArrowLeft size={18} />
          Back
        </Link>
        <div>
          <h1 className="text-3xl font-bold text-gray-900">{portfolio.name}</h1>
          <p className="text-sm text-gray-500">{portfolio.code}</p>
        </div>
      </div>

      {error && (
        <div className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <div className="border-b border-gray-200">
        <div className="flex gap-4">
          {(["projects", "scoring", "scenarios"] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-2 font-medium transition-colors ${
                activeTab === tab
                  ? "border-b-2 border-blue-600 text-blue-600"
                  : "text-gray-600 hover:text-gray-900"
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
            <h2 className="text-lg font-semibold text-gray-900">Portfolio Projects</h2>
            <button
              onClick={() => setShowAddProject(!showAddProject)}
              className="flex items-center gap-2 rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
            >
              <Plus size={16} />
              Add Project
            </button>
          </div>

          {showAddProject && (
            <div className="rounded-lg border border-gray-200 bg-white p-4">
              <div className="flex gap-2">
                <select
                  value={selectedProjectId}
                  onChange={(e) => setSelectedProjectId(e.target.value)}
                  className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="">Select a project...</option>
                  {allProjects
                    .filter((p) => !projects.some((pp) => pp.projectId === p.id))
                    .map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.code} - {p.name}
                      </option>
                    ))}
                </select>
                <button
                  onClick={handleAddProject}
                  className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
                >
                  Add
                </button>
                <button
                  onClick={() => setShowAddProject(false)}
                  className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                >
                  Cancel
                </button>
              </div>
            </div>
          )}

          {projects.length === 0 ? (
            <div className="rounded-lg border border-gray-200 bg-white p-8 text-center">
              <p className="text-gray-500">No projects in this portfolio yet.</p>
            </div>
          ) : (
            <div className="space-y-2">
              {projects.map((project) => (
                <div
                  key={project.projectId}
                  className="flex items-center justify-between rounded-lg border border-gray-200 bg-white p-4"
                >
                  <div>
                    <p className="font-medium text-gray-900">{project.projectName}</p>
                    <p className="text-sm text-gray-500">{project.projectCode}</p>
                  </div>
                  <button
                    onClick={() => handleRemoveProject(project.projectId)}
                    className="rounded px-2 py-1 text-sm text-red-600 hover:bg-red-50"
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
            <h2 className="text-lg font-semibold text-gray-900">Scoring & Ranking</h2>
            <button
              onClick={handleCalculateRanking}
              className="flex items-center gap-2 rounded-md bg-green-600 px-3 py-2 text-sm font-medium text-white hover:bg-green-700"
            >
              <Calculator size={16} />
              Calculate Ranking
            </button>
          </div>

          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <p className="text-gray-600">
              Scoring criteria and project rankings will be displayed here.
            </p>
          </div>
        </div>
      )}

      {activeTab === "scenarios" && (
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-gray-900">Scenarios</h2>

          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <p className="text-gray-600">Portfolio scenarios will be displayed here.</p>
          </div>
        </div>
      )}
    </div>
  );
}
