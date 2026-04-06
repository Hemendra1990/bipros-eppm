"use client";

import { ProgressVariance } from "@/lib/api/gisApi";

interface ProgressVarianceTableProps {
  projectId: string;
  variance: ProgressVariance[];
}

export function ProgressVarianceTable({
  projectId,
  variance,
}: ProgressVarianceTableProps) {
  const getStatusColor = (status: string) => {
    switch (status) {
      case "ON_TRACK":
        return "bg-green-950 text-green-300";
      case "BEHIND":
        return "bg-red-950 text-red-300";
      case "AHEAD":
        return "bg-blue-950 text-blue-300";
      default:
        return "bg-slate-800 text-slate-300";
    }
  };

  const getVarianceColor = (variance?: number) => {
    if (!variance) return "text-slate-400";
    if (variance > 10) return "text-red-400 font-bold";
    if (variance < -5) return "text-green-400 font-bold";
    return "text-white";
  };

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h3 className="text-lg font-semibold text-white">
          Progress Variance Analysis
        </h3>
        <p className="text-sm text-slate-400">
          Comparing derived vs claimed progress
        </p>
      </div>

      {variance.length === 0 ? (
        <div className="bg-slate-900/50 rounded-lg border border-slate-800 p-8 text-center">
          <p className="text-slate-400">No progress data available</p>
        </div>
      ) : (
        <div className="bg-slate-900/50 rounded-lg border border-slate-800 overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-900/80 border-b border-slate-800">
              <tr>
                <th className="px-6 py-3 text-left font-medium text-slate-300">
                  WBS Code
                </th>
                <th className="px-6 py-3 text-left font-medium text-slate-300">
                  WBS Name
                </th>
                <th className="px-6 py-3 text-center font-medium text-slate-300">
                  Derived %
                </th>
                <th className="px-6 py-3 text-center font-medium text-slate-300">
                  Claimed %
                </th>
                <th className="px-6 py-3 text-center font-medium text-slate-300">
                  Variance %
                </th>
                <th className="px-6 py-3 text-center font-medium text-slate-300">
                  Status
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {variance.map((v) => (
                <tr key={v.wbsPolygonId} className="hover:bg-slate-800/30">
                  <td className="px-6 py-3 font-mono text-white">
                    {v.wbsCode}
                  </td>
                  <td className="px-6 py-3 text-slate-300">{v.wbsName}</td>
                  <td className="px-6 py-3 text-center text-white">
                    {v.derivedPercent !== null &&
                    v.derivedPercent !== undefined
                      ? v.derivedPercent.toFixed(1) + "%"
                      : "-"}
                  </td>
                  <td className="px-6 py-3 text-center text-white">
                    {v.claimedPercent !== null &&
                    v.claimedPercent !== undefined
                      ? v.claimedPercent.toFixed(1) + "%"
                      : "-"}
                  </td>
                  <td className={`px-6 py-3 text-center ${getVarianceColor(v.variancePercent)}`}>
                    {v.variancePercent !== null &&
                    v.variancePercent !== undefined
                      ? (v.variancePercent > 0 ? "+" : "") +
                        v.variancePercent.toFixed(1) +
                        "%"
                      : "-"}
                  </td>
                  <td className="px-6 py-3 text-center">
                    <span
                      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${getStatusColor(v.varianceStatus)}`}
                    >
                      {v.varianceStatus.replace(/_/g, " ")}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Legend */}
      <div className="bg-blue-950 border border-blue-700 rounded-lg p-4 text-sm text-slate-300">
        <p className="font-medium mb-2">Understanding Variance:</p>
        <ul className="space-y-1 text-xs">
          <li>
            <span className="font-medium">Derived %:</span> Progress calculated
            from satellite imagery
          </li>
          <li>
            <span className="font-medium">Claimed %:</span> Progress reported by
            contractor
          </li>
          <li>
            <span className="font-medium">Variance:</span> Derived minus Claimed
          </li>
          <li>
            <span className="font-medium text-green-400">AHEAD:</span> Variance
            &lt; -5% (ahead of contractor claims)
          </li>
          <li>
            <span className="font-medium text-amber-400">ON_TRACK:</span>{" "}
            Variance between -5% and +10%
          </li>
          <li>
            <span className="font-medium text-red-400">BEHIND:</span> Variance
            &gt; +10% (behind contractor claims)
          </li>
        </ul>
      </div>
    </div>
  );
}
