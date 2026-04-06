"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { getErrorMessage } from "@/lib/utils/error";
import { documentApi } from "@/lib/api/documentApi";
import { TabTip } from "@/components/common/TabTip";
import type { RfiRegister } from "@/lib/api/documentApi";

interface RfiFormData {
  rfiNumber: string;
  subject: string;
  description: string;
  priority: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  raisedBy: string;
  assignedTo: string;
  raisedDate: string;
  dueDate: string;
}

export default function RfisPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [filterStatus, setFilterStatus] = useState<string>("ALL");
  const [formData, setFormData] = useState<RfiFormData>({
    rfiNumber: "",
    subject: "",
    description: "",
    priority: "MEDIUM",
    raisedBy: "",
    assignedTo: "",
    raisedDate: new Date().toISOString().split("T")[0],
    dueDate: "",
  });
  const [error, setError] = useState("");
  const queryClient = useQueryClient();

  const { data: rfis = [] } = useQuery({
    queryKey: ["rfis", projectId],
    queryFn: () => documentApi.listRfis(projectId),
    select: (response) => response.data || [],
  });

  const createRfiMutation = useMutation({
    mutationFn: (data: RfiFormData) => documentApi.createRfi(projectId, { projectId, ...data }),
    onSuccess: () => {
      toast.success("RFI created successfully");
      setShowCreateForm(false);
      setFormData({
        rfiNumber: "",
        subject: "",
        description: "",
        priority: "MEDIUM",
        raisedBy: "",
        assignedTo: "",
        raisedDate: new Date().toISOString().split("T")[0],
        dueDate: "",
      });
      setError("");
      queryClient.invalidateQueries({ queryKey: ["rfis", projectId] });
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to create RFI");
      setError(msg);
      toast.error(msg);
    },
  });

  const filteredRfis = rfis.filter((rfi) =>
    filterStatus === "ALL" ? true : rfi.status === filterStatus
  );

  const handleFormChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!formData.rfiNumber || !formData.subject) {
      setError("RFI Number and Subject are required");
      return;
    }

    createRfiMutation.mutate(formData);
  };

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
        return "bg-red-500/10 text-red-400 ring-1 ring-red-500/20";
      case "RESPONDED":
        return "bg-blue-500/10 text-blue-400 ring-1 ring-blue-500/20";
      case "CLOSED":
        return "bg-emerald-500/10 text-emerald-400 ring-1 ring-emerald-500/20";
      case "OVERDUE":
        return "bg-orange-500/10 text-orange-400 ring-1 ring-orange-500/20";
      default:
        return "bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50";
    }
  };

  const getPriorityColor = (priority: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL") => {
    switch (priority) {
      case "LOW":
        return "bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50";
      case "MEDIUM":
        return "bg-amber-500/10 text-amber-400 ring-1 ring-amber-500/20";
      case "HIGH":
        return "bg-orange-500/10 text-orange-400 ring-1 ring-orange-500/20";
      case "CRITICAL":
        return "bg-red-500/10 text-red-400 ring-1 ring-red-500/20";
      default:
        return "bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50";
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
      <TabTip
        title="Requests for Information (RFIs)"
        description="Track questions and clarifications between contractor and client. Each RFI has a response deadline and status to ensure timely resolution."
      />
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-white">Request for Information</h1>
          <p className="text-sm text-slate-400 mt-1">
            {filteredRfis.length} RFI{filteredRfis.length !== 1 ? "s" : ""} found
          </p>
        </div>
        <button
          onClick={() => setShowCreateForm(!showCreateForm)}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-500 transition-colors text-sm font-medium"
        >
          + Create RFI
        </button>
      </div>

      {/* Create Form */}
      {showCreateForm && (
        <div className="bg-slate-900/50 rounded-xl border border-slate-800 p-6 shadow-xl">
          {error && (
            <div className="mb-4 rounded-md bg-red-500/10 p-3 text-sm text-red-400">
              {error}
            </div>
          )}
          <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">
                RFI Number
              </label>
              <input
                type="text"
                name="rfiNumber"
                value={formData.rfiNumber}
                onChange={handleFormChange}
                placeholder="e.g., RFI-001"
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">Priority</label>
              <select
                name="priority"
                value={formData.priority}
                onChange={handleFormChange}
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
              >
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
                <option value="CRITICAL">CRITICAL</option>
              </select>
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-slate-300 mb-1">Subject</label>
              <input
                type="text"
                name="subject"
                value={formData.subject}
                onChange={handleFormChange}
                placeholder="Brief description of RFI"
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Description
              </label>
              <textarea
                name="description"
                value={formData.description}
                onChange={handleFormChange}
                placeholder="Detailed description"
                rows={3}
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Raised By
              </label>
              <input
                type="text"
                name="raisedBy"
                value={formData.raisedBy}
                onChange={handleFormChange}
                placeholder="Your name"
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Assigned To
              </label>
              <input
                type="text"
                name="assignedTo"
                value={formData.assignedTo}
                onChange={handleFormChange}
                placeholder="Assignee"
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">Raised Date *</label>
              <input
                type="date"
                name="raisedDate"
                value={formData.raisedDate}
                onChange={handleFormChange}
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">Due Date *</label>
              <input
                type="date"
                name="dueDate"
                value={formData.dueDate}
                onChange={handleFormChange}
                className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
              />
            </div>
            <div className="col-span-2 flex gap-3">
              <button
                type="submit"
                disabled={createRfiMutation.isPending}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-500 disabled:bg-slate-600 transition-colors font-medium"
              >
                {createRfiMutation.isPending ? "Creating..." : "Create RFI"}
              </button>
              <button
                type="button"
                onClick={() => setShowCreateForm(false)}
                className="flex-1 px-4 py-2 border border-slate-700 bg-slate-800 text-slate-300 rounded-lg hover:bg-slate-700 transition-colors font-medium"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Filter Tabs */}
      <div className="flex gap-2 border-b border-slate-800">
        {["ALL", "OPEN", "RESPONDED", "CLOSED", "OVERDUE"].map((status) => (
          <button
            key={status}
            onClick={() => setFilterStatus(status)}
            className={`px-4 py-2 text-sm font-medium transition-colors ${
              filterStatus === status
                ? "border-b-2 border-blue-500 text-blue-400"
                : "text-slate-400 hover:text-white"
            }`}
          >
            {status}
          </button>
        ))}
      </div>

      {/* RFI List */}
      <div className="bg-slate-900/50 rounded-xl border border-slate-800 overflow-hidden shadow-xl">
        {filteredRfis.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-900/80 border-b border-slate-800">
                <tr>
                  <th className="px-6 py-3 text-left font-semibold text-slate-400">
                    RFI Number
                  </th>
                  <th className="px-6 py-3 text-left font-semibold text-slate-400">Subject</th>
                  <th className="px-6 py-3 text-left font-semibold text-slate-400">
                    Raised By
                  </th>
                  <th className="px-6 py-3 text-left font-semibold text-slate-400">
                    Assigned To
                  </th>
                  <th className="px-6 py-3 text-left font-semibold text-slate-400">Priority</th>
                  <th className="px-6 py-3 text-left font-semibold text-slate-400">Status</th>
                  <th className="px-6 py-3 text-left font-semibold text-slate-400">Due Date</th>
                  <th className="px-6 py-3 text-center font-semibold text-slate-400">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/50">
                {filteredRfis.map((rfi) => (
                  <tr
                    key={rfi.id}
                    className={`hover:bg-slate-800/30 transition-colors border-slate-800/50 ${
                      isOverdue(rfi.dueDate) && rfi.status !== "CLOSED"
                        ? "bg-orange-500/10"
                        : ""
                    }`}
                  >
                    <td className="px-6 py-4 text-white font-medium">{rfi.rfiNumber}</td>
                    <td className="px-6 py-4 text-white max-w-xs truncate">{rfi.subject}</td>
                    <td className="px-6 py-4 text-slate-400 text-sm">{rfi.raisedBy}</td>
                    <td className="px-6 py-4 text-slate-400 text-sm">{rfi.assignedTo}</td>
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
                        <span className="text-slate-400">
                          {new Date(rfi.dueDate).toLocaleDateString()}
                        </span>
                        {getDueDateAlert(rfi.dueDate, rfi.status) && (
                          <span className="text-xs font-medium text-orange-400">
                            {getDueDateAlert(rfi.dueDate, rfi.status)}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-4 text-center">
                      <button className="text-blue-400 hover:text-blue-300 text-xs font-medium">
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
            <p className="text-slate-500">
              {filterStatus === "ALL" ? "No RFIs found" : `No ${filterStatus} RFIs found`}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
