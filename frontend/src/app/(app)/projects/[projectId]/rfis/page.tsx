"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { documentApi } from "@/lib/api/documentApi";
import type { RfiRegister } from "@/lib/api/documentApi";

export default function RfisPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [filterStatus, setFilterStatus] = useState<string>("ALL");
  const queryClient = useQueryClient();

  const { data: rfis = [] } = useQuery({
    queryKey: ["rfis", projectId],
    queryFn: () => documentApi.listRfis(projectId),
    select: (response) => response.data || [],
  });

  const filteredRfis = rfis.filter((rfi) =>
    filterStatus === "ALL" ? true : rfi.status === filterStatus
  );

  const isOverdue = (dueDate: string): boolean => {
    return new Date(dueDate) < new Date();
  };

  const daysUntilDue = (dueDate: string): number => {
    const today = new Date();
    const due = new Date(dueDate);
    const diff = Math.ceil((due.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
    return diff;
  };

  const getStatusColor = (status: "OPEN" | "RESPONDED" | "CLOSED" | "OVERDUE") => {
    switch (status) {
      case "OPEN":
        return "bg-red-100 text-red-800";
      case "RESPONDED":
        return "bg-blue-100 text-blue-800";
      case "CLOSED":
        return "bg-green-100 text-green-800";
      case "OVERDUE":
        return "bg-orange-100 text-orange-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const getPriorityColor = (priority: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL") => {
    switch (priority) {
      case "LOW":
        return "bg-gray-100 text-gray-800";
      case "MEDIUM":
        return "bg-yellow-100 text-yellow-800";
      case "HIGH":
        return "bg-orange-100 text-orange-800";
      case "CRITICAL":
        return "bg-red-100 text-red-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
  };

  const getDueDateAlert = (dueDate: string, status: string) => {
    if (status === "CLOSED") return null;

    const days = daysUntilDue(dueDate);
    if (days < 0) return "⚠️ Overdue";
    if (days === 0) return "📅 Due Today";
    if (days <= 3) return `📅 ${days}d remaining`;
    return null;
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Request for Information</h1>
          <p className="text-sm text-gray-600 mt-1">
            {filteredRfis.length} RFI{filteredRfis.length !== 1 ? "s" : ""} found
          </p>
        </div>
        <button
          onClick={() => setShowCreateForm(!showCreateForm)}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium"
        >
          + Create RFI
        </button>
      </div>

      {/* Create Form */}
      {showCreateForm && (
        <div className="bg-blue-50 rounded-lg border border-blue-200 p-6">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                RFI Number
              </label>
              <input
                type="text"
                placeholder="e.g., RFI-001"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Priority</label>
              <select className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent">
                <option>LOW</option>
                <option>MEDIUM</option>
                <option>HIGH</option>
                <option>CRITICAL</option>
              </select>
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">Subject</label>
              <input
                type="text"
                placeholder="Brief description of RFI"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Description
              </label>
              <textarea
                placeholder="Detailed description"
                rows={3}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Raised By
              </label>
              <input
                type="text"
                placeholder="Your name"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Assigned To
              </label>
              <input
                type="text"
                placeholder="Assignee"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Due Date</label>
              <input
                type="date"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <div className="col-span-2 flex gap-3">
              <button className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium">
                Create RFI
              </button>
              <button
                onClick={() => setShowCreateForm(false)}
                className="flex-1 px-4 py-2 border border-gray-300 bg-white text-gray-700 rounded-lg hover:bg-gray-50 transition-colors font-medium"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Filter Tabs */}
      <div className="flex gap-2 border-b border-gray-200">
        {["ALL", "OPEN", "RESPONDED", "CLOSED", "OVERDUE"].map((status) => (
          <button
            key={status}
            onClick={() => setFilterStatus(status)}
            className={`px-4 py-2 text-sm font-medium transition-colors ${
              filterStatus === status
                ? "border-b-2 border-blue-600 text-blue-600"
                : "text-gray-600 hover:text-gray-900"
            }`}
          >
            {status}
          </button>
        ))}
      </div>

      {/* RFI List */}
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        {filteredRfis.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">
                    RFI Number
                  </th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">Subject</th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">
                    Raised By
                  </th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">
                    Assigned To
                  </th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">Priority</th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">Status</th>
                  <th className="px-6 py-3 text-left font-semibold text-gray-700">Due Date</th>
                  <th className="px-6 py-3 text-center font-semibold text-gray-700">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {filteredRfis.map((rfi) => (
                  <tr
                    key={rfi.id}
                    className={`hover:bg-gray-50 transition-colors ${
                      isOverdue(rfi.dueDate) && rfi.status !== "CLOSED"
                        ? "bg-orange-50"
                        : ""
                    }`}
                  >
                    <td className="px-6 py-4 text-gray-900 font-medium">{rfi.rfiNumber}</td>
                    <td className="px-6 py-4 text-gray-900 max-w-xs truncate">{rfi.subject}</td>
                    <td className="px-6 py-4 text-gray-600 text-sm">{rfi.raisedBy}</td>
                    <td className="px-6 py-4 text-gray-600 text-sm">{rfi.assignedTo}</td>
                    <td className="px-6 py-4">
                      <span
                        className={`inline-block px-3 py-1 rounded-full text-xs font-medium ${getPriorityColor(
                          rfi.priority
                        )}`}
                      >
                        {rfi.priority}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <span
                        className={`inline-block px-3 py-1 rounded-full text-xs font-medium ${getStatusColor(
                          rfi.status
                        )}`}
                      >
                        {rfi.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm">
                      <div className="flex flex-col gap-1">
                        <span className="text-gray-600">
                          {new Date(rfi.dueDate).toLocaleDateString()}
                        </span>
                        {getDueDateAlert(rfi.dueDate, rfi.status) && (
                          <span className="text-xs font-medium text-orange-600">
                            {getDueDateAlert(rfi.dueDate, rfi.status)}
                          </span>
                        )}
                      </div>
                    </td>
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
            <p className="text-gray-500">
              {filterStatus === "ALL" ? "No RFIs found" : `No ${filterStatus} RFIs found`}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
