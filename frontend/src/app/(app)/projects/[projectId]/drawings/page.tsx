"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { documentApi } from "@/lib/api/documentApi";

export default function DrawingsPage() {
  const params = useParams();
  const projectId = params.projectId as string;

  const { data: drawings = [] } = useQuery({
    queryKey: ["drawings", projectId],
    queryFn: () => documentApi.listDrawings(projectId),
    select: (response) => response.data || [],
  });

  const getStatusColor = (
    status: "PRELIMINARY" | "IFA" | "IFC" | "AS_BUILT" | "SUPERSEDED"
  ) => {
    switch (status) {
      case "PRELIMINARY":
        return "bg-yellow-100 text-yellow-800";
      case "IFA":
        return "bg-blue-100 text-blue-800";
      case "IFC":
        return "bg-green-100 text-green-800";
      case "AS_BUILT":
        return "bg-purple-100 text-purple-800";
      case "SUPERSEDED":
        return "bg-gray-100 text-gray-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const getDisciplineIcon = (discipline: string) => {
    const icons: Record<string, string> = {
      CIVIL: "🏗️",
      STRUCTURAL: "🏢",
      ELECTRICAL: "⚡",
      MECHANICAL: "⚙️",
      ARCHITECTURAL: "🏛️",
      PLUMBING: "🔧",
      HVAC: "❄️",
    };
    return icons[discipline] || "📋";
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Drawing Register</h1>
          <p className="text-sm text-gray-600 mt-1">
            {drawings.length} drawing{drawings.length !== 1 ? "s" : ""} found
          </p>
        </div>
        <button className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium">
          + Add Drawing
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        {drawings.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">
                    Drawing Number
                  </th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">Title</th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">Discipline</th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">Revision</th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">
                    Revision Date
                  </th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">Status</th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">Package</th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">Scale</th>
                  <th className="px-6 py-3 text-center font-semibold text-gray-700">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {drawings.map((drawing) => (
                  <tr key={drawing.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4 text-gray-900 font-medium">
                      {drawing.drawingNumber}
                    </td>
                    <td className="px-6 py-4 text-gray-900">{drawing.title}</td>
                    <td className="px-6 py-4">
                      <span className="flex items-center gap-2">
                        {getDisciplineIcon(drawing.discipline)} {drawing.discipline}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-gray-600">{drawing.revision}</td>
                    <td className="px-6 py-4 text-gray-600 text-xs">
                      {new Date(drawing.revisionDate).toLocaleDateString()}
                    </td>
                    <td className="px-6 py-4">
                      <span
                        className={`inline-block px-3 py-1 rounded-full text-xs font-medium ${getStatusColor(
                          drawing.status
                        )}`}
                      >
                        {drawing.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-gray-600 text-sm">{drawing.packageCode}</td>
                    <td className="px-6 py-4 text-gray-600 text-sm">{drawing.scale}</td>
                    <td className="px-6 py-4 text-center">
                      <button className="text-blue-600 hover:text-blue-800 text-xs font-medium">
                        View
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="text-center py-12">
            <p className="text-gray-500">No drawings found</p>
          </div>
        )}
      </div>
    </div>
  );
}
