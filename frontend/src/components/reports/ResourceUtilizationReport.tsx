"use client";

import { useMemo } from "react";
import { Users } from "lucide-react";
import { Progress } from "@/components/ui/progress";
import type { ResourceUtilizationData } from "@/lib/api/reportDataApi";

interface ResourceUtilizationReportProps {
  data: ResourceUtilizationData;
}

export function ResourceUtilizationReport({ data }: ResourceUtilizationReportProps) {
  const utilizationStatus = useMemo(() => {
    if (data.avgUtilization >= 80) return { status: "Optimal", color: "bg-emerald-500/10 text-emerald-400" };
    if (data.avgUtilization >= 60) return { status: "Good", color: "bg-blue-500/10 text-blue-700" };
    if (data.avgUtilization >= 40) return { status: "Fair", color: "bg-amber-500/10 text-amber-400" };
    return { status: "Low", color: "bg-red-500/10 text-red-400" };
  }, [data.avgUtilization]);

  const resourcesByType = useMemo(() => {
    const types = new Map<string, number>();
    data.resources.forEach((r) => {
      types.set(r.type, (types.get(r.type) || 0) + 1);
    });
    return Array.from(types.entries()).map(([type, count]) => ({ type, count }));
  }, [data.resources]);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-gradient-to-r from-purple-50 to-blue-50 p-4 rounded-lg border border-purple-200">
        <div className="flex justify-between items-start">
          <div>
            <h3 className="font-semibold text-lg text-white">{data.projectName}</h3>
            <p className="text-sm text-slate-400">Resource Utilization Analysis</p>
          </div>
          <div className={`px-3 py-1 rounded-full text-sm font-semibold ${utilizationStatus.color}`}>
            {utilizationStatus.status}
          </div>
        </div>
      </div>

      {/* Overall Utilization Gauge */}
      <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-6">
        <div className="text-center mb-6">
          <p className="text-slate-400 mb-2">Average Utilization</p>
          <p className="text-5xl font-bold text-purple-600">{data.avgUtilization.toFixed(1)}%</p>
        </div>
        <Progress value={data.avgUtilization} className="h-4" />
        <div className="flex justify-between text-xs text-slate-500 mt-2">
          <span>0%</span>
          <span>50%</span>
          <span>100%</span>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
        <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
          <p className="text-xs text-slate-500 uppercase tracking-wider">Total Resources</p>
          <p className="text-3xl font-bold text-white">{data.totalResources}</p>
        </div>

        <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
          <p className="text-xs text-slate-500 uppercase tracking-wider">Fully Utilized</p>
          <p className="text-3xl font-bold text-emerald-400">
            {data.resources.filter((r) => r.utilPct >= 80).length}
          </p>
        </div>

        <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
          <p className="text-xs text-slate-500 uppercase tracking-wider">Underutilized</p>
          <p className="text-3xl font-bold text-orange-600">
            {data.resources.filter((r) => r.utilPct < 60).length}
          </p>
        </div>
      </div>

      {/* Resources by Type */}
      {resourcesByType.length > 0 && (
        <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
          <h4 className="font-semibold text-white mb-4 flex items-center gap-2">
            <Users size={20} />
            Resources by Type
          </h4>
          <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
            {resourcesByType.map((rt, idx) => (
              <div key={idx} className="bg-slate-900/80 rounded-lg p-3 text-center">
                <p className="text-sm text-slate-400">{rt.type}</p>
                <p className="text-2xl font-bold text-white">{rt.count}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Resource Utilization Details */}
      <div className="bg-slate-900/50 border border-slate-800 rounded-lg p-4">
        <h4 className="font-semibold text-white mb-4">Individual Resource Utilization</h4>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800">
                <th className="text-left py-3 px-2 text-slate-400 font-semibold">Code</th>
                <th className="text-left py-3 px-2 text-slate-400 font-semibold">Name</th>
                <th className="text-left py-3 px-2 text-slate-400 font-semibold">Type</th>
                <th className="text-right py-3 px-2 text-slate-400 font-semibold">Planned Hours</th>
                <th className="text-right py-3 px-2 text-slate-400 font-semibold">Actual Hours</th>
                <th className="text-center py-3 px-2 text-slate-400 font-semibold">Utilization</th>
              </tr>
            </thead>
            <tbody>
              {data.resources.map((resource, idx) => (
                <tr key={idx} className="border-b border-slate-800/50 hover:bg-slate-900/80">
                  <td className="py-3 px-2 font-mono text-white">{resource.code}</td>
                  <td className="py-3 px-2 text-slate-300">{resource.name}</td>
                  <td className="py-3 px-2 text-slate-400">{resource.type}</td>
                  <td className="py-3 px-2 text-right text-slate-300">{resource.plannedHours.toFixed(1)}</td>
                  <td className="py-3 px-2 text-right text-slate-300">{resource.actualHours.toFixed(1)}</td>
                  <td className="py-3 px-2">
                    <div className="flex items-center justify-center gap-2">
                      <Progress value={resource.utilPct} className="h-2 w-24" />
                      <span className={`text-xs font-semibold ${
                        resource.utilPct >= 80
                          ? "text-emerald-400"
                          : resource.utilPct >= 60
                          ? "text-blue-400"
                          : "text-orange-600"
                      }`}>
                        {resource.utilPct.toFixed(0)}%
                      </span>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Recommendations */}
      <div className="bg-blue-500/10 border border-blue-200 rounded-lg p-4">
        <h4 className="font-semibold text-white mb-3">Insights & Recommendations</h4>
        <ul className="space-y-2 text-sm text-slate-300">
          {data.avgUtilization < 60 && (
            <li>• Average utilization is below optimal levels. Consider reallocating resources or reviewing project scope.</li>
          )}
          {data.resources.filter((r) => r.utilPct > 100).length > 0 && (
            <li>• {data.resources.filter((r) => r.utilPct > 100).length} resources are over-allocated. Consider hiring additional resources or extending timelines.</li>
          )}
          {data.resources.filter((r) => r.utilPct < 40).length > 0 && (
            <li>• {data.resources.filter((r) => r.utilPct < 40).length} resources are significantly underutilized. Consider redistributing work or releasing resources.</li>
          )}
          {data.avgUtilization >= 80 && (
            <li>• Resource utilization is at optimal levels. Maintain current staffing and allocation strategy.</li>
          )}
        </ul>
      </div>
    </div>
  );
}
