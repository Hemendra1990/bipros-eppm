"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useParams, useSearchParams, useRouter, usePathname } from "next/navigation";
import { ChevronDown } from "lucide-react";
import { projectApi } from "@/lib/api/projectApi";
import { cn } from "@/lib/utils/cn";

export default function ProjectDetailLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const params = useParams();
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const projectId = params.projectId as string;
  const activeTab = searchParams.get("tab") || "overview";
  const [moreDropdownOpen, setMoreDropdownOpen] = useState(false);

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

  // Tab-based navigation (query parameter)
  const tabs = [
    { id: "overview", label: "Overview", href: null },
    { id: "wbs", label: "WBS", href: null },
    { id: "activities", label: "Activities", href: null },
    { id: "gantt", label: "Gantt", href: null },
    { id: "resources", label: "Resources", href: null },
    { id: "costs", label: "Costs", href: null },
    { id: "evm", label: "EVM", href: null },
    // These navigate to separate route pages:
    { id: "contracts", label: "Contracts", href: `/projects/${projectId}/contracts` },
    { id: "documents", label: "Documents", href: `/projects/${projectId}/documents` },
    { id: "gis", label: "GIS", href: `/projects/${projectId}/gis-viewer` },
  ];

  const moreLinks = [
    { label: "Schedule Health", href: `/projects/${projectId}/schedule-health` },
    { label: "Schedule Compression", href: `/projects/${projectId}/schedule-compression` },
    { label: "Risk Analysis", href: `/projects/${projectId}/risk-analysis` },
    { label: "Predictions", href: `/projects/${projectId}/predictions` },
    { label: "RA Bills", href: `/projects/${projectId}/ra-bills` },
    { label: "Drawings", href: `/projects/${projectId}/drawings` },
    { label: "RFIs", href: `/projects/${projectId}/rfis` },
    { label: "Equipment Logs", href: `/projects/${projectId}/equipment-logs` },
    { label: "Labour Returns", href: `/projects/${projectId}/labour-returns` },
    { label: "Materials", href: `/projects/${projectId}/material-reconciliation` },
  ];

  // Determine if a tab is active
  const isTabActive = (tab: typeof tabs[0]): boolean => {
    if (tab.href) {
      // For href-based tabs, check if pathname matches
      return pathname.includes(tab.href);
    } else {
      // For query-based tabs, check the query parameter
      return activeTab === tab.id;
    }
  };

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{project.name}</h1>
        <p className="text-sm text-gray-600">{project.code}</p>
      </div>

      <div className="border-b border-gray-200">
        <nav className="flex items-center gap-8" aria-label="Tabs">
          {tabs.map((t) => {
            const isActive = isTabActive(t);
            return (
              <button
                key={t.id}
                onClick={() => {
                  if (t.href) {
                    router.push(t.href);
                  } else {
                    router.push(`/projects/${projectId}?tab=${t.id}`);
                  }
                }}
                className={cn(
                  "px-1 py-4 text-sm font-medium border-b-2 transition-colors cursor-pointer",
                  isActive
                    ? "border-blue-500 text-blue-600"
                    : "border-transparent text-gray-600 hover:text-gray-900 hover:border-gray-300"
                )}
              >
                {t.label}
              </button>
            );
          })}

          {/* More Dropdown */}
          <div className="relative">
            <button
              onClick={() => setMoreDropdownOpen(!moreDropdownOpen)}
              className="flex items-center gap-1 px-1 py-4 text-sm font-medium border-b-2 border-transparent text-gray-600 hover:text-gray-900 transition-colors cursor-pointer"
            >
              More
              <ChevronDown
                size={16}
                className={cn(
                  "transition-transform duration-200",
                  moreDropdownOpen && "rotate-180"
                )}
              />
            </button>

            {moreDropdownOpen && (
              <div className="absolute right-0 mt-0 w-48 bg-white border border-gray-200 rounded-md shadow-lg z-10">
                {moreLinks.map((link) => (
                  <button
                    key={link.href}
                    onClick={() => {
                      router.push(link.href);
                      setMoreDropdownOpen(false);
                    }}
                    className="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 hover:text-gray-900 first:rounded-t-md last:rounded-b-md transition-colors"
                  >
                    {link.label}
                  </button>
                ))}
              </div>
            )}
          </div>
        </nav>
      </div>

      <div className="mt-6">{children}</div>
    </div>
  );
}
