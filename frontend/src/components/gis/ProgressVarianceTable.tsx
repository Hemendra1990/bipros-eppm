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
        return "bg-red-950 text-danger";
      case "AHEAD":
        return "bg-blue-950 text-blue-300";
      default:
        return "bg-surface-hover text-text-secondary";
    }
  };

  const getVarianceColor = (variance?: number) => {
    if (!variance) return "text-text-secondary";
    if (variance > 10) return "text-danger font-bold";
    if (variance < -5) return "text-success font-bold";
    return "text-text-primary";
  };

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h3 className="text-lg font-semibold text-text-primary">
          Progress Variance Analysis
        </h3>
        <p className="text-sm text-text-secondary">
          Comparing derived vs claimed progress
        </p>
      </div>

      {variance.length === 0 ? (
        <div className="bg-surface/50 rounded-lg border border-border p-8 text-center">
          <p className="text-text-secondary">No progress data available</p>
        </div>
      ) : (
        <div className="bg-surface/50 rounded-lg border border-border overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-surface/80 border-b border-border">
              <tr>
                <th className="px-6 py-3 text-left font-medium text-text-secondary">
                  WBS Code
                </th>
                <th className="px-6 py-3 text-left font-medium text-text-secondary">
                  WBS Name
                </th>
                <th className="px-6 py-3 text-center font-medium text-text-secondary">
                  Derived %
                </th>
                <th className="px-6 py-3 text-center font-medium text-text-secondary">
                  Claimed %
                </th>
                <th className="px-6 py-3 text-center font-medium text-text-secondary">
                  Variance %
                </th>
                <th className="px-6 py-3 text-center font-medium text-text-secondary">
                  Status
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {variance.map((v) => (
                <tr key={v.wbsPolygonId} className="hover:bg-surface-hover/30">
                  <td className="px-6 py-3 font-mono text-text-primary">
                    {v.wbsCode}
                  </td>
                  <td className="px-6 py-3 text-text-secondary">{v.wbsName}</td>
                  <td className="px-6 py-3 text-center text-text-primary">
                    {v.derivedPercent !== null &&
                    v.derivedPercent !== undefined
                      ? v.derivedPercent.toFixed(1) + "%"
                      : "-"}
                  </td>
                  <td className="px-6 py-3 text-center text-text-primary">
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
      <div className="bg-blue-950 border border-blue-700 rounded-lg p-4 text-sm text-text-secondary">
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
            <span className="font-medium text-success">AHEAD:</span> Variance
            &lt; -5% (ahead of contractor claims)
          </li>
          <li>
            <span className="font-medium text-warning">ON_TRACK:</span>{" "}
            Variance between -5% and +10%
          </li>
          <li>
            <span className="font-medium text-danger">BEHIND:</span> Variance
            &gt; +10% (behind contractor claims)
          </li>
        </ul>
      </div>
    </div>
  );
}
