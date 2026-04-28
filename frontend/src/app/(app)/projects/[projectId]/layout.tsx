"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useParams, useSearchParams, useRouter, usePathname } from "next/navigation";
import { ChevronDown, Database } from "lucide-react";
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
  // Check if we're on a sub-route (not the base project page with ?tab= params)
  const isOnSubRoute = pathname !== `/projects/${projectId}` && !pathname.endsWith(`/projects/${projectId}`);
  const activeTab = isOnSubRoute ? null : (searchParams.get("tab") || "overview");
  const [moreDropdownOpen, setMoreDropdownOpen] = useState(false);
  const [masterDataOpen, setMasterDataOpen] = useState(false);

  const { data: projectData, isLoading } = useQuery({
    queryKey: ["project", projectId],
    queryFn: () => projectApi.getProject(projectId),
  });

  const project = projectData?.data;

  if (isLoading) {
    return <div className="p-6 text-center text-text-muted">Loading...</div>;
  }

  if (!project) {
    return <div className="p-6 text-center text-danger">Project not found</div>;
  }

  // Tab-based navigation (query parameter)
  const tabs = [
    { id: "overview", label: "Overview", href: null },
    { id: "wbs", label: "WBS", href: null },
    { id: "activities", label: "Activities", href: `/projects/${projectId}/activities` },
    { id: "gantt", label: "Gantt", href: null },
    { id: "network", label: "Network", href: null },
    { id: "baselines", label: "Baselines", href: null },
    { id: "resources", label: "Resources", href: null },
    { id: "costs", label: "Costs", href: null },
    { id: "evm", label: "EVM", href: null },
    { id: "dpr", label: "DPR", href: `/projects/${projectId}/dpr` },
    { id: "daily-outputs", label: "Daily Outputs", href: `/projects/${projectId}/daily-outputs` },
    { id: "capacity", label: "Capacity Util.", href: `/projects/${projectId}/capacity-utilization` },
    { id: "risks", label: "Risks", href: `/projects/${projectId}/risks` },
    // These navigate to separate route pages:
    { id: "contracts", label: "Contracts", href: `/projects/${projectId}/contracts` },
    { id: "documents", label: "Documents", href: `/projects/${projectId}/documents` },
    { id: "gis", label: "GIS", href: `/projects/${projectId}/gis-viewer` },
  ];

  // PMS Master Data — the 5 project-scoped reference entities from TESTING_GUIDE.md §2
  const masterDataLinks = [
    { label: "BOQ & Budget", href: `/projects/${projectId}/boq` },
    { label: "Stretches", href: `/projects/${projectId}/stretches` },
    { label: "Material Sources", href: `/projects/${projectId}/material-sources` },
    { label: "Material Catalogue", href: `/projects/${projectId}/materials` },
    { label: "Stock Register", href: `/projects/${projectId}/stock-register` },
  ];

  const moreLinks = [
    { label: "Relationships", href: `/projects/${projectId}/relationships` },
    { label: "Daily Cost Report", href: `/projects/${projectId}/daily-cost-report` },
    { label: "Material Consumption", href: `/projects/${projectId}/material-consumption` },
    { label: "Material Reconciliation", href: `/projects/${projectId}/material-reconciliation` },
    { label: "Resource Deployment", href: `/projects/${projectId}/resource-deployment` },
    { label: "Weather Log", href: `/projects/${projectId}/weather-log` },
    { label: "Next Day Plan", href: `/projects/${projectId}/next-day-plan` },
    { label: "Schedule Health", href: `/projects/${projectId}/schedule-health` },
    { label: "Schedule Compression", href: `/projects/${projectId}/schedule-compression` },
    { label: "Risk Analysis", href: `/projects/${projectId}/risk-analysis` },
    { label: "Activity Correlations", href: `/projects/${projectId}/activity-correlations` },
    { label: "Predictions", href: `/projects/${projectId}/predictions` },
    { label: "RA Bills", href: `/projects/${projectId}/ra-bills` },
    { label: "Drawings", href: `/projects/${projectId}/drawings` },
    { label: "RFIs", href: `/projects/${projectId}/rfis` },
    { label: "Equipment Logs", href: `/projects/${projectId}/equipment-logs` },
    { label: "Labour Returns", href: `/projects/${projectId}/labour-returns` },
    { label: "GRNs", href: `/projects/${projectId}/grns` },
    { label: "Issues", href: `/projects/${projectId}/issues` },
  ];

  // Check if any dropdown link is active (check first so we can exclude them below)
  const isMasterDataActive = masterDataLinks.some((link) => pathname.includes(link.href));
  const isMoreActive = moreLinks.some((link) => pathname.includes(link.href));

  // Check if any href-based tab matches
  const isAnyHrefTabActive = tabs.some((t) => t.href && pathname.includes(t.href));

  // Determine if a tab is active
  const isTabActive = (tab: typeof tabs[0]): boolean => {
    if (tab.href) {
      return pathname.includes(tab.href);
    }
    // For query-based tabs on the base project page
    if (activeTab !== null) {
      return activeTab === tab.id;
    }
    // On a sub-route: check if the pathname contains /activities/, /activity-codes/ etc.
    // that should map back to the query-based tab
    if (isOnSubRoute && !isMasterDataActive && !isMoreActive && !isAnyHrefTabActive) {
      const subRouteSegment = pathname.replace(`/projects/${projectId}`, "").split("/")[1];
      if (tab.id === "activities" && (subRouteSegment === "activities" || subRouteSegment === "activity-codes")) return true;
    }
    return false;
  };

  return (
    <div className="min-w-0">
      <div className="mb-6 px-6 pt-6">
        <h1 className="text-2xl font-bold text-text-primary">{project.name}</h1>
        <p className="text-sm text-text-secondary">{project.code}</p>
      </div>

      <div className="border-b border-border px-6">
        <nav className="flex items-center gap-8" aria-label="Tabs">
          <div className="flex items-center gap-8 overflow-x-auto">
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
                    "px-1 py-4 text-sm font-medium border-b-2 transition-colors cursor-pointer shrink-0",
                    isActive
                      ? "border-accent text-accent"
                      : "border-transparent text-text-secondary hover:text-text-primary hover:border-border"
                  )}
                >
                  {t.label}
                </button>
              );
            })}
          </div>

          {/* Master Data Dropdown */}
          <div className="relative shrink-0">
            <button
              onClick={() => {
                setMasterDataOpen(!masterDataOpen);
                setMoreDropdownOpen(false);
              }}
              className={cn(
                "flex items-center gap-1.5 px-1 py-4 text-sm font-medium border-b-2 transition-colors cursor-pointer",
                isMasterDataActive
                  ? "border-accent text-accent"
                  : "border-transparent text-text-secondary hover:text-text-primary hover:border-border"
              )}
            >
              <Database size={15} strokeWidth={1.75} />
              Master data
              <ChevronDown
                size={16}
                className={cn(
                  "transition-transform duration-200",
                  masterDataOpen && "rotate-180"
                )}
              />
            </button>

            {masterDataOpen && (
              <div className="absolute right-0 mt-0 w-56 bg-surface border border-border rounded-md shadow-lg z-50">
                {masterDataLinks.map((link) => (
                  <button
                    key={link.href}
                    onClick={() => {
                      router.push(link.href);
                      setMasterDataOpen(false);
                    }}
                    className={cn(
                      "block w-full text-left px-4 py-2 text-sm first:rounded-t-md last:rounded-b-md transition-colors",
                      pathname.includes(link.href)
                        ? "bg-surface-hover/50 text-accent font-semibold"
                        : "text-text-secondary hover:bg-surface-hover/50 hover:text-text-primary"
                    )}
                  >
                    {link.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* More Dropdown */}
          <div className="relative shrink-0">
            <button
              onClick={() => {
                setMoreDropdownOpen(!moreDropdownOpen);
                setMasterDataOpen(false);
              }}
              className={cn(
                "flex items-center gap-1 px-1 py-4 text-sm font-medium border-b-2 transition-colors cursor-pointer",
                isMoreActive
                  ? "border-accent text-accent"
                  : "border-transparent text-text-secondary hover:text-text-primary hover:border-border"
              )}
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
              <div className="absolute right-0 mt-0 w-48 bg-surface border border-border rounded-md shadow-lg z-50">
                {moreLinks.map((link) => (
                  <button
                    key={link.href}
                    onClick={() => {
                      router.push(link.href);
                      setMoreDropdownOpen(false);
                    }}
                    className="block w-full text-left px-4 py-2 text-sm text-text-secondary hover:bg-surface-hover/50 hover:text-text-primary first:rounded-t-md last:rounded-b-md transition-colors"
                  >
                    {link.label}
                  </button>
                ))}
              </div>
            )}
          </div>
        </nav>
      </div>

      <div className="mt-6 min-w-0 overflow-x-auto">{children}</div>
    </div>
  );
}
