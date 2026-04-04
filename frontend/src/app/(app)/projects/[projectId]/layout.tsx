"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useParams } from "next/navigation";
import { projectApi } from "@/lib/api/projectApi";
import Link from "next/link";

export default function ProjectDetailLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const params = useParams();
  const projectId = params.projectId as string;
  const [activeTab, setActiveTab] = useState("overview");

  const { data: projectData, isLoading } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
  });

  const project = projectData?.data;

  if (isLoading) {
    return <div className="p-6 text-center text-gray-500">Loading...</div>;
  }

  if (!project) {
    return <div className="p-6 text-center text-red-500">Project not found</div>;
  }

  const tabs = [
    { id: "overview", label: "Overview" },
    { id: "wbs", label: "WBS" },
    { id: "activities", label: "Activities" },
    { id: "gantt", label: "Gantt" },
    { id: "resources", label: "Resources" },
    { id: "costs", label: "Costs" },
    { id: "evm", label: "EVM" },
  ];

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{project.name}</h1>
        <p className="text-sm text-gray-600">{project.code}</p>
      </div>

      <div className="border-b border-gray-200">
        <nav className="flex gap-8" aria-label="Tabs">
          {tabs.map((tab) => (
            <Link
              key={tab.id}
              href={`/projects/${projectId}?tab=${tab.id}`}
              onClick={(e) => {
                e.preventDefault();
                setActiveTab(tab.id);
              }}
              className={`px-1 py-4 text-sm font-medium border-b-2 transition-colors ${
                activeTab === tab.id
                  ? "border-blue-500 text-blue-600"
                  : "border-transparent text-gray-600 hover:text-gray-900 hover:border-gray-300"
              }`}
            >
              {tab.label}
            </Link>
          ))}
        </nav>
      </div>

      <div className="mt-6">{children}</div>
    </div>
  );
}
