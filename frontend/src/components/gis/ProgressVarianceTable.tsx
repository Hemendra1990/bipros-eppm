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
        return "bg-green-100 text-green-800";
      case "BEHIND":
        return "bg-red-100 text-red-800";
      case "AHEAD":
        return "bg-blue-100 text-blue-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const getVarianceColor = (variance?: number) => {
    if (!variance) return "text-gray-500";
    if (variance > 10) return "text-red-600 font-bold";
    if (variance < -5) return "text-green-600 font-bold";
    return "text-gray-900";
  };

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h3 className="text-lg font-semibold text-gray-900">
          Progress Variance Analysis
        </h3>
        <p className="text-sm text-gray-600">
          Comparing derived vs claimed progress
        </p>
      </div>

      {variance.length === 0 ? (
        <div className="bg-white rounded-lg border border-gray-200 p-8 text-center">
          <p className="text-gray-500">No progress data available</p>
        </div>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200 overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left font-medium text-gray-700">
                  WBS Code
                </th>
                <th className="px-6 py-3 text-left font-medium text-gray-700">
                  WBS Name
                </th>
                <th className="px-6 py-3 text-center font-medium text-gray-700">
                  Derived %
                </th>
                <th className="px-6 py-3 text-center font-medium text-gray-700">
                  Claimed %
                </th>
                <th className="px-6 py-3 text-center font-medium text-gray-700">
                  Variance %
                </th>
                <th className="px-6 py-3 text-center font-medium text-gray-700">
                  Status
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {variance.map((v) => (
                <tr key={v.wbsPolygonId} className="hover:bg-gray-50">
                  <td className="px-6 py-3 font-mono text-gray-900">
                    {v.wbsCode}
                  </td>
                  <td className="px-6 py-3 text-gray-600">{v.wbsName}</td>
                  <td className="px-6 py-3 text-center text-gray-900">
                    {v.derivedPercent !== null &&
                    v.derivedPercent !== undefined
                      ? v.derivedPercent.toFixed(1) + "%"
                      : "-"}
                  </td>
                  <td className="px-6 py-3 text-center text-gray-900">
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
      <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 text-sm text-gray-700">
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
            <span className="font-medium text-green-600">AHEAD:</span> Variance
            &lt; -5% (ahead of contractor claims)
          </li>
          <li>
            <span className="font-medium text-amber-600">ON_TRACK:</span>{" "}
            Variance between -5% and +10%
          </li>
          <li>
            <span className="font-medium text-red-600">BEHIND:</span> Variance
            &gt; +10% (behind contractor claims)
          </li>
        </ul>
      </div>
    </div>
  );
}
